package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import com.inscopexlabs.gitana.gitbridge.local.limits.ContentLengthCheck
import com.inscopexlabs.gitana.gitbridge.local.limits.InFlightLimiter
import com.inscopexlabs.gitana.gitbridge.local.limits.LocalLimits
import com.inscopexlabs.gitana.gitbridge.local.limits.LocalRateLimiter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.request.path
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import java.net.BindException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Observable local HTTP availability, kept separate from relay health (RTB-53). */
sealed interface LocalServerState {
    /** Not listening. */
    data object Stopped : LocalServerState

    /** Listeners are being established. */
    data object Starting : LocalServerState

    /** Listening on [addresses] at [port]; both are shown in the UI (`DV-RTB-PORT`). */
    data class Running(val addresses: List<String>, val port: Int) : LocalServerState

    /** The server is not usable. Calling [LocalServer.start] again retries. */
    data class Failed(val reason: LocalServerFailure) : LocalServerState
}

/** Why the local server is [LocalServerState.Failed]. */
enum class LocalServerFailure {
    /** The configured port is outside the permitted range; retrying needs a new configuration. */
    INVALID_PORT,

    /** The address or port could not be bound, typically because it is already in use. */
    ADDRESS_UNAVAILABLE,

    /** The platform refused permission to listen. */
    PERMISSION_DENIED,

    /** The IPv4 listener stopped unexpectedly after starting. */
    LISTENER_FAILED,

    /** Any other start failure. */
    START_FAILED,
}

/** Sink for local diagnostics. Messages carry no headers, credentials, paths or bodies. */
fun interface LocalDiagnostics {
    /** Records one diagnostic [message]. */
    fun record(message: String)
}

/** One loopback listener. An instance is started at most once. */
interface LocalListener {
    /** Binds and starts serving. Throws if the listener cannot be established. */
    suspend fun start()

    /** Releases the listener. Safe to call after a failed [start]. */
    suspend fun stop()
}

/** Creates listeners, so that [LocalServer] does not depend on the HTTP engine. */
fun interface LocalListenerFactory {
    /** @param onFailure invoked, possibly on another thread, if the listener fails on its own. */
    fun create(host: String, port: Int, onFailure: (Throwable) -> Unit): LocalListener
}

/**
 * Owns the loopback listeners and publishes their [state].
 *
 * RTB-03: `127.0.0.1` is mandatory and its failure prevents start; `::1` is attempted afterwards
 * and its failure is recorded in [diagnostics] without affecting the IPv4 listener.
 */
class LocalServer(
    private val listenerFactory: LocalListenerFactory,
    private val diagnostics: LocalDiagnostics,
    private val port: Int = LoopbackBinding.DEFAULT_PORT,
) {
    private class Slot(val address: String) {
        @Volatile
        var failed = false
    }

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<LocalServerState>(LocalServerState.Stopped)
    private val listeners = mutableListOf<LocalListener>()

    @Volatile
    private var slots: List<Slot> = emptyList()

    /** Current availability. */
    val state: StateFlow<LocalServerState> = mutableState.asStateFlow()

    /**
     * Starts the listeners. Does nothing if already running. Failures are reported through
     * [state] and are not thrown; cancellation releases every listener and is rethrown.
     */
    suspend fun start(): Unit = mutex.withLock {
        if (mutableState.value is LocalServerState.Running) return@withLock
        closeAll()
        if (!LoopbackBinding.isAllowedPort(port)) {
            mutableState.value = LocalServerState.Failed(LocalServerFailure.INVALID_PORT)
            return@withLock
        }
        mutableState.value = LocalServerState.Starting
        try {
            val ipv4 = try {
                open(LoopbackBinding.IPV4)
            } catch (cause: Exception) {
                currentCoroutineContext().ensureActive()
                diagnostics.record("IPv4 loopback listener failed: ${cause.javaClass.simpleName}")
                mutableState.value = LocalServerState.Failed(classify(cause))
                return@withLock
            }
            val ipv6 = try {
                open(LoopbackBinding.IPV6)
            } catch (cause: Exception) {
                currentCoroutineContext().ensureActive()
                diagnostics.record(
                    "IPv6 loopback listener unavailable, continuing on IPv4: ${cause.javaClass.simpleName}",
                )
                null
            }
            slots = listOfNotNull(ipv4, ipv6)
            mutableState.value = LocalServerState.Running(slots.map { it.address }, port)
            // A listener may have failed before the running state was published.
            reconcile()
        } finally {
            // Still starting here means start() was cancelled or ended abnormally.
            if (mutableState.value == LocalServerState.Starting) {
                withContext(NonCancellable) { closeAll() }
                mutableState.value = LocalServerState.Stopped
            }
        }
    }

    /** Stops every listener. Safe to call in any state. */
    suspend fun stop(): Unit = mutex.withLock {
        withContext(NonCancellable) { closeAll() }
        mutableState.value = LocalServerState.Stopped
    }

    private suspend fun open(address: String): Slot {
        val slot = Slot(address)
        val listener = listenerFactory.create(LoopbackBinding.requireLoopback(address), port) { cause ->
            diagnostics.record("Listener on $address failed: ${cause.javaClass.simpleName}")
            slot.failed = true
            reconcile()
        }
        // Tracked before starting so that cancellation cannot leak it.
        listeners.add(listener)
        try {
            listener.start()
        } catch (cause: Throwable) {
            withContext(NonCancellable) { closeQuietly(listener) }
            listeners.remove(listener)
            throw cause
        }
        return slot
    }

    private fun reconcile() {
        mutableState.update { current ->
            if (current !is LocalServerState.Running) return@update current
            val failed = slots.filter { it.failed }.map { it.address }
            if (LoopbackBinding.IPV4 in failed) {
                LocalServerState.Failed(LocalServerFailure.LISTENER_FAILED)
            } else {
                current.copy(addresses = current.addresses - failed.toSet())
            }
        }
    }

    private suspend fun closeAll() {
        slots = emptyList()
        listeners.toList().forEach { closeQuietly(it) }
        listeners.clear()
    }

    private suspend fun closeQuietly(listener: LocalListener) {
        try {
            listener.stop()
        } catch (cause: Exception) {
            diagnostics.record("Listener did not stop cleanly: ${cause.javaClass.simpleName}")
        }
    }

    private fun classify(cause: Throwable): LocalServerFailure {
        val chain = generateSequence(cause) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        return when {
            chain.any { it is SecurityException } -> LocalServerFailure.PERMISSION_DENIED
            chain.any { it is BindException } -> LocalServerFailure.ADDRESS_UNAVAILABLE
            else -> LocalServerFailure.START_FAILED
        }
    }

    private companion object {
        const val MAX_CAUSE_DEPTH = 10
    }
}

/** A listener could not start; wraps the engine's cause. */
class LocalListenerStartException(cause: Throwable) : RuntimeException("Listener failed to start", cause)

/**
 * Ktor CIO implementation of [LocalListenerFactory].
 *
 * Every listener from one factory shares one set of limits, so IPv4 and IPv6 cannot be combined
 * to exceed them. No routes exist until D3: a request that passes the guards gets `NOT_FOUND`.
 *
 * The request timeout cancels the call's coroutine and reports `TIMEOUT`, which the error
 * catalogue reserves for pre-dispatch expiry. D3 must therefore not dispatch a mutating
 * operation inside the call coroutine (RTB-43, RTB-44).
 */
class KtorListenerFactory(
    clock: ClockPort,
    private val auth: LocalAuthPlugin,
    private val diagnostics: LocalDiagnostics,
    private val limits: LocalLimits = LocalLimits(),
) : LocalListenerFactory {
    private val guard = HostHeaderGuard()
    private val rateLimiter = LocalRateLimiter(limits.rateLimitPerMinute, clock)
    private val inFlight = InFlightLimiter(limits.maxInFlight)

    override fun create(host: String, port: Int, onFailure: (Throwable) -> Unit): LocalListener {
        val address = LoopbackBinding.requireLoopback(host)
        val server = embeddedServer(
            factory = CIO,
            rootConfig = serverConfig(applicationEnvironment()) {
                // Without a handler, a failed bind in the CIO engine reaches the thread's
                // uncaught exception handler, which terminates an Android process.
                parentCoroutineContext = CoroutineExceptionHandler { _, cause -> onFailure(cause) }
                module { installGuards() }
            },
        ) {
            // CIO offers an idle timeout but no maximum connection count.
            connectionIdleTimeoutSeconds = limits.requestTimeoutSeconds
            connector {
                this.host = address
                this.port = port
            }
        }
        return object : LocalListener {
            override suspend fun start() {
                try {
                    server.startSuspend(wait = false)
                } catch (cause: CancellationException) {
                    // CIO can surface a bind failure as a cancellation. Only rethrow it as one
                    // if this coroutine really was cancelled.
                    currentCoroutineContext().ensureActive()
                    throw LocalListenerStartException(cause)
                }
            }

            override suspend fun stop() {
                server.stopSuspend(gracePeriodMillis = STOP_GRACE_MILLIS, timeoutMillis = STOP_TIMEOUT_MILLIS)
            }
        }
    }

    private fun Application.installGuards() {
        // Bounds bodies that declare no length, as they are read.
        install(RequestBodyLimit) { bodyLimit { limits.maxRequestBytes } }

        intercept(ApplicationCallPipeline.Plugins) {
            val headers = call.request.headers
            if (!guard.isPermitted(
                    headers.getAll(HttpHeaders.Host).orEmpty(),
                    headers.getAll(HttpHeaders.Origin).orEmpty(),
                )
            ) {
                call.reject(TransportErrors.forbidden)
                return@intercept finish()
            }
            when (limits.checkContentLength(headers.getAll(HttpHeaders.ContentLength).orEmpty())) {
                ContentLengthCheck.MALFORMED -> {
                    call.reject(TransportErrors.malformedLength)
                    return@intercept finish()
                }
                ContentLengthCheck.TOO_LARGE -> {
                    call.reject(TransportErrors.requestTooLarge)
                    return@intercept finish()
                }
                ContentLengthCheck.ACCEPTED -> Unit
            }
            val retryAfter = rateLimiter.tryAcquire()
            if (retryAfter > 0) {
                call.reject(TransportErrors.rateLimited, retryAfter)
                return@intercept finish()
            }
            if (!inFlight.tryAcquire()) {
                call.reject(TransportErrors.busy, BUSY_RETRY_SECONDS)
                return@intercept finish()
            }
            try {
                withTimeout(limits.requestTimeoutSeconds * MILLIS_PER_SECOND) { proceed() }
            } catch (cause: TimeoutCancellationException) {
                diagnostics.record("Local request timed out")
                call.reject(TransportErrors.timeout)
                finish()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                if (generateSequence<Throwable>(cause) { it.cause }.any { it is PayloadTooLargeException }) {
                    call.reject(TransportErrors.requestTooLarge)
                } else {
                    diagnostics.record("Local request failed: ${cause.javaClass.simpleName}")
                    call.reject(TransportErrors.internal)
                }
                finish()
            } finally {
                inFlight.release()
            }
        }

        val authentication = PipelinePhase("LocalAuth")
        insertPhaseAfter(ApplicationCallPipeline.Plugins, authentication)
        intercept(authentication) {
            val header = call.request.headers[HttpHeaders.Authorization]
            when (val result = auth.authenticate(call.request.path(), header)) {
                is LocalAuthResult.Authenticated -> call.attributes.put(localPrincipalKey, result.principal)
                LocalAuthResult.Exempt -> Unit
                LocalAuthResult.Rejected -> {
                    call.respondText("", status = HttpStatusCode.Unauthorized)
                    finish()
                }
            }
        }

        // Ktor's own fallback answers 404 with an empty body and is registered ahead of module
        // interceptors, so unknown routes are answered in a phase before it.
        val unknownRoute = PipelinePhase("UnknownRoute")
        insertPhaseBefore(ApplicationCallPipeline.Fallback, unknownRoute)
        intercept(unknownRoute) {
            if (!call.response.isCommitted && call.response.status() == null) {
                call.reject(TransportErrors.notFound)
            }
        }

        // Covers responses of known length; D3 responses are sized text.
        sendPipeline.intercept(ApplicationSendPipeline.After) { content ->
            val length = (content as? OutgoingContent)?.contentLength ?: return@intercept
            if (length > limits.maxResponseBytes) {
                val error = TransportErrors.responseTooLarge
                proceedWith(
                    TextContent(
                        error.toTransportJson(),
                        ContentType.Application.Json,
                        HttpStatusCode.PayloadTooLarge,
                    ),
                )
            }
        }
    }

    /** Sends the transport error envelope, if nothing has been sent yet. */
    private suspend fun ApplicationCall.reject(error: BridgeError, retryAfterSeconds: Int? = null) {
        if (response.isCommitted || response.isSent) return
        try {
            retryAfterSeconds?.let { response.header(HttpHeaders.RetryAfter, it.toString()) }
            respondText(
                error.toTransportJson(),
                ContentType.Application.Json,
                HttpStatusCode.fromValue(error.httpStatus ?: HttpStatusCode.InternalServerError.value),
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            // Typically the client has disconnected; there is nobody left to answer.
            diagnostics.record("Could not send error response: ${cause.javaClass.simpleName}")
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val STOP_GRACE_MILLIS = 1_000L
        const val STOP_TIMEOUT_MILLIS = 2_000L
        const val BUSY_RETRY_SECONDS = 1
    }
}

/** Holds the authenticated principal of a call, for the D3 routes. */
internal val localPrincipalKey = AttributeKey<Principal>("gitbridge.local.principal")

/** Pre-request errors, with the fields fixed by the `error.v1.json` catalogue. */
internal object TransportErrors {
    val forbidden = error(
        ErrorCode.FORBIDDEN, ErrorCategory.AUTHORIZATION, 403, false,
        ExecutionState.NOT_STARTED, "Host or Origin is not permitted.",
    )
    val malformedLength = error(
        ErrorCode.VALIDATION_FAILED, ErrorCategory.VALIDATION, 400, false,
        ExecutionState.NOT_STARTED, "Content-Length is invalid.",
    )
    val requestTooLarge = error(
        ErrorCode.PAYLOAD_TOO_LARGE, ErrorCategory.RESOURCE_LIMIT, 413, false,
        ExecutionState.NOT_STARTED, "Request body exceeds the size limit.",
    )
    val responseTooLarge = error(
        ErrorCode.PAYLOAD_TOO_LARGE, ErrorCategory.RESOURCE_LIMIT, 413, false,
        ExecutionState.NOT_STARTED, "Response exceeds the size limit.",
    )
    val rateLimited = error(
        ErrorCode.RATE_LIMITED, ErrorCategory.RESOURCE_LIMIT, 429, true,
        ExecutionState.NOT_STARTED, "Local rate limit exceeded.",
    )
    val busy = error(
        ErrorCode.RATE_LIMITED, ErrorCategory.RESOURCE_LIMIT, 429, true,
        ExecutionState.NOT_STARTED, "Too many requests in progress.",
    )
    val notFound = error(
        ErrorCode.NOT_FOUND, ErrorCategory.PROVIDER, 404, false,
        ExecutionState.FAILED, "Unknown route.",
    )
    val timeout = error(
        ErrorCode.TIMEOUT, ErrorCategory.TRANSPORT, 504, false,
        ExecutionState.FAILED, "Request timed out.",
    )
    val internal = error(
        ErrorCode.INTERNAL, ErrorCategory.INTERNAL, 500, false,
        ExecutionState.FAILED, "Internal bridge failure.",
    )

    private fun error(
        code: ErrorCode,
        category: ErrorCategory,
        status: Int,
        retryable: Boolean,
        executionState: ExecutionState,
        message: String,
    ) = BridgeError(code, category, status, retryable, executionState, message)
}

/**
 * Serializes the `TransportError` envelope. Only for the fixed [TransportErrors], whose messages
 * need no JSON escaping.
 */
internal fun BridgeError.toTransportJson(): String =
    """{"error":{"code":"${code.name}","category":"${category.name}","http_status":$httpStatus,""" +
        """"retryable":$retryable,"execution_state":"${executionState.name}","message":"$message"}}"""
