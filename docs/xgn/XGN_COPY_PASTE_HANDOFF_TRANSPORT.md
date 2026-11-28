# XGN-13: XGN Copy/Paste Handoff Transport

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-13 | **Group:** C — Transport and command surface
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-09 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 9

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module specifies the **copy/paste handoff transport**: a human-mediated channel for chat-only environments where an agent has no fetch tool, no shell and no connector. The agent proposes a request in the conversation; the operator carries it into Gitana on the trusted device; Gitana validates it; the operator approves it; the trusted runtime dispatches it.

The agent never sees a capability, a credential or a provider secret. The operator is the carrier, and the operator's action is the authority.

In scope: the handoff package format, size limit and lifetime; the operator paste, validate and approve flow; correlation of the outcome back to the agent; confirmation and correction; the payload restrictions that make this the lowest capability tier; the security properties of the package.

Out of scope: the Composed GET transport (XGN-10), the connector transport (XGN-11) and the shell transport (XGN-12); the command protocol and encoding (XGN-28); command capabilities (XGN-29); the grant (XGN-04), the policy (XGN-15), the approval surface (XGN-16) and the execution path (XGN-17); the error code set (XGN-23); the event catalogue (XGN-25).

## 2. The handoff model

### 2.1 When it applies

This channel is last in the selection order (XGN-09 §5.1, `XGN-DEC-001`). It applies when the agent platform offers no way to reach Gitana directly: no connector tool, no fetch tool that can open a link that appeared in a reply, no shell. It is always available, because a human can always carry a package, which makes it the fallback for every other channel being unusable.

| ID | Requirement |
|---|---|
| CP-01 | The agent MUST compose a handoff package and hand it to the operator in the conversation. It MUST NOT attempt to deliver a request to Gitana by itself. |
| CP-02 | The agent MUST NOT receive a capability, a session credential, a provider credential or a URL through this transport. |
| CP-03 | The transport MUST be declared with the capabilities `read` (delivered by operator relay), `no_headers` and `offline`, and MUST NOT be declared with `compose_query`, `extend_link`, `capability_in_url`, `bulk_content`, `set_header` or `post_body` (XGN-09 §3.2). |

### 2.2 The four steps

| Step | Actor | Action |
|---|---|---|
| 1 | Agent | Composes the package and gives it to the operator in the conversation. |
| 2 | Operator | Pastes the package into the handoff field of the session on the trusted device. |
| 3 | Runtime | Parses the package, binds it to the session, validates it against the grant and the policy, and presents the request. |
| 4 | Operator | Approves or declines. The runtime dispatches only an approved request. |

| ID | Requirement |
|---|---|
| CP-04 | The transport MUST NOT deliver a request into the runtime without an operator-plane paste and an operator-plane approval (INV-01). |
| CP-05 | The runtime MUST NOT accept a handoff while the session is locked, suspended, expired or revoked. |
| CP-06 | A session MUST hold at most one open handoff and at most one unresolved request (INV-07). |
| CP-07 | This transport is the lowest capability tier: channel tier B. The operation and every argument value MUST be fixed by the grant, and the agent selects among the values the grant declares for that operation (XGN-09 §4.1). |

## 3. The handoff package

### 3.1 Format

The package is a single UTF-8 text block with a fixed grammar: a header line, ordered `key: value` lines, zero or more `arg:` lines, and a terminator.

```text
XGN-HANDOFF/1
ref: hnd_7Qk2Zp9Lx4Vm
session: ses_1Ab2Cd3Ef4Gh5Ij6Kl7
op: write_file
intent: Add the release note the agent drafted.
arg: path=docs/notes.md
arg: message=Add release note
end
```

| Field | Rule |
|---|---|
| header | MUST be the literal `XGN-HANDOFF/1`. A different version MUST be rejected. |
| `ref` | The handoff reference code the runtime issued for this session. The runtime resolves it. |
| `session` | The non-secret session reference. A correlation identifier that carries no authority. |
| `op` | The operation name from the catalogue. MUST be permitted by the grant for the current step. |
| `intent` | A short agent-authored statement of what the request is for. Advisory only. |
| `arg:` | One argument, `name=value`. The value MUST be one the grant declares for that operation. |
| `end` | The terminator. A package without it MUST be rejected. |

The reference code uses the identifier form `hnd_` + 12 base64url characters, following the identifier rules of XGN-01 §4. It is a correlation identifier and carries no authority.

| ID | Requirement |
|---|---|
| CP-08 | The package MUST be UTF-8 and MUST NOT contain a C0 control character other than TAB, LF and CR. |
| CP-09 | An unknown key, a repeated key, a missing `ref`, a missing `session`, a missing `op` or a missing terminator MUST be rejected with `MALFORMED_REQUEST`. |
| CP-10 | Every argument value MUST be a value the grant declares for that operation, either an exact value or a member of a bounded declared set. A value the grant does not declare MUST be rejected with `OPERATION_NOT_PERMITTED`. |
| CP-11 | The package MUST NOT contain a long field — `content`, `body`, or any field listed as a long field in the capability description. Where the operation requires one, the operator supplies it on the device, outside the agent's reach. |
| CP-12 | The `intent` line is advisory. It MUST NOT be dispatched, MUST NOT be executed, and MUST NOT contain file content, a credential or a capability. |
| CP-13 | The package MUST NOT contain a capability, a session credential, a provider credential or any value derived from a secret. |

### 3.2 Size limit

| Limit | Value |
|---|---|
| Total package size | 4096 bytes, UTF-8, including the header and the terminator |
| Argument lines | 8 |
| One argument value | 512 bytes |
| `intent` line | 200 bytes |
| `ref` value | 16 characters |
| `session` value | 20 characters |

| ID | Requirement |
|---|---|
| CP-14 | A package that exceeds any limit in §3.2 MUST be rejected with `DRAFT_LIMIT`, and the rejection MUST state which limit was exceeded and its value. |
| CP-15 | A rejected package MUST NOT alter the session, the draft, the open request or the session sequence number. The operator is shown the rejection, and the agent is asked for a shorter package. |
| CP-16 | Because the package is bounded at 4096 bytes and carries no long field, this transport MUST NOT be able to carry agent-authored bulk content. Bulk content would transit the operator's clipboard, where it is read, copied, pasted, logged and forwarded by systems outside Gitana's control, and no approval summary could present it meaningfully. |

### 3.3 Lifetime

| ID | Requirement |
|---|---|
| CP-17 | The handoff reference code is valid for 15 minutes from the moment the runtime issues it. A package presented after that MUST be rejected with `VALIDATION_FAILED` and a remediation asking for a new code. |
| CP-18 | A reference code may be accepted exactly once. A second paste of the same code MUST be rejected with `VALIDATION_FAILED` and a remediation pointing at the status read. |
| CP-19 | A reference code remains usable as a correlation key for status reads while the session is active and for 60 minutes after acceptance, and MUST stop being readable when the session ends (XGN-DEC-008). |
| CP-20 | The runtime MUST issue a fresh reference code for every attempt, and MUST NOT reuse a code after the attempt is closed. |

## 4. The operator flow

### 4.1 Paste, validate, approve

| ID | Requirement |
|---|---|
| CP-21 | The runtime MUST validate the package in this order: format, freshness, binding to the reference code, grant and policy, and only then present the request for approval. It MUST NOT present an invalid package for approval. |
| CP-22 | The operator's paste MUST be an operator-plane action. The agent MUST NOT be able to cause a paste, and the conversation MUST NOT authorise anything (INV-02). |
| CP-23 | The runtime MUST present the operator with an approval summary generated by the runtime, naming the operation, the target and the arguments as the runtime resolved them. It MUST NOT render the raw package text as an instruction (XGN-16, XGN-00 AR-08). |
| CP-24 | The runtime MUST apply the same policy evaluation to a handoff as to a request from any other transport, and MUST dispatch only an approved request (XGN-DEC-006). |

### 4.2 The package is advisory

| ID | Requirement |
|---|---|
| CP-25 | The operator MAY edit any argument value before accepting the package. The edited values are the values dispatched. |
| CP-26 | An edit MUST NOT move the operation outside the grant. The runtime MUST re-validate the edited values against the grant before presenting the request for approval. |
| CP-27 | The runtime MUST record an operator edit as an event of type `handoff.edited`, carrying the names of the fields that were changed and no values. |
| CP-28 | The agent's package MUST NOT be presented to the operator as authoritative, and the runtime MUST NOT infer authority from the package. |

### 4.3 Re-issue

| ID | Requirement |
|---|---|
| CP-29 | When the operator declines a handoff, the runtime MUST record the decline as an event of type `handoff.declined` with a reason code, and MUST display to the operator the reason and the next reference code for a further attempt. |
| CP-30 | A reference code that has been accepted or closed MUST NOT be reused. A further attempt MUST use a newly issued code. |
| CP-31 | Only the operator can ask the agent to re-issue, and only in the conversation. The runtime MUST NOT instruct the agent and MUST NOT infer what the agent should change. |

## 5. Correlation

The agent learns the outcome by one of two bounded mechanisms.

### 5.1 Mechanism A — the operator's report

| ID | Requirement |
|---|---|
| CP-32 | The runtime MUST display to the operator the exact status line to relay for a handoff, including the reference code and the state. |
| CP-33 | An operator's report in the conversation is a report. It is not authorisation and not proof of execution (INV-02), and the agent MUST NOT treat it as evidence that an operation was dispatched. |

### 5.2 Mechanism B — a status read

Where the agent holds an egress the operator has authorised for status reads on the session, the agent MAY read the status of its handoff using the non-secret reference.

| ID | Requirement |
|---|---|
| CP-34 | A status read MUST present the `ref` and the `session` reference. It MUST NOT consume the reference, MUST NOT alter the handoff, and MUST NOT advance the session sequence number (XGN-DEC-023). |
| CP-35 | A status read MUST return only the bounded summary: the reference code, the state, the operation name, the result and the time of the last change. It MUST NOT return argument values, paths, content, the repository, the grant, the operator's identity or a provider reference. |
| CP-36 | A status read MUST be rate limited to 6 per minute per reference, and to at most 1 per 10 seconds. A read beyond the limit MUST be rejected with `RATE_LIMITED`. |
| CP-37 | Where the agent holds no authorised egress, mechanism B is unavailable and the outcome reaches the agent only through mechanism A. The runtime MUST NOT imply otherwise. |
| CP-38 | A status read MUST NOT reveal that any other handoff, session or grant exists. |

### 5.3 The states

| State | Meaning |
|---|---|
| `RECEIVED` | The package was accepted and is awaiting the operator's decision. |
| `ACCEPTED` | The operator approved the request; it has entered the pipeline. |
| `DECLINED` | The operator declined. The step remains current. |
| `EXECUTED` | The request was dispatched and completed. |
| `FAILED` | The request was dispatched and failed. |
| `EXPIRED` | The reference code expired before it was pasted. |

| ID | Requirement |
|---|---|
| CP-39 | The state MUST be recorded as an event of type `handoff.received`, `handoff.accepted`, `handoff.declined`, `handoff.executed` or `handoff.failed`, with no content in the attributes (XGN-25). |
| CP-40 | The handoff events MUST carry the `transport` attribute set to `copy_paste`, so a handoff is distinguishable from a request delivered by another channel. |

## 6. Security

| ID | Requirement |
|---|---|
| CP-41 | The package MUST contain no capability and no credential. A leaked package grants nothing: it does not authorise an operation, does not approve one, does not open a draft, does not expose content and does not reveal the grant. |
| CP-42 | Only the operator can paste, and only the operator can approve. A holder of a leaked package can compose a package, but cannot cause acceptance (CP-22, CP-23). |
| CP-43 | The residual disclosure of a leaked package is the coarse status of that one handoff and the name of its operation, available only to a caller presenting both the reference code and the session reference (CP-35). |
| CP-44 | The reference code MUST NOT be usable to issue a command, open a draft, commit a request, approve a request or read content. It is not a capability in any respect (XGN-29). |
| CP-45 | The transport MUST NOT weaken the content rules of INV-13. A handoff carries no content at all (CP-11). |
| CP-46 | The operator's conversation with the agent MUST NOT be treated as a request, an approval, a scope change or a grant change (INV-02). |
| CP-47 | The transport MUST NOT provide a path that completes a mutating operation without an approval record on the trusted device. |

## 7. Limits in one place

| Limit | Value |
|---|---|
| Total package size | 4096 bytes |
| Argument lines per package | 8 |
| One argument value | 512 bytes |
| `intent` line | 200 bytes |
| Reference code lifetime before paste | 15 minutes |
| Reference code readable as a correlation key | the life of the session, and 60 minutes after acceptance |
| Status reads per reference | 6 per minute, at most 1 per 10 seconds |
| Open handoffs per session | 1 |
| Long fields carried | none |

## 8. Decisions recorded

| ID | Subject | How this module applies it | Owner |
|---|---|---|---|
| `XGN-DEC-001` | Channel order | This transport is the last channel in the order and the fallback of last resort (§2.1) | XGN-09 |
| `XGN-DEC-006` | Approval on mutating commits | §4.1 keeps the operator approval requirement identical on this transport | XGN-16 |
| `XGN-DEC-008` | Session and capability lifetime | §3.3 bounds the reference code against the session lifetime | XGN-04, XGN-29 |
| `XGN-DEC-023` | Status polling | §5.2 makes the correlation read non-consuming, non-advancing and rate limited | XGN-28, XGN-23 |
| `XGN-DEC-016` | Supported chat applications | The transport is used wherever no other channel is available; qualification is by probe outcome, not by name | XGN-30 |

No new decision identifier is introduced by this module. The module-local rules are carried by the `CP-nn` clauses above.

## 9. Open decisions

| ID | Question | Owner |
|---|---|---|
| OD-13-01 | Whether the 4096 byte package ceiling is sufficient for the intended operations is not settled. It is set here as the starting value, and the measured need for a larger package is not established. | XGN-13 |
| OD-13-02 | The `hnd_` identifier family is defined in this module and MUST be added to the identifier registry in XGN-01 before this module leaves Proposed. | XGN-01, XGN-13 |
| OD-13-03 | Whether the operator may edit a package into an operation that differs from the one the agent proposed is not settled. §4.2 currently permits editing argument values only. | XGN-16, XGN-13 |
| OD-13-04 | Which chat applications are supported for this transport is not settled. | XGN-30 (`XGN-DEC-016`) |

## 10. Related specifications

- **Depends on:** XGN-01, XGN-02, XGN-03, XGN-09.
- **Referenced by:** XGN-09, XGN-15, XGN-16, XGN-25, XGN-26.
- **Sibling:** XGN-10, XGN-11 and XGN-12 specify the other transports; XGN-23 owns the error codes used in §3 and §5; XGN-25 owns the event types used in §4 and §5; XGN-29 owns the command capability, which this transport never carries.
- **Predecessor material:** the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026), which listed the copy/paste channel as a chat-only case in which the operator pastes the agent's request into Gitana, and left its format, limits and lifetime undefined. Its claims and open decisions remain proposals.
- **Out of scope:** every rule listed in section 1.
