# XGN-02: XGN Shared Contracts

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-02 | **Group:** A — Framework foundation
**Owner:** Gitana Architecture
**Depends on:** XGN-01 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** none outstanding at this level

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the interfaces and contract boundaries shared by transports, the gateway, the trusted runtime, the policy engine, the approval service, the execution layer and the provider adapters. It states who owns each contract, how contracts are versioned, and what a conforming implementation must expose.

This module defines the **shape** of the shared request. The lifecycle of that request is owned by XGN-06; its canonical form and digest are owned by XGN-08 and XGN-31.

**Out of scope:** transport-specific framing (XGN-10 to XGN-14, XGN-28 to XGN-30), the state machine (XGN-21), the provider operation catalogue (XGN-19), and any implementation language.

## 2. Contract catalogue

| Contract | Owner | Consumers | Purpose |
|---|---|---|---|
| **Capability Contract** | XGN-29 | Gateway, transports | How a session-scoped, single-use command capability is minted, carried, validated and consumed. |
| **Request Envelope Contract** | XGN-06 | Gateway, runtime, policy, approval, execution, audit | The canonical representation of one committed request. |
| **Draft Contract** | XGN-07 | Runtime, transports | The editable representation of a request under construction. |
| **Policy Decision Contract** | XGN-15 | Runtime, approval, execution | The result of evaluating a request against the grant and policy. |
| **Approval Contract** | XGN-16 | Runtime, execution, audit | The operator's decision, bound to a request digest. |
| **Execution Contract** | XGN-17 | Runtime, provider adapters, audit | The authorised dispatch of an approved request and its outcome. |
| **Event Contract** | XGN-25 | All components | The append-only audit event shape. |
| **Error Contract** | XGN-23 | Gateway, transports, runtime | The stable error taxonomy and code set. |
| **Capability Description Contract** | XGN-03 | Transports, agents | The machine-readable self-description of what a session may do now. |
| **Panel Contract** | XGN-14 | Transports, agents | The agent-visible reply shape. |
| **Provider Adapter Contract** | XGN-18 | Execution layer, provider adapters | Mapping an approved operation onto a provider API and back. |

Every contract listed here is defined exactly once. A module that needs behaviour from another contract MUST reference the owning module rather than restate the contract.

## 3. Contract shape conventions

- Every contract is expressed as a JSON Schema document with a stable `$id` of the form `urn:gitana:xgn:schema:<name>:<major>.<minor>`.
- Contract references resolve by `$id`, never by relative path.
- Wire field names are `snake_case`. Operations, transports and command names are `lower_snake_case`.
- A **request** rejects unknown fields. A **response** ignores unknown fields, so a minor version can add fields without breaking a consumer.
- `contract_version` MUST be a string of the form `<major>.<minor>`.
- A major version increment requires a new schema set. A minor version increment MAY add fields, MUST NOT remove or rename fields, and MUST NOT change the meaning of an existing field.
- A consumer MUST reject a request whose `contract_version` major is greater than the major it implements.
- A consumer MAY accept a request whose `contract_version` minor is greater than the minor it implements, provided it ignores unknown fields.
- Every contract MUST define its own `error` object using the Error Contract.

## 4. The Request Envelope Contract

One committed request. This is the only representation that may be evaluated for policy, presented for approval, dispatched or audited.

```json
{
  "contract_version": "1.0",
  "request_id": "req_7Qm2x0Zr4n8Vc1aBd6Ef",
  "session_id": "ses_1Ab2Cd3Ef4Gh5Ij6Kl7",
  "grant_id": "grt_9Zx8Yw7Vu6Ts5Rq4Po3",
  "step_id": "stp_4Mn5Op6",
  "principal_id": "prn_3Kq9Lr7N",
  "transport": "composed_get",
  "issued_at": "2026-11-27T16:04:11Z",
  "expires_at": "2026-11-27T18:04:11Z",
  "policy_version": "pol_2026_11_27",
  "operation": "write_file",
  "arguments": {
    "repo": "acme/app",
    "branch": "agent/task-42",
    "path": "docs/notes.md",
    "message": "Add notes",
    "content_b64": "IyBOb3Rlcwo=",
    "content_bytes": 9,
    "expect_blob_sha": "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391",
    "expect_head_sha": "3c4f5e6a7b8c9d0e1f2a3b4c5d6e7f8091a2b3c4"
  },
  "draft_digest": "sha256:3f9a...",
  "envelope_digest": "sha256:9c1e...",
  "signing_key_id": "k_2026_11"
}
```

### 4.1 Field rules

| Field | Rule |
|---|---|
| `contract_version` | Stamped by the runtime. |
| `request_id`, `session_id`, `grant_id`, `step_id`, `principal_id` | Stamped by the runtime. Never supplied by an agent. |
| `transport` | The transport the request arrived on. Informational for audit; it MUST NOT influence the policy decision or the approval. |
| `issued_at`, `expires_at` | Stamped by the runtime. `expires_at` MUST NOT exceed the session expiry. |
| `policy_version` | The version of policy in force when the request was created. |
| `operation` | The operation name from the operation catalogue (XGN-19). |
| `arguments` | The normalised arguments for that operation. Every value MUST be canonical per XGN-08. |
| `draft_digest` | Digest of the draft at commit time. See XGN-31. |
| `envelope_digest` | Digest of this complete envelope, excluding the `envelope_digest` field itself. See XGN-31. |
| `signing_key_id` | Identifies the runtime key that produced the signature. The signature itself travels in the approval record, not in the envelope, so the envelope stays canonical. |

### 4.2 The "stamped, not supplied" rule

The agent supplies only the operation and the arguments. Every other field is stamped by the runtime.

A transport MUST NOT allow an agent to influence `principal_id`, `request_id`, `session_id`, `grant_id`, `issued_at`, `expires_at`, `policy_version`, `draft_digest` or `envelope_digest`. A request that carries a value for these fields that differs from the runtime's own value MUST be rejected as `MALFORMED_REQUEST`.

This rule is what makes the transport-neutral pipeline possible. Two transports that deliver the same logical operation MUST produce envelopes that differ only in `transport`, `request_id`, timestamps and digests.

## 5. The Capability Description Contract

The machine-readable self-description of what the session may do now. Owned by XGN-03, referenced here because it is the contract an agent reads first.

```json
{
  "contract_version": "1.0",
  "session_id": "ses_1Ab2Cd3Ef4Gh5Ij6Kl7",
  "state": "ACTIVE",
  "transport": "composed_get",
  "transport_caps": ["read", "compose_query", "extend_link", "no_headers"],
  "limits": {
    "max_command_bytes": 2048,
    "max_encoded_url_chars": 900,
    "max_draft_bytes": 65536,
    "max_parts_per_field": 64,
    "max_commands_per_session": 512,
    "idempotency_window_seconds": 90,
    "capability_ttl_seconds": 600
  },
  "operations": [
    {
      "name": "write_file",
      "required": ["path", "message", "content"],
      "optional": ["expect_blob_sha", "expect_head_sha"],
      "long_fields": ["content"],
      "preconditions": ["expect_blob_sha when the path exists"],
      "mutating": true,
      "requires_approval": true
    }
  ],
  "commands": [
    {"name": "new", "params": ["op"], "states": ["HUB"]},
    {"name": "set", "params": ["field", "value"], "states": ["DRAFT", "PREVIEW"]},
    {"name": "append", "params": ["field", "data"], "states": ["DRAFT", "PREVIEW"]},
    {"name": "replace", "params": ["part", "data"], "states": ["DRAFT", "PREVIEW"]},
    {"name": "unset", "params": ["field"], "states": ["DRAFT", "PREVIEW"]},
    {"name": "preview", "params": [], "states": ["DRAFT", "PREVIEW"]},
    {"name": "show", "params": ["field", "page"], "states": ["PREVIEW"]},
    {"name": "commit", "params": ["hash"], "states": ["PREVIEW"]},
    {"name": "cancel", "params": [], "states": ["DRAFT", "PREVIEW"]},
    {"name": "status", "params": [], "states": ["LOCKED", "HUB", "WAIT", "APPROVAL", "RESULT"]},
    {"name": "batch", "params": ["ops"], "states": ["DRAFT", "PREVIEW"]}
  ],
  "errors": ["SCOPE_VIOLATION", "SEQ_INVALID", "DRAFT_HASH_MISMATCH", "RATE_LIMITED"],
  "recovery": {
    "rejected_command": "Re-issue the corrected command on the newest capability.",
    "lost_reply": "Re-send the exact same URL within the idempotency window.",
    "unknown_outcome": "Read authoritative state. Never re-commit blindly."
  }
}
```

**Rules.**

- The description MUST be generated from the same authoritative command and operation definitions the validator uses. A hand-maintained second copy is prohibited, because it will drift.
- The description MUST NOT contain a secret, a capability value, or repository content.
- The description MUST be readable by an agent with no prior instruction.
- The description MUST reflect the **current** session state. `commands[].states` lists the states in which the command is legal.

## 6. The Policy Decision Contract

```json
{
  "decision": "ALLOW",
  "policy_version": "pol_2026_11_27",
  "grant_id": "grt_9Zx8Yw7Vu6Ts5Rq4Po3",
  "request_id": "req_7Qm2x0Zr4n8Vc1aBd6Ef",
  "evaluated_at": "2026-11-27T16:04:12Z",
  "checks": [
    {"check": "repo_in_grant", "result": "PASS"},
    {"check": "branch_in_grant", "result": "PASS"},
    {"check": "path_in_grant", "result": "PASS"},
    {"check": "operation_allowed", "result": "PASS"},
    {"check": "repo_private", "result": "PASS"},
    {"check": "limits_within_grant", "result": "PASS"}
  ],
  "reasons": []
}
```

- `decision` is one of `ALLOW`, `DENY`, `REQUIRE_APPROVAL`, `RECHECK`.
- `reasons` MUST contain a stable reason code for every failed check, and MUST NOT contain content or secrets.
- The decision is made by the trusted runtime. The gateway MUST NOT produce one.

## 7. The Approval Contract

```json
{
  "approval_id": "apr_5Ts8Rw7Qu6Pn4Ml3Kj2",
  "request_id": "req_7Qm2x0Zr4n8Vc1aBd6Ef",
  "envelope_digest": "sha256:9c1e...",
  "decision": "APPROVE",
  "decided_by": "prn_3Kq9Lr7N",
  "decided_at": "2026-11-27T16:05:40Z",
  "expires_at": "2026-11-27T16:15:40Z",
  "summary_digest": "sha256:4b7d..."
}
```

- The approval MUST carry the `envelope_digest` of the request it approves. An approval without a digest is invalid.
- `decided_by` identifies the operator. The runtime MUST NOT accept an approval that carries a digest differing from the current envelope digest. See XGN-31.
- `expires_at` is set by the runtime at a short default. An expired approval MUST NOT authorise dispatch.

## 8. The Execution Contract

```json
{
  "execution_id": "exe_2Hj4Kl6Mn8",
  "request_id": "req_7Qm2x0Zr4n8Vc1aBd6Ef",
  "approval_id": "apr_5Ts8Rw7Qu6Pn4Ml3Kj2",
  "envelope_digest": "sha256:9c1e...",
  "started_at": "2026-11-27T16:05:41Z",
  "state": "COMPLETED",
  "outcome": "SUCCESS",
  "provider_reference": "refs/heads/agent/task-42",
  "result_digest": "sha256:aa12...",
  "finished_at": "2026-11-27T16:05:49Z"
}
```

- `state` is one of `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `OUTCOME_UNKNOWN`.
- `outcome` is one of `SUCCESS`, `FAILURE`, `REJECTED_BY_PROVIDER`, `OUTCOME_UNKNOWN`.
- The execution layer MUST verify `envelope_digest` against the approved envelope before dispatch. A mismatch MUST abort with `INTEGRITY_MISMATCH`.
- The execution record MUST be durable before dispatch is attempted, so that a crash mid-execution leaves a reconcilable record. See XGN-22.

## 9. The Event Contract

```json
{
  "event_id": "evt_6Yh8Ui9Op0",
  "event_type": "request.approved",
  "occurred_at": "2026-11-27T16:05:40Z",
  "session_id": "ses_1Ab2Cd3Ef4Gh5Ij6Kl7",
  "request_id": "req_7Qm2x0Zr4n8Vc1aBd6Ef",
  "actor": {"kind": "operator", "id": "prn_3Kq9Lr7N"},
  "attributes": {"envelope_digest": "sha256:9c1e..."},
  "outcome": "SUCCESS"
}
```

- `event_type` is a dotted lowercase identifier: `<object>.<past-tense-verb>`.
- `actor.kind` is one of `operator`, `agent`, `runtime`, `provider`, `gateway`.
- `attributes` MUST NOT contain content, credentials, capabilities or repository data. Digests, identifiers and counts are permitted.
- Events are append-only. A correction is a new event, never an edit.

## 10. The Error Contract

```json
{
  "error": {
    "code": "SCOPE_VIOLATION",
    "category": "POLICY",
    "retryable": false,
    "http_status": 403,
    "message": "Path is outside the granted directory.",
    "remediation": "Re-issue the command with a path inside the granted directory.",
    "details": {"field": "path"}
  }
}
```

- `code` is a stable uppercase identifier from the error catalogue in XGN-23.
- `category` is one of `SESSION`, `SEQUENCE`, `POLICY`, `VALIDATION`, `INTEGRITY`, `APPROVAL`, `EXECUTION`, `TRANSPORT`, `PROVIDER`, `RATE`, `INTERNAL`.
- `retryable` states whether an identical re-issue may succeed later without any change.
- `message` is written by Gitana, MUST NOT include content, secrets or capabilities, and MUST NOT include provider text verbatim.
- `remediation` tells the agent what to do next. It MUST NOT contain a link.
- `details` MAY contain field names, limits and digests. It MUST NOT contain values that the agent wrote, except where the agent already holds them.

## 11. The Panel Contract

```text
STATE     DRAFT  step 2 of 3
TASK      [x] create_branch  [>] write_file  [ ] create_pull_request
DRAFT     op=write_file
  repo    acme/app                  fixed
  branch  agent/task-42             fixed
  path    set, 13 bytes
  message set, 9 bytes
  content open, 9 bytes, parts: 1
LAST      ok: append seq=7
COMMANDS  base = https://gitana.example/gitana/nav/v1/s?t=<capability>
  append   &cmd=append&field=content&enc=text&data=<text>&seq=8
  preview  &cmd=preview&seq=8
  cancel   &cmd=cancel&seq=8
  limit    data up to 140 characters
```

- The panel has exactly five blocks: `STATE`, `TASK`, `DRAFT`, `LAST`, `COMMANDS`.
- Every label is generated by Gitana. No label is derived from repository content or agent-written content.
- Agent-written content appears only inside an explicit read, marked as data. Otherwise only sizes and digests appear.
- The block set and field semantics are owned by XGN-14.

## 12. Versioning and compatibility rules

| Rule | Requirement |
|---|---|
| VC-01 | A contract change that removes a field, renames a field or changes a field's meaning is a major change and requires a new schema set. |
| VC-02 | A contract change that adds an optional field or a new enumeration value is a minor change. |
| VC-03 | A consumer MUST reject a request with an unknown major `contract_version`. |
| VC-04 | A consumer MUST ignore unknown fields in a response. |
| VC-05 | A contract owner MUST publish a changelog with every version change. |
| VC-06 | A module that changes a shared contract MUST record an impact review across every dependent module before the change is accepted. |
| VC-07 | Every schema set MUST include machine-readable schemas and MUST keep them in step with the owning module. A schema and its module that disagree are a defect, and the schema wins until the module is corrected. |
| VC-08 | Every schema MUST include a set of positive and negative test vectors. |

## 13. Contract ownership and change management

- The owning module is the sole authority for a contract. Other modules reference it by module ID and section.
- A change to the Request Envelope Contract requires an impact review of XGN-06, XGN-07, XGN-08, XGN-15, XGN-16, XGN-17, XGN-21, XGN-25, XGN-26 and every transport module.
- A change to the Capability Contract requires an impact review of XGN-09, XGN-10, XGN-20, XGN-28, XGN-29 and XGN-30.
- A change to the Policy Decision Contract requires an impact review of XGN-15, XGN-16, XGN-17 and XGN-22.
- A contract change and the corresponding index update MUST land in the same change set.
- Removed or superseded requirements MUST be marked explicitly. Architectural history MUST NOT be silently deleted.

## 14. Decisions recorded

| ID | Decision | Owner |
|---|---|---|
| `XGN-DEC-060` | A breaking change requires a new schema set; an additive change is a minor version. A consumer rejects an unknown major `contract_version` and ignores unknown fields in a response. A contract change requires an impact review across every dependent module. | XGN-02 |

## 15. Related specifications

- **Depends on:** XGN-01.
- **Referenced by:** XGN-06, XGN-07, XGN-15, XGN-16, XGN-17, XGN-18, XGN-21, XGN-22, XGN-23, XGN-25, XGN-03, XGN-14 and every transport module.
- **Siblings:** the capability contract in XGN-29, the draft contract in XGN-07, the panel contract in XGN-14, the capability description contract in XGN-03.
