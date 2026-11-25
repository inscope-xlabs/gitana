package com.inscopexlabs.gitana.gitbridge.core.ports

import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState

/** Metadata only: no file content or request bodies (spec 8.3). */
data class RequestRecord(
    val requestId: String,
    val fingerprint: String,
    val state: RequestState,
    val updatedAtMillis: Long,
)

interface RequestStorePort {
    /** Returns false if a record with this request id already exists. */
    fun insert(record: RequestRecord): Boolean

    fun find(requestId: String): RequestRecord?

    fun updateState(requestId: String, state: RequestState, atMillis: Long)
}
