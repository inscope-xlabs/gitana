package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.authz.PolicyDecision
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode

/**
 * Phone-side authorization for every operation (spec 6.2). Runs before any GitHub request.
 *
 * Scaffold behaviour fails closed: everything is denied until the real evaluation is written.
 */
@Suppress("unused")
class PolicyEngine(
    private val config: PolicyConfig,
    private val allowList: RepositoryAllowList = RepositoryAllowList(config),
    private val paths: PathNormalizer = PathNormalizer(),
    private val protectedPaths: ProtectedPaths = ProtectedPaths(),
    private val branches: BranchRules = BranchRules(),
    private val confirmations: ConfirmationRules = ConfirmationRules(config.confirmation),
) {
    fun evaluate(request: RequestEnvelope): PolicyDecision =
        PolicyDecision.Deny(ErrorCode.POLICY_DENIED, "Policy engine not implemented; failing closed")
}
