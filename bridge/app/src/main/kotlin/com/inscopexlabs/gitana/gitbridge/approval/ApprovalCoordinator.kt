package com.inscopexlabs.gitana.gitbridge.approval

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalResult

/** Scaffold fails closed: every approval request is denied until the approval UI exists. */
class ApprovalCoordinator : ApprovalGate {
    override suspend fun requestApproval(request: RequestEnvelope, reason: String): ApprovalResult =
        ApprovalResult.DENIED
}
