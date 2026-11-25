package com.inscopexlabs.gitana.gitbridge.contracts.envelope

import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult

data class ResponseEnvelope(
    val requestId: String,
    val state: RequestState,
    val result: ToolResult? = null,
    val error: BridgeError? = null,
    val correlationId: String? = null,
)
