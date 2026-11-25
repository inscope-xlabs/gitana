package com.inscopexlabs.gitana.gitbridge.core.application

/** Serializes conflicting writes to the same repository/path (spec 7.1, 10). */
class WriteCoordinator {
    suspend fun <T> serialize(repo: String, path: String, block: suspend () -> T): T =
        TODO("per-repo/path serialization")
}
