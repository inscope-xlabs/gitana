package com.inscopexlabs.gitana.task.contract

import java.util.concurrent.ConcurrentHashMap

/** Rebuilds one task type. May throw if the fields are malformed or the version is unknown. */
fun interface TaskContractFactory {
    fun create(typeVersion: Int, core: TaskCore, fields: Map<String, TaskValue>): TaskContract
}

/** Result of rebuilding a contract from its parts. */
sealed class TaskRestoreResult {
    /** The contract was rebuilt. A non-empty [issues] list means it is only a draft. */
    data class Restored(val contract: TaskContract, val issues: List<TaskIssue>) : TaskRestoreResult()
    data class UnknownType(val typeId: String) : TaskRestoreResult()
    data class Rejected(val typeId: String, val reason: String) : TaskRestoreResult()
}

/**
 * Known task types. [GeneralTaskContract] is registered from the start. Unknown or lossy
 * input fails safely. Restoring never grants approval or authority.
 */
class TaskContractRegistry {
    private val factories = ConcurrentHashMap<String, TaskContractFactory>()

    init {
        factories[GeneralTaskContract.TYPE_ID] = GeneralTaskContract.FACTORY
    }

    /** Adds a type. Returns false if the name is invalid or already registered. */
    fun register(typeId: String, factory: TaskContractFactory): Boolean =
        TaskRules.TYPE_ID.matches(typeId) && factories.putIfAbsent(typeId, factory) == null

    /** Rebuilds a contract and confirms that no type, version, core or field data was changed. */
    fun restore(
        typeId: String,
        typeVersion: Int,
        core: TaskCore,
        fields: Map<String, TaskValue> = emptyMap(),
    ): TaskRestoreResult {
        val factory = factories[typeId] ?: return TaskRestoreResult.UnknownType(typeId)
        if (!core.contractVersion.isSupported) {
            return TaskRestoreResult.Rejected(typeId, "Unsupported contract version.")
        }
        return try {
            val contract = factory.create(typeVersion, core, fields)
            if (contract.typeId != typeId ||
                contract.typeVersion != typeVersion ||
                contract.core != core ||
                contract.extensionFields() != fields
            ) {
                TaskRestoreResult.Rejected(typeId, "The factory did not preserve the input.")
            } else {
                TaskRestoreResult.Restored(contract, contract.validate())
            }
        } catch (e: Exception) {
            TaskRestoreResult.Rejected(typeId, "The factory failed: ${e.javaClass.simpleName}.")
        }
    }
}
