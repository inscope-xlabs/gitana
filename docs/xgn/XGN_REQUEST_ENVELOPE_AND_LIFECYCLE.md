# XGN-06: XGN Request Envelope and Lifecycle

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-06 | **Group:** B — Session and request lifecycle
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-04 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 10

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines two things: **who owns each field of the XGN Envelope**, and **the lifecycle of an XGN Request** — the states a committed request may occupy, the transitions between them, who owns each transition, and what evidence a transition requires.

**Scope boundary.** This module defines the **request** lifecycle. XGN-21 owns the authoritative state machine for the whole system — the session machine, the draft machine, the approval machine, the execution machine and the recovery machine — and composes the request machine defined here into it. Where the two could be read differently, XGN-21 is authoritative for the transition model and this module is authoritative for the request states and their meaning.

**Out of scope:** the envelope field set and its JSON shape (XGN-02 §4), canonicalisation (XGN-08), the digest algorithm and the integrity binding (XGN-31), policy evaluation (XGN-15), approval capture and the operator interface (XGN-16), execution and provider behaviour (XGN-17, XGN-18), the reconciliation procedure (XGN-22), the error catalogue (XGN-23) and the audit record (XGN-25).

## 2. The envelope and its owners

The envelope is defined by the Request Envelope Contract in XGN-02 §4. This module does not restate the contract; it states the owner and the stamp rule for each field, because the ownership of a field is what decides whether an agent may influence it.

| Field | Owner | Stamp rule | Agent-supplied |
|---|---|---|---|
| `contract_version` | Runtime | Stamped from the version the runtime implements at commit. | No |
| `request_id` | Runtime | `req_` plus 16 base64url characters, never reused (XGN-01 §4). | No |
| `session_id` | Runtime | The session that composed the request. | No |
| `grant_id` | Runtime | The grant in force at commit (XGN-04 §3.2). | No |
| `step_id` | Runtime | The granted step the request targets. | No |
| `principal_id` | Runtime | The acting agent principal of the session. | No |
| `transport` | Runtime | The transport the request arrived on. Informational for audit; it MUST NOT influence the policy decision or the approval. | No |
| `issued_at` | Runtime | RFC 3339 UTC, stamped at commit (XGN-01 §6). | No |
| `expires_at` | Runtime | The earlier of the session expiry and one hour after commit (section 8). | No |
| `policy_version` | Runtime | The policy in force when the request was created. | No |
| `operation` | Agent | The operation name from the operation catalogue (XGN-19). | **Yes** |
| `arguments` | Agent | The normalised arguments for that operation. Every value MUST be canonical per XGN-08. | **Yes** |
| `draft_digest` | Runtime | The digest of the draft at commit time (XGN-31). | No |
| `envelope_digest` | Runtime | The digest of this complete envelope, excluding the `envelope_digest` field itself (XGN-31). | No |
| `signing_key_id` | Runtime | Identifies the runtime key. The signature itself travels in the approval record, not in the envelope, so the envelope stays canonical (XGN-02 §4.1). | No |

| ID | Requirement |
|---|---|
| R-01 | The agent supplies only the operation and its arguments. Every other field is stamped by the runtime (XGN-02 §4.2, INV-04). |
| R-02 | A transport MUST NOT allow an agent to influence `principal_id`, `request_id`, `session_id`, `grant_id`, `step_id`, `issued_at`, `expires_at`, `policy_version`, `draft_digest` or `envelope_digest`. |
| R-03 | An envelope that carries a value for a stamped field differing from the runtime's own value MUST be rejected as `MALFORMED_REQUEST`. A transport that delivers such an envelope MUST NOT repair it and MUST NOT pass it on. |
| R-04 | Two transports that deliver the same logical operation MUST produce envelopes that differ only in `transport`, the identifiers, the timestamps and the digests (XGN-02 §4.2). |

## 3. Request states

| State | Meaning | Owner of the record | Agent-visible effect |
|---|---|---|---|
| `RECEIVED` | The runtime has accepted the request into the pipeline and assigned `request_id`. Nothing has been validated. | Runtime | The request exists; no outcome yet. |
| `VALIDATING` | Canonicalisation, schema validation and policy evaluation are running. | Runtime | The request is under evaluation. |
| `AWAITING_APPROVAL` | Validation passed and the request needs an operator decision (XGN-16). | Runtime | Status only. The agent may not influence the decision. |
| `APPROVED` | The operator approved. The approval record binds to the envelope digest. | Runtime, on the operator's decision | Status only. |
| `REJECTED` | The request will not proceed: the policy engine denied it, or the operator declined it. The reason is recorded. | Policy engine (XGN-15) or operator (XGN-16) | Status only, with the reason code. |
| `EXECUTING` | The runtime has dispatched the request to the provider adapter. | Runtime | Status only. |
| `COMPLETED` | A definitive successful outcome exists. | Runtime | The outcome and its reference. |
| `FAILED` | A definitive unsuccessful outcome exists, including a precondition conflict and an integrity mismatch. | Runtime | The outcome and its reason code. |
| `OUTCOME_UNKNOWN` | Dispatch was attempted and no definitive result was obtained. | Runtime | Status only. The agent may not re-commit. |
| `RECONCILING` | The runtime is determining the actual effect by reading authoritative state (XGN-22). | Runtime | Status only. |
| `CANCELLED` | The request was cancelled before dispatch. | Operator | Status only, with the reason. |
| `EXPIRED` | The request's lifetime elapsed before it reached a terminal outcome. | Runtime | Status only, with the reason. |

| ID | Requirement |
|---|---|
| R-05 | The trusted runtime owns the request record and is the only component that writes a request state (XGN-00 §5.1). The gateway MUST NOT assert a request state or an outcome. |
| R-06 | The agent owns no transition in this lifecycle. It may read the state of a request it composed and nothing else (INV-01). |
| R-07 | A request MUST NOT be dispatched before it has reached `APPROVED`, or before the policy engine has returned `ALLOW` and the operation does not require approval (XGN-15, XGN-DEC-006). |

## 4. Transition table

| Current state | Event | Next state | Owner | Required evidence |
|---|---|---|---|---|
| — | The agent commits a draft | `RECEIVED` | Runtime | The envelope accepted, `request_id` assigned, `issued_at` and `expires_at` stamped. |
| `RECEIVED` | Shape or decode check fails | `REJECTED` | Runtime | The error code and the failing field name (XGN-23). No provider contact. |
| `RECEIVED` | Validation begins | `VALIDATING` | Runtime | The canonical form computed (XGN-08). |
| `RECEIVED` | The operator cancels | `CANCELLED` | Operator | The operator principal and the timestamp. |
| `RECEIVED` | The request expiry elapses | `EXPIRED` | Runtime | The expiry timestamp. |
| `VALIDATING` | The policy engine returns `DENY` | `REJECTED` | Policy engine | The policy decision record (XGN-15). |
| `VALIDATING` | The policy engine returns `REQUIRE_APPROVAL` | `AWAITING_APPROVAL` | Runtime | The policy decision record, and the request presented for approval. |
| `VALIDATING` | The policy engine returns `ALLOW` and no approval is required | `EXECUTING` | Runtime | The policy decision record and the execution record opened (XGN-02 §8). |
| `VALIDATING` | The operator cancels | `CANCELLED` | Operator | The operator principal and the timestamp. |
| `VALIDATING` | The request expiry elapses | `EXPIRED` | Runtime | The expiry timestamp. |
| `AWAITING_APPROVAL` | The operator approves | `APPROVED` | Operator | The approval record carrying the envelope digest (XGN-16). |
| `AWAITING_APPROVAL` | The operator declines | `REJECTED` | Operator | The approval record with the decline decision. The granted step is retained. |
| `AWAITING_APPROVAL` | The operator cancels, or the session is revoked | `CANCELLED` | Operator | The operator principal and the timestamp. |
| `AWAITING_APPROVAL` | The approval window elapses | `EXPIRED` | Runtime | The window deadline and the reason `APPROVAL_WINDOW_EXPIRED`. |
| `APPROVED` | The envelope digest is re-verified and matches | `EXECUTING` | Runtime | The digest match and the execution record opened (XGN-02 §8). |
| `APPROVED` | The envelope digest does not match | `FAILED` | Runtime | The reason `INTEGRITY_MISMATCH`. No provider call is made. |
| `APPROVED` | The approval record expired before dispatch | `EXPIRED` | Runtime | The approval expiry. |
| `APPROVED` | The operator cancels before dispatch | `CANCELLED` | Operator | The operator principal and the timestamp. |
| `EXECUTING` | The provider returns a definitive success | `COMPLETED` | Runtime | The execution record with the provider reference and the result digest. |
| `EXECUTING` | The provider returns a definitive failure or a precondition conflict | `FAILED` | Runtime | The execution record with the reason code. |
| `EXECUTING` | Dispatch was attempted and no definitive result came back | `OUTCOME_UNKNOWN` | Runtime | The execution record with the outcome `OUTCOME_UNKNOWN` and the dispatch timestamp. |
| `OUTCOME_UNKNOWN` | Reconciliation starts | `RECONCILING` | Runtime | The reconciliation record (XGN-22). |
| `RECONCILING` | The effect is found to have occurred | `COMPLETED` | Runtime | The reconciliation record and the authoritative reference read. |
| `RECONCILING` | The effect is found not to have occurred | `FAILED` | Runtime | The reconciliation record. |
| `RECONCILING` | The effect remains indeterminate | `OUTCOME_UNKNOWN` | Runtime | The reconciliation record with the attempt count. Attempts are bounded (XGN-22). |

| ID | Requirement |
|---|---|
| R-08 | Only the transitions in the table above are legal. Any other transition MUST be refused, and the refusal MUST be recorded (XGN-23). |
| R-09 | A request MUST NOT be cancelled once it has been dispatched. `EXECUTING` and later states have no cancellation edge. |
| R-10 | A request that has been dispatched MUST NOT expire. It resolves through `COMPLETED`, `FAILED`, `OUTCOME_UNKNOWN` and `RECONCILING`. |

### 4.1 Terminal states

`COMPLETED`, `FAILED`, `REJECTED`, `CANCELLED` and `EXPIRED` are terminal. A terminal request record is immutable; the only permitted subsequent change is the addition of an audit event (XGN-25).

| ID | Requirement |
|---|---|
| R-11 | A terminal request MUST NOT be re-opened, re-validated, re-approved, re-dispatched or migrated to another session. Its identifier MUST NOT be reused (XGN-01 §4). |
| R-12 | Only the operator may cancel a request. The runtime cancels a request only on an operator instruction or on the revocation of its session (XGN-04 §5.3). The agent has no cancellation verb for a committed request; it may discard an uncommitted draft instead (XGN-07). |
| R-13 | A cancellation MUST record who cancelled it and when, and MUST NOT be representable as an execution outcome. |

## 5. Expiry of a pending approval

| ID | Requirement |
|---|---|
| R-14 | The pending-approval window is 10 minutes from the moment the request enters `AWAITING_APPROVAL`. The window is not extendable, and the operator does not hold it open by leaving the session idle. |
| R-15 | When the window elapses the request moves to `EXPIRED` with the reason `APPROVAL_WINDOW_EXPIRED`. The request record is retained (XGN-25) and the draft content is discarded (XGN-DEC-018). |
| R-16 | An approval decision that arrives after the window has elapsed MUST be refused and MUST NOT authorise dispatch. The approval record MAY still be written, but it MUST carry the fact that it was late, and it MUST NOT move the request out of `EXPIRED`. |
| R-17 | After an expired approval the session remains at its granted step with no open draft. The agent MUST compose a new draft and commit a new request. The expired request MUST NOT be reused. |

## 6. Approval binding

The approval service (XGN-16) is the owner of the approval record. What binds that record to the request is defined here because it is a property of the request lifecycle.

| ID | Requirement |
|---|---|
| R-18 | An approval MUST carry the `envelope_digest` of the request it approves. An approval without a digest is invalid and MUST NOT authorise dispatch (XGN-02 §7, XGN-DEC-019). |
| R-19 | The runtime MUST recompute the envelope digest immediately before dispatch and compare it with the approved digest. A mismatch MUST abort the dispatch with `INTEGRITY_MISMATCH`, and the request MUST move to `FAILED` without any provider call (XGN-02 §8). |
| R-20 | A material change invalidates the approval. A material change is any difference in the canonical bytes of the envelope. Because a committed envelope is immutable (XGN-07, XGN-DEC-018), a material change can only arise from corruption or tampering, which R-19 catches. |
| R-21 | A change in the provider state that the request was preconditioned on is not a material change of the envelope. The digest still matches and the provider rejects the operation, which is a `FAILED` outcome with a conflict reason (XGN-19, XGN-DEC-020). |
| R-22 | The agent MUST NOT amend a committed request. A change of intent requires a new request, with a new identifier and a new approval. |

## 7. Timestamps and expiry

| ID | Requirement |
|---|---|
| R-23 | Every timestamp in the request lifecycle MUST be stamped by the runtime, in RFC 3339 UTC with a `Z` offset (XGN-01 §6). An agent-supplied timestamp MUST be refused. |
| R-24 | The request expiry is the earlier of the session expiry and one hour after `issued_at`. `expires_at` MUST NOT exceed the session expiry (XGN-02 §4.1). |
| R-25 | The lifecycle stamps `issued_at` at commit, `evaluated_at` on the policy decision, `decided_at` on the approval decision, `started_at` on dispatch and `finished_at` on the outcome. The names and owners of these are those of the Policy Decision, Approval and Execution contracts (XGN-02 §6 to §8). |
| R-26 | An expired request MUST NOT be dispatched and MUST NOT be re-validated. Re-committing the same intent requires a new request. |

## 8. An unknown outcome is reconciled, never repeated

| ID | Requirement |
|---|---|
| R-27 | A request in `OUTCOME_UNKNOWN` or `RECONCILING` MUST NOT be re-committed, re-approved or re-dispatched. Blind re-execution is forbidden (INV-16, XGN-00 DP-08). |
| R-28 | A request in `OUTCOME_UNKNOWN` or `RECONCILING` MUST be resolved by reading authoritative state (XGN-22, XGN-DEC-029). The agent MAY perform reads within the grant to assist, and MAY NOT initiate any further side effect. |
| R-29 | A fresh request for the same operation is permitted only after a reconciliation that concluded `FAILED`, and it MUST be a new request with a new identifier and a new approval. It MUST NOT reuse the identifier, the approval or the digest of the request being reconciled. |
| R-30 | The reconciliation record, the dispatch attempt and the eventual outcome MUST remain linked to the same `request_id`, so that the audit trail shows one request with an uncertain dispatch, not two requests. |

## 9. Relationship to XGN-21

XGN-21 owns the authoritative state machine. This module owns the request states, the legal transitions between them and the evidence each transition requires. XGN-21 MUST compose this request machine into the session, draft, approval, execution and recovery machines, and MUST NOT redefine any state defined here.

| ID | Requirement |
|---|---|
| R-31 | The states in section 3 and the transitions in section 4 are the request sub-machine. XGN-21 MUST preserve them and MUST map them into the composite model without renaming, merging or splitting them. |
| R-32 | A component MUST NOT implement a request lifecycle other than the one specified here, whatever transport it serves (XGN-00 §4.1). |

## 10. Decisions recorded

| ID | Decision | Owner |
|---|---|---|
| XGN-DEC-006 | Every mutating operation requires operator approval, on every transport. | XGN-16, applied in R-07 |
| XGN-DEC-019 | The approval binding covers the complete immutable envelope, including expected provider state. | XGN-31, applied in R-18 |
| XGN-DEC-035 | A request in `OUTCOME_UNKNOWN` or `RECONCILING` MUST NOT be re-committed, re-approved or re-dispatched. A fresh request for the same operation requires a new identifier and a new approval, and is permitted only after a reconciliation concludes `FAILED`. | XGN-06; the reconciliation procedure is owned by XGN-22 |
| XGN-DEC-036 | A request expires at the earlier of the session expiry and one hour after commit. The pending-approval window is 10 minutes. Neither is extendable. | XGN-06 |

The register in XGN-01 §7 is the index; the owner module holds the authoritative statement.

## 11. Open decisions

| ID | Question | Note |
|---|---|---|
| OD-06-1 | Whether an operator may approve a bounded retry pattern in one decision, so that a transient provider failure does not need a second approval. | The default in this module is one approval per request. XGN-16 and XGN-23 own the answer. |
| OD-06-2 | Whether a request that is `FAILED` on a provider conflict may be re-issued by the runtime on the agent's behalf, with the same arguments. | Not settled by this set; the default is that a new request is composed by the agent. |

## 12. Related specifications

- **Depends on:** XGN-01 (identifiers, decision register), XGN-02 (shared contracts), XGN-04 (session, grant and lifetimes).
- **Referenced by:** XGN-07, XGN-08, XGN-15, XGN-16, XGN-17, XGN-21, XGN-22, XGN-23, XGN-25, XGN-31.
- **Predecessor material:** the request lifecycle and the "commit creates the request, which then enters the pipeline at receipt" behaviour of the *Navigation Transport Specification* (0.1-draft), and the states of the *Gitana Surface Map (Composed GET): Proposal*, redistributed here with the transition table made explicit.
- **Out of scope:** the envelope field set, canonicalisation, digests, policy, approval, execution, reconciliation, errors and audit, as listed in section 1.
