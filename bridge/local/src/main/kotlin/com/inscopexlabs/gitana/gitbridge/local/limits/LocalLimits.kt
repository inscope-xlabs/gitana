package com.inscopexlabs.gitana.gitbridge.local.limits

/** Local interface limits. Values are the Appendix D starting defaults. */
object LocalLimits {
    /** `DV-RTB-REQUEST-MAX-BYTES`. */
    const val MAX_REQUEST_BYTES: Long = 2_097_152

    /** `DV-RTB-TIMEOUT-LOCAL-S`. */
    const val REQUEST_TIMEOUT_SECONDS: Int = 60
}
