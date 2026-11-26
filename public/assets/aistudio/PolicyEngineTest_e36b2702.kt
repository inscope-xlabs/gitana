// bridge/core/src/test/kotlin/com/inscopexlabs/gitana/gitbridge/core/policy/PolicyEngineTest.kt
package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.ToolRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        operation: String = "update_file",
        repo: String = "acme/widgets",
        branch: String? = "feature/x",
        path: String = "src/App.kt",
    ) = ToolRequest(
        requestId = "req-test",
        operation = operation,
        repoFullName = repo,
        branch = branch,
        path = path,
        // expiresAt is not consulted by PolicyEngine.
        expiresAt = null,
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
        // Field added by B5 (see "Required changes to B4" below).
        confirmationRules = confirmationRules,
        resourceLimits = ResourceLimits.DEFAULT,
    )

    private fun engine(cfg: PolicyConfig) = PolicyEngineImpl { cfg }

    // ------------------------------------------------------------------
    // Stage 1 – happy path
    // ------------------------------------------------------------------

    @Test
    fun `read on allow-listed repo with safe path and branch is allowed without approval`() =
        runTest {
            val decision = engine(config()).evaluate(
                request(operation = "get_file", branch = "main", path = "src/App.kt"),
                canonicalPath = "src/App.kt",
            )
            assertTrue(decision.allowed)
            assertFalse(decision.requiresApproval)
        }

    // ------------------------------------------------------------------
    // Stage 3 – every rejection branch
    // ------------------------------------------------------------------

    @Test
    fun `repo not in allow-list is denied`() = runTest {
        val decision = engine(config(allowList = listOf("other/repo")))
            .evaluate(request(repo = "acme/widgets"), canonicalPath = "src/App.kt")
        assertFalse(decision.allowed)
        assertEquals(ErrorCode.POLICY_VIOLATION, decision.errorCode)
    }

    @Test
    fun `empty allow-list denies everything`() = runTest {
        val decision = engine(config(allowList = emptyList()))
            .evaluate(request(), canonicalPath = "src/App.kt")
        assertFalse(decision.allowed)
    }

    @Test
    fun `null canonical path is denied even with allow-listed repo`() = runTest {
        val decision = engine(config()).evaluate(request(), canonicalPath = null)
        assertFalse(decision.allowed)
        assertEquals(ErrorCode.INVALID_PATH, decision.errorCode)
    }

    @Test
    fun `blank canonical path is denied`() = runTest {
        val decision = engine(config()).evaluate(request(), canonicalPath = "  ")
        assertFalse(decision.allowed)
        assertEquals(ErrorCode.INVALID_PATH, decision.errorCode)
    }

    @Test
    fun `blocked operation is denied regardless of path and branch`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "force_push", branch = "main", path = "src/App.kt"),
            canonicalPath = "src/App.kt",
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun `unknown operation requires approval (fail-closed)`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "brand_new_op"),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `blank operation requires approval (fail-closed)`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "   "),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `blocked path is denied`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "get_file", path = ".git/config"),
            canonicalPath = ".git/config",
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun `blocked branch is denied`() = runTest {
        // default level is ALLOWED, so inject an explicit block rule for `main`
        val rules = BranchRules(
            rules = listOf(
                BranchRule("main", BranchProtectionLevel.BLOCKED, "Primary branch"),
            ),
        )
        val decision = engine(config(branchRules = rules)).evaluate(
            request(operation = "get_file", branch = "main"),
            canonicalPath = "src/App.kt",
        )
        assertFalse(decision.allowed)
    }

    // ------------------------------------------------------------------
    // Stage 3 – every approval branch
    // ------------------------------------------------------------------

    @Test
    fun `write operation requires approval even on safe path and branch`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "create_file", branch = "feature/x", path = "src/App.kt"),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `protected path requires approval even for read operations`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "get_file", path = ".github/workflows/ci.yml"),
            canonicalPath = ".github/workflows/ci.yml",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `protected branch requires approval even for read operations`() = runTest {
        val decision = engine(config()).evaluate(
            request(operation = "get_file", branch = "main", path = "src/App.kt"),
            canonicalPath = "src/App.kt",
        )
        assertTrue(decision.allowed)
        assertTrue(decision.requiresApproval)
    }

    @Test
    fun `approval reasons from multiple sources are concatenated`() = runTest {
        val decision = engine(config()).evaluate(
            // update_file → REQUIRED, main → REQUIRES_APPROVAL,
            // .github/workflows/** → REQUIRES_APPROVAL
            request(operation = "update_file", branch = "main",
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