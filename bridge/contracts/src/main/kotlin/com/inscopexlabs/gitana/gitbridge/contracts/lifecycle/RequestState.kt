package com.inscopexlabs.gitana.gitbridge.contracts.lifecycle

/**
 * Execution Lifecycle Contract (spec 8.3).
 *
 * Finalized per Phase A (A3): MANUAL_REVIEW and CANCELLED are first-class lifecycle states.
 */
enum class RequestState(val terminal: Boolean) {
    RECEIVED(false),
    AUTHORIZED(false),
    AWAITING_APPROVAL(false),
    EXECUTING(false),
    SUCCEEDED(true),
    FAILED(true),
    OUTCOME_UNKNOWN(false),
    RECONCILING(false),
    MANUAL_REVIEW(false),
    EXPIRED(true),
    REJECTED(true),
    CANCELLED(true);

    fun allowedNext(): Set<RequestState> = when (this) {
        RECEIVED -> setOf(AUTHORIZED, REJECTED, EXPIRED, CANCELLED)
        AUTHORIZED -> setOf(AWAITING_APPROVAL, EXECUTING, REJECTED, EXPIRED, CANCELLED)
        AWAITING_APPROVAL -> setOf(EXECUTING, REJECTED, EXPIRED, CANCELLED)
        EXECUTING -> setOf(SUCCEEDED, FAILED, OUTCOME_UNKNOWN)
        OUTCOME_UNKNOWN -> setOf(RECONCILING, MANUAL_REVIEW)
        RECONCILING -> setOf(SUCCEEDED, FAILED, MANUAL_REVIEW)
        MANUAL_REVIEW -> setOf(SUCCEEDED, FAILED, CANCELLED)
        SUCCEEDED, FAILED, EXPIRED, REJECTED, CANCELLED -> emptySet()
    }

    fun canTransitionTo(next: RequestState): Boolean = next in allowedNext()
}
