package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

internal class ReadFile {
    suspend fun run(client: GitHubHttpClient, args: ReadFileArgs): ToolResult.FileContent =
        TODO("ReadFile: see spec 7 and 7.1 for the GitHub mapping")
}
