package com.inscopexlabs.gitana.gitbridge.github.ops

import com.inscopexlabs.gitana.gitbridge.contracts.tool.DeleteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

internal class DeleteFile {
    suspend fun run(client: GitHubHttpClient, args: DeleteFileArgs): ToolResult.FileDeleted =
        TODO("DeleteFile: see spec 7 and 7.1 for the GitHub mapping")
}
