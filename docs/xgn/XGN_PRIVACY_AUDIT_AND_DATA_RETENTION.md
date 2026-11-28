# XGN-25: XGN Privacy, Audit and Data Retention

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-25 | **Group:** F — Assurance
**Owner:** Gitana Architecture
**Depends on:** XGN-00, XGN-01, XGN-02 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 13

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module classifies the data XGN handles, states where each class may be stored, defines the audit event model and the immutable event history, defines redaction, and fixes the retention schedule.

This module owns the **Event Contract** (XGN-02 §2), the data classification, the retention schedule and the deletion procedure. It does not own request lifecycle rules, the capability lifecycle, the panel format, the capability description or the transport framing.

**Out of scope:** the request envelope shape, the draft semantics, the operation catalogue, the error taxonomy and any provider-specific data handling.

## 2. Data classification

Sensitivity levels: **S0 public** — safe to disclose anywhere the agent can see. **S1 internal** — identifiers, digests and counts; safe in logs and metrics but not meaningful to an attacker. **S2 confidential** — operator-scoped repository and session metadata. **S3 secret** — anything that grants or amplifies authority.

| # | Data class | Level | Examples | May be stored in | Must not appear in |
|---|---|---|---|---|---|
| 1 | Agent-written content | S2 | draft field values, file content, commit messages | runtime memory only, until the draft is discarded | gateway, metrics, logs, events, error text, approval summary |
| 2 | Repository content | S2 | file text, path names, branch names read under the grant | runtime memory during an explicit read; provider response handling | gateway, chat beyond the explicit read, metrics, events |
| 3 | Provider credentials | S3 | the provider token, any credential | the runtime credential store only | everything else, without exception (H-15, XGN-24) |
| 4 | Command capabilities | S3 | `cap_` values, session link | the link the agent is expected to use, and the runtime registry as a `capability_id` digest | logs, events, panels, errors, metrics, notifications (H-16) |
| 5 | Session and grant identifiers | S1 | `session_id`, `grant_id`, `request_id`, `principal_id` | gateway, runtime, audit log, metrics labels | nowhere — these are the safe residue |
| 6 | Digests | S1 | `envelope_digest`, `draft_digest`, `result_digest` | gateway, runtime, audit log, metrics | nowhere; a digest is not content |
| 7 | Provider responses | S2 | provider API bodies, error text, refs | runtime during dispatch and reconciliation; the provider reference and result digest persist | gateway, chat, logs, events, metrics |
| 8 | Operational telemetry | S1 | counters, durations, state names | metrics store, alerting | content, credentials, capabilities, free-text labels |
| 9 | Operator onboarding text | S2 | operator-authored instructions shown to the agent | runtime, versioned and hashed into the audit trail | logs, events, metrics |
| 10 | Approval records | S1 | `approval_id`, decision, `decided_by`, `envelope_digest` | runtime, audit log | content, chat, gateway |
| 11 | Operator identity | S2 | the operator's account reference | runtime, audit log as `principal_id` | chat, agent-visible replies |

| ID | Requirement |
|---|---|
| PV-01 | Every data item handled by any XGN component MUST be assigned to one of the classes in section 2, and each component MUST enforce the storage restriction of its class. |
| PV-02 | The gateway MUST NOT retain a class 1, 2 or 7 item. The gateway MAY retain class 5 and 6 items only for the lifetime of a request. |

## 3. Content handling rules

| ID | Requirement |
|---|---|
| PV-03 | The trusted runtime MAY hold draft content in memory for the lifetime of the draft. The draft is discarded on commit, cancel, expiry, suspension, revocation or runtime restart (XGN-DEC-018). The discard MUST complete within 60 seconds of the trigger. |
| PV-04 | A committed envelope is immutable and is retained until the request is resolved. Its content is then deleted within 24 hours. Its metadata, digests and outcome are retained for 90 days as a request record. |
| PV-05 | A provider response body MUST NOT be retained beyond the execution record. The execution record keeps the outcome, the provider reference and the result digest. The body MAY be re-read from the provider during reconciliation, which is itself audited. |
| PV-06 | An explicit content read MUST be bounded at 4 KB per page and 16 pages per field, and MUST be marked as data in the reply (XGN-14, XGN-24). |
| PV-07 | Operator onboarding text MUST be versioned and hash-recorded in the audit trail, and MUST NOT include repository content. |

## 4. Secrets

| ID | Requirement |
|---|---|
| PV-08 | A capability, a credential or a provider token MUST NOT appear in an event, a log line, an error message, a panel, a metric, a metric label, a notification or a diagnostic bundle. |
| PV-09 | There is exactly one deliberate exception: the command capability in the link the agent is expected to use. That link MUST carry the capability and nothing else that is secret-bearing, and the capability MUST NOT be repeated in any other artefact. |
| PV-10 | A capability MUST be replaced by a stable `capability_id` — a digest of the capability — wherever an identifier is needed for correlation. The `capability_id` is not usable to act. |
| PV-11 | The pairing challenge is a non-secret device-displayed code. It MUST NOT appear in an agent-visible reply and MUST NOT be recorded in an event (XGN-DEC-009). |

## 5. The audit event model

The Event Contract shape is defined in XGN-02 §9. This module extends it, as a minor change under VC-02, with the chain fields below; the impact review required by VC-06 covers XGN-02, XGN-22 and XGN-27.

| Field | Rule |
|---|---|
| `event_id` | `evt_` plus 16 base64url characters. Never reused. |
| `event_type` | Dotted lowercase identifier, `<object>.<past-tense-verb>`. |
| `occurred_at` | RFC 3339 UTC, stamped by the runtime. |
| `session_id` | Present for every session-scoped event. |
| `request_id` | Present where a request exists. |
| `actor` | `kind` is one of `operator`, `agent`, `runtime`, `provider`, `gateway`; `id` is a `principal_id`. |
| `attributes` | Digests, identifiers, counts, sizes, state names, error codes. MUST NOT contain content, credentials, capabilities or repository data. |
| `outcome` | `SUCCESS`, `FAILURE` or `REJECTED`. |
| `prev_event_id` | The previous event in the session chain. Null for the first event. |
| `event_digest` | SHA-256 over the canonical event, excluding `event_digest` itself. |
| `component` | The emitting component: `runtime`, `gateway` or `provider_adapter`. |

| ID | Requirement |
|---|---|
| PV-12 | Events are append-only. A correction MUST be a new event that references the event it corrects. An event MUST NOT be edited or deleted before the end of its retention period. |
| PV-13 | Each session's events form a hash chain. A break in the chain MUST be detectable by any reader of the log and MUST be raised as an alert. |

### 5.1 Event coverage

| Event type | Recorded when | Information recorded | Never recorded |
|---|---|---|---|
| `session.created` | the operator creates the session and grant | `grant_id`, scope triple, base branch, ordered steps, limits, `policy_version`, `issued_at`, `expires_at` | operator identity beyond `principal_id`, credentials |
| `pairing.confirmed` | the operator confirms the pairing challenge on the device | `session_id`, actor, timestamp, result | the challenge value |
| `command.received` | every command that reaches the runtime | command name, `seq`, `capability_id`, result code, state before and after | the capability value, arguments, content |
| `draft.modified` | each accepted change to the draft | field name, change kind, `part_id`, byte count, resulting `draft_digest` | the field value |
| `preview.generated` | a preview succeeds | `draft_digest`, per-field sizes, validation result, normalised path | content |
| `request.committed` | a commit is accepted | `request_id`, `envelope_digest`, `policy_version`, approval requirement, argument names | argument values |
| `request.approved` | the operator approves | `approval_id`, decision, `decided_by`, `decided_at`, `envelope_digest`, expiry | summary content |
| `request.declined` | the operator declines | `approval_id`, decision, `decided_by`, `decided_at`, `envelope_digest` | summary content |
| `execution.attempted` | dispatch begins | `execution_id`, `request_id`, `approval_id`, `envelope_digest`, operation, target reference, adapter id | the provider credential, the request body |
| `execution.completed` | the outcome is recorded | `execution_id`, outcome, provider reference, `result_digest`, duration | the provider response body |
| `recovery.reconciled` | reconciliation activity occurs | `execution_id`, method, the authoritative state read, conclusion, operator involvement | content |
| `session.suspended` | automatic suspension on detection | trigger, actor, `capability_id` | the capability value |
| `session.revoked` | revocation | reason code, actor, whether outstanding capabilities were invalidated | — |
| `capability.reused` | reuse of a consumed or superseded capability | `capability_id`, detection class, `seq`, session state, fingerprint signals | the capability value |

## 6. Accountability

The audit trail MUST distinguish four things. Technical review B §3.8 identified this as essential, and this module makes it normative.

| Question | The record that answers it | Event source |
|---|---|---|
| What the agent requested | the received command and the declared argument names | `command.received`, `draft.modified` |
| What Gitana accepted | the committed envelope and its digest | `request.committed`, the policy decision in XGN-15 |
| What the operator approved | the approval bound to the envelope digest | `request.approved` |
| What the provider actually executed | the execution attempt and its outcome | `execution.attempted`, `execution.completed` |

| ID | Requirement |
|---|---|
| PV-14 | An audit review MUST be able to state, for one request, all four answers separately and to detect a divergence between them. |
| PV-15 | A divergence between the approved `envelope_digest` and the dispatched `envelope_digest` MUST be raised as an integrity incident, not reconciled silently. |

## 7. Log redaction

| What is redacted | Where | How |
|---|---|---|
| The command capability | gateway access logs, edge logs, runtime application logs, crash dumps | the query string is excluded from the access log for the session route; where a request line must be logged, the capability is replaced by `capability_id` |
| The provider credential | everywhere | never constructed outside the credential store; the adapter injects it at dispatch and removes it from the request context before logging |
| Agent-written content | runtime info logs, traces, metrics | content is never logged; only byte counts and digests |
| Repository content | runtime info logs, traces, metrics | as above |
| Provider response text | error messages and logs | the provider text is not echoed; a provider error is mapped to a stable XGN error code |
| Operator identity | shared logs and metrics | `principal_id` only |
| Operator free-text notes | the audit log | the note is not part of an event; if retained at all it belongs to the session metadata and is deleted with it |
| The pairing challenge | events and logs | never recorded |

| ID | Requirement |
|---|---|
| PV-16 | Redaction MUST be applied at the point of writing, not by a later filter. A log pipeline MUST NOT be the only place a secret is removed. |
| PV-17 | Redaction MUST be verifiable by the privacy test in XGN-26, which greps the gateway log, the runtime log and the event stream for capability-shaped and content-shaped values. |

## 8. Retention

| Data class | Retention period | Starts at | Deleted by |
|---|---|---|---|
| Draft content (class 1), in memory | minutes: until commit, cancel, expiry, suspension, revocation or restart, and within 60 s of the trigger | command acceptance | the runtime |
| Committed envelope content | until the request is resolved, then within 24 h | resolution | the runtime |
| Provider response body | not retained beyond the execution record | dispatch | the runtime |
| Audit events | 400 days | event time | the audit log, by expiry |
| Request records (metadata, digests, outcome) | 90 days | request resolution | the runtime |
| Session metadata (identifiers, scope, grant, timestamps) | 30 days after session expiry | expiry | the runtime |
| Capability registry entries | 12 minutes — the 10 minute lifetime plus the 90 second idempotency window | issuance | the gateway and the runtime |
| Operator onboarding text | session lifetime plus 30 days | session expiry | the runtime |
| Operational telemetry | 30 days | collection | the metrics store |
| Reconciliation records | 90 days | reconciliation | the runtime |
| Diagnostic bundles | 30 days | creation | the support store |
| Content-bearing backups | 35 days | creation | the backup store |

| ID | Requirement |
|---|---|
| PV-18 | The retention periods in this section are the periods. A component MUST NOT extend them and MUST NOT shorten them without a change record under XGN-27 and an impact review under VC-06. |
| PV-19 | Audit events MUST NOT be deleted by an operator action before the end of their retention period. An erasure request is honoured for classes 1, 2 and 7 only, is recorded as an audit event, and is executed within 30 days. |

### 8.1 Deletion procedure

1. The trigger is recorded as an audit event.
2. The owning component deletes the data within the period stated for its class.
3. Content-bearing backups expire within 35 days and are not restored over a deletion request; the restore procedure MUST re-apply the deletion.
4. After each deletion run, the runtime produces a deletion report carrying counts only, and appends it to the audit log.

## 9. Component responsibilities

| Component | Responsibility |
|---|---|
| Gateway | Retains nothing. No content, no authoritative state, no capability registry beyond the 12 minute window, no reply cache for a content-bearing reply (XGN-DEC-027). |
| Trusted runtime | Holds the draft, the committed envelope, the approval and the execution records, and performs deletion. It is the only component that holds content beyond a request. |
| Audit log | Append-only, chain-verified, holds identifiers and digests only, retains for 400 days. |
| Provider adapter | Holds the provider credential, attaches it at dispatch, discards the response body after the outcome is recorded. |
| Transport adapter | Carries content in transit and MUST NOT persist it. |

| ID | Requirement |
|---|---|
| PV-20 | The audit log MUST be a separate store from the session state, so that suspending a session does not alter the history and clearing a session does not erase it. |

## 10. Repository content and chat

| Crosses into chat | Does not cross into chat |
|---|---|
| The agent's own draft content, in an explicit read, marked as data | Repository content the agent did not request |
| File content the agent explicitly reads, bounded and marked as data | The content of any other session |
| Path, branch and operation names within the granted scope | Provider credentials, operator identity, the policy, the grant beyond its scope summary |
| The capability link the agent is expected to use | The capability value in any other form |
| The runtime-generated approval summary, on the device | The full command URL, except the capability link the agent is expected to use |

| ID | Requirement |
|---|---|
| PV-21 | Repository content MUST NOT be included in a reply except by an explicit read, marked as data, and bounded by PV-06. The chat application, the fetch service and the network intermediaries are outside Gitana's control; XGN does not claim content stays private from them. |
| PV-22 | A reply MUST NOT contain a complete command URL other than the capability link the agent is expected to use. |

## 11. Decisions recorded

| ID | Decision as applied here | Authority |
|---|---|---|
| XGN-DEC-018 | The draft is discarded on commit, cancel, expiry, suspension, revocation or runtime restart; the committed envelope survives in immutable form until the request is resolved. | XGN-07 |
| XGN-DEC-027 | The gateway MUST NOT cache a reply carrying agent-authored content. | XGN-20 |
| XGN-DEC-028 | On runtime restart, draft content is discarded; session, grant, request, approval and execution records survive; a request pending approval is invalidated and requires a new commit. | XGN-07, XGN-21 |
| XGN-DEC-031 | Retention periods: audit events 400 days; request records 90 days; session metadata 30 days after expiry; draft content minutes; provider response bodies not retained. | XGN-25 |

## 12. Open decisions

| ID | Question | Default position in this module |
|---|---|---|
| OD-25-1 | May an operator request an early export of the audit trail of a session for external review? | Not provided in this version. Exports would leave Gitana's control and would need their own classification. |
| OD-25-2 | Is the 400 day audit retention compatible with the jurisdictions Gitana operates in? | Not verified. The retention review per XGN-27 must confirm it before release. |
| OD-25-3 | Should operator onboarding text be retained beyond the session, as a reusable template? | No. It is retained with the session metadata for 30 days after expiry. |

## 13. Related specifications

- **Depends on:** XGN-00, XGN-01, XGN-02.
- **Referenced by:** XGN-20, XGN-21, XGN-22, XGN-26, XGN-27, and every module that emits an event.
- **Predecessor material:** the audit and observability recommendation in §3.8 of technical review B, and the logging and retention rules of the *Navigation Transport Specification* (0.1-draft), redistributed and extended here.
- **Out of scope:** request envelope shape (XGN-02, XGN-06), draft semantics (XGN-07), panel format (XGN-14), error taxonomy (XGN-23), metric definitions and alerting (XGN-27).
