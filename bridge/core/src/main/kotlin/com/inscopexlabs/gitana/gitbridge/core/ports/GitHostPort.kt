package com.inscopexlabs.gitana.gitbridge.core.ports

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult

/** Port implemented by the GitHub adapter. Throws BridgeException with a sanitized error on failure. */
interface GitHostPort {
    suspend fun execute(request: RequestEnvelope): ToolResult
}
