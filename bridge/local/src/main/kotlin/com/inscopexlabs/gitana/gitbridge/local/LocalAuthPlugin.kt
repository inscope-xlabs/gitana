package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import com.inscopexlabs.gitana.gitbridge.core.ports.PrincipalResolverPort

/** Outcome of [LocalAuthPlugin.authenticate]. */
sealed interface LocalAuthResult {
    /** The route needs no credential. */
    data object Exempt : LocalAuthResult

    /** The credential is valid and maps to [principal]. */
    data class Authenticated(val principal: Principal) : LocalAuthResult

    /** The request must not proceed. */
    data object Rejected : LocalAuthResult
}

/** Authenticates the local-client credential on every route except health (RTB-22). */
class LocalAuthPlugin(
    private val credentials: CredentialStorePort,
    private val principals: PrincipalResolverPort,
) {
    fun authenticate(path: String, authorization: String?): LocalAuthResult {
        if (path == HEALTH_PATH) return LocalAuthResult.Exempt
        val token = authorization.orEmpty().removePrefix(SCHEME).toCharArray()
        if (!credentials.verify(CredentialClass.LOCAL_CLIENT, token)) return LocalAuthResult.Rejected
        val principal = principals.resolve(TransportKind.LOCALHOST, SUBJECT_ID)
        return principal?.let { LocalAuthResult.Authenticated(it) } ?: LocalAuthResult.Rejected
    }

    companion object {
        /** Subject presented to [PrincipalResolverPort] for the local-client credential. */
        const val SUBJECT_ID: String = "local-client"

        private const val HEALTH_PATH = "/api/v1/health"
        private const val SCHEME = "Bearer "
    }
}
