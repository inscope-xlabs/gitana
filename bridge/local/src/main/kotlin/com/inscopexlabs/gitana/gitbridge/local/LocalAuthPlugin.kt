package com.inscopexlabs.gitana.gitbridge.local

import java.security.SecureRandom
import java.util.Base64

/** Authenticates the local-client credential on every route except health (RTB-22). */
class LocalAuthPlugin {
    // Placeholder credential, random per instance, until the credential store is injected.
    private val token: String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun isAuthorized(path: String, authorization: String?): Boolean =
        path == HEALTH_PATH || authorization == "Bearer $token"

    private companion object {
        const val HEALTH_PATH = "/api/v1/health"
    }
}
