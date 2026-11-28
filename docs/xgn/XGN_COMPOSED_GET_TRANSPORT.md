# XGN-10: XGN Composed GET Transport

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-10 | **Group:** C — Transport
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-09 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** XGN-DEC-016

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines **Composed GET**: the transport by which an agent that can only issue HTTPS GET requests discovers its session, composes one command, and reads the reply. It defines the transport model, the single fixed request path, the anti-prefetch protections, the channel tiers, the transport-level limits, the conditions under which GET is acceptable as a command channel, the transport-level link lifecycle, and the HTTP response envelope.

Out of scope: the command catalogue, parameter order and sequence semantics (XGN-28); the capability format, binding, rotation and replay handling (XGN-29); the panel block semantics and the marking of untrusted content (XGN-14); channel selection and transport fallback (XGN-09); the qualification probes (XGN-30); grant scope, policy, approval and execution (XGN-04, XGN-15, XGN-16, XGN-17); the error and rate-limit catalogue (XGN-23).

## 2. The transport model

The agent this transport exists for is severely constrained. The transport is designed around those constraints, not in spite of them.

| Agent constraint | Consequence for the transport |
|---|---|
| Can issue HTTPS GET only | The method carries the command. Every state-changing instruction travels in a URL, which is the central risk of this transport (section 7). |
| Cannot set request headers | No `Authorization`, no `Content-Type`, no cookie. All authority travels in the path or the query string. Only the session command capability travels there; no long-lived credential ever does (INV-14). |
| Cannot run a shell | No `curl`, no `git`, no direct provider call, no request body, no second connection. The only reachable endpoint is the single navigation path in section 3. |
| May be unable to compose a URL | Some fetch tools open only a URL that appears verbatim in a reply; others open only a URL the agent composed. Which is supported is what channel tier measures (XGN-30). |
| Has no storage and no clock | The reply MUST carry everything needed to continue: the state, the legal commands, the next sequence number, the applicable limits and the new capability. |
| Carries a URL into a chat transcript | The capability in that URL is exposed to the chat provider, to link previewers, to scanners and to intermediaries. It MUST therefore be short-lived, single-use and revocable (XGN-29). |

**Rule.** The Composed GET transport MUST NOT require the agent to perform any action other than opening a URL. A workflow that cannot be expressed as a sequence of GET requests to one fixed path does not belong on this transport.

## 3. The fixed path and the request form

Every command travels over one path, with all parameters in the query string.

```text
GET https://<host>/gitana/nav/v1/s?t=<capability>&cmd=<command>&<params>&seq=<n>
```

| Element | Rule |
|---|---|
| Scheme | `https` only. A plaintext request MUST be refused. There is no downgrade path. |
| Host | The operator-visible host for the deployment. It is part of the capability base and MUST NOT vary within a session. |
| Path | Exactly `/gitana/nav/v1/s`. It MUST NOT vary by command, operation, state or parameter. No per-action routes, no path suffixes, no matrix parameters. |
| `t` | The session command capability (XGN-29). The only authority the URL carries. |
| `cmd` | The command name (XGN-28). |
| `<params>` | The parameters of that command, in the fixed order the command defines (XGN-28). |
| `seq` | The session sequence number (XGN-28). Always last, so that a link truncated at the end is detectable. |

**Rules.**

- A request to any other path MUST be refused as `PATH_NOT_SUPPORTED`. It MUST NOT be redirected, rewritten or treated as a command.
- A method other than `GET` MUST be refused as `METHOD_NOT_SUPPORTED`.
- A parameter other than `t`, `cmd`, the command's own parameters and `seq` MUST be refused as `VALIDATION_FAILED`. A repeated parameter MUST be refused as `VALIDATION_FAILED`.
- `seq` MUST be last. A request whose parameters are in any other order MUST be refused as `VALIDATION_FAILED`.
- A request carrying `cmd` but no `seq` MUST be refused as `SEQ_MISSING`. It is a malformed command, not a read.
- A request carrying no `cmd` is a **capability base read**; see section 4.
- The gateway MUST reject an over-long URL before forwarding, so an oversized command never reaches the trusted runtime.

## 4. Anti-prefetch protections

A GET command is a side effect carried in a URL. Chat applications, link previewers, security scanners, proxies and caches may request a URL without the agent intending anything. The protections below are the reason this transport can be used at all. They are mandatory, not optional hardening.

| ID | Protection | Requirement |
|---|---|---|
| P-01 | Capability base is inert | A request carrying the capability and no `cmd` MUST return the current panel and MUST consume nothing. It MUST NOT change the draft, the request, the grant or the next sequence number (XGN-DEC-017). |
| P-02 | Consumption requires a complete valid command | A capability MUST be consumed only by a request carrying a valid `cmd`, the expected `seq` and every parameter that command requires. A request missing `cmd` or `seq` MUST NOT consume the capability and MUST NOT advance the sequence number. |
| P-03 | An inert read rotates nothing | A capability base read MUST return the current capability base and MUST NOT supersede it. A scanner cannot invalidate the agent's link by reading it. |
| P-04 | One in-flight command per session | The session MUST admit at most one command in flight. A second command submitted while one is in flight MUST be refused as `CONCURRENT_COMMAND` and MUST NOT consume the capability. |
| P-05 | Concurrent use of one capability | Exactly one of two concurrent submissions of the same capability MUST take effect. The other MUST be refused without effect. |
| P-06 | No store, no cache | Every response MUST carry `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache`. A cached reply MUST NOT be served for a later request, and a response MUST NOT be reused after the capability it references has been superseded. |
| P-07 | Capability bound to a permitted command set | A capability MUST be bound to the command names and parameter names it permits (XGN-29). A request whose `cmd` is not in the bound set MUST be refused and MUST NOT consume the capability. |
| P-08 | No complete command URL in any reply | A reply MUST NOT contain a complete command URL, that is, a capability base followed by a `cmd` parameter. The base alone is delivered; the command URL is assembled by the agent at the moment it issues the command. A panel template MUST NOT contain a capability value. |
| P-09 | Unique URLs | Every response carrying a capability base MUST carry one never issued before, so no two command URLs are identical and a cache cannot serve one in place of another. |
| P-10 | No effect from reading | A read of any kind MUST NOT advance the sequence number, change the draft, alter a request or change the grant. |

**Why the protections are layered.** A prefetcher holds the capability base. It can open it (P-01), receives the current panel and a copy of the base (P-03), and cannot add a command the bound command set permits (P-07) without also knowing the expected sequence number. Because no reply contains a complete command URL (P-08), a prefetcher never sees a command URL in the normal case. The sequence number, the command set and the capability together are what a prefetcher lacks.

**Residual risk.** A prefetcher that observes a complete command URL — which requires the agent to have placed one where the scanner can read it — can consume that capability. The bound command set, the expected sequence number and the operator approval boundary limit what that achieves. This residual risk is recorded in XGN-24 and is why XGN-30 PRB-12 is a mandatory qualification probe.

## 5. Channel tiers and the per-session opt-in

| Tier | What the agent may do | What the fetch tool must support |
|---|---|---|
| **Channel tier A** | Reads and navigation only: onboarding, the capability description, `status`, the grant summary. Links are issued verbatim. | Opening a URL that appears in a reply. |
| **Channel tier B** | Tier A, plus mutations whose arguments are fixed at grant time, each requiring operator approval. Links are issued verbatim. | Opening a URL that appears in a reply. |
| **Channel tier C** | Tier B, plus agent-authored arguments and content: the full command set, with parameters the agent supplies. | Extending a URL the agent received with additional `&` parameters. |

**Rules.**

- Channel tier C MUST be off by default, and MUST be enabled per session, per channel and per application, only after the application has passed qualification (XGN-DEC-013, XGN-30).
- An agent MUST NOT widen its tier, and no reply MUST advertise a tier the session does not hold.
- The tier in force MUST be stated in the `STATE` block of every panel and in the capability description.
- The opt-in is an operator action on the trusted device. The agent MUST NOT cause it.
- The channel tier MUST NOT widen the grant. Tier is a property of the channel, not of the permission (XGN-DEC-030).

## 6. Transport limits

These are the Composed GET values. They MUST be published in the capability description and, where they differ, in the panel, so the agent learns the applicable value from the session itself.

| Limit | Value | Derivation |
|---|---|---|
| Maximum complete URL length | 900 characters | The XGN ceiling, published as `max_encoded_url_chars`. The applicable limit for an application is the largest length that arrives intact, measured by XGN-30 PRB-06, and MUST NOT exceed 900. |
| Maximum encoded `data` parameter | 140 characters per command | Derived from the 250-character fetch cap measured in the inherited material. The longest command template costs 114 characters of fixed overhead for a 14-character host: 38 for the origin and path, 29 for the capability, 47 for `&cmd=append&field=content&enc=text&data=&seq=nn`. That leaves 136 characters under a 250-character cap, so 140 is the XGN ceiling and the value the panel states. The budget that applies is `min(140, applicable URL limit - template overhead)`. |
| Maximum encoded data per command | 2048 bytes (2 KB) | The framework ceiling (XGN-DEC-012). It is not reachable on this transport; the 140-character budget binds first. |
| Maximum decoded value per command | 2048 bytes (2 KB) | The framework ceiling on one field value (XGN-DEC-012). Decoded bytes are what the runtime validates and stores. |
| Maximum draft total | 65536 bytes (64 KB) | XGN-DEC-012. |
| Maximum parts per field | 64 | XGN-DEC-012. |
| Maximum commands per session | 512 | XGN-DEC-012. |
| Maximum session lifetime | 2 hours, operator-extendable | XGN-DEC-008. |
| Capability lifetime | 10 minutes, or until used | XGN-DEC-008. |
| Exact-retry window | 90 seconds | XGN-DEC-007. |
| Status poll allowance | 6 polls per 60 seconds, 60 polls per session | The Composed GET status rate limit. It MUST be published in the capability description and in the `WAIT` panel (XGN-DEC-023). The general rate-limit framework is owned by XGN-23. |
| Maximum panel size | 8192 bytes | The panel is a navigation surface, not a data channel. |
| Maximum panel line length | 120 characters | A panel MUST stay readable in a narrow terminal and in a chat window. |

**Rule.** Every limit MUST be enforced at the earliest practical validation boundary and re-checked by the trusted runtime (XGN-03 CD-09). A limit the gateway enforces and the runtime does not is not enforced.

## 7. GET as a command channel: the risk and the safeguards

### 7.1 The risk, stated plainly

**GET is unsafe as a command channel.** HTTP defines GET as safe and idempotent: it retrieves a representation and changes nothing. Using it to carry a state-changing instruction breaks that expectation, and every component in the path — the chat application, its fetch service, an indexer, a link previewer, a security scanner, a corporate proxy, a caching layer, a browser, the agent's own retry logic — is entitled to behave on the assumption that a GET changes nothing.

| # | Consequence of GET semantics | Effect on this transport |
|---|---|---|
| 1 | Any intermediary may issue the request without the agent acting. | A capability is consumed, or a command executes, without the agent's intent. |
| 2 | Caches store and replay responses. | A stale panel is served as current, or a consumed capability is replayed. |
| 3 | Clients and proxies retry GET automatically on failure. | The same command is issued twice. |
| 4 | The URL is exposed wherever URLs go. | The capability reaches chat history, logs, referrers, telemetry and intermediaries. |
| 5 | The method has no body. | Every value must fit in the query string, so a payload needs many commands, and each command is another prefetchable URL. |

**This transport does not claim that the risk is removed.** It claims the risk is bounded, detected and never authoritative. Execution authority stays on the trusted device, every mutating operation requires operator approval, and a stolen or scanned capability can do nothing the operator has not approved.

### 7.2 The mandatory safeguard set

The channel MUST NOT be used for a session unless every one of these is in place. This list is the minimum, not a starting point for negotiation.

| # | Safeguard | Owner |
|---|---|---|
| 1 | The capability base is inert; only a complete valid command with the expected sequence number consumes a capability. | XGN-10, XGN-28, XGN-29 |
| 2 | One in-flight command per session; concurrent use of one capability is refused. | XGN-10, XGN-28 |
| 3 | Every response carries a no-store, no-cache directive; no reply is cached or reused. | XGN-10, XGN-20 |
| 4 | No complete command URL is placed in a reply; command URLs are assembled by the agent at use time. | XGN-10, XGN-14 |
| 5 | The capability is single-use, short-lived, rotated on every response and revocable. | XGN-29 |
| 6 | A capability is bound to the command and parameter names it permits, so a scanner cannot act on it. | XGN-29 |
| 7 | Reuse of a consumed or superseded capability is recorded and suspends the session. | XGN-29, XGN-24 |
| 8 | The exact-retry window returns the cached reply and produces no second effect. | XGN-29 |
| 9 | Every mutating operation requires operator approval on the trusted device. | XGN-16 |
| 10 | Scope, path and precondition validation happen on the device, before any external service is contacted. | XGN-15, XGN-19 |
| 11 | The gateway validates shape, size, method, path and rate only, and asserts nothing about acceptance or execution. | XGN-20, XGN-00 |
| 12 | The application has passed qualification for the tier it is used at. | XGN-30 |
| 13 | The transport is refused when the agent cannot carry a command URL intact, or when prefetch consumes or executes a command. | XGN-30 |

### 7.3 Conditions under which the channel MUST be refused

The transport MUST NOT be used, and a session on it MUST NOT be continued, when any of the following holds.

| # | Condition | Result |
|---|---|---|
| 1 | The application opens links it was given but cannot extend a server-issued link with parameters. | Tier C refused; tier A or B only. |
| 2 | The largest URL that arrives intact is below 250 characters. | Tier C refused; tier A or B only. |
| 3 | An automatic prefetch consumes a capability, or executes a command. | Tier C refused; a prefetch that executes a mutating command refuses the channel entirely. |
| 4 | The application corrupts query values, truncates them at a reserved character, or substitutes a space for `+`. | Tier C refused. |
| 5 | The application requires an operator approval prompt for every fetch, beyond the budget in XGN-30 PRB-05. | Tier C refused as unusable; tier A or B only. |
| 6 | The chat provider renders the capability in a shared, public or archived transcript the operator cannot revoke. | The channel MUST NOT be used for that conversation. |
| 7 | The gateway or the runtime cannot enforce the size and shape limits at the boundary. | The channel MUST NOT be used. |
| 8 | A capability on this route would be logged in full by an intermediary that cannot be configured otherwise, and the operator has not accepted the residual risk. | The channel MUST NOT be used until the operator accepts it in writing. |

## 8. Link lifecycle at transport level

The capability lifecycle itself is owned by XGN-29. This section states the transport-visible order.

| Stage | What the agent receives | What changes |
|---|---|---|
| Session link | One URL: the capability base. It is the only URL the agent receives that Gitana did not issue in reply to a command. | The session is created. Nothing is consumed. |
| First open | The onboarding panel, the pairing challenge reference, the capability description and the current capability base. | The session moves to `LOCKED`. Nothing is consumed (XGN-DEC-017). |
| Command | The agent appends `&cmd=…&…&seq=<n>` to the current base and fetches it. | On acceptance: the command takes effect, the sequence number advances, a new capability is issued. |
| Command refused | The panel with the error and the `FIX` template, plus a fresh capability. | The draft, the pending request and the next sequence number are unchanged (INV-11, XGN-DEC-024). |
| Inert read | The current panel, with the current base. | Nothing changes and nothing rotates. |
| Expiry | The capability stops working after 10 minutes or on use, whichever comes first. | The next command fails with `LINK_EXPIRED`; the agent recovers by reading `status`, and the operator may issue a new session link. |
| Revocation | Every outstanding capability of the session becomes invalid. | The session is terminal. |

**Transport-level refusal versus command-level rejection.**

| Refusal type | Raised by | Carries a capability | Effect on the current capability |
|---|---|---|---|
| Transport-level (method, path, redirect, size, rate, malformed token format) | The gateway | No | None. The current capability remains valid and usable. |
| Command-level (state, sequence, validation, scope, integrity, limit) | The trusted runtime | Yes, a fresh one | The presented capability is retired and a fresh one is issued (INV-11). |

**Rule.** The gateway MUST NOT issue a capability and MUST NOT assert that a command was accepted or executed (XGN-00 section 5).

## 9. Response format

| Property | Requirement |
|---|---|
| Media type | `text/plain; charset=utf-8`. A reply MUST NOT be HTML and MUST NOT reference an external resource. |
| Encoding | UTF-8. A reply MUST NOT contain unpaired surrogates or C0 control characters other than TAB, LF and CR. |
| Structure | Exactly the five blocks of the panel contract: `STATE`, `TASK`, `DRAFT`, `LAST`, `COMMANDS` (XGN-14). |
| Headers | `Cache-Control: no-store, no-cache, must-revalidate, private`, `Pragma: no-cache`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`. |
| Redirects | A reply MUST NOT be a redirect. A 3xx response MUST be refused as `REDIRECT_REFUSED`, because a redirect moves the capability to a host not bound to the session. |
| Status code | 200 for a processed request, 4xx for a refused request. The body of a refusal is still a five-block panel, so the agent always receives the same shape. |
| Content | Agent-written or repository-derived content MUST appear only inside an explicit read, in a marked data region (INV-13, XGN-14). |
| Size | 8192 bytes maximum. |
| Secrets | A reply MUST NOT contain a credential, a provider token, or a capability other than the one it delivers (INV-14, XGN-03 CD-10). |

**Rule.** A reply MUST NOT contain a complete command URL (P-08). The agent assembles the command URL itself.

## 10. Transport requirements

| ID | Requirement |
|---|---|
| P-11 | The transport MUST use exactly one path, `/gitana/nav/v1/s`, with all parameters in the query string, and MUST refuse any other path or method. |
| P-12 | `cmd` MUST be the first parameter after `t`, the command's own parameters MUST follow in the order the command defines, and `seq` MUST be last. |
| P-13 | A request carrying `cmd` without `seq` MUST be refused as `SEQ_MISSING`, so that a truncated link is detected. |
| P-14 | The gateway MUST reject an over-long URL, an unknown parameter and a repeated parameter before forwarding. |
| P-15 | The transport MUST require HTTPS and MUST NOT follow or accept a redirect. |
| P-16 | A transport-level refusal MUST NOT issue a capability and MUST NOT invalidate the current capability. A command-level rejection MUST issue a fresh capability and MUST leave the draft and the next sequence number unchanged. |
| P-17 | Channel tier C MUST be off by default and MUST be enabled per session, per channel, per application, after qualification. The tier in force and the applicable limits MUST be stated in the capability description and in the panel. |
| P-18 | The transport MUST be refused for an application that fails the tier C qualification probes, and the refusal MUST be recorded. |
| P-19 | The gateway MUST NOT assert that a command was accepted or executed. |
| P-20 | A reply MUST NOT contain agent-written or repository-derived content outside an explicit read. |
| P-21 | The limits in section 6 MUST be enforced at the gateway and re-checked by the runtime. |

## 11. Decisions recorded

| ID | Decision | Where it is stated |
|---|---|---|
| `XGN-DEC-013` | Channel tier availability: tier C is off by default and is enabled per session, per channel, only after the channel passes qualification. | Section 5. |
| `XGN-DEC-054` | GET as a command channel: Composed GET is acceptable only with the mandatory safeguard set in section 7.2, and MUST be refused in the conditions in section 7.3. GET is not a safe method; the risk is never removed, only bounded and detected. | Sections 7.1 to 7.3. |

`XGN-DEC-017` (the inert capability base) is owned by XGN-29 and is implemented here in P-01 and P-03. `XGN-DEC-016` (the supported chat application list) remains open and is owned by XGN-30.

## 12. Open decisions

| ID | Question | Note |
|---|---|---|
| `XGN-DEC-016` | Which chat applications are supported on this transport. | Open. This set does not settle the list. Qualification is by probe, not by name (XGN-30). |

No further decision is outstanding at this level.

## 13. Related specifications

- **Depends on:** XGN-01 (vocabulary, identifiers, percent-encoding conventions), XGN-02 (shared contracts, panel contract), XGN-03 (capability discovery, surface map), XGN-09 (transport abstraction and selection).
- **Referenced by:** XGN-14, XGN-28, XGN-29, XGN-30, XGN-20, XGN-23, XGN-24, XGN-26.
- **Predecessor material:** the *Navigation Transport Specification* (0.1-draft, 25 November 2026) sections 2, 6, 9.1, 9.2, 9.3, 10 and 12, and the Composed GET sections of the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026). These are predecessor material, redistributed and corrected here; their claims remain proposals until separately verified.
- **Out of scope:** the command catalogue, the capability lifecycle, the panel block semantics, the probe suite, and every question of grant, policy, approval and execution authority.
