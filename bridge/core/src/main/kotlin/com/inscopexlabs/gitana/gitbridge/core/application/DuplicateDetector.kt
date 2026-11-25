package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope

/** Duplicate suppression by request_id plus operation fingerprint (spec 8.4). */
class DuplicateDetector {
    /** TODO: stable hash over operation and canonical arguments, never over credentials. */
    fun fingerprint(request: RequestEnvelope): String = TODO("operation fingerprint")
}
