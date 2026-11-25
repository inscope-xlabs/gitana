package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation

class ConfirmationRules(private val policy: ConfirmationPolicy) {
    fun requiresApproval(operation: ToolOperation): Boolean = when (operation) {
        ToolOperation.DELETE_FILE -> policy.requireForDelete
        ToolOperation.CREATE_PULL_REQUEST -> policy.requireForPullRequest
        ToolOperation.WRITE_FILE, ToolOperation.CREATE_BRANCH -> policy.requireForWrite
        ToolOperation.LIST_BRANCHES, ToolOperation.READ_FILE -> false
    }
}
