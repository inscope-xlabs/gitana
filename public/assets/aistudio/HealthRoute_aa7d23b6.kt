package com.inscopexlabs.gitana.gitbridge.local.routes

import com.inscopexlabs.gitana.gitbridge.local.mapping.LocalEnvelopeMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

/** GET /api/v1/health — non-sensitive liveness (RTB-18). No auth (OpenAPI security: []). */
class HealthRoute(
    private val mapper: LocalEnvelopeMapper = LocalEnvelopeMapper(),
) {
    suspend fun handle(call: ApplicationCall) {
        call.respondText(mapper.toHealthJson(), ContentType.Application.Json, HttpStatusCode.OK)
    }
}