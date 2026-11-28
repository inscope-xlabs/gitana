# XGN-27: XGN Operations, Observability and Governance

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-27 | **Group:** F — Assurance
**Owner:** Gitana Architecture
**Depends on:** XGN-00, XGN-01, XGN-02 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 11

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines what an operator can see about a session, what the platform measures, when it raises an alert, who may change what, how versions are supported, how a module, contract or transport is deprecated, how an extension is approved and registered, and how this specification set is governed.

This module owns the operational surface: diagnostics, metrics, alerts, configuration records, the support window, the deprecation process and the extension process. It does not own the behaviour of any component, the requirement text of any other module, or the audit event model and retention schedule (XGN-25).

**Out of scope:** product behaviour; the audit event model and retention periods; the conformance programme; implementation tooling.

## 2. Operational diagnostics

### 2.1 The operator session view

The operator sees a session on the trusted device. This view is diagnostic: it explains what the session is doing. It is not a content viewer.

| Dimension | What the operator sees | What is withheld | Why |
|---|---|---|---|
| Session state | the current state name and the reason it changed | the gateway's view of the state, which is not authoritative | the device record wins every disagreement (XGN-00 §5.1) |
| Step | the step index, the step count and the step name from the grant | the step's internal state machine position | the grant is the operator's own object |
| Gate | whether a gate is active, its type and what clears it | the agent's chat report of the gate | the agent's report is not verified (XGN-05) |
| Draft summary | operation, target repository, branch, path, per-field byte counts, `draft_digest` | the draft content, except through an explicit expansion the operator must request | content is class 2 data (XGN-25 §2) |
| Request status | `request_id`, `envelope_digest`, policy version, approval requirement | the argument values | arguments are class 1 or 2 data |
| Approval state | pending, approved or declined, with `decided_by` and `decided_at` | the agent's rationale for the request | a rationale is agent-authored content |
| Execution state | `execution_id`, attempt count, outcome, provider reference, `result_digest` | the provider response body and the provider credential | class 3 and class 7 data |
| Reconciliation state | whether reconciliation is active, the method, the reads performed and the conclusion | the content read during reconciliation | class 2 data |
| Capability state | how many capabilities are outstanding, and the `capability_id` of the current one | the capability value | class 4 data (PV-08) |
| Audit position | the last verified `event_id` and the chain status | the full event stream in the operator view | the audit log has its own reader |

| ID | Requirement |
|---|---|
| OV-01 | The operator view MUST show the current state, the step, the gate, the draft summary, the request status, the approval state, the execution state and the reconciliation state for any session it is authorised to see. |
| OV-02 | The operator view MUST NOT display a capability value, a credential, or content that has not been explicitly requested and expanded. |
| OV-03 | An operator may request an explicit expansion of the draft or of the diff under review. The expansion MUST be marked as data, MUST be bounded by the limits in PV-06, and MUST be recorded as an audit event. |

### 2.2 Who may see what

| Viewer | May see |
|---|---|
| The operator | the operator view above, for sessions created by that operator |
| The agent | the panel and the capability description for its own session only (XGN-03, XGN-14) |
| The gateway operator | gateway metrics and gateway error codes; no session content |
| The platform operator | platform metrics and configuration records; no session content unless an operator-scoped expansion request is recorded |

| ID | Requirement |
|---|---|
| OV-04 | An operator's access to a session MUST be authenticated on the operator plane and MUST be recorded as an audit event. Access to one session MUST NOT imply access to another. |

## 3. Health and telemetry boundaries

Telemetry describes the system's behaviour. It MUST NOT describe what the system carried.

| ID | Requirement |
|---|---|
| OV-05 | Telemetry MUST NOT contain content, credentials or capabilities, in a value, a label, a tag, an exemplar or an error message. |
| OV-06 | Every metric label MUST be drawn from a fixed enumeration defined in this module or in XGN-27's configuration record. A label MUST NEVER be derived from agent-written content, repository content, a path, a command argument or an error text. |
| OV-07 | A metric whose label would take an unbounded value MUST be aggregated into a bounded bucket before it is emitted. |

| Metric | Type | Meaning | Content safety |
|---|---|---|---|
| `xgn_sessions_active` | gauge | sessions currently in a non-terminal state | count only |
| `xgn_session_state_total` | counter | transitions, labelled by target state | fixed state names |
| `xgn_commands_total` | counter | commands received, labelled by command name and result | fixed names and error codes |
| `xgn_command_rejections_total` | counter | rejections, labelled by error code | fixed error codes |
| `xgn_capability_reuse_total` | counter | capability reuse detections | count only; no `capability_id` |
| `xgn_draft_bytes` | histogram | bytes held per open draft | sizes only |
| `xgn_commit_to_approval_seconds` | histogram | time from commit to the operator's decision | durations only |
| `xgn_execution_outcome_total` | counter | executions, labelled by outcome | fixed outcome names |
| `xgn_execution_duration_seconds` | histogram | dispatch duration | durations only |
| `xgn_reconciliation_total` | counter | reconciliations, labelled by method and result | fixed names |
| `xgn_reconciliation_age_seconds` | gauge | age of the oldest open reconciliation | durations only |
| `xgn_rate_limited_total` | counter | rate-limit rejections, labelled by route | fixed route names |
| `xgn_gateway_rejected_total` | counter | gateway rejections, labelled by reason | fixed reason names |
| `xgn_provider_error_total` | counter | provider errors, labelled by provider and code | fixed names |
| `xgn_device_connected` | gauge | reachability of the trusted runtime | boolean |
| `xgn_audit_events_appended_total` | counter | events appended, labelled by event type | fixed event types |
| `xgn_approval_pending` | gauge | requests awaiting an operator decision | count only |
| `xgn_telemetry_dropped_total` | counter | samples dropped by a rate limit | count only |

| ID | Requirement |
|---|---|
| OV-08 | A diagnostic bundle MUST contain identifiers, digests, counters and state names only. Including content in a bundle requires an explicit operator action, which MUST be recorded as an audit event. |

## 4. Alerts

| Alert | Condition | Severity | Required operator action |
|---|---|---|---|
| Capability reuse | one `capability.reused` event | high | the session is already suspended; review the audit trail, then revoke or resume |
| Unknown outcome | an execution in `OUTCOME_UNKNOWN` for more than 5 minutes | high | reconcile by reads; never re-execute (XGN-22) |
| Provider error rate | more than 5% of dispatch attempts failed in a 15 minute window | medium | check provider status and credential validity |
| Repeated invalid commands | 5 consecutive invalid commands in one session | medium | the session is suspended; review and resume or revoke |
| Device offline | no heartbeat for 60 seconds | medium | check the device; sessions fail fast |
| Device offline, prolonged | no heartbeat for 15 minutes | high | sessions expire; open sessions are listed for review |
| Policy change | a new `policy_version` takes effect | low | confirm the change was intended; the change record is in the audit trail |
| Approval backlog | more than 5 requests pending approval for more than 10 minutes | low | clear the backlog or suspend the session |
| Audit chain break | a hash-chain mismatch in the event stream | high | investigate as an integrity incident; the log is not trusted until resolved |
| Metric cardinality guard | more than 500 distinct label values in a 10 minute window | medium | find the unbounded label and fix it (OV-07) |

| ID | Requirement |
|---|---|
| OV-09 | An alert MUST carry the session, request or execution identifier it concerns, the condition, the threshold crossed and the time. It MUST NOT carry content, a capability value or a credential. |
| OV-10 | A capability-reuse alert MUST be raised at the moment of detection, not at the next poll. |
| OV-11 | An alert that has fired MUST remain visible until an operator acknowledges it. Acknowledgement MUST be recorded as an audit event. |

## 5. Deployment and configuration governance

### 5.1 Who may change what

| Configuration item | Changeable by | Approval required | Impact review | Recorded in |
|---|---|---|---|---|
| Policy rules and defaults | security owner | yes | yes (VC-06) | configuration record and audit trail |
| Grant scope defaults and limits | product owner with architecture | yes | yes (VC-06) | configuration record |
| Capability lifetime and idempotency window | architecture | yes | yes | configuration record |
| Channel tier enablement | operator, per session and per channel | per session | no | session metadata |
| Gateway rate limits and edge configuration | operations | yes | yes | configuration record |
| Provider credential and its scope | security owner | yes | yes | configuration record |
| Contract schema versions | the contract owner (XGN-02) | yes | yes (VC-06) | changelog |
| Retention periods | privacy owner | yes | yes (VC-06) | configuration record |
| Alert thresholds | operations | yes | no | configuration record |
| Module content and requirement text | Gitana Architecture | yes | yes | change set |

### 5.2 Configuration records

Every configuration change produces a record.

| Field | Rule |
|---|---|
| `config_id` | `cfg_` plus 16 base64url characters |
| `item` | The configuration item |
| `previous_value`, `new_value` | The values, in full |
| `actor`, `approver` | `principal_id` values |
| `reason` | A free-text justification, retained with the configuration record, not in the session audit trail |
| `impact_review_ref` | The review required by VC-06, where one applies |
| `effective_at` | RFC 3339 UTC |
| `review_at` | The date the change must be re-examined |

| ID | Requirement |
|---|---|
| OV-12 | A configuration change MUST NOT take effect before its approval and, where required, its impact review are recorded. |
| OV-13 | The configuration in force MUST carry a version identifier, which is the `policy_version` or the limits version referenced by the envelope and the capability description. |
| OV-14 | Configuration records MUST be retained as audit events for 400 days (XGN-25 §8). |

### 5.3 Change control

| ID | Requirement |
|---|---|
| OV-15 | A change to policy or to a limit MUST record an impact review across every dependent module before it is accepted (XGN-02 VC-06). The review MUST name the modules reviewed and the outcome. |
| OV-16 | A limit change that reduces a limit already advertised to an agent MUST take effect only for sessions created after the change. A session already running keeps the limits it was given. |
| OV-17 | A configuration change that cannot be reverted MUST be applied during a declared maintenance window, and the window MUST be recorded. |

## 6. Version support

| Rule | Requirement |
|---|---|
| Supported contract versions | the current major version of the shared contracts and the two preceding minor versions. A consumer MUST reject a request with an unknown major version (VC-03). |
| Supported modules | a module at status `Approved`, `Implemented` or `Verified` in XGN-01 §5.2. A module at `Proposed` is not a support commitment. |
| Supported transports | a transport holding a current qualification record at level L3 (XGN-26 §2.2). |
| Compatibility window | 24 months from the first release that requires a major contract version. A minor version is supported until the next major version is released. |
| End of life | announced 6 months before the end of the compatibility window. |

### 6.1 The end-of-life process

1. The owning module records the deprecation and the date.
2. The deprecation is announced to operators and recorded in the changelog.
3. The 6 month notice period runs; the deprecated object remains fully supported.
4. At the end of the notice period the object is marked `Deprecated` and is no longer supported.
5. Removed or superseded requirements are **marked, not deleted** (XGN-02 §13, XGN-00 §8.10).

| ID | Requirement |
|---|---|
| OV-18 | A deprecation MUST state the replacement, the notice period and the end-of-life date before it takes effect. |
| OV-19 | An end-of-life change MUST NOT remove a requirement clause from the record. The clause is marked with its status and its superseding clause, and remains readable. |

## 7. Deprecation of a module, contract or transport

| Object | Process | Notice | At end of life |
|---|---|---|---|
| A module | the owner marks it `Deprecated` and states the successor | 6 months | the module is retained for traceability and removed from the supported list |
| A contract | the owner publishes a new schema set and marks the old major version deprecated | 6 months | requests on the old major are rejected, not silently upgraded |
| A transport | the owner disables it for new sessions and marks it deprecated | 6 months | existing sessions finish on the old transport; the transport is removed from the supported list |

| ID | Requirement |
|---|---|
| OV-20 | A deprecated transport MUST NOT be enabled for a new session before the end of its notice period, and MUST NOT be enabled at all after it. |
| OV-21 | A transport that is removed MUST NOT leave a live session without a path. A session on a removed transport is moved to a supported transport with unchanged scope, approval and integrity guarantees (XGN-DEC-030), or is closed with a recorded reason. |

## 8. Extension approval

### 8.1 The process

1. **Proposal.** The proposal states the object, its owner, the requirement clauses it would add, and the modules it would change.
2. **Review.** Gitana Architecture reviews the proposal against the single-pipeline rule, the shared contracts and the conformance programme.
3. **Decision.** The decision is recorded as a decision in the register in XGN-01, and the change set carries the module edits.
4. **Registration.** The object is registered in the XGN Framework Specification Index, and any new module identifier is allocated from the catalogue by Gitana Architecture.

### 8.2 Checklist for a new transport

| # | Requirement |
|---|---|
| 1 | The transport extends the transport abstraction in XGN-09 and declares its capability set. It MUST NOT define authority. |
| 2 | The transport converges on the shared request contract in XGN-02 §4. It produces the same envelope shape and MUST NOT create a parallel request model. |
| 3 | The transport adds nothing to the shared contract that is not a minor version change, and the impact review required by VC-06 is recorded. |
| 4 | The transport passes the conformance tests in XGN-26, including the convergence tests TST-37 and TST-38. |
| 5 | The transport creates no parallel authorisation model: no grant of its own, no policy model, no approval concept, no execution route and no audit format (XGN-00 §4.1). |
| 6 | The transport holds a qualification record at level L3 and, for each client it is shipped for, a current client declaration. |
| 7 | The transport is registered in the index, with its module identifier, its requirement prefix and its qualification state. |

### 8.3 Checklist for a new operation

| # | Requirement |
|---|---|
| 1 | The operation is added to the profile in XGN-19 with its required and optional fields, its preconditions, its mutating flag and its approval class. |
| 2 | The operation's argument validation is added to the validator in XGN-08 and to the capability description, from one source of truth (CD-01). |
| 3 | The operation's rate limit and budget are set in XGN-23 and XGN-28. |
| 4 | The operation's audit events are defined in XGN-25. |
| 5 | The operation is covered by at least one passing `TST-nn` test, including its precondition failure. |

### 8.4 Checklist for a new adapter

| # | Requirement |
|---|---|
| 1 | The adapter conforms to the Provider Adapter Contract in XGN-18 and introduces no new credential class. |
| 2 | Provider errors are mapped to the error taxonomy in XGN-23; provider text is never echoed. |
| 3 | The adapter attaches the provider credential at dispatch and discards the response body after the outcome is recorded (PV-05). |
| 4 | The adapter's responses are covered by the reconciliation rules in XGN-22. |

| ID | Requirement |
|---|---|
| OV-22 | A new transport, operation or adapter MUST NOT be enabled for any session until it is registered in the index and holds the qualification required by its checklist. |

## 9. Governance of this specification set

### 9.1 Owners

| Area | Modules | Owner |
|---|---|---|
| A — foundation | XGN-00 to XGN-03 | Gitana Architecture |
| B — session and request | XGN-04 to XGN-08, XGN-31 | Gitana Architecture |
| C — transport | XGN-09 to XGN-14, XGN-28 to XGN-30 | Gitana Architecture with the transport owner |
| D — policy and execution | XGN-15 to XGN-19 | Gitana Architecture with the security owner |
| E — relay and reliability | XGN-20 to XGN-23 | Gitana Architecture with the operations owner |
| F — assurance | XGN-24 to XGN-27 | Gitana Architecture with the privacy and operations owners |

| ID | Requirement |
|---|---|
| OV-23 | Every module MUST have a named owner and a stated review cadence. A module whose owner has changed MUST be reviewed before its next release. |

### 9.2 Change set rules

| Rule | Requirement |
|---|---|
| One change set per concern | A change set MUST touch one owner and state the modules it changes. |
| Contract changes | A change to a shared contract MUST carry the impact review required by VC-06 and the changelog entry required by VC-05. |
| Requirement changes | A change that removes or weakens a requirement MUST state the risk accepted and MUST mark the removed clause rather than delete it. |
| Index changes | A change that adds, renames or retires a module, a contract, a requirement prefix or a decision MUST update the XGN Framework Specification Index in the same change set (XGN-02 §13). |
| Decision changes | A new decision MUST be added to the register in XGN-01 with an owner, and the modules it binds MUST cite it. |
| Consistency | A change set that leaves a module and the index disagreeing is not complete. |
| Status honesty | A change set MUST NOT move a module past `Approved` without the evidence required by XGN-26. |

| ID | Requirement |
|---|---|
| OV-24 | The XGN Framework Specification Index MUST be updated in the same change set as any change to the module catalogue, the requirement prefixes or the decision register. |

## 10. Decisions recorded

| ID | Decision as applied here | Authority |
|---|---|---|
| XGN-DEC-013 | Channel tier C is off by default and is enabled per session, per channel, only after the channel passes qualification. | XGN-10 |
| XGN-DEC-030 | A session may move to another transport without widening scope, skipping approval or invalidating an existing approval. | XGN-09 |
| XGN-DEC-031 | The retention periods that this module's configuration records follow: audit events 400 days; request records 90 days; session metadata 30 days after expiry; draft content minutes. | XGN-25 |
| XGN-DEC-033 | The compatibility window is 24 months from the first release requiring a major contract version, and the end-of-life notice period is 6 months. | XGN-27 |

## 11. Open decisions

| ID | Question | Default position in this module |
|---|---|---|
| OD-27-1 | What is the review cadence for each module? | Not settled. The owner table in section 9.1 is the starting point. |
| OD-27-2 | Which alert delivery channels are supported, and what is the acknowledgement deadline? | Not settled. The alerts exist; the delivery and acknowledgement path is open, and it is shared with OD-24-3 in XGN-24. |
| OD-27-3 | Are the alert thresholds fixed or per deployment? | Fixed by default in this module; a per-deployment override is permitted for the device-offline thresholds only, and the override is a configuration record. |

## 12. Related specifications

- **Depends on:** XGN-00, XGN-01, XGN-02.
- **Referenced by:** every module in the set; XGN-20, XGN-22, XGN-26 and XGN-25 in particular.
- **Predecessor material:** the deployment checklist in §13 of the *Navigation Transport Specification* (0.1-draft), the operational and reliability recommendations in §3.9 and §4 of technical review B, and the governance rules in XGN-02, redistributed here.
- **Out of scope:** audit event model and retention (XGN-25), conformance programme (XGN-26), requirement behaviour of any component.
