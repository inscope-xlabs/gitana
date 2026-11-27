// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/ops/DeleteFile.kt
package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.DeleteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubFailure
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubJson
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubMethod
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubRequest
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubTransport
import com.inscopexlabs.gitana.gitbridge.github.client.UpstreamErrorMapper
import com.inscopexlabs.gitana.gitbridge.github.client.encodePathSegments
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Standard 1 – Stage 1: A file is deleted through
 *                       `DELETE /repos/{owner}/{repo}/contents/{path}`.
 * Standard 1 – Stage 2: The transport is injected; the operation keeps no
 *                       state between calls.
 * Standard 1 – Stage 3: The current file SHA is resolved before the delete,
 *                       a missing file is reported as not found, and a
 *                       conflict is reported as a conflict rather than
 *                       retried.
 *
 * Spec 7, 7.1, RTB-T17, RTB-T18, RTB-48.
 */
internal class DeleteFile(private val transport: GitHubTransport) {

    /** Deletes `args.path` on `args.branch`. */
    suspend fun run(owner: String, name: String, args: DeleteFileArgs): ToolResult.FileDeleted {
        // A deletion requires the current file SHA (spec 7.1, RTB-T17).
        val sha = resolveFileSha(owner, name, args.path, args.branch)
            ?: throw GitHubFailure.notFound("The file to delete does not exist")

        val body = buildJsonObject {
            put("message", args.message)
            put("sha", sha)
            put("branch", args.branch)
        }.toString()

        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.DELETE,
                path = "/repos/$owner/$name/contents/${encodePathSegments(args.path)}",
                body = body,
                mutating = true,
            ),
        )
        if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

        val json = GitHubJson.parseObject(response.body)
            ?: throw GitHubFailure.malformed("contents")
        val commitSha = GitHubJson.obj(json, "commit")?.let { GitHubJson.string(it, "sha") }
            ?: throw GitHubFailure.malformed("contents")

        return ToolResult.FileDeleted(path = args.path, commitSha = commitSha)
    }

    /** Resolves the current file SHA, or null when the file does not exist. */
    private suspend fun resolveFileSha(
        owner: String,
        name: String,
        path: String,
        branch: String,
    ): String? {
        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.GET,
                path = "/repos/$owner/$name/contents/${encodePathSegments(path)}",
                query = mapOf("ref" to branch),
            ),
        )
        if (response.status == HTTP_NOT_FOUND) return null
        if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

        val json = GitHubJson.parseObject(response.body)
            ?: throw GitHubFailure.malformed("contents")
        return GitHubJson.string(json, "sha")
    }

    private companion object {
        /** Status that means the file does not exist. */
        const val HTTP_NOT_FOUND: Int = 404
    }
}
