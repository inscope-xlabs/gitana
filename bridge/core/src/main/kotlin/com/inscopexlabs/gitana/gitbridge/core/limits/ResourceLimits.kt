package com.inscopexlabs.gitana.gitbridge.core.limits

/**
 * Resource limits (spec 10).
 *
 * PLACEHOLDER VALUES: the spec requires numeric limits to be chosen after device testing.
 * Treat every default here as provisional.
 */
data class ResourceLimits(
    val maxRequestBodyBytes: Long = 256 * 1024,
    val maxFileContentBytes: Long = 512 * 1024,
    val maxResponseBytes: Long = 1024 * 1024,
    val maxWebSocketFrameBytes: Long = 256 * 1024,
    val maxGlobalConcurrency: Int = 4,
    val maxPerRepoConcurrency: Int = 1,
    val requestLifetimeMillis: Long = 60_000,
)
