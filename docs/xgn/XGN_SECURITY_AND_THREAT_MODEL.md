# XGN-24: XGN Security and Threat Model

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-24 | **Group:** F — Assurance
**Owner:** Gitana Architecture
**Depends on:** XGN-00, XGN-01, XGN-02 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 12

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module states the security posture of the XGN Framework: trust boundaries, attack surfaces, the threat inventory, the mitigations and their honest residual risk. It is **cross-cutting**; every other module MUST satisfy the controls stated here.

This module owns the threat model and the mitigation list, not the mechanisms it names: capability minting and consumption live in XGN-29, Composed GET mechanics in XGN-10 and XGN-28, qualification probes in XGN-30, privacy and retention in XGN-25, tests in XGN-26, operational handling in XGN-27. Where this module states a limit it restates the value owned elsewhere and cites the owner, so the two cannot drift.

**Out of scope:** capability lifecycle mechanics; transport framing; the audit event model and retention periods; the test catalogue; alerting and change control.

## 2. Security posture

### 2.1 The posture XGN accepts

An agent's authority travels to Gitana in a URL as a bearer capability. **A bearer capability in a URL cannot be made secret-proof.** It can be made short-lived, single-use, scope-bound, revocable and observable. Until it is revoked or expires, whoever holds a valid current capability can act within the granted scope.

XGN therefore makes no claim that misuse is prevented. Prevention of the realistic failure modes — prefetch, preview, scanner, cache, replay — is designed; detection of misuse is the backstop.

| ID | Requirement |
|---|---|
| H-01 | Every capability in a URL MUST be treated as a disclosed bearer token. The protection model is short lifetime, single use, scope binding, revocation and detection. Detection is the control, not prevention. |
| H-02 | The trusted runtime MUST be the only security anchor. A control enforceable only by the agent, the chat application or the fetch service MUST NOT be relied on. |
| H-03 | Every input crossing a trust boundary MUST be re-validated on the device side, even if the gateway already validated it. |

### 2.2 Trust boundaries

| Boundary | Trusted side | Untrusted side | Controls at the boundary |
|---|---|---|---|
| B-1 agent to transport | none | agent, chat application, fetch tool | capability validation, sequence check, shape and size limits, rate limit (XGN-28, XGN-29, XGN-23) |
| B-2 transport to gateway | gateway | network, chat provider, intermediaries | TLS, query-string redaction, no content retention (XGN-20) |
| B-3 gateway to runtime | runtime is authoritative | gateway | re-validation; the device record wins every disagreement (XGN-00 §5.1, XGN-20) |
| B-4 runtime to operator | operator on the trusted device | agent impersonation, chat transcript | operator-plane authentication independent of every agent-plane credential (XGN-05, XGN-16) |
| B-5 runtime to external service | provider adapter | provider | scoped provider credential; precondition re-check immediately before dispatch (XGN-17, XGN-18, XGN-19) |
| B-6 repository content to agent plane | nothing — content is data | repository content, agent-written content | content marked as data, never as instruction (XGN-14, XGN-16) |

### 2.3 Plane discipline

- The agent plane holds no operator verb. No agent-visible reply may contain a link, parameter or instruction that performs an operator-plane action (XGN-00 §4.2, XGN-14).
- Operator-plane authentication MUST be independent of every agent-plane credential and of the provider credential (XGN-05).
- A chat message is never authorisation. It may inform an operator; it may never substitute for an operator-plane action on the trusted device (INV-02).

## 3. Attack surfaces

| # | Surface | What crosses it | Why it is dangerous |
|---|---|---|---|
| 1 | The URL | the command capability | a bearer token that is copied, logged and historic |
| 2 | The chat transcript | session link, command URLs, agent-written content | outside Gitana's control; retained by the provider |
| 3 | Fetch tool, link previewer, scanner, crawler, proxy | links found in content | they open URLs without the agent's intent |
| 4 | Gateway and edge infrastructure | full URLs, headers, bodies | access logs and caches are widely readable |
| 5 | Panel and approval surface | operation, target, sizes, digests, expandable content | what the operator decides on; a manipulation target |
| 6 | Repository content read by the agent | file text, path names, commit messages | third-party text reaching a decision maker |
| 7 | The operator device | grants, approvals, credentials | its compromise defeats every control |
| 8 | The provider credential store | a long-lived provider credential | its loss bypasses the framework |
| 9 | The audit log | identifiers, digests, outcomes | tampering hides misuse |
| 10 | The agent itself | every composed command | the agent is untrusted by definition |

## 4. Threat table

| ID | Threat | Attack surface | Mitigation | Residual risk | Owning module |
|---|---|---|---|---|---|
| T-01 | Stolen session link | the first capability delivered to the agent | session lifetime 2 h; pairing gate before any command; scope-triple binding; approval on every mutating commit; immediate revocation; fork detection | an attacker acting before the legitimate agent holds a valid session until detection; loss bounded by the grant | XGN-04, XGN-05, XGN-16, XGN-29 |
| T-02 | URL exposure through chat history, provider logs, browser history and intermediaries | the URL as text | capability lifetime 10 min and single use; one command per capability; `Cache-Control: no-store`; query-string redaction at the edge; the capability appears only in the link the agent is expected to use | the URL may be copied and replayed inside its lifetime; intermediaries may retain it beyond Gitana's control | XGN-20, XGN-29 |
| T-03 | Replay of a command capability | a consumed capability | single-use consumption; exact retry inside the 90 s window returns the original reply; any other reuse is recorded and suspends the session | a replay inside the window is indistinguishable from a lost reply; detection starts after the window | XGN-29, XGN-21 |
| T-04 | Automated prefetch or link scanning consumes a capability | previewers, scanners, crawlers | the bare capability base is inert; a capability is consumed only by a complete valid command carrying the expected sequence number and matching capability digest; no-store | a scanner that completes a full command consumes it; the misuse is detected by suspension, not silently corrupting a draft | XGN-10, XGN-29, XGN-30 |
| T-05 | GET used as a command channel | the transport itself | command-completeness rule; no side effect on navigation; POST preferred where the client supports it; one command in flight per session; no caching | GET semantics stay visible to intermediaries; a third party that learns a complete command URL can trigger it | XGN-09, XGN-10, XGN-28 |
| T-06 | A capability appears in a log | gateway, edge, fetch provider, crash dump | query-string redaction; the raw capability is never written to a log; a stable `capability_id` replaces it; no capability in an event, panel, metric or error | intermediaries outside Gitana's control may retain a URL; a leaked capability is a short-lived exposure | XGN-20, XGN-25 |
| T-07 | Prompt injection from repository content, a file or a chat transcript | content the agent reads | content is data and never instruction; the approval summary is runtime-generated from the envelope; the agent plane holds no operator verb; operator approval limits the blast radius; content size bounds | an injection can still make the agent propose a harmful but in-scope request; it cannot approve it | XGN-14, XGN-15, XGN-16 |
| T-08 | A malicious or confused agent | the agent itself | every request is validated against grant, policy and preconditions; approval for mutating operations; scope cannot be widened by the requester; command budget 512 per session | the agent can exhaust operator attention; a confused agent can burn the session budget | XGN-15, XGN-16, XGN-23 |
| T-09 | Scope escape by path traversal, encoded separators or Unicode | path and branch arguments | canonicalisation at the authoritative validation boundary; exactly one decode; rejection of `..`, absolute paths, `%2F`, `%5C`, control characters, confusables and non-normalised forms; comparison against the grant prefix at a path boundary | a defect in the canonicaliser; covered by the scope tests in XGN-26 | XGN-08, XGN-15, XGN-19 |
| T-10 | Cross-session access | capability and identifiers | a capability is bound to one session and one grant; identifiers are unguessable; no session may address another's state | a runtime defect that accepts a foreign capability | XGN-04, XGN-15, XGN-29 |
| T-11 | Operator impersonation | chat, agent, network | the pairing challenge is a non-secret device-displayed code and is never an authorisation factor; only operator-plane actions on the trusted device change session state | the agent can display anything it likes; operator judgement remains a control | XGN-05, XGN-16 |
| T-12 | Approval phishing through a manipulated approval summary | the approval surface | the summary is generated by the runtime from the canonical envelope; labels are runtime-generated and never derived from content; the summary carries the digest the operator can compare | a compromised runtime or device can forge a summary; the defence is digest binding, not presentation | XGN-16, XGN-31 |
| T-13 | Race between approval and dispatch (TOCTOU) | approval to dispatch | the approval binds to the envelope digest; the runtime re-verifies the digest before dispatch and aborts on mismatch; provider preconditions detect concurrent change | a provider that changes state between the precondition check and the write leaves a narrow window | XGN-16, XGN-17, XGN-31 |
| T-14 | Gateway compromise | the gateway | the gateway holds no authoritative state and no content; the device record wins; the runtime re-validates every request; the provider credential never reaches the gateway | a compromised gateway can read traffic in flight, delay, drop or replay within the protocol, and deny service | XGN-20 |
| T-15 | Denial of service by prompt flooding | agent, chat, operator | command budget 512 per session; 64 parts per field; 64 KB draft; 2 KB encoded data per command; status poll interval 5 s; cap of 5 consecutive invalid commands; one approval request per mutating commit per step | an attacker can exhaust operator attention; bounded, not eliminated | XGN-16, XGN-23, XGN-28 |
| T-16 | Stolen provider credential | the credential store | the credential never leaves the trusted runtime; fine-grained scope limited to the approved repository; rotation; the agent never holds one | compromise of the device yields the provider credential | XGN-05, XGN-17, XGN-18 |
| T-17 | A malicious relay | the relay | the relay is treated as untrusted transport; device-side validation of every request; no content retention; the device record is authoritative | a relay can withhold, reorder or replay within the protocol and read content in transit unless end-to-end encryption is added | XGN-20 |
| T-18 | Replay of a stale approval at dispatch | the approval record | an approval carries a 10 minute expiry and the envelope digest; dispatch requires a live approval for that exact digest | none inside the approval lifetime | XGN-16, XGN-31 |
| T-19 | Audit log tampering | the audit log | append-only; a correction is a new event; each event carries the digest of its predecessor; the log is separate from session state | an attacker with storage write access can suppress events, though not alter recorded ones | XGN-25 |

## 5. GET as a command channel

### 5.1 Why the channel is exposed

A GET URL is visible, cacheable, prefetchable, linkable and historic. Four risks are specific to it:

- **URL prefetching.** Chat clients, link previewers, corporate proxies and security scanners open links found in content, without the agent intending a command.
- **Caching.** Clients and intermediaries may cache a response and re-serve it later. A cached reply produces no second effect, but it can mask a failure and mislead the agent.
- **Scanners.** Automated scanners treat a GET as harmless and safe to fetch.
- **Proxies.** Query strings are routinely written to access logs and forwarded to intermediaries.

### 5.2 Mandatory safeguards

The channel is acceptable only because of these safeguards.

| Safeguard | Requirement | Owner |
|---|---|---|
| Command completeness | A capability MUST be consumed only by a request carrying a valid `cmd`, the expected sequence number, the session binding and the matching capability digest. A bare capability base returns the current panel and consumes nothing. | XGN-29, XGN-DEC-017 |
| No side effect on navigation | A GET that is not a complete command MUST NOT change session state, advance the sequence number or consume a capability. | XGN-28 |
| No caching | A reply carrying a capability or agent-authored content MUST carry `Cache-Control: no-store`; the gateway MUST NOT cache it. | XGN-20, XGN-DEC-027 |
| One command in flight | At most one command may be in flight per session; a concurrent submission of the same capability MUST be rejected, not queued. | XGN-29, XGN-23 |
| Method preference | Where a client can issue a POST, the transport MUST use it. Composed GET remains the fallback for clients that cannot. | XGN-09, XGN-10 |
| Bounded URL content | Agent-written content MAY appear in a URL only as the encoded `data` value of an `append` or `replace` command, bounded to 2 KB of encoded data. | XGN-28, XGN-DEC-012 |
| Link hygiene | The runtime MUST NOT render a complete command URL for the operator, nor echo one into prose, a summary, a notification or a support bundle. | XGN-14, XGN-27 |
| Prefetch qualification | A channel MUST NOT be enabled for a client until the prefetch probe in XGN-30 shows that opening a link found in a fetched reply consumes no capability. | XGN-10, XGN-30 |
| Revocation | Operator revocation MUST invalidate every outstanding capability immediately, without waiting for expiry. | XGN-04, XGN-29 |

### 5.3 The honest statement

The channel cannot be made safe against an intermediary that sees a complete command URL. It can be made safe against the realistic failure modes — prefetch, preview, scanner, cache — and detectable when it fails. This is why channel tier C is off by default and enabled per session, per channel, only after qualification (XGN-DEC-013).

## 6. Prompt-injection defences

| Defence | Requirement | Owner |
|---|---|---|
| Content is data | Repository content and agent-written content appear only inside an explicit read, in a delimited data block, and MUST NOT be presented as an instruction, a command template or a system message. | XGN-03, XGN-14 |
| Runtime-generated approval summary | The approval summary MUST be produced by the runtime from the canonical envelope and the policy decision. It MUST NOT quote, echo, summarise or interpret agent-written content, repository content or chat text. | XGN-16 |
| No operator verb on the agent plane | No agent-visible reply may contain a link, parameter or instruction that performs an operator-plane action. | XGN-00 §4.2, XGN-14 |
| Approval is the blast-radius control | A mutating operation never reaches the external service without an operator decision recorded against the envelope digest. An injected instruction cannot forge that decision. | XGN-16, XGN-31 |
| Content size bounds | An explicit read is bounded at 4 KB per page and 16 pages per field; a command carries at most 2 KB of encoded data. Exceeding a bound truncates with a marker and never fails open. | XGN-23, XGN-28 |
| Labels are runtime-generated | No panel label, error message or approval line is derived from repository content or agent-written content. | XGN-02 §11, XGN-14 |
| Chat is not a channel of authority | Text pasted into chat is never read by the runtime and cannot change a decision. | XGN-00 §4.2 |

## 7. Capability theft, replay and fork detection

**Detection events.** The runtime MUST record and surface: reuse of a consumed capability, use of a superseded capability, a sequence gap, an unknown session, a rate-limit breach, and a capability presented by a second client fingerprint.

**What a fork looks like.** Two clients hold the same session. One consumes a capability first. The other presents a consumed or superseded capability, which is recorded as reuse, which suspends the session and alerts the operator.

**The limits of detection.** A threat model that overstates its own detection is worthless, so these are stated plainly.

1. Detection requires the misuse to reach Gitana. An attacker who acts first and completes a valid command leaves no fork.
2. Detection is retrospective. The first use is never blocked; only later use is.
3. Source address and user agent are risk signals only, never authentication (XGN-DEC-007).
4. An attacker who stays within the grant and uses only fresh capabilities produces a session that looks normal until the operator reads the audit trail.
5. Detection depends on the device. If the device is offline, reuse is detected on reconnection, or not at all.
6. A capability captured by an intermediary's log is undetectable until it is used. Single use and the 10 minute lifetime bound that exposure.

## 8. Credential exposure

| ID | Requirement |
|---|---|
| H-14 | No long-lived credential may appear in a URL, a link, a reply, a log, an error message, an event or a metric. The only credential class permitted in a URL is the short-lived, session-bound, single-use command capability defined in XGN-29 (INV-14). |
| H-15 | Provider credentials MUST NOT leave the trusted runtime. Only the provider adapter attaches one, at dispatch. The agent, the gateway and the chat application never receive one. |
| H-16 | The capability in the link the agent is expected to use is the only secret-bearing element of that link. It MUST NOT be echoed into a summary, a diff, an error message, a notification, an event or a metric label. |

## 9. Injection and abuse cases

| Case | Required behaviour | Control |
|---|---|---|
| A repository file instructs the agent to widen scope | the instruction is data; the proposal is rejected with `SCOPE_VIOLATION` before any provider call | XGN-08, XGN-15 |
| A repository file embeds a URL carrying a command capability | the capability is foreign to the session; the request fails session binding and is rejected | XGN-04, XGN-29 |
| A chat message asks the agent to send its capability to a third party | the runtime never emits the capability into prose; the only copy is the link the agent is expected to use | XGN-03, XGN-14 |
| A confused agent issues repeated invalid commands | the 5th consecutive invalid command suspends the session and notifies the operator; the draft is preserved | XGN-23 |
| An agent tries to widen scope by setting the branch field | the branch is fixed by the grant for `write_file`; the attempt is rejected as `SCOPE_VIOLATION` | XGN-04, XGN-19 |
| A prompt flood of 512 commands | the command budget is exhausted; the session moves to a gated state and requires the operator | XGN-23, XGN-28 |
| An approval summary crafted to mislead the operator | the summary is runtime-generated from the envelope and carries its digest; a summary that does not match the envelope cannot be produced by the runtime | XGN-16, XGN-31 |

## 10. Required mitigations

Each row is a requirement of the XGN Framework. The last column names the module that owns the mechanism; XGN-24 owns the requirement text and the inventory.

| ID | Requirement | Mechanism owner |
|---|---|---|
| H-01 | A capability in a URL is a disclosed bearer token; detection is the control, not prevention. | XGN-24 |
| H-02 | The trusted runtime is the only security anchor. | XGN-17, XGN-05 |
| H-03 | Every input crossing a trust boundary is re-validated on the device side. | XGN-08, XGN-15 |
| H-04 | The agent plane holds no operator verb. | XGN-00, XGN-14 |
| H-05 | A capability is consumed only by a complete valid command; the bare capability base is inert. | XGN-29, XGN-DEC-017 |
| H-06 | A consuming request MUST carry the expected session binding, the expected sequence number and the matching capability digest. | XGN-28, XGN-29 |
| H-07 | A GET that is not a complete command MUST NOT change state or advance the sequence number. | XGN-28 |
| H-08 | A reply carrying a capability or agent-authored content MUST carry `Cache-Control: no-store` and MUST NOT be cached by the gateway. | XGN-20, XGN-DEC-027 |
| H-09 | At most one command may be in flight per session; a concurrent use of one capability is rejected. | XGN-29, XGN-23 |
| H-10 | A capability is valid for 10 minutes or one use; a session for 2 hours; revocation invalidates outstanding capabilities immediately. | XGN-29, XGN-04, XGN-DEC-008 |
| H-11 | Reuse of a consumed or superseded capability MUST be recorded, MUST suspend the session and MUST alert the operator. | XGN-21, XGN-22, XGN-27 |
| H-12 | An exact retry inside the 90 second idempotency window returns the original reply and produces no second effect; outside the window it is reuse. | XGN-29, XGN-DEC-007 |
| H-13 | Fingerprints MUST NOT be used as authentication. | XGN-29 |
| H-14 | No long-lived credential in a URL, a link, a reply, a log, an error message, an event or a metric. | XGN-20, XGN-25 |
| H-15 | Provider credentials never leave the trusted runtime. | XGN-17, XGN-18 |
| H-16 | The capability is the only secret-bearing element of the link the agent is expected to use. | XGN-29, XGN-25 |
| H-17 | Canonical scope validation: exactly one decode; rejection of `..`, absolute paths, encoded separators, control characters, confusables and non-normalised Unicode; comparison against the grant prefix at a path boundary. | XGN-08, XGN-15 |
| H-18 | Repository visibility and provider authorisation MUST be re-checked immediately before dispatch, not only at commit. | XGN-17, XGN-19 |
| H-19 | Repository and agent content appear only in an explicit read, marked as data, never as instruction. | XGN-14, XGN-16 |
| H-20 | The approval summary is generated by the runtime from the canonical envelope and the policy decision, and MUST NOT quote content. | XGN-16 |
| H-21 | An approval binds to the envelope digest; the runtime re-verifies the digest before dispatch and aborts on mismatch; provider preconditions detect concurrent change. | XGN-16, XGN-31 |
| H-22 | Budgets enforced at the earliest practical boundary and re-checked by the runtime: 512 commands per session, 64 parts per field, 64 KB draft, 2 KB encoded data per command, 5 consecutive invalid commands, status poll interval 5 s, one approval request per mutating commit per step. | XGN-23, XGN-28, XGN-DEC-012 |
| H-23 | The gateway MUST NOT retain content or authoritative state; the device's record wins every disagreement. | XGN-20, XGN-00 §5.1 |
| H-24 | The audit log is append-only and MUST NOT contain content, credentials or capabilities. | XGN-25 |
| H-25 | The runtime MUST record and surface capability reuse, sequence anomalies, out-of-scope attempts and unknown outcomes. | XGN-22, XGN-25 |

## 11. Decisions recorded

| ID | Decision as applied here | Authority |
|---|---|---|
| XGN-DEC-004 | Capability length is 22 base64url characters, 128 bits of entropy. | XGN-29 |
| XGN-DEC-007 | The idempotency window is 90 seconds; the caller identity is the session capability plus the exact link and body digest; a fingerprint is a risk signal only. | XGN-29 |
| XGN-DEC-008 | A session lives 2 hours, operator-extendable; a capability is valid until used or for 10 minutes. | XGN-04, XGN-29 |
| XGN-DEC-009 | The pairing challenge is a non-secret 6 digit code shown on the device only, never an authorisation factor. | XGN-05 |
| XGN-DEC-017 | A bare capability base is inert and consumes nothing. | XGN-29 |
| XGN-DEC-027 | The gateway MUST NOT cache a reply carrying agent-authored content. | XGN-20 |
| XGN-DEC-034 | The security posture is detection-based: a bearer capability cannot be made secret-proof, so the framework bounds exposure and makes misuse observable rather than claiming prevention. | XGN-24 |

## 12. Open decisions

| ID | Question | Default position in this module |
|---|---|---|
| OD-24-1 | May the Composed GET channel tier ever be enabled by default? | No. Off by default, enabled per session and per channel only after qualification (XGN-DEC-013). |
| OD-24-2 | Is end-to-end encryption between the gateway and the runtime required to reduce the relay's visibility of content? | Not required in this version; the relay's ability to read content in transit is an accepted residual risk of T-17. |
| OD-24-3 | What is the alert delivery path and acknowledgement time for a capability-reuse suspension? | A suspension is notified on the device and through the configured channel; the acknowledgement deadline is not settled. |

## 13. Related specifications

- **Depends on:** XGN-00, XGN-01, XGN-02.
- **Referenced by:** every module in the set; XGN-25, XGN-26 and XGN-27 in particular.
- **Predecessor material:** the threat model in §10 of the *Navigation Transport Specification* (0.1-draft) and the security findings of technical reviews A and B, redistributed and strengthened here. Their claims remain proposals until verified by the tests in XGN-26 and the probes in XGN-30.
- **Out of scope:** capability lifecycle mechanics (XGN-29), GET channel mechanics (XGN-10, XGN-28), the audit event model and retention (XGN-25), conformance testing (XGN-26), operations and change control (XGN-27).
