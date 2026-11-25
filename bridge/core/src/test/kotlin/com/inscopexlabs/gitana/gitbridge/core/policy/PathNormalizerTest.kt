package com.inscopexlabs.gitana.gitbridge.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

/** Executable requirements for spec 7.2. Remove @Ignore as the normalizer is implemented. */
class PathNormalizerTest {
    private val normalizer = PathNormalizer()

    @Ignore("Pending PathNormalizer implementation")
    @Test
    fun rejectsTraversal() = assertNull(normalizer.normalize("../etc/passwd"))

    @Ignore("Pending PathNormalizer implementation")
    @Test
    fun rejectsEncodedTraversal() = assertNull(normalizer.normalize("%2e%2e/secrets"))

    @Ignore("Pending PathNormalizer implementation")
    @Test
    fun rejectsBackslashSeparators() = assertNull(normalizer.normalize("src\\..\\x"))

    @Ignore("Pending PathNormalizer implementation")
    @Test
    fun collapsesDotSegments() = assertEquals("src/Main.kt", normalizer.normalize("src/./Main.kt"))

    @Ignore("Pending PathNormalizer implementation")
    @Test
    fun workflowBypassIsStillProtected() {
        val n = normalizer.normalize(".github//workflows/../workflows/ci.yml")
        assertEquals(".github/workflows/ci.yml", n)
    }
}
