// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/client/RateLimitHandler.kt
package com.inscopexlabs.gitana.gitbridge.github.client

import kotlin.math.min
import kotlin.math.pow

/**
 * Standard 1 – Stage 1: A retryable upstream failure is retried after a
 *                       bounded delay instead of failing the request
 *                       immediately.
 * Standard 1 – Stage 2: The bounds come from `DV-RTB-BACKOFF` and are
 *                       injected, so a different profile can be supplied
 *                       without touching call sites. The jitter source is
 *                       injected as well, so the behaviour is reproducible.
 * Standard 1 – Stage 3: A server-supplied `Retry-After` is honoured but
 *                       capped at the maximum backoff, the attempt count is
 *                       bounded, and a delay that would outlive the request
 *                       deadline is refused rather than slept through.
 *
 * Spec 11.1, RTB-D16, `DV-RTB-BACKOFF`.
 */
class RateLimitHandler(
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val initialDelayMillis: Long = INITIAL_DELAY_MILLIS,
    private val multiplier: Double = MULTIPLIER,
    private val maxDelayMillis: Long = MAX_DELAY_MILLIS,
    private val random: () -> Double = { Math.random() },
) {

    /**
     * Delay before the next attempt, in milliseconds.
     *
     * [attempt] is 1-based: attempt 1 is the first retry. A server-supplied
     * [retryAfterSeconds] is honoured exactly, but never beyond
     * [maxDelayMillis]; otherwise the delay is drawn from the exponential
     * window with full jitter.
     */
    fun delayMillis(attempt: Int, retryAfterSeconds: Long? = null): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            return (retryAfterSeconds * 1000L).coerceIn(0L, maxDelayMillis)
        }
        val exponential = min(
            initialDelayMillis * multiplier.pow((safeAttempt - 1).toDouble()),
            maxDelayMillis.toDouble(),
        )
        val jittered = (exponential * random()).toLong()
        return jittered.coerceIn(0L, maxDelayMillis)
    }

    /**
     * True when another attempt is allowed: attempts remain and the deadline
     * has not passed.
     */
    fun canRetry(attempt: Int, deadlineMillis: Long, nowMillis: Long): Boolean =
        attempt < maxAttempts && nowMillis < deadlineMillis

    /** True when [delayMillis] still fits inside the remaining deadline. */
    fun fitsDeadline(delayMillis: Long, deadlineMillis: Long, nowMillis: Long): Boolean =
        nowMillis + delayMillis <= deadlineMillis

    companion object {
        /** Maximum attempts, including the first (`DV-RTB-BACKOFF`). */
        const val MAX_ATTEMPTS: Int = 5

        /** Initial backoff interval in milliseconds (`DV-RTB-BACKOFF`). */
        const val INITIAL_DELAY_MILLIS: Long = 1_000L

        /** Backoff multiplier (`DV-RTB-BACKOFF`). */
        const val MULTIPLIER: Double = 2.0

        /** Maximum backoff interval in milliseconds (`DV-RTB-BACKOFF`). */
        const val MAX_DELAY_MILLIS: Long = 60_000L
    }
}
