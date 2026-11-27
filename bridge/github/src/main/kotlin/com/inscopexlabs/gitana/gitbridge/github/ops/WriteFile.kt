// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/ops/WriteFile.kt
package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubFailure
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubJson
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubMethod
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubRequest
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubTransport
import com.inscopexlabs.gitana.gitbridge.github.client.UpstreamErrorMapper
import com.inscopexlabs.gitana.gitbridge.github.client.encodePathSegments
import com.inscopexlabs.gitana.gitbridge.github.codec.Base64Content
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Standard 1 – Stage 1: A file is written through
 *                       `PUT /repos/{owner}/{repo}/contents/{path}`.
 * Standard 1 – Stage 2: The transport is injected; the operation keeps no
 *                       state between calls and never logs the content.
 * Standard 1 – Stage 3: The current file SHA is resolved before an update
 *                       and omitted for a creation, oversized content is
 *                       rejected before it is encoded, and a conflict is
 *                       reported as a conflict rather than retried.
 *
 * Spec 7, 7.1, RTB-T17, RTB-T18, RTB-48, `DV-RTB-REQUEST-MAX-BYTES`.
 */
internal class WriteFile(private val transport: GitHubTransport) {

    /** Writes `args.content` to `args.path` on `args.branch`. */
    suspend fun run(owner: String, name: String, args: WriteFileArgs): ToolResult.FileWritten {
        val contentBytes = args.content.toByteArray(StandardCharsets.UTF_8)
        if (contentBytes.size > MAX_CONTENT_BYTES) {
            throw GitHubFailure.payloadTooLarge(
                "The file content exceeds the $MAX_CONTENT_BYTES byte ceiling",
            )
        }

        // An update needs the current file SHA; a creation must not send one
        // (spec 7.1, RTB-T17).
        val existingSha = resolveFileSha(owner, name, args.path, args.branch)

        val body = buildJsonObject {
            put("message", args.message)
            put("content", Base64Content.encode(args.content))
            put("branch", args.branch)
            if (existingSha != null) put("sha", existingSha)
        }.toString()

        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.PUT,
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

        return ToolResult.FileWritten(path = args.path, commitSha = commitSha)
    }

    /**
     * Resolves the current file SHA, or null when the file does not exist
     * and the write will therefore create it.
     */
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
        /** Maximum size of the written content (`DV-RTB-REQUEST-MAX-BYTES`). */
        const val MAX_CONTENT_BYTES: Int = 2 * 1024 * 1024

        /** Status that means the file does not exist yet. */
        const val HTTP_NOT_FOUND: Int = 404
    }
}
