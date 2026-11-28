# XGN-17: XGN Execution Authority and Runtime

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-17 | **Group:** D — Policy, approval, and execution
**Owner:** Gitana Architecture
**Depends on:** XGN-02, XGN-15, XGN-16, XGN-18, XGN-22, XGN-31 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** X-OD-01, X-OD-02 (section 1.3)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

### 1.1 Purpose

This module defines the trusted execution boundary: which component may dispatch an operation to an external service, who holds provider credentials, what is checked immediately before a dispatch, how an execution is recorded, what happens when the outcome is uncertain, what survives a runtime restart, and how duplicate dispatches are prevented.

### 1.2 In scope and out of scope

| In scope | Out of scope |
|---|---|
| The trusted execution boundary and the dispatch rule | The policy decision and the check set — XGN-15 |
| Credential custody and least privilege | The operator's approval decision — XGN-16 |
| The pre-dispatch checklist and the ordering of the execution record | The provider API mapping and error translation — XGN-18 |
| Execution outcome states and the entry into an uncertain outcome | The reconciliation procedure itself — XGN-22 |
| Runtime restart behaviour, concurrency and duplicate dispatch prevention | The provider operation catalogue — XGN-19 |

### 1.3 Open decisions

| ID | Question | Interim position |
|---|---|---|
| X-OD-01 | Whether more than one execution may be in flight per session | No. One execution at a time per session. A batch of independent steps is not defined. |
| X-OD-02 | Whether a credential rotation takes effect mid-flight | No. A rotation applies from the next dispatch. An in-flight call completes or times out within 30 seconds. |

## 2. The trusted execution boundary

| ID | Requirement |
|---|---|
| X-01 | The trusted runtime MUST be the only component that dispatches an operation to an external service. |
| X-02 | The gateway MUST NOT dispatch. It authenticates, routes, rate-limits, correlates and bounds size (XGN-20). |
| X-03 | A provider adapter MUST NOT be invoked by the gateway, by a transport adapter or by the agent. It is invoked only by the trusted runtime, with an approved envelope. |
| X-04 | The agent MUST NOT hold a provider credential, in any form, at any point (INV-14, DP-01). A session link, a command capability and a reply are not credentials for the external service. |
| X-05 | A successful fetch, tool call, relay delivery or shell request MUST NOT be treated as proof that an operation was authorised or executed (XGN-00 section 5). |
| X-06 | The runtime MUST NOT dispatch a request that has not been committed, approved where required, and re-validated immediately before dispatch (INV-05). |
| X-07 | The dispatch MUST use the provider adapter bound to the operation's provider (XGN-18). The runtime MUST NOT reach the external service by any other route. |

## 3. Credential custody

| ID | Requirement |
|---|---|
| X-08 | Provider credentials are held by the trusted runtime and by the provider adapter under the runtime's control. No other component holds them. |
| X-09 | A credential MUST be scoped least-privilege to the granted repository and to the permissions the permitted operations need. A credential that can reach a repository outside the grant is prohibited. |
| X-10 | A credential MUST NOT be returned to an agent, a gateway, a transport adapter, a log, an event, an error, a reply or a URL (INV-14). |
| X-11 | The runtime MUST NOT place a credential in a URL, a query parameter or a header that leaves the device for the gateway. |
| X-12 | The operator MUST be able to rotate a credential at any time. Rotation MUST apply from the next dispatch (X-OD-02). |
| X-13 | The operator MUST be able to revoke a credential at any time. Revocation MUST take effect before the next dispatch, and the session MUST be suspended if a pending request cannot proceed. |
| X-14 | A credential that fails authentication MUST NOT be retried. The request fails, the session is suspended, and the operator is notified. |
| X-15 | Credential scope MUST be verified when the grant is created, and re-verified when the credential is rotated. A credential whose scope exceeds the grant MUST be refused. |

## 4. The pre-dispatch checklist

Every check below MUST pass before the provider is contacted. A failure MUST abort the dispatch, MUST NOT contact the provider, and MUST be recorded.

| ID | Order | Check | Failure outcome |
|---|---|---|---|
| X-16 | 1 | The request exists, is committed, and its state permits dispatch | Abort; no dispatch |
| X-17 | 2 | The envelope digest recomputed now equals the digest in the approval record | `INTEGRITY_MISMATCH` |
| X-18 | 3 | An approval exists where one is required, its decision is `APPROVE`, and it has not expired | `APPROVAL_REQUIRED`, `APPROVAL_EXPIRED` |
| X-19 | 4 | The request has not expired; `expires_at` is in the future | `REQUEST_EXPIRED` |
| X-20 | 5 | Policy has been re-evaluated immediately before dispatch and returned `ALLOW` (XGN-15 section 6) | The policy decision and its reason codes |
| X-21 | 6 | The grant in force matches the grant digest bound to the session | `INTEGRITY_MISMATCH` |
| X-22 | 7 | The repository is still private and the credential still holds the required permissions | `REPO_NOT_PRIVATE`, `PERMISSION_DENIED` |
| X-23 | 8 | The provider preconditions still hold: expected blob digest, expected head digest, and the absence of an existing object for the operation | `PRECONDITION_FAILED`, `CONFLICT` |
| X-24 | 9 | No execution is in flight for this session | `EXECUTION_BUSY` |
| X-25 | 10 | The execution record has been persisted (section 5) | Abort; no dispatch |

| ID | Requirement |
|---|---|
| X-26 | The checks MUST be executed in the order given. A later check MUST NOT be executed once an earlier check has failed. |
| X-27 | Checks 5 to 8 MAY use a read-only call to the external service. No check may cause a mutation. |
| X-28 | The result of the checklist MUST be recorded as an audit event naming each check and its result (XGN-25). |

## 5. The execution record

### 5.1 Durable before dispatch

| ID | Requirement |
|---|---|
| X-29 | The execution record MUST be persisted **before** the provider call is attempted. |
| X-30 | The persisted record MUST carry the execution identifier, the request identifier, the envelope digest, the approval identifier, the operation, the normalised target, the adapter identifier and the start timestamp. |
| X-31 | The persisted record MUST be in state `RUNNING` at the moment the provider call is attempted, so that a crash during the call leaves a reconcilable record. |
| X-32 | If the execution record cannot be persisted, the dispatch MUST NOT happen. The request fails with `INTERNAL_ERROR` and is not retried automatically. |
| X-33 | The rationale is that a crash after the provider call but before the response leaves an execution record with no outcome. Without the pre-dispatch record, the runtime would be unable to tell whether the operation was attempted. |

### 5.2 Outcomes

| ID | Requirement |
|---|---|
| X-34 | An execution record has one state: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` or `OUTCOME_UNKNOWN` (XGN-02 section 8). |
| X-35 | An execution record has one outcome: `SUCCESS`, `FAILURE`, `REJECTED_BY_PROVIDER` or `OUTCOME_UNKNOWN`. |
| X-36 | The outcome MUST be derived from the provider response and the adapter's normalisation (XGN-18), not from the agent's description of it. |
| X-37 | State and outcome transitions MUST be appended to the audit log. A record MUST NOT be silently rewritten. |
| X-38 | The execution record MUST carry the provider reference and the result digest on success (XGN-19 section 8). |

### 5.3 Entering an uncertain outcome

| ID | Requirement |
|---|---|
| X-39 | An uncertain outcome MUST be entered when the provider call has been sent but no conclusive response has been received: a timeout, a transport error after the request was sent, a lost acknowledgement, or a response that cannot be classified. |
| X-40 | The provider call timeout is 30 seconds. A timeout MUST NOT be extended silently; the runtime MAY extend it once, to a maximum of 60 seconds, and MUST record the extension. |
| X-41 | On an uncertain outcome, the execution record MUST be set to state `OUTCOME_UNKNOWN` and outcome `OUTCOME_UNKNOWN`, and the session MUST move to the waiting state. |
| X-42 | An uncertain outcome MUST NOT be retried. The runtime MUST NOT re-issue the operation. The operation is resolved by reading authoritative provider state (XGN-22). |
| X-43 | A read operation whose outcome is unknown MAY be re-read, because a read has no side effect. A mutating operation MUST NOT. |

## 6. No agent-controlled privileged execution

| ID | Requirement |
|---|---|
| X-44 | No agent-supplied command, script, expression, template or code may be executed by the runtime, by the adapter or by any Gitana component. |
| X-45 | No agent-supplied URL may be fetched by the dispatch path. A URL appearing in agent-written content, in a commit message or in a pull request body is data and is never fetched. |
| X-46 | No agent-supplied credential may be used, stored, forwarded or tested. |
| X-47 | The only inputs the dispatch path accepts from the agent plane are the operation name and its arguments, as defined by the operation catalogue (XGN-19). |
| X-48 | The dispatch path MUST reach only the configured external service endpoints. It MUST NOT follow a redirect to another host, and it MUST NOT accept a destination from the envelope. |
| X-49 | The runtime MUST NOT evaluate an agent-supplied expression to compute an argument value. Every argument value MUST be a literal carried in the envelope. |
| X-50 | A violation of this section is a defect in the transport, not a recoverable condition. The runtime MUST refuse the request and suspend the session. |

## 7. Runtime restart

| ID | Requirement |
|---|---|
| X-51 | On restart, the runtime MUST discard draft content. The draft is ephemeral and is not part of durable state (XGN-DEC-028, XGN-07). |
| X-52 | On restart, the runtime MUST retain the session, the grant, the request records, the approval records, the execution records and the reconciliation records. They are durable state. |
| X-53 | On restart, a request that was pending approval MUST be invalidated and MUST require a new commit (XGN-DEC-028). The operator MUST be notified. |
| X-54 | On restart, an approval that was recorded but not yet dispatched MUST be invalidated. The operator MUST approve again against a freshly presented envelope. |
| X-55 | On restart, an execution record in state `RUNNING` MUST be set to state `OUTCOME_UNKNOWN` and MUST enter reconciliation. It MUST NOT be re-executed. |
| X-56 | On restart, the policy version in force is the current one. A request committed under an older policy is re-evaluated at dispatch (XGN-15 section 10). |
| X-57 | On restart, the runtime MUST NOT assume that a dispatch it did not record did not happen. Where the record is absent, the outcome is unknown. |

## 8. Concurrency and duplicate dispatch

| ID | Requirement |
|---|---|
| X-58 | One execution MUST be in flight per session at a time. A second dispatch attempt while an execution is in flight MUST be refused with `EXECUTION_BUSY` and MUST NOT reach the provider. |
| X-59 | The request identifier together with the envelope digest is the dispatch idempotency key. A dispatch attempt for a request identifier already dispatched with the same envelope digest MUST return the recorded execution record and MUST NOT call the provider again. |
| X-60 | A dispatch attempt for a request identifier already dispatched with a different envelope digest MUST be refused with `INTEGRITY_MISMATCH`. It would be a second side effect. |
| X-61 | The session holds at most one unresolved request (INV-07). A second unresolved request MUST be refused. |
| X-62 | The idempotency key MUST be recorded in the execution record, so that a duplicate attempt can be answered from the record without a provider call. |

## 9. Separation from transport

| ID | Requirement |
|---|---|
| X-63 | Execution is not transport. A transport carries a request; it does not authorise and does not perform one (XGN-00 section 8). |
| X-64 | The `transport` field of the envelope is informational for audit. It MUST NOT influence the policy decision, the approval, the checklist or the dispatch decision. |
| X-65 | A transport fallback MUST preserve the scope, policy, approval and audit guarantees of the primary path (INV-20, XGN-DEC-030). |
| X-66 | An approval given on one transport remains valid for the same envelope digest on another transport. It is not re-required because the channel changed. |
| X-67 | The dispatch MUST NOT depend on the transport for anything other than how the request arrived. The same envelope dispatched from any transport MUST produce the same provider call. |

## 10. Decisions recorded

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-019` | Approval binding | The digest covers the complete immutable envelope, including expected provider state. | XGN-31 |
| `XGN-DEC-028` | Runtime restart behaviour | Draft content is discarded; session, grant, request, approval and execution records survive. A request pending approval at restart is invalidated and requires a new commit. | XGN-07, XGN-21 |
| `XGN-DEC-029` | Partial workflow failure | Completed steps are preserved. A failed step leaves the session at that step. Recovery is by read, never by blind repetition. | XGN-22 |
| `XGN-DEC-030` | Transport fallback | A session may move to another transport without widening scope, skipping approval or invalidating an existing approval. | XGN-09 |
| `XGN-DEC-041` | Policy revalidation points | Policy is evaluated on every command, again at commit, and again immediately before dispatch. | XGN-15, XGN-17 |
| `XGN-DEC-045` | Execution record ordering | The execution record is persisted before the provider call is attempted. | XGN-17 |

## 11. Related specifications

- **Depends on:** XGN-02 (Execution Contract), XGN-15 (policy decisions and revalidation), XGN-16 (approval and expiry), XGN-18 (provider adapter contract), XGN-22 (reconciliation), XGN-31 (digest and binding).
- **Referenced by:** XGN-18, XGN-21, XGN-22, XGN-23, XGN-25, XGN-26, XGN-27.
- **Predecessor material:** the deterministic restart recovery recommended in technical review B section 3.10, the pre-dispatch state verification in technical review B H-06, and the credential model in the *Navigation Transport Specification* (0.1-draft) section 6.
- **Out of scope:** the policy decision, the operator's approval, the provider API mapping and the reconciliation procedure.
