package com.inscopexlabs.gitana.gitbridge.core.policy

/** Per-repository policy (spec 7.2). Everything is denied unless explicitly enabled. */
data class RepositoryPolicy(
    val canRead: Boolean = false,
    val canWrite: Boolean = false,
    val writableBranches: Set<String> = emptySet(),
    val protectedBranches: Set<String> = emptySet(),
    val extraProtectedPaths: Set<String> = emptySet(),
)

/** Confirmation defaults follow spec section 14. */
data class ConfirmationPolicy(
    val requireForDelete: Boolean = true,
    val requireForPullRequest: Boolean = true,
    val requireForWrite: Boolean = false,
)

data class PolicyConfig(
    val repositories: Map<String, RepositoryPolicy> = emptyMap(),
    val confirmation: ConfirmationPolicy = ConfirmationPolicy(),
)
