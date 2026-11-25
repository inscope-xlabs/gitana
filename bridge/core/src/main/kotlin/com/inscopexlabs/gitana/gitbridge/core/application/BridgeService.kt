package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.ResponseEnvelope
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import com.inscopexlabs.gitana.gitbridge.core.ports.AuditSinkPort
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import com.inscopexlabs.gitana.gitbridge.core.ports.GitHostPort
import com.inscopexlabs.gitana.gitbridge.core.ports.RequestStorePort

/**
 * Single entrypoint shared by the localhost and relay adapters. Transport-specific types must never
 * appear here.
 */
@Suppress("unused")
class BridgeService(
    private val policy: PolicyEngine,
    private val gitHost: GitHostPort,
    private val approvals: ApprovalGate,
    private val requests: RequestStorePort,
    private val audit: AuditSinkPort,
    private val clock: ClockPort,
) {
    suspend fun submit(request: RequestEnvelope): ResponseEnvelope = TODO("implement the spec 6.2 pipeline")

    fun status(requestId: String): ResponseEnvelope? = TODO("lifecycle lookup")
}
