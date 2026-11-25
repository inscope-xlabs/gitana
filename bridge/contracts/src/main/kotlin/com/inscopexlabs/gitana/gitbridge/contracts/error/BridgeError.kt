package com.inscopexlabs.gitana.gitbridge.contracts.error

enum class ExecutionState { NOT_STARTED, EXECUTING, SUCCEEDED, FAILED, OUTCOME_UNKNOWN }

/** Error contract (spec 11.1). `message` must be sanitized; `correlationId` is never a credential. */
data class BridgeError(
    val code: ErrorCode,
    val category: ErrorCategory,
    val httpStatus: Int?,
    val retryable: Boolean,
    val executionState: ExecutionState,
    val message: String,
    val correlationId: String? = null,
)

class BridgeException(val error: BridgeError) : RuntimeException(error.message)
