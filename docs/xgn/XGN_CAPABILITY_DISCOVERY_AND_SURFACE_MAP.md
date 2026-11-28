# XGN-03: XGN Capability Discovery and the Surface Map

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-03 | **Group:** A — Framework foundation
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** none outstanding at this level

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose

This module defines how an agent discovers what Gitana can do, what its session permits, and which actions are available right now. It defines the **XGN Surface Map**: the set of panels, states, actions and commands reachable within an XGN session.

Discovery is a security property as much as a usability one. An agent that can only act on what it has been told is told about is an agent that cannot widen its own scope by guessing. Therefore the discovery surface MUST be authoritative, MUST be derived from the same definitions the validator uses, and MUST never advertise more than the session's grant allows.

**Out of scope:** the panel rendering format and command vocabulary presentation, owned by XGN-14; the wire form of the capability description, owned by XGN-02.

## 2. The discovery contract

An agent MUST be able to answer these questions from the session itself, without external instruction:

| Question | Answered by |
|---|---|
| What contract version am I speaking? | `contract_version` in the capability description |
| Which operations exist? | `operations[]` |
| Which fields does each operation require, and which are optional? | `operations[].required`, `operations[].optional` |
| Which fields are long and arrive in parts? | `operations[].long_fields` |
| Which commands can I issue? | `commands[]` |
| Which commands are legal in my current state? | `commands[].states` |
| What are the size and count limits? | `limits` |
| How do I encode a value? | `encoding` rules, restated per command in XGN-28 |
| What went wrong last time and how do I recover? | `errors[]`, `recovery` |
| What does this session permit? | `grant` summary and the permitted scope in the panel |
| How long is my capability valid? | `limits.capability_ttl_seconds` |
| How long may I wait before retrying? | `limits.idempotency_window_seconds` |

### 2.1 Self-describing requirement

The capability description MUST be generated from the same authoritative operation and command definitions used by the request validator.

**A hand-maintained second copy of the command set is prohibited.** It will drift from the validator, and the drift is precisely where an agent discovers a command that does not exist, or misses one that does.

### 2.2 What discovery MUST NOT reveal

- Any capability value, in any form. A capability is only ever delivered in a link the agent is expected to use.
- Any credential, token, key or provider secret.
- Any repository content, path listing, branch name outside the granted scope, or file content.
- Any operation, command or field that the session's grant does not permit.
- The existence of another session, another operator, or another grant.

### 2.3 What discovery MUST reveal

- The legal commands for the **current** state, including the exact parameter names and the encoding rules for those commands.
- The operation catalogue restricted to the operations the grant permits, with their required and optional fields.
- The limits that apply to this session, including the real values, not merely their existence.
- The error codes the agent may encounter, and the recovery action for each.
- The current state, the current step and the progress through the granted steps.

## 3. The Surface Map

The Surface Map is a directed graph of **nodes**, where a node is a panel the agent can reach.

```text
                 operator creates session + grant
                              |
                              v
                        [ LOCKED ]  onboarding + pairing challenge
                              |  operator confirms on device
                              v
   agent: new -> [ HUB ] <------------------ cancel
                 |  new(op)                     ^
                 v                              |
             [ DRAFT ] <---- set/append ---- [ PREVIEW ]
                 |  preview                     |  set/append returns to DRAFT
                 |                              |  commit(hash)
                 |                              v
                 |                         [ APPROVAL ]  operator-only decision
                 |                              |  decline -> HUB (step retained)
                 |                              v
                 |                         [ RESULT ]  success -> next step / completed
                 |                              |  unknown -> [ WAIT ]
                 |                              v
                 +-------------------------- [ WAIT ]  reconcile by reads only
```

### 3.1 Node definitions

| Node | Meaning | Agent may | Agent must not |
|---|---|---|---|
| **LOCKED** | Session created, pairing not yet confirmed on the device. | Read onboarding, read the pairing challenge, read status. | Do anything else. The session cannot be unlocked from the agent plane. |
| **HUB** | Session is live at a granted step with no open draft. | `new`, `status`, `cancel` (no-op). | Open a second draft. |
| **DRAFT** | A draft is open and editable. | `set`, `append`, `replace`, `unset`, `batch`, `preview`, `show`, `cancel`. | Commit an incomplete draft. |
| **PREVIEW** | The draft is complete and validated. | `show`, `commit`, `cancel`, and any edit, which returns the session to DRAFT and voids the digest. | Commit with a stale digest. |
| **APPROVAL** | The request is committed and awaiting the operator's decision. | `status`. | Approve, decline, or influence the decision. |
| **RESULT** | A definitive outcome is available. | `status`, `new` for the next step. | Assume a step succeeded because it was dispatched. |
| **WAIT** | The outcome is unknown, the session is gated, suspended, or the device is unreachable. | A rate-limited re-check. | Re-commit blindly. |

### 3.2 Node and command matrix

| Command | LOCKED | HUB | DRAFT | PREVIEW | APPROVAL | RESULT | WAIT |
|---|---|---|---|---|---|---|---|
| `new` | — | yes | — | — | — | yes | — |
| `set` | — | — | yes | yes | — | — | — |
| `append` | — | — | yes | yes | — | — | — |
| `replace` | — | — | yes | yes | — | — | — |
| `unset` | — | — | yes | yes | — | — | — |
| `batch` | — | — | yes | yes | — | — | — |
| `preview` | — | — | yes | yes | — | — | — |
| `show` | — | — | — | yes | — | — | — |
| `commit` | — | — | — | yes | — | — | — |
| `cancel` | — | — | yes | yes | — | — | — |
| `status` | yes | yes | yes | yes | yes | yes | yes |

A command not listed for the current node MUST be rejected with `STATE_INVALID`. A command listed but issued with an illegal parameter combination MUST be rejected with `VALIDATION_FAILED`.

### 3.3 Read-only commands

`status` is a read, not a command that consumes a capability.

- It MUST NOT consume a command capability.
- It MUST NOT advance the session sequence number.
- It MUST NOT alter a draft, a request or the grant.
- It MUST be rate limited. The limit is stated in the capability description and the `WAIT` panel.
- It MUST return the current authoritative state, never a cached one.

## 4. The discovery lifecycle

| Stage | When | What the agent receives |
|---|---|---|
| D0 | Session created, capability issued. | Session link. Nothing else. |
| D1 | First open of the session link. | Onboarding text, the pairing challenge, the capability description and the first capability base. |
| D2 | Pairing confirmed on the device. | HUB panel with the granted steps and the first capability base. |
| D3 | Each command. | The panel for the new node, the new capability base and the next sequence number. |
| D4 | `preview`. | Sizes, normalised path, digests, and the draft digest to use in `commit`. |
| D5 | `commit`. | APPROVAL or RESULT panel. |
| D6 | `status`. | Current state panel, rate limited. |

D1 is the only point at which the agent receives the full capability description. The agent MUST be able to re-read it: the session MUST expose it as a read, so an agent that has lost the thread can recover without the operator re-issuing the link. The read is subject to the same rate limit as `status`.

## 5. Discovery across transports

The discovery **content** is transport-independent. The discovery **delivery** is not.

| Transport | How discovery is delivered |
|---|---|
| Composed GET | The capability description is embedded in the D1 panel, in a fenced block. An agent that cannot parse JSON still reads the human-readable command list in the same panel. |
| Connector / tool | The capability description is returned as a structured tool result and as a field of the session tool. |
| Shell | The capability description is printed by a dedicated command and is also available as a machine-readable document. |
| Copy/paste handoff | The capability description is included in the handoff package the operator pastes. |

**Rule:** every transport MUST expose the same capability description content, derived from the same authoritative source. A transport that cannot express the description MUST provide a human-readable equivalent in the same reply, generated from the same definitions. A transport MUST NOT ship a shortened or static copy.

## 6. Progressive disclosure

An agent that receives everything at once is an agent that reads everything at once. The discovery surface is therefore layered.

| Layer | Contents | Always present |
|---|---|---|
| L1 — Orientation | State, step, task progress, the legal commands, the next sequence number, the capability base. | Yes |
| L2 — Limits | The numeric limits that apply to this session. | Yes |
| L3 — Operation detail | Fields, required and optional, long fields, preconditions, mutating flag. | On request, or when the agent opens a draft |
| L4 — Content | The agent's own draft content, or repository content. | Only in an explicit read, marked as data |

L1 and L2 are always present. L3 is returned when the agent opens a draft, or on request. L4 is never returned except by an explicit read.

## 7. Consistency requirements

| ID | Requirement |
|---|---|
| CD-01 | The capability description and the validator MUST derive from one source of truth. |
| CD-02 | A command advertised in the capability description MUST be executable in the advertised state. |
| CD-03 | A command not advertised MUST NOT be executable. |
| CD-04 | The `COMMANDS` block of a panel and the `commands[]` array of the capability description MUST agree for the current state. |
| CD-05 | The limits in the capability description MUST be the limits the runtime enforces. |
| CD-06 | The operation list MUST be filtered by the grant. An operation the grant does not permit MUST NOT appear. |
| CD-07 | The recovery instructions MUST match the error contract's `remediation` for each listed code. |
| CD-08 | The capability description MUST NOT be cached by a transport beyond the current reply. |
| CD-09 | Every limit in the description MUST be enforced at the earliest practical validation boundary and re-checked by the runtime. |
| CD-10 | A discovery response MUST NOT contain a capability other than the one it is delivering, and MUST NOT contain a value derived from a secret. |

## 8. Failure behaviour

| Condition | Behaviour |
|---|---|
| The agent requests a capability description it is not entitled to | Rejected `POLICY_DENIED`. No partial description. |
| The capability description cannot be generated | The session fails closed to `LOCKED` and the operator is alerted. A degraded description is worse than none. |
| The agent requests an operation outside the grant | Rejected `OPERATION_NOT_PERMITTED`. The operation MUST NOT be disclosed as existing. |
| The agent requests a command in an illegal state | Rejected `STATE_INVALID`, with the legal states listed. |
| The session is expired or revoked | Rejected `SESSION_EXPIRED` or `SESSION_REVOKED`, with no further detail. |

## 9. Decisions recorded

| ID | Decision | Owner |
|---|---|---|
| `XGN-DEC-022` | The session's first reply carries a machine-readable capability description generated from the same authoritative definitions the validator uses. | XGN-03 |

## 10. Related specifications

- **Depends on:** XGN-01, XGN-02.
- **Referenced by:** XGN-09, XGN-14, XGN-21, XGN-28, XGN-30.
- **Sibling:** XGN-14 owns the panel presentation of the same information.
- **Predecessor material:** the surface map concept from the *Gitana Surface Map (Composed GET): Proposal*.
