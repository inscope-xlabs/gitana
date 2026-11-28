package com.inscopexlabs.gitana.gitbridge.local.routes

import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.core.application.BridgeService
import com.inscopexlabs.gitana.gitbridge.local.mapping.LocalEnvelopeMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText

/**
 * POST /api/v1/tools/{operation}
 *
 * Path operation must equal the envelope's `operation`. Only the six canonical tools are routable.
 * Read ops → 200 + ResponseEnvelope; mutating ops → 202 + AcceptedResponse (poll status).
 */
class ToolRoute(
    private val bridge: BridgeService,
    private val mapper: LocalEnvelopeMapper = LocalEnvelopeMapper(),
) {
    suspend fun handle(call: ApplicationCall, pathOperation: String) {
        val operation = ToolOperation.fromWireName(pathOperation)
        if (operation == null) {
            call.respondText(
                """{"error":{"code":"NOT_FOUND","category":"PROVIDER","http_status":404,"retryable":false,"execution_state":"FAILED","message":"Unknown route."}}""",
                ContentType.Application.Json,
                HttpStatusCode.NotFound,
            )
            return
        }

        val body = call.receiveText()
        when (val parsed = mapper.parseRequest(pathOperation, body)) {
            is LocalEnvelopeMapper.ParseResult.Fail -> {
                call.respondText(
                    mapper.validationErrorJson(parsed.error),
                    ContentType.Application.Json,
                    HttpStatusCode.fromValue(parsed.error.httpStatus ?: 400),
                )
            }
            is LocalEnvelopeMapper.ParseResult.Ok -> {
                val response = bridge.submit(parsed.envelope)
                if (operation.mutating) {
                    // 202 Accepted — client polls GET /api/v1/requests/{id}
                    call.respondText(
                        mapper.toAcceptedJson(response.requestId, response.state),
                        ContentType.Application.Json,
                        HttpStatusCode.Accepted,
                    )
                } else {
                    call.respondText(
                        mapper.toResponseJson(response),
                        ContentType.Application.Json,
                        HttpStatusCode.OK,
                    )
                }
            }
        }
    }
}
