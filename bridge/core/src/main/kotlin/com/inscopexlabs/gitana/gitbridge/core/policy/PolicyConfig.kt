// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/PolicyConfig.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import java.time.Instant

/**
 * Standard 1 – Stage 1: `default()` returns a fully‑populated, fail‑closed
 *                       configuration usable by `PolicyEngine` (B5) with no
 *                       further setup.
 * Standard 1 – Stage 2: The aggregate is immutable; every policy subsystem
 *                       (allow‑list, protected paths, branch rules, resource
 *                       limits) is a typed field. Changes go through
 *                       [withChanges] which performs an optimistic‑concurrency
 *                       version bump for the persistence layer (B11).
 * Standard 1 – Stage 3: Invariant violations throw in `init`; load/save
 *                       failures are represented by [PolicyConfigLoadResult.Failed]
 *                       so that callers never have to catch unexpected exceptions.
 */
data class PolicyConfig(
    /** Monotonically increasing version. Used by `PolicyDataStore` (B11) for CAS writes. */
    val version: Long,

    /** Which repositories may be operated on. Fail‑closed by default. */
    val repositoryAllowList: RepositoryAllowList,

    /** Which paths require approval or are blocked outright. */
    val protectedPaths: ProtectedPaths,

    /** Per‑branch protection levels (also carries its own `defaultLevel`). */
    val branchRules: BranchRules,

    val confirmationRules: ConfirmationRules,

    /** Static resource caps enforced by `BridgeService` (B1) and B10. */
    val resourceLimits: ResourceLimits,

    /** Wall‑clock of the last successful save. */
    val updatedAt: Instant = Instant.EPOCH,
) {
    init {
        require(version >= 0) { "version must be >= 0, was $version" }
    }

    /**
     * Produces a new config with the given overrides applied and the version
     * incremented. Used by the operator‑facing write path in B11.
     *
     * The caller is expected to first `load()` the current config, produce the
     * updated instance via this method, then `save()` it. If another writer
     * bumped the version in the meantime, B11's save will reject the write —
     * no silent lost‑update.
     */
    fun withChanges(
        repositoryAllowList: RepositoryAllowList = this.repositoryAllowList,
        protectedPaths: ProtectedPaths = this.protectedPaths,
        branchRules: BranchRules = this.branchRules,
        confirmationRules: ConfirmationRules = this.confirmationRules,
        resourceLimits: ResourceLimits = this.resourceLimits,
        now: Instant = Instant.now(),
    ): PolicyConfig = copy(
        version = version + 1,
        repositoryAllowList = repositoryAllowList,
        protectedPaths = protectedPaths,
        branchRules = branchRules,
        confirmationRules = confirmationRules,
        resourceLimits = resourceLimits,
        updatedAt = now,
    )

    companion object {
        /**
         * Safe, working default that a fresh installation starts with.
         *
         *  - Empty allow‑list     → bridge cannot act until configured
         *  - Default protected paths (`.git/&#42;&#42;`, workflows, …)
         *  - Default branch rules (main/master/release/&#42;/hotfix/&#42; need approval)
         *  - Default resource limits
         *
         * This is what `PolicyDataStore.load()` returns when no persisted
         * config exists yet (B11 first‑run path).
         */
        fun default(now: Instant = Instant.now()): PolicyConfig = PolicyConfig(
            version = 0,
            repositoryAllowList = RepositoryAllowList.EMPTY,
            protectedPaths = ProtectedPaths(),
            branchRules = BranchRules(),
            confirmationRules = ConfirmationRules(),
            resourceLimits = ResourceLimits.DEFAULT,
            updatedAt = now,
        )
    }
}

/** Result of a load/save round‑trip through [PolicyConfigSource]. */
sealed class PolicyConfigLoadResult {

    data class Ok(val config: PolicyConfig) : PolicyConfigLoadResult()

    /**
     * Typed failure. [errorCode] uses the shared `ErrorCode` so it can be
     * lifted straight into a `ResponseEnvelope` by B1 if needed.
     */
    data class Failed(
        val reason: String,
        val errorCode: ErrorCode = ErrorCode.INTERNAL,
    ) : PolicyConfigLoadResult()
}

/**
 * Persistence contract for [PolicyConfig]. B4 declares the shape; B11 provides
 * the real `PolicyDataStore` implementation that backs it with
 * `storage:request/RequestRecordDao`.
 *
 * `save()` is expected to be **compare‑and‑swap** on `config.version`: if the
 * persisted version is not `config.version - 1`, save returns
 * `Failed(errorCode = POLICY_DENIED, reason = "version conflict")`.
 */
interface PolicyConfigSource {
    suspend fun load(): PolicyConfigLoadResult
    suspend fun save(config: PolicyConfig): PolicyConfigLoadResult
}

// ---------------------------------------------------------------------
// Placeholder – real implementation lives in
//   bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/limits/ResourceLimits.kt
// (task B10). It is declared here so that `PolicyConfig` is a complete
// aggregate from B4's perspective. When B10 lands, move this class to its
// own file and update the import in this file.
// ---------------------------------------------------------------------
data class ResourceLimits(
    val maxRequestBytes: Long = 64L * 1024,
    val maxFilesPerRequest: Int = 100,
    val maxConcurrentRequests: Int = 4,
    val maxUploadBytes: Long = 16L * 1024 * 1024,
    val maxDispatchDurationSeconds: Long = 120,
) {
    init {
        require(maxRequestBytes > 0)            { "maxRequestBytes must be > 0" }
        require(maxFilesPerRequest > 0)         { "maxFilesPerRequest must be > 0" }
        require(maxConcurrentRequests > 0)      { "maxConcurrentRequests must be > 0" }
        require(maxUploadBytes > 0)             { "maxUploadBytes must be > 0" }
        require(maxDispatchDurationSeconds > 0) { "maxDispatchDurationSeconds must be > 0" }
    }

    companion object {
        val DEFAULT = ResourceLimits()
    }
}
