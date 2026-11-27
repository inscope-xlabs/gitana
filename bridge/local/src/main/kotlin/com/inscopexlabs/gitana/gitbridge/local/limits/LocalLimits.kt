package com.inscopexlabs.gitana.gitbridge.local.limits

import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/**
 * Local interface limits (RTB-20). Defaults are the Appendix D starting values; accepted ranges
 * follow `local-transport.v1.json`.
 *
 * @throws IllegalArgumentException if a value is outside its contract range.
 */
data class LocalLimits(
    /** `DV-RTB-REQUEST-MAX-BYTES`. */
    val maxRequestBytes: Long = 2_097_152,
    /** `DV-RTB-RESPONSE-MAX-BYTES`. */
    val maxResponseBytes: Long = 4_194_304,
    /** `DV-RTB-TIMEOUT-LOCAL-S`. */
    val requestTimeoutSeconds: Int = 60,
    /** `DV-RTB-RATE-LOCAL`. */
    val rateLimitPerMinute: Int = 30,
    /** `DV-RTB-MAX-INFLIGHT`. */
    val maxInFlight: Int = 2,
) {
    init {
        require(maxRequestBytes in 1024L..4_194_304L) { "maxRequestBytes out of range" }
        require(maxResponseBytes in 1024L..8_388_608L) { "maxResponseBytes out of range" }
        require(requestTimeoutSeconds in 1..120) { "requestTimeoutSeconds out of range" }
        require(rateLimitPerMinute in 1..600) { "rateLimitPerMinute out of range" }
        require(maxInFlight >= 1) { "maxInFlight must be positive" }
    }

    /** Judges the declared body size from every `Content-Length` header value, before any parsing. */
    fun checkContentLength(values: List<String>): ContentLengthCheck {
        if (values.isEmpty()) return ContentLengthCheck.ACCEPTED
        val value = values.singleOrNull()
        if (value.isNullOrEmpty() || value.any { it !in '0'..'9' }) return ContentLengthCheck.MALFORMED
        // A digit string too large for a Long is certainly over the limit.
        val length = value.toLongOrNull() ?: return ContentLengthCheck.TOO_LARGE
        return if (length > maxRequestBytes) ContentLengthCheck.TOO_LARGE else ContentLengthCheck.ACCEPTED
    }
}

/** Outcome of [LocalLimits.checkContentLength]. */
enum class ContentLengthCheck { ACCEPTED, MALFORMED, TOO_LARGE }

/**
 * Thread-safe token bucket holding [perMinute] tokens, refilled continuously.
 *
 * One bucket is a global ceiling. Per-principal buckets need an authenticated principal (D2).
 */
class LocalRateLimiter(private val perMinute: Int, private val clock: ClockPort) {
    private var tokens = perMinute.toDouble()
    private var refilledAt = clock.nowMillis()

    /** Takes one token and returns 0, or returns the whole seconds to wait before retrying. */
    @Synchronized
    fun tryAcquire(): Int {
        val now = clock.nowMillis()
        // A wall clock that moves backwards must not drain or over-fill the bucket.
        val elapsed = (now - refilledAt).coerceAtLeast(0)
        refilledAt = now
        tokens = (tokens + elapsed * perMinute / MILLIS_PER_MINUTE).coerceAtMost(perMinute.toDouble())
        if (tokens >= 1) {
            tokens -= 1
            return 0
        }
        return ceil((1 - tokens) * SECONDS_PER_MINUTE / perMinute).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000.0
        const val SECONDS_PER_MINUTE = 60.0
    }
}

/** Thread-safe ceiling on requests being processed at once. Excess requests are not queued. */
class InFlightLimiter(private val maximum: Int) {
    private val active = AtomicInteger()

    /** Reserves a slot. Every `true` must be paired with one [release]. */
    fun tryAcquire(): Boolean {
        while (true) {
            val current = active.get()
            if (current >= maximum) return false
            if (active.compareAndSet(current, current + 1)) return true
        }
    }

    /** Returns a slot obtained from [tryAcquire]. */
    fun release() {
        active.decrementAndGet()
    }
}
