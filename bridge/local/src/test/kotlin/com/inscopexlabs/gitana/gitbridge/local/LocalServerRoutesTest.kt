package com.inscopexlabs.gitana.gitbridge.local

import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass
import com.inscopexlabs.gitana.gitbridge.contracts.envelope.RequestEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import com.inscopexlabs.gitana.gitbridge.core.application.AuditSink
import com.inscopexlabs.gitana.gitbridge.core.application.BridgeService
import com.inscopexlabs.gitana.gitbridge.core.application.GitHubAdapter
import com.inscopexlabs.gitana.gitbridge.core.application.InMemoryWriteCoordinator
import com.inscopexlabs.gitana.gitbridge.core.application.RequestPipeline
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate
import com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalResult
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyDecision
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort
import com.inscopexlabs.gitana.gitbridge.core.ports.CredentialStorePort
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalServerRoutesTest {
    private val validToken = "A".repeat(43)

    @Test
    fun healthCapabilitiesToolsStatusAndGuardsWorkEndToEnd() = runBlocking {
        val port = freePort()
        val bridge = fakeBridgeService()
        val server = LocalServer(
            KtorListenerFactory(clock(), {}, fakeCredentials(), bridge),
            {},
            port,
        )
        try {
            server.start()
            assertTrue("${server.state.value}", server.state.value is LocalServerState.Running)

            // Health is exempt from Authorization
            val health = request(port, "GET", "/api/v1/health", listOf("Host: 127.0.0.1:$port"))
            assertTrue(health, health.startsWith("HTTP/1.1 200"))
            assertTrue(health, """"status":"ok"""" in health)

            // Capabilities requires Bearer auth
            val capsNoAuth = request(port, "GET", "/api/v1/capabilities", listOf("Host: 127.0.0.1:$port"))
            assertTrue(capsNoAuth, capsNoAuth.startsWith("HTTP/1.1 401"))

            val caps = request(
                port,
                "GET",
                "/api/v1/capabilities",
                listOf("Host: 127.0.0.1:$port", "Authorization: Bearer $validToken"),
            )
            assertTrue(caps, caps.startsWith("HTTP/1.1 200"))
            ToolOperation.entries.forEach { op ->
                assertTrue(caps, "\"${op.wireName}\"" in caps)
            }

            // Unknown tool path returns 404
            val unknownTool = request(
                port,
                "POST",
                "/api/v1/tools/not_a_tool",
                listOf("Host: 127.0.0.1:$port", "Authorization: Bearer $validToken"),
                "{}",
            )
            assertTrue(unknownTool, unknownTool.startsWith("HTTP/1.1 404"))
            assertTrue(unknownTool, "\"code\":\"NOT_FOUND\"" in unknownTool)

            // Valid read_file returns 200 + ResponseEnvelope
            val readBody = """
                {
                  "contract_version": "1.0",
                  "request_id": "req-route-read-001",
                  "issued_at": "2026-11-28T00:00:00Z",
                  "expires_at": "2099-11-28T00:05:00Z",
                  "principal": {"id": "local-agent", "transport": "localhost"},
                  "operation": "read_file",
                  "arguments": {"repo": "example-org/sandbox", "path": "README.md", "ref": "main"}
                }
            """.trimIndent()
            val readResp = request(
                port,
                "POST",
                "/api/v1/tools/read_file",
                listOf("Host: 127.0.0.1:$port", "Authorization: Bearer $validToken"),
                readBody,
            )
            assertTrue(readResp, readResp.startsWith("HTTP/1.1 200"))
            assertTrue(readResp, "\"state\":\"SUCCEEDED\"" in readResp)

            // Status for completed request returns 200; cancel on terminal returns 409
            val statusResp = request(
                port,
                "GET",
                "/api/v1/requests/req-route-read-001",
                listOf("Host: 127.0.0.1:$port", "Authorization: Bearer $validToken"),
            )
            assertTrue(statusResp, statusResp.startsWith("HTTP/1.1 200"))
            assertTrue(statusResp, "\"state\":\"SUCCEEDED\"" in statusResp)

            val cancelConflict = request(
                port,
                "POST",
                "/api/v1/requests/req-route-read-001/cancel",
                listOf("Host: 127.0.0.1:$port", "Authorization: Bearer $validToken"),
            )
            assertTrue(cancelConflict, cancelConflict.startsWith("HTTP/1.1 409"))

            // Bad Host still 403; oversize Content-Length still 413
            val badHost = request(port, "GET", "/api/v1/health", listOf("Host: example.org"))
            assertTrue(badHost, badHost.startsWith("HTTP/1.1 403"))

            val tooLarge = request(
                port,
                "POST",
                "/api/v1/tools/read_file",
                listOf(
                    "Host: localhost",
                    "Authorization: Bearer $validToken",
                    "Content-Length: 2097153",
                ),
            )
            assertTrue(tooLarge, tooLarge.startsWith("HTTP/1.1 413"))
        } finally {
            server.stop()
        }
    }

    private fun fakeBridgeService(): BridgeService = BridgeService(
        RequestPipeline(
            policyEngine = object : PolicyEngine {
                override suspend fun evaluate(request: RequestEnvelope, canonicalPath: String?): PolicyDecision =
                    PolicyDecision(allowed = true, requiresApproval = false, reason = "ok")
            },
            approvalGate = object : ApprovalGate {
                override suspend fun requestApproval(
                    request: RequestEnvelope,
                    reason: String,
                ): ApprovalResult = ApprovalResult.APPROVED
            },
            githubAdapter = object : GitHubAdapter {
                override suspend fun dispatch(
                    request: RequestEnvelope,
                    canonicalPath: String?,
                ): ToolResult = ToolResult.FileContent(
                    path = canonicalPath ?: "README.md",
                    ref = "main",
                    sha = "abc123",
                    content = "aGVsbG8=",
                )
            },
            auditSink = object : AuditSink {
                override suspend fun record(request: RequestEnvelope, result: ToolResult) = Unit
            },
            writeCoordinator = InMemoryWriteCoordinator(),
        ),
    )

    private fun clock(): ClockPort = object : ClockPort {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }

    private fun fakeCredentials(): CredentialStorePort = object : CredentialStorePort {
        override fun <T> withGitHubToken(block: (CharArray) -> T): T = error("unused")
        override fun verify(credentialClass: CredentialClass, presented: CharArray): Boolean {
            if (credentialClass != CredentialClass.LOCAL_CLIENT) return false
            val expected = validToken.toCharArray()
            return try {
                if (presented.size != expected.size) return false
                var diff = 0
                for (i in presented.indices) {
                    diff = diff or (presented[i].code xor expected[i].code)
                }
                diff == 0
            } finally {
                expected.fill('\u0000')
            }
        }
        override fun revoke(credentialClass: CredentialClass) = Unit
    }

    private fun freePort(): Int =
        ServerSocket(0, 1, InetAddress.getByName(LoopbackBinding.IPV4)).use { it.localPort }

    private fun request(
        port: Int,
        method: String,
        path: String,
        headers: List<String>,
        body: String? = null,
    ): String = Socket(LoopbackBinding.IPV4, port).use { socket ->
        socket.soTimeout = 5_000
        val bodyBytes = body?.toByteArray(Charsets.UTF_8)
        val hasContentLength = headers.any { it.startsWith("Content-Length:", ignoreCase = true) }
        val allHeaders = buildList {
            addAll(headers)
            if (bodyBytes != null && !hasContentLength) {
                add("Content-Type: application/json")
                add("Content-Length: ${bodyBytes.size}")
            } else if (method == "POST" && !hasContentLength) {
                add("Content-Length: 0")
            }
            add("Connection: close")
        }.joinToString("") { "$it\r\n" }
        socket.getOutputStream().apply {
            write("$method $path HTTP/1.1\r\n$allHeaders\r\n".toByteArray())
            if (bodyBytes != null && !hasContentLength) write(bodyBytes)
            flush()
        }
        val reader = socket.getInputStream().bufferedReader()
        val status = reader.readLine().orEmpty()
        if (" 413" in status) status else status + "\n" + reader.readText()
    }
}
