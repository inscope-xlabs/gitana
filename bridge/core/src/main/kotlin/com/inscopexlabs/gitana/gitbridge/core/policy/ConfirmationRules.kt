// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/ConfirmationRules.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

/**
 * Standard 1 – Stage 1: `evaluate("create_file")` returns `RequiresConfirmation`
 *                       and `evaluate("get_file")` returns `Allowed` for the
 *                       default rule set.
 * Standard 1 – Stage 2: Rules are injected via the constructor, held in an
 *                       immutable map keyed by lowercase operation name; the
 *                       caller gets a sealed `ConfirmationDecision`.
 * Standard 1 – Stage 3: null/blank operations, unknown operations, and empty
 *                       rule sets are handled explicitly. A malformed rule
 *                       (blank operation) is rejected at construction time so
 *                       it can never exist at evaluation time.
 *
 * ConfirmationRules is the *operation‑level* peer to ProtectedPaths (path‑
 * level) and BranchRules (branch‑level). PolicyEngine aggregates all three.
 */
enum class ConfirmationLevel {
    /** Operation may proceed without any user interaction. */
    NONE,

    /** Operation requires an approval from the user (via ApprovalGate, B6). */
    REQUIRED,

    /** Operation is never permitted through the bridge. */
    BLOCKED,
}

data class ConfirmationRule(
    val operation: String,
    val level: ConfirmationLevel,
    val reason: String = "",
) {
    init {
        require(operation.isNotBlank()) { "Operation must not be blank" }
    }
}

sealed class ConfirmationDecision {
    object Allowed : ConfirmationDecision()
    data class RequiresConfirmation(val rule: ConfirmationRule) : ConfirmationDecision()
    data class Blocked(val rule: ConfirmationRule) : ConfirmationDecision()
}

class ConfirmationRules(
    rules: List<ConfirmationRule> = DEFAULT_RULES,
    /**
     * Level applied to operations that are not present in [rules].
     * Defaults to REQUIRED (fail‑closed) so a newly‑added upstream operation
     * cannot be silently dispatched without approval.
     */
    private val unknownOperationLevel: ConfirmationLevel = ConfirmationLevel.REQUIRED,
) {
    private val byOperation: Map<String, ConfirmationRule> =
        rules.associateBy { it.operation.lowercase() }

    fun evaluate(operation: String?): ConfirmationDecision {
        // Stage 3: null / blank operation → fail‑closed
        if (operation.isNullOrBlank()) {
            return ConfirmationDecision.RequiresConfirmation(FALLBACK_RULE)
        }
        val rule = byOperation[operation.lowercase()] ?: return when (unknownOperationLevel) {
            ConfirmationLevel.BLOCKED  -> ConfirmationDecision.Blocked(FALLBACK_RULE)
            ConfirmationLevel.REQUIRED -> ConfirmationDecision.RequiresConfirmation(FALLBACK_RULE)
            ConfirmationLevel.NONE     -> ConfirmationDecision.Allowed
        }
        return when (rule.level) {
            ConfirmationLevel.NONE     -> ConfirmationDecision.Allowed
            ConfirmationLevel.REQUIRED -> ConfirmationDecision.RequiresConfirmation(rule)
            ConfirmationLevel.BLOCKED  -> ConfirmationDecision.Blocked(rule)
        }
    }

    companion object {
        /**
         * Conservative default rule set. Overridable via `PolicyConfig` (B4).
         * NOTE: this is the *bridge‑level* default; operators can extend it
         * with their own operations without having to re‑declare the safe ones.
         */
        val DEFAULT_RULES: List<ConfirmationRule> = listOf(
            // Read operations – no confirmation.
            ConfirmationRule("read_file",           ConfirmationLevel.NONE),
            ConfirmationRule("list_branches",       ConfirmationLevel.NONE),
            ConfirmationRule("get_file",            ConfirmationLevel.NONE),
            ConfirmationRule("list_files",          ConfirmationLevel.NONE),
            ConfirmationRule("read_tree",           ConfirmationLevel.NONE),
            ConfirmationRule("get_metadata",        ConfirmationLevel.NONE),

            // Write operations – require confirmation.
            ConfirmationRule("create_branch",       ConfirmationLevel.REQUIRED, "Creates a branch"),
            ConfirmationRule("write_file",          ConfirmationLevel.REQUIRED, "Writes a file"),
            ConfirmationRule("create_file",         ConfirmationLevel.REQUIRED, "Creates a new file"),
            ConfirmationRule("update_file",         ConfirmationLevel.REQUIRED, "Modifies an existing file"),
            ConfirmationRule("delete_file",         ConfirmationLevel.REQUIRED, "Deletes a file"),
            ConfirmationRule("create_pull_request", ConfirmationLevel.REQUIRED, "Creates a pull request"),
            ConfirmationRule("batch_update",        ConfirmationLevel.REQUIRED, "Batch write"),

            // Dangerous operations – always blocked.
            ConfirmationRule("force_push",          ConfirmationLevel.BLOCKED,  "Force push is not permitted"),
        )

        private val FALLBACK_RULE = ConfirmationRule(
            operation = "<fallback>",
            level = ConfirmationLevel.REQUIRED,
            reason = "Operation requires explicit confirmation",
        )
    }
}
