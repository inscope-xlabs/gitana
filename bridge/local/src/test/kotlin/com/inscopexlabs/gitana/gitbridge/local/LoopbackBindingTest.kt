package com.inscopexlabs.gitana.gitbridge.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackBindingTest {
    @Test
    fun allowsOnlyLoopback() {
        assertTrue(LoopbackBinding.isAllowed("127.0.0.1"))
        assertTrue(LoopbackBinding.isAllowed("::1"))
    }

    @Test
    fun rejectsWildcardLanAndHostnames() {
        listOf("0.0.0.0", "::", "192.168.1.10", "10.0.0.2", "localhost", "").forEach {
            assertFalse("should reject '$it'", LoopbackBinding.isAllowed(it))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireLoopbackThrowsForWildcard() {
        LoopbackBinding.requireLoopback("0.0.0.0")
    }
}
