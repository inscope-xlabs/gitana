package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.core.ports.RequestRecord

/** Resolves OUTCOME_UNKNOWN by safe read-back, never by blind replay (spec 8.4). */
class OutcomeReconciler {
    suspend fun reconcile(record: RequestRecord): RequestState = TODO("read-back reconciliation")
}
