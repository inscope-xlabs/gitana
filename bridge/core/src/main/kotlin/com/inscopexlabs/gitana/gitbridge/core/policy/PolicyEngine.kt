// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/PolicyEngine.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreateBranchArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreatePullRequestArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.DeleteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ListBranchesArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import kotlinx.coroutines.CancellationException

/**
 * Standard 1 – Stage 1: Happy‑path decisions for allow‑listed repos, safe
 *                       paths, safe branches and read operations.
 * Standard 1 – Stage 2: Aggregates the four policy subsystems declared in
 *                       B3/B4 plus ConfirmationRules. State (PolicyConfig)
 *                       is supplied via a provider so hot‑reloads are honoured
 *                       on every call.
 * Standard 1 – Stage 3: Every subsystem's rejection branch is honoured:
 *                       allow‑list Denied, protected‑path Blocked, branch
 *                       Blocked, operation Blocked. Null/blank path or
 *                       operation is never allowed. Unexpected exceptions are
 *                       caught and mapped to `allowed = false`.
 */
interface PolicyEngine {
    /**
     * Returns a [PolicyDecision] for [request] given its already‑canonicalized
     * path. Callers (B1) are expected to run `PathNormalizer` first; a null or
     * blank [canonicalPath] is nevertheless rejected defensively.
     */
    suspend fun evaluate(request: RequestEnvelope, canonicalPath: String?): PolicyDecision
}

data class PolicyDecision(
    val allowed: Boolean,
    val requiresApproval: Boolean = false,
    val reason: String = "",
    val errorCode: ErrorCode = ErrorCode.POLICY_DENIED,
)

class PolicyEngineImpl(
    private val configProvider: () -> PolicyConfig,
) : PolicyEngine {

    override suspend fun evaluate(
        request: RequestEnvelope,
        canonicalPath: String?,
    ): PolicyDecision = try {
        evaluateInternal(request, canonicalPath)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Stage 3: policy evaluation must never crash the pipeline.
        deny(
            reason = "Policy evaluation failed: ${e.message ?: e::class.simpleName}",
            code = ErrorCode.INTERNAL,
        )
    }

    private fun evaluateInternal(
        request: RequestEnvelope,
        canonicalPath: String?,
    ): PolicyDecision {
        val config = configProvider()

        // 1. Allow-list (fail-closed in RepositoryAllowList).
        when (val allow = config.repositoryAllowList.evaluate(request.arguments.repo)) {
            is AllowListDecision.Denied -> return deny(allow.reason, allow.errorCode)
            AllowListDecision.Allowed   -> Unit
        }

        // 2. Canonical path must be present (B1 short-circuits earlier;
        //    this is the defensive re-check required by Standard 1 Stage 3).
        if (canonicalPath.isNullOrBlank()) {
            return deny("Path could not be canonicalized", ErrorCode.VALIDATION_FAILED)
        }

        // 3. Operation-level confirmation.
        val confirmation = config.confirmationRules.evaluate(request.operation.wireName)
        if (confirmation is ConfirmationDecision.Blocked) {
            return deny(
                "Operation '${request.operation.wireName}' is blocked: ${confirmation.rule.reason}",
                ErrorCode.POLICY_DENIED,
            )
        }

        // 4. Protected paths.
        val pathDecision = config.protectedPaths.evaluate(canonicalPath)
        if (pathDecision is ProtectionDecision.Protected &&
            pathDecision.rule.mode == ProtectionMode.BLOCKED
        ) {
            return deny(
                "Path '$canonicalPath' is blocked: ${pathDecision.rule.reason}",
                ErrorCode.POLICY_DENIED,
            )
        }

        // 5. Branch rules.
        var branchRequiresApproval = false
        val branch = extractBranch(request)
        if (branch != null) {
            val level = when (val bd = config.branchRules.evaluate(branch)) {
                is BranchDecision.Matched -> bd.level
                is BranchDecision.Default -> bd.level
            }
            when (level) {
                BranchProtectionLevel.BLOCKED -> return deny(
                    "Branch '$branch' is blocked by policy",
                    ErrorCode.POLICY_DENIED,
                )
                BranchProtectionLevel.REQUIRES_APPROVAL -> branchRequiresApproval = true
                BranchProtectionLevel.ALLOWED           -> Unit
            }
        }

        // 6. Aggregate the approval requirement across all three sources.
        val operationRequiresApproval = confirmation is ConfirmationDecision.RequiresConfirmation
        val pathRequiresApproval =
            pathDecision is ProtectionDecision.Protected &&
                pathDecision.rule.mode == ProtectionMode.REQUIRES_APPROVAL

        val requiresApproval =
            operationRequiresApproval || pathRequiresApproval || branchRequiresApproval

        return if (requiresApproval) {
            PolicyDecision(
                allowed = true,
                requiresApproval = true,
                reason = reasonForApproval(
                    operationRequiresApproval = operationRequiresApproval,
                    pathRequiresApproval = pathRequiresApproval,
                    branchRequiresApproval = branchRequiresApproval,
                ),
            )
        } else {
            PolicyDecision(allowed = true, requiresApproval = false)
        }
    }

    private fun extractBranch(request: RequestEnvelope): String? = when (val args = request.arguments) {
        is ReadFileArgs -> args.ref
        is WriteFileArgs -> args.branch
        is DeleteFileArgs -> args.branch
        is CreateBranchArgs -> args.newBranch
        is CreatePullRequestArgs -> args.base
        is ListBranchesArgs -> null
    }

    private fun deny(reason: String, code: ErrorCode) = PolicyDecision(
        allowed = false,
        requiresApproval = false,
        reason = reason,
        errorCode = code,
    )

    private fun reasonForApproval(
        operationRequiresApproval: Boolean,
        pathRequiresApproval: Boolean,
        branchRequiresApproval: Boolean,
    ): String = buildList {
        if (operationRequiresApproval) add("operation requires confirmation")
        if (pathRequiresApproval)      add("protected path requires approval")
        if (branchRequiresApproval)    add("branch requires approval")
    }.joinToString("; ")
}
