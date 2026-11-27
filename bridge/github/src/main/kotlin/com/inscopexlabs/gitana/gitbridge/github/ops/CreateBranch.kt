// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/ops/CreateBranch.kt
package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreateBranchArgs
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
 * Standard 1 – Stage 1: A branch is created through
 *                       `POST /repos/{owner}/{repo}/git/refs`.
 * Standard 1 – Stage 2: The transport is injected; the operation keeps no
 *                       state between calls.
 * Standard 1 – Stage 3: The base branch is resolved to a commit SHA before
 *                       the ref is created, so a missing base branch and an
 *                       already existing branch are reported distinctly, and
 *                       a non-successful response is mapped to a typed error.
 *
 * Spec 7, 7.1, RTB-T17, RTB-T18.
 */
internal class CreateBranch(private val transport: GitHubTransport) {

    /**
     * Creates `args.newBranch` in `owner/name` from the head of
     * `args.base`.
     */
    suspend fun run(owner: String, name: String, args: CreateBranchArgs): ToolResult.BranchCreated {
        // The base branch is resolved to a commit SHA first; GitHub refuses a
        // ref creation without one (spec 7.1, RTB-T17).
        val baseSha = resolveCommitSha(owner, name, args.base)

        val body = buildJsonObject {
            put("ref", "refs/heads/${args.newBranch}")
            put("sha", baseSha)
        }.toString()

        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.POST,
                path = "/repos/$owner/$name/git/refs",
                body = body,
                mutating = true,
            ),
        )
        if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

        val json = GitHubJson.parseObject(response.body)
            ?: throw GitHubFailure.malformed("reference")
        val sha = GitHubJson.obj(json, "object")?.let { GitHubJson.string(it, "sha") }
            ?: throw GitHubFailure.malformed("reference")
        val ref = GitHubJson.string(json, "ref") ?: "refs/heads/${args.newBranch}"

        return ToolResult.BranchCreated(ref = ref, sha = sha)
    }

    /** Resolves a branch, tag or commit reference to its commit SHA. */
    private suspend fun resolveCommitSha(owner: String, name: String, ref: String): String {
        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.GET,
                path = "/repos/$owner/$name/commits/${encodePathSegments(ref)}",
            ),
        )
        if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

        val json = GitHubJson.parseObject(response.body)
            ?: throw GitHubFailure.malformed("commit")
        return GitHubJson.string(json, "sha")
            ?: throw GitHubFailure.malformed("commit")
    }
}
