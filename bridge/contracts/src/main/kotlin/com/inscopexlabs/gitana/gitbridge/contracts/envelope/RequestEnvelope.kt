package com.inscopexlabs.gitana.gitbridge.contracts.envelope

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolArguments
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.version.ContractVersion

/**
 * Canonical command envelope (spec 8.2). Authentication material is deliberately not part of it.
 * Times are epoch milliseconds across transports without requiring desugaring.
 */
data class RequestEnvelope(
    val contractVersion: ContractVersion,
    val requestId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val principal: Principal,
    val operation: ToolOperation,
    val arguments: ToolArguments,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}
