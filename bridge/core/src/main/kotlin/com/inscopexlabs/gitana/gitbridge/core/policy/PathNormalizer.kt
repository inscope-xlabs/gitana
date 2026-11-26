// bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/PathNormalizer.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import java.net.URLDecoder

/**
 * Standard 1 – Stage 1: Happy‑path normalization works for common paths.
 * Standard 1 – Stage 2: Result is exposed through a sealed type; all data is
 *                       immutable and injected via the constructor.
 * Standard 1 – Stage 3: Every unsafe edge case (null, blank, traversal,
 *                       encoded traversal, control chars, oversize, reserved
 *                       names, empty result) is rejected with a typed reason.
 */
sealed class PathNormalizationResult {

    /** The path was normalized successfully. */
    data class Normalized(val path: String) : PathNormalizationResult()

    /** The path was rejected. Reason is user‑visible. */
    data class Rejected(
        val reason: String,
        val errorCode: ErrorCode = ErrorCode.VALIDATION_FAILED,
    ) : PathNormalizationResult()
}

/**
 * Canonicalizes repository‑relative paths coming in from the bridge protocol.
 *
 * Rules:
 *  - `\` is converted to `/`
 *  - Leading and trailing `/` are stripped
 *  - Empty segments (`//`) are collapsed
 *  - `.` segments are dropped
 *  - `..` segments are rejected (no traversal)
 *  - Single URL‑decoding pass, then re‑validation (defeats `%2e%2e`)
 *  - Maximum path length, segment count and segment length are enforced
 */
class PathNormalizer(
    private val maxPathLength: Int = 4096,
    private val maxSegmentLength: Int = 255,
    private val maxSegments: Int = 64,
) {

    fun normalize(rawPath: String?): PathNormalizationResult {
        // --- Stage 3: null / blank ---------------------------------------
        if (rawPath == null) {
            return reject("Path is null")
        }
        if (rawPath.isBlank()) {
            return reject("Path is blank")
        }
        if (rawPath.length > maxPathLength) {
            return reject("Path exceeds maximum length of $maxPathLength characters")
        }

        // --- Stage 3: control characters / null bytes --------------------
        for (ch in rawPath) {
            if (ch == '\u0000' || (ch.code in 1..31 && ch != '\t')) {
                return reject("Path contains control characters")
            }
        }

        // --- Stage 3: single URL‑decode, then re‑check ------------------
        val decoded: String = try {
            URLDecoder.decode(rawPath, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            return reject("Path contains invalid percent‑encoding")
        }
        if (decoded.contains('\u0000')) {
            return reject("Path contains a null byte after decoding")
        }

        // --- Stage 1: separator + prefix/suffix normalization -----------
        var working = decoded.replace('\\', '/')
        working = working.trimStart('/')
        working = working.trimEnd('/')

        if (working.isEmpty()) {
            return reject("Path is empty after normalization")
        }

        // --- Stage 2 / 3: per‑segment validation ------------------------
        val rawSegments = working.split('/')
        if (rawSegments.size > maxSegments) {
            return reject("Path has too many segments (${rawSegments.size} > $maxSegments)")
        }

        val cleanSegments = ArrayList<String>(rawSegments.size)
        for (segment in rawSegments) {
            when (segment) {
                ""   -> continue          // collapse `//`
                "."  -> continue          // current dir
                ".." -> return reject("Path contains directory traversal ('..')")
                else -> {
                    if (segment.length > maxSegmentLength) {
                        return reject("Path segment exceeds $maxSegmentLength characters")
                    }
                    if (isWindowsReservedName(segment)) {
                        return reject("Path contains Windows reserved name: '$segment'")
                    }
                    cleanSegments.add(segment)
                }
            }
        }

        if (cleanSegments.isEmpty()) {
            return reject("Path resolves to empty after normalization")
        }

        return PathNormalizationResult.Normalized(cleanSegments.joinToString("/"))
    }

    // --- Helpers -------------------------------------------------------

    private fun isWindowsReservedName(segment: String): Boolean {
        // Base name without extension, per Windows reserved-name rules.
        val base = segment.substringBefore('.').uppercase()
        if (base.isEmpty()) return false
        return when {
            base in WINDOWS_RESERVED_BASE -> true
            base.length == 4 && base.startsWith("COM") && base.last() in '1'..'9' -> true
            base.length == 4 && base.startsWith("LPT") && base.last() in '1'..'9' -> true
            else -> false
        }
    }

    private fun reject(reason: String) = PathNormalizationResult.Rejected(reason)

    private companion object {
        val WINDOWS_RESERVED_BASE = setOf("CON", "PRN", "AUX", "NUL")
    }
}
