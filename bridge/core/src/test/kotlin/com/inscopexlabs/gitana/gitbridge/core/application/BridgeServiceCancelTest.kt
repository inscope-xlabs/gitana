package com.inscopexlabs.gitana.gitbridge.core.application

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.version.ContractVersion
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalResult
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyDecision
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeServiceCancelTest {

    @Test
    fun `cancel unknown request returns NotFound`() = runTest {
        val service = BridgeService(pipeline())
        assertEquals(BridgeService.CancelResult.NotFound, service.cancel("missing-id"))
        assertEquals(BridgeService.CancelResult.NotFound, service.cancel("  "))
    }

    @Test
    fun `cancel pre-dispatch and manual review states transitions to CANCELLED`() = runTest {
        val cancellable = listOf(
            RequestState.RECEIVED,
            RequestState.AUTHORIZED,
            RequestState.AWAITING_APPROVAL,
            RequestState.MANUAL_REVIEW,
        )
        val service = BridgeService(pipeline())
        cancellable.forEachIndexed { index, state ->
            val id = "req-cancel-$index"
            service.seedStateForTest(id, state)
            assertEquals(
                "Expected $state to cancel",
                BridgeService.CancelResult.Ok(RequestState.CANCELLED),
                service.cancel(id),
            )
            assertEquals(RequestState.CANCELLED, service.getStatus(id))
        }
    }

    @Test
    fun `cancel already CANCELLED is idempotent Ok`() = runTest {
        val service = BridgeService(pipeline())
        service.seedStateForTest("req-already-cancelled", RequestState.CANCELLED)

        assertEquals(
            BridgeService.CancelResult.Ok(RequestState.CANCELLED),
            service.cancel("req-already-cancelled"),
        )
        assertEquals(RequestState.CANCELLED, service.getStatus("req-already-cancelled"))
    }

    @Test
    fun `cancel executing outcome_unknown reconciling and terminal states returns Conflict`() = runTest {
        val nonCancellable = listOf(
            RequestState.EXECUTING,
            RequestState.OUTCOME_UNKNOWN,
            RequestState.RECONCILING,
            RequestState.SUCCEEDED,
            RequestState.FAILED,
            RequestState.REJECTED,
            RequestState.EXPIRED,
        )
        val service = BridgeService(pipeline())
        nonCancellable.forEachIndexed { index, state ->
            val id = "req-conflict-$index"
            service.seedStateForTest(id, state)
            assertEquals(
                "Expected $state to return Conflict",
                BridgeService.CancelResult.Conflict(state),
                service.cancel(id),
            )
            assertEquals(state, service.getStatus(id))
        }
    }

    @Test
    fun `cancel while awaiting approval keeps CANCELLED state`() = runTest {
        val enteredApproval = CompletableDeferred<Unit>()
        val releaseApproval = CompletableDeferred<Unit>()
        val service = BridgeService(
            pipeline(
                requiresApproval = true,
                onApproval = {
                    enteredApproval.complete(Unit)
                    releaseApproval.await()
                    ApprovalResult.DENIED
                },
            ),
        )
        val request = RequestEnvelope(
            contractVersion = ContractVersion.CURRENT,
            requestId = "req-awaiting-001",
            issuedAtMillis = 1_000L,
            expiresAtMillis = Long.MAX_VALUE,
            principal = Principal("local-agent", TransportKind.LOCALHOST),
            operation = ToolOperation.WRITE_FILE,
            arguments = WriteFileArgs(
                repo = "example-org/sandbox",
                path = "docs/note.md",
                content = "aGVsbG8=",
                branch = "feature/test",
                message = "update",
            ),
        )

        val job = launch { service.submit(request) }
        enteredApproval.await()

        assertEquals(
            BridgeService.CancelResult.Ok(RequestState.CANCELLED),
            service.cancel(request.requestId),
        )
        releaseApproval.complete(Unit)
        job.join()

        assertEquals(RequestState.CANCELLED, service.getStatus(request.requestId))
    }

    @Test
    fun `cancel while executing returns Conflict`() = runTest {
        val enteredDispatch = CompletableDeferred<Unit>()
        val releaseDispatch = CompletableDeferred<Unit>()
        val service = BridgeService(
            pipeline(
                onDispatch = {
                    enteredDispatch.complete(Unit)
                    releaseDispatch.await()
                    ToolResult.FileContent("README.md", "main", "abc", "aGVsbG8=")
                },
            ),
        )
        val request = RequestEnvelope(
            contractVersion = ContractVersion.CURRENT,
            requestId = "req-executing-001",
            issuedAtMillis = 1_000L,
            expiresAtMillis = Long.MAX_VALUE,
            principal = Principal("local-agent", TransportKind.LOCALHOST),
            operation = ToolOperation.READ_FILE,
            arguments = ReadFileArgs("example-org/sandbox", "README.md", "main"),
        )

        val job = launch { service.submit(request) }
        enteredDispatch.await()

        val result = service.cancel(request.requestId)
        assertTrue(result is BridgeService.CancelResult.Conflict)
        assertEquals(RequestState.EXECUTING, (result as BridgeService.CancelResult.Conflict).state)

        releaseDispatch.complete(Unit)
        job.join()
        assertEquals(RequestState.SUCCEEDED, service.getStatus(request.requestId))
    }

    private fun pipeline(
        requiresApproval: Boolean = false,
        onApproval: suspend () -> ApprovalResult = { ApprovalResult.APPROVED },
        onDispatch: suspend () -> ToolResult = {
            ToolResult.FileContent("README.md", "main", "abc", "aGVsbG8=")
        },
    ): RequestPipeline = RequestPipeline(
        policyEngine = object : PolicyEngine {
            override suspend fun evaluate(request: RequestEnvelope, canonicalPath: String?): PolicyDecision =
                PolicyDecision(
                    allowed = true,
                    requiresApproval = requiresApproval,
                    reason = "test",
                )
        },
        approvalGate = object : ApprovalGate {
            override suspend fun requestApproval(
                request: RequestEnvelope,
                reason: String,
            ): ApprovalResult = onApproval()
        },
        githubAdapter = object : GitHubAdapter {
            override suspend fun dispatch(
                request: RequestEnvelope,
                canonicalPath: String?,
            ): ToolResult = onDispatch()
        },
        auditSink = object : AuditSink {
            override suspend fun record(request: RequestEnvelope, result: ToolResult) = Unit
        },
        writeCoordinator = InMemoryWriteCoordinator(),
    )
}
