package com.inscopexlabs.gitana.gitbridge.local.mapping

import com.inscopexlabs.gitana.gitbridge.contracts.envelope.ResponseEnvelope
import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState
import com.inscopexlabs.gitana.gitbridge.contracts.lifecycle.RequestState
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ListBranchesArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ReadFileArgs
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolOperation
import com.inscopexlabs.gitana.gitbridge.contracts.tool.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEnvelopeMapperTest {
    private val mapper = LocalEnvelopeMapper()

    @Test
    fun parsesValidListBranchesAndReadFile() {
        val listBody = """
            {
              "contract_version": "1.0",
              "request_id": "req-list-001",
              "issued_at": "2026-11-28T00:00:00Z",
              "expires_at": "2026-11-28T00:05:00Z",
              "principal": {"id": "local-agent", "transport": "localhost"},
              "operation": "list_branches",
              "arguments": {"repo": "example-org/sandbox", "page": 1, "per_page": 20}
            }
        """.trimIndent()
        val listParsed = mapper.parseRequest("list_branches", listBody)
        assertTrue(listParsed is LocalEnvelopeMapper.ParseResult.Ok)
        val listEnv = (listParsed as LocalEnvelopeMapper.ParseResult.Ok).envelope
        assertEquals(ToolOperation.LIST_BRANCHES, listEnv.operation)
        assertEquals(ListBranchesArgs("example-org/sandbox", 1, 20), listEnv.arguments)

        val readBody = """
            {
              "contract_version": "1.0",
              "request_id": "req-read-001",
              "issued_at": "2026-11-28T00:00:00Z",
              "expires_at": "2026-11-28T00:05:00Z",
              "principal": {"id": "local-agent", "transport": "localhost"},
              "operation": "read_file",
              "arguments": {"repo": "example-org/sandbox", "path": "README.md", "ref": "main"}
            }
        """.trimIndent()
        val readParsed = mapper.parseRequest("read_file", readBody)
        assertTrue(readParsed is LocalEnvelopeMapper.ParseResult.Ok)
        val readEnv = (readParsed as LocalEnvelopeMapper.ParseResult.Ok).envelope
        assertEquals(ToolOperation.READ_FILE, readEnv.operation)
        assertEquals(ReadFileArgs("example-org/sandbox", "README.md", "main"), readEnv.arguments)
    }

    @Test
    fun rejectsPathOperationMismatch() {
        val body = """
            {
              "contract_version": "1.0",
              "request_id": "req-mismatch-001",
              "issued_at": "2026-11-28T00:00:00Z",
              "expires_at": "2026-11-28T00:05:00Z",
              "principal": {"id": "local-agent", "transport": "localhost"},
              "operation": "read_file",
              "arguments": {"repo": "example-org/sandbox", "path": "README.md", "ref": "main"}
            }
        """.trimIndent()
        val parsed = mapper.parseRequest("list_branches", body)
        assertTrue(parsed is LocalEnvelopeMapper.ParseResult.Fail)
        assertEquals(ErrorCode.VALIDATION_FAILED, (parsed as LocalEnvelopeMapper.ParseResult.Fail).error.code)
    }

    @Test
    fun rejectsUnknownEnvelopeField() {
        val body = """
            {
              "contract_version": "1.0",
              "request_id": "req-unknown-001",
              "issued_at": "2026-11-28T00:00:00Z",
              "expires_at": "2026-11-28T00:05:00Z",
              "principal": {"id": "local-agent", "transport": "localhost"},
              "operation": "list_branches",
              "arguments": {"repo": "example-org/sandbox"},
              "unexpected": true
            }
        """.trimIndent()
        val parsed = mapper.parseRequest("list_branches", body)
        assertTrue(parsed is LocalEnvelopeMapper.ParseResult.Fail)
        assertEquals(ErrorCode.VALIDATION_FAILED, (parsed as LocalEnvelopeMapper.ParseResult.Fail).error.code)
    }

    @Test
    fun rejectsInvalidRfc3339Timestamp() {
        val body = """
            {
              "contract_version": "1.0",
              "request_id": "req-time-001",
              "issued_at": "2026-11-28 00:00:00",
              "expires_at": "2026-11-28T00:05:00Z",
              "principal": {"id": "local-agent", "transport": "localhost"},
              "operation": "list_branches",
              "arguments": {"repo": "example-org/sandbox"}
            }
        """.trimIndent()
        val parsed = mapper.parseRequest("list_branches", body)
        assertTrue(parsed is LocalEnvelopeMapper.ParseResult.Fail)
        assertEquals(ErrorCode.VALIDATION_FAILED, (parsed as LocalEnvelopeMapper.ParseResult.Fail).error.code)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSucceededResponseWithError() {
        mapper.toResponseJson(
            ResponseEnvelope(
                requestId = "req-bad-001",
                state = RequestState.SUCCEEDED,
                result = ToolResult.BranchList(listOf("main"), hasMore = false),
                error = BridgeError(
                    ErrorCode.INTERNAL,
                    ErrorCategory.INTERNAL,
                    500,
                    false,
                    ExecutionState.FAILED,
                    "contradiction",
                ),
            ),
        )
    }

    @Test
    fun acceptsOnlySixCanonicalOperations() {
        val unknown = mapper.parseRequest("run_shell", "{}")
        assertTrue(unknown is LocalEnvelopeMapper.ParseResult.Fail)
        assertEquals(ErrorCode.VALIDATION_FAILED, (unknown as LocalEnvelopeMapper.ParseResult.Fail).error.code)

        val caps = mapper.toCapabilitiesJson()
        ToolOperation.entries.forEach { op ->
            assertTrue("missing ${op.wireName}", "\"${op.wireName}\"" in caps)
        }
        assertEquals(6, ToolOperation.entries.size)
    }
}
