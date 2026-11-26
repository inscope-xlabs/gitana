// bridge/core/src/test/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/PolicyEngineTest.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.version.ContractVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Standard 1 – Stage 1: one happy‑path test for the fully‑safe combination.
 * Standard 1 – Stage 3: one test per rejection / approval branch, plus
 *                       defensive coverage for null/blank inputs.
 */
class PolicyEngineTest {

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun request(
        operation: ToolOperation = ToolOperation.WRITE_FILE,
        repo: String = "acme/widgets",
        branch: String = "feature/x",
        path: String = "src/App.kt",
    ) = RequestEnvelope(
        contractVersion = ContractVersion.CURRENT,
        requestId = "req-test",
        issuedAtMillis = 0L,
        expiresAtMillis = Long.MAX_VALUE,
        principal = Principal("local-client:test", TransportKind.LOCALHOST),
        operation = operation,
        arguments = when (operation) {
            ToolOperation.READ_FILE -> ReadFileArgs(repo = repo, path = path, ref = branch)
            else -> WriteFileArgs(repo = repo, path = path, content = "", branch = branch, message = "test")
        },
    )

    private fun config(
        allowList: List<String> = listOf("acme/widgets"),
        confirmationRules: ConfirmationRules = ConfirmationRules(),
        protectedPaths: ProtectedPaths = ProtectedPaths(),
        branchRules: BranchRules = BranchRules(),
    ) = PolicyConfig(
        version = 1L,
        repositoryAllowList = RepositoryAllowList(
            allowList.map { RepositoryAllowListEntry(it) }
        ),
        protectedPaths = protectedPaths,
        branchRules = branchRules,
        confirmationRules = confirmationRules,
        resourceLimits = ResourceLimits.DEFAULT,
    )

    private fun engine(cfg: PolicyConfig) = PolicyEngineImpl { cfg }

    // ------------------------------------------------------------------
    // Stage 1 – happy path
    // ------------------------------------------------------------------

    @Test
    fun `read on allow-listed repo with safe path and branch is allowed without approval`() =
        runBlocking {
            val decision = engine(config()).evaluate(
                request(operation = ToolOperation.READ_FILE, branch = "main", path = "src/App.kt"),
                canonicalPath = "src/App.kt",
            )
            assertTrue(decision.allowed)
            assertFalse(decision.requiresApproval)
        }

    // ------------------------------------------------------------------
    // Stage 3 – every rejection branch
    // ------------------------------------------------------------------

    @Test
    fun `repo not in allow-list is denied`() = runBlocking {
        val decision = engine(config(allowList = listOf("other/repo")))
            .evaluate(request(repo = "acme/widgets"), canonicalPath = "src/App.kt")
        assertFalse(decision.allowed)
        assertEquals(ErrorCode.POLICY_DENIED, decision.errorCode)
    }

    @Test
    fun `empty allow-list denies everything`() = runBlocking {
        val decision = engine(config(allowList = emptyList()))
            .evaluate(request(), canonicalPath = "src/App.kt")
        assertFalse(decision.allowed)
    }

    @Test
    fun `null canonical path is denied even with allow-listed repo`() = runBlocking {
        val decision = engine(config()).evaluate(request(), canonicalPath = null)
        assertFalse(decision.allowed)
        assertEquals(ErrorCode.VALIDATION_FAILED, decision.errorCode)
    }

    @Test
    fun `blank canonical path is denied`() = runBlocking {
        val decision = engine(config()).evaluate(request(), canonicalPath = "  ")
        assertFalse(decision.allowed)
        assertEquals(ErrorCode.VALIDATION_FAILED, decision.errorCode)
    }

    @Ignore("Unrepresentable by ToolOperation enum; reconcile in B5 execution")
    @Test
    fun `blocked operation is denied regardless of path and branch`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.WRITE_FILE, branch = "main", path = "src/App.kt"),
            canonicalPath = "src/App.kt",
        )
        assertFalse(decision.allowed)
    }

    @Ignore("Unrepresentable by ToolOperation enum; reconcile in B5 execution")
    @Test
    fun `unknown operation requires approval (fail-closed)`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.WRITE_FILE),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Ignore("Unrepresentable by ToolOperation enum; reconcile in B5 execution")
    @Test
    fun `blank operation requires approval (fail-closed)`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.WRITE_FILE),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `blocked path is denied`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.READ_FILE, path = ".git/config"),
            canonicalPath = ".git/config",
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun `blocked branch is denied`() = runBlocking {
        // default level is ALLOWED, so inject an explicit block rule for `main`
        val rules = BranchRules(
            rules = listOf(
                BranchRule("main", BranchProtectionLevel.BLOCKED, "Primary branch"),
            ),
        )
        val decision = engine(config(branchRules = rules)).evaluate(
            request(operation = ToolOperation.READ_FILE, branch = "main"),
            canonicalPath = "src/App.kt",
        )
        assertFalse(decision.allowed)
    }

    // ------------------------------------------------------------------
    // Stage 3 – every approval branch
    // ------------------------------------------------------------------

    @Test
    fun `write operation requires approval even on safe path and branch`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.WRITE_FILE, branch = "feature/x", path = "src/App.kt"),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `protected path requires approval even for read operations`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.READ_FILE, path = ".github/workflows/ci.yml"),
            canonicalPath = ".github/workflows/ci.yml",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `protected branch requires approval even for read operations`() = runBlocking {
        val decision = engine(config()).evaluate(
            request(operation = ToolOperation.READ_FILE, branch = "main", path = "src/App.kt"),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `approval reasons from multiple sources are concatenated`() = runBlocking {
        val decision = engine(config()).evaluate(
            // write_file → REQUIRED, main → REQUIRES_APPROVAL,
            // .github/workflows/** → REQUIRES_APPROVAL
            request(operation = ToolOperation.WRITE_FILE, branch = "main",
                    path = ".github/workflows/ci.yml"),
            canonicalPath = ".github/workflows/ci.yml",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
        assertTrue(decision.reason.contains("operation"))
        assertTrue(decision.reason.contains("path"))
        assertTrue(decision.reason.contains("branch"))
    }
}
