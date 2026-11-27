package com.inscopexlabs.gitana.gitbridge.approval

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalResult
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates in-process approval requests.
 *
 * Standard 1, Stage 1: uses an in-memory pending-request map. Durable storage,
 * lifecycle recovery, timeout handling and user-visible error handling are later stages.
 */
class ApprovalCoordinator : ApprovalGate {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<ApprovalResult>>()

    /**
     * Suspends until [approve] or [deny] completes the decision for this request.
     */
    override suspend fun requestApproval(
        request: RequestEnvelope,
        reason: String,
    ): ApprovalResult {
        val decision = CompletableDeferred<ApprovalResult>()
        pending[request.requestId] = decision
        return try {
            decision.await()
        } finally {
            pending.remove(request.requestId, decision)
        }
    }

    /** Completes a pending request as approved. */
    fun approve(requestId: String): Boolean =
        pending[requestId]?.complete(ApprovalResult.APPROVED) ?: false

    /** Completes a pending request as denied. */
    fun deny(requestId: String): Boolean =
        pending[requestId]?.complete(ApprovalResult.DENIED) ?: false

    /** Returns whether a request is currently waiting for a decision. */
    fun isPending(requestId: String): Boolean = pending.containsKey(requestId)
}
