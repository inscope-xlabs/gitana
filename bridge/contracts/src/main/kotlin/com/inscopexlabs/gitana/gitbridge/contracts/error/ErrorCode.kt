package com.inscopexlabs.gitana.gitbridge.contracts.error

/** Stable error codes (spec 11.1). The full catalog is an open task; this is the initial set. */
enum class ErrorCode {
    AUTH_FAILED,
    POLICY_DENIED,
    VALIDATION_FAILED,
    EXPIRED,
    NOT_FOUND,
    RATE_LIMITED,
    PAYLOAD_TOO_LARGE,
    CONFLICT,
    UPSTREAM_UNAVAILABLE,
    OUTCOME_UNKNOWN,
    NOT_IMPLEMENTED,
    INTERNAL,
}
