package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.local.limits.LocalLimits
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable local HTTP availability, kept separate from relay health (RTB-53). */
sealed interface LocalServerState {
    /** Not listening. */
    data object Stopped : LocalServerState

    /** Listeners are being established. */
    data object Starting : LocalServerState

    /** Listening on [addresses] at [port]; both are shown in the UI (`DV-RTB-PORT`). */
    data class Running(val addresses: List<String>, val port: Int) : LocalServerState

    /** The server could not start. */
    data object Failed : LocalServerState
}

/** One bound loopback listener. */
interface LocalListener {
    suspend fun start()

    suspend fun stop()
}

/** Creates listeners, so that [LocalServer] does not depend on the HTTP engine. */
fun interface LocalListenerFactory {
    fun create(host: String, port: Int): LocalListener
}

/** Owns the loopback listeners and publishes their state. */
class LocalServer(
    private val listenerFactory: LocalListenerFactory,
    private val port: Int = LoopbackBinding.DEFAULT_PORT,
) {
    private val mutableState = MutableStateFlow<LocalServerState>(LocalServerState.Stopped)
    private var listeners: List<LocalListener> = emptyList()

    val state: StateFlow<LocalServerState> = mutableState.asStateFlow()

    suspend fun start() {
        mutableState.value = LocalServerState.Starting
        val addresses = listOf(LoopbackBinding.IPV4, LoopbackBinding.IPV6)
        listeners = addresses.map { address ->
            listenerFactory.create(LoopbackBinding.requireLoopback(address), port).also { it.start() }
        }
        mutableState.value = LocalServerState.Running(addresses, port)
    }

    suspend fun stop() {
        listeners.forEach { it.stop() }
        listeners = emptyList()
        mutableState.value = LocalServerState.Stopped
    }
}

/** Ktor CIO implementation of [LocalListenerFactory]. No routes exist until D3. */
class KtorListenerFactory(
    private val limits: LocalLimits = LocalLimits(),
    private val guard: HostHeaderGuard = HostHeaderGuard(),
) : LocalListenerFactory {
    override fun create(host: String, port: Int): LocalListener {
        val server = embeddedServer(
            factory = CIO,
            rootConfig = serverConfig(applicationEnvironment()) {
                module {
                    intercept(ApplicationCallPipeline.Plugins) {
                        val hostHeader = call.request.headers[HttpHeaders.Host].orEmpty()
                        val origin = call.request.headers[HttpHeaders.Origin]
                        val status = if (guard.isPermitted(hostHeader, origin)) {
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
            connectionIdleTimeoutSeconds = limits.requestTimeoutSeconds
            connector {
                this.host = LoopbackBinding.requireLoopback(host)
                this.port = port
            }
        }
        return object : LocalListener {
            override suspend fun start() {
                server.startSuspend(wait = false)
            }

            override suspend fun stop() {
                server.stopSuspend(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
            }
        }
    }
}
