# XGN-30: XGN Channel Qualification Probes

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-30 | **Group:** C — Transport
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-10, XGN-14 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** XGN-DEC-016

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the **channel qualification probe suite**: the ordered suite of probes PRB-01 to PRB-28 that determine whether a given chat application may carry the Composed GET transport at a given channel tier, and whether a given implementation of that transport is sound enough to be used at all.

In scope: the probe method and setup requirements; the full ordered suite; the channel decision matrix; the implementation qualification matrix; the recording requirements; the mapping from the inherited claims C1 to C6 to the probes that would prove them; and the status of the supported chat application list.

Out of scope: the transport surface and the anti-prefetch protections (XGN-10), the command catalogue (XGN-28), the capability lifecycle (XGN-29), the panel rendering (XGN-14), the conformance test suite for the framework as a whole (XGN-26), and the operation catalogue (XGN-19).

**No probe in this suite has been run.** The claims C1 to C6 remain untested. Nothing in this module asserts that any application, any fetch tool or any implementation passes any probe.

## 2. Probe method and setup

### 2.1 Method

| Rule | Requirement |
|---|---|
| PRB-M1 | Each probe MUST be run against one named chat application, in one named plan and mode, and the result MUST be recorded against that combination (section 6). |
| PRB-M2 | Each probe MUST produce exactly one result: `PASS`, `FAIL` or `INCONCLUSIVE`. `INCONCLUSIVE` MUST NOT be treated as `PASS` in any decision. |
| PRB-M3 | Each probe MUST record the measured values it produces, not only its result. A probe that produces a measured value MUST record it. |
| PRB-M4 | Probes MUST be run in the order PRB-01 to PRB-28. A probe that cannot be run MUST be recorded as `INCONCLUSIVE` with the reason. |
| PRB-M5 | Dummy data only. A probe MUST NOT write to a real repository, MUST NOT create a real pull request, and MUST NOT use a real credential or a real provider token. |
| PRB-M6 | A probe MUST NOT be reported as `PASS` on the basis of a single observation. Delivery, execution, prompting and retry behaviour MUST be measured separately (PRB-05). |

### 2.2 Setup requirements

| Setup | Requirement |
|---|---|
| **Echo endpoint** | An endpoint that returns the exact bytes of the request it received: method, path, the raw query string, the header set, and the total request length. It MUST NOT normalise, reorder or re-encode the query string. |
| **Logging endpoint** | An endpoint that issues links into replies and records each arrival in order with a timestamp, the request length and the observed query string. It MUST be able to distinguish a link that appeared in a reply from a link that was pasted into the chat. |
| **Scanner simulation** | A service that fetches every URL that appears in a reply, as a security scanner or a link previewer would, and records what it fetched. |
| **Gitana session** | A session created against a dummy grant: a dummy repository, a dummy branch, a dummy directory, dummy steps and a dummy operator. The session MUST be created only for the probe, and revoked afterwards. |

## 3. The probe suite

### 3.1 Application probes

These probes measure the behaviour of the chat application and its fetch tool. They decide the channel tier.

| ID | What it does | Setup | Pass when |
|---|---|---|---|
| PRB-01 | Open a URL pasted into chat. | Echo | The reply matches the request byte for byte, and the echo endpoint records exactly one arrival. |
| PRB-02 | Add `&` parameters to a pasted URL. | Echo | Every added parameter arrives, in the order added, with its value unmodified. |
| PRB-03 | Open a link that appears only inside a fetched reply. | Logging | The link is opened and the reply is delivered back to the agent. |
| PRB-04 | Add `&` parameters to a link that appeared only inside a fetched reply. | Logging | Every added parameter arrives, unmodified and in order. **This is the decisive tier C test.** PRB-02 alone is not sufficient, because it extends a link the agent typed rather than one the server issued. |
| PRB-05 | Run 15 commands in sequence. | Logging | Delivery, ordering, execution, prompting and retrying MUST be measured **separately**: (a) all 15 requests arrived; (b) they arrived in the order issued; (c) each took effect exactly once; (d) the number of operator approval prompts the application asked for is at most 3 in total and at most 1 per command; (e) no request was issued twice by the application, or any that was produced no second effect. |
| PRB-06 | Open URLs of 150, 250, 500, 1000 and 2000 characters. | Echo | The largest length that arrives intact and unmodified is recorded. That value is the applicable URL limit for the application and MUST NOT exceed 900 characters (XGN-10 section 6). |
| PRB-07 | Send a value containing space, newline, `&`, `#`, `%`, `+` and non-ASCII characters. | Echo | The bytes received equal the bytes sent exactly: no case change, no `+` to space substitution, no truncation at `#`, no double decoding, no re-encoding. |
| PRB-08 | Open and extend a link whose capability is of production length, `cap_` plus 22 base64url characters. | Logging | The link is opened and extended, and the capability is neither refused nor altered as a credential. **The inherited 43-character token is corrected to the production length here (XGN-DEC-004).** |
| PRB-09 | Send the same URL twice. | Logging | The second send is delivered identically and returns the cached reply, with no second effect. **This probe MUST pass for claim C6** (XGN-01 section 8). |
| PRB-10 | Compare the source address and the user agent across requests. | Logging | The variation is recorded. This probe is informational: it exists to demonstrate that address and user agent vary and therefore cannot serve as an identity (XGN-DEC-007). |
| PRB-11 | Continue after stopping for a chat turn. | Logging | The newest link still opens and extends after the interruption. The 10-minute capability lifetime continues to apply across the interruption. |
| PRB-12 | Automatic prefetch. Open every URL that appears in a reply, as a scanner or a link previewer would. | Logging plus scanner | Two results are recorded separately: `consumption` and `execution`. `consumption` MUST be `none` — no capability consumed, no sequence number advanced, no draft changed, no `LINK_REUSE` raised by the scanner. `execution` MUST be `none` — no command took effect. |

**PRB-05 prompt budget.** The approval prompts counted by PRB-05 are the **chat application's own confirmation prompts** ("open this link?"), not the Gitana operator approval, which is a separate control owned by XGN-16. A budget is stated because an application that asks the operator to confirm every fetch is unusable for the twenty-odd `append` commands a small file needs, even though each individual fetch succeeds.

### 3.2 Implementation probes

These probes exercise the XGN implementation itself, not the application. They decide whether the transport is sound enough to be used.

| ID | What it does | Pass when |
|---|---|---|
| PRB-13 | **Link leakage simulation.** Publish a live capability in a place the operator can revoke, then use it. | The leaked link is revocable, and it cannot exceed its grant: a request outside the granted scope is refused without contacting the external service (INV-03). The use is recorded. |
| PRB-14 | **Expired capability replay.** Replay a capability after its lifetime. | The replay is refused as `LINK_EXPIRED`. No state changes, no draft corruption, no sequence advance, no execution. |
| PRB-15 | **Concurrent command submission.** Submit two commands carrying the same capability at the same moment. | Exactly one valid state transition occurs. The other is refused as `CONCURRENT_COMMAND`. No partial application, no double effect (XGN-10 P-18). |
| PRB-16 | **Status polling.** Poll `status` repeatedly. | Each poll returns the current authoritative state, never a cached one. No poll consumes a capability, advances a sequence number or alters a draft. Polling beyond the allowance is refused as `RATE_LIMITED` (XGN-DEC-023). |
| PRB-17 | **Runtime restart during a draft.** Restart the trusted runtime while a draft is open. | The draft is discarded safely (XGN-DEC-028). The session, the grant and the lifecycle records survive. The agent is told that the draft is gone and that it must open a new one. No half-restored draft remains. |
| PRB-18 | **Runtime restart during execution.** Restart the trusted runtime while a request is executing or awaiting approval. | The outcome is recovered without a duplicate write (XGN-DEC-029, XGN-22). A request pending approval at restart is invalidated and requires a new commit. The gateway does not assert that the request was accepted. |
| PRB-19 | **Existing-file modification.** Change a target file between `preview` and `commit`. | The version conflict is detected and the write is refused (XGN-DEC-020), before any external service is contacted. |
| PRB-20 | **Path traversal and encoding attacks.** Submit paths containing `..`, absolute paths, percent-encoded separators, double-encoded separators, trailing-slash tricks and Unicode look-alikes. | Every out-of-scope path is refused as `SCOPE_VIOLATION`. No request reaches the external service (INV-03). |
| PRB-21 | **Unicode and malformed encoding.** Submit malformed percent-encoding, overlong UTF-8 sequences, lone surrogates, mixed normalisation forms and split multi-byte characters across parts. | Decoding and canonicalisation are consistent and deterministic. Malformed input is refused as `VALIDATION_FAILED` and is never silently repaired. |
| PRB-22 | **Oversized payload.** Submit a command whose URL and whose data exceed every limit. | The payload is refused at the earliest boundary, before any resource is exhausted, with a bounded error, no state change and no partial allocation (XGN-29; XGN-03 CD-09). |
| PRB-23 | **Partial workflow failure.** Let one step succeed and the next fail. | Completed steps are preserved. The session remains at the failed step. Recovery is by read, never by blind repetition (XGN-DEC-029, XGN-22). |
| PRB-24 | **Approval digest manipulation.** Alter the request after the digest was shown, then dispatch. | The dispatch is refused with `INTEGRITY_MISMATCH`. The digest covers the complete envelope, not only the operation arguments (XGN-DEC-019, XGN-31). |
| PRB-25 | **Duplicate pull-request creation.** Dispatch a pull-request operation whose outcome is unknown, then retry it. | No unintended duplicate pull request is produced. The second attempt is detected as a duplicate and reconciled by reading authoritative state (XGN-22). |
| PRB-26 | **Gateway and runtime state disagreement.** Make the gateway's view differ from the runtime's. | The runtime's record is authoritative and is restored. The gateway does not assert that an operation was accepted or executed, and no unauthorised execution occurs (XGN-00 section 5.1). |
| PRB-27 | **Batch failure.** Submit a batch in which one operation fails validation. | Nothing is applied. The draft is unchanged, the next sequence number is unchanged, and a fresh capability is issued (XGN-DEC-021, XGN-DEC-024). |
| PRB-28 | **Transport fallback.** Move a session to another transport mid-task. | The existing authorisation and request integrity are preserved. Scope is not widened, approval is not bypassed, and the draft and the request are not duplicated (XGN-DEC-030). |

## 4. Channel decision matrix

The matrix is deterministic. Rules are evaluated in order; the first rule that matches decides.

### 4.1 Application qualification (PRB-01 to PRB-12)

| Rule | Condition | Outcome |
|---|---|---|
| A1 | PRB-01 is not `PASS`, or PRB-03 is not `PASS` | **Use another channel.** |
| A2 | PRB-12 records `execution` other than `none` | **Use another channel.** A prefetcher executed a command. |
| A3 | PRB-12 records `consumption` other than `none` | **Channel tier A only.** A prefetcher consumed a capability. |
| A4 | PRB-11 is not `PASS` | **Channel tier A only.** The session cannot be continued reliably after an interruption. |
| A5 | PRB-04, PRB-05, PRB-07 or PRB-09 is not `PASS`, or the PRB-05 prompt budget is exceeded, or the PRB-06 applicable URL limit is below 250 characters | **Channel tier B only.** |
| A6 | otherwise | **Channel tier C.** |

PRB-02, PRB-06 (above 250 characters), PRB-08 and PRB-10 do not change the outcome on their own; they are recorded as measurements and as evidence for the claims.

### 4.2 Implementation qualification (PRB-13 to PRB-28)

| Rule | Condition | Outcome |
|---|---|---|
| B1 | PRB-15, PRB-20, PRB-21 or PRB-22 is not `PASS` | **Composed GET is not qualified.** The implementation cannot contain a malformed or hostile request, and cannot contain concurrency. |
| B2 | PRB-14 or PRB-19 is not `PASS` | **Composed GET is not qualified.** A stale or replayed request corrupts state. |
| B3 | PRB-24 or PRB-25 is not `PASS` | **Composed GET is not qualified.** The integrity and duplicate controls do not hold. |
| B4 | PRB-16, PRB-26, PRB-27 or PRB-28 is not `PASS` | **Composed GET is not qualified.** Reads, disagreement, atomicity and fallback are unsound. |
| B5 | PRB-13, PRB-17, PRB-18 or PRB-23 is not `PASS` | **Composed GET is not qualified.** Leakage, restart and partial failure are unsound. |
| B6 | otherwise | **Qualified.** |

**Combination rule.** An application outcome and an implementation outcome MUST both be present before a channel tier is granted. A tier C application on an unqualified implementation is **channel tier A or tier B only**, and the qualification failure MUST be recorded and reported to the operator.

## 5. Recording requirements

| Rule | Requirement |
|---|---|
| PRB-R1 | Every probe result MUST record the application name, the provider, the plan, the model, the mode, the fetch tool in use, and the date and time in RFC 3339 UTC. |
| PRB-R2 | Every probe result MUST record the probe ID, the result, the measured values, and the operator or engineer who ran it. |
| PRB-R3 | The record MUST state the XGN module set version the probes were run against. |
| PRB-R4 | A record MUST NOT contain agent-written content, credentials, capability values, repository content or personal data. |
| PRB-R5 | **Application behaviour changes without notice.** A qualification record is valid only for the exact combination in PRB-R1. A change of application, plan, model or mode invalidates it, and the affected probes MUST be re-run. |
| PRB-R6 | A qualification record MUST be re-run at least every 6 months, and after any change to the transport implementation. |
| PRB-R7 | The record MUST state the decision reached under sections 4.1 and 4.2, and the reason. |

## 6. Which probes prove which claim

| Claim | Probes that would prove it | Additional evidence required |
|---|---|---|
| **C1** — an agent can build and submit a write request using only HTTPS GET | PRB-01, PRB-02, PRB-03, PRB-04, PRB-05, PRB-06 (applicable limit at least 250), PRB-07, PRB-08, PRB-11, PRB-12 | The mock run in XGN-26, with no external instruction. |
| **C2** — the agent needs no prior knowledge of the command set | PRB-01, PRB-04 | The capability description in the first panel (XGN-03) and the mock run with no instruction beyond the session link. No probe alone proves C2. |
| **C3** — the agent cannot unlock, approve, widen scope or execute | PRB-13, PRB-15, PRB-16, PRB-19, PRB-24, PRB-26 | The invariant tests for INV-01 and INV-02 in XGN-26. |
| **C4** — what is approved is what is dispatched | PRB-24 | The binding test in XGN-26, against the complete envelope digest of XGN-31. |
| **C5** — one mistake can be fixed without rebuilding the draft | PRB-23, PRB-27 | The mock run with injected errors in XGN-26, exercising `replace`, `unset` and `batch`. |
| **C6** — a lost reply can be retried without a second effect | **PRB-09, which MUST pass**, and PRB-05 (the retry measurement) and PRB-14 | The replay test in XGN-26, against the 90-second exact-retry rule of XGN-29. |

**PRB-09 is mandatory for C6.** An application whose fetch tool cannot send the same URL twice cannot exercise the idempotency path at all, and the claim is untestable on it.

## 7. The supported chat application list

`XGN-DEC-016` remains **open**. The named list of supported chat applications is a product decision and is not settled by this specification set.

| Rule | Requirement |
|---|---|
| PRB-N1 | Qualification is by probe result, not by application name. |
| PRB-N2 | XGN MUST NOT assert that any named application supports Composed GET. |
| PRB-N3 | An application name MAY appear in a qualification record as a fact about that record. It is not a statement of support. |
| PRB-N4 | A tier MUST be granted to a recorded application-and-plan combination only. It MUST NOT be extended to another plan, model or mode without re-running the affected probes (PRB-R5). |
| PRB-N5 | Where an application cannot be qualified, the correct outcome is to use another channel, not to relax the transport (XGN-DEC-001, XGN-09). |

## 8. Probe suite requirements

| ID | Requirement |
|---|---|
| PRB-01 | The suite MUST consist of the ordered probes PRB-01 to PRB-28, each producing `PASS`, `FAIL` or `INCONCLUSIVE`. |
| PRB-02 | A probe MUST NOT be treated as passed when it is inconclusive. |
| PRB-03 | PRB-05 MUST measure delivery, ordering, execution, approval prompting and retrying separately, and MUST apply a stated prompt budget. |
| PRB-04 | PRB-08 MUST use a capability of the production length, `cap_` plus 22 base64url characters. |
| PRB-05 | PRB-09 MUST pass for claim C6 to be considered testable. |
| PRB-06 | PRB-12 MUST record command consumption and command execution separately, and both MUST be `none` for tier C. |
| PRB-07 | The channel decision matrix MUST be deterministic: every combination of probe results MUST reach exactly one outcome. |
| PRB-08 | An application outcome and an implementation outcome MUST both be present before a tier is granted. |
| PRB-09 | Every probe result MUST record the application, provider, plan, model, mode, date, operator and measured values, and MUST NOT contain content, credentials or capability values. |
| PRB-10 | A qualification record MUST be invalidated by a change of application, plan, model or mode, and MUST be re-run at least every 6 months. |
| PRB-11 | The mapping from claims C1 to C6 to probes MUST be maintained as claims are revised. |
| PRB-12 | The supported chat application list MUST remain a product decision, and qualification MUST NOT be by name. |
| PRB-13 | Probes MUST use dummy data only and MUST NOT write to a real repository. |

## 9. Decisions recorded

| ID | Decision | Where it is stated |
|---|---|---|
| `XGN-DEC-016` | Supported chat applications: **open**. The named list is a product decision and is not settled by this set. Qualification is by probe, not by name. | Section 7. |

`XGN-DEC-013` (channel tier availability) is owned by XGN-10 and is applied by sections 4.1 and 4.2. `XGN-DEC-004` (capability length) is owned by XGN-29 and is applied by PRB-08.

## 10. Open decisions

| ID | Question | Note |
|---|---|---|
| `XGN-DEC-016` | Which chat applications are supported, and whether a named list is published at all. | Open. Qualification in this module is by probe. |

No further decision is outstanding at this level.

## 11. Related specifications

- **Depends on:** XGN-01 (vocabulary, decision register, claim status), XGN-02 (shared contracts), XGN-03 (capability discovery, consistency requirements), XGN-10 (transport surface, tiers, limits), XGN-14 (panel rendering used by the probe replies).
- **Referenced by:** XGN-09 (channel selection), XGN-16, XGN-21, XGN-26 (conformance testing).
- **Predecessor material:** the eleven-probe table and channel decision matrix of the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026), and the acceptance tests P12 to P28 of technical review B. The suite is renumbered PRB-01 to PRB-28 in that order; the inherited 43-character token in the capability-length probe is corrected to the production 22-character value; the approval-prompt budget, the separate measurement of delivery, execution, prompting and retrying, and the requirement that PRB-09 pass are added in response to review A.
- **Out of scope:** the transport surface, the command catalogue, the capability lifecycle, the panel rendering, and the framework-wide conformance suite.
