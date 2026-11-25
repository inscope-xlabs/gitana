package com.inscopexlabs.gitana.gitbridge.github

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeException
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import com.inscopexlabs.gitana.gitbridge.core.ports.GitHostPort
import com.inscopexlabs.gitana.gitbridge.github.client.GitHubHttpClient

/** Maps approved internal commands to specific GitHub REST calls. Never accepts raw endpoints. */
@Suppress("unused")
class GitHubAdapter(
    private val client: GitHubHttpClient,
    private val credentials: CredentialStorePort,
) : GitHostPort {
    override suspend fun execute(request: RequestEnvelope): ToolResult = throw BridgeException(
        BridgeError(
            code = ErrorCode.NOT_IMPLEMENTED,
            category = ErrorCategory.INTERNAL,
            httpStatus = 501,
            retryable = false,
            executionState = ExecutionState.NOT_STARTED,
            message = "GitHub adapter not implemented",
        ),
    )
}
