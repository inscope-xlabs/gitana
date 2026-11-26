package com.inscopexlabs.gitana.gitbridge.contracts.error

/** Stable error codes (spec 11.1), mirrored in schemas/error.v1.json. */
enum class ErrorCode {
    AUTH_FAILED,
    FORBIDDEN,
    POLICY_DENIED,
    APPROVAL_DECLINED,
    VALIDATION_FAILED,
    UNSUPPORTED_VERSION,
    EXPIRED,
    REPLAY_DETECTED,
    NOT_FOUND,
    CONFLICT,
    UNSUPPORTED_CONTENT,
    PAYLOAD_TOO_LARGE,
    RATE_LIMITED,
    UPSTREAM_UNAVAILABLE,
    TIMEOUT,
    OUTCOME_UNKNOWN,
    NOT_IMPLEMENTED,
    INTERNAL,
}
