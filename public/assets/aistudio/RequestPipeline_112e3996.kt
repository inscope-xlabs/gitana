// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/application/RequestPipeline.kt
package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.*
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Standard 1 – Stage 1: Core behaviour with dummy data (fake delay, listOf()).
 * Standard 1 – Stage 2: State is exposed via a sealed type (PipelineResult).
 * Standard 1 – Stage 3: Every step handles failures, empty/null inputs, and edge cases.
 */
class RequestPipeline(
    private val policyEngine: PolicyEngine,
    private val approvalGate: ApprovalGate,
    private val githubAdapter: GitHubAdapter,
    private val auditSink: AuditSink,
) {

    /** Result of each pipeline step. */
    sealed class PipelineResult {
        data class Continue(val state: RequestState) : PipelineResult()
        data class Reject(val reason: String, val errorCode: ErrorCode) : PipelineResult()
        data class Fail(val reason: String, val errorCode: ErrorCode) : PipelineResult()
        data class Success(val result: ToolResult) : PipelineResult()
    }

    /**
     * Executes the full pipeline for [request].
     * The caller (BridgeService) is responsible for persisting the final state.
     */
    suspend fun execute(request: ToolRequest): PipelineResult {
        // 1. Validate envelope (happy‑path, edge‑case: null/empty required fields)
        val validation = validateEnvelope(request)
        if (validation != null) return validation

        // 2. Expiry check (edge‑case: expired request)
        if (isExpired(request)) {
            return PipelineResult.Reject("Request expired", ErrorCode.REQUEST_EXPIRED)
        }

        // 3. Path canonicalisation (dummy implementation – real logic belongs to B2)
        val canonicalPath = canonicalizePath(request.path)
        if (canonicalPath == null) {
            return PipelineResult.Reject("Invalid path", ErrorCode.INVALID_PATH)
        }

        // 4. Policy engine (dummy allow‑all for Standard 1; real rules in B5)
        val policyDecision = policyEngine.evaluate(request, canonicalPath)
        if (!policyDecision.allowed) {
            return PipelineResult.Reject(policyDecision.reason, policyDecision.errorCode)
        }

        // 5. Approval gate (only for operations that require it – see B6)
        if (policyDecision.requiresApproval) {
            val approval = approvalGate.requestApproval(request)
            if (!approval.approved) {
                return PipelineResult.Reject("Approval denied", ErrorCode.APPROVAL_DENIED)
            }
        }

        // 6. GitHub dispatch (dummy adapter – real implementation in C1)
        val dispatchResult = try {
            githubAdapter.dispatch(request, canonicalPath)
        } catch (e: Exception) {
            // Stage 3: network/IO failures never crash the pipeline
            return PipelineResult.Fail(
                "GitHub dispatch failed: ${e.message}",
                ErrorCode.UPSTREAM_ERROR
            )
        }

        // 7. Audit (fire‑and‑forget is safe because AuditSink is responsible for its own errors)
        auditSink.record(request, dispatchResult)

        // 8. Terminal state
        return when (dispatchResult) {
            is ToolResult.Success -> PipelineResult.Success(dispatchResult)
            is ToolResult.Error   -> PipelineResult.Fail(dispatchResult.message, dispatchResult.code)
        }
    }

    // ------------------------------------------------------------------
    // Helper functions – all contain explicit error handling (Stage 3)
    // ------------------------------------------------------------------

    private fun validateEnvelope(request: ToolRequest): PipelineResult.Reject? {
        if (request.operation.isBlank()) {
            return PipelineResult.Reject("Operation is blank", ErrorCode.INVALID_REQUEST)
        }
        if (request.repoFullName.isBlank()) {
            return PipelineResult.Reject("Repository name is blank", ErrorCode.INVALID_REQUEST)
        }
        // In a real implementation the full envelope schema would be validated here.
        return null
    }

    private fun isExpired(request: ToolRequest): Boolean {
        val now = Instant.now()
        return request.expiresAt?.isBefore(now) ?: false
    }

    /**
     * Dummy canonicalisation – returns null for obviously bad paths.
     * Real algorithm is part of B2.
     */
    private fun canonicalizePath(rawPath: String?): String? {
        if (rawPath == null) return null
        if (rawPath.contains("..")) return null   // edge‑case: traversal attempt
        return rawPath.trim().trimStart('/')
    }
}

// ------------------------------------------------------------------
// Placeholder interfaces – they will be replaced by real implementations
// in tasks B5, B6, C1, and storage respectively. For Standard 1 they
// provide the correct shape so the pipeline is functionally complete.
// ------------------------------------------------------------------

interface PolicyEngine {
    suspend fun evaluate(request: ToolRequest, canonicalPath: String?): PolicyDecision
}

data class PolicyDecision(
    val allowed: Boolean,
    val requiresApproval: Boolean = false,
    val reason: String = "",
    val errorCode: ErrorCode = ErrorCode.POLICY_VIOLATION,
)

interface ApprovalGate {
    suspend fun requestApproval(request: ToolRequest): ApprovalResult
}

data class ApprovalResult(val approved: Boolean)

interface GitHubAdapter {
    suspend fun dispatch(request: ToolRequest, canonicalPath: String?): ToolResult
}

interface AuditSink {
    suspend fun record(request: ToolRequest, result: ToolResult)
}