// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/RepositoryAllowList.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.ErrorCode

/**
 * Standard 1 – Stage 1: Happy‑path evaluation for exact and wildcard patterns.
 * Standard 1 – Stage 2: Entries are pre‑compiled to Regex once and held in an
 *                       immutable list; patterns are injected via the constructor.
 * Standard 1 – Stage 3: Every unsafe input (null, blank, whitespace, malformed,
 *                       empty allow‑list) produces a typed [AllowListDecision.Denied].
 *
 * Security posture: **fail‑closed**. An empty allow‑list denies every repository.
 * A pattern that cannot match a well‑formed `owner/repo` string is impossible by
 * construction (the entry constructor throws).
 */
sealed class AllowListDecision {

    /** The repository is allowed to be operated on. */
    object Allowed : AllowListDecision()

    /** The repository is not allowed. [reason] is safe to surface to the caller. */
    data class Denied(
        val reason: String,
        val errorCode: ErrorCode = ErrorCode.POLICY_VIOLATION,
    ) : AllowListDecision()
}

/**
 * One allow‑list pattern. Accepted forms:
 *   - `*`            → matches any `owner/repo`
 *   - `owner/repo`   → exact match (case‑insensitive, GitHub semantics)
 *   - `owner/*`      → any repository under `owner`
 *   - `*&#47;repo`       → that repository under any owner
 *   - `*&#47;*`         → equivalent to `*`
 */
data class RepositoryAllowListEntry(
    val pattern: String,
    val note: String = "",
) {
    init {
        require(pattern.isNotBlank()) { "Allow-list pattern must not be blank" }
        if (pattern != "*") {
            val parts = pattern.split('/')
            require(parts.size == 2) {
                "Allow-list pattern must be '*' or 'owner/repo', was '$pattern'"
            }
            require(parts[0].isNotBlank() && parts[1].isNotBlank()) {
                "Allow-list pattern segments must not be blank: '$pattern'"
            }
        }
    }
}

class RepositoryAllowList(
    entries: List<RepositoryAllowListEntry> = emptyList(),
) {

    // Compile each pattern to a regex once at construction (Stage 2).
    private val compiled: List<Pair<Regex, RepositoryAllowListEntry>> =
        entries.map { compile(it.pattern) to it }

    fun isEmpty(): Boolean = compiled.isEmpty()

    /**
     * Evaluates [repoFullName] against the configured patterns.
     *
     * Pre‑conditions enforced here rather than relying on the caller:
     *  - null / blank                                → Denied
     *  - any whitespace                              → Denied
     *  - not exactly `<owner>/<repo>`                → Denied
     *  - empty allow‑list                            → Denied
     */
    fun evaluate(repoFullName: String?): AllowListDecision {
        // --- Stage 3: input validation -----------------------------------
        if (repoFullName.isNullOrBlank()) {
            return AllowListDecision.Denied("Repository name is null or blank")
        }
        if (repoFullName.any { it.isWhitespace() }) {
            return AllowListDecision.Denied("Repository name contains whitespace")
        }
        val parts = repoFullName.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return AllowListDecision.Denied(
                "Repository name is not in 'owner/repo' format: '$repoFullName'"
            )
        }

        // --- Stage 1 / 2: pattern match ----------------------------------
        // Fail‑closed: if `compiled` is empty, the loop does nothing and we
        // fall through to Denied. No branch here can silently allow.
        for ((regex, _) in compiled) {
            if (regex.matches(repoFullName)) return AllowListDecision.Allowed
        }

        return AllowListDecision.Denied(
            "Repository '$repoFullName' is not present in the allow-list"
        )
    }

    // ------------------------------------------------------------------
    // Stage 3: pattern compilation rejects anything that would not match
    // a well-formed `owner/repo` string.
    // ------------------------------------------------------------------
    private fun compile(pattern: String): Regex {
        if (pattern == "*") {
            return Regex("^[^/]+/[^/]+$", RegexOption.IGNORE_CASE)
        }
        val parts = pattern.split('/')
        val ownerRx = if (parts[0] == "*") "[^/]+" else Regex.escape(parts[0])
        val repoRx  = if (parts[1] == "*") "[^/]+" else Regex.escape(parts[1])
        return Regex("^$ownerRx/$repoRx$", RegexOption.IGNORE_CASE)
    }

    companion object {
        /**
         * Fail‑closed default: an empty allow‑list denies every repository.
         * This is the value used by `PolicyConfig.default()` so that a
         * misconfigured or freshly installed bridge cannot act anywhere
         * until the operator explicitly lists allowed repositories.
         */
        val EMPTY = RepositoryAllowList(emptyList())
    }
}