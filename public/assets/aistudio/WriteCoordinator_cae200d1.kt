package com.inscopexlabs.gitana.gitbridge.core.application

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes operations that mutate the same repository-relative target.
 *
 * The key is the exact repository and path supplied by the already-validated
 * request pipeline. This class deliberately does not make repository-name or
 * path-canonicalization decisions; those belong to the A6 and B2 contracts.
 */
interface WriteCoordinator {
    /**
     * Executes [block] while holding the exclusive lock for [repo] and [path].
     *
     * Calls with the same key execute one at a time. Calls with different keys
     * may execute concurrently.
     *
     * @throws IllegalArgumentException if [repo] or [path] is blank
     */
    suspend fun <T> serialize(
        repo: String,
        path: String,
        block: suspend () -> T,
    ): T
}

/**
 * In-memory keyed-mutex implementation of [WriteCoordinator].
 *
 * Lock entries are reference-counted and removed when the last holder/waiter
 * leaves. This avoids an unbounded lock-map growth as new paths are written.
 * Use one instance per BridgeGraph so all transports share the same lock set.
 */
class InMemoryWriteCoordinator : WriteCoordinator {

    private data class LockKey(
        val repository: String,
        val path: String,
    )

    private class LockEntry {
        val mutex = Mutex()
        var references: Int = 0
    }

    private val registryMonitor = Any()
    private val entries = HashMap<LockKey, LockEntry>()

    override suspend fun <T> serialize(
        repo: String,
        path: String,
        block: suspend () -> T,
    ): T {
        require(repo.isNotBlank()) { "Repository must not be blank" }
        require(path.isNotBlank()) { "Write target path must not be blank" }

        val key = LockKey(repository = repo, path = path)
        val entry = synchronized(registryMonitor) {
            val selected = entries.getOrPut(key) { LockEntry() }
            selected.references += 1
            selected
        }

        var acquired = false
        try {
            entry.mutex.lock()
            acquired = true
            return block()
        } finally {
            if (acquired) {
                entry.mutex.unlock()
            }

            synchronized(registryMonitor) {
                entry.references -= 1
                check(entry.references >= 0) {
                    "WriteCoordinator lock reference count became negative"
                }
                if (entry.references == 0 && !entry.mutex.isLocked &&
                    entries[key] === entry
                ) {
                    entries.remove(key)
                }
            }
        }
    }
}
