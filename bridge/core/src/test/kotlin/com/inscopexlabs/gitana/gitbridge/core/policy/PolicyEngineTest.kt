package com.inscopexlabs.gitana.gitbridge.core.policy

import com.inscopexlabs.gitana.gitbridge.contracts.authz.PolicyDecision
import com.inscopexlabs.gitana.gitbridge.contracts.authz.Principal
import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.version.ContractVersion
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    @Test
    fun scaffoldFailsClosed() {
        val request = RequestEnvelope(
            contractVersion = ContractVersion.CURRENT,
            requestId = "urn:uuid:test",
            issuedAtMillis = 0,
            expiresAtMillis = 60_000,
            principal = Principal("local-client:test", TransportKind.LOCALHOST),
            operation = ToolOperation.READ_FILE,
            arguments = ReadFileArgs("owner/repo", "README.md", "main"),
        )
        assertTrue(PolicyEngine(PolicyConfig()).evaluate(request) is PolicyDecision.Deny)
    }
}
