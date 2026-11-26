// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/BranchRules.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

/**
 * Standard 1 – Stage 1: basic glob matching on branch names.
 * Standard 1 – Stage 2: rule set and default level injected via constructor.
 * Standard 1 – Stage 3: null/blank branch, malformed patterns and empty rule
 *                       sets are all handled explicitly.
 *
 * Semantics:
 *  - When several rules match, the *strongest* protection wins:
 *        BLOCKED > REQUIRES_APPROVAL > ALLOWED
 *  - When no rule matches, [defaultLevel] is returned (constructor arg).
 */
data class BranchRule(
    val pattern: String,                    // glob over full branch name
    val level: BranchProtectionLevel,
    val reason: String = "",
) {
    init {
        require(pattern.isNotBlank()) { "Branch pattern must not be blank" }
    }
}

enum class BranchProtectionLevel {
    ALLOWED,
    REQUIRES_APPROVAL,
    BLOCKED,
}

sealed class BranchDecision {
    data class Matched(
        val rule: BranchRule,
        val level: BranchProtectionLevel,
    ) : BranchDecision()

    data class Default(
        val level: BranchProtectionLevel,
    ) : BranchDecision()
}

class BranchRules(
    rules: List<BranchRule> = DEFAULT_RULES,
    private val defaultLevel: BranchProtectionLevel = BranchProtectionLevel.ALLOWED,
) {
    private val compiled: List<Pair<Regex, BranchRule>> =
        rules.map { it.pattern.toRegex() to it }

    fun evaluate(branch: String?): BranchDecision {
        if (branch.isNullOrBlank()) {
            // Safe default: unknown branch cannot be trusted.
            return BranchDecision.Default(BranchProtectionLevel.REQUIRES_APPROVAL)
        }

        var best: BranchRule? = null
        for ((regex, rule) in compiled) {
            if (!regex.matches(branch)) continue
            best = when {
                best == null -> rule
                rule.level.rank() > best.level.rank() -> rule
                else -> best
            }
        }
        return if (best != null) {
            BranchDecision.Matched(best, best.level)
        } else {
            BranchDecision.Default(defaultLevel)
        }
    }

    private fun BranchProtectionLevel.rank(): Int = when (this) {
        BranchProtectionLevel.ALLOWED            -> 0
        BranchProtectionLevel.REQUIRES_APPROVAL  -> 1
        BranchProtectionLevel.BLOCKED            -> 2
    }

    companion object {
        /** Sensible default. Overridable via `PolicyConfig` (B4). */
        val DEFAULT_RULES: List<BranchRule> = listOf(
            BranchRule("main",       BranchProtectionLevel.REQUIRES_APPROVAL, "Primary branch"),
            BranchRule("master",     BranchProtectionLevel.REQUIRES_APPROVAL, "Primary branch"),
            BranchRule("release/*",  BranchProtectionLevel.REQUIRES_APPROVAL, "Release branch"),
            BranchRule("hotfix/*",   BranchProtectionLevel.REQUIRES_APPROVAL, "Hotfix branch"),
        )
    }
}