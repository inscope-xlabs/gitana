package com.inscopexlabs.gitana.task.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskContractTest {
    private val operations = setOf("read_file", "create_branch", "write_file", "create_pull_request")

    private val sample = TaskCore(
        taskId = "task-1",
        boundary = TaskBoundary.Repository("example-org/sandbox"),
        createdBy = "owner",
        createdAtMillis = 1_000,
        title = "Add a document",
        objective = "Propose docs/hello.md through a pull request.",
        deliverables = listOf(TaskDeliverable("pr", "Pull request", "pull_request")),
        acceptance = listOf(
            AcceptanceCriterion("review", "Owner accepts the change.", Verification.HumanReview("owner")),
        ),
        authority = TaskAuthority(operations, approvalRequired = setOf("create_pull_request")),
    )

    private class Extended(core: TaskCore, private val note: String) : TaskContract(core) {
        override val typeId = "test.extended"
        override val typeVersion = 1
        override fun extensionFields() = mapOf("note" to TaskValue.Text(note))
        override fun onValidate(): List<TaskIssue> {
            check(note != "boom")
            return emptyList()
        }
    }

    private fun child(authority: TaskAuthority) = GeneralTaskContract(
        sample.copy(taskId = "task-2", parentTaskId = "task-1", authority = authority),
    )

    @Test
    fun sampleTaskIsValid() = assertEquals(emptyList<TaskIssue>(), GeneralTaskContract(sample).validate())

    @Test
    fun incompleteDraftReportsIssues() {
        val fields = GeneralTaskContract(sample.copy(objective = " ", acceptance = emptyList()))
            .validate().map { it.field }
        assertEquals(listOf("objective", "acceptance"), fields)
    }

    @Test
    fun subtypeCannotBypassBaseRules() =
        assertTrue(Extended(sample.copy(title = ""), "ok").validate().any { it.field == "title" })

    @Test
    fun throwingSubtypeFailsClosed() = assertEquals(
        TaskIssueCode.EXTENSION_FAILURE,
        Extended(sample, "boom").validate().single().code,
    )

    @Test
    fun childWithinParentIsAccepted() {
        val narrow = child(TaskAuthority(setOf("read_file")))
        assertTrue(narrow.authorityIssuesAgainst(GeneralTaskContract(sample)).isEmpty())
    }

    @Test
    fun childCannotWidenAuthorityOrDropApproval() {
        val wide = child(TaskAuthority(operations + "delete_file", networkAllowed = true))
        val fields = wide.authorityIssuesAgainst(GeneralTaskContract(sample)).map { it.field }
        assertEquals(
            listOf("authority.allowedOperations", "authority.approvalRequired", "authority.networkAllowed"),
            fields,
        )
    }

    @Test
    fun unknownTypeIsNotRestored() = assertTrue(
        TaskContractRegistry().restore("other.type", 1, sample) is TaskRestoreResult.UnknownType,
    )

    @Test
    fun unexpectedFieldIsRejected() = assertTrue(
        TaskContractRegistry().restore(
            GeneralTaskContract.TYPE_ID, 1, sample, mapOf("x" to TaskValue.Flag(true)),
        ) is TaskRestoreResult.Rejected,
    )

    @Test
    fun unsupportedTypeVersionIsRejected() = assertTrue(
        TaskContractRegistry().restore(GeneralTaskContract.TYPE_ID, 2, sample) is TaskRestoreResult.Rejected,
    )

    @Test
    fun unsupportedContractVersionIsRejected() = assertTrue(
        TaskContractRegistry().restore(
            GeneralTaskContract.TYPE_ID, 1, sample.copy(contractVersion = TaskContractVersion(9, 0)),
        ) is TaskRestoreResult.Rejected,
    )

    @Test
    fun registeredExtensionRoundTrips() {
        val registry = TaskContractRegistry()
        val factory = TaskContractFactory { _, core, f ->
            Extended(core, (f.getValue("note") as TaskValue.Text).value)
        }
        assertTrue(registry.register("test.extended", factory))
        assertFalse(registry.register("test.extended", factory))
        val original = Extended(sample, "ok")
        val result = registry.restore("test.extended", 1, sample, original.extensionFields())
        assertEquals(original, (result as TaskRestoreResult.Restored).contract)
    }
}
