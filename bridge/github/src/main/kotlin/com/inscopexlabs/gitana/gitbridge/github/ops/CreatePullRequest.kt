// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/ops/CreatePullRequest.kt
package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreatePullRequestArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubFailure
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubJson
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubMethod
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubRequest
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubTransport
import com.inscopexlabs.gitana.gitbridge.github.client.UpstreamErrorMapper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Standard 1 – Stage 1: A pull request is created through
 *                       `POST /repos/{owner}/{repo}/pulls`.
 * Standard 1 – Stage 2: The transport is injected; the operation keeps no
 *                       state between calls.
 * Standard 1 – Stage 3: An equivalent open pull request is returned
 *                       idempotently instead of creating a duplicate, the
 *                       head syntax is validated, and a non-successful
 *                       response is mapped to a typed error.
 *
 * Spec 7, 7.1, RTB-D12, RTB-T18.
 */
internal class CreatePullRequest(private val transport: GitHubTransport) {

    /** Opens a pull request from `args.head` to `args.base`. */
    suspend fun run(
        owner: String,
        name: String,
        args: CreatePullRequestArgs,
    ): ToolResult.PullRequestCreated {
        val head = resolveHead(owner, args.head)

        // An equivalent open pull request makes this call idempotent
        // (RTB-D12).
        findExisting(owner, name, head, args.base)?.let { return it }

        val body = buildJsonObject {
            put("title", args.title)
            put("head", head)
            put("base", args.base)
            if (args.body != null) put("body", args.body)
        }.toString()

        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.POST,
                path = "/repos/$owner/$name/pulls",
                body = body,
                mutating = true,
            ),
        )
        if (!response.isSuccessful) {
            // A concurrent creation may have produced the pull request
            // between the check above and this call.
            if (response.status == HTTP_UNPROCESSABLE) {
                findExisting(owner, name, head, args.base)?.let { return it }
            }
            throw UpstreamErrorMapper.failure(response)
        }

        val json = GitHubJson.parseObject(response.body)
            ?: throw GitHubFailure.malformed("pull request")
        val number = GitHubJson.int(json, "number")
            ?: throw GitHubFailure.malformed("pull request")
        val url = GitHubJson.string(json, "html_url")
            ?: throw GitHubFailure.malformed("pull request")

        return ToolResult.PullRequestCreated(
            number = number,
            url = url,
            alreadyExisted = false,
            note = null,
        )
    }

    /**
     * Resolves the head syntax to the `owner:branch` form GitHub expects.
     *
     * A bare branch belongs to the approved repository owner; a qualified
     * `owner:branch` keeps its own owner.
     */
    private fun resolveHead(approvedOwner: String, head: String): String {
        val separator = head.indexOf(':')
        if (separator < 0) {
            if (!BRANCH_NAME.matches(head)) {
                throw GitHubFailure.validation("The pull request head is not a valid branch name")
            }
            return "$approvedOwner:$head"
        }
        val owner = head.substring(0, separator)
        val branch = head.substring(separator + 1)
        if (!OWNER_NAME.matches(owner) || !BRANCH_NAME.matches(branch)) {
            throw GitHubFailure.validation("The pull request head is not a valid 'owner:branch' reference")
        }
        return "$owner:$branch"
    }

    /**
     * Returns the equivalent open pull request for `(head, base)`, or null
     * when there is none.
     */
    private suspend fun findExisting(
        owner: String,
        name: String,
        head: String,
        base: String,
    ): ToolResult.PullRequestCreated? {
        val response = transport.send(
            GitHubRequest(
                method = GitHubMethod.GET,
                path = "/repos/$owner/$name/pulls",
                query = mapOf(
                    "state" to "open",
                    "head" to head,
                    "base" to base,
                ),
            ),
        )
        if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

        val items = GitHubJson.parseArray(response.body)
            ?: throw GitHubFailure.malformed("pull request list")
        val entry = items.firstOrNull() as? JsonObject ?: return null
        val number = GitHubJson.int(entry, "number") ?: return null
        val url = GitHubJson.string(entry, "html_url") ?: return null

        return ToolResult.PullRequestCreated(
            number = number,
            url = url,
            alreadyExisted = true,
            note = "An equivalent open pull request already existed",
        )
    }

    private companion object {
        /** Status GitHub uses when the pull request cannot be created. */
        const val HTTP_UNPROCESSABLE: Int = 422

        /** Repository owner pattern (`tool-contract.v1.json`). */
        val OWNER_NAME: Regex = Regex("^[A-Za-z0-9][A-Za-z0-9-]{0,38}$")

        /** Branch name pattern (`tool-contract.v1.json`, `BranchName`). */
        val BRANCH_NAME: Regex = Regex(
            "^(?!/)(?!.*//)(?!.*\\.\\.)(?!.*@\\{)(?!.*[\\x00-\\x20\\x7f~^:?*\\[\\\\])" +
                "(?!.*(?:^|/)\\.)(?!.*\\.lock(?:/|$))(?!.*[/.]$)(?!@$).+$",
        )
    }
}
