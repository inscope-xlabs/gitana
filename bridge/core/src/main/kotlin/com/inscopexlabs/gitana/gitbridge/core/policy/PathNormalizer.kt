package com.inscopexlabs.gitana.gitbridge.core.policy

/**
 * Canonical path normalization (spec 7.2): must reject traversal, ambiguous encodings, invalid
 * separators and policy bypasses. Prefix-only string checks are insufficient.
 *
 * Scaffold behaviour fails closed: every path is rejected until this is implemented.
 */
class PathNormalizer {
    /** Returns the canonical repository-relative path, or null if the path must be rejected. */
    fun normalize(raw: String): String? = null
}
