package com.inscopexlabs.gitana.gitbridge.di

import com.inscopexlabs.gitana.gitbridge.approval.ApprovalCoordinator
import com.inscopexlabs.gitana.gitbridge.approval.ApprovalNotificationReceiver
import com.inscopexlabs.gitana.gitbridge.core.application.InMemoryWriteCoordinator
import com.inscopexlabs.gitana.gitbridge.core.application.WriteCoordinator
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyConfig
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngineImpl
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort

/**
 * Manual DI composition root. One coordinator instance is shared by every
 * RequestPipeline created for local and relay transports.
 */
class BridgeGraph {
    val clock: ClockPort = object : ClockPort {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }

    val policyEngine: PolicyEngine = PolicyEngineImpl { PolicyConfig.default() }

    val approvalCoordinator: ApprovalCoordinator = ApprovalCoordinator()
    val approvalGate: ApprovalGate = approvalCoordinator
    val approvalNotificationReceiver: ApprovalNotificationReceiver =
        ApprovalNotificationReceiver(approvalCoordinator)

    /** Shared process-wide serialization coordinator for this bridge graph. */
    val writeCoordinator: WriteCoordinator = InMemoryWriteCoordinator()

    // TODO: vault, storage, GitHubAdapter, BridgeService, LocalServer, RelayClient.
}
