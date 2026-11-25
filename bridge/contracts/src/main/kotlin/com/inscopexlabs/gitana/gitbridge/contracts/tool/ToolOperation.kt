package com.inscopexlabs.gitana.gitbridge.contracts.tool

/** The canonical tool surface (spec 7). No operation accepts a raw endpoint, host or HTTP method. */
enum class ToolOperation(
    val wireName: String,
    val mutating: Boolean,
    val destructive: Boolean = false,
) {
    LIST_BRANCHES("list_branches", mutating = false),
    CREATE_BRANCH("create_branch", mutating = true),
    READ_FILE("read_file", mutating = false),
    WRITE_FILE("write_file", mutating = true),
    DELETE_FILE("delete_file", mutating = true, destructive = true),
    CREATE_PULL_REQUEST("create_pull_request", mutating = true);

    companion object {
        fun fromWireName(name: String): ToolOperation? = entries.firstOrNull { it.wireName == name }
    }
}
