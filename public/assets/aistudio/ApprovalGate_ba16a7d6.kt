package com.inscopexlabs.gitana.gitbridge.core.approval

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope

/** Result returned after the user responds to an approval request. */
enum class ApprovalResult {
    APPROVED,
    DENIED,
    TIMED_OUT,
}

/**
 * Suspends a request until an approval decision is received.
 *
 * The implementation belongs to the Android app; this interface remains in core so
 * the core module does not depend on Android UI or notification APIs.
 */
interface ApprovalGate {
    /**
     * Requests approval for [request] and suspends until the user responds.
     *
     * @param request the request awaiting approval
     * @param reason the policy explanation shown to the user
     * @return the user's approval decision
     */
    suspend fun requestApproval(
        request: RequestEnvelope,
        reason: String,
    ): ApprovalResult
}
