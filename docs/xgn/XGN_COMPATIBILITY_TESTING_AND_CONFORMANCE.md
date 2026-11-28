# XGN-26: XGN Compatibility Testing and Conformance

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-26 | **Group:** F — Assurance
**Owner:** Gitana Architecture
**Depends on:** XGN-00, XGN-01, XGN-02 | **Supersedes:** none
**Conformance:** self | **Open decisions:** see section 9

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines how a module, a contract, a transport, a client channel and an implementation are qualified for use with Gitana agent ingress. It defines the test catalogue, the acceptance criteria for a release, the declaration mechanism for supported clients and the rules for release qualification.

This module owns the conformance programme and the `TST-nn` identifiers. It does not own the behaviour under test, the channel qualification probes (XGN-30), the requirement text of any other module, or the release process itself (XGN-27).

**Out of scope:** specification of any product behaviour; the probe suite (XGN-30); test tooling and CI infrastructure; release scheduling.

## 2. The conformance programme

### 2.1 Qualification objects

| Object | Qualified against | Evidence |
|---|---|---|
| A module | Its own requirement clauses | a test report citing `TST-nn` identifiers |
| A contract | The schema set and the positive and negative test vectors required by VC-08 | schema conformance report |
| A transport | The transport requirements plus the convergence tests | conformance report plus a qualification record |
| A client channel | The probes in XGN-30 for that client | probe results, per client, version and date |
| An implementation | Every applicable test plus the contract schemas | a qualification record naming the implementation version |

### 2.2 Qualification levels

| Level | Meaning | What it permits |
|---|---|---|
| L0 — declared | The object exists and is specified | it may be described in documentation |
| L1 — conformant | Its `TST-nn` tests have passed against a reference build | it may be used in an internal build |
| L2 — channel-qualified | Its tests and the XGN-30 probes for the client have passed | it may be enabled for a specific client |
| L3 — supported | The release acceptance criteria in section 4 are met | it may be listed as supported |

A level is a claim about evidence. It is not a claim about the source of that evidence.

### 2.3 The qualification record

| Field | Rule |
|---|---|
| `record_id` | `qal_` plus 16 base64url characters |
| `object` | The module, contract, transport, client or implementation qualified |
| `version` | The exact version qualified. A record does not cover a later version. |
| `tests` | The `TST-nn` identifiers executed and their result |
| `probes` | The `PRB-nn` identifiers executed and their result, where the object is a client or transport |
| `environment` | The runtime version, the transport version and the provider mode used |
| `performed_by` | The person or system that executed the qualification |
| `performed_at` | RFC 3339 UTC |
| `expires_at` | The date the record stops being valid evidence |

| ID | Requirement |
|---|---|
| TST-R-01 | A qualification MUST cite the identifier of every test it claims. A test that was not executed MUST NOT appear in the record. |
| TST-R-02 | A qualification record MUST be invalidated when the qualified version changes, and MUST be reissued before the version it names is superseded. |
| TST-R-03 | A qualification MUST NOT be issued from a proposal, a design document or a partial run. |

### 2.4 Test identifier scheme

| Prefix | Owner | Meaning |
|---|---|---|
| `TST-nn` | XGN-26 | A conformance test in the catalogue in section 3 |
| `PRB-nn` | XGN-30 | A channel qualification probe |

`TST-nn` identifiers are stable. A withdrawn test is marked withdrawn and its number is not reused.

### 2.5 Test method rules

- A conformance test MUST be black-box against a conforming runtime and MUST NOT reach into internal state except where the assertion is about an audit record.
- Every `MUST NOT` in the set MUST have at least one negative test.
- A test that cannot run because a dependency is unavailable MUST be recorded as `BLOCKED`, never as `PASS`.
- A test that fails MUST be reported with the identifier, the version under test and the observable outcome.

## 3. The test catalogue

| ID | Area | Assertion | Requirement | Pass criteria |
|---|---|---|---|---|
| TST-01 | Scope | a path outside the granted directory is rejected | INV-03, K-nn (XGN-15) | rejected `SCOPE_VIOLATION`; provider call count 0 |
| TST-02 | Scope | a branch or repository outside the grant is rejected | INV-03 | rejected; provider call count 0 |
| TST-03 | Scope | traversal forms `..`, `%2F`, `%5C`, `/abs`, `//host` are all rejected | H-17 (XGN-24), V-nn (XGN-08) | 100% rejected; no variant normalises into scope |
| TST-04 | Scope | Unicode confusables and non-NFC forms resolve consistently | H-17 | identical bytes give one canonical path; ambiguous forms are rejected |
| TST-05 | Scope | scope is re-checked at dispatch, not only at commit | H-18 | a scope change between commit and dispatch is rejected |
| TST-06 | Private-only | a public repository is refused at session creation | INV-03, K-nn | session creation refused |
| TST-07 | Private-only | a repository made public after creation is denied at commit and the session is suspended | H-18 | commit denied; session suspended; event recorded |
| TST-08 | Pairing | the session stays locked until the operator confirms on the device | INV-01, T-nn (XGN-05) | every agent command rejected `SESSION_LOCKED` |
| TST-09 | Pairing | no agent-plane reply carries an operator verb | INV-01, H-04 | a scan of every reply finds no operator action |
| TST-10 | Pairing | the pairing challenge never appears in an agent-visible reply | XGN-DEC-009 | absent from every reply; confirmation still requires the device |
| TST-11 | Gates | a gated session exposes no forward link | INV-01, B-nn (XGN-14) | the command block holds only a rate-limited re-check |
| TST-12 | Gates | no agent command advances a step or clears a gate | INV-01 | the step index is unchanged after every agent command |
| TST-13 | Gates | clearing a turn does not substitute for per-request approval | XGN-DEC-011 | a cleared turn still requires approval for a mutating commit |
| TST-14 | Replay | a consumed capability reused with a different command is rejected and suspends the session | INV-09, L-nn (XGN-29) | reuse recorded; session suspended; operator notified |
| TST-15 | Replay | a capability presented after its 10 minute lifetime is rejected with no state change | L-nn, XGN-DEC-008 | rejected; session state unchanged |
| TST-16 | Idempotency | an exact retry inside the 90 s window returns the original reply and produces no second effect | INV-08 | reply identical to the original; one effect only |
| TST-17 | Idempotency | an exact retry outside the window is treated as reuse, not as a retry | L-nn, XGN-DEC-007 | session suspended; reuse event recorded |
| TST-18 | Consumption | the bare capability base returns the current panel and consumes nothing | XGN-DEC-017 | panel returned; capability still valid; no event |
| TST-19 | Consumption | prefetch of a complete command URL does not execute the command | H-05, PRB prefetch (XGN-30) | no state change; the channel is not enabled if it cannot pass |
| TST-20 | Consumption | a capability is consumed by exactly one complete command | L-nn | one consumption event; a second attempt is rejected |
| TST-21 | Consumption | status does not consume a capability and does not advance the sequence number | XGN-DEC-023 | capability still valid; sequence unchanged |
| TST-22 | Sequence | a duplicate sequence number has no new effect | INV-10 | the state after the duplicate equals the state after the original |
| TST-23 | Sequence | a gap is rejected | INV-10 | rejected `SEQ_INVALID`; no state change |
| TST-24 | Sequence | a rejected command does not advance the next sequence number and issues a fresh capability | INV-11, XGN-DEC-024 | next sequence unchanged; the new capability works |
| TST-25 | Digest binding | the approved digest equals the dispatched digest | INV-06, I-nn (XGN-31) | byte-identical; a mismatch aborts with `INTEGRITY_MISMATCH` |
| TST-26 | Digest binding | a draft altered after preview fails at commit | I-nn | commit rejected `DRAFT_HASH_MISMATCH`; no approval is requested |
| TST-27 | Digest binding | a tampered envelope digest aborts dispatch | I-nn, X-nn (XGN-17) | no provider call; incident recorded |
| TST-28 | Digest binding | the digest covers the whole envelope, including expected provider state | XGN-DEC-019 | changing any covered field changes the digest |
| TST-29 | Panel | the command block offers only commands legal in the current state | INV-12 | every offered command is executable; every legal command is offered |
| TST-30 | Panel | the panel and the capability description agree for the current state | CD-04 (XGN-03) | the two lists are identical |
| TST-31 | Discovery | the capability description is generated from the validator's definitions | CD-01, XGN-DEC-022 | a description entry the validator would reject is a failure |
| TST-32 | Discovery | a command not advertised is not executable | CD-03 | rejected `STATE_INVALID` |
| TST-33 | Privacy | no capability, credential or content appears in a log line, an event, an error message or a metric | INV-14, H-14, PV-08 | a scan of all sinks finds none |
| TST-34 | Privacy | the gateway holds nothing: no content and no authoritative state | XGN-00 §5.1, PV-02 | inspection after a full session finds no class 1, 2 or 7 data |
| TST-35 | Privacy | the gateway does not cache a content-bearing reply | XGN-DEC-027 | no cache entry after a content read |
| TST-36 | Isolation | one session cannot observe or affect another | INV-01, S-nn (XGN-04) | a foreign capability is rejected; no cross-session state is visible |
| TST-37 | Convergence | two transports delivering the same logical request produce envelopes differing only in transport, `request_id`, timestamps and digests | XGN-02 §4.2 | a field-by-field diff shows no other difference |
| TST-38 | Convergence | every transport produces the same policy decision, the same approval requirement, the same execution and the same audit event types | AO-04, XGN-09 | the recorded outcomes are equal in kind |
| TST-39 | State machine | every transition in the transition table is exercised | SM-nn (XGN-21) | 100% of the table's rows covered |
| TST-40 | State machine | an illegal transition is rejected | SM-nn | rejected `STATE_INVALID`; no state change |
| TST-41 | State machine | a rejected command leaves the draft, the pending envelope and the next sequence number unchanged | INV-11 | all three are byte-identical before and after |
| TST-42 | Recovery | an unknown outcome is reconciled by reading authoritative state, with no second provider call | INV-16, RC-nn (XGN-22) | provider call count during reconciliation is 0 |
| TST-43 | Recovery | an unidentifiable operation suspends execution and requires operator review | RC-nn | state `WAIT`; operator alerted |
| TST-44 | Recovery | a lost reply is recovered by the exact retry inside the window | INV-08, C6 | the original reply is returned; one effect |
| TST-45 | Restart | a restart during a draft discards the draft and keeps the session | XGN-DEC-028 | no draft content survives; the session is usable |
| TST-46 | Restart | a restart during pending approval invalidates the request and requires a new commit | XGN-DEC-028, R-nn (XGN-06) | the request is invalidated; a new commit is required |
| TST-47 | Restart | a restart during execution leaves a reconcilable record and does not duplicate the write | XGN-DEC-028, RC-nn | one side effect; the outcome is reconciled |
| TST-48 | Fail closed | unknown, malformed, stale, out-of-scope and integrity-invalid input fails closed | INV-15 | every case rejected; no partial state change |
| TST-49 | Batching | a batch that fails one field applies nothing | D-nn (XGN-07), XGN-DEC-021 | the draft is byte-identical before and after |
| TST-50 | Approval | the approval summary is runtime-generated and carries the envelope digest | A-nn (XGN-16), H-20 | the summary contains no agent-written or repository content |
| TST-51 | Approval | an approval for a different digest does not authorise dispatch | INV-06, I-nn | dispatch refused; no provider call |

## 4. Acceptance criteria for a release

| Criterion | Evidence | Source |
|---|---|---|
| Every `TST-nn` test in scope for the release has a recorded pass against the release build | test report naming each identifier | section 3 |
| No test is `BLOCKED` for a shipped path | test report | section 2.5 |
| Every client shipped in the release has passed the probes in XGN-30 | probe results per client, version and date | XGN-30 |
| The claim table in XGN-01 §8 shows evidence for every claim the release asserts; every other claim remains `Untested` | claim table | XGN-01 §8 |
| The gateway retains nothing, verified by inspection | TST-34 result | section 3 |
| The audit chain verifies end to end for a full session | chain verification output | PV-13 (XGN-25) |
| Every transition of the state machine is covered | TST-39 result | XGN-21 |
| No open defect of severity P0 or P1 against a release-blocking requirement | defect register | XGN-27 |
| The release carries a changelog and an impact review for every shared contract change | changelog and review record | VC-05, VC-06 |

## 5. Supported-client declarations

A client is declared supported by a declaration record.

| Field | Rule |
|---|---|
| `client_id` | The client product and plan, for example a chat application and its plan |
| `client_version` | The version or build tested |
| `transport` | The transport the client is declared for |
| `transport_caps` | The capability set the client demonstrated: for example `read`, `compose_query`, `extend_link`, `no_headers` |
| `max_url_chars` | The largest complete command URL the client delivered intact |
| `max_payload_chars` | The largest encoded payload the client delivered intact |
| `approval_prompt_behaviour` | How the client surfaces a URL approval prompt |
| `probe_results` | The `PRB-nn` identifiers executed and their result |
| `declared_at` | RFC 3339 UTC |
| `expires_at` | `declared_at` plus 180 days |

| ID | Requirement |
|---|---|
| TST-R-04 | A client MUST NOT be listed as supported without a current declaration naming the probe results. |
| TST-R-05 | A declaration expires 180 days after it is made. A client MUST NOT be listed as supported after its declaration expires without a new declaration. |
| TST-R-06 | A declaration MUST be withdrawn immediately when a probe that previously passed regresses, and the channel MUST be disabled for that client. |
| TST-R-07 | A declaration MUST record the client version tested. Behaviour that changes without a version change is handled by TST-R-06, not by a wider validity period. |

## 6. Release qualification

| To be called | Must hold |
|---|---|
| A transport **supported** | Every `TST-nn` test applicable to it has passed; every client it is shipped for has a current declaration; the convergence tests TST-37 and TST-38 have passed; no open P0 or P1 defect. |
| A client **supported** | The probes in XGN-30 have passed for that client and version; the declaration in section 5 is current; the channel is enabled per session and per channel only after qualification (XGN-DEC-013). |
| An implementation **supported** | The contract schemas in XGN-02 validate; every applicable `TST-nn` test has passed against the implementation build; the qualification record names the implementation version. |
| A module **verified** | Every requirement clause in the module is covered by at least one passing test, or is explicitly listed as untestable with a reason. |

| ID | Requirement |
|---|---|
| TST-R-08 | The status vocabulary of XGN-01 §5.2 is the only vocabulary used. `Supported` is a release designation recorded in the qualification record, not a module status. |

## 7. A proposal is not an approval

Nothing in this module makes the inherited claims C1 to C6 true. They remain `Untested` until the corresponding probes and tests have run and their results have been recorded in the claim table in XGN-01 §8.

| ID | Requirement |
|---|---|
| TST-R-09 | A module, a contract or a design proposal MUST NOT be described as qualified, verified, supported or approved on the basis of this specification alone. |
| TST-R-10 | A qualification record asserts only what it tested, at the version it tested. A pass in one area MUST NOT be read as a pass in another. |

## 8. Decisions recorded

| ID | Decision as applied here | Authority |
|---|---|---|
| XGN-DEC-016 | Qualification is by probe, not by the name of a chat application. The named list of supported applications remains open. | XGN-30 |
| XGN-DEC-022 | The capability description is generated from the same definitions the validator uses, which is the basis of TST-31. | XGN-03 |
| XGN-DEC-032 | Release qualification: a transport, client or implementation is called supported only when its conformance tests and channel probes have passed and the evidence is recorded; a proposal is not an approval. | XGN-26 |

## 9. Open decisions

| ID | Question | Default position in this module |
|---|---|---|
| OD-26-1 | What format does a qualification record take? | Undefined. A machine-readable record is preferred but not settled. |
| OD-26-2 | May a transport waive a `TST-nn` test, and who may grant a waiver? | No waiver is defined. A transport that cannot meet a test is not supported for that scope. |
| OD-26-3 | Is 180 days the right validity period for a client declaration? | Proposed. The period is driven by how fast client behaviour changes, which is not yet measured. |

## 10. Related specifications

- **Depends on:** XGN-00, XGN-01, XGN-02.
- **Referenced by:** every module in the set; XGN-09, XGN-10, XGN-30 and XGN-27 in particular.
- **Predecessor material:** the acceptance criteria in §12 of the *Navigation Transport Specification* (0.1-draft) and the expanded test set in §6 of technical review B, redistributed here with stable identifiers.
- **Out of scope:** channel qualification probes (XGN-30), release management and change control (XGN-27), the behaviour of any product component.
