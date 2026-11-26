package com.inscopexlabs.gitana.gitbridge.local.limits

/** Local interface limits. Defaults are the Appendix D starting values and are injected. */
data class LocalLimits(
    /** `DV-RTB-REQUEST-MAX-BYTES`. */
    val maxRequestBytes: Long = 2_097_152,
    /** `DV-RTB-TIMEOUT-LOCAL-S`. */
    val requestTimeoutSeconds: Int = 60,
)
