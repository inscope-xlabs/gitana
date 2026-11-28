package com.inscopexlabs.gitana.task.contract

/** Version of the base contract. A different major version is unsupported (PCS 9.4). */
data class TaskContractVersion(val major: Int, val minor: Int) {
    val isSupported: Boolean get() = major == CURRENT.major && minor >= 0

    companion object {
        val CURRENT = TaskContractVersion(0, 1)
    }
}

/** Where the work belongs: one repository, or an explicitly declared workspace (DP-01 is open). */
sealed class TaskBoundary {
    abstract val id: String

    data class Repository(override val id: String) : TaskBoundary()
    data class Workspace(override val id: String) : TaskBoundary()
}

/** An input made available to the task. [reference] identifies it and never embeds credentials. */
data class TaskInput(val id: String, val reference: String, val description: String = "")

/** A deliverable the task must produce. */
data class TaskDeliverable(
    val id: String,
    val description: String,
    val artifactType: String,
    val format: String? = null,
    val destination: String? = null,
)

/** How an acceptance criterion is checked. */
sealed class Verification {
    data class Automated(val validatorId: String) : Verification()
    data class HumanReview(val reviewerRole: String) : Verification()
}

/** A condition for completion. A worker's success claim never satisfies it by itself. */
data class AcceptanceCriterion(
    val id: String,
    val description: String,
    val verification: Verification,
    val requiredEvidence: List<String> = emptyList(),
    val mandatory: Boolean = true,
)

/**
 * Authority granted to the task. Anything not listed in [allowedOperations] is denied, and
 * [forbiddenOperations] takes precedence.
 */
data class TaskAuthority(
    val allowedOperations: Set<String> = emptySet(),
    val forbiddenOperations: Set<String> = emptySet(),
    val approvalRequired: Set<String> = emptySet(),
    val networkAllowed: Boolean = false,
    val expiresAtMillis: Long? = null,
) {
    val effectiveOperations: Set<String> get() = allowedOperations - forbiddenOperations
}

/** Non-permission limits on the work. */
data class TaskConstraints(
    val policyReferences: List<String> = emptyList(),
    val resourceLimits: Map<String, Long> = emptyMap(),
    val deadlineMillis: Long? = null,
    val dataHandling: List<String> = emptyList(),
)

/** What a worker must support. Deliberately has no vendor or model field. */
data class WorkerRequirements(
    val capabilities: Set<String> = emptySet(),
    val environment: Set<String> = emptySet(),
)

/**
 * Fields shared by every task. New fields must be optional with a default, or the major
 * version must change. Times are epoch milliseconds.
 *
 * Collections are supplied by the caller and must not be mutated after construction.
 *
 * @property revision increases when objective, scope, outputs, criteria or authority change.
 * @property parentTaskId set for a child task created by decomposition.
 * @property originReference source of an imported or templated task.
 */
data class TaskCore(
    val taskId: String,
    val boundary: TaskBoundary,
    val createdBy: String,
    val createdAtMillis: Long,
    val title: String,
    val objective: String,
    val deliverables: List<TaskDeliverable>,
    val acceptance: List<AcceptanceCriterion>,
    val authority: TaskAuthority,
    val contractVersion: TaskContractVersion = TaskContractVersion.CURRENT,
    val revision: Int = 1,
    val updatedAtMillis: Long = createdAtMillis,
    val description: String = "",
    val disciplineProfile: String? = null,
    val scope: List<String> = emptyList(),
    val outOfScope: List<String> = emptyList(),
    val inputs: List<TaskInput> = emptyList(),
    val constraints: TaskConstraints = TaskConstraints(),
    val workerRequirements: WorkerRequirements = WorkerRequirements(),
    val parentTaskId: String? = null,
    val originReference: String? = null,
)

/** Portable value types for extension fields. */
sealed class TaskValue {
    data class Text(val value: String) : TaskValue()
    data class Whole(val value: Long) : TaskValue()
    data class Flag(val value: Boolean) : TaskValue()
    data class Items(val values: List<TaskValue>) : TaskValue()
    data class Fields(val values: Map<String, TaskValue>) : TaskValue()
}
