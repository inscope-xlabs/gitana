package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort

/**
 * Authenticates the local-client credential on every request, even on loopback (RTB-22, RTB-23, RTB-04).
 *
 * - Credential travels only in the `Authorization` header as `Bearer <43-char token>`.
 * - Token shape: 32 random bytes, base64url without padding → exactly 43 characters of `[A-Za-z0-9_-]`.
 * - Missing or malformed header → [AuthDecision.Unauthorized] (HTTP 401 / AUTH_FAILED).
 * - Token present in the query string → [AuthDecision.TokenInQuery] (HTTP 400 / VALIDATION_FAILED).
 * - Health path (`/api/v1/health`) is exempt (OpenAPI `security: []`).
 * - The presented token is never logged or retained beyond the constant-time compare.
 */
class LocalAuthPlugin(
    private val credentials: CredentialStorePort,
) {
    /**
     * @param path request path (e.g. `/api/v1/health` or `/api/v1/tools/read_file`)
     * @param authorizationHeaders every `Authorization` header value received
     * @param queryString raw query string, or null / empty when absent
     */
    fun authenticate(
        path: String,
        authorizationHeaders: List<String>,
        queryString: String?,
    ): AuthDecision {
        if (isHealthPath(path)) return AuthDecision.Ok

        if (queryContainsToken(queryString)) return AuthDecision.TokenInQuery

        val header = authorizationHeaders.singleOrNull()
            ?: return AuthDecision.Unauthorized

        val token = parseBearerToken(header) ?: return AuthDecision.Unauthorized

        val presented = token.toCharArray()
        return try {
            if (credentials.verify(CredentialClass.LOCAL_CLIENT, presented)) {
                AuthDecision.Ok
            } else {
                AuthDecision.Unauthorized
            }
        } finally {
            presented.fill('\u0000')
        }
    }

    private fun isHealthPath(path: String): Boolean {
        val normalized = path.trimEnd('/').lowercase()
        return normalized == HEALTH_PATH || normalized.endsWith(HEALTH_PATH)
    }

    private fun queryContainsToken(query: String?): Boolean {
        if (query.isNullOrEmpty()) return false
        // Any query parameter that looks like a bearer or raw token is rejected (RTB-23).
        return QUERY_TOKEN_HINT.containsMatchIn(query)
    }

    private fun parseBearerToken(header: String): String? {
        val match = BEARER_PATTERN.matchEntire(header.trim()) ?: return null
        return match.groupValues[1]
    }

    private companion object {
        const val HEALTH_PATH = "/api/v1/health"
        /** Exactly 43 base64url characters after the Bearer scheme (schema RequestHeaders). */
        val BEARER_PATTERN = Regex("""^Bearer ([A-Za-z0-9_-]{43})$""")
        /**
         * Detects tokens placed in the query string (e.g. `?token=…`, `?access_token=…`,
         * `?authorization=Bearer…`). Case-insensitive; does not require a full parse.
         */
        val QUERY_TOKEN_HINT = Regex(
            """(?:^|[&?])(?:token|access_token|authorization|auth|bearer)=""",
            RegexOption.IGNORE_CASE,
        )
    }
}

/** Outcome of local-client authentication. */
sealed interface AuthDecision {
    /** Credential accepted, or health path exempted. */
    data object Ok : AuthDecision

    /** Missing, malformed, or non-matching Authorization header → 401 AUTH_FAILED. */
    data object Unauthorized : AuthDecision

    /** Token appeared in the query string → 400 VALIDATION_FAILED. */
    data object TokenInQuery : AuthDecision
}
