package com.inscopexlabs.gitana.task.contract

/**
 * The single task contract of the Gitana framework (PCS section 9).
 *
 * Every task type MUST extend this class. Components MUST accept [TaskContract] and MUST NOT
 * define a parallel task model. A contract describes what is wanted; plans, lifecycle state,
 * outcomes and provenance are separate records that refer to [TaskCore.taskId].
 *
 * Subclass rules:
 * - Add data only through immutable properties, and expose all of it in [extensionFields].
 * - Add rules only through [onValidate]. Base rules are final and cannot be removed.
 * - Register a [TaskContractFactory] so that the type can be rebuilt.
 *
 * Construction never throws, so incomplete drafts can be held. Call [validate] before use.
 *
 * @property core fields shared by every task type.
 */
abstract class TaskContract(val core: TaskCore) {

    /** Namespaced, stable type name such as `gitana.general`. */
    abstract val typeId: String

    /** Version of this type's extension fields, starting at 1. */
    abstract val typeVersion: Int

    /** All data added by the subtype. Keys must not be blank and values must not contain secrets. */
    abstract fun extensionFields(): Map<String, TaskValue>

    /** Extra rules for a subtype. The default adds none. */
    protected open fun onValidate(): List<TaskIssue> = emptyList()

    /**
     * Checks base rules and then subtype rules. A subtype that throws is reported as an issue,
     * so validation fails closed. An empty list means the contract is ready for use.
     */
    fun validate(): List<TaskIssue> {
        val issues = TaskRules.check(core).toMutableList()
        if (!TaskRules.TYPE_ID.matches(typeId)) {
            issues += TaskIssue(TaskIssueCode.MALFORMED, "typeId", "Expected a namespaced lowercase name.")
        }
        if (typeVersion < 1) {
            issues += TaskIssue(TaskIssueCode.OUT_OF_RANGE, "typeVersion", "Must be 1 or greater.")
        }
        try {
            if (extensionFields().keys.any { it.isBlank() }) {
                issues += TaskIssue(TaskIssueCode.MISSING, "extensionFields", "Field names must not be blank.")
            }
            issues += onValidate()
        } catch (e: Exception) {
            issues += TaskIssue(
                TaskIssueCode.EXTENSION_FAILURE,
                "extension",
                "Subtype validation failed: ${e.javaClass.simpleName}.",
            )
        }
        return issues
    }

    /**
     * Reports every way in which this child task exceeds the authority of [parent].
     * An empty list means the child stays within it. This does not replace [validate]
     * or runtime policy enforcement.
     */
    fun authorityIssuesAgainst(parent: TaskContract): List<TaskIssue> =
        TaskRules.checkChild(core, parent.core)

    final override fun equals(other: Any?): Boolean =
        this === other || (
            other is TaskContract &&
                typeId == other.typeId &&
                typeVersion == other.typeVersion &&
                core == other.core &&
                extensionFields() == other.extensionFields()
            )

    final override fun hashCode(): Int =
        listOf(typeId, typeVersion, core, extensionFields()).hashCode()

    /** Omits task content so that it is not copied into logs. */
    final override fun toString(): String =
        "TaskContract(typeId=$typeId, typeVersion=$typeVersion, taskId=${core.taskId}, revision=${core.revision})"
}

/** The plain task type, for work that needs no extension fields. */
class GeneralTaskContract(core: TaskCore) : TaskContract(core) {
    override val typeId: String get() = TYPE_ID
    override val typeVersion: Int get() = TYPE_VERSION
    override fun extensionFields(): Map<String, TaskValue> = emptyMap()

    companion object {
        const val TYPE_ID = "gitana.general"
        const val TYPE_VERSION = 1

        /** Factory that rejects any extension field. */
        val FACTORY = TaskContractFactory { _, core, fields ->
            require(fields.isEmpty()) { "gitana.general accepts no extension fields." }
            GeneralTaskContract(core)
        }
    }
}
