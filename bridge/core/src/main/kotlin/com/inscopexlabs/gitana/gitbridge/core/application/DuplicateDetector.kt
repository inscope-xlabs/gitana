package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.tool.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale

/** RTB-45 duplicate detection. Call after B2 path normalization; pass its canonical path. */
class DuplicateDetector(
    private val store: DuplicateRecordStore,
    private val retentionWindowMillis: Long,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    init { require(retentionWindowMillis > 0) { "Duplicate retention window must be positive" } }

    /** Hashes operation + typed canonical arguments; file content is nested-hashed, never returned. */
    fun fingerprint(request: RequestEnvelope, normalizedPath: String? = null): String {
        val fields = CanonicalFields()
        fields.add("operation", request.operation.wireName)
        fields.add("repo", request.arguments.repo.lowercase(Locale.ROOT)) // RTB-D13
        when (val a = request.arguments) {
            is ListBranchesArgs -> {
                require(request.operation == ToolOperation.LIST_BRANCHES) { "Operation/arguments mismatch" }
                fields.add("page", a.page?.toString()); fields.add("per_page", a.perPage?.toString())
            }
            is CreateBranchArgs -> {
                require(request.operation == ToolOperation.CREATE_BRANCH) { "Operation/arguments mismatch" }
                fields.add("base", a.base); fields.add("new_branch", a.newBranch)
            }
            is ReadFileArgs -> {
                require(request.operation == ToolOperation.READ_FILE) { "Operation/arguments mismatch" }
                fields.add("path", normalizedPath ?: a.path); fields.add("ref", a.ref)
            }
            is WriteFileArgs -> {
                require(request.operation == ToolOperation.WRITE_FILE) { "Operation/arguments mismatch" }
                fields.add("path", normalizedPath ?: a.path); fields.add("content_sha256", sha256(a.content))
                fields.add("branch", a.branch); fields.add("message", a.message)
            }
            is DeleteFileArgs -> {
                require(request.operation == ToolOperation.DELETE_FILE) { "Operation/arguments mismatch" }
                fields.add("path", normalizedPath ?: a.path); fields.add("branch", a.branch); fields.add("message", a.message)
            }
            is CreatePullRequestArgs -> {
                require(request.operation == ToolOperation.CREATE_PULL_REQUEST) { "Operation/arguments mismatch" }
                fields.add("head", a.head); fields.add("base", a.base); fields.add("title", a.title); fields.add("body", a.body)
            }
        }
        return sha256(fields.value())
    }

    fun check(request: RequestEnvelope, normalizedPath: String? = null): DuplicateDecision {
        require(request.requestId.isNotBlank()) { "request_id must not be blank" }
        val now = clockMillis()
        val candidate = DuplicateRecord(request.requestId, fingerprint(request, normalizedPath), now, DuplicateStatus.IN_PROGRESS, null)
        return when (val claim = store.claim(candidate, now, retentionWindowMillis)) {
            DuplicateClaim.Claimed -> DuplicateDecision.NewRequest(candidate.fingerprint)
            DuplicateClaim.ExistingInProgress -> DuplicateDecision.InProgress(candidate.fingerprint)
            is DuplicateClaim.ExistingSucceeded -> DuplicateDecision.Replay(candidate.fingerprint, claim.result)
            DuplicateClaim.RequestIdConflict -> DuplicateDecision.RequestIdConflict
            DuplicateClaim.CapacityExceeded -> DuplicateDecision.CapacityExceeded
        }
    }

    fun recordSuccess(requestId: String, fingerprint: String, result: ToolResult): Boolean =
        store.complete(requestId, fingerprint, result, clockMillis(), retentionWindowMillis)

    /** Only for a definitive pre-dispatch failure. Never release OUTCOME_UNKNOWN. */
    fun releaseBeforeDispatch(requestId: String, fingerprint: String): Boolean =
        store.releaseBeforeDispatch(requestId, fingerprint)
}

sealed interface DuplicateDecision {
    data class NewRequest(val fingerprint: String) : DuplicateDecision
    data class InProgress(val fingerprint: String) : DuplicateDecision
    data class Replay(val fingerprint: String, val result: ToolResult) : DuplicateDecision
    data object RequestIdConflict : DuplicateDecision
    data object CapacityExceeded : DuplicateDecision
}

enum class DuplicateStatus { IN_PROGRESS, SUCCEEDED }

/** Result is process-memory-only; do not persist this object or log its result. */
data class DuplicateRecord(
    val requestId: String,
    val fingerprint: String,
    val createdAtMillis: Long,
    val status: DuplicateStatus,
    val result: ToolResult?,
) {
    override fun toString(): String =
        "DuplicateRecord(requestId=$requestId, fingerprint=$fingerprint, createdAtMillis=$createdAtMillis, status=$status, result=<redacted>)"
}

sealed interface DuplicateClaim {
    data object Claimed : DuplicateClaim
    data object ExistingInProgress : DuplicateClaim
    data class ExistingSucceeded(val result: ToolResult) : DuplicateClaim
    data object RequestIdConflict : DuplicateClaim
    data object CapacityExceeded : DuplicateClaim
}

/** Atomic repository boundary. Durable implementations may persist metadata, never result content. */
interface DuplicateRecordStore {
    fun claim(candidate: DuplicateRecord, nowMillis: Long, retentionWindowMillis: Long): DuplicateClaim
    fun complete(requestId: String, fingerprint: String, result: ToolResult, nowMillis: Long, retentionWindowMillis: Long): Boolean
    fun releaseBeforeDispatch(requestId: String, fingerprint: String): Boolean
}

/** Bounded process-local implementation. B11 must provide durable metadata separately. */
class InMemoryDuplicateRecordStore(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) : DuplicateRecordStore {
    private val records = LinkedHashMap<String, DuplicateRecord>()
    init { require(maxEntries > 0) { "maxEntries must be positive" } }

    @Synchronized override fun claim(candidate: DuplicateRecord, nowMillis: Long, retentionWindowMillis: Long): DuplicateClaim {
        prune(nowMillis, retentionWindowMillis)
        val old = records[candidate.requestId]
        if (old != null) {
            if (old.fingerprint != candidate.fingerprint) return DuplicateClaim.RequestIdConflict
            return when (old.status) {
                DuplicateStatus.IN_PROGRESS -> DuplicateClaim.ExistingInProgress
                DuplicateStatus.SUCCEEDED -> old.result?.let(DuplicateClaim::ExistingSucceeded) ?: DuplicateClaim.ExistingInProgress
            }
        }
        if (records.size >= maxEntries) return DuplicateClaim.CapacityExceeded
        records[candidate.requestId] = candidate
        return DuplicateClaim.Claimed
    }

    @Synchronized override fun complete(requestId: String, fingerprint: String, result: ToolResult, nowMillis: Long, retentionWindowMillis: Long): Boolean {
        prune(nowMillis, retentionWindowMillis)
        val old = records[requestId] ?: return false
        if (old.fingerprint != fingerprint || old.status != DuplicateStatus.IN_PROGRESS) return false
        records[requestId] = old.copy(status = DuplicateStatus.SUCCEEDED, result = result)
        return true
    }

    @Synchronized override fun releaseBeforeDispatch(requestId: String, fingerprint: String): Boolean {
        val old = records[requestId] ?: return false
        if (old.fingerprint != fingerprint || old.status != DuplicateStatus.IN_PROGRESS) return false
        records.remove(requestId)
        return true
    }

    private fun prune(now: Long, window: Long) {
        val iterator = records.entries.iterator()
        while (iterator.hasNext()) {
            val record = iterator.next().value
            // Clock rollback retains entries (fail closed).
            if (now >= record.createdAtMillis && now - record.createdAtMillis >= window) iterator.remove()
        }
    }

    companion object { const val DEFAULT_MAX_ENTRIES = 1_024 }
}

/** Length-prefixing makes the canonical input unambiguous, including nullable fields. */
private class CanonicalFields {
    private val value = StringBuilder()
    fun add(name: String, field: String?) {
        value.append(name.length).append(':').append(name)
        if (field == null) value.append("-1:") else {
            val length = field.toByteArray(StandardCharsets.UTF_8).size
            value.append(length).append(':').append(field)
        }
    }
    fun value(): String = value.toString()
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
