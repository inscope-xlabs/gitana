package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreatePullRequestArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

internal class CreatePullRequest {
    suspend fun run(client: GitHubHttpClient, args: CreatePullRequestArgs): ToolResult.PullRequestCreated =
        TODO("CreatePullRequest: see spec 7 and 7.1 for the GitHub mapping")
}
