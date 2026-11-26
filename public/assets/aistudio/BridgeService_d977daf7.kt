// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/application/BridgeService.kt
package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Standard 1 – Stage 1: Happy‑path submit/status implemented.
 * Standard 1 – Stage 2: Real state stored in an in‑memory map (placeholder for
 *                       RequestStorePort, B11); state exposed through a sealed type.
 * Standard 1 – Stage 3: All failures (invalid request, pipeline rejection,
 *                       unexpected exception) are caught and returned as a
 *                       ToolResult.Error – the service never crashes.
 */
class BridgeService(
    private val pipeline: RequestPipeline,
) {

    // In‑memory store – will be replaced by RequestStorePort in B11.
    private val store = ConcurrentHashMap<String, RequestRecord>()
    private val mutex = Mutex()

    /**
     * Submits a new [ToolRequest] and returns the terminal [ToolResult].
     * The method is safe to call from any coroutine context.
     */
    suspend fun submit(request: ToolRequest): ToolResult {
        // 1. Basic pre‑check (Stage 3: fail fast on missing ID)
        if (request.requestId.isBlank()) {
            return ToolResult.Error(
                code = ErrorCode.INVALID_REQUEST,
                message = "requestId must not be blank"
            )
        }

        // 2. Store the request in RECEIVED state
        val initialRecord = RequestRecord(
            requestId = request.requestId,
            state = RequestState.RECEIVED,
            createdAt = java.time.Instant.now(),
        )
        mutex.withLock { store[request.requestId] = initialRecord }

        // 3. Run the pipeline – every step is wrapped so that any exception
        //    becomes a user‑visible error (Stage 3).
        val pipelineResult = try {
            pipeline.execute(request)
        } catch (e: Exception) {
            PipelineResult.Fail(
                "Pipeline crashed: ${e.message ?: "unknown error"}",
                ErrorCode.INTERNAL_ERROR
            )
        }

        // 4. Map the pipeline result to a terminal state and persist it
        val (finalState, toolResult) = when (pipelineResult) {
            is PipelineResult.Success -> RequestState.SUCCEEDED to pipelineResult.result
            is PipelineResult.Reject  -> RequestState.REJECTED  to ToolResult.Error(
                code = pipelineResult.errorCode,
                message = pipelineResult.reason
            )
            is PipelineResult.Fail    -> RequestState.FAILED    to ToolResult.Error(
                code = pipelineResult.errorCode,
                message = pipelineResult.reason
            )
            is PipelineResult.Continue -> {
                // Should never happen – pipeline always ends in Success, Reject or Fail.
                RequestState.FAILED to ToolResult.Error(
                    code = ErrorCode.INTERNAL_ERROR,
                    message = "Pipeline ended in an intermediate state"
                )
            }
        }

        mutex.withLock {
            store[request.requestId] = initialRecord.copy(
                state = finalState,
                completedAt = java.time.Instant.now(),
            )
        }

        return toolResult
    }

    /**
     * Returns the current [RequestState] for [requestId], or null if unknown.
     * Safe to call from any thread.
     */
    suspend fun getStatus(requestId: String): RequestState? = mutex.withLock {
        store[requestId]?.state
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