// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/GitHubAdapter.kt
package com.inscopexlabs.gitana.gitbridge.github

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeException
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreateBranchArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreatePullRequestArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.DeleteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ListBranchesArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolArguments
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.core.ports.GitHostPort
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubFailure
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubTransport
import com.inscopexlabs.gitana.gitbridge.github.client.RateLimitHandler
import com.inscopexlabs.gitana.gitbridge.github.ops.CreateBranch
import com.inscopexlabs.gitana.gitbridge.github.ops.CreatePullRequest
import com.inscopexlabs.gitana.gitbridge.github.ops.DeleteFile
import com.inscopexlabs.gitana.gitbridge.github.ops.ListBranches
import com.inscopexlabs.gitana.gitbridge.github.ops.ReadFile
import com.inscopexlabs.gitana.gitbridge.github.ops.WriteFile
import kotlinx.coroutines.delay

/**
 * Standard 1 – Stage 1: Every canonical operation reaches GitHub and returns
 *                       its documented result.
 * Standard 1 – Stage 2: The transport and the backoff policy are injected, so
 *                       the adapter holds no network or timing state of its
 *                       own and both can be replaced in isolation.
 * Standard 1 – Stage 3: Failures are mapped to typed errors, retryable
 *                       failures are retried inside the recorded backoff
 *                       bounds and the request deadline, and a write whose
 *                       outcome is unknown is never replayed.
 *
 * Spec 7, 7.1, 11.1, RTB-15, RTB-43, RTB-44, RTB-48, RTB-58.
 */
class GitHubAdapter(
    private val client: GitHubTransport,
    private val rateLimitHandler: RateLimitHandler = RateLimitHandler(),
) : GitHostPort {

    private val listBranches = ListBranches(client)
    private val createBranch = CreateBranch(client)
    private val readFile = ReadFile(client)
    private val writeFile = WriteFile(client)
    private val deleteFile = DeleteFile(client)
    private val createPullRequest = CreatePullRequest(client)

    /**
     * Executes [request] against GitHub.
     *
     * Throws [BridgeException] with a sanitized error on failure.
     */
    override suspend fun execute(request: RequestEnvelope): ToolResult {
        val repository = parseRepository(request.arguments.repo)
        return when (request.operation) {
            ToolOperation.LIST_BRANCHES -> withRetry(request, mutating = false) {
                listBranches.run(
                    repository.owner,
                    repository.name,
                    requireArgs<ListBranchesArgs>(request.arguments),
                )
            }

            ToolOperation.CREATE_BRANCH -> withRetry(request, mutating = true) {
                createBranch.run(
                    repository.owner,
                    repository.name,
                    requireArgs<CreateBranchArgs>(request.arguments),
                )
            }

            ToolOperation.READ_FILE -> withRetry(request, mutating = false) {
                readFile.run(
                    repository.owner,
                    repository.name,
                    requireArgs<ReadFileArgs>(request.arguments),
                )
            }

            ToolOperation.WRITE_FILE -> withRetry(request, mutating = true) {
                writeFile.run(
                    repository.owner,
                    repository.name,
                    requireArgs<WriteFileArgs>(request.arguments),
                )
            }

            ToolOperation.DELETE_FILE -> withRetry(request, mutating = true) {
                deleteFile.run(
                    repository.owner,
                    repository.name,
                    requireArgs<DeleteFileArgs>(request.arguments),
                )
            }

            ToolOperation.CREATE_PULL_REQUEST -> withRetry(request, mutating = true) {
                createPullRequest.run(
                    repository.owner,
                    repository.name,
                    requireArgs<CreatePullRequestArgs>(request.arguments),
                )
            }
        }
    }

    /**
     * Runs [block] and retries it while the failure is retryable, the
     * attempt budget allows another try and the request deadline has not
     * passed.
     */
    private suspend fun <T> withRetry(
        request: RequestEnvelope,
        mutating: Boolean,
        block: suspend () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (failure: GitHubFailure) {
                val error = finalError(failure, mutating)
                val now = System.currentTimeMillis()
                val deadline = request.expiresAtMillis
                if (!error.retryable || !rateLimitHandler.canRetry(attempt, deadline, now)) {
                    throw BridgeException(error)
                }
                val waitMillis = rateLimitHandler.delayMillis(attempt, failure.retryAfterSeconds)
                if (!rateLimitHandler.fitsDeadline(waitMillis, deadline, now)) {
                    throw BridgeException(error)
                }
                delay(waitMillis)
                attempt++
            }
        }
    }

    /**
     * Decides the error the caller sees.
     *
     * A write that reached GitHub but failed without a usable answer is
     * reported as `OUTCOME_UNKNOWN`: the side effect may have been applied,
     * so the request must be reconciled rather than replayed (RTB-43,
     * RTB-44). A failure that never left the device stays retryable.
     */
    private fun finalError(failure: GitHubFailure, mutating: Boolean): BridgeError {
        if (!mutating) return failure.error
        if (!failure.dispatched) return failure.error
        if (failure.error.code != ErrorCode.UPSTREAM_UNAVAILABLE) return failure.error
        return failure.error.copy(
            code = ErrorCode.OUTCOME_UNKNOWN,
            category = ErrorCategory.TRANSPORT,
            httpStatus = 504,
            retryable = false,
            executionState = ExecutionState.OUTCOME_UNKNOWN,
            message = "The write reached GitHub but the outcome is unknown; " +
                "reconcile the repository state before retrying",
        )
    }

    /**
     * Resolves the canonical owner and repository name.
     *
     * The allow-list check belongs to the policy engine; the adapter still
     * fails closed on a malformed repository name (RTB-15).
     */
    private fun parseRepository(repo: String): Repository {
        val separator = repo.indexOf('/')
        if (separator <= 0 || separator >= repo.length - 1) {
            throw BridgeException(
                validationError("The repository must be given as 'owner/name'"),
            )
        }
        val owner = repo.substring(0, separator)
        val name = repo.substring(separator + 1)
        if (!OWNER_NAME.matches(owner) || !REPOSITORY_NAME.matches(name)) {
            throw BridgeException(
                validationError("The repository name is not in canonical 'owner/name' form"),
            )
        }
        // Repositories are compared in lower case (RTB-D13).
        return Repository(owner = owner.lowercase(), name = name.lowercase())
    }

    /** The owner and name resolved from an approved repository record. */
    private data class Repository(val owner: String, val name: String)

    private companion object {
        /** Repository owner pattern (`tool-contract.v1.json`). */
        val OWNER_NAME: Regex = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,38}$")

        /** Repository name pattern (`tool-contract.v1.json`). */
        val REPOSITORY_NAME: Regex = Regex("^[A-Za-z0-9_.-]{1,100}$")
    }
}

/**
 * Narrows [arguments] to the type the operation expects, failing closed when
 * the envelope and the operation disagree.
 */
private inline fun <reified T : ToolArguments> requireArgs(arguments: ToolArguments): T =
    arguments as? T ?: throw BridgeException(
        BridgeError(
            code = ErrorCode.VALIDATION_FAILED,
            category = ErrorCategory.VALIDATION,
            httpStatus = 400,
            retryable = false,
            executionState = ExecutionState.NOT_STARTED,
            message = "The operation arguments do not match the requested operation",
        ),
    )

/** Builds the validation error used when the request cannot be dispatched. */
private fun validationError(message: String): BridgeError = BridgeError(
    code = ErrorCode.VALIDATION_FAILED,
    category = ErrorCategory.VALIDATION,
    httpStatus = 400,
    retryable = false,
    executionState = ExecutionState.NOT_STARTED,
    message = message,
)
