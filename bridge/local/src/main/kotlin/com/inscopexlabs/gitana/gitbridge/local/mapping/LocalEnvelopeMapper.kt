package com.inscopexlabs.gitana.gitbridge.local.mapping

import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.ResponseEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreateBranchArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.CreatePullRequestArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.DeleteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ListBranchesArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolArguments
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.contracts.tool.WriteFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.version.ContractVersion
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Maps local HTTP JSON bodies to and from the canonical contracts (D3).
 *
 * - Unknown fields on the request envelope are rejected (RTB-08).
 * - RFC 3339 UTC timestamps become epoch millis at the boundary.
 * - Path [operation] must equal the envelope's `operation`.
 * - [ResponseEnvelope] with `state == SUCCEEDED` and a non-null `error` is rejected on the way out.
 */
class LocalEnvelopeMapper {

    sealed interface ParseResult {
        data class Ok(val envelope: RequestEnvelope) : ParseResult
        data class Fail(val error: BridgeError) : ParseResult
    }

    /**
     * @param pathOperation wire name from `/api/v1/tools/{operation}`
     * @param body raw JSON request body (already size-bounded by D1)
     */
    fun parseRequest(pathOperation: String, body: String): ParseResult {
        val operation = ToolOperation.fromWireName(pathOperation)
            ?: return ParseResult.Fail(validation("Unknown operation '$pathOperation'."))

        val root = try {
            JSONObject(body)
        } catch (_: JSONException) {
            return ParseResult.Fail(validation("Request body is not valid JSON."))
        }

        val keys = root.keys().asSequence().toSet()
        if (!keys.containsAll(REQUIRED_ENVELOPE_KEYS)) {
            val missing = REQUIRED_ENVELOPE_KEYS - keys
            return ParseResult.Fail(validation("Missing required field(s): ${missing.sorted().joinToString()}."))
        }
        val unknown = keys - ALLOWED_ENVELOPE_KEYS
        if (unknown.isNotEmpty()) {
            return ParseResult.Fail(validation("Unknown field(s): ${unknown.sorted().joinToString()}."))
        }

        val contractVersion = ContractVersion.parse(root.getString("contract_version"))
            ?: return ParseResult.Fail(
                BridgeError(
                    ErrorCode.UNSUPPORTED_VERSION,
                    ErrorCategory.VALIDATION,
                    400,
                    false,
                    ExecutionState.NOT_STARTED,
                    "Unsupported or malformed contract_version.",
                ),
            )
        if (!contractVersion.isCompatibleWith(ContractVersion.CURRENT)) {
            return ParseResult.Fail(
                BridgeError(
                    ErrorCode.UNSUPPORTED_VERSION,
                    ErrorCategory.VALIDATION,
                    400,
                    false,
                    ExecutionState.NOT_STARTED,
                    "Incompatible contract_version '$contractVersion'.",
                ),
            )
        }

        val requestId = root.getString("request_id")
        if (!REQUEST_ID.matches(requestId)) {
            return ParseResult.Fail(validation("request_id is malformed."))
        }

        val issuedAt = parseTimestamp(root.getString("issued_at"))
            ?: return ParseResult.Fail(validation("issued_at must be RFC 3339 UTC (…Z)."))
        val expiresAt = parseTimestamp(root.getString("expires_at"))
            ?: return ParseResult.Fail(validation("expires_at must be RFC 3339 UTC (…Z)."))
        if (expiresAt <= issuedAt) {
            return ParseResult.Fail(validation("expires_at must be after issued_at."))
        }

        val principalObj = root.getJSONObject("principal")
        val principalKeys = principalObj.keys().asSequence().toSet()
        if (principalKeys != setOf("id", "transport")) {
            return ParseResult.Fail(validation("principal must contain only id and transport."))
        }
        val principalId = principalObj.getString("id")
        if (principalId.isBlank() || principalId.length > 128) {
            return ParseResult.Fail(validation("principal.id is invalid."))
        }
        val transport = when (principalObj.getString("transport")) {
            "localhost" -> TransportKind.LOCALHOST
            "relay" -> TransportKind.RELAY
            else -> return ParseResult.Fail(validation("principal.transport is invalid."))
        }
        // Local transport only accepts localhost principals.
        if (transport != TransportKind.LOCALHOST) {
            return ParseResult.Fail(validation("Local transport requires principal.transport=localhost."))
        }

        val bodyOperation = root.getString("operation")
        if (bodyOperation != pathOperation) {
            return ParseResult.Fail(
                validation("Path operation '$pathOperation' does not match envelope operation '$bodyOperation'."),
            )
        }
        if (ToolOperation.fromWireName(bodyOperation) != operation) {
            return ParseResult.Fail(validation("operation is not one of the six canonical tools."))
        }

        val argsObj = root.getJSONObject("arguments")
        val arguments = parseArguments(operation, argsObj)
            ?: return ParseResult.Fail(validation("arguments are invalid for operation '$pathOperation'."))

        return ParseResult.Ok(
            RequestEnvelope(
                contractVersion = contractVersion,
                requestId = requestId,
                issuedAtMillis = issuedAt,
                expiresAtMillis = expiresAt,
                principal = Principal(principalId, transport),
                operation = operation,
                arguments = arguments,
            ),
        )
    }

    /** Serializes a [ResponseEnvelope]. Rejects SUCCEEDED+error (schema constraint). */
    fun toResponseJson(envelope: ResponseEnvelope): String {
        require(!(envelope.state == RequestState.SUCCEEDED && envelope.error != null)) {
            "SUCCEEDED response must not carry an error"
        }
        require(!(envelope.state == RequestState.SUCCEEDED && envelope.result == null)) {
            "SUCCEEDED response must carry a result"
        }

        val root = JSONObject()
        root.put("request_id", envelope.requestId)
        root.put("state", envelope.state.name)
        envelope.correlationId?.let { root.put("correlation_id", it) }
        envelope.result?.let { root.put("result", resultToJson(it)) }
        envelope.error?.let { root.put("error", errorToJson(it)) }
        return root.toString()
    }

    fun toAcceptedJson(requestId: String, state: RequestState): String =
        JSONObject()
            .put("request_id", requestId)
            .put("state", state.name)
            .toString()

    fun toStatusJson(requestId: String, state: RequestState, error: BridgeError? = null): String {
        val root = JSONObject()
            .put("request_id", requestId)
            .put("state", state.name)
        error?.let { root.put("error", errorToJson(it)) }
        return root.toString()
    }

    fun toHealthJson(): String = JSONObject().put("status", "ok").toString()

    fun toCapabilitiesJson(): String {
        val ops = JSONArray()
        ToolOperation.entries.forEach { ops.put(it.wireName) }
        return JSONObject()
            .put("contract_version", ContractVersion.CURRENT.toString())
            .put("operations", ops)
            .toString()
    }

    fun validationErrorJson(error: BridgeError): String =
        JSONObject().put("error", errorToJson(error)).toString()

    private fun parseArguments(operation: ToolOperation, obj: JSONObject): ToolArguments? {
        val keys = obj.keys().asSequence().toSet()
        return try {
            when (operation) {
                ToolOperation.LIST_BRANCHES -> {
                    val allowed = setOf("repo", "page", "per_page")
                    if (!keys.containsAll(setOf("repo")) || keys.any { it !in allowed }) return null
                    ListBranchesArgs(
                        repo = obj.getString("repo"),
                        page = obj.optIntOrNull("page"),
                        perPage = obj.optIntOrNull("per_page"),
                    )
                }
                ToolOperation.CREATE_BRANCH -> {
                    if (keys != setOf("repo", "base", "new_branch")) return null
                    CreateBranchArgs(
                        repo = obj.getString("repo"),
                        base = obj.getString("base"),
                        newBranch = obj.getString("new_branch"),
                    )
                }
                ToolOperation.READ_FILE -> {
                    if (keys != setOf("repo", "path", "ref")) return null
                    ReadFileArgs(
                        repo = obj.getString("repo"),
                        path = obj.getString("path"),
                        ref = obj.getString("ref"),
                    )
                }
                ToolOperation.WRITE_FILE -> {
                    if (keys != setOf("repo", "path", "content", "branch", "message")) return null
                    WriteFileArgs(
                        repo = obj.getString("repo"),
                        path = obj.getString("path"),
                        content = obj.getString("content"),
                        branch = obj.getString("branch"),
                        message = obj.getString("message"),
                    )
                }
                ToolOperation.DELETE_FILE -> {
                    if (keys != setOf("repo", "path", "branch", "message")) return null
                    DeleteFileArgs(
                        repo = obj.getString("repo"),
                        path = obj.getString("path"),
                        branch = obj.getString("branch"),
                        message = obj.getString("message"),
                    )
                }
                ToolOperation.CREATE_PULL_REQUEST -> {
                    val allowed = setOf("repo", "head", "base", "title", "body")
                    if (!keys.containsAll(setOf("repo", "head", "base", "title")) ||
                        keys.any { it !in allowed }
                    ) {
                        return null
                    }
                    CreatePullRequestArgs(
                        repo = obj.getString("repo"),
                        head = obj.getString("head"),
                        base = obj.getString("base"),
                        title = obj.getString("title"),
                        body = if (obj.has("body")) obj.getString("body") else null,
                    )
                }
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun resultToJson(result: ToolResult): JSONObject = when (result) {
        is ToolResult.BranchList -> JSONObject()
            .put("branches", JSONArray(result.branches))
            .put("has_more", result.hasMore)
        is ToolResult.BranchCreated -> JSONObject()
            .put("ref", result.ref)
            .put("sha", result.sha)
        is ToolResult.FileContent -> JSONObject()
            .put("path", result.path)
            .put("ref", result.ref)
            .put("sha", result.sha)
            .put("content", result.content)
        is ToolResult.FileWritten -> JSONObject()
            .put("path", result.path)
            .put("commit_sha", result.commitSha)
        is ToolResult.FileDeleted -> JSONObject()
            .put("path", result.path)
            .put("commit_sha", result.commitSha)
        is ToolResult.PullRequestCreated -> JSONObject()
            .put("number", result.number)
            .put("url", result.url)
            .put("already_existed", result.alreadyExisted)
            .also { if (result.note != null) it.put("note", result.note) }
    }

    private fun errorToJson(error: BridgeError): JSONObject {
        val o = JSONObject()
            .put("code", error.code.name)
            .put("category", error.category.name)
            .put("retryable", error.retryable)
            .put("execution_state", error.executionState.name)
            .put("message", error.message)
        error.httpStatus?.let { o.put("http_status", it) }
        error.correlationId?.let { o.put("correlation_id", it) }
        return o
    }

    private fun parseTimestamp(text: String): Long? {
        if (!TIMESTAMP.matches(text)) return null
        return try {
            Instant.parse(text).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun validation(message: String) = BridgeError(
        ErrorCode.VALIDATION_FAILED,
        ErrorCategory.VALIDATION,
        400,
        false,
        ExecutionState.NOT_STARTED,
        message,
    )

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private companion object {
        val REQUIRED_ENVELOPE_KEYS = setOf(
            "contract_version", "request_id", "issued_at", "expires_at",
            "principal", "operation", "arguments",
        )
        val ALLOWED_ENVELOPE_KEYS = REQUIRED_ENVELOPE_KEYS
        val REQUEST_ID = Regex("^[A-Za-z0-9._:-]{8,128}$")
        val TIMESTAMP = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z$")
    }
}
