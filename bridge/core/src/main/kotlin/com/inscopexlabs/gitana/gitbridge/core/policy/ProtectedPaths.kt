// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/ProtectedPaths.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

/**
 * Standard 1 – Stage 1: basic `glob` matching on protected path patterns.
 * Standard 1 – Stage 2: rule set is injected via constructor, immutable.
 * Standard 1 – Stage 3: null/blank paths, malformed patterns and empty rule
 *                       sets are all handled explicitly.
 *
 * A rule's [mode] tells the caller (PolicyEngine, B5) how the matching path
 * must be treated. `ProtectedPaths` itself is a pure decision function – it
 * never performs IO and never throws.
 */
data class ProtectedPathRule(
    val pattern: String,                       // glob, repo‑relative
    val reason: String,
    val mode: ProtectionMode = ProtectionMode.REQUIRES_APPROVAL,
) {
    init {
        require(pattern.isNotBlank()) { "Protected path pattern must not be blank" }
    }
}

enum class ProtectionMode {
    /** The path may be written, but the caller must obtain approval first. */
    REQUIRES_APPROVAL,

    /** The path may never be written through the bridge. */
    BLOCKED,
}

sealed class ProtectionDecision {
    object NotProtected : ProtectionDecision()
    data class Protected(val rule: ProtectedPathRule) : ProtectionDecision()
}

class ProtectedPaths(
    rules: List<ProtectedPathRule> = DEFAULT_RULES,
) {
    // Pre‑compile the globs so evaluation is a fast regex pass.
    private val compiled: List<Pair<Regex, ProtectedPathRule>> =
        rules.map { it.pattern.toRegex() to it }

    /**
     * Evaluates [normalizedPath] (output of [PathNormalizer]) against the
     * configured rules.
     *
     * If more than one rule matches, the *highest protection* wins:
     *   1. BLOCKED
     *   2. REQUIRES_APPROVAL
     * Any non‑matching input yields [ProtectionDecision.NotProtected].
     */
    fun evaluate(normalizedPath: String?): ProtectionDecision {
        if (normalizedPath.isNullOrBlank()) {
            // Stage 3: treat a null/blank path as requiring approval rather
            // than silently allowing it. This is the safe default.
            return ProtectionDecision.Protected(SAFE_FALLBACK)
        }

        var best: ProtectedPathRule? = null
        for ((regex, rule) in compiled) {
            if (!regex.matches(normalizedPath)) continue
            best = when {
                best == null -> rule
                rule.mode == ProtectionMode.BLOCKED -> rule
                best.mode == ProtectionMode.REQUIRES_APPROVAL -> best
                else -> rule
            }
        }
        return best?.let { ProtectionDecision.Protected(it) }
            ?: ProtectionDecision.NotProtected
    }

    companion object {
        /** Reasonable default. Can be overridden by `PolicyConfig` (B4). */
        val DEFAULT_RULES: List<ProtectedPathRule> = listOf(
            ProtectedPathRule(".git/**",            "Git metadata",              ProtectionMode.BLOCKED),
            ProtectedPathRule(".github/workflows/**", "CI workflow definitions", ProtectionMode.REQUIRES_APPROVAL),
            ProtectedPathRule(".github/actions/**",   "CI action definitions",   ProtectionMode.REQUIRES_APPROVAL),
            ProtectedPathRule("CODEOWNERS",           "Code ownership file",     ProtectionMode.REQUIRES_APPROVAL),
        )

        /** Applied when a null/blank path slips through. */
        private val SAFE_FALLBACK = ProtectedPathRule(
            pattern = "**",
            reason = "Unparseable path – denying by default",
            mode = ProtectionMode.BLOCKED,
        )
    }
}

// ---------------------------------------------------------------------
// Shared glob helper – used by both ProtectedPaths and BranchRules.
// ---------------------------------------------------------------------
internal fun String.toRegex(): Regex {
    val glob = this
    val sb = StringBuilder("^")
    var i = 0
    while (i < glob.length) {
        when (val c = glob[i]) {
            '*' -> {
                if (i + 1 < glob.length && glob[i + 1] == '*') {
                    sb.append(".*")
                    i += 2
                } else {
                    sb.append("[^/]*")
                    i++
                }
            }
            '?' -> { sb.append("[^/]"); i++ }
            '.', '(', ')', '+', '|', '^', '$', '@', '%',
            '[', ']', '{', '}' -> { sb.append('\\').append(c); i++ }
            else -> { sb.append(c); i++ }
        }
    }
    sb.append('$')
    return Regex(sb.toString())
}
