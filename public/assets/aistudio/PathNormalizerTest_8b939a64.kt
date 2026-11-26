// bridge/core/src/test/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/PathNormalizerTest.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Standard 1 – Stage 1: covers happy‑path normalizations.
 * Standard 1 – Stage 3: covers every rejection branch of PathNormalizer.
 */
class PathNormalizerTest {

    private val sut = PathNormalizer()

    // ---------- Stage 1: happy path -----------------------------------

    @Test
    fun `passes a simple path through unchanged`() {
        assertNormalized("src/Main.kt", "src/Main.kt")
    }

    @Test
    fun `strips leading slash`() {
        assertNormalized("/src/Main.kt", "src/Main.kt")
    }

    @Test
    fun `strips trailing slash`() {
        assertNormalized("src/Main.kt/", "src/Main.kt")
    }

    @Test
    fun `converts backslashes to forward slashes`() {
        assertNormalized("src\\nested\\Main.kt", "src/nested/Main.kt")
    }

    @Test
    fun `drops dot segments`() {
        assertNormalized("./src/./Main.kt", "src/Main.kt")
    }

    @Test
    fun `collapses repeated slashes`() {
        assertNormalized("src//nested///Main.kt", "src/nested/Main.kt")
    }

    @Test
    fun `decodes percent-encoded segments once`() {
        assertNormalized("docs/Hello%20World.md", "docs/Hello World.md")
    }

    // ---------- Stage 3: every rejection branch -----------------------

    @Test
    fun `rejects null`() {
        assertRejected(null)
    }

    @Test
    fun `rejects blank`() {
        assertRejected("   ")
    }

    @Test
    fun `rejects directory traversal`() {
        assertRejected("../etc/passwd")
    }

    @Test
    fun `rejects encoded directory traversal`() {
        assertRejected("%2e%2e/etc/passwd")
    }

    @Test
    fun `rejects oversize path`() {
        assertRejected("a/" + "b".repeat(5000))
    }

    @Test
    fun `rejects too many segments`() {
        val many = (1..200).joinToString("/") { "s" }
        assertRejected(many)
    }

    @Test
    fun `rejects oversize segment`() {
        assertRejected("a/" + "b".repeat(300))
    }

    @Test
    fun `rejects control characters`() {
        assertRejected("src/\u0007evil.kt")
    }

    @Test
    fun `rejects windows reserved name`() {
        assertRejected("src/CON.kt")
    }

    @Test
    fun `rejects path that becomes empty after normalization`() {
        assertRejected("././")
    }

    // ---------- helpers ----------------------------------------------

    private fun assertNormalized(input: String, expected: String) {
        val result = sut.normalize(input)
        assertIs<PathNormalizationResult.Normalized>(result)
        assertEquals(expected, result.path)
    }

    private fun assertRejected(input: String?) {
        val result = sut.normalize(input)
        assertIs<PathNormalizationResult.Rejected>(result)
        assertEquals(
            com.inscopexlabs.gitana.gitbridge.contracts.ErrorCode.INVALID_PATH,
            result.errorCode,
        )
    }
}