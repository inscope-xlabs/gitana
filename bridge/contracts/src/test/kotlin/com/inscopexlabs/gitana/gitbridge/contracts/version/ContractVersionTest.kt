package com.inscopexlabs.gitana.gitbridge.contracts.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractVersionTest {
    @Test
    fun parsesMajorMinor() = assertEquals(ContractVersion(1, 2), ContractVersion.parse("1.2"))

    @Test
    fun rejectsMalformed() {
        assertNull(ContractVersion.parse("1"))
        assertNull(ContractVersion.parse("a.b"))
    }

    @Test
    fun majorMismatchIsIncompatible() {
        assertTrue(ContractVersion(1, 0).isCompatibleWith(ContractVersion(1, 5)))
        assertFalse(ContractVersion(1, 0).isCompatibleWith(ContractVersion(2, 0)))
    }
}
