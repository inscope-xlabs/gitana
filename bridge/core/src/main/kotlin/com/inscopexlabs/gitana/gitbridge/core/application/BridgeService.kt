// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/application/BridgeService.kt
package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.ResponseEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.core.application.RequestPipeline.PipelineResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Standard 1 – Stage 1: Happy‑path submit/status implemented.
 * Standard 1 – Stage 2: Real state stored in an in‑memory map (placeholder for
 *                       RequestStorePort, B11); state exposed through a sealed type.
 * Standard 1 – Stage 3: All failures (invalid request, pipeline rejection,
 *                       unexpected exception) are caught and returned as a
 *                       ResponseEnvelope with BridgeError – the service never crashes.
 */
class BridgeService(
    private val pipeline: RequestPipeline,
) {

    // In‑memory store – will be replaced by RequestStorePort in B11.
    private val store = ConcurrentHashMap<String, RequestRecord>()
    private val mutex = Mutex()

    /**
     * Submits a new [RequestEnvelope] and returns the terminal [ResponseEnvelope].
     * The method is safe to call from any coroutine context.
     */
    suspend fun submit(request: RequestEnvelope): ResponseEnvelope {
        // 1. Basic pre‑check (Stage 3: fail fast on missing ID)
        if (request.requestId.isBlank()) {
            return ResponseEnvelope(
                requestId = request.requestId,
                state = RequestState.REJECTED,
                error = toBridgeError(
                    code = ErrorCode.VALIDATION_FAILED,
                    message = "requestId must not be blank",
                    executionState = ExecutionState.NOT_STARTED,
                ),
            )
        }

        // 2. Reject duplicate request IDs without overwriting active/existing records
        val initialRecord = RequestRecord(
            requestId = request.requestId,
            state = RequestState.RECEIVED,
            createdAt = java.time.Instant.now(),
        )
        val inserted = mutex.withLock {
            if (store.containsKey(request.requestId)) {
                false
            } else {
                store[request.requestId] = initialRecord
                true
            }
        }
        if (!inserted) {
            return ResponseEnvelope(
                requestId = request.requestId,
                state = RequestState.REJECTED,
                error = toBridgeError(
                    code = ErrorCode.CONFLICT,
                    message = "Duplicate requestId '${request.requestId}'",
                    executionState = ExecutionState.NOT_STARTED,
                ),
            )
        }

        // 3. Run the pipeline – every step is wrapped so that any exception
        //    becomes a user‑visible error (Stage 3).
        val pipelineResult = try {
            pipeline.execute(request) { nextState ->
                mutex.withLock {
                    store[request.requestId]?.let { current ->
                        if (!current.state.terminal && current.state.canTransitionTo(nextState)) {
                            store[request.requestId] = current.copy(state = nextState)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PipelineResult.Fail(
                "Pipeline crashed: ${e.message ?: "unknown error"}",
                ErrorCode.INTERNAL
            )
        }

        // 4. Map the pipeline result to a terminal state and persist it
        val (finalState, responseEnvelope) = when (pipelineResult) {
            is PipelineResult.Success -> RequestState.SUCCEEDED to ResponseEnvelope(
                requestId = request.requestId,
                state = RequestState.SUCCEEDED,
                result = pipelineResult.result,
            )
            is PipelineResult.Reject -> RequestState.REJECTED to ResponseEnvelope(
                requestId = request.requestId,
                state = RequestState.REJECTED,
                error = toBridgeError(
                    code = pipelineResult.errorCode,
                    message = pipelineResult.reason,
                    executionState = ExecutionState.NOT_STARTED,
                ),
            )
            is PipelineResult.Fail -> RequestState.FAILED to ResponseEnvelope(
                requestId = request.requestId,
                state = RequestState.FAILED,
                error = toBridgeError(
                    code = pipelineResult.errorCode,
                    message = pipelineResult.reason,
                    executionState = ExecutionState.FAILED,
                ),
            )
            is PipelineResult.Continue -> {
                // Should never happen – pipeline always ends in Success, Reject or Fail.
                RequestState.FAILED to ResponseEnvelope(
                    requestId = request.requestId,
                    state = RequestState.FAILED,
                    error = toBridgeError(
                        code = ErrorCode.INTERNAL,
                        message = "Pipeline ended in an intermediate state",
                        executionState = ExecutionState.FAILED,
                    ),
                )
            }
        }

        mutex.withLock {
            val current = store[request.requestId]
            if (current?.state != RequestState.CANCELLED) {
                store[request.requestId] = initialRecord.copy(
                    state = finalState,
                    completedAt = java.time.Instant.now(),
                )
            }
        }

        return responseEnvelope
    }

    /** Outcome of a cancellation request (A3). */
    sealed interface CancelResult {
        data class Ok(val state: RequestState) : CancelResult
        data object NotFound : CancelResult
        data class Conflict(val state: RequestState) : CancelResult
    }

    /**
     * Cancels [requestId] when in a cancellable state (pre-dispatch or [RequestState.MANUAL_REVIEW]).
     * Already-cancelled requests succeed idempotently.
     */
    suspend fun cancel(requestId: String): CancelResult = mutex.withLock {
        if (requestId.isBlank()) return@withLock CancelResult.NotFound
        val current = store[requestId] ?: return@withLock CancelResult.NotFound
        if (current.state == RequestState.CANCELLED) {
            return@withLock CancelResult.Ok(RequestState.CANCELLED)
        }
        if (current.state.canTransitionTo(RequestState.CANCELLED)) {
            store[requestId] = current.copy(
                state = RequestState.CANCELLED,
                completedAt = java.time.Instant.now(),
            )
            return@withLock CancelResult.Ok(RequestState.CANCELLED)
        }
        CancelResult.Conflict(current.state)
    }

    /**
     * Returns the current [RequestState] for [requestId], or null if unknown.
     * Safe to call from any thread.
     */
    suspend fun getStatus(requestId: String): RequestState? = mutex.withLock {
        store[requestId]?.state
    }

    internal suspend fun seedStateForTest(requestId: String, state: RequestState) {
        mutex.withLock {
            val now = java.time.Instant.now()
            store[requestId] = RequestRecord(
                requestId = requestId,
                state = state,
                createdAt = now,
                completedAt = if (state.terminal) now else null,
            )
        }
    }

    private fun toBridgeError(
        code: ErrorCode,
        message: String,
        executionState: ExecutionState,
    ): BridgeError = when (code) {
        ErrorCode.VALIDATION_FAILED -> BridgeError(code, ErrorCategory.VALIDATION, 400, false, executionState, message)
        ErrorCode.POLICY_DENIED -> BridgeError(code, ErrorCategory.POLICY, 403, false, executionState, message)
        ErrorCode.APPROVAL_DECLINED -> BridgeError(code, ErrorCategory.AUTHORIZATION, 403, false, executionState, message)
        ErrorCode.EXPIRED -> BridgeError(code, ErrorCategory.VALIDATION, 400, false, executionState, message)
        ErrorCode.CONFLICT -> BridgeError(code, ErrorCategory.VALIDATION, 409, false, executionState, message)
        ErrorCode.UPSTREAM_UNAVAILABLE -> BridgeError(code, ErrorCategory.PROVIDER, 502, true, executionState, message)
        ErrorCode.RATE_LIMITED -> BridgeError(code, ErrorCategory.PROVIDER, 429, true, executionState, message)
        ErrorCode.TIMEOUT -> BridgeError(code, ErrorCategory.TRANSPORT, 504, true, executionState, message)
        ErrorCode.OUTCOME_UNKNOWN -> BridgeError(code, ErrorCategory.PROVIDER, 502, false, ExecutionState.OUTCOME_UNKNOWN, message)
        else -> BridgeError(code, ErrorCategory.INTERNAL, 500, false, executionState, message)
    }
}

// ------------------------------------------------------------------
// Simple in‑memory record – placeholder for the real RequestStorePort (B11).
// ------------------------------------------------------------------
data class RequestRecord(
    val requestId: String,
    val state: RequestState,
    val createdAt: java.time.Instant,
    val completedAt: java.time.Instant? = null,
)
