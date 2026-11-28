package com.inscopexlabs.gitana.gitbridge.di

import com.inscopexlabs.gitana.gitbridge.approval.ApprovalCoordinator
import com.inscopexlabs.gitana.gitbridge.approval.ApprovalNotificationReceiver
import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeException
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.core.application.AuditSink
import com.inscopexlabs.gitana.gitbridge.core.application.BridgeService
import com.inscopexlabs.gitana.gitbridge.core.application.GitHubAdapter
import com.inscopexlabs.gitana.gitbridge.core.application.InMemoryWriteCoordinator
import com.inscopexlabs.gitana.gitbridge.core.application.RequestPipeline
import com.inscopexlabs.gitana.gitbridge.core.application.WriteCoordinator
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyConfig
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngineImpl
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import com.inscopexlabs.gitana.gitbridge.local.KtorListenerFactory
import com.inscopexlabs.gitana.gitbridge.local.LocalDiagnostics
import com.inscopexlabs.gitana.gitbridge.local.LocalListenerFactory
import com.inscopexlabs.gitana.gitbridge.local.LocalServer

/**
 * Manual DI composition root. One coordinator and one BridgeService instance are
 * shared across transports; local transport does not depend on relay (RTB-29).
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

    /**
     * Fail-closed credential store until KeystoreCredentialVault (Phase F/vault) is implemented:
     * all verify checks return false and GitHub PAT access throws.
     */
    val credentialStore: CredentialStorePort = object : CredentialStorePort {
        override fun <T> withGitHubToken(block: (CharArray) -> T): T {
            throw BridgeException(
                BridgeError(
                    code = ErrorCode.NOT_IMPLEMENTED,
                    category = ErrorCategory.INTERNAL,
                    httpStatus = 501,
                    retryable = false,
                    executionState = ExecutionState.NOT_STARTED,
                    message = "Credential vault is not initialized.",
                ),
            )
        }

        override fun verify(credentialClass: CredentialClass, presented: CharArray): Boolean = false

        override fun revoke(credentialClass: CredentialClass) = Unit
    }

    private val unconfiguredGitHubAdapter: GitHubAdapter = object : GitHubAdapter {
        override suspend fun dispatch(request: RequestEnvelope, canonicalPath: String?): ToolResult {
            throw BridgeException(
                BridgeError(
                    code = ErrorCode.NOT_IMPLEMENTED,
                    category = ErrorCategory.INTERNAL,
                    httpStatus = 501,
                    retryable = false,
                    executionState = ExecutionState.NOT_STARTED,
                    message = "GitHub adapter is not wired in BridgeGraph.",
                ),
            )
        }
    }

    private val noopAuditSink: AuditSink = object : AuditSink {
        override suspend fun record(request: RequestEnvelope, result: ToolResult) = Unit
    }

    val requestPipeline: RequestPipeline = RequestPipeline(
        policyEngine = policyEngine,
        approvalGate = approvalGate,
        githubAdapter = unconfiguredGitHubAdapter,
        auditSink = noopAuditSink,
        writeCoordinator = writeCoordinator,
    )

    val bridgeService: BridgeService = BridgeService(requestPipeline)

    val localDiagnostics: LocalDiagnostics = LocalDiagnostics { _ -> }

    val localListenerFactory: LocalListenerFactory = KtorListenerFactory(
        clock = clock,
        diagnostics = localDiagnostics,
        credentials = credentialStore,
        bridge = bridgeService,
    )

    val localServer: LocalServer = LocalServer(
        listenerFactory = localListenerFactory,
        diagnostics = localDiagnostics,
    )

    // TODO: vault, storage, GitHubAdapter wiring (F1), RelayClient.
}
