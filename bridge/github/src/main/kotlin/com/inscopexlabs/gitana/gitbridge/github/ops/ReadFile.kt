// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/ops/ReadFile.kt
package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
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

/**
 * Standard 1 – Stage 1: A file is read through
 *                       `GET /repos/{owner}/{repo}/contents/{path}`.
 * Standard 1 – Stage 2: The transport is injected; the operation keeps no
 *                       state between calls.
 * Standard 1 – Stage 3: A missing file, a directory, a binary payload and an
 *                       oversized payload each produce their documented
 *                       outcome, and a non-successful response is mapped to a
 *                       typed error.
 *
 * Spec 7, 7.1, RTB-T19, `DV-RTB-RESPONSE-MAX-BYTES`.
 */
internal class ReadFile(private val transport: GitHubTransport) {

    /** Reads `args.path` at `args.ref` from `owner/name`. */
    suspend fun run(owner: String, name: String, args: ReadFileArgs): ToolResult.FileContent {
        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.GET,
                path = "/repos/$owner/$name/contents/${encodePathSegments(args.path)}",
                query = mapOf("ref" to args.ref),
            ),
        )
        if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

        val json = GitHubJson.parseObject(response.body)
            ?: throw GitHubFailure.malformed("contents")

        // A directory or a submodule is not readable text content.
        val type = GitHubJson.string(json, "type")
        if (type != null && type != "file") {
            throw GitHubFailure.unsupportedContent(
                "The requested path is a '$type', not a readable file",
            )
        }

        val sha = GitHubJson.string(json, "sha")
            ?: throw GitHubFailure.malformed("contents")

        val encoding = GitHubJson.string(json, "encoding")
        val raw = GitHubJson.string(json, "content")
        if (encoding == null || encoding == "none" || raw == null) {
            // GitHub omits the payload for files it cannot inline.
            throw GitHubFailure.payloadTooLarge(
                "GitHub did not return the file content; the file is too large to inline",
            )
        }
        if (encoding != "base64") {
            throw GitHubFailure.unsupportedContent(
                "GitHub returned the file content in an unsupported encoding",
            )
        }

        val content = when (val decoded = Base64Content.decode(raw)) {
            is Base64Content.DecodeResult.Rejected ->
                throw GitHubFailure.unsupportedContent(decoded.reason)

            is Base64Content.DecodeResult.Decoded -> decoded.content
        }

        if (content.toByteArray(StandardCharsets.UTF_8).size > MAX_CONTENT_BYTES) {
            throw GitHubFailure.payloadTooLarge(
                "The file content exceeds the $MAX_CONTENT_BYTES byte ceiling",
            )
        }

        return ToolResult.FileContent(
            path = args.path,
            ref = args.ref,
            sha = sha,
            content = content,
        )
    }

    private companion object {
        /** Maximum size of decoded file content (`DV-RTB-RESPONSE-MAX-BYTES`). */
        const val MAX_CONTENT_BYTES: Int = 4 * 1024 * 1024
    }
}
