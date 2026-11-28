package com.inscopexlabs.gitana.gitbridge.local.routes

import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.core.application.BridgeService
import com.inscopexlabs.gitana.gitbridge.local.mapping.LocalEnvelopeMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

/**
 * GET  /api/v1/requests/{request_id}
 * POST /api/v1/requests/{request_id}/cancel  (A3: cancel route kept)
 */
class RequestStatusRoute(
    private val bridge: BridgeService,
    private val mapper: LocalEnvelopeMapper = LocalEnvelopeMapper(),
) {
    suspend fun status(call: ApplicationCall, requestId: String) {
        val state = bridge.getStatus(requestId)
        if (state == null) {
            call.respondText(
                mapper.validationErrorJson(
                    BridgeError(
                        ErrorCode.NOT_FOUND,
                        ErrorCategory.PROVIDER,
                        404,
                        false,
                        ExecutionState.FAILED,
                        "Unknown request_id.",
                    ),
                ),
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
            return
        }
        call.respondText(
            mapper.toStatusJson(requestId, state),
            ContentType.Application.Json,
            HttpStatusCode.OK,
        )
    }

    suspend fun cancel(call: ApplicationCall, requestId: String) {
        when (val outcome = bridge.cancel(requestId)) {
            is BridgeService.CancelResult.Ok -> {
                call.respondText(
                    mapper.toStatusJson(requestId, outcome.state),
                    ContentType.Application.Json,
                    HttpStatusCode.OK,
                )
            }
            is BridgeService.CancelResult.NotFound -> {
                call.respondText(
                    mapper.validationErrorJson(
                        BridgeError(
                            ErrorCode.NOT_FOUND,
                            ErrorCategory.PROVIDER,
                            404,
                            false,
                            ExecutionState.FAILED,
                            "Unknown request_id.",
                        ),
                    ),
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound,
                )
            }
            is BridgeService.CancelResult.Conflict -> {
                call.respondText(
                    mapper.validationErrorJson(
                        BridgeError(
                            ErrorCode.CONFLICT,
                            ErrorCategory.PROVIDER,
                            409,
                            false,
                            ExecutionState.FAILED,
                            "Request is not cancellable in state ${outcome.state.name}.",
                        ),
                    ),
                    ContentType.Application.Json,
                    HttpStatusCode.Conflict,
                )
            }
        }
    }
}
