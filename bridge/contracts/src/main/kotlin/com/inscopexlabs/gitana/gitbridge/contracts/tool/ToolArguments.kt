package com.inscopexlabs.gitana.gitbridge.contracts.tool

/** Typed arguments per operation. `repo` is "owner/name" and is resolved against the approved-repository record. */
sealed interface ToolArguments {
    val repo: String
}

data class ListBranchesArgs(
    override val repo: String,
    val page: Int? = null,
    val perPage: Int? = null,
) : ToolArguments

data class CreateBranchArgs(
    override val repo: String,
    val base: String,
    val newBranch: String,
) : ToolArguments

data class ReadFileArgs(
    override val repo: String,
    val path: String,
    val ref: String,
) : ToolArguments

data class WriteFileArgs(
    override val repo: String,
    val path: String,
    val content: String,
    val branch: String,
    val message: String,
) : ToolArguments {
    // File content must never reach logs (spec 10, 11).
    override fun toString(): String =
        "WriteFileArgs(repo=$repo, path=$path, branch=$branch, content=<redacted>)"
}

data class DeleteFileArgs(
    override val repo: String,
    val path: String,
    val branch: String,
    val message: String,
) : ToolArguments

data class CreatePullRequestArgs(
    override val repo: String,
    val head: String,
    val base: String,
    val title: String,
    val body: String? = null,
) : ToolArguments
