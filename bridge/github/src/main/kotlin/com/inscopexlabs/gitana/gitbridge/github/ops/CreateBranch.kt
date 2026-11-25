package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreateBranchArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

internal class CreateBranch {
    suspend fun run(client: GitHubHttpClient, args: CreateBranchArgs): ToolResult.BranchCreated =
        TODO("CreateBranch: see spec 7 and 7.1 for the GitHub mapping")
}
