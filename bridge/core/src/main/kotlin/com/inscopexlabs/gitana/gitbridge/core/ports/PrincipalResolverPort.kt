package com.inscopexlabs.gitana.gitbridge.core.ports

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind

/** Maps an adapter-verified subject to a canonical principal, or null if unknown or revoked. */
interface PrincipalResolverPort {
    fun resolve(transport: TransportKind, subjectId: String): Principal?
}
