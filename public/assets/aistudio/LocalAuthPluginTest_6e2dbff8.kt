package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAuthPluginTest {
    private val validToken = "A".repeat(43) // 43 chars of [A-Za-z0-9_-]
    private val store = FakeCredentialStore(validToken)
    private val auth = LocalAuthPlugin(store)

    @Test
    fun happyPathAcceptsValidBearer() {
        assertEquals(
            AuthDecision.Ok,
            auth.authenticate("/api/v1/tools/read_file", listOf("Bearer $validToken"), null),
        )
    }

    @Test
    fun healthPathIsExemptWithoutHeader() {
        assertEquals(
            AuthDecision.Ok,
            auth.authenticate("/api/v1/health", emptyList(), null),
        )
        assertEquals(
            AuthDecision.Ok,
            auth.authenticate("/api/v1/health/", emptyList(), null),
        )
    }

    @Test
    fun missingAuthorizationIsUnauthorized() {
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate("/api/v1/capabilities", emptyList(), null),
        )
    }

    @Test
    fun multipleAuthorizationHeadersAreUnauthorized() {
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate(
                "/api/v1/capabilities",
                listOf("Bearer $validToken", "Bearer $validToken"),
                null,
            ),
        )
    }

    @Test
    fun wrongSchemeIsUnauthorized() {
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate("/api/v1/capabilities", listOf("Basic $validToken"), null),
        )
    }

    @Test
    fun wrongLengthIsUnauthorized() {
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate("/api/v1/capabilities", listOf("Bearer ${"A".repeat(42)}"), null),
        )
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate("/api/v1/capabilities", listOf("Bearer ${"A".repeat(44)}"), null),
        )
    }

    @Test
    fun nonMatchingTokenIsUnauthorized() {
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate("/api/v1/capabilities", listOf("Bearer ${"B".repeat(43)}"), null),
        )
    }

    @Test
    fun tokenInQueryIsRejectedEvenWithValidHeader() {
        assertEquals(
            AuthDecision.TokenInQuery,
            auth.authenticate(
                "/api/v1/capabilities",
                listOf("Bearer $validToken"),
                "token=$validToken",
            ),
        )
        assertEquals(
            AuthDecision.TokenInQuery,
            auth.authenticate(
                "/api/v1/capabilities",
                emptyList(),
                "access_token=secret",
            ),
        )
    }

    @Test
    fun invalidCharactersInTokenAreUnauthorized() {
        // '+' and '/' are standard base64, not base64url
        val bad = "A".repeat(42) + "+"
        assertEquals(
            AuthDecision.Unauthorized,
            auth.authenticate("/api/v1/capabilities", listOf("Bearer $bad"), null),
        )
    }

    private class FakeCredentialStore(private val expected: String) : CredentialStorePort {
        override fun <T> withGitHubToken(block: (CharArray) -> T): T =
            error("not used in LocalAuthPlugin tests")

        override fun verify(credentialClass: CredentialClass, presented: CharArray): Boolean {
            if (credentialClass != CredentialClass.LOCAL_CLIENT) return false
            val expectedChars = expected.toCharArray()
            return try {
                if (presented.size != expectedChars.size) return false
                var result = 0
                for (i in presented.indices) {
                    result = result or (presented[i].code xor expectedChars[i].code)
                }
                result == 0
            } finally {
                expectedChars.fill('\u0000')
            }
        }

        override fun revoke(credentialClass: CredentialClass) = Unit
    }
}