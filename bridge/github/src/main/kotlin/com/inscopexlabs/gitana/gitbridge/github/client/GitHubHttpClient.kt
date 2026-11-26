// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/client/GitHubHttpClient.kt
package com.inscopexlabs.gitana.gitbridge.github.client

import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import com.inscopexlabs.gitana.gitbridge.github.GitHubApiVersion
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** HTTP methods the GitHub adapter is permitted to use. */
enum class GitHubMethod { GET, POST, PUT, DELETE }

/**
 * A request the adapter is allowed to send.
 *
 * The host is fixed by the transport; only the path and the query vary, and
 * the path is always built from an approved owner/repository record
 * (RTB-15).
 */
data class GitHubRequest(
    val method: GitHubMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** True when the request can change repository state. */
    val mutating: Boolean = false,
)

/**
 * A bounded response from GitHub.
 *
 * [body] holds response content and must never be logged (spec 10). Header
 * names are lower-cased so lookups are case-insensitive.
 */
data class GitHubResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    /** True when the status is a 2xx success. */
    val isSuccessful: Boolean get() = status in 200..299
}

/**
 * The data source the operations call.
 *
 * Implementations are fixed to the GitHub host and never accept a
 * caller-supplied URL, host or method (RTB-15).
 */
interface GitHubTransport {

    /** Sends [request] with the GitHub credential and returns the response. */
    suspend fun send(request: GitHubRequest): GitHubResponse
}

/**
 * Standard 1 – Stage 1: GitHub REST calls are performed over TLS against the
 *                       fixed GitHub host.
 * Standard 1 – Stage 2: The credential is obtained through the injected
 *                       [CredentialStorePort] inside the scope that owns the
 *                       plaintext, so no token is ever held as a field of this
 *                       class or as a parameter of an operation.
 * Standard 1 – Stage 3: Connect and read timeouts, the response-size
 *                       ceiling, unfollowed redirects, pre-dispatch failures
 *                       and post-dispatch failures are all mapped to typed
 *                       errors. The response body and the credential are
 *                       never logged.
 *
 * Spec 7.1, 9.4, 10, RTB-15, RTB-43, RTB-54, RTB-58, `DV-RTB-TIMEOUT-CONNECT-S`,
 * `DV-RTB-TIMEOUT-GITHUB-S`, `DV-RTB-RESPONSE-MAX-BYTES`.
 */
class GitHubHttpClient(
    private val credentials: CredentialStorePort,
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MILLIS,
    private val maxResponseBytes: Int = MAX_RESPONSE_BYTES,
    private val baseUrl: String = BASE_URL,
) : GitHubTransport {

    override suspend fun send(request: GitHubRequest): GitHubResponse =
        withContext(Dispatchers.IO) {
            credentials.withGitHubToken { token ->
                try {
                    execute(request, token)
                } finally {
                    // The credential is only valid inside the vault's scope
                    // (CredentialStorePort), so the buffer is cleared as soon
                    // as the call returns (RTB-55).
                    token.fill('\u0000')
                }
            }
        }

    /**
     * Performs one request. Blocking by design: it runs on the IO
     * dispatcher, which is what lets the credential scope stay synchronous.
     */
    private fun execute(request: GitHubRequest, token: CharArray): GitHubResponse {
        val connection = openConnection(request, token)
        var dispatched = false
        return try {
            if (request.body != null) {
                // Writing the body is what puts the request on the wire.
                writeBody(connection, request.body)
            }
            dispatched = true
            val status = connection.responseCode
            GitHubResponse(
                status = status,
                headers = readHeaders(connection),
                body = readBoundedBody(connection, status),
            )
        } catch (e: IOException) {
            throw transportFailure(e, dispatched, request.mutating)
        } finally {
            connection.disconnect()
        }
    }

    /** Opens a connection that is pinned to the GitHub host. */
    private fun openConnection(request: GitHubRequest, token: CharArray): HttpURLConnection {
        val connection = URL(baseUrl + request.path + queryString(request.query))
            .openConnection() as HttpURLConnection
        connection.requestMethod = request.method.name
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        // Renamed repositories must never be followed implicitly (spec 7.2).
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", GitHubApiVersion.HEADER_VALUE)
        connection.setRequestProperty("User-Agent", USER_AGENT)
        // The JDK requires the header value to be a String, so the token is
        // copied into an immutable String here. It cannot be zeroized; the
        // copy is therefore never logged and never retained (RTB-54, RTB-55).
        connection.setRequestProperty("Authorization", "Bearer " + String(token))
        if (request.body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
        }
        return connection
    }

    /** Writes the request body. */
    private fun writeBody(connection: HttpURLConnection, body: String) {
        connection.outputStream.use { stream ->
            stream.write(body.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /** Reads the response headers with lower-cased names. */
    private fun readHeaders(connection: HttpURLConnection): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        for ((name, values) in connection.headerFields) {
            val key = name?.lowercase() ?: continue
            if (values.isNullOrEmpty()) continue
            headers[key] = values.joinToString(",")
        }
        return headers
    }

    /**
     * Reads the response body, refusing to buffer more than
     * [maxResponseBytes] (spec 10).
     */
    private fun readBoundedBody(connection: HttpURLConnection, status: Int): String {
        val stream: InputStream? =
            if (status >= HTTP_BAD_REQUEST) connection.errorStream else connection.inputStream
        if (stream == null) return ""

        val declared = connection.getHeaderField("Content-Length")?.trim()?.toLongOrNull()
        if (declared != null && declared > maxResponseBytes) {
            throw GitHubFailure.payloadTooLarge(
                "GitHub response exceeds the $maxResponseBytes byte ceiling",
            )
        }

        val buffer = ByteArrayOutputStream()
        stream.use { input ->
            val chunk = ByteArray(CHUNK_SIZE)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxResponseBytes) {
                    throw GitHubFailure.payloadTooLarge(
                        "GitHub response exceeds the $maxResponseBytes byte ceiling",
                    )
                }
                buffer.write(chunk, 0, read)
            }
        }
        return buffer.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Maps a transport failure to a typed error.
     *
     * A failure before the request was dispatched is always safe to retry.
     * A failure after dispatch leaves a write in an unknown state, so it is
     * reported as `OUTCOME_UNKNOWN` and must be reconciled rather than
     * replayed (RTB-43, RTB-44).
     */
    private fun transportFailure(
        cause: IOException,
        dispatched: Boolean,
        mutating: Boolean,
    ): GitHubFailure {
        val message = cause.message?.take(MAX_CAUSE_MESSAGE) ?: "no detail"
        return if (!dispatched) {
            GitHubFailure(
                error = BridgeError(
                    code = ErrorCode.UPSTREAM_UNAVAILABLE,
                    category = ErrorCategory.PROVIDER,
                    httpStatus = 503,
                    retryable = true,
                    executionState = ExecutionState.FAILED,
                    message = "GitHub could not be reached: $message",
                ),
                dispatched = false,
            )
        } else if (mutating) {
            GitHubFailure(
                error = BridgeError(
                    code = ErrorCode.OUTCOME_UNKNOWN,
                    category = ErrorCategory.TRANSPORT,
                    httpStatus = 504,
                    retryable = false,
                    executionState = ExecutionState.OUTCOME_UNKNOWN,
                    message = "The write reached GitHub but no response was received; " +
                        "reconcile the repository state before retrying",
                ),
                dispatched = true,
            )
        } else {
            GitHubFailure(
                error = BridgeError(
                    code = ErrorCode.UPSTREAM_UNAVAILABLE,
                    category = ErrorCategory.PROVIDER,
                    httpStatus = 503,
                    retryable = true,
                    executionState = ExecutionState.FAILED,
                    message = "GitHub could not be reached: $message",
                ),
                dispatched = true,
            )
        }
    }

    /** Builds the query string for [query]. */
    private fun queryString(query: Map<String, String>): String {
        if (query.isEmpty()) return ""
        return query.entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
            "${encodeQueryComponent(key)}=${encodeQueryComponent(value)}"
        }
    }

    companion object {
        /** The GitHub REST host. It is never configurable per request. */
        const val BASE_URL: String = "https://api.github.com"

        /** User agent sent with every request. */
        const val USER_AGENT: String = "GitanaToolBridge/0.1"

        /** Outbound connect timeout (`DV-RTB-TIMEOUT-CONNECT-S`). */
        const val CONNECT_TIMEOUT_MILLIS: Int = 15_000

        /** GitHub request timeout (`DV-RTB-TIMEOUT-GITHUB-S`). */
        const val READ_TIMEOUT_MILLIS: Int = 45_000

        /** Maximum buffered response size (`DV-RTB-RESPONSE-MAX-BYTES`). */
        const val MAX_RESPONSE_BYTES: Int = 4 * 1024 * 1024

        /** Size of the copy buffer used while reading a response. */
        private const val CHUNK_SIZE: Int = 8 * 1024

        /** Lowest HTTP status that carries an error body. */
        private const val HTTP_BAD_REQUEST: Int = 400

        /** Longest transport detail copied into an error message. */
        private const val MAX_CAUSE_MESSAGE: Int = 120
    }
}

/**
 * Percent-encodes a query component.
 *
 * `URLEncoder` targets form encoding, so a space is re-encoded as `%20`
 * rather than `+`.
 */
internal fun encodeQueryComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

/**
 * Percent-encodes a repository-relative path, keeping the `/` separators
 * intact so nested paths and refs containing slashes stay addressable.
 */
internal fun encodePathSegments(path: String): String =
    path.split('/').joinToString("/") { segment -> encodeQueryComponent(segment) }

/**
 * Reads the small, well-known parts of GitHub JSON responses.
 *
 * Every accessor is total: a missing or mistyped field yields null instead
 * of an exception, so a changed upstream payload cannot crash the adapter.
 */
internal object GitHubJson {

    /** Lenient enough to ignore unknown fields, strict about JSON syntax. */
    private val JSON: Json = Json { ignoreUnknownKeys = true }

    /** Parses [body] as a JSON object, or null when it is not one. */
    fun parseObject(body: String): JsonObject? =
        runCatching { JSON.parseToJsonElement(body) as? JsonObject }.getOrNull()

    /** Parses [body] as a JSON array, or null when it is not one. */
    fun parseArray(body: String): JsonArray? =
        runCatching { JSON.parseToJsonElement(body) as? JsonArray }.getOrNull()

    /** The string value of [key], or null when absent or not a string. */
    fun string(source: JsonObject, key: String): String? =
        (source[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** The integer value of [key], or null when absent or not an integer. */
    fun int(source: JsonObject, key: String): Int? =
        (source[key] as? JsonPrimitive)?.content?.trim()?.toIntOrNull()

    /** The nested object at [key], or null when absent. */
    fun obj(source: JsonObject, key: String): JsonObject? = source[key] as? JsonObject
}
