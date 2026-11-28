# XGN-23: XGN Errors, Retries and Rate Limits

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-23 | **Group:** E — Relay and reliability
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** ER-OD-01, ER-OD-02 (section 11)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module owns the **error taxonomy**, the **stable error code catalogue**, the **retry contract**, the **rate limits**, and the **FIX template** that accompanies a rejected command.

An error code is a contract between Gitana and an agent. It is stable, it is machine-readable, and it tells the agent what it may do next. The code catalogue is deliberately small and deliberately complete: a code is added when an agent needs a distinct action, and two conditions that require the same action share a code.

**In scope:** the error contract shape and the sanitisation rules, the category set, the complete code catalogue, retryability and the backoff schedule, the rate limits, the FIX template, offline behaviour, and escalation.

**Out of scope:** the transitions that produce an error (XGN-21), the remediation procedure for an unknown outcome (XGN-22), the panel rendering of the error (XGN-14), the provider-specific error semantics (XGN-18, XGN-19), and the retention of error records (XGN-25).

## 2. The error contract

Every error reply MUST use the Error Contract of XGN-02 §10.

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

### 2.1 Categories

| Category | Meaning |
|---|---|
| `SESSION` | The session's state forbids the command, or the session is not usable. |
| `SEQUENCE` | The session sequence number presented is wrong. |
| `POLICY` | The grant or the policy forbids the operation. |
| `VALIDATION` | The command is malformed, incomplete, or outside the declared limits. |
| `INTEGRITY` | A digest, a binding or an expected state does not match. |
| `APPROVAL` | The approval step blocked or closed the request. |
| `EXECUTION` | The dispatch was attempted and did not complete definitively. |
| `TRANSPORT` | The capability, the link or the channel failed. |
| `PROVIDER` | The external service refused or failed the operation. |
| `RATE` | A limit was reached. |
| `INTERNAL` | A defect in Gitana or an unexpected condition. |

### 2.2 Sanitisation

| ID | Rule |
|---|---|
| ER-01 | The `message` MUST be written by Gitana. It MUST NOT contain a capability, a token, a credential, the pairing challenge, the provider credential, or any agent-authored or repository content. |
| ER-02 | The `message` MUST NOT echo provider text verbatim. A provider's message is mapped to a stable Gitana reason code; the provider's wording MUST NOT be passed through. |
| ER-03 | The `remediation` MUST NOT contain a link, a capability, a sequence number that the runtime did not issue, or an operator-plane instruction. |
| ER-04 | The `details` object MAY contain field names, limits, counts and digests. It MUST NOT contain a value the agent wrote, except a value the agent already holds. |
| ER-05 | The error reply MUST NOT disclose the existence of another session, another operator, another grant, or an operation outside the grant. |
| ER-06 | An error MUST NOT disclose the granted scope beyond what the agent already knows from its own session. |
| ER-07 | Where the provider's text is required for diagnosis, it MUST be held in the operator-facing view and in the audit log under the retention of XGN-25, and MUST NOT appear in the agent-visible error. |
| ER-08 | The error code, the category and the `retryable` flag MUST be stable across transports. The same condition MUST produce the same code whichever transport carried the command. |

## 3. The stable error code catalogue

The catalogue is closed. A code not listed here MUST NOT be returned; a new condition MUST be mapped to an existing code or added here by a change to this module.

| Code | Category | Status | Retryable | Meaning | Agent remediation |
|---|---|---|---|---|---|
| `SESSION_LOCKED` | SESSION | 409 | no | The session exists but the pairing challenge is not confirmed on the device. | Stop. Report to the operator and wait for confirmation on the device. Do not retry the command. |
| `SESSION_SUSPENDED` | SESSION | 409 | no | The session is held for operator review. | Stop. Read `status` for the reason. Wait for the operator to resume or revoke. |
| `SESSION_EXPIRED` | SESSION | 410 | no | The session lifetime ended. | Stop. The session cannot be renewed. A new session requires the operator. |
| `SESSION_REVOKED` | SESSION | 410 | no | The operator revoked the session. | Stop. No further command is possible. |
| `STATE_INVALID` | VALIDATION | 409 | no | The command is not legal in the current session state. | Read `status` for the current state and the commands legal now. Issue a command from that list. |
| `SEQ_INVALID` | SEQUENCE | 409 | no | The session sequence number presented is wrong: it is a gap, or it is not the expected value. | Re-read the panel, take the next expected sequence number, and re-issue the command on the newest capability base. |
| `SCOPE_VIOLATION` | POLICY | 403 | no | The path, branch or repository is outside the granted scope. | Re-issue with a target inside the granted scope. If no target qualifies, stop and ask the operator. |
| `OPERATION_NOT_PERMITTED` | POLICY | 403 | no | The grant does not permit the operation. | Do not retry. The operation MUST NOT be disclosed as existing. Ask the operator for a new session if it is needed. |
| `REPO_NOT_PRIVATE` | POLICY | 403 | no | The repository is no longer private. | Stop. The session is suspended by the runtime. The operator must resolve the repository visibility. |
| `DRAFT_LIMIT` | VALIDATION | 413 | no | A size or count limit was exceeded: encoded data per command, total draft size, parts per field, or commands per session. | Shorten the value, split it differently, or `cancel` the draft. The limit that was exceeded is in `details`. |
| `DRAFT_HASH_MISMATCH` | INTEGRITY | 409 | no | The request digest presented to `commit` does not match the current draft. | Run `preview` again, take the new request digest, and re-issue `commit` on the newest capability base. |
| `DRAFT_INCOMPLETE` | VALIDATION | 422 | no | A required field is missing or the draft has not been previewed. | Run `preview`; the error names the missing field. |
| `LINK_CONSUMED` | TRANSPORT | 409 | no | The command capability presented has already been used. | Read `status`. If the command took effect, do not repeat it. If it did not, take a fresh capability base from the panel. |
| `LINK_EXPIRED` | TRANSPORT | 410 | no | The command capability is older than its 600-second lifetime. | Read `status`. Take a fresh capability base from the panel if the session is live. |
| `LINK_REUSE` | TRANSPORT | 409 | no | A consumed capability was presented again outside the idempotency window. | Stop. The session is suspended by the runtime and the operator is alerted. |
| `TURN_NOT_CLEARED` | SESSION | 409 | yes | A turn gate is not cleared. | Wait. Read `status` at the permitted interval. The operator must clear the turn on the device. |
| `RATE_LIMITED` | RATE | 429 | yes | A rate limit was reached. | Wait for the interval in `details`, then re-issue the identical command. |
| `DEVICE_OFFLINE` | TRANSPORT | 503 | yes | The trusted runtime is unreachable. | Wait, then read `status`. Do not re-send a mutating command until the authoritative state is known. |
| `POLICY_DENIED` | POLICY | 403 | no | The policy engine denied the request. | Stop. The reasons are in `details` as stable reason codes. Ask the operator. |
| `VALIDATION_FAILED` | VALIDATION | 422 | no | A parameter is missing, repeated, out of order, of the wrong type, or outside its constraint. | Correct the parameter named in `details` and re-issue on the newest capability base. |
| `MALFORMED_REQUEST` | VALIDATION | 400 | no | The delivery does not conform to the contract: unknown field, wrong method, wrong route, unparseable body, or a runtime-stamped field the agent supplied. | Rebuild the request using the capability description. Do not resend the same bytes. |
| `CONTRACT_VERSION_UNSUPPORTED` | VALIDATION | 400 | no | The contract major version is greater than the one the runtime implements. | Stop. The agent MUST NOT retry with a different version on its own. |
| `INTEGRITY_MISMATCH` | INTEGRITY | 409 | no | A digest, a binding or an expected provider state does not match the approved envelope. | Stop. The dispatch was aborted. Reconciliation decides the outcome (XGN-22). |
| `APPROVAL_REQUIRED` | APPROVAL | 202 | no | The operation is mutating and awaits the operator's decision. | Wait. Read `status` after the operator has been prompted. Do not re-commit. |
| `APPROVAL_EXPIRED` | APPROVAL | 409 | no | The approval lapsed before dispatch. | The request is closed. Rebuild the request and commit again to obtain a new approval. |
| `REJECTED` | APPROVAL | 403 | no | The operator declined the request. | Stop for this step. Report the outcome. The step stays current; a new attempt requires a new commit and a new approval. |
| `OUTCOME_UNKNOWN` | EXECUTION | 202 | no | The operation was dispatched and its result is not known. | Do not re-send. The runtime is reconciling by reads. Read `status` until the outcome is definitive. |
| `EXECUTION_FAILED` | EXECUTION | 502 | no | The dispatch was attempted and failed definitively, for a reason that is not the provider's. | Read `status` for the recorded reason. Rebuilding the request requires a new commit and a new approval. |
| `PROVIDER_ERROR` | PROVIDER | 502 | yes | The external service returned an error that may be transient. | Wait for the backoff interval, then re-issue the identical command only if the runtime records that nothing was dispatched. Otherwise reconcile. |
| `PROVIDER_CONFLICT` | PROVIDER | 409 | no | The external service refused because the expected state has moved on: a stale blob digest, a moved branch head, or a duplicate object. | Read the current state, reconcile the expected preconditions with it, and compose a new request. Do not repeat the same request. |
| `PRECONDITION_FAILED` | EXECUTION | 409 | no | A precondition in the envelope did not hold at dispatch, so the operation was not applied. | Read the current state and recompose the request with fresh preconditions. |
| `INTERNAL_ERROR` | INTERNAL | 500 | yes | A defect or an unexpected condition in Gitana. | Wait for the backoff interval, then read `status`. Report to the operator if it recurs. |
| `UNSUPPORTED_TRANSPORT` | TRANSPORT | 400 | no | The transport in use is not available for this session. | Ask the operator to enable the transport or to move the session to another transport. A transport change MUST NOT widen scope (XGN-DEC-030). |

## 4. Retryability

**ER-09**: `retryable` states whether an identical re-issue may succeed later with no change by the agent. Only the codes marked retryable in section 3 are retryable.

**ER-10**: A retry MUST be byte-identical to the original delivery: the same command, the same parameters, the same session sequence number, the same body digest. A changed command is a new command and MUST be issued on a fresh capability base.

**ER-11**: A retry MUST NOT repeat a side effect. It is permitted only when the runtime's record shows that the command was not dispatched, or when the command is a read. A command whose dispatch status is unknown MUST NOT be retried; it MUST be reconciled (XGN-22).

**ER-12**: A retry MUST NOT be issued for `OUTCOME_UNKNOWN`, `EXECUTION_FAILED`, `PROVIDER_CONFLICT`, `PRECONDITION_FAILED`, `REJECTED`, `APPROVAL_EXPIRED` or `INTEGRITY_MISMATCH`. For those codes the remediation is to read authoritative state or to compose a new request.

### 4.1 Backoff

| Attempt | Wait before the attempt |
|---|---|
| 1 | 0 s (the first attempt) |
| 2 | 2 s |
| 3 | 4 s |
| 4 | 8 s |
| 5 | 16 s |

**ER-13**: The initial wait is 2 seconds. The wait doubles after each failure, and is capped at 60 seconds. The maximum number of attempts is 5, including the first.

**ER-14**: After the fifth attempt the agent MUST stop retrying and MUST read `status`. It MUST NOT continue on a fixed interval.

**ER-15**: The wait applies per command. A new command does not inherit the remaining attempts of a previous one.

**ER-16**: A rate-limited command MUST be retried after the interval given in `details`, and that interval MUST NOT be shorter than the interval the rate limit requires. When the two differ, the longer interval applies.

**ER-17**: A `RATE_LIMITED` rejection MUST NOT consume the session's command budget and MUST NOT advance the session sequence number (XGN-20 GW-48).

## 5. Rate limits

The limits below are authoritative for the framework. The gateway enforces the ingress-side limits and the runtime re-checks them (XGN-20 §8).

| ID | Limit | Value | Scope | On breach |
|---|---|---|---|---|
| ER-18 | Status polls | 1 per 5 seconds | per session | `RATE_LIMITED`. The panel always states the interval. |
| ER-19 | Status polls | 60 per session | per session | `RATE_LIMITED`. The session is not suspended. |
| ER-20 | Commands | 512 per session | per session | `RATE_LIMITED`. A further command is refused until the session ends. |
| ER-21 | Commits | 128 per session | per session | `RATE_LIMITED`. This is the approval-prompt budget. |
| ER-22 | Consecutive invalid attempts | 10 | per session | The session is suspended by the runtime and the operator is alerted. A successful command resets the counter. |
| ER-23 | Encoded data per command | 2048 bytes | per command | `DRAFT_LIMIT`. |
| ER-24 | Total draft size | 65536 bytes | per draft | `DRAFT_LIMIT`. |
| ER-25 | Parts per field | 64 | per field | `DRAFT_LIMIT`. |
| ER-26 | In-flight commands | 1 | per session | `RATE_LIMITED` at the gateway. |
| ER-27 | Deliveries per client address | 120 per minute, 600 per hour | per client address | `RATE_LIMITED`. A risk signal only, never an identity. |

**ER-28**: The limits in ER-23 to ER-25 are the draft and chunk limits of `XGN-DEC-012`. The limit in ER-20 is the command budget of the same decision.

**ER-29**: The runtime MUST expose the limits that apply to the session in the capability description, and the values in the description MUST be the values the runtime enforces (XGN-03 CD-05).

**ER-30**: A session that reaches the consecutive-invalid-attempt limit MUST be suspended, not merely rate limited. Ten malformed commands in a row indicate an agent that is not following the protocol, and continuing to serve it is a cost and a risk.

**ER-31**: A `status` poll is a read. It MUST NOT consume a command capability and MUST NOT advance the session sequence number (`XGN-DEC-023`).

## 6. The FIX template

A rejected command MUST be answered with the error contract **and** a FIX template. The template is the agent's instruction for what to do next, so it is emitted with the rejection, in the `LAST` block of the panel (XGN-14).

```text
FIX
  code       <ERROR_CODE>
  reason     <one sanitised line>
  field      <field name>          (only when the error names a field)
  expected   <what the runtime expects>
  found      <what the runtime received>   (only a value the agent already holds)
  action     <action>
  command    <corrected command template, or none>
  seq        <next expected session sequence number>
```

| ID | Rule |
|---|---|
| ER-32 | The template MUST contain exactly these elements, in this order. An element that does not apply is omitted, except `code`, `reason`, `action` and `seq`, which are always present. |
| ER-33 | `action` MUST be one of `retry_same`, `reissue_on_new_capability`, `edit_field`, `re_preview`, `recompose_request`, `wait`, `read_state`, `operator_review`, `stop`. |
| ER-34 | `command` MUST be a template with the agent's placeholders, not a fully formed command URL. It MUST NOT contain a link or a capability. The newest capability base is issued separately, in the `COMMANDS` block. |
| ER-35 | The template MUST NOT contain a credential, a capability, content, a repository value, or a provider's wording. |
| ER-36 | `seq` MUST be the next expected session sequence number. After a rejection it is unchanged (`XGN-DEC-024`), so the agent re-issues on the same sequence number with a fresh capability base. |
| ER-37 | The template MUST NOT instruct an operator-plane action. It may state that the operator must act, but it cannot name the action to take on the device. |
| ER-38 | The template MUST be derived from the same authoritative error definitions the runtime uses to produce the rejection, so that the instruction and the rejection cannot disagree. |

**Example.** A command whose path is outside the granted scope is rejected as follows.

```text
LAST
  error  SCOPE_VIOLATION (POLICY, not retryable)
  FIX
    code       SCOPE_VIOLATION
    reason     The path is outside the granted directory.
    field      path
    expected   A path inside the granted directory prefix.
    action     edit_field
    command    set field=path value=<path>
    seq        9
```

## 7. Offline behaviour

| ID | Rule |
|---|---|
| ER-39 | When the trusted runtime is unreachable, the gateway fails fast and returns `DEVICE_OFFLINE`. It MUST NOT queue the delivery and MUST NOT retry it internally (XGN-20 §9). |
| ER-40 | `DEVICE_OFFLINE` is retryable. The agent waits, then reads `status` before it does anything else. |
| ER-41 | A mutating command MUST NOT be re-sent while the device is unreachable, because the acceptance status is unknown and a repeat may be a second operation. |
| ER-42 | When the device returns, the agent MUST read `status` first. The status shows whether the earlier command was accepted, rejected or never seen. |
| ER-43 | If the earlier command was accepted and its outcome is unknown, the runtime is reconciling and the agent MUST NOT re-send. If it was never accepted, the agent MAY re-send the identical command on the newest capability base. |
| ER-44 | An outage preserves the session, the grant, the committed envelope, the approval record and the execution record. A temporary outage is not a restart: the open draft survives an outage. |
| ER-45**: A restart is different. On a restart the draft is discarded and a pending approval is invalidated (`XGN-DEC-028`, XGN-21 §10). The agent rebuilds from the current step. |

## 8. Escalation and operator recovery

| Class | What the operator is told | What the operator may do |
|---|---|---|
| SESSION | The session identifier, the state, and whether the cause was an operator action, an expiry, or a detection. | Resume, revoke, or issue a new session. |
| SEQUENCE | The session identifier, the expected and presented sequence numbers. | None. This is an agent-side correction. |
| POLICY | The failed check names, as stable reason codes, and the operation and target. | Decide whether to grant a wider scope in a new session, or to stop. |
| VALIDATION | The command name, the field name and the limit that was exceeded. | None. |
| INTEGRITY | The request identifier, the digest that was expected and the digest that was received. | Decide whether to re-approve. The runtime MUST NOT dispatch a mismatched envelope. |
| APPROVAL | The request identifier, the operation, the target and the outcome of the decision. | Approve, decline, cancel the request, or revoke. |
| EXECUTION | The request identifier, the execution identifier, the outcome state and the recorded reason code. | Cancel the request, or open the operator view for the provider detail. |
| TRANSPORT | The transport, the capability state and the delivery outcome. | Issue a fresh capability, or move the session to another transport. |
| PROVIDER | The request identifier, the provider reference, the stable provider reason code and the operator-facing provider detail. | Decide whether to accept the outcome, or to cancel. |
| RATE | The limit that was reached and its value. | None, unless the agent is misbehaving, in which case suspend. |
| INTERNAL | The request identifier, the internal trace identifier and the failure category. | Investigate. The session SHOULD be suspended. |

**ER-46**: The operator-facing view MAY contain provider text and provider identifiers. The agent-facing error MUST NOT (ER-02).

**ER-47**: An escalation MUST NOT require the operator to read a provider message in order to decide, except in the `PROVIDER` class, where the operator-facing view carries the provider detail behind a stable reason code.

**ER-48**: A `REPO_NOT_PRIVATE` rejection MUST escalate to the operator immediately, because it indicates that the repository the session was granted against has changed its visibility. The runtime suspends the session.

## 9. Decisions recorded

| ID | Decision | Source |
|---|---|---|
| `XGN-DEC-023` | Status polling is a read: it does not consume a command capability, does not advance the sequence number, and is rate limited. | `XGN-DEC-023`, owned by XGN-28 and XGN-23 |
| `XGN-DEC-024` | A rejected command does not consume a sequence number and does not change the next sequence number. | `XGN-DEC-024`, owned by XGN-28 |
| `XGN-DEC-012` | Draft and chunk limits: 2 KB of encoded data per command, 64 KB total draft, 64 parts per field, 512 commands per session. | `XGN-DEC-012`, owned by XGN-28 and XGN-04 |
| `XGN-DEC-007` | The idempotency window is 90 seconds; the caller identity is the session capability plus the exact link and body digest. | `XGN-DEC-007`, owned by XGN-29 |

## 10. Related specifications

- **Depends on:** XGN-01 (the decision register and the identifier conventions), XGN-02 (the Error Contract shape and the category set).
- **Referenced by:** XGN-03, XGN-06, XGN-08, XGN-10, XGN-14, XGN-15, XGN-16, XGN-17, XGN-19, XGN-20, XGN-21, XGN-22, XGN-25, XGN-26, XGN-28, XGN-29, XGN-30.
- **Sibling:** XGN-14 renders the FIX template in the panel; XGN-21 owns the transitions that produce these codes.
- **Predecessor material:** the error and recovery tables of the *Gitana Surface Map (Composed GET): Proposal*, the error list of the *Navigation Transport Specification* (0.1-draft), and Technical Review A items 6, 8 and 9. These are restated here as XGN-native requirements owned by this module.

**Open decisions.**

| ID | Question | Effect if unresolved |
|---|---|---|
| ER-OD-01 | The mapping from provider-specific error codes to the stable catalogue is owned by XGN-18 and XGN-19 and is not yet written. Until it exists, `PROVIDER_ERROR` carries the category. | Agent remediation for provider failures stays generic. |
| ER-OD-02 | Whether the FIX template is delivered in the panel body or as a structured field alongside it. The working choice is a structured field, with the text rendering defined by XGN-14. | Panel shape only. |
