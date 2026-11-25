package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import com.inscopexlabs.gitana.gitbridge.core.ports.RequestStorePort

/** Enforces the state machine from the Execution Lifecycle Contract. */
class RequestLifecycle(
    private val store: RequestStorePort,
    private val clock: ClockPort,
) {
    fun transition(requestId: String, next: RequestState) {
        val current = checkNotNull(store.find(requestId)) { "Unknown request" }
        check(current.state.canTransitionTo(next)) { "Illegal transition ${current.state} -> $next" }
        store.updateState(requestId, next, clock.nowMillis())
    }
}
