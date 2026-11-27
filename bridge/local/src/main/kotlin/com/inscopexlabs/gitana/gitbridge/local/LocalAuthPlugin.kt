package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import com.inscopexlabs.gitana.gitbridge.core.ports.PrincipalResolverPort
import java.net.URLDecoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Outcome of [LocalAuthPlugin.authenticate]. */
sealed interface LocalAuthResult {
    /** The route needs no credential. */
    data object Exempt : LocalAuthResult

    /** The credential is valid and maps to [principal]. */
    data class Authenticated(val principal: Principal) : LocalAuthResult

    /** The request must not proceed; [error] is the transport error to return. */
    data class Rejected(val error: BridgeError) : LocalAuthResult
}

/**
 * Authenticates the local-client credential on every route except `GET /api/v1/health`
 * (RTB-22, RTB-23). Anything that cannot be positively verified is rejected.
 *
 * The credential is accepted only from a single `Authorization: Bearer` header in the
 * `local-transport.v1.json` form. It is checked only against [CredentialClass.LOCAL_CLIENT]
 * (RTB-04) and is never logged or copied into a result (RTB-54). The character buffer passed to
 * the store is cleared afterwards; the platform's header strings cannot be, so no zeroization
 * guarantee is made (RTB-55).
 *
 * @param dispatcher where the blocking store and resolver calls run.
 */
class LocalAuthPlugin(
    private val credentials: CredentialStorePort,
    private val principals: PrincipalResolverPort,
    private val diagnostics: LocalDiagnostics,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * @param method the HTTP method.
     * @param path the raw request path, without the query string.
     * @param authorization every `Authorization` header value received.
     * @param rawQuery the undecoded query string, empty if there is none.
     * @return never throws, apart from cancellation of the calling coroutine.
     */
    suspend fun authenticate(
        method: String,
        path: String,
        authorization: List<String>,
        rawQuery: String,
    ): LocalAuthResult {
        // Checked first and on every route: a credential in a URL is refused even if the
        // header is also correct, or the route is exempt.
        queryError(rawQuery)?.let { return LocalAuthResult.Rejected(it) }
        if (method == HEALTH_METHOD && path == HEALTH_PATH) return LocalAuthResult.Exempt

        val match = authorization.singleOrNull()?.let { BEARER.matchEntire(it) }
            ?: return LocalAuthResult.Rejected(TransportErrors.authFailed)
        val token = match.groupValues[1].toCharArray()
        return try {
            withContext(dispatcher) { verify(token) }
        } catch (cause: Exception) {
            currentCoroutineContext().ensureActive()
            // Only the type is recorded: the message could carry secret material.
            diagnostics.record("Local credential check unavailable: ${cause.javaClass.simpleName}")
            LocalAuthResult.Rejected(TransportErrors.internal)
        } finally {
            token.fill('\u0000')
        }
    }

    private fun verify(token: CharArray): LocalAuthResult {
        val principal = if (credentials.verify(CredentialClass.LOCAL_CLIENT, token)) {
            principals.resolve(TransportKind.LOCALHOST, SUBJECT_ID)
        } else {
            null
        }
        // Wrong, unknown, revoked and cross-transport all look the same to the caller.
        if (principal == null || principal.transport != TransportKind.LOCALHOST) {
            diagnostics.record("Local authentication failed")
            return LocalAuthResult.Rejected(TransportErrors.authFailed)
        }
        return LocalAuthResult.Authenticated(principal)
    }

    private fun queryError(rawQuery: String): BridgeError? {
        if (rawQuery.isEmpty()) return null
        for (part in rawQuery.split('&', ';')) {
            val name: String
            val value: String
            try {
                name = decode(part.substringBefore('='))
                value = decode(part.substringAfter('=', ""))
            } catch (_: IllegalArgumentException) {
                return TransportErrors.malformedQuery
            }
            if (name.lowercase() in CREDENTIAL_PARAMETERS || looksLikeCredential(name) ||
                looksLikeCredential(value)
            ) {
                return TransportErrors.credentialInQuery
            }
        }
        return null
    }

    private fun decode(text: String): String = URLDecoder.decode(text, "UTF-8")

    private fun looksLikeCredential(text: String): Boolean =
        TOKEN.matches(text) || text.startsWith("bearer ", ignoreCase = true)

    companion object {
        /** Subject presented to [PrincipalResolverPort] for the local-client credential. */
        const val SUBJECT_ID: String = "local-client"

        private const val HEALTH_METHOD = "GET"
        private const val HEALTH_PATH = "/api/v1/health"
        private const val TOKEN_PATTERN = "[A-Za-z0-9_-]{43}"
        private val TOKEN = Regex(TOKEN_PATTERN)
        private val BEARER = Regex("Bearer ($TOKEN_PATTERN)")
        private val CREDENTIAL_PARAMETERS =
            setOf("access_token", "token", "authorization", "auth", "bearer")
    }
}
