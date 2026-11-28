# XGN-04: XGN Session and Grant Model

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-04 | **Group:** B — Session and request lifecycle
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 10

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the **XGN Session** and the **XGN Grant**: who creates a session, what a grant contains, which values apply by default, how a session moves between its states, what a gate is, how a request is bound to the grant in force, and how sessions are kept apart from one another.

**Out of scope:** the command vocabulary and encoding (XGN-28, XGN-29), the draft object (XGN-07), evaluation of a request against the grant (XGN-15), approval capture (XGN-16), execution (XGN-17), the provider operation catalogue (XGN-19), panel rendering (XGN-14), the request lifecycle (XGN-06) and the transition model (XGN-21).

## 2. Session creation

A session is created by the operator, on the trusted device, for one task. The runtime creates the session record; the operator creates the authority it runs under.

| ID | Requirement |
|---|---|
| S-01 | Only the operator may create a session. An agent MUST NOT create a session, and MUST NOT cause one to be created by any means. |
| S-02 | A session MUST be bound to exactly one grant, one task and one operator principal. One session MUST NOT serve two tasks. |
| S-03 | The trusted runtime MUST assign `session_id` and `grant_id` per XGN-01 §4. Neither identifier may be derived from operator or agent input. |
| S-04 | The operator MUST set the scope triple, the base branch, the ordered steps, the gates, the limits and the budgets before the first command capability is issued. |
| S-05 | The channel for the session MUST be confirmed by the operator at creation (XGN-DEC-001, XGN-09). A transport MUST NOT select itself. |
| S-06 | The runtime MUST verify that the named repository exists and is private before the session becomes live, and the visibility check MUST be repeated before every mutating commit (XGN-15, XGN-18). A repository that is not private MUST be refused. |

**Creation sequence.** (1) The operator selects the task, repository, branch, directory, base branch, steps, gates, limits, budgets and channel on the device. (2) The runtime assigns the identifiers, stamps `created_at` and `expires_at`, computes the grant digest, and persists the session and grant in state `CREATED`. (3) The runtime issues the first command capability and the session link, moving the session to `LOCKED`; the link goes to the operator, not to the agent. (4) The operator hands the session link to the agent out of band and confirms pairing on the device, moving the session to `ACTIVE` (the pairing ceremony is defined in XGN-05 §4). (5) The agent opens the session link and receives onboarding, the capability description and the first capability base (XGN-03 §4, D1).

## 3. The grant

### 3.1 Scope triple

| Element | Rule | Owner |
|---|---|---|
| Repository | Exactly one repository, canonical owner/name, private. | Operator; verified by the runtime (S-06). |
| Branch | Exactly one branch name, or one branch-name prefix reserved for this task, such as `agent/<task-id>`. The grant names one, never both (XGN-DEC-010). | Operator. |
| Directory | One canonical path prefix. Every path a request names MUST fall inside it. Canonicalisation is defined in XGN-08. | Operator. |

All three elements MUST match on every command. A request outside any element MUST be rejected **without contacting the external service** (INV-03). The base branch is the start point of a branch operation and the target of a pull request; it MUST NOT be used as a working branch.

### 3.2 Grant contents

| Field | Meaning | Who sets it | May it change in-session |
|---|---|---|---|
| `grant_id` | Identifies this grant. | Runtime | No |
| `session_id` | The session the grant belongs to. | Runtime | No |
| `repository` | The single repository in scope. | Operator | No |
| `branch` | The single branch in scope. | Operator | No |
| `directory` | The single path prefix in scope. | Operator | No |
| `base_branch` | The branch a branch operation starts from and a pull request targets. | Operator | No |
| `steps[]` | Ordered steps. Each names the operation it covers, its command budget and at most one gate. At most 16 steps. | Operator | No |
| `gates[]` | The gate for each step, by type (section 7). At most one gate per step. | Operator | No |
| `limits` | The numeric ceilings that apply to this session (section 4). | Operator, within the platform maximums | No |
| `budgets` | The command allowance for the session and for each step. | Operator, within the platform maximums | No |
| `lifetime` | Initial 2 h, the absolute `expires_at`, the extension allowance. | Runtime stamps; the operator may extend | Lifetime only, by extension (section 5.1) |
| `policy_version` | The policy in force for the session. | Runtime | Follows the policy in force; not operator-settable |
| `channel` | The transport the session uses. | Gitana proposes; the operator confirms (XGN-DEC-001) | Only by transport fallback (XGN-DEC-030, XGN-09), which MUST NOT widen scope, skip approval or invalidate an existing approval |
| `operator_principal` | The operator principal that created the session. | Runtime | No |
| `grant_digest` | Digest over the authority-bearing grant fields (section 3.3). | Runtime | No |

### 3.3 Grant digest

The grant is digested so that a request binds to the grant in force, rather than to a grant identifier a later change could make ambiguous.

| ID | Requirement |
|---|---|
| S-07 | The runtime MUST compute a grant digest over the canonical grant form. The digest algorithm and exposed length are defined in XGN-31 (XGN-DEC-003). |
| S-08 | The grant digest MUST cover the authority-bearing fields only: the scope triple, the base branch, the ordered steps, the gates, the limits and the budgets. It MUST NOT cover timestamps, identifiers, the policy version or the lifetime, because none of those changes what the session is permitted to do. |
| S-09 | The grant digest MUST be an input to the request digest defined in XGN-31, so that a request binds to the exact grant in force and not merely to a `grant_id`. Whether the grant digest is also carried as an envelope field is an open item for XGN-31 (section 10). |
| S-10 | A request whose grant digest does not match the grant in force MUST be rejected before any provider contact, and the mismatch MUST be recorded (XGN-15). |

## 4. Limits, budgets and defaults

| Limit | Value | Enforced by | Source |
|---|---|---|---|
| Session lifetime | 2 h (`PT2H`), operator-extendable | Runtime | XGN-DEC-008 |
| Session extension | At most `PT2H` each, at most 3, total lifetime at most `PT8H` | Runtime | S-11 |
| Command capability lifetime | 10 min (`PT10M`) or until used | Gateway | XGN-DEC-008, XGN-29 |
| Commands per session | 512 | Runtime | XGN-DEC-012 |
| Commands per step | 128 | Runtime | S-12 |
| Draft size | 64 KB (65536 bytes) | Runtime | XGN-DEC-012 |
| Parts per field | 64 | Runtime | XGN-DEC-012 |
| Encoded data per command | 2 KB (2048 bytes) | Transport, re-checked by the runtime | XGN-DEC-012 |
| Idempotency window | 90 s (`PT90S`) | Gateway | XGN-DEC-007 |
| Concurrent sessions per device | 8 | Runtime | XGN-DEC-037 |
| Open drafts per session | 1 | Runtime | INV-07 |
| Unresolved requests per session | 1 | Runtime | INV-07 |
| Ordered steps per grant | 16 | Runtime | S-13 |
| Gates per step | 1 | Runtime | S-14 |
| Pending approvals per session | 1 | Runtime | INV-07 |
| Status poll interval | At least 10 s; further polling returns `RATE_LIMITED` | Gateway | XGN-23 |

| ID | Requirement |
|---|---|
| S-11 | An extension MUST be an operator-plane action, MUST NOT exceed `PT2H` or 3 extensions, and MUST NOT raise the total lifetime above `PT8H`. |
| S-12 | The per-step command budget MUST NOT exceed 128 and the per-session budget MUST NOT exceed 512. A grant requesting a higher value MUST be clamped to the platform maximum and the operator MUST be told. A grant MUST NOT declare more than 16 ordered steps, and a step MUST declare at most one gate. |
| S-13 | When the session command budget is exhausted the session MUST move to `SUSPENDED` with the reason `BUDGET_EXHAUSTED`, and further commands MUST be rejected. Resuming MUST NOT restore budget; a larger budget requires a new session and a new grant (XGN-DEC-015). |

## 5. Session states and ownership

| State | Meaning | Who may leave it | Agent-visible effect |
|---|---|---|---|
| `CREATED` | Session and grant persisted. No capability issued, no link handed over. | Operator, by completing creation | Nothing. The session is not addressable by the agent. |
| `LOCKED` | A capability has been issued; pairing is not yet confirmed. | Operator, by confirming pairing | Onboarding, capability description, status. No other command. |
| `ACTIVE` | Pairing confirmed. Commands legal within the current step and turn. | Operator, by suspending, revoking, or letting the session expire | The full command surface for the current step. |
| `GATED` | A gate is in force; the agent must wait. | Operator, by clearing the turn | No forward command link. A rate-limited re-check only. |
| `SUSPENDED` | The session is paused, by the operator or by automatic detection. | Operator, by resuming or revoking | Status only. Commands are rejected with `SESSION_SUSPENDED`. |
| `COMPLETED` | Every granted step reached a definitive outcome. | Nobody. Terminal. | Terminal status. |
| `EXPIRED` | The lifetime elapsed. | Nobody. Terminal. | `SESSION_EXPIRED`. |
| `REVOKED` | The operator revoked the session. | Nobody. Terminal. | `SESSION_REVOKED`. |

| ID | Requirement |
|---|---|
| S-14 | The trusted runtime owns the session record and is the only component that writes a session state. The gateway MUST NOT assert a session state; where the gateway and the device disagree, the device's record wins (XGN-00 §5.1). |
| S-15 | The agent MUST NOT cause any transition out of `LOCKED`, `GATED` or `SUSPENDED`, and MUST NOT cause a session to be created, extended, suspended or revoked (INV-01). |

### 5.1 Expiry and extension

At `expires_at` the session moves to `EXPIRED`. Every outstanding command capability becomes invalid immediately, the open draft is discarded (XGN-DEC-018), and a request pending approval moves to `EXPIRED` (XGN-06 §5).

| ID | Requirement |
|---|---|
| S-16 | An extension raises `expires_at` only. It MUST NOT change the scope, the steps, the gates, the limits or the budgets, MUST NOT change the grant digest (S-08), MUST NOT reset the session sequence number (XGN-01 §4), MUST NOT restore a discarded draft and MUST NOT reinstate an expired request. An expired session MUST NOT be resumed; a new session is created instead. |

### 5.2 Suspension and resumption

| ID | Requirement |
|---|---|
| S-17 | A session MUST be suspended by the operator, and MUST be suspended by the runtime when an integrity signal fires: capability reuse (INV-09), a repository visibility change at a mutating commit, budget exhaustion (S-15) or sustained rate-limit abuse (XGN-23). |
| S-18 | Suspension MUST NOT change the scope of the grant and MUST NOT be a route to widening it. |
| S-19 | Resumption is an operator-plane action. It returns the session to `ACTIVE` at the same step, with the same grant, the same session sequence number series and, if the gate that caused the suspension is still in force, a fresh turn that must be cleared again. |

### 5.3 Revocation

| ID | Requirement |
|---|---|
| S-20 | Revocation is an operator-plane action, takes effect immediately, and needs no further confirmation. It moves the session to `REVOKED`. |
| S-21 | Every command capability issued under the session MUST be invalid the moment the session is revoked, including a capability inside the idempotency window. A pending request MUST be cancelled (XGN-06 §4). |
| S-22 | A request already dispatched to the external service MUST NOT be recalled. Its outcome MUST be recorded and reconciled in the normal way (XGN-22). |
| S-23 | The agent MUST see only that the session is revoked, the revocation timestamp, and that no further work is possible. A revocation reason MUST NOT be disclosed to the agent if it names another session, another operator or policy internals. No scope beyond the identifiers the agent already holds MAY be disclosed, and no repository content MAY be disclosed. |
| S-24 | A revoked session MUST NOT be resumed, and its identifiers MUST NOT be reused (XGN-01 §4). |

## 6. Scope is immutable within a session

A grant is fixed for the lifetime of the session it belongs to (XGN-DEC-015). Scope, permission and lifetime are separate: only the lifetime may be extended.

| ID | Requirement |
|---|---|
| S-25 | The scope triple, the base branch, the ordered steps, the gates, the limits and the budgets MUST NOT change while the session exists. |

**Why the prohibition exists.** A mutable grant would let the party the grant constrains change the constraint (INV-17). An approval binds to a digest computed under a grant in force, so a scope change between approval and dispatch would make the approved object differ from the dispatched object (INV-06, AR-04). A command capability is scope-bound, so a scope change would have to invalidate capabilities in flight, producing the fork and replay conditions the capability model exists to detect (AR-01, AR-03). The policy decision is evaluated against a named grant, and a changed grant invalidates the decision it was taken against (XGN-15). Audit traceability requires a stable chain from operator intent to dispatched effect (INV-18).

**What replaces a mid-session scope change.** The operator creates a **new session** with a new grant expressing the new scope. The runtime issues a new session link and a new pairing ceremony. Completed steps stay completed in the old session; the new session may start at the first step the operator has not already satisfied. The old session is closed as `COMPLETED`, `EXPIRED` or `REVOKED`. The two sessions share nothing: different identifiers, grant, sequence number series and capabilities. A request dispatched under the old grant completes under the old grant and is never migrated.

## 7. Gates and turns

A **gate** is a point at which the agent must stop and the operator must act. A **turn** is the interval during which the agent may issue commands after a gate has been cleared. `turn_id` is assigned per XGN-01 §4.

| Gate type | Trigger | Cleared by |
|---|---|---|
| `AFTER_N_COMMANDS` | The Nth accepted command in the step completes, N fixed by the grant and at most 128. | Operator |
| `AFTER_NAMED_COMMAND` | A named command completes, for example `preview`. | Operator |
| `BEFORE_MUTATING_COMMIT` | Every mutating commit is proposed. | Operator |
| `PHASE_BOUNDARY` | The agent has reached the end of a granted step. | Operator |
| `OPERATOR_PAUSE` | The operator pauses at any time, including between two commands. | Operator |

While any gate is in force the session exposes no forward command link and offers a rate-limited re-check only.

| ID | Requirement |
|---|---|
| S-26 | A gate MUST be cleared by an operator-plane action that records a `turn_id`. The agent MUST NOT be able to clear a gate (INV-01). |
| S-27 | Clearing a turn MUST NOT approve, decline or bypass the per-request approval required for a mutating commit. A turn and an approval are separate facts and both are recorded (XGN-DEC-011, owned by XGN-16). |
| S-28 | Clearing a turn MUST NOT widen the scope, extend the lifetime, restore a discarded draft or restore an exhausted budget. |
| S-29 | A report by the agent in chat is not verified by Gitana and is not an authorisation (INV-02). The operator judges it. |

## 8. Isolation between sessions

| ID | Requirement |
|---|---|
| S-30 | A session MUST NOT read, observe or affect another session's state, draft, request, grant, budget, capability or identifiers. |
| S-31 | A command capability, a session sequence number and a grant identifier belong to exactly one session, and MUST be rejected when presented against any other. |
| S-32 | The gateway MUST NOT merge, share, reorder or expose state across sessions. A shared counter MUST NOT make one session's activity observable to another. |
| S-33 | An operator action in one session MUST NOT change the state of another session. The agent-visible surface MUST NOT disclose the existence, identifier, scope or state of another session (XGN-03 §2.2); the audit log MAY contain events from many sessions (XGN-25). |
| S-34 | Device-level resources are shared and their exhaustion MUST be reported to the operator, not as cross-session detail to an agent. |

## 9. Decisions recorded

XGN-DEC-008 (session and capability lifetime) — XGN-04, XGN-29. XGN-DEC-010 (branch scope) — XGN-04. XGN-DEC-012 (draft and chunk limits) — XGN-04, XGN-28. XGN-DEC-015 (mid-session scope change) — XGN-04. XGN-DEC-037 (concurrent sessions per device) — XGN-04. XGN-DEC-038 (grant digest and binding) — XGN-04, XGN-31.

The authoritative statement of each decision is held by its owner module; the register in XGN-01 §7 is the index and carries the one-line summaries.

## 10. Open decisions

| ID | Question | Note |
|---|---|---|
| OD-04-1 | Whether the grant digest is an envelope field or only an input to the request digest. | An envelope change needs an impact review under XGN-02 §13; XGN-31 must settle it. |
| OD-04-2 | Whether a session may move to a second trusted device. | Not settled by this set; a device change is assumed to require a new session until decided. |
| OD-04-3 | Whether an operator may delegate a bounded set of approvals to a second principal on the device. | Approval attribution is owned by XGN-16; identity constraints by XGN-05. |

## 11. Related specifications

- **Depends on:** XGN-01 (identifiers, decision register), XGN-02 (shared contracts), XGN-03 (discovery model).
- **Referenced by:** XGN-05, XGN-06, XGN-07, XGN-09, XGN-14, XGN-15, XGN-16, XGN-20, XGN-21, XGN-28, XGN-29, XGN-31.
- **Predecessor material:** the session scope, lifecycle, phase, step and turn model of the *Navigation Transport Specification* (0.1-draft) and the grant and gate concepts of the *Gitana Surface Map (Composed GET): Proposal*, redistributed here. Their claims remain untested (XGN-01 §8).
- **Out of scope:** command semantics, policy evaluation, approval capture, execution and the state machine, as listed in section 1.
