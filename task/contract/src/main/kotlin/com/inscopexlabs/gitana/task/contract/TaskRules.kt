package com.inscopexlabs.gitana.task.contract

/** Category of a validation problem. */
enum class TaskIssueCode {
    MISSING,
    MALFORMED,
    DUPLICATE,
    OUT_OF_RANGE,
    CONFLICT,
    UNSUPPORTED_VERSION,
    EXCEEDS_PARENT,
    EXTENSION_FAILURE,
}

/** One problem, located by [field]. Messages contain no task content. */
data class TaskIssue(val code: TaskIssueCode, val field: String, val message: String)

/** Base rules. Internal so that subtypes cannot replace or bypass them. */
internal object TaskRules {
    val TYPE_ID = Regex("[a-z][a-z0-9_-]*(\\.[a-z][a-z0-9_-]*)+")
    private val ID = Regex("[A-Za-z0-9._:/-]{1,256}")

    fun check(core: TaskCore): List<TaskIssue> {
        val issues = mutableListOf<TaskIssue>()
        fun add(code: TaskIssueCode, field: String, message: String) {
            issues += TaskIssue(code, field, message)
        }
        fun text(value: String, field: String) {
            if (value.isBlank()) add(TaskIssueCode.MISSING, field, "Must not be blank.")
        }
        fun id(value: String, field: String) {
            if (!ID.matches(value)) add(TaskIssueCode.MALFORMED, field, "Missing or invalid identifier.")
        }
        fun unique(ids: List<String>, field: String) {
            if (ids.size != ids.toSet().size) add(TaskIssueCode.DUPLICATE, field, "Identifiers must be unique.")
        }

        if (!core.contractVersion.isSupported) {
            add(TaskIssueCode.UNSUPPORTED_VERSION, "contractVersion", "Unsupported contract version.")
        }
        id(core.taskId, "taskId")
        id(core.boundary.id, "boundary.id")
        text(core.createdBy, "createdBy")
        text(core.title, "title")
        text(core.objective, "objective")
        if (core.revision < 1) add(TaskIssueCode.OUT_OF_RANGE, "revision", "Must be 1 or greater.")
        if (core.createdAtMillis < 0) add(TaskIssueCode.OUT_OF_RANGE, "createdAtMillis", "Must not be negative.")
        if (core.updatedAtMillis < core.createdAtMillis) {
            add(TaskIssueCode.CONFLICT, "updatedAtMillis", "Precedes creation.")
        }
        core.parentTaskId?.let {
            id(it, "parentTaskId")
            if (it == core.taskId) add(TaskIssueCode.CONFLICT, "parentTaskId", "A task cannot be its own parent.")
        }

        if (core.deliverables.isEmpty()) {
            add(TaskIssueCode.MISSING, "deliverables", "At least one deliverable is required.")
        }
        unique(core.deliverables.map { it.id }, "deliverables")
        core.deliverables.forEach {
            id(it.id, "deliverables.id")
            text(it.description, "deliverables.description")
            text(it.artifactType, "deliverables.artifactType")
        }

        if (core.acceptance.none { it.mandatory }) {
            add(TaskIssueCode.MISSING, "acceptance", "At least one mandatory criterion is required.")
        }
        unique(core.acceptance.map { it.id }, "acceptance")
        core.acceptance.forEach {
            id(it.id, "acceptance.id")
            text(it.description, "acceptance.description")
            when (val v = it.verification) {
                is Verification.Automated -> text(v.validatorId, "acceptance.validatorId")
                is Verification.HumanReview -> text(v.reviewerRole, "acceptance.reviewerRole")
            }
            it.requiredEvidence.forEach { e -> text(e, "acceptance.requiredEvidence") }
        }

        unique(core.inputs.map { it.id }, "inputs")
        core.inputs.forEach {
            id(it.id, "inputs.id")
            text(it.reference, "inputs.reference")
        }

        val authority = core.authority
        if (authority.effectiveOperations.isEmpty()) {
            add(TaskIssueCode.MISSING, "authority.allowedOperations", "No operation is permitted.")
        }
        (authority.allowedOperations + authority.forbiddenOperations).forEach {
            text(it, "authority.operations")
        }
        if (!authority.allowedOperations.containsAll(authority.approvalRequired)) {
            add(TaskIssueCode.CONFLICT, "authority.approvalRequired", "Names an operation that is not allowed.")
        }
        authority.expiresAtMillis?.let {
            if (it <= core.createdAtMillis) {
                add(TaskIssueCode.OUT_OF_RANGE, "authority.expiresAtMillis", "Must follow creation.")
            }
        }
        core.constraints.deadlineMillis?.let {
            if (it <= core.createdAtMillis) {
                add(TaskIssueCode.OUT_OF_RANGE, "constraints.deadlineMillis", "Must follow creation.")
            }
        }
        if (core.constraints.resourceLimits.any { it.key.isBlank() || it.value < 0 }) {
            add(TaskIssueCode.OUT_OF_RANGE, "constraints.resourceLimits", "Blank name or negative limit.")
        }
        return issues
    }

    fun checkChild(child: TaskCore, parent: TaskCore): List<TaskIssue> {
        val issues = mutableListOf<TaskIssue>()
        fun exceeds(condition: Boolean, field: String) {
            if (condition) {
                issues += TaskIssue(TaskIssueCode.EXCEEDS_PARENT, field, "Exceeds or does not match the parent.")
            }
        }
        // A child limit must be present and no looser whenever the parent sets one.
        fun looser(childValue: Long?, parentValue: Long?) =
            parentValue != null && (childValue == null || childValue > parentValue)

        val c = child.authority
        val p = parent.authority
        exceeds(child.parentTaskId != parent.taskId, "parentTaskId")
        exceeds(child.boundary != parent.boundary, "boundary")
        exceeds(!p.effectiveOperations.containsAll(c.effectiveOperations), "authority.allowedOperations")
        exceeds(
            !c.approvalRequired.containsAll(p.approvalRequired.intersect(c.effectiveOperations)),
            "authority.approvalRequired",
        )
        exceeds(c.networkAllowed && !p.networkAllowed, "authority.networkAllowed")
        exceeds(looser(c.expiresAtMillis, p.expiresAtMillis), "authority.expiresAtMillis")
        exceeds(
            looser(child.constraints.deadlineMillis, parent.constraints.deadlineMillis),
            "constraints.deadlineMillis",
        )
        exceeds(
            parent.constraints.resourceLimits.any { (name, max) ->
                looser(child.constraints.resourceLimits[name], max)
            },
            "constraints.resourceLimits",
        )
        exceeds(
            !child.constraints.policyReferences.containsAll(parent.constraints.policyReferences),
            "constraints.policyReferences",
        )
        exceeds(
            !child.constraints.dataHandling.containsAll(parent.constraints.dataHandling),
            "constraints.dataHandling",
        )
        return issues
    }
}
