package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

internal class WriteFile {
    suspend fun run(client: GitHubHttpClient, args: WriteFileArgs): ToolResult.FileWritten =
        TODO("WriteFile: see spec 7 and 7.1 for the GitHub mapping")
}
