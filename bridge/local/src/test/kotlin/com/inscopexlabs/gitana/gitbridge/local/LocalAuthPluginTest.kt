package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import com.inscopexlabs.gitana.gitbridge.core.ports.PrincipalResolverPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuthPluginTest {
    private val token = "a1_-".repeat(10) + "xyz"
    private val principal = Principal("local-client:test", TransportKind.LOCALHOST)
    private val log = mutableListOf<String>()
    private val checkedClasses = mutableListOf<CredentialClass>()
    private var presentedBuffer: CharArray? = null

    @Test
    fun acceptsValidBearerAndClearsTheBuffer() {
        assertEquals(LocalAuthResult.Authenticated(principal), authenticate(listOf("Bearer $token")))
        assertEquals(listOf(CredentialClass.LOCAL_CLIENT), checkedClasses)
        assertTrue(presentedBuffer!!.all { it == '\u0000' })
    }

    @Test
    fun onlyGetHealthIsExempt() {
        assertEquals(LocalAuthResult.Exempt, authenticate(emptyList(), path = "/api/v1/health"))
        assertRejected(ErrorCode.AUTH_FAILED, authenticate(emptyList(), "POST", "/api/v1/health"))
        assertRejected(ErrorCode.AUTH_FAILED, authenticate(emptyList(), path = "/api/v1/health/"))
        assertRejected(ErrorCode.AUTH_FAILED, authenticate(emptyList(), path = "/api/v1/capabilities"))
    }

    @Test
    fun rejectsMissingMalformedAndRepeatedHeadersWithoutAskingTheStore() {
        listOf(
            emptyList(),
            listOf(""),
            listOf(token),
            listOf("Basic $token"),
            listOf("bearer $token"),
            listOf("Bearer  $token"),
            listOf("Bearer ${token.dropLast(1)}"),
            listOf("Bearer ${token}a"),
            listOf("Bearer $token", "Bearer $token"),
        ).forEach { assertRejected(ErrorCode.AUTH_FAILED, authenticate(it)) }
        assertTrue(checkedClasses.isEmpty())
    }

    @Test
    fun rejectsWrongUnknownAndCrossTransportCredentials() {
        val header = listOf("Bearer $token")
        assertRejected(ErrorCode.AUTH_FAILED, authenticate(header, valid = false))
        assertRejected(ErrorCode.AUTH_FAILED, authenticate(header, resolved = null))
        val relay = Principal("relay:test", TransportKind.RELAY)
        assertRejected(ErrorCode.AUTH_FAILED, authenticate(header, resolved = relay))
    }

    @Test
    fun rejectsCredentialsInTheQueryStringEvenWithAValidHeader() {
        listOf(
            "access_token=x",
            "Token=x",
            "a=1&authorization=x",
            "q=$token",
            token,
            "q=Bearer%20x",
            "access%5Ftoken=x",
        ).forEach {
            val result = authenticate(listOf("Bearer $token"), query = it)
            assertRejected(ErrorCode.VALIDATION_FAILED, result)
        }
        assertRejected(
            ErrorCode.VALIDATION_FAILED,
            authenticate(emptyList(), path = "/api/v1/health", query = "token=x"),
        )
        assertRejected(ErrorCode.VALIDATION_FAILED, authenticate(emptyList(), query = "q=%zz"))
    }

    @Test
    fun storeFailureFailsClosedAndLogsNoSecret() {
        val result = authenticate(listOf("Bearer $token"), failure = IllegalStateException(token))
        assertRejected(ErrorCode.INTERNAL, result)
        assertTrue(log.isNotEmpty())
        assertTrue(log.none { token in it })
        assertTrue(presentedBuffer!!.all { it == '\u0000' })
    }

    private fun assertRejected(code: ErrorCode, result: LocalAuthResult) {
        assertTrue("$result", result is LocalAuthResult.Rejected)
        assertEquals(code, (result as LocalAuthResult.Rejected).error.code)
    }

    private fun authenticate(
        headers: List<String>,
        method: String = "GET",
        path: String = "/api/v1/requests/x",
        query: String = "",
        valid: Boolean = true,
        resolved: Principal? = principal,
        failure: Exception? = null,
    ): LocalAuthResult {
        val credentials = object : CredentialStorePort {
            override fun <T> withGitHubToken(block: (CharArray) -> T): T = error("not used")

            override fun verify(credentialClass: CredentialClass, presented: CharArray): Boolean {
                checkedClasses.add(credentialClass)
                presentedBuffer = presented
                failure?.let { throw it }
                return valid && String(presented) == token
            }

            override fun revoke(credentialClass: CredentialClass) = Unit
        }
        val principals = object : PrincipalResolverPort {
            override fun resolve(transport: TransportKind, subjectId: String): Principal? = resolved
        }
        val plugin = LocalAuthPlugin(credentials, principals, { log.add(it) }, Dispatchers.Unconfined)
        return runBlocking { plugin.authenticate(method, path, headers, query) }
    }
}
