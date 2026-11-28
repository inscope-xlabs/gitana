# XGN-21: XGN State Machine and Transitions

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-21 | **Group:** E — Relay and reliability
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** SM-OD-01, SM-OD-02, SM-OD-03 (section 14)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module owns the **authoritative transition model** for XGN: the state of every machine in the framework, the legal transitions between states, the owner of each transition, the authorisation each transition requires, and the evidence each transition records.

This module is the single place where a transition is defined. Other modules reference a transition by identifier; they do not restate it. A transport, a policy rule or an error code that implies a state change MUST defer to this module.

**In scope:** the session, draft, request, approval, execution and recovery machines; timeout, expiry, suspension, resumption, restart and revocation behaviour; concurrent command handling; the command and state transition matrix.

**Out of scope:** the panel presentation (XGN-03, XGN-14); the operation catalogue (XGN-19); the error codes (XGN-23); the reconciliation procedure (XGN-22); the grant rules (XGN-04); the approval policy (XGN-16).

## 2. The authoritative transition model

### 2.1 General rules

| ID | Rule |
|---|---|
| SM-01 | Every transition MUST have exactly one owner: the component or party that causes it, not the one that observes it. |
| SM-02 | Every transition MUST state its authorisation requirement. |
| SM-03 | Every transition MUST record evidence in the event shape of XGN-02 §9, with a dotted lowercase `<object>.<past-tense-verb>` type. |
| SM-04 | A transition MUST NOT be inferred. A move no transition table here describes is illegal. |
| SM-05 | A transition not legal in the current state MUST be rejected with `STATE_INVALID`, changing no state. |
| SM-06 | Only the trusted runtime applies a transition. The gateway and the transport adapters MUST NOT apply one on the runtime's behalf. |
| SM-07 | The runtime MUST re-verify the session sequence number before applying any transition that consumes a command. |
| SM-08 | A state change and its evidence MUST be applied atomically. A crash between them leaves the pre-transition state. |
| SM-09 | A transition that depends on an external service result MUST NOT be applied until the runtime holds that result, or holds a record that the result is unknown. |

**Owner values.** `Runtime` — the trusted runtime, on a timer, a validation outcome or a provider read. `Operator` — the human operator on the device. `Agent` — an agent command with a valid capability. `Gateway` — a transport action that is not a transition.

**Authorisation values.** `Agent command` — a valid, unexpired, unconsumed capability. `Operator action` — an operator-plane action authenticated independently of the agent plane. `Runtime timer` — time is the trigger. `Provider result` — a response through an authorised adapter.

### 2.2 A rejected command changes nothing

| ID | Rule |
|---|---|
| SM-10 | A rejected command MUST NOT change the session state, the draft, the open request, the grant, or the next session sequence number, and MUST NOT have an effect (`XGN-DEC-024`). |
| SM-11 | A rejected command MUST be answered with the Error Contract of XGN-02 §10 and the remediation of XGN-23, and MUST emit a `command.rejected` event with the error code and the sequence number presented. |
| SM-12 | After a rejection the runtime MUST issue a fresh capability base and MUST restate the next expected sequence number, which is unchanged. |

### 2.3 Sequence numbers

| ID | Rule |
|---|---|
| SM-13 | The session sequence number starts at 1 when the session is created and increases by exactly 1 for each accepted command. |
| SM-14 | A duplicate sequence number presented inside the idempotency window is an exact retry: the original reply is returned and no new effect occurs (XGN-29). |
| SM-15 | A sequence number gap is rejected with `SEQ_INVALID`. |
| SM-16 | A read-only command carries no sequence number, consumes no capability and does not advance the sequence number (`XGN-DEC-023`). |
| SM-17 | The sequence number MUST NOT reset when a session is resumed, and MUST NOT be reused after a session ends. |

### 2.4 Concurrent command handling

| ID | Rule |
|---|---|
| SM-18 | The runtime MUST serialise command processing per session. Two commands for one session MUST NOT be applied concurrently. |
| SM-19 | The gateway MUST accept at most one in-flight delivery per session and MUST reject a second with `RATE_LIMITED` (XGN-20 §8). |
| SM-20 | Two commands carrying the same expected sequence number MUST NOT both produce an effect. Exactly one MUST be applied. The other MUST be answered as an exact retry inside the window, or rejected as `LINK_CONSUMED` with session suspension outside it. |
| SM-21 | Commands for different sessions MUST NOT affect each other's state, and a session MUST NOT observe another session's existence. |
| SM-22 | A transition conditional on external state MUST re-read that state when it is applied, not when it is validated. |

## 3. The session machine

### 3.1 States

| State | Meaning | Terminal |
|---|---|---|
| `LOCKED` | The session exists. The pairing challenge is not confirmed on the device. Only reads are legal. | no |
| `HUB` | The session is live at a granted step, with no open draft and no unresolved request. | no |
| `DRAFT` | A draft is open and editable. | no |
| `PREVIEW` | The draft is complete and validated, and a request digest exists. | no |
| `APPROVAL` | A committed request awaits the operator's decision. | no |
| `RESULT` | A definitive outcome is available for the current step. | no |
| `WAIT` | The session is held. The cause is recorded: an uncleared turn gate, an unreachable device, or an outcome being reconciled. | no |
| `SUSPENDED` | The session is held for operator review, by operator action or by detection. | no |
| `COMPLETED` | Every granted step has a definitive successful outcome. | yes |
| `EXPIRED` | The session lifetime ended. | yes |
| `REVOKED` | The operator revoked the session. | yes |

| ID | Rule |
|---|---|
| SM-23 | Predecessor material uses one name, `ACTIVE`, for the states in which the session is live. XGN does not. `ACTIVE` is the class `{HUB, DRAFT, PREVIEW}`, not a state. |

### 3.2 Transitions

| From | Trigger | To | Owner | Authorisation | Evidence | Effect on the session |
|---|---|---|---|---|---|---|
| — | The operator creates the session and grant | `LOCKED` | Operator | Operator action | `session.created` | Session, grant and step list exist. The first sequence number is 1. |
| `LOCKED` | The operator confirms the pairing challenge on the device | `HUB` | Operator | Operator action; the agent MUST NOT cause it | `session.paired` | The first capability base is issued. Agent commands become legal. |
| `LOCKED` | The session lifetime ends | `EXPIRED` | Runtime | Runtime timer | `session.expired` | Terminal. Capabilities invalidated, draft discarded. |
| `LOCKED` | The operator revokes | `REVOKED` | Operator | Operator action | `session.revoked` | Terminal. |
| `HUB` | `new` is accepted | `DRAFT` | Agent | Agent command | `draft.opened` | One draft is open. At most one per session (INV-07). |
| `HUB` | `cancel` | `HUB` | Agent | Agent command | — | No-op: there is no draft to cancel. |
| `HUB` | A turn gate is reached | `WAIT` | Runtime | Runtime timer or gate evaluation | `session.gated` | No forward command is offered. Wait cause `gate`. |
| `HUB` | The operator suspends, or a detection holds the session | `SUSPENDED` | Operator | Operator action, or runtime detection | `session.suspended` | The draft is discarded. Request records are preserved. |
| `DRAFT` | `set`, `append`, `replace`, `unset` or `batch` is accepted | `DRAFT` | Agent | Agent command | `draft.updated` | The draft changes. Any request digest is voided. |
| `DRAFT` | `preview` is accepted | `PREVIEW` | Agent | Agent command | `draft.previewed` | Validation passed. A request digest is issued. |
| `DRAFT` | `preview` is rejected | `DRAFT` | Agent | Agent command | `command.rejected` | No change. The error names the incomplete field. |
| `DRAFT` | `cancel` | `HUB` | Agent | Agent command | `draft.discarded` | The draft is discarded (XGN-DEC-018). |
| `PREVIEW` | Any field edit | `DRAFT` | Agent | Agent command | `draft.updated` | The request digest is voided. |
| `PREVIEW` | `commit` is accepted | `APPROVAL` | Agent | Agent command; the request enters the request machine at `RECEIVED` | `request.received` | The draft becomes an immutable envelope and is discarded. It is the session's only unresolved request (INV-07). |
| `PREVIEW` | `cancel` | `HUB` | Agent | Agent command | `draft.discarded` | The draft is discarded. |
| `APPROVAL` | The operator approves | `APPROVAL` | Operator | Operator action bound to the request digest (XGN-16) | `request.approved` | The request moves to `APPROVED` then `EXECUTING`. The session stays `APPROVAL` until a result exists. |
| `APPROVAL` | The operator declines | `HUB` | Operator | Operator action | `request.rejected` | The step stays current. The draft is not restored. |
| `APPROVAL` | The approval lifetime ends | `HUB` | Runtime | Runtime timer | `request.expired` | The request is closed. The step stays current. |
| `APPROVAL` | A turn gate is reached, or the device becomes unreachable | `WAIT` | Runtime | Runtime timer | `session.gated` | Wait cause `gate` or `device`. The request record is preserved. |
| `RESULT` | `new` for the next granted step | `DRAFT` | Agent | Agent command | `draft.opened` | The next step begins. |
| `RESULT` | The last granted step reaches a definitive successful outcome | `COMPLETED` | Runtime | Runtime evaluation of the granted steps | `session.completed` | Terminal. |
| `RESULT` | The operator suspends | `SUSPENDED` | Operator | Operator action | `session.suspended` | The draft is discarded. |
| `WAIT` | The operator clears the turn | `HUB` | Operator | Operator action | `session.gated_cleared` | The wait cause clears. Forward commands become legal. |
| `WAIT` | Reconciliation resolves to success | `RESULT` | Runtime | Provider result (XGN-22) | `recovery.resolved`, `request.completed` | The step is complete. |
| `WAIT` | Reconciliation resolves to failure | `RESULT` | Runtime | Provider result (XGN-22) | `recovery.resolved`, `request.failed` | The step stays current. The agent may rebuild. |
| `WAIT` | The operation cannot be identified | `SUSPENDED` | Runtime | Runtime detection | `recovery.operator_review` | The session is held for the operator. |
| `WAIT` | The operator suspends | `SUSPENDED` | Operator | Operator action | `session.suspended` | The draft is discarded. |
| `SUSPENDED` | The operator resumes | The state held before the suspension | Operator | Operator action | `session.resumed` | The session returns to its prior state. The sequence number does not reset. |
| `SUSPENDED` | The operator revokes | `REVOKED` | Operator | Operator action | `session.revoked` | Terminal. |
| `SUSPENDED` | The session lifetime ends | `EXPIRED` | Runtime | Runtime timer | `session.expired` | Terminal. |
| `COMPLETED`, `EXPIRED`, `REVOKED` | — | — | — | — | — | No transition exists. These states are terminal. |

### 3.3 Projection onto the surface map

Panel node names are owned by XGN-03 §3.1; this module owns the state underneath them. The projection is one-to-one except `SUSPENDED`, which shows as `WAIT` with a reason, `COMPLETED`, which shows a terminal panel, and `EXPIRED` and `REVOKED`, which show nothing. The commands legal in each state are in section 12.

| ID | Rule |
|---|---|
| SM-24 | The panel MUST show the authoritative session state, not a summary of it. The projection above MUST NOT be used to infer a transition; the transition tables in this module are authoritative. |

## 4. The draft machine

| From | Trigger | To | Owner | Authorisation | Evidence | Effect |
|---|---|---|---|---|---|---|
| — | `new` is accepted | `OPEN` | Agent | Agent command | `draft.opened` | A draft identifier is assigned. |
| `OPEN` | A field edit is accepted | `OPEN` | Agent | Agent command | `draft.updated` | A field, or one part of a long field, changes. |
| `OPEN` | `preview` is accepted | `PREVIEWED` | Agent | Agent command | `draft.previewed` | Validation passed. A request digest is issued. |
| `OPEN` | `preview` is rejected | `OPEN` | Agent | Agent command | `command.rejected` | No change. The error names the missing field. |
| `PREVIEWED` | A field edit is accepted | `OPEN` | Agent | Agent command | `draft.updated` | The request digest is voided. |
| `PREVIEWED` | `commit` is accepted | `COMMITTED` | Agent | Agent command | `request.received` | The draft becomes an immutable envelope. The draft content is discarded. |
| `OPEN`, `PREVIEWED` | `cancel`, expiry, suspension, revocation or a runtime restart | `DISCARDED` | Runtime | Runtime or agent | `draft.discarded` | The draft content is erased (XGN-DEC-018, XGN-DEC-028). |
| `COMMITTED` | — | — | — | — | — | The draft is immutable. The envelope is the authoritative object. |

| ID | Rule |
|---|---|
| SM-25 | A session holds at most one draft and at most one unresolved request (INV-07). A `new` that would open a second draft MUST be rejected with `STATE_INVALID`. |
| SM-26 | A committed envelope MUST survive until the request is resolved, even though the editable draft does not (XGN-DEC-018). |

## 5. The request machine

| From | Trigger | To | Owner | Authorisation | Evidence | Effect on the session |
|---|---|---|---|---|---|---|
| — | `commit` is accepted | `RECEIVED` | Agent | Agent command | `request.received` | The session moves to `APPROVAL`. |
| `RECEIVED` | Validation and canonicalisation complete | `VALIDATING` | Runtime | Runtime | `request.validated` | None. |
| `VALIDATING` | The policy decision is `REQUIRE_APPROVAL` | `AWAITING_APPROVAL` | Runtime | Runtime; the policy engine decides (XGN-15) | `request.awaiting_approval` | The operator is prompted on the device. |
| `VALIDATING` | The policy decision is `ALLOW` | `EXECUTING` | Runtime | Runtime; the policy engine decides | `request.approved` | The approval step is skipped only for a read-only operation. Every mutating operation requires approval (`XGN-DEC-006`). |
| `VALIDATING` | The policy decision is `DENY` | `REJECTED` | Runtime | Runtime; the policy engine decides | `request.rejected` | The session moves to `HUB`. The step stays current. |
| `AWAITING_APPROVAL` | The operator approves | `APPROVED` | Operator | Operator action bound to the request digest (XGN-16) | `request.approved` | None. |
| `AWAITING_APPROVAL` | The operator declines | `REJECTED` | Operator | Operator action | `request.rejected` | The session moves to `HUB`. |
| `AWAITING_APPROVAL` | The approval lifetime ends | `EXPIRED` | Runtime | Runtime timer | `request.expired` | The session moves to `HUB`. The step stays current. |
| `APPROVED` | The runtime dispatches | `EXECUTING` | Runtime | Runtime, against a durable execution record | `execution.started` | None. |
| `EXECUTING` | The provider returns a definitive success | `COMPLETED` | Runtime | Provider result | `request.completed`, `execution.completed` | The session moves to `RESULT`. |
| `EXECUTING` | The provider returns a definitive failure | `FAILED` | Runtime | Provider result | `request.failed`, `execution.failed` | The session moves to `RESULT`. The step stays current. |
| `EXECUTING` | No response, a timeout, or a crash | `OUTCOME_UNKNOWN` | Runtime | Runtime timer | `request.outcome_unknown`, `execution.outcome_unknown` | The session moves to `WAIT`. |
| `OUTCOME_UNKNOWN` | Reconciliation begins | `RECONCILING` | Runtime | Runtime, using read-only provider reads only (XGN-22) | `recovery.started` | The session stays in `WAIT`. |
| `RECONCILING` | A provider read identifies the operation as complete | `COMPLETED` | Runtime | Provider result | `request.completed`, `recovery.resolved` | The session moves to `RESULT`. |
| `RECONCILING` | A provider read identifies the operation as not done | `FAILED` | Runtime | Provider result | `request.failed`, `recovery.resolved` | The session moves to `RESULT`. The step stays current. |
| `RECONCILING` | The operation cannot be identified | `OUTCOME_UNKNOWN` | Runtime | Runtime | `recovery.operator_review` | The session moves to `SUSPENDED`. |
| `OUTCOME_UNKNOWN` | The runtime restarts | `OUTCOME_UNKNOWN` | Runtime | Runtime | `recovery.started` | The record survives. Reconciliation restarts. |
| `RECONCILING` | The operator decides the operation did not happen | `FAILED` | Operator | Operator action, recorded against the provider read | `request.failed`, `recovery.resolved` | The session moves to `RESULT`. |
| `RECONCILING` | The operator cancels the request | `CANCELLED` | Operator | Operator action | `request.cancelled` | The session moves to `HUB`. No further dispatch. |
| `RECEIVED`, `VALIDATING`, `AWAITING_APPROVAL`, `APPROVED` | The operator revokes the session, or the session expires | `CANCELLED` | Operator | Operator action, or runtime timer | `request.cancelled` | The session becomes `REVOKED` or `EXPIRED`. No dispatch occurs. |
| `EXECUTING`, `OUTCOME_UNKNOWN`, `RECONCILING` | The operator revokes the session | no transition | Operator | Operator action | `session.revoked` | The session becomes `REVOKED`. The request record is preserved for audit. No further dispatch. |
| Any request state | The request lifetime ends | `EXPIRED` | Runtime | Runtime timer | `request.expired` | The session moves to `HUB`. |

| ID | Rule |
|---|---|
| SM-27 | The request machine MUST NOT move from `OUTCOME_UNKNOWN` or `RECONCILING` to `EXECUTING`. A dispatched request is never dispatched again. Recovery is by reads (INV-16, XGN-22). |
| SM-28 | A request that fails because of a provider conflict is not a provider error. The provider conflict code is defined in XGN-23. |

## 6. The approval machine

| From | Trigger | To | Owner | Authorisation | Evidence | Effect |
|---|---|---|---|---|---|---|
| — | The request reaches `AWAITING_APPROVAL` | `PENDING` | Runtime | Runtime | `approval.requested` | An approval record is created with the request digest and an expiry. |
| `PENDING` | The operator approves | `APPROVED` | Operator | Operator action; the approval carries the current request digest (XGN-16) | `approval.recorded` | The approval authorises exactly one dispatch. |
| `PENDING` | The operator declines | `DECLINED` | Operator | Operator action | `approval.declined` | The request moves to `REJECTED`. |
| `PENDING` | The approval lifetime ends | `EXPIRED` | Runtime | Runtime timer | `approval.expired` | The request moves to `EXPIRED`. No dispatch. |
| `PENDING` | The request digest changes | `INVALIDATED` | Runtime | Runtime | `approval.invalidated` | The approval no longer authorises dispatch. |
| `PENDING` | The runtime restarts | `INVALIDATED` | Runtime | Runtime | `approval.invalidated` | A pending approval does not survive a restart (XGN-DEC-028). The request requires a new commit. |
| `APPROVED` | Dispatch begins | `CONSUMED` | Runtime | Runtime | `execution.started` | The approval is spent. |
| `APPROVED` | The runtime restarts before dispatch | `INVALIDATED` | Runtime | Runtime | `approval.invalidated` | No dispatch occurs. |
| `DECLINED`, `EXPIRED`, `INVALIDATED`, `CONSUMED` | — | — | — | — | — | Terminal for the approval. |

| ID | Rule |
|---|---|
| SM-29 | The approval lifetime is 600 seconds from the decision. The operator MAY extend it to a maximum of 1800 seconds. The runtime MUST refuse a longer extension. |
| SM-30 | An approval MUST carry the request digest of the request it approves. A runtime MUST reject an approval whose digest differs from the current envelope digest with `INTEGRITY_MISMATCH` (XGN-31). |

## 7. The execution machine

The execution states are the `state` values of the Execution Contract in XGN-02 §8.

| From | Trigger | To | Owner | Authorisation | Evidence | Effect |
|---|---|---|---|---|---|---|
| — | The request is approved | `PENDING` | Runtime | Runtime | `execution.created` | A candidate execution record exists. Nothing has been dispatched. |
| `PENDING` | The execution record is made durable | `RUNNING` | Runtime | Runtime | `execution.started` | The record is durable **before** dispatch is attempted, so a crash leaves a reconcilable record. |
| `RUNNING` | The provider returns success | `COMPLETED` | Runtime | Provider result | `execution.completed` | The request moves to `COMPLETED`. |
| `RUNNING` | The provider returns a definitive failure | `FAILED` | Runtime | Provider result | `execution.failed` | The request moves to `FAILED`. |
| `RUNNING` | A timeout, an absent response, or a crash | `OUTCOME_UNKNOWN` | Runtime | Runtime timer | `execution.outcome_unknown` | The request moves to `OUTCOME_UNKNOWN`. The result is unknown, not failed. |
| `OUTCOME_UNKNOWN` | Reconciliation resolves the outcome | `COMPLETED` or `FAILED` | Runtime | Provider result (XGN-22) | `recovery.resolved` | The request leaves `OUTCOME_UNKNOWN`. |

| ID | Rule |
|---|---|
| SM-31 | The runtime MUST write the execution record durably before dispatch. A crash between the record and the dispatch MUST leave the request in `OUTCOME_UNKNOWN`, never in `FAILED`. |
| SM-32 | The provider call timeout is 30 seconds. Expiry produces `OUTCOME_UNKNOWN`, not `FAILED`. A provider that returns a definitive error produces `FAILED`. |

## 8. The recovery machine

The recovery machine is the lifecycle of one reconciliation activity. The request machine's `RECONCILING` state and this machine's `RECONCILING` state are entered and left together.

| From | Trigger | To | Owner | Authorisation | Evidence | Effect |
|---|---|---|---|---|---|---|
| — | An uncertain outcome is detected | `DETECTED` | Runtime | Runtime | `recovery.started` | The session moves to `WAIT`. |
| `DETECTED` | The runtime classifies the uncertainty | `IDENTIFYING` | Runtime | Runtime | `recovery.classified` | The classification is one of the six cases in XGN-22 §4. |
| `IDENTIFYING` | A read-only provider read identifies the operation | `RECONCILING` | Runtime | Provider result | `recovery.reconciled` | The outcome is determined. |
| `IDENTIFYING` | The operation cannot be identified | `OPERATOR_REVIEW` | Runtime | Runtime | `recovery.operator_review` | The session moves to `SUSPENDED`. |
| `RECONCILING` | The reconciliation concludes | `RESOLVED` | Runtime | Provider result | `recovery.resolved` | The request machine leaves `RECONCILING`. |
| `OPERATOR_REVIEW` | The operator decides | `RESOLVED` | Operator | Operator action | `recovery.resolved` | The request machine leaves `RECONCILING`. |
| `OPERATOR_REVIEW` | The operator declines to decide | `UNRESOLVED` | Operator | Operator action | `recovery.unresolved` | The session stays `SUSPENDED`. |
| `RESOLVED`, `UNRESOLVED` | — | — | — | — | — | Terminal for the activity. |

| ID | Rule |
|---|---|
| SM-33 | The recovery machine MUST NOT cause a dispatch. A reconciliation read MUST be a read-only provider operation (XGN-22 §6). |

## 9. Timeout and expiry transitions

| ID | Timer | Value | From | To | Owner |
|---|---|---|---|---|---|
| SM-34 | Session lifetime | 2 hours from creation, operator-extendable (`XGN-DEC-008`) | any non-terminal state | `EXPIRED` | Runtime |
| SM-35 | Command capability lifetime | 600 seconds, or until used (`XGN-DEC-008`) | — | — | Gateway issues; the runtime rejects |
| SM-36 | Idempotency window | 90 seconds (`XGN-DEC-007`) | — | — | Runtime |
| SM-37 | Approval lifetime | 600 seconds, extendable to 1800 seconds | `PENDING` | `EXPIRED` | Runtime |
| SM-38 | Request lifetime | 3600 seconds from commit | any request state | `EXPIRED` | Runtime |
| SM-39 | Provider call timeout | 30 seconds | `RUNNING` | `OUTCOME_UNKNOWN` | Runtime |
| SM-40 | Reconciliation window | 15 minutes from the first read | `OUTCOME_UNKNOWN` | `OPERATOR_REVIEW` | Runtime |
| SM-41 | Single reconciliation read | 10 seconds | `RECONCILING` | `OUTCOME_UNKNOWN` | Runtime |

| ID | Rule |
|---|---|
| SM-42 | A turn gate has no timer. A gate is cleared by the operator, or not at all. The runtime MUST NOT expire a gate on its own. |
| SM-43 | When a lifetime ends, the runtime MUST discard the open draft, invalidate every outstanding capability, close the request without dispatch, and emit the corresponding terminal event. |

## 10. Runtime restart recovery

This is `XGN-DEC-028`.

| Object | Behaviour on restart |
|---|---|
| The open draft | Discarded; content erased. The session returns to `HUB`. |
| The session, the grant, the step list | Survive. The sequence number resumes at its last accepted value plus one. |
| A committed envelope | Survives in immutable form. |
| A request record | Survives. |
| A pending approval | Invalidated. The request requires a new commit. |
| An approval consumed by a dispatch | Survives. |
| An execution record | Survives, including one in `RUNNING` at the crash. Such a record is treated as `OUTCOME_UNKNOWN`. |
| A reconciliation record | Survives and restarts. |

| ID | Rule |
|---|---|
| SM-44 | A restart MUST NOT cause a dispatch to be repeated. A record in `RUNNING` at restart is never re-dispatched; it is reconciled by reads (INV-16). |

## 11. Suspension, resumption and revocation

| ID | Rule |
|---|---|
| SM-45 | The session enters `SUSPENDED` when the operator suspends it, when a link reuse is detected (INV-09), when the repository is no longer private, when the reconciliation cannot identify the operation, or when a policy check fails in a way the runtime classifies as a security event. |
| SM-46 | On suspension the open draft is discarded. Request, approval and execution records are preserved. |
| SM-47 | The session leaves `SUSPENDED` only by an operator action: resume or revoke. The agent MUST NOT cause either. |
| SM-48 | On resumption the session returns to the state it held before the suspension. The session sequence number does not reset. |
| SM-49 | Revocation is terminal and irreversible. The session identifier MUST NOT be reused (XGN-01 §4). |
| SM-50 | On revocation the runtime MUST invalidate every outstanding capability, close every unresolved request without dispatch, and emit `session.revoked`. |
| SM-51 | The agent's view of a suspended session is a `WAIT` panel carrying the reason and the statement that the operator must act. It MUST NOT be told which detection triggered the suspension where disclosure would assist an attacker. |

## 12. Command and state transition matrix

This is the authoritative matrix. Every command is listed against every session state. A rejection changes nothing (section 2.2).

**Legend.** `ok` — accepted, transition defined in section 3.2. `noop` — accepted, no effect. `op` — a legal operator-plane action. `—` — not applicable. An agent-plane cell names the rejection code.

| Command | LOCKED | HUB | DRAFT | PREVIEW | APPROVAL | RESULT | WAIT | SUSPENDED | COMPLETED | EXPIRED | REVOKED |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `status` | ok | ok | ok | ok | ok | ok | ok | ok | ok | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `new` | `SESSION_LOCKED` | ok | `STATE_INVALID` | `STATE_INVALID` | `STATE_INVALID` | ok ⁽¹⁾ | `STATE_INVALID` ⁽²⁾ | `SESSION_SUSPENDED` | `STATE_INVALID` | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `set`, `append`, `replace`, `unset`, `batch` | `SESSION_LOCKED` | `STATE_INVALID` | ok | ok | `STATE_INVALID` | `STATE_INVALID` | `STATE_INVALID` ⁽²⁾ | `SESSION_SUSPENDED` | `STATE_INVALID` | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `preview` | `SESSION_LOCKED` | `STATE_INVALID` | ok | ok | `STATE_INVALID` | `STATE_INVALID` | `STATE_INVALID` ⁽²⁾ | `SESSION_SUSPENDED` | `STATE_INVALID` | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `show` | `SESSION_LOCKED` | `STATE_INVALID` | `STATE_INVALID` ⁽³⁾ | ok | `STATE_INVALID` | `STATE_INVALID` | `STATE_INVALID` | `SESSION_SUSPENDED` | `STATE_INVALID` | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `commit` | `SESSION_LOCKED` | `STATE_INVALID` | `DRAFT_INCOMPLETE` ⁽⁴⁾ | ok | `STATE_INVALID` | `STATE_INVALID` | `STATE_INVALID` ⁽²⁾ | `SESSION_SUSPENDED` | `STATE_INVALID` | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `cancel` | `SESSION_LOCKED` | noop | ok | ok | `STATE_INVALID` | `STATE_INVALID` | `STATE_INVALID` | `SESSION_SUSPENDED` | `STATE_INVALID` | `SESSION_EXPIRED` | `SESSION_REVOKED` |
| `confirm_pairing` ⁽⁵⁾ | op | — | — | — | — | — | — | — | — | — | — |
| `clear_turn` ⁽⁵⁾ | — | — | — | — | — | — | op | — | — | — | — |
| `approve` ⁽⁵⁾ | — | — | — | — | op | — | — | — | — | — | — |
| `decline` ⁽⁵⁾ | — | — | — | — | op | — | — | — | — | — | — |
| `suspend` ⁽⁵⁾ | op | op | op | op | op | op | op | — | — | — | — |
| `resume` ⁽⁵⁾ | — | — | — | — | — | — | — | op | — | — | — |
| `revoke` ⁽⁵⁾ | op | op | op | op | op | op | op | op | — | — | — |
| `extend_session` ⁽⁵⁾ | op | op | op | op | op | op | op | op | — | — | — |

⁽¹⁾ In `RESULT`, `new` is accepted only when another granted step remains; otherwise it is rejected with `STATE_INVALID`.

⁽²⁾ In `WAIT`, the rejection code is `TURN_NOT_CLEARED` when the wait cause is an uncleared turn gate, and `STATE_INVALID` for every other wait cause.

⁽³⁾ `show` is legal in `PREVIEW` only, matching XGN-03 §3.2 and XGN-02 §5. Extending it to `DRAFT` is SM-OD-02.

⁽⁴⁾ A commit requires a validated draft, so `commit` in `DRAFT` is rejected with `DRAFT_INCOMPLETE`; the agent must run `preview` first.

⁽⁵⁾ Operator-plane action. The agent MUST NOT be able to cause any of these (INV-01). The operator plane is authenticated independently.

| ID | Rule |
|---|---|
| SM-52 | The matrix MUST be generated from the same authoritative definitions the validator and the capability description use. A hand-maintained copy is prohibited (XGN-03 §2.1). |
| SM-53 | A command not listed for the current state MUST be rejected with `STATE_INVALID`, and a listed command issued with an illegal parameter combination with `VALIDATION_FAILED`. |
| SM-54 | A command legal in a state but refused by policy MUST be rejected with the policy code, and the panel MUST still offer it, because the gateway and the agent cannot know the policy decision. |

## 13. Decisions recorded

| ID | Decision | Owner |
|---|---|---|
| `XGN-DEC-024` | A rejected command does not consume a sequence number and does not change the next sequence number. | XGN-28 |
| `XGN-DEC-028` | On a runtime restart, draft content is discarded; session, grant, request, approval and execution records survive; a pending approval is invalidated. | XGN-07 |
| `XGN-DEC-008` | Session lifetime is 2 hours, operator-extendable; a capability is valid until used or for 10 minutes. | XGN-04, XGN-29 |
| `XGN-DEC-006` | Every mutating operation requires approval, on every transport, including `create_pull_request`. | XGN-16 |

## 14. Related specifications

- **Depends on:** XGN-01 (identifiers, sequence-number rules, the decision register), XGN-02 (the shared contracts, the event shape and the Error Contract), XGN-03 (the surface map nodes this module projects onto).
- **Referenced by:** XGN-04, XGN-06, XGN-07, XGN-09, XGN-14, XGN-15, XGN-16, XGN-17, XGN-20, XGN-22, XGN-23, XGN-25, XGN-26, XGN-28, XGN-29.
- **Sibling:** XGN-22 owns the reconciliation procedure the recovery machine runs; XGN-23 owns the error codes named here.
- **Predecessor material:** the illustrative transition table and required transition definitions in Technical Review B §3.2, and the panel-state mapping in the *Gitana Surface Map (Composed GET): Proposal*, restated as XGN-native requirements.

**Open decisions.**

| ID | Question | Effect if unresolved |
|---|---|---|
| SM-OD-01 | Whether `suspend` from `LOCKED` should be a distinct operator action or a consequence of `revoke`. | Panel wording only. |
| SM-OD-02 | Whether `show` should become legal in `DRAFT`, as Technical Review A item 10 suggests. XGN-03 §3.2 and XGN-02 §5 currently restrict it to `PREVIEW`. | Affects the capability description, owned by XGN-03. |
| SM-OD-03 | The exact request lifetime. The working value is 3600 seconds. | Affects only the value of SM-38. |
