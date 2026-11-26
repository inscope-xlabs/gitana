// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/ops/ListBranches.kt
package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.ListBranchesArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubFailure
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubJson
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubMethod
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubRequest
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubTransport
import com.inscopexlabs.gitana.gitbridge.github.client.UpstreamErrorMapper
import kotlinx.serialization.json.JsonObject

/**
 * Standard 1 – Stage 1: The branches of an approved repository are listed
 *                       through `GET /repos/{owner}/{repo}/branches`.
 * Standard 1 – Stage 2: The transport is injected; the operation keeps no
 *                       state between calls.
 * Standard 1 – Stage 3: Pagination is bounded by page count and by the
 *                       contract's maximum result size, an empty repository
 *                       yields an empty list rather than an error, and a
 *                       non-successful response is mapped to a typed error.
 *
 * Spec 7, 7.1, `tool-contract.v1.json` (`BranchList`).
 */
internal class ListBranches(private val transport: GitHubTransport) {

    /**
     * Lists the branches of `owner/name`.
     *
     * [ListBranchesArgs.page] selects the first page to read; the result
     * reports whether further pages exist.
     */
    suspend fun run(owner: String, name: String, args: ListBranchesArgs): ToolResult.BranchList {
        val perPage = (args.perPage ?: DEFAULT_PER_PAGE).coerceIn(MIN_PER_PAGE, MAX_PER_PAGE)
        val branches = LinkedHashSet<String>()
        var page = (args.page ?: FIRST_PAGE).coerceAtLeast(FIRST_PAGE)
        var lastPageWasFull = false
        var pagesFetched = 0

        while (pagesFetched < MAX_PAGES && branches.size < MAX_BRANCHES) {
            val response = transport.send(
                GitHubRequest(
                    method = GitHubMethod.GET,
                    path = "/repos/$owner/$name/branches",
                    query = mapOf(
                        "per_page" to perPage.toString(),
                        "page" to page.toString(),
                    ),
                ),
            )
            if (!response.isSuccessful) throw UpstreamErrorMapper.failure(response)

            val items = GitHubJson.parseArray(response.body)
                ?: throw GitHubFailure.malformed("branch list")
            pagesFetched++
            if (items.isEmpty()) {
                // An empty repository has no branches; that is a result, not
                // an error (spec 7.1).
                lastPageWasFull = false
                break
            }
            for (item in items) {
                val entry = item as? JsonObject ?: continue
                val branch = GitHubJson.string(entry, "name") ?: continue
                branches.add(branch)
            }
            lastPageWasFull = items.size >= perPage
            if (!lastPageWasFull) break
            page++
        }

        return ToolResult.BranchList(
            branches = branches.take(MAX_BRANCHES),
            hasMore = lastPageWasFull,
        )
    }

    private companion object {
        /** Default page size used when the caller does not choose one. */
        const val DEFAULT_PER_PAGE: Int = 100

        /** Smallest accepted page size (`tool-contract.v1.json`). */
        const val MIN_PER_PAGE: Int = 1

        /** Largest accepted page size (`tool-contract.v1.json`). */
        const val MAX_PER_PAGE: Int = 100

        /** First page index. */
        const val FIRST_PAGE: Int = 1

        /** Maximum number of pages read in one call. */
        const val MAX_PAGES: Int = 10

        /** Maximum number of branches reported (`tool-contract.v1.json`). */
        const val MAX_BRANCHES: Int = 100
    }
}
