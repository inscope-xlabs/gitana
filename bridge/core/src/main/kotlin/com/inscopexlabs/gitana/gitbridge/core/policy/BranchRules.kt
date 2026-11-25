package com.inscopexlabs.gitana.gitbridge.core.policy

/** Branch restrictions evaluated against normalized names (spec 6.3). */
class BranchRules {
    /** Scaffold fails closed: no branch is writable until implemented. */
    fun isWritable(policy: RepositoryPolicy, normalizedBranch: String): Boolean = false
}
