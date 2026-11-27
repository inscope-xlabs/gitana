// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/application/RequestPipeline.kt
package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeException
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreateBranchArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.DeleteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalResult
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import kotlinx.coroutines.CancellationException

/**
 * Request pipeline with B7 write serialization.
 *
 * The caller must inject the shared [WriteCoordinator] from the application
 * composition root. All write dispatches for an identical repository/target
 * are serialized before reaching the GitHub adapter.
 */
class RequestPipeline(
    private val policyEngine: PolicyEngine,
    private val approvalGate: ApprovalGate,
    private val githubAdapter: GitHubAdapter,
    private val auditSink: AuditSink,
    private val writeCoordinator: WriteCoordinator,
) {

    sealed class PipelineResult {
        data class Continue(val state: RequestState) : PipelineResult()
        data class Reject(val reason: String, val errorCode: ErrorCode) : PipelineResult()
        data class Fail(val reason: String, val errorCode: ErrorCode) : PipelineResult()
        data class Success(val result: ToolResult) : PipelineResult()
    }

    suspend fun execute(
        request: RequestEnvelope,
        onStateChange: suspend (RequestState) -> Unit = {},
    ): PipelineResult {
        val validation = validateEnvelope(request)
        if (validation != null) return validation

        if (request.isExpired(System.currentTimeMillis())) {
            return PipelineResult.Reject("Request expired", ErrorCode.EXPIRED)
        }

        val rawPath = extractPath(request)
        val canonicalPath = if (rawPath != null) {
            canonicalizePath(rawPath)
                ?: return PipelineResult.Reject("Invalid path", ErrorCode.VALIDATION_FAILED)
        } else {
            null
        }

        val policyDecision = policyEngine.evaluate(request, canonicalPath)
        if (!policyDecision.allowed) {
            return PipelineResult.Reject(policyDecision.reason, policyDecision.errorCode)
        }
        onStateChange(RequestState.AUTHORIZED)

        if (policyDecision.requiresApproval) {
            onStateChange(RequestState.AWAITING_APPROVAL)
            when (approvalGate.requestApproval(request, policyDecision.reason)) {
                ApprovalResult.APPROVED -> Unit
                ApprovalResult.DENIED ->
                    return PipelineResult.Reject("Approval denied", ErrorCode.APPROVAL_DECLINED)
                ApprovalResult.TIMED_OUT ->
                    return PipelineResult.Reject("Approval timed out", ErrorCode.TIMEOUT)
            }
        }

        onStateChange(RequestState.EXECUTING)
        val dispatchResult = try {
            dispatchWithWriteSerialization(request, canonicalPath)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IllegalArgumentException) {
            return PipelineResult.Reject(
                failure.message ?: "Invalid write target",
                ErrorCode.VALIDATION_FAILED,
            )
        } catch (failure: BridgeException) {
            return PipelineResult.Fail(failure.error.message, failure.error.code)
        } catch (failure: Exception) {
            return PipelineResult.Fail(
                "GitHub dispatch failed: ${failure.message ?: failure::class.simpleName}",
                ErrorCode.UPSTREAM_UNAVAILABLE,
            )
        }

        try {
            auditSink.record(request, dispatchResult)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return PipelineResult.Fail(
                "Audit recording failed",
                ErrorCode.INTERNAL,
            )
        }

        return PipelineResult.Success(dispatchResult)
    }

    /**
     * Write operations acquire a per-repository/per-target lock around the
     * complete upstream mutation. Read-only and unrelated operations do not.
     */
    private suspend fun dispatchWithWriteSerialization(
        request: RequestEnvelope,
        canonicalPath: String?,
    ): ToolResult = when (val args = request.arguments) {
        is WriteFileArgs -> writeCoordinator.serialize(
            repo = args.repo,
            path = requireNotNull(canonicalPath) { "Write path was not canonicalized" },
        ) {
            githubAdapter.dispatch(request, canonicalPath)
        }

        is DeleteFileArgs -> writeCoordinator.serialize(
            repo = args.repo,
            path = requireNotNull(canonicalPath) { "Delete path was not canonicalized" },
        ) {
            githubAdapter.dispatch(request, canonicalPath)
        }

        is CreateBranchArgs -> writeCoordinator.serialize(
            repo = args.repo,
            path = "@refs/heads/${args.newBranch}",
        ) {
            githubAdapter.dispatch(request, canonicalPath)
        }

        else -> githubAdapter.dispatch(request, canonicalPath)
    }

    private fun validateEnvelope(request: RequestEnvelope): PipelineResult.Reject? {
        if (request.requestId.isBlank()) {
            return PipelineResult.Reject("Request ID is blank", ErrorCode.VALIDATION_FAILED)
        }
        if (request.arguments.repo.isBlank()) {
            return PipelineResult.Reject("Repository name is blank", ErrorCode.VALIDATION_FAILED)
        }
        return null
    }

    private fun extractPath(request: RequestEnvelope): String? = when (val args = request.arguments) {
        is ReadFileArgs -> args.path
        is WriteFileArgs -> args.path
        is DeleteFileArgs -> args.path
        else -> null
    }

    /** Existing B2 path-normalization work replaces this B1 scaffold helper. */
    private fun canonicalizePath(rawPath: String?): String? {
        if (rawPath == null || rawPath.isBlank()) return null
        if (rawPath.contains("..")) return null
        return rawPath.trim().trimStart('/').takeIf(String::isNotBlank)
    }
}

/** Implemented by the GitHub adapter module. */
interface GitHubAdapter {
    suspend fun dispatch(request: RequestEnvelope, canonicalPath: String?): ToolResult
}

/** Implemented by the storage/audit module. */
interface AuditSink {
    suspend fun record(request: RequestEnvelope, result: ToolResult)
}
