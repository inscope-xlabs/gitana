package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.local.limits.LocalLimits
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.application.serverConfig
import io.ktor.server.response.respondText

/** Ktor server bound to loopback only (spec 5.1). No routes exist until D3, so requests get 404. */
class LocalServer {
    private val server = embeddedServer(
        factory = CIO,
        rootConfig = serverConfig(applicationEnvironment()) {
            module {
                val guard = HostHeaderGuard()
                intercept(ApplicationCallPipeline.Plugins) {
                    val host = call.request.headers[HttpHeaders.Host].orEmpty()
                    val origin = call.request.headers[HttpHeaders.Origin]
                    val status = if (guard.isPermitted(host, origin)) {
                        HttpStatusCode.NotFound
                    } else {
                        HttpStatusCode.Forbidden
                    }
                    call.respondText("", status = status)
                    finish()
                }
            }
        },
    ) {
        connectionIdleTimeoutSeconds = LocalLimits.REQUEST_TIMEOUT_SECONDS
        LoopbackBinding.ALLOWED_HOSTS.forEach { address ->
            connector {
                host = LoopbackBinding.requireLoopback(address)
                port = LoopbackBinding.DEFAULT_PORT
            }
        }
    }

    suspend fun start() {
        server.startSuspend(wait = false)
    }

    suspend fun stop() {
        server.stopSuspend(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
    }
}
