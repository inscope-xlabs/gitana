package com.inscopexlabs.gitana.gitbridge.contracts.authz

import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode

/** Authorization Contract decision (spec 4.3, 6.2). */
sealed interface PolicyDecision {
    data object Allow : PolicyDecision

    data class RequireApproval(val reason: String) : PolicyDecision

    data class Deny(val code: ErrorCode, val reason: String) : PolicyDecision
}
