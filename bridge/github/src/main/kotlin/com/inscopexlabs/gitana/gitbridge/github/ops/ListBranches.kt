package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.ListBranchesArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

internal class ListBranches {
    suspend fun run(client: GitHubHttpClient, args: ListBranchesArgs): ToolResult.BranchList =
        TODO("ListBranches: see spec 7 and 7.1 for the GitHub mapping")
}
