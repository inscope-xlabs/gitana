package com.inscopexlabs.gitana.gitbridge.contracts.tool

/** Canonical results, independent of transport. */
sealed interface ToolResult {
    data class BranchList(val branches: List<String>, val hasMore: Boolean) : ToolResult

    data class BranchCreated(val ref: String, val sha: String) : ToolResult

    data class FileContent(val path: String, val ref: String, val sha: String, val content: String) : ToolResult {
        override fun toString(): String = "FileContent(path=$path, ref=$ref, sha=$sha, content=<redacted>)"
    }

    data class FileWritten(val path: String, val commitSha: String) : ToolResult

    data class FileDeleted(val path: String, val commitSha: String) : ToolResult

    data class PullRequestCreated(val number: Int, val url: String) : ToolResult
}
