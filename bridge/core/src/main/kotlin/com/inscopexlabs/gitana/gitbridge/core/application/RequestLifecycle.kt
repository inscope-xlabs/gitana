// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/application/RequestLifecycle.kt
package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState

/**
 * Standard 1 – Stage 1: Happy‑path logic.
 * Standard 1 – Stage 2: State is wired through a sealed type.
 * Standard 1 – Stage 3: All edge cases (invalid transitions, terminal states) are handled.
 */
object RequestLifecycle {

    /** Returns true if [next] is a legal successor of [current]. */
    fun canTransition(current: RequestState, next: RequestState): Boolean =
        current.canTransitionTo(next)

    /** Throws [IllegalStateException] if the transition is not allowed. */
    fun requireTransition(current: RequestState, next: RequestState) {
        check(canTransition(current, next)) {
            "Invalid transition from $current to $next"
        }
    }

    /** Returns true when the state is terminal and no further changes are allowed. */
    fun isTerminal(state: RequestState): Boolean = state.terminal
}
