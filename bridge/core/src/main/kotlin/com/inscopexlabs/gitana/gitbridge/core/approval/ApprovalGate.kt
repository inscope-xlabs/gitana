package com.inscopexlabs.gitana.gitbridge.core.approval

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope

enum class ApprovalResult { APPROVED, DENIED, TIMED_OUT }

/** Suspends execution pending explicit user approval (AWAITING_APPROVAL). Implemented by the app. */
interface ApprovalGate {
    suspend fun requestApproval(request: RequestEnvelope, reason: String): ApprovalResult
}
