package com.inscopexlabs.gitana.gitbridge.core.policy

/** Approved-repository lookup. TODO: canonical owner/name normalization (case, aliases, renames). */
class RepositoryAllowList(private val config: PolicyConfig) {
    fun policyFor(repo: String): RepositoryPolicy? = config.repositories[repo]
}
