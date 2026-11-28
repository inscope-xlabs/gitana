# XGN-11: XGN Connector and Tool Transport

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-11 | **Group:** C — Transport and command surface
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-09 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 13

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module specifies delivery of an XGN Request through an **agent connector**: a component that exposes Gitana operations to an agent platform as callable tools. It defines the tool surface, the mapping of tool inputs to the shared request contract, the mapping of tool results back to an XGN Panel, credential handling, rate limits and error mapping, idempotency for tool calls, structured output, and capability discovery over a connector.

The connector is the first-ranked channel in the selection order (XGN-09 §5.1, `XGN-DEC-001`) because it is the only channel in which neither a URL nor an unparsable reply is exposed to an intermediary.

In scope: the connector transport model; the tool surface and its arguments; input and output mapping; credential handling; rate limits and error mapping; idempotency; capability discovery.

Out of scope: the Composed GET transport (XGN-10), the shell transport (XGN-12) and the copy/paste handoff transport (XGN-13); the shared contracts (XGN-02); the command protocol and encoding (XGN-28); command capabilities and replay rules (XGN-29); the error catalogue (XGN-23); the event catalogue (XGN-25); the trust model (XGN-05); how a connector is distributed, installed or certified by a platform (XGN-27).

## 2. The connector transport model

### 2.1 What a connector is

A connector exposes a small, fixed set of tools to an agent platform and relays each tool call into the XGN pipeline. The connector is an agent-plane component. It is a carrier, and it holds no authority.

| ID | Requirement |
|---|---|
| CT-01 | A connector MUST expose the tool surface of §3 and MUST NOT expose a second, undocumented path to Gitana. |
| CT-02 | A connector MUST NOT decide permissions, evaluate policy, capture an approval or cause execution (XGN-09 G-07). |
| CT-03 | A connector MUST NOT hold authoritative state. A connector MAY hold the bound session reference, a rate counter and the last reply for the duration of a call (XGN-09 G-08). |
| CT-04 | A connector MUST NOT be usable from the operator plane. A connector credential MUST NOT satisfy an operator-plane authentication requirement (XGN-00 §4.2). |
| CT-05 | A connector MUST be bound to exactly one XGN Session at a time. The binding MUST be made by an operator-plane action, and the agent MUST NOT choose or change it. |

### 2.2 The delivery path

```text
agent  ->  tool call  ->  connector  ->  XGN Gateway / trusted runtime
                                               |
                                       shared request contract
                                               |
                              validation, policy, approval, execution
                                               |
                                       structured tool result
```

| ID | Requirement |
|---|---|
| CT-06 | Every tool call MUST enter the pipeline through the shared request contract of XGN-02 §4. A connector MUST NOT have a private request format. |
| CT-07 | A connector MUST NOT relay a request the runtime has not authorised, and MUST NOT assert that an operation was accepted or executed unless the runtime has said so (XGN-00 §5.1). |
| CT-08 | A connector MUST NOT bypass the gateway for a request that the gateway would rate-limit or bound. |

## 3. The tool surface

### 3.1 The catalogue

| Tool | Purpose | Arguments | Result |
|---|---|---|---|
| `gitana_session` | Open or refresh the bound session and obtain the discovery material. | none required; optional `action` with the value `open` or `refresh` | the current panel and the capability description |
| `gitana_command` | Issue one command against the bound session. | `cmd` (required); `seq` (required except for `status`); and, where the command takes them, `op`, `field`, `value`, `data`, `enc`, `part`, `page`, `hash`, `ops`, `capability` | the next panel, the next session sequence number, the next command capability |
| `gitana_status` | Read the current state of the session and of any request in flight. | none | the current state panel. It MUST NOT carry content |
| `gitana_capabilities` | Read the capability description of the session. | none | the capability description of XGN-02 §5 |

### 3.2 Argument rules

| ID | Requirement |
|---|---|
| CT-09 | `cmd` MUST be one of the command names advertised by `gitana_capabilities` for the current state. A command that is not advertised MUST be rejected with `STATE_INVALID` and MUST NOT be executed (XGN-03 CD-03). |
| CT-10 | `seq` MUST equal the session sequence number last issued to the agent. A mismatch MUST be rejected with `SEQ_INVALID`, and the next sequence number MUST NOT change (XGN-DEC-024). |
| CT-11 | `capability`, when the connector requires one, MUST be the newest command capability issued for the session. A stale capability MUST be rejected with `LINK_EXPIRED`; a consumed capability MUST be rejected with `LINK_CONSUMED` (XGN-29). |
| CT-12 | `value` and `data` MUST use the encoding declared for that command: base64url by default, `enc=text` only for short, simple text (XGN-DEC-026, XGN-28). |
| CT-13 | `ops` carries the bounded list of field changes for `batch`, applied atomically with one sequence number (XGN-DEC-021). |
| CT-14 | The connector MUST NOT accept an argument that maps to a runtime-stamped field of the request envelope. Such a call MUST be rejected with `MALFORMED_REQUEST` (XGN-02 §4.2). |
| CT-15 | An unknown or repeated argument MUST be rejected with `VALIDATION_FAILED` before the call reaches the runtime. |
| CT-16 | `gitana_status` and `gitana_capabilities` MUST NOT accept `seq`, MUST NOT consume a command capability and MUST NOT advance the session sequence number (XGN-DEC-023). |

## 4. Mapping tool inputs to the shared request contract

| Tool input | Maps to | Rule |
|---|---|---|
| `cmd`, `op`, `field`, `value`, `data`, `enc`, `part`, `page`, `hash`, `ops` | the command that builds or commits the draft; on commit these become `operation` and `arguments` in the envelope | Canonicalised and validated by the runtime (XGN-08). |
| `seq` | the session sequence number | Gates the command and is recorded in the event. It is not an argument of the dispatched request. |
| `capability` | the command capability of XGN-29 | Authorises the command and is consumed when the command is accepted. |
| `session` (implicit) | the bound `session_id` | Bound by the operator; never supplied by the agent. |
| none | `request_id`, `grant_id`, `step_id`, `principal_id`, `issued_at`, `expires_at`, `policy_version`, `draft_digest`, `envelope_digest` | Stamped by the runtime. A tool call MUST NOT supply them. |

| ID | Requirement |
|---|---|
| CT-17 | The mapping MUST be one-to-one: two tool calls that differ only in transport framing MUST produce the same envelope, differing only as XGN-02 §4.2 permits. |
| CT-18 | A connector MUST NOT synthesise, infer or default any value for a runtime-stamped field. A missing argument MUST be a validation error, not an empty value. |
| CT-19 | The connector MUST NOT translate an operation name, an argument name or an encoding into a provider-specific form. Provider mapping belongs to the provider adapter (XGN-18). |

## 5. Mapping tool results back to a panel

| Result field | Panel block | Note |
|---|---|---|
| `state`, `step`, `task` | `STATE`, `TASK` | The task progress comes from the grant. |
| `draft` | `DRAFT` | Field status, sizes and digests. Agent-written values are not echoed. |
| `last` | `LAST` | The outcome of the previous command, or the error contract object with its remediation. |
| `commands`, `next`, `seq`, `limits` | `COMMANDS` | Only the commands legal in the current state. |
| `content` | `DRAFT` or `LAST`, marked as data | Present only in an explicit read (§9). |

| ID | Requirement |
|---|---|
| CT-20 | A tool result MUST be renderable as the five panel blocks of XGN-02 §11 and MUST NOT add a sixth block to the panel. It MAY carry machine-readable fields beside the panel. |
| CT-21 | Every label in a tool result MUST be generated by Gitana and MUST NOT be derived from repository content or agent-written content (XGN-02 §11, XGN-00 AR-08). |
| CT-22 | A tool result MUST carry `contract_version`, the current session sequence number and the next command capability. |

## 6. Credential handling

| ID | Requirement |
|---|---|
| CT-23 | A connector MUST authenticate to Gitana as an agent-plane principal and MUST present a session credential bound to the bound session (XGN-05). |
| CT-24 | A connector MUST NOT hold, request, receive, forward or store a provider credential. Gitana's long-lived provider credentials live on the trusted runtime and MUST NOT be reachable from a connector (XGN-00 §4.2, XGN-17). |
| CT-25 | A tool call that carries a credential field MUST be rejected with `MALFORMED_REQUEST`. The connector MUST NOT accept a provider credential from the agent as a means of gaining reach. |
| CT-26 | A connector MUST NOT be able to cause execution. It carries a request; the runtime decides, the operator approves, the runtime dispatches. |
| CT-27 | The session credential MUST be scoped to the bound session, MUST be revocable independently of the session, and MUST be replaced when the transport changes (XGN-09 §6.3). |
| CT-28 | A connector MUST NOT write the session credential, a command capability or agent-authored content to a log, a telemetry record or a crash report. |
| CT-29 | Where the agent platform requires the agent to hold a token of its own, that token MUST be a platform credential and MUST NOT grant any authority against Gitana beyond the agent-plane session credential. |

## 7. Rate limits and error mapping

### 7.1 Rate limits

The session-wide budget is owned by XGN-23. This module adds transport-local ceilings only, and they MUST NOT exceed that budget.

| Limit | Value |
|---|---|
| Tool calls per session per minute | 60, with a burst of 20 |
| Status or capability reads per session per minute | 6, and at most 1 per 10 seconds |
| Commands per session | 512 (XGN-DEC-012) |
| Concurrent in-flight commands per session | 1 |

| ID | Requirement |
|---|---|
| CT-30 | A tool call that exceeds a limit MUST be rejected with `RATE_LIMITED`, with `retryable` set to true and a remediation naming the minimum wait. |
| CT-31 | A connector MUST NOT retry a rejected call on its own initiative. Retry of an accepted call is governed by §8; retry of a rejected call is the agent's decision. |

### 7.2 Error mapping

The error code set is owned by XGN-23. This table maps connector failures onto it and introduces no new code.

| Failure | Code | Category | Retryable |
|---|---|---|---|
| The runtime or the gateway cannot be reached | `DEVICE_OFFLINE` | TRANSPORT | yes |
| The tool call is malformed before it leaves the connector | `MALFORMED_REQUEST` | VALIDATION | no |
| The operator has not yet confirmed the session | `SESSION_LOCKED` | SESSION | no |
| The session is suspended, expired or revoked | `SESSION_SUSPENDED`, `SESSION_EXPIRED`, `SESSION_REVOKED` | SESSION | no |
| The session sequence number does not match | `SEQ_INVALID` | SEQUENCE | no |
| The command capability has expired | `LINK_EXPIRED` | VALIDATION | no |
| The command capability was already consumed | `LINK_CONSUMED` | VALIDATION | no |
| The command is not legal in the current state | `STATE_INVALID` | SESSION | no |
| An argument falls outside the grant | `SCOPE_VIOLATION` | POLICY | no |
| The operation is not permitted by the grant | `OPERATION_NOT_PERMITTED` | POLICY | no |
| A size or count limit is exceeded | `DRAFT_LIMIT` | VALIDATION | no |
| The request digest does not match the committed draft | `DRAFT_HASH_MISMATCH` | INTEGRITY | no |
| A rate limit is exceeded | `RATE_LIMITED` | RATE | yes |

| ID | Requirement |
|---|---|
| CT-32 | A tool result that carries an error MUST use the Error Contract shape of XGN-02 §10, with a stable code, a category, a `retryable` flag, a remediation and no secret and no content. |
| CT-33 | A connector MUST NOT translate an error into free text, and MUST NOT substitute a provider message for the code. |

## 8. Idempotency for tool calls

The **request identity** of a tool call is the tuple of the bound `session_id`, the command name, the session sequence number, the command capability, and the request digest of the canonical command arguments.

| ID | Requirement |
|---|---|
| CT-34 | A repeated tool call with the same request identity MUST NOT produce a second effect. Inside the idempotency window it MUST return the original reply (XGN-DEC-007, INV-08). |
| CT-35 | The idempotency window is 90 seconds, measured from the first acceptance of that identity. It MUST NOT be extended by a retry, and it MUST NOT be restarted by a further call. |
| CT-36 | Caller identity is the session credential plus the exact command digest. A source address or user agent MAY be recorded as a risk signal and MUST NOT be treated as authentication (XGN-DEC-007). |
| CT-37 | A call that reuses a consumed identity with different arguments MUST be recorded as a reuse event and MUST suspend the session (INV-09). |
| CT-38 | Beyond the idempotency window, a repeated call MUST NOT be treated as a retry. The agent MUST read authoritative status instead of re-issuing (XGN-22, INV-16). |
| CT-39 | The runtime MUST serialise the commands of one session. A second concurrent command MUST be rejected with `STATE_INVALID` and MUST NOT advance the sequence number. |
| CT-40 | A reply that carries agent-authored content MUST NOT be cached by a connector or by the gateway (XGN-DEC-027, XGN-09 G-55). |

## 9. Structured output

| ID | Requirement |
|---|---|
| CT-41 | A tool result MUST be a single structured object carrying the panel blocks of §5, the `contract_version`, the session sequence number and the next command capability. |
| CT-42 | A tool result MUST NOT contain a free-form body that an agent could read as an instruction. Gitana-generated text only. |
| CT-43 | Agent-written and repository-derived content MUST appear only in an explicit read — `gitana_command` with `cmd=show` — and MUST be marked as data (INV-13). |
| CT-44 | A tool result MUST NOT contain a capability other than the one it is delivering, and MUST NOT contain a value derived from a secret (XGN-03 CD-10). |
| CT-45 | A tool result MUST NOT contain a long-lived credential, a provider credential or a URL that carries one. |
| CT-46 | The connector MUST NOT summarise, truncate or reformat the panel. It presents what the runtime produced. |

## 10. Capability discovery over a connector

| ID | Requirement |
|---|---|
| CT-47 | `gitana_capabilities` MUST return the capability description of XGN-02 §5, generated from the same authoritative command and operation definitions the validator uses (XGN-03 CD-01). |
| CT-48 | The description MUST reflect the current session state, and the operation list MUST be filtered by the grant (XGN-03 CD-06). |
| CT-49 | The description MUST NOT contain a capability value, a credential, a repository path outside the grant or any repository content (XGN-03 §2.2). |
| CT-50 | The description MUST be re-readable at any time, subject to the read rate limit, so an agent that has lost the thread recovers without the operator re-issuing anything. |
| CT-51 | The `transport_caps` field of the description MUST carry the connector's capability declaration (XGN-09 §3). |
| CT-52 | A connector MUST NOT ship a shortened, static or hand-maintained copy of the description, and MUST NOT fail open with a partial description (XGN-03 §8). |
| CT-53 | `gitana_session` MUST return the description together with the first panel, so a single call bootstraps discovery (XGN-03 D1). |

## 11. Audit

| ID | Requirement |
|---|---|
| CT-54 | The connector transport MUST cause the events defined in XGN-25 to be recorded for the commands it carries, with the `transport` attribute set to `connector`. |
| CT-55 | The connector MUST NOT introduce a new event type for a condition XGN-25 already covers. |
| CT-56 | A tool call that never reached the runtime MUST be recorded as an event of type `connector.call_failed`, carrying the command name, the session identifier and the reason code, and no content and no credential. |

## 12. Decisions recorded

| ID | Subject | How this module applies it | Owner |
|---|---|---|---|
| `XGN-DEC-001` | Channel order | The connector is the first-ranked channel and is the one this module specifies | XGN-09 |
| `XGN-DEC-022` | Capability discovery | §10 requires the description over a connector to be the same description every other transport exposes | XGN-03 |
| `XGN-DEC-023` | Status polling | §3.2 makes `gitana_status` and `gitana_capabilities` non-consuming, non-advancing reads | XGN-28, XGN-23 |
| `XGN-DEC-006` | Approval on mutating commits | A tool call that commits a mutating operation enters the same approval path as every other transport | XGN-16 |
| `XGN-DEC-007` | Idempotency window and caller identity | §8 applies the 90 second window and the session-credential-plus-digest caller identity to tool calls | XGN-29 |
| `XGN-DEC-012` | Draft and chunk limits | §7.1 applies the 512 command per session budget | XGN-28, XGN-04 |
| `XGN-DEC-026` | Default content encoding | §3.2 requires base64url by default and `enc=text` for short simple text | XGN-28 |
| `XGN-DEC-027` | Relay caching of content | CT-40 forbids caching a content-bearing result | XGN-20 |
| `XGN-DEC-030` | Transport fallback | CT-27 requires the credential to be replaced when the transport changes | XGN-09 |

No new decision identifier is introduced by this module. The module-local rules are carried by the `CT-nn` clauses above.

## 13. Open decisions

| ID | Question | Owner |
|---|---|---|
| OD-11-01 | Whether a connector operated by a party other than Gitana may be offered to an operator, and under what certification and revocation terms, is not settled. | XGN-27, XGN-05 |
| OD-11-02 | The gateway profile used by a connector transport — rate budget, body ceiling, and whether a connector may be served directly by the runtime without the gateway — is not settled. | XGN-20 |
| OD-11-03 | Whether a connector may serve more than one session at once, with the agent choosing between them at call time, is not settled. | XGN-11 |

## 14. Related specifications

- **Depends on:** XGN-01, XGN-02, XGN-03, XGN-09.
- **Referenced by:** XGN-09, XGN-15, XGN-16, XGN-20, XGN-26, XGN-27.
- **Sibling:** XGN-10, XGN-12 and XGN-13 specify the other transports; XGN-28 owns the command names and encoding that §3 uses; XGN-29 owns the command capability; XGN-23 owns the error code set that §7.2 maps onto.
- **Predecessor material:** the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026), which listed the connector channel and left the tool surface undefined; and the *Navigation Transport Specification* (0.1-draft, 25 November 2026), which explicitly placed the tool surface outside its own scope and established that a transport is an adapter over one shared request contract. Their claims and open decisions remain proposals.
- **Out of scope:** every rule listed in section 1, and the distribution and certification of connectors.
