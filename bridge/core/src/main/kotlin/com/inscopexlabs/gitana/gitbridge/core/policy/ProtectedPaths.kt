package com.inscopexlabs.gitana.gitbridge.core.policy

/** Default-deny sensitive paths (spec 6.3, 7.2). */
class ProtectedPaths {
    /** Scaffold fails closed: everything is treated as protected until glob matching is implemented. */
    fun isProtected(normalizedPath: String, extraPatterns: Set<String>): Boolean = true

    companion object {
        val DEFAULT_PATTERNS: List<String> = listOf(".github/workflows/**")
    }
}
