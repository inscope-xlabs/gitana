// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/application/RequestLifecycle.kt
package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.RequestState

/**
 * Standard 1 – Stage 1: Happy‑path logic.
 * Standard 1 – Stage 2: State is wired through a sealed type.
 * Standard 1 – Stage 3: All edge cases (invalid transitions, terminal states) are handled.
 */
object RequestLifecycle {

    /** Returns true if [next] is a legal successor of [current]. */
    fun canTransition(current: RequestState, next: RequestState): Boolean = when (current) {
        RequestState.RECEIVED   -> next in setOf(RequestState.VALIDATED, RequestState.REJECTED)
        RequestState.VALIDATED  -> next in setOf(RequestState.POLICY_CHECKED, RequestState.REJECTED)
        RequestState.POLICY_CHECKED -> next in setOf(RequestState.AWAITING_APPROVAL, RequestState.EXECUTING, RequestState.REJECTED)
        RequestState.AWAITING_APPROVAL -> next in setOf(RequestState.EXECUTING, RequestState.REJECTED)
        RequestState.EXECUTING  -> next in setOf(RequestState.SUCCEEDED, RequestState.FAILED, RequestState.OUTCOME_UNKNOWN, RequestState.MANUAL_REVIEW)
        RequestState.SUCCEEDED, RequestState.FAILED, RequestState.REJECTED,
        RequestState.OUTCOME_UNKNOWN, RequestState.MANUAL_REVIEW, RequestState.CANCELLED -> false // terminal
    }

    /** Throws [IllegalStateException] if the transition is not allowed. */
    fun requireTransition(current: RequestState, next: RequestState) {
        check(canTransition(current, next)) {
            "Invalid transition from $current to $next"
        }
    }

    /** Returns true when the state is terminal and no further changes are allowed. */
    fun isTerminal(state: RequestState): Boolean = when (state) {
        RequestState.SUCCEEDED, RequestState.FAILED, RequestState.REJECTED,
        RequestState.OUTCOME_UNKNOWN, RequestState.MANUAL_REVIEW, RequestState.CANCELLED -> true
        else -> false
    }
}