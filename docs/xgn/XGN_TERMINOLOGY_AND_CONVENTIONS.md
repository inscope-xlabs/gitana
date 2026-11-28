# XGN-01: XGN Terminology and Conventions

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-01 | **Group:** A — Framework foundation
**Owner:** Gitana Architecture
**Depends on:** none | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** none outstanding at this level

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose

This module defines the canonical vocabulary, identifier schemes, notation and normative conventions used by every XGN module. It also carries the **decision register**: the authoritative index of decisions that have been taken while building the specification set, and the owner module for each.

A term defined here MUST NOT be redefined with a different meaning elsewhere in the set. Where another module needs to sharpen a term, it MUST say how, not restate it differently.

## 2. Canonical framework names

| Term | Meaning |
|---|---|
| **XGN Framework** | The complete navigation and request architecture described by this specification set. |
| **XGN Specification Set** | The index and all subordinate XGN module specifications. |
| **XGN Surface Map** | The discoverable set of panels, states, actions and commands reachable within an XGN session. |
| **Composed GET** | The URL-based command-composition transport mechanism within XGN. Not a name for the framework. |
| **XGN Session** | A bounded, authorised interaction context governed by a grant and a session lifecycle. |
| **XGN Request** | A structured request submitted through an XGN-supported transport and represented using the shared request contract. |
| **XGN Gateway** | The logical entry point that receives and routes XGN navigation or request traffic. Its physical deployment is defined in XGN-20. |
| **XGN Panel** | One agent-visible reply describing state, task, draft, last outcome and the commands legal right now. |
| **XGN Command** | A single agent-issued instruction within a transport, such as `set` or `commit`. |
| **XGN Draft** | The mutable, editable representation of a request under construction, held by the trusted runtime. |
| **XGN Envelope** | The immutable canonical representation of a committed request. |
| **XGN Grant** | The explicit, bounded authority a session operates under. |
| **XGN Capability** | A short-lived, single-use, session-bound command capability carried in a link. |

### 2.1 Retired and superseded labels

The following labels referred to the same evolving material and are **not** separate frameworks, subsystems or specifications. They MUST NOT be used as names for XGN components.

| Retired label | XGN term |
|---|---|
| Navigation Transport specification | Composed GET transport (XGN-10, XGN-28 to XGN-30) within the transport abstraction (XGN-09) |
| Composed GET (as a framework name) | Composed GET transport |
| Surface Map (as a standalone specification) | XGN Surface Map (XGN-03, XGN-14) |
| PIN (as a session secret) | Pairing challenge (XGN-05) |
| Draft hash | Request digest (XGN-31) |
| Rolling link | Command capability (XGN-29) |
| Base link | Capability base (XGN-29) |
| `seq` | Session sequence number (XGN-28) |
| Tier A / B / C | Channel tier (XGN-10) |

## 3. Component names

| Term | Meaning | Module |
|---|---|---|
| **Agent** | The AI agent or permitted client that discovers, navigates and submits. Never an authority. | XGN-00 |
| **Operator** | The human on the trusted device. The only source of grant, approval and revocation. | XGN-05, XGN-16 |
| **Trusted runtime** | The Gitana runtime on the operator's device. Holds all authoritative state. | XGN-17 |
| **Gateway** | The XGN ingress component. Routing, rate limiting, transport protections only. | XGN-20 |
| **Transport adapter** | The component that frames an XGN Request for one channel. | XGN-09 |
| **Provider adapter** | The component that maps an approved operation onto an external service API. | XGN-18 |
| **External service** | The provider system, such as GitHub. | XGN-18 |
| **Policy engine** | The component that evaluates the grant and scope against a proposed operation. | XGN-15 |
| **Approval service** | The component that captures and binds the operator's decision. | XGN-16 |
| **Audit log** | The append-only record of lifecycle events. | XGN-25 |

## 4. Identifier schemes

All identifiers are stable, opaque and MUST NOT encode secrets.

| Identifier | Form | Assigned by | Notes |
|---|---|---|---|
| `session_id` | `ses_` + 16 base64url chars | Runtime | One session. Never reused. |
| `grant_id` | `grt_` + 16 base64url chars | Runtime | One grant. Bound to one session. |
| `request_id` | `req_` + 16 base64url chars | Runtime | One committed request. Never reused. |
| `draft_id` | `drf_` + 16 base64url chars | Runtime | One open draft. At most one per session. |
| `step_id` | `stp_` + 8 base64url chars | Runtime | One step within a grant. |
| `turn_id` | `trn_` + 8 base64url chars | Runtime | One gate or turn. |
| `approval_id` | `apr_` + 16 base64url chars | Runtime | One approval decision. |
| `execution_id` | `exe_` + 16 base64url chars | Runtime | One execution attempt. |
| `event_id` | `evt_` + 16 base64url chars | Runtime | One audit event. |
| `capability` | `cap_` + 22 base64url chars | Gateway | One command capability. 128 bits of entropy. |
| `principal_id` | `prn_` + 12 base64url chars | Runtime | One acting principal. |
| `seq` | Integer, monotonic from 1 | Runtime | Session sequence number. |

**Rules.**

- Identifier suffixes MUST be generated by a cryptographically secure random source.
- Identifiers MUST NOT be reused, including after revocation.
- A sequence number MUST NOT be reused across a session, and MUST NOT reset when a session resumes.
- Identifiers MUST NOT contain a timestamp, a counter that reveals volume, or any value derived from a secret.

## 5. Normative language and notation

- **MUST / MUST NOT** — an absolute requirement. Deviation is a conformance failure.
- **SHOULD / SHOULD NOT** — a strong recommendation. Deviation requires a recorded justification.
- **MAY** — an optional capability, permitted but not required.
- **CAN** — the capability physically exists.
- **SHALL** — reserved for externally imposed behaviour. XGN does not use it.
- *SETUP*, *SYNC*, *REVIEW*, *TODO*, *TBD*, *XXX* — placeholders. A module carrying any of these is **not** complete and MUST carry the status `Proposed` with the item listed in its open-decisions table.

### 5.1 Requirement identifiers

Each normative requirement in a module MUST carry a stable identifier so that tests and audits can cite it.

| Prefix | Scope |
|---|---|
| `INV-nn` | Framework invariants (XGN-00 section 6) |
| `AO-nn` | Architecture objectives (XGN-00 section 3) |
| `DP-nn` | Design principles (XGN-00 section 7) |
| `AR-nn` | Architectural risks (XGN-00 section 11) |
| `C-nn` | Contract clauses (XGN-02) |
| `S-nn` | Session and grant clauses (XGN-04) |
| `T-nn` | Trust clauses (XGN-05) |
| `R-nn` | Request envelope and lifecycle clauses (XGN-06) |
| `D-nn` | Draft and composition clauses (XGN-07) |
| `V-nn` | Validation and canonicalisation clauses (XGN-08) |
| `I-nn` | Integrity and binding clauses (XGN-31) |
| `G-nn` | Transport abstraction clauses (XGN-09) |
| `P-nn` | Composed GET transport clauses (XGN-10) |
| `Q-nn` | Composed GET command protocol clauses (XGN-28) |
| `L-nn` | Capability and replay clauses (XGN-29) |
| `CT-nn` | Connector and tool transport clauses (XGN-11) |
| `SH-nn` | Shell and authenticated transport clauses (XGN-12) |
| `CP-nn` | Copy/paste handoff clauses (XGN-13) |
| `B-nn` | Panel and command surface clauses (XGN-14) |
| `K-nn` | Policy clauses (XGN-15) |
| `A-nn` | Approval clauses (XGN-16) |
| `X-nn` | Execution clauses (XGN-17) |
| `EA-nn` | External service adapter clauses (XGN-18) |
| `GH-nn` | GitHub operation profile clauses (XGN-19) |
| `GW-nn` | Gateway and relay clauses (XGN-20) |
| `SM-nn` | State machine and transition clauses (XGN-21) |
| `RC-nn` | Reconciliation and recovery clauses (XGN-22) |
| `ER-nn` | Error, retry and rate limit clauses (XGN-23) |
| `H-nn` | Security clauses (XGN-24) |
| `PV-nn` | Privacy, audit and retention clauses (XGN-25) |
| `TST-nn` | Conformance test identifiers (XGN-26) |
| `OV-nn` | Operations and governance clauses (XGN-27) |
| `PRB-nn` | Channel qualification probe identifiers (XGN-30) |

### 5.2 Status vocabulary

Every module MUST carry a status block using this lifecycle.

| Status | Meaning |
|---|---|
| **Proposed** | Drafted for review. Not authoritative for implementation. |
| **Under review** | Being assessed; unresolved decisions remain. |
| **Approved** | Accepted as the current normative specification. |
| **Implemented** | A corresponding implementation exists. |
| **Verified** | The required conformance and acceptance tests have passed. |
| **Deprecated** | Superseded; retained for historical traceability. |

Each module MUST also record its version, owner, last review date, dependencies, superseded documents, open decisions and implementation or test references.

## 6. Time, units and encodings

- **Time** is UTC, expressed as RFC 3339 with a `Z` offset. A timestamp MUST be recorded by the runtime, never by the agent.
- **Durations** use the ISO 8601 duration form, for example `PT90S`, `PT10M`, `PT2H`.
- **Sizes** are expressed in bytes unless the name says otherwise. `KB` means 1024 bytes; `KiB` is not used.
- **Text** is UTF-8. Content-bearing text MUST NOT contain unpaired surrogates or C0 control characters other than `TAB`, `LF`, `CR`.
- **Binary** content MUST be base64url-encoded without padding, and MUST be marked as such.
- **Canonical encoding** for a request component is defined in XGN-08. A canonical form MUST be byte-exact reproducible.

### 6.1 Percent-encoding conventions

- A space is `%20`. `+` means a literal plus sign and MUST NOT be treated as a space.
- Decoding happens **exactly once**, at the authoritative validation boundary.
- Reserved characters MUST be percent-encoded in a value: `/` `%2F` `?` `%3F` `#` `%23` `&` `%26` `=` `%3D` `+` `%2B` `%` `%25` and all characters outside `A-Z a-z 0-9 - . _ ~`.
- Parameter order is fixed per command, defined in XGN-28. A command with parameters in a different order MUST be rejected.
- A repeated parameter MUST be rejected. An unknown parameter MUST be rejected.

## 7. Decision register

Every decision taken while building this specification set is listed here with its owner module. The **owning module holds the authoritative statement**; this table is a pointer and a one-line summary. A decision marked `Proposed` is not yet settled.

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-001` | Channel order | Connector, then Composed GET, then Shell, then Copy/Paste. Gitana proposes; the operator confirms at session creation. | XGN-09 |
| `XGN-DEC-002` | "Curl / git" meaning | Authenticated requests to Gitana only. Direct git with agent-held credentials is never a Gitana channel. | XGN-12 |
| `XGN-DEC-003` | Digest algorithm | SHA-256 over the canonical envelope. Full digest held internally; 32 hex characters (128 bits) exposed for confirmation. | XGN-31 |
| `XGN-DEC-004` | Capability length | At least 128 bits of entropy, 22 base64url characters. Probe P8 uses the production length. | XGN-29 |
| `XGN-DEC-005` | `new` carrying short fields | No. `new` opens an empty draft for one operation. Efficiency is achieved with `batch`. | XGN-28 |
| `XGN-DEC-006` | Approval on mutating commits | Yes, on every transport, for every mutating operation, including `create_pull_request`. | XGN-16 |
| `XGN-DEC-007` | Idempotency window and caller identity | 90 seconds. Caller identity is the session capability plus the exact link and body digest. A network fingerprint is a risk signal only, never authentication. | XGN-29 |
| `XGN-DEC-008` | Session and capability lifetime | Session 2 hours, operator-extendable. A capability is valid until used or for 10 minutes, whichever comes first. | XGN-04, XGN-29 |
| `XGN-DEC-009` | Pairing secret | A non-secret one-time challenge code of 6 digits, shown on the device only. It never appears in an agent-visible panel and is never an authorisation factor. | XGN-05 |
| `XGN-DEC-010` | Branch scope | An exact branch name, or a task-reserved prefix `agent/<task-id>`. Both are permitted; the grant names one. | XGN-04 |
| `XGN-DEC-011` | Clearing a turn versus approval | Clearing a turn does **not** replace per-request approval. Both are retained. | XGN-16 |
| `XGN-DEC-012` | Draft and chunk limits | 2 KB of encoded data per command, 64 KB total draft, 64 parts per field, 512 commands per session. | XGN-28, XGN-04 |
| `XGN-DEC-013` | Channel tier availability | Tier C is off by default and enabled per session, per channel, only after the channel passes qualification. | XGN-10 |
| `XGN-DEC-014` | Reads against other refs | None by default. A read outside the scoped branch is rejected. | XGN-19 |
| `XGN-DEC-015` | Mid-session scope change | Not permitted. A change of scope requires a new session. | XGN-04 |
| `XGN-DEC-016` | Supported chat applications | **Open.** The named list is a product decision and is not settled by this set. Qualification is by probe, not by name. | XGN-30 |
| `XGN-DEC-017` | Bare base capability | Inert. Opening it returns the current panel and consumes nothing. | XGN-29 |
| `XGN-DEC-018` | Draft content lifetime | The draft is discarded on commit, cancel, expiry, suspension, revocation or runtime restart. The committed envelope survives in immutable form until the request is resolved. | XGN-07 |
| `XGN-DEC-019` | Approval binding | The digest covers the complete immutable envelope, including expected provider state. | XGN-31 |
| `XGN-DEC-020` | Overwrite protection | Writing an existing file requires the expected blob digest; a branch operation requires the expected head digest. A mismatch is a rejection. | XGN-19 |
| `XGN-DEC-021` | Command batching | `batch` carries a bounded list of field changes, applied atomically, in one command, with one sequence number. | XGN-28 |
| `XGN-DEC-022` | Capability discovery | The session's first reply carries a machine-readable capability description generated from the same authoritative definitions the validator uses. | XGN-03 |
| `XGN-DEC-023` | Status polling | Status is a read. It does not consume a command capability, does not advance the sequence number, and is rate limited. | XGN-28, XGN-23 |
| `XGN-DEC-024` | Rejected commands and sequence numbers | A rejected command does not consume a sequence number and does not change the next sequence number. | XGN-28 |
| `XGN-DEC-025` | Part identity | Parts carry stable identifiers independent of the sequence number that created them. Ordering is explicit. | XGN-28 |
| `XGN-DEC-026` | Default content encoding | base64url for arbitrary content. Percent-encoded text is permitted only for short, simple text and MUST be marked `enc=text`. | XGN-28 |
| `XGN-DEC-027` | Relay caching of content | The gateway MUST NOT cache a reply that carries agent-authored content. | XGN-20 |
| `XGN-DEC-028` | Runtime restart behaviour | Draft content is discarded; session, grant, request, approval and execution records survive. A request pending approval at restart is invalidated and requires a new commit. | XGN-07, XGN-21 |
| `XGN-DEC-029` | Partial workflow failure | Completed steps are preserved. A failed step leaves the session at that step. Recovery is by read, never by blind repetition. | XGN-22 |
| `XGN-DEC-030` | Transport fallback | A session may move to another transport without widening scope, skipping approval or invalidating an existing approval. | XGN-09 |
| `XGN-DEC-031` | Retention periods | Audit events 400 days; request records 90 days; session metadata 30 days after expiry; draft content minutes; provider response bodies are not retained beyond the execution record. | XGN-25 |
| `XGN-DEC-032` | Release qualification | A transport, client or implementation is called supported only when its conformance tests and channel probes have passed and the evidence is recorded. A proposal is not an approval. | XGN-26 |
| `XGN-DEC-033` | Compatibility window and end of life | A major contract version is supported for 24 months from the first release requiring it; the end of life is announced 6 months in advance; removed requirements are marked, not deleted. | XGN-27 |
| `XGN-DEC-034` | Security posture | A bearer capability in a URL cannot be made secret-proof. The framework bounds exposure and makes misuse observable; detection is the control, not prevention. | XGN-24 |
| `XGN-DEC-035` | No re-commit of an unknown-outcome request | A request in `OUTCOME_UNKNOWN` or `RECONCILING` MUST NOT be re-committed, re-approved or re-dispatched. A fresh request for the same operation requires a new identifier and a new approval, and is permitted only after a reconciliation concludes `FAILED`. | XGN-06; the reconciliation procedure is owned by XGN-22 |
| `XGN-DEC-036` | Request expiry | A request expires at the earlier of the session expiry and one hour after commit. The pending-approval window is 10 minutes. Neither is extendable. | XGN-06 |
| `XGN-DEC-037` | Concurrent sessions per device | A device runs at most 8 concurrent sessions. | XGN-04 |
| `XGN-DEC-038` | Grant digest and binding | The runtime computes a grant digest over the canonical grant form, using the algorithm and exposed length of `XGN-DEC-003`. A request is bound to the grant in force. | XGN-04, XGN-31 |
| `XGN-DEC-039` | Four credential classes | Session capability, operator credential, provider credential and gateway device credential. None is derived from another, and the operator credential is independent of every agent-plane credential. | XGN-05 |
| `XGN-DEC-040` | Scope boundary check | A path is in scope only when its canonical form equals the canonical granted directory, or begins with it followed by `/`. A bare string prefix is not a scope check. | XGN-15 |
| `XGN-DEC-041` | Policy revalidation points | Policy is evaluated on every command, again at commit, and again immediately before dispatch. | XGN-15, XGN-17 |
| `XGN-DEC-042` | Repository privacy failure | A repository that is not private denies the commit and suspends the session. | XGN-15 |
| `XGN-DEC-043` | Approval expiry | An approval is valid for 10 minutes from presentation. An expired approval does not authorise dispatch, and the request requires a fresh commit. | XGN-16 |
| `XGN-DEC-044` | Approval presentation | The approval screen is a review surface with a fixed block layout. Agent-written and repository-derived content is rendered as data, never as instruction. | XGN-16 |
| `XGN-DEC-045` | Execution record ordering | The execution record is persisted before the provider call is attempted. | XGN-17 |
| `XGN-DEC-046` | Provider isolation | Provider-specific behaviour is confined to the provider adapter and the provider profile. The envelope, digest, error shape and event shape are provider-neutral. | XGN-18 |
| `XGN-DEC-047` | Initial GitHub operation surface | The initial surface is `create_branch`, `write_file` and `create_pull_request`, with `read_file` and `list_branches` as read-only links. `delete_file` is not enabled. | XGN-19 |
| `XGN-DEC-048` | Step dependency | The branch created in the first step is the branch written in later steps. A pull request head equals the working branch and its base equals the granted base branch. | XGN-15 |
| `XGN-DEC-049` | Pairing confirmation is device-local | The challenge never reaches the agent plane, no component accepts it as a credential, and the unlock is authenticated by the operator credential. | XGN-05 |
| `XGN-DEC-050` | Two composition objects | A draft is mutable, agent-editable and discarded at commit, cancel, expiry or restart. Its committed form is an immutable pending envelope that retains the complete content until the request is resolved. A draft is never an approval artefact. | XGN-07 |
| `XGN-DEC-051` | Part replacement and removal | A replaced part keeps its identifier and its position. A part replaced with an empty payload is removed from the reconstructed value; the identifiers of the surviving parts keep their relative order. | XGN-07 |
| `XGN-DEC-052` | Field state labels | A draft field is reported in exactly one of four states: `FIXED`, `MISSING`, `SET`, `OPEN`. A part count is always labelled `parts:`; part identifiers are always listed separately under `part ids:`. A sequence number is never a part identifier or a part count. | XGN-07 |
| `XGN-DEC-053` | Canonical representation | The canonical representation of an operation is the length-prefixed, LF-separated rendering of the fixed field order defined in XGN-08 section 5. Every request digest in XGN is taken over that byte sequence. | XGN-08 |
| `XGN-DEC-054` | GET as a command channel | Composed GET is acceptable only with the mandatory safeguard set in XGN-10 section 7.2, and MUST be refused in the conditions in section 7.3. GET is not a safe method; the risk is never removed, only bounded and detected. | XGN-10 |
| `XGN-DEC-055` | Panel structure and content marking | A panel has exactly five blocks; agent-written and repository-derived content appear only inside an explicit read, in a data region with an explicit byte count, and are never interpreted as instructions. | XGN-14 |
| `XGN-DEC-056` | Capability binding | The binding tuple is session, grant, node, expected next sequence number, and the permitted command and parameter names, summarised by a `binding_digest`. The binding is to names, never to values. | XGN-29 |
| `XGN-DEC-057` | Draft digest and envelope digest | Two digests exist. The draft digest covers the agent-supplied composition and binds a preview to a commit. The envelope digest covers the complete immutable envelope and is the approval binding. The draft digest MUST NOT be used as an approval binding. | XGN-31 |
| `XGN-DEC-058` | Envelope signature placement and key rotation | The runtime signs the canonical representation with a device key. The signature is stored in the approval record, not in the envelope, so the envelope stays canonical. Rotation is recorded by the key identifier and MUST NOT invalidate a pending approval. | XGN-31 |
| `XGN-DEC-059` | XGN is authoritative for agent ingress | XGN is the authoritative source for agent ingress to Gitana. A document describing agent ingress that is not part of this set is out of scope and MUST NOT be cited as the authority for a request contract, envelope, lifecycle, policy decision, approval, execution path or audit record. Where such a document conflicts with XGN, XGN wins and the conflict MUST be reported rather than silently resolved. | XGN-00 |
| `XGN-DEC-060` | Contract versioning and compatibility | A breaking change requires a new schema set; an additive change is a minor version. A consumer rejects an unknown major `contract_version` and ignores unknown fields in a response. A contract change requires an impact review across every dependent module. | XGN-02 |

## 8. Decisions recorded

This module owns no decision of its own. It holds the **register** in section 7, which is the index of every decision taken while building the set. Each entry names the module that holds the authoritative statement.

## 9. Status of the inherited claims

The proposal that seeded this set made six claims (`C1` to `C6`). **None has been tested.** Writing this specification set does not test them.

| Claim | Status | Verification method |
|---|---|---|
| C1 — an agent can build and submit a write request using only HTTPS GET | Untested | XGN-30 probes PRB-01 to PRB-28, then the mock run in XGN-26 |
| C2 — the agent needs no prior knowledge of the command set | Untested | Machine-readable capability description, XGN-03; mock run with no external instruction |
| C3 — the agent cannot unlock, approve, widen scope or execute | Untested | INV-01, INV-02; scope and gate tests in XGN-26 |
| C4 — what is approved is what is dispatched | Untested | Digest coverage in XGN-31; binding test in XGN-26 |
| C5 — one mistake can be fixed without rebuilding the draft | Untested | `replace` and `unset` semantics in XGN-28; mock run with injected errors |
| C6 — a lost reply can be retried without a second effect | Untested | Idempotency window in XGN-29; replay test in XGN-26 |

## 10. Related specifications

- **Depends on:** none.
- **Referenced by:** every module in the set.
- **Predecessor material:** the *Gitana Surface Map (Composed GET): Proposal*, which introduced the terms surface map, panel, grant, capability base, session sequence number, draft hash, part and gate.
