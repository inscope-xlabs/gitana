package com.inscopexlabs.gitana.gitbridge.contracts.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestStateTest {
    @Test
    fun terminalStatesHaveNoTransitions() {
        RequestState.entries.filter { it.terminal }.forEach { assertTrue(it.allowedNext().isEmpty()) }
    }

    @Test
    fun executingCannotGoBackToAuthorized() {
        assertFalse(RequestState.EXECUTING.canTransitionTo(RequestState.AUTHORIZED))
    }

    @Test
    fun outcomeUnknownMustReconcileOrEscalate() {
        assertEquals(
            setOf(RequestState.RECONCILING, RequestState.MANUAL_REVIEW),
            RequestState.OUTCOME_UNKNOWN.allowedNext(),
        )
    }
}
