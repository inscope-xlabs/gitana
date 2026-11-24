package com.example.data

import com.example.domain.GitHubRepoSlug
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.ByteString.Companion.encodeUtf8
import okio.source
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

enum class CloneStep {
    VERIFYING_ACCOUNT_AND_SOURCE,
    FETCHING_GIT_OBJECTS,
    CREATING_TARGET_REPO,
    PUSHING_REFS_AND_PACK,
    CONFIGURING_DEFAULT_BRANCH,
}

data class CloneProgress(
    val step: CloneStep,
    val detail: String,
    val completedSteps: Set<CloneStep>,
)

data class CloneSuccessResult(
    val sourceFullName: String,
    val targetFullName: String,
    val targetHtmlUrl: String,
    val branchesCount: Int,
    val tagsCount: Int,
    val packSizeBytes: Long,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val isEmptyRepo: Boolean,
)

data class CloneFailureException(
    val failedStep: CloneStep,
    val title: String,
    override val message: String,
    val suggestion: String,
) : Exception(message)

class GitHubCloneRepository(
    private val cacheDir: File,
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
    apiBaseUrl: String = "https://api.github.com/",
) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val errorAdapter = moshi.adapter(GitHubApiErrorDto::class.java)

    private val apiService: GitHubApiService = Retrofit.Builder()
        .baseUrl(apiBaseUrl)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApiService::class.java)

    suspend fun inspectRepository(
        slug: GitHubRepoSlug,
        token: String?,
    ): Result<GitHubRepoDto> = withContext(Dispatchers.IO) {
        try {
            val authHeader = token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
            val response = apiService.getRepository(slug.owner, slug.name, authHeader)
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return@withContext Result.failure(IOException("Empty response from GitHub"))
                Result.success(body)
            } else {
                val apiMsg = parseApiErrorMessage(response)
                val friendly = when (response.code()) {
                    404 -> "Repository '${slug.fullName}' was not found (or is private without a valid token)."
                    401 -> "Authentication failed (401). Check your Personal Access Token."
                    403 -> "Access forbidden (403): ${apiMsg ?: "Rate limit exceeded or insufficient scope."}"
                    else -> "GitHub returned HTTP ${response.code()}: ${apiMsg ?: response.message()}"
                }
                Result.failure(IOException(friendly))
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("No internet connection or unable to reach github.com."))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Connection to GitHub timed out. Please try again."))
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Unable to inspect repository."))
        }
    }

    suspend fun cloneRepository(
        source: GitHubRepoSlug,
        newRepoName: String,
        isPrivate: Boolean,
        customDescription: String,
        token: String,
        onProgress: suspend (CloneProgress) -> Unit,
    ): Result<CloneSuccessResult> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanNewName = newRepoName.trim()
        val bearerAuth = "Bearer $cleanToken"
        val gitBasicAuth = buildGitBasicAuthHeader(cleanToken)
        val completed = linkedSetOf<CloneStep>()
        var packTempFile: File? = null

        try {
            // Step 1: Verify authenticated user, source repo, and ensure target name is available
            onProgress(
                CloneProgress(
                    step = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    detail = "Authenticating with GitHub and inspecting ${source.fullName}…",
                    completedSteps = completed.toSet(),
                ),
            )

            val userResponse = apiService.getAuthenticatedUser(bearerAuth)
            if (!userResponse.isSuccessful) {
                throw mapRestFailure(
                    step = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    response = userResponse,
                    context = "account authentication",
                )
            }
            val targetOwner = userResponse.body()?.login
                ?: throw CloneFailureException(
                    failedStep = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    title = "Account Verification Failed",
                    message = "GitHub returned an empty user profile.",
                    suggestion = "Verify your Personal Access Token and try again.",
                )

            val sourceResponse = apiService.getRepository(source.owner, source.name, bearerAuth)
            if (!sourceResponse.isSuccessful) {
                throw mapRestFailure(
                    step = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    response = sourceResponse,
                    context = "source repository '${source.fullName}'",
                )
            }
            val sourceRepo = sourceResponse.body()
                ?: throw CloneFailureException(
                    failedStep = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    title = "Source Repository Unreadable",
                    message = "GitHub returned an empty response for '${source.fullName}'.",
                    suggestion = "Check the repository slug and try again.",
                )

            // Check if destination already exists before doing any heavy transfer
            val existingTargetCheck = apiService.getRepository(targetOwner, cleanNewName, bearerAuth)
            if (existingTargetCheck.isSuccessful) {
                throw CloneFailureException(
                    failedStep = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    title = "Destination Already Exists",
                    message = "Repository '$targetOwner/$cleanNewName' already exists in your GitHub account.",
                    suggestion = "Choose a different new repository name.",
                )
            } else if (existingTargetCheck.code() != 404) {
                throw mapRestFailure(
                    step = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                    response = existingTargetCheck,
                    context = "checking target availability '$targetOwner/$cleanNewName'",
                )
            }

            completed.add(CloneStep.VERIFYING_ACCOUNT_AND_SOURCE)

            // Step 2: Discover Git refs and download full packfile from source
            onProgress(
                CloneProgress(
                    step = CloneStep.FETCHING_GIT_OBJECTS,
                    detail = "Discovering branches & tags from ${sourceRepo.fullName}…",
                    completedSteps = completed.toSet(),
                ),
            )

            val sourceAuthForGit = if (sourceRepo.private) gitBasicAuth else null
            val sourceRefsAdv = fetchRefAdvertisement(
                cloneUrl = sourceRepo.cloneUrl,
                service = "git-upload-pack",
                basicAuth = sourceAuthForGit,
            )

            val branches = sourceRefsAdv.refs.filter { it.refName.startsWith("refs/heads/") }
            val tags = sourceRefsAdv.refs.filter { it.refName.startsWith("refs/tags/") }
            val allRefsToClone = branches + tags
            val isEmptyRepo = allRefsToClone.isEmpty()

            var packSizeBytes = 0L
            if (!isEmptyRepo) {
                packTempFile = File.createTempFile("repoclone_", ".pack", cacheDir)
                onProgress(
                    CloneProgress(
                        step = CloneStep.FETCHING_GIT_OBJECTS,
                        detail = "Downloading ${branches.size} branch(es) and ${tags.size} tag(s)…",
                        completedSteps = completed.toSet(),
                    ),
                )
                packSizeBytes = downloadUploadPack(
                    cloneUrl = sourceRepo.cloneUrl,
                    advertisement = sourceRefsAdv,
                    refsToFetch = allRefsToClone,
                    basicAuth = sourceAuthForGit,
                    destinationFile = packTempFile,
                ) { serverMsg ->
                    // Progress hook kept lightweight without blocking I/O
                }
            }

            completed.add(CloneStep.FETCHING_GIT_OBJECTS)

            // Step 3: Create the destination repository on GitHub
            onProgress(
                CloneProgress(
                    step = CloneStep.CREATING_TARGET_REPO,
                    detail = "Creating '$targetOwner/$cleanNewName' on GitHub…",
                    completedSteps = completed.toSet(),
                ),
            )

            val finalDescription = customDescription.trim().ifEmpty {
                sourceRepo.description?.takeIf { it.isNotBlank() }
                    ?: "Cloned from ${sourceRepo.fullName}"
            }

            val createResponse = apiService.createUserRepository(
                authorization = bearerAuth,
                request = CreateRepoRequest(
                    name = cleanNewName,
                    description = finalDescription,
                    private = isPrivate,
                    autoInit = false,
                ),
            )
            if (!createResponse.isSuccessful) {
                throw mapRestFailure(
                    step = CloneStep.CREATING_TARGET_REPO,
                    response = createResponse,
                    context = "creating repository '$targetOwner/$cleanNewName'",
                )
            }
            val createdRepo = createResponse.body()
                ?: throw CloneFailureException(
                    failedStep = CloneStep.CREATING_TARGET_REPO,
                    title = "Repository Creation Failed",
                    message = "GitHub returned an empty response after creating '$targetOwner/$cleanNewName'.",
                    suggestion = "Check your GitHub account to verify the repository status.",
                )

            completed.add(CloneStep.CREATING_TARGET_REPO)

            // Step 4: Push packfile and all refs via git-receive-pack
            if (!isEmptyRepo && packTempFile != null) {
                onProgress(
                    CloneProgress(
                        step = CloneStep.PUSHING_REFS_AND_PACK,
                        detail = "Pushing ${formatByteSize(packSizeBytes)} across ${allRefsToClone.size} ref(s)…",
                        completedSteps = completed.toSet(),
                    ),
                )

                val receiveAdv = fetchRefAdvertisement(
                    cloneUrl = createdRepo.cloneUrl,
                    service = "git-receive-pack",
                    basicAuth = gitBasicAuth,
                )
                if ("report-status" !in receiveAdv.capabilities && receiveAdv.capabilities.isNotEmpty()) {
                    throw CloneFailureException(
                        failedStep = CloneStep.PUSHING_REFS_AND_PACK,
                        title = "Push Protocol Mismatch",
                        message = "Target Git server did not advertise 'report-status'.",
                        suggestion = "Verify your token has write access ('repo' or 'Contents: Read & Write').",
                    )
                }

                pushReceivePack(
                    cloneUrl = createdRepo.cloneUrl,
                    refs = allRefsToClone,
                    packFile = packTempFile,
                    basicAuth = gitBasicAuth,
                )
            }

            completed.add(CloneStep.PUSHING_REFS_AND_PACK)

            // Step 5: Match the default branch if needed
            val desiredDefaultBranch = sourceRepo.defaultBranch
            val hasDefaultBranchRef = branches.any { it.refName == "refs/heads/$desiredDefaultBranch" }
            if (!isEmptyRepo && hasDefaultBranchRef && createdRepo.defaultBranch != desiredDefaultBranch) {
                onProgress(
                    CloneProgress(
                        step = CloneStep.CONFIGURING_DEFAULT_BRANCH,
                        detail = "Setting default branch to '$desiredDefaultBranch'…",
                        completedSteps = completed.toSet(),
                    ),
                )
                apiService.updateRepository(
                    owner = targetOwner,
                    repo = cleanNewName,
                    authorization = bearerAuth,
                    request = UpdateRepoRequest(defaultBranch = desiredDefaultBranch),
                )
            }
            completed.add(CloneStep.CONFIGURING_DEFAULT_BRANCH)

            Result.success(
                CloneSuccessResult(
                    sourceFullName = sourceRepo.fullName,
                    targetFullName = createdRepo.fullName,
                    targetHtmlUrl = createdRepo.htmlUrl,
                    branchesCount = branches.size,
                    tagsCount = tags.size,
                    packSizeBytes = packSizeBytes,
                    defaultBranch = if (hasDefaultBranchRef) desiredDefaultBranch else createdRepo.defaultBranch,
                    isPrivate = createdRepo.private,
                    isEmptyRepo = isEmptyRepo,
                ),
            )
        } catch (e: CloneFailureException) {
            Result.failure(e)
        } catch (e: UnknownHostException) {
            Result.failure(
                CloneFailureException(
                    failedStep = currentActiveStep(completed),
                    title = "Network Unavailable",
                    message = "Unable to resolve github.com. Check your internet connection.",
                    suggestion = "Reconnect to Wi-Fi or mobile data and retry.",
                ),
            )
        } catch (e: SocketTimeoutException) {
            Result.failure(
                CloneFailureException(
                    failedStep = currentActiveStep(completed),
                    title = "Request Timed Out",
                    message = "GitHub took too long to respond while transferring repository data.",
                    suggestion = "Check your connection speed and try again.",
                ),
            )
        } catch (e: Exception) {
            Result.failure(
                CloneFailureException(
                    failedStep = currentActiveStep(completed),
                    title = "Clone Failed",
                    message = e.message ?: "An unexpected error occurred during cloning.",
                    suggestion = "Review your inputs and token permissions, then try again.",
                ),
            )
        } finally {
            packTempFile?.delete()
        }
    }

    private fun fetchRefAdvertisement(
        cloneUrl: String,
        service: String,
        basicAuth: String?,
    ): RefAdvertisement {
        val normalizedUrl = cloneUrl.removeSuffix("/")
        val requestBuilder = Request.Builder()
            .url("$normalizedUrl/info/refs?service=$service")
            .header("Accept", "application/x-$service-advertisement")
            .header("User-Agent", "git/2.42.0 RepoClone-Android/1.0")
            .get()
        if (basicAuth != null) {
            requestBuilder.header("Authorization", basicAuth)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val step = if (service == "git-upload-pack") {
                    CloneStep.FETCHING_GIT_OBJECTS
                } else {
                    CloneStep.PUSHING_REFS_AND_PACK
                }
                throw mapGitHttpFailure(step, response.code, service)
            }
            val bodyStream = response.body?.byteStream()
                ?: throw IOException("Empty ref advertisement from Git server")
            return GitPktLineCodec.parseRefAdvertisement(bodyStream, service)
        }
    }

    private fun downloadUploadPack(
        cloneUrl: String,
        advertisement: RefAdvertisement,
        refsToFetch: List<GitRef>,
        basicAuth: String?,
        destinationFile: File,
        onServerProgress: (String) -> Unit,
    ): Long {
        val normalizedUrl = cloneUrl.removeSuffix("/")
        val payloadBytes = GitPktLineCodec.buildUploadPackRequest(
            refs = refsToFetch,
            advertisedCapabilities = advertisement.capabilities,
        )
        val useSideBand = "side-band-64k" in advertisement.capabilities ||
            "side-band" in advertisement.capabilities

        val requestBuilder = Request.Builder()
            .url("$normalizedUrl/git-upload-pack")
            .header("Content-Type", "application/x-git-upload-pack-request")
            .header("Accept", "application/x-git-upload-pack-result")
            .header("User-Agent", "git/2.42.0 RepoClone-Android/1.0")
            .post(payloadBytes.toRequestBody("application/x-git-upload-pack-request".toMediaType()))
        if (basicAuth != null) {
            requestBuilder.header("Authorization", basicAuth)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw mapGitHttpFailure(CloneStep.FETCHING_GIT_OBJECTS, response.code, "git-upload-pack")
            }
            val stream = response.body?.byteStream()
                ?: throw IOException("Empty git-upload-pack response")
            return FileOutputStream(destinationFile).use { fos ->
                GitPktLineCodec.extractPackFromUploadResponse(
                    input = stream,
                    packOutput = fos,
                    useSideBand = useSideBand,
                    onProgressMessage = onServerProgress,
                )
            }
        }
    }

    private fun pushReceivePack(
        cloneUrl: String,
        refs: List<GitRef>,
        packFile: File,
        basicAuth: String,
    ) {
        val normalizedUrl = cloneUrl.removeSuffix("/")
        val headerBytes = GitPktLineCodec.buildReceivePackCommandHeader(refs)
        val requestBody = object : RequestBody() {
            override fun contentType() = "application/x-git-receive-pack-request".toMediaType()
            override fun contentLength(): Long = headerBytes.size.toLong() + packFile.length()
            override fun writeTo(sink: BufferedSink) {
                sink.write(headerBytes)
                packFile.source().use { source ->
                    sink.writeAll(source)
                }
            }
        }

        val request = Request.Builder()
            .url("$normalizedUrl/git-receive-pack")
            .header("Authorization", basicAuth)
            .header("Content-Type", "application/x-git-receive-pack-request")
            .header("Accept", "application/x-git-receive-pack-result")
            .header("User-Agent", "git/2.42.0 RepoClone-Android/1.0")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw mapGitHttpFailure(CloneStep.PUSHING_REFS_AND_PACK, response.code, "git-receive-pack")
            }
            val stream = response.body?.byteStream()
                ?: throw IOException("Empty git-receive-pack response")
            val report = GitPktLineCodec.parseReceivePackReport(stream)
            if (!report.isSuccess) {
                val failedRefs = report.refResults
                    .filterValues { it != "ok" }
                    .entries
                    .joinToString("; ") { "${it.key}: ${it.value}" }
                val detail = buildString {
                    if (report.unpackStatus != "ok") {
                        append("unpack ${report.unpackStatus}")
                    }
                    if (failedRefs.isNotEmpty()) {
                        if (isNotEmpty()) append(" — ")
                        append(failedRefs)
                    }
                }.ifEmpty { "Git server rejected the push." }

                throw CloneFailureException(
                    failedStep = CloneStep.PUSHING_REFS_AND_PACK,
                    title = "Git Push Rejected",
                    message = detail,
                    suggestion = "Ensure your token has write permission ('repo' or 'Contents: Read & Write') and retry.",
                )
            }
        }
    }

    private fun <T> mapRestFailure(
        step: CloneStep,
        response: Response<T>,
        context: String,
    ): CloneFailureException {
        val apiMsg = parseApiErrorMessage(response)
        return when (response.code()) {
            401 -> CloneFailureException(
                failedStep = step,
                title = "Authentication Failed (401)",
                message = "GitHub rejected your Personal Access Token during $context.",
                suggestion = "Double-check that your token is valid, unexpired, and pasted without extra characters.",
            )
            403 -> CloneFailureException(
                failedStep = step,
                title = "Permission Denied (403)",
                message = apiMsg ?: "Your token lacks required permissions for $context.",
                suggestion = "Use a classic PAT with 'repo' scope or a fine-grained PAT with Administration & Contents (Read & Write).",
            )
            404 -> CloneFailureException(
                failedStep = step,
                title = "Repository Not Found (404)",
                message = "Could not find $context on GitHub.",
                suggestion = "Verify the owner/repo spelling, or make sure your token has read access if it is private.",
            )
            422 -> CloneFailureException(
                failedStep = step,
                title = "Validation Error (422)",
                message = apiMsg ?: "GitHub rejected the repository configuration for $context.",
                suggestion = "Check that the new repository name isn't already taken or reserved.",
            )
            else -> CloneFailureException(
                failedStep = step,
                title = "GitHub Error (${response.code()})",
                message = apiMsg ?: response.message().ifEmpty { "Request failed during $context." },
                suggestion = "Check GitHub status and retry in a moment.",
            )
        }
    }

    private fun mapGitHttpFailure(step: CloneStep, code: Int, service: String): CloneFailureException {
        return when (code) {
            401, 403 -> CloneFailureException(
                failedStep = step,
                title = "Git Authorization Failed ($code)",
                message = "GitHub denied access to $service ($code).",
                suggestion = "Verify your token has 'repo' (classic) or 'Contents: Read & Write' (fine-grained) access.",
            )
            404 -> CloneFailureException(
                failedStep = step,
                title = "Git Endpoint Not Found (404)",
                message = "Could not access $service endpoint for this repository.",
                suggestion = "Ensure the repository exists and is accessible with your token.",
            )
            else -> CloneFailureException(
                failedStep = step,
                title = "Git Transfer Error ($code)",
                message = "Git transfer ($service) failed with HTTP $code.",
                suggestion = "Retry the clone operation.",
            )
        }
    }

    private fun <T> parseApiErrorMessage(response: Response<T>): String? {
        return try {
            val raw = response.errorBody()?.string() ?: return null
            val dto = errorAdapter.fromJson(raw) ?: return null
            val detailErrors = dto.errors
                ?.mapNotNull { it.message?.takeIf { m -> m.isNotBlank() } }
                ?.joinToString("; ")
                ?.takeIf { it.isNotBlank() }
            listOfNotNull(dto.message?.takeIf { it.isNotBlank() }, detailErrors)
                .joinToString(": ")
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildGitBasicAuthHeader(token: String): String {
        val encoded = "x-access-token:$token".encodeUtf8().base64()
        return "Basic $encoded"
    }

    private fun currentActiveStep(completed: Set<CloneStep>): CloneStep {
        return CloneStep.entries.firstOrNull { it !in completed } ?: CloneStep.PUSHING_REFS_AND_PACK
    }

    companion object {
        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        fun formatByteSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            }
        }
    }
}
