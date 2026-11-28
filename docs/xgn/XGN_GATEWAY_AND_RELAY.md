# XGN-20: XGN Gateway and Relay

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-20 | **Group:** E — Relay and reliability
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** GW-OD-01, GW-OD-02 (section 13)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the **XGN Gateway**: the network-facing ingress that receives XGN traffic, applies transport protections, and forwards it to the trusted runtime. It specifies what the gateway may decide, what it must never decide, what it may hold, and how it behaves when the runtime is unreachable.

The gateway is the component an attacker reaches first. Its value is that it is **not** the component that matters. Authority lives on the trusted runtime (XGN-17). The gateway's job is to make the pipeline cheap to abuse and obvious to monitor, and to keep every path to the runtime narrow and bounded.

**In scope:** ingress acceptance, routing, transport protections, shape and size validation, rate enforcement, correlation, forwarding, relay logging and retention, availability behaviour, deployment topology, deployment verification.

**Out of scope:** session and grant state (XGN-04), the request lifecycle (XGN-06), draft content and its lifecycle (XGN-07), validation and canonicalisation of operation arguments (XGN-08), policy decisions (XGN-15), approval (XGN-16), execution (XGN-17), the state machine (XGN-21), reconciliation (XGN-22), the error catalogue (XGN-23), content retention of the runtime and the audit log (XGN-25), the threat model (XGN-24).

## 2. Gateway responsibilities

| ID | Responsibility | Requirement |
|---|---|---|
| GW-01 | Ingress | The gateway MUST be the only network-facing entry point for XGN traffic. It MUST reject traffic that does not match a declared route. |
| GW-02 | Routing | The gateway MUST route each delivery to the trusted runtime that owns the session named by the presented capability. It MUST NOT fall back to another runtime, and MUST NOT fail over to a runtime the operator has not authorised for the session. |
| GW-03 | Transport protections | The gateway MUST enforce the transport protections declared by the transport module in use: capability validity, single use, method and route shape. It MUST NOT mint a capability that is not bound to the session, the transport and the expected next sequence number. |
| GW-04 | Shape validation | The gateway MUST validate parameter names, parameter count, parameter order, duplicate parameters and value types against the command definitions (XGN-28). |
| GW-05 | Size validation | The gateway MUST enforce the encoded size limits before forwarding, and MUST NOT forward a delivery that exceeds them. |
| GW-06 | Rate enforcement | The gateway MUST enforce the ingress rate limits in section 8. |
| GW-07 | Correlation | The gateway MUST bind a delivery to the session it belongs to, using only the session identifier and the capability it presented, and MUST propagate identifiers minted by the runtime unchanged. |
| GW-08 | Forwarding | The gateway MUST forward the delivery to the trusted runtime, preserving the capability, the transport framing and the request body without modification of the command's meaning. |
| GW-09 | Reply shaping | The gateway MUST return the runtime's reply to the agent. It MUST NOT add, remove or rewrite a panel block, a field, a limit or an error (XGN-14 owns the panel). |
| GW-10 | Reporting | The gateway MUST emit its own telemetry for rejected deliveries, rate rejections and runtime-unreachable outcomes, in the event shape of XGN-02 §9. |

**GW-11**: The gateway MUST NOT evaluate the meaning of an operation, a path, a branch or a repository. It MAY reject a delivery whose shape is wrong; it MUST NOT reject a delivery because the operation looks out of scope, and it MUST NOT accept one because it looks in scope.

## 3. The gateway holds no authoritative state

**GW-12**: The gateway MUST NOT hold authoritative state. The following is the complete list of state it MAY hold.

| Held state | Lifetime | Contents permitted |
|---|---|---|
| Rate counters | The counter window | Counts. No identifiers beyond the session identifier needed for the counter. |
| Short-lived capability registry | Until the capability expires, at most 600 seconds | The capability, the session identifier, the transport, the issue time, the observed consumption time. No arguments, no body, no content. |
| Single-reply idempotency cache | The idempotency window, 90 seconds | The reply for one delivery, where the reply carries no content (section 6). |

**GW-13**: The gateway MUST NOT hold any other state, and MUST NOT hold a copy of a session, a grant, a draft, an envelope, a policy decision, an approval, an execution record or a reconciliation record.

**Reasoning.** Authority and state cannot be separated safely when they are split across a network boundary. If the gateway held a copy of the session state, then two components would answer "what is the state of this session", and they would eventually disagree. The disagreement would have to be resolved by a rule, and that rule would in practice be decided by whichever component answered first — that is, by the attacker who reached it first. XGN-00 §5.1 therefore places all authoritative state on the trusted runtime, and the gateway holds only counters and short-lived registries that carry no decision.

**GW-14**: Where the gateway and the trusted runtime disagree about whether a delivery was accepted, dispatched, executed or completed, **the runtime's record is authoritative**. The gateway MUST NOT assert that an operation was accepted, and MUST NOT assert that an operation was executed, unless the runtime has said so. A gateway-local record of delivery, consumption or forwarding is evidence of delivery only.

**GW-15**: The runtime's reply is authoritative over the gateway's reply for the same delivery, even when the gateway's reply was already sent. Where the gateway has already told the agent that a delivery was accepted and the runtime later records that it was not, the gateway MUST NOT repeat the acceptance. The agent resolves the conflict by reading authoritative state (XGN-22).

**GW-16**: The gateway's capability binding is advisory. Where a capability names an expected next sequence number, the gateway MAY use it to reject an obviously stale delivery early, but the runtime MUST re-verify the sequence number against its own record (XGN-28), and the runtime's record wins.

## 4. Authority limits

The following are absolute. A gateway that breaks any of them is not a conforming XGN gateway.

| ID | The gateway MUST NOT |
|---|---|
| GW-17 | Decide that an operation is permitted. |
| GW-18 | Widen a grant, a scope, a step or a lifetime. |
| GW-19 | Interpret operation arguments: it MUST NOT canonicalise paths, resolve branch names, decode content into semantic fields, or judge whether a value is inside the grant. |
| GW-20 | Assert that an operation was accepted or executed, unless the runtime has said so. |
| GW-21 | Accept an approval, a decline, a pairing confirmation, a turn clearance, a scope change, a resumption or a revocation. The operator plane is authenticated independently of the agent plane (XGN-00 §4.2), and the gateway MUST NOT carry an agent-plane delivery into the operator plane. |
| GW-22 | Retain or forward an agent-plane delivery to any destination other than the trusted runtime that owns the session. |
| GW-23 | Supply, override or infer `request_id`, `session_id`, `grant_id`, `step_id`, `principal_id`, `issued_at`, `expires_at`, `policy_version`, `draft_digest` or `envelope_digest` (XGN-02 §4.2). |
| GW-24 | Make a decision that the runtime has not made about a session, a draft or a request. |

**GW-25**: The gateway MUST NOT contact an external service. Only the execution path defined in XGN-17 and XGN-18 may do so. A gateway that can reach an external service can be used to probe scope without an operator.

**GW-26**: A delivery the gateway rejects on shape or size grounds MUST NOT be forwarded. A delivery the gateway cannot judge MUST be forwarded for the runtime to judge. Failing to forward a delivery the gateway could not judge is a gateway defect, not a safety measure.

## 5. Ingress, routing and correlation

| ID | Rule |
|---|---|
| GW-27 | The gateway MUST accept only the routes and methods declared by the transport module in use. An unknown route MUST be rejected with `MALFORMED_REQUEST`. |
| GW-28 | The gateway MUST NOT accept a method other than the one declared for the route. |
| GW-29 | The gateway MUST NOT follow a redirect to a different origin. A redirect to a non-Gitana origin MUST be refused and the delivery rejected. |
| GW-30 | The gateway MUST NOT accept a second, concurrent delivery for the same session. A second in-flight delivery MUST be rejected with `RATE_LIMITED`. One in-flight command per session is a hard rule, not a policy. |
| GW-31 | The gateway MUST record the transport, the arrival time and the outcome of every delivery in its telemetry. It MUST NOT record the capability value or any part of the query string. |
| GW-32 | Where a platform provides an edge trace identifier, the gateway MAY attach it to the delivery for correlation. It MUST NOT mint an identifier in the runtime's namespace, and it MUST NOT present a trace identifier to an agent as a runtime identifier. |
| GW-33 | The gateway MUST treat a delivery that arrives after the capability's expiry as expired, and MUST reject it with `LINK_EXPIRED` without forwarding it. |

## 6. No content at rest

**GW-34**: The gateway MUST NOT persist repository content, credentials, operation arguments, request bodies, response bodies, or agent-authored content, in any store, including short-lived caches, buffers written to disk, crash dumps, or tracing payloads.

**GW-35**: The gateway MUST NOT retain a draft chunk. A chunk that carries agent-authored content MUST be forwarded to the trusted runtime in the delivery and MUST NOT be stored, logged, sampled or included in telemetry. This is the relay-side half of the draft lifecycle owned by XGN-07.

**GW-36**: The single-reply idempotency cache MUST NOT hold a content-bearing reply. A reply that carries agent-authored content, repository content, or a credential MUST NOT be cached by the gateway, even inside the idempotency window. Such a delivery MUST be forwarded without a cached reply, and the recovery path for a lost reply on that delivery is defined in XGN-22.

**GW-37**: The idempotency cache MUST be keyed on the delivery's identity — the session, the capability, the command and the digest of the request body — and MUST NOT be keyed on a network fingerprint (XGN-DEC-007).

**GW-38**: A gateway that cannot forward a content-bearing delivery without retaining it MUST refuse that delivery rather than retain it.

**GW-39**: The gateway MUST NOT place a credential, a long-lived provider credential, or a session secret in any log, metric label, trace attribute, URL, or reply (XGN-00 INV-14).

**GW-40**: Where a gateway terminates TLS, holds a platform cache, or writes to an object store for replay delivery, those facilities are in scope of this section. A facility that cannot be configured to hold no body MUST NOT be used on a capability-bearing route.

## 7. Transport protections, shape and size validation

The gateway validates **form**, never **meaning**.

| ID | The gateway MUST validate | The gateway MUST NOT validate |
|---|---|---|
| GW-41 | Route, method, content type, parameter names, parameter count, parameter order, absence of repeated parameters, absence of unknown parameters, presence and integer form of the sequence number, encoded URL length, encoded payload length, per-value decoded length ceiling. | Whether the operation exists, whether the path is inside the granted directory, whether the repository is private, whether the branch is in scope, whether the operation is mutating, whether approval is required, whether the request is permitted, whether the provider would accept it. |

**GW-42**: The gateway MUST reject a delivery that violates form with `MALFORMED_REQUEST`, `VALIDATION_FAILED` or `DRAFT_LIMIT`, as defined in XGN-23, and MUST NOT forward it.

**GW-43**: The gateway MUST NOT partially forward a delivery, and MUST NOT forward a delivery whose form it could not fully parse.

**GW-44**: The gateway MUST perform decoding exactly once, at the validation boundary, and MUST NOT decode a value twice (XGN-01 §6.1).

**GW-45**: The gateway MUST NOT reject a delivery because a value fails a semantic rule. A semantic failure is the runtime's decision, and the runtime reports it with a code from XGN-23.

## 8. Rate enforcement and concurrency

**GW-46**: The gateway MUST enforce the limits below before forwarding. The authoritative values are defined in XGN-23 §7; this table names the enforcement point for each and records the limits this module owns.

| Limit | Value | Enforcement point | Authority |
|---|---|---|---|
| Commands per session | 512 | Gateway counter, re-checked by the runtime | XGN-23, `XGN-DEC-012` |
| Status polls | 1 per 5 seconds; 60 per session | Gateway | XGN-23, `XGN-DEC-023` |
| Consecutive invalid attempts | 10, then the session is suspended | Runtime; the gateway MUST NOT suspend | XGN-23 |
| Encoded URL length | 900 characters | Gateway | XGN-28 |
| Encoded data per command | 2048 bytes | Gateway | XGN-28 |
| In-flight deliveries per session | 1 | Gateway | XGN-20 (this module) |
| Deliveries per client address | 120 per minute, 600 per hour | Gateway | XGN-20 (this module) |

**GW-47**: A client-address counter is a **risk signal only**. It MUST NOT be used to authenticate, to identify a caller, or to decide whether a delivery is legitimate. Agents commonly share fetch infrastructure, so address-based limits produce false positives; the limit therefore applies a coarse ceiling and never substitutes for the capability.

**GW-48**: A rate rejection MUST return `RATE_LIMITED` with the retry hint defined in XGN-23, and MUST NOT consume a command capability, MUST NOT advance a session sequence number, and MUST NOT change any session state.

**GW-49**: The gateway MUST count a rate-rejected delivery against the client-address counter and MUST NOT count it against the session's command budget, because the session's budget counts commands the runtime saw.

## 9. Availability behaviour

**GW-50**: When the trusted runtime is unreachable, the gateway MUST fail fast. It MUST return `DEVICE_OFFLINE` and MUST NOT wait, retry internally, or hold the delivery for later delivery.

| ID | Rule |
|---|---|
| GW-51 | The gateway MUST NOT queue a mutating delivery. A queued mutating delivery would be dispatched at a time the operator did not see, against a state the operator no longer approved. |
| GW-52 | The gateway MUST NOT queue any delivery that has already been presented as accepted. |
| GW-53 | The gateway MUST NOT retry a delivery that may have had a side effect. A retry is a side effect decision, and side effects belong to the runtime (XGN-17, XGN-22). |
| GW-54 | A read-only delivery MAY be retried by the gateway at most once, after 2 seconds, and only if the first attempt failed before any byte was forwarded. The gateway MUST NOT retry a delivery whose forwarding status is unknown. |
| GW-55 | On `DEVICE_OFFLINE` the gateway MUST NOT assert that the delivery was accepted, and MUST NOT return a panel. It MUST return the Error Contract of XGN-02 §10 with code `DEVICE_OFFLINE`. |
| GW-56 | The session, the grant and any open draft are unaffected by an unreachable runtime. They live on the runtime (XGN-04, XGN-07). |
| GW-57**: The agent's recovery after `DEVICE_OFFLINE` is to wait, then re-read authoritative state with `status`, and to re-send the original delivery byte-identically only if it is still within the idempotency window and the agent holds no newer capability. The full procedure is in XGN-22 and XGN-23. |

## 10. Relay logging and data retention

**GW-58**: The gateway MUST NOT log the query string of a capability-bearing route. Where the platform permits, URL and query-string logging MUST be disabled for that route.

**GW-59**: The gateway SHOULD log the following, and MUST NOT log the following.

| May be logged | Must not be logged |
|---|---|
| Route identifier, method, transport name | Capability value, capability prefix, session identifier in full |
| Delivery outcome and error code | Query string, path parameters, request body, response body |
| Byte counts | Operation arguments, paths, branch names, repository names |
| Arrival and completion timestamps | Agent-authored content, repository content, credentials |
| The gateway's own counters | The pairing challenge, the operator's identity beyond a principal identifier |

**GW-60**: Where the platform does not permit query-string logging to be disabled, the gateway MUST compensate by shortening the capability lifetime, MUST NOT extend the capability beyond the lifetime in `XGN-DEC-008`, and MUST record the residual risk in the operator-facing text required by section 12.

**GW-61**: Retention. Gateway telemetry MUST be retained for 7 days. The capability registry MUST NOT outlive the capability's 600-second lifetime. The idempotency cache MUST NOT outlive the 90-second idempotency window. The gateway MUST NOT retain any body for any period.

**GW-62**: A gateway-side request log MUST NOT be presented as an audit record. The audit log is owned by XGN-25 and records the runtime's decisions.

## 11. Deployment topology

**GW-63**: The gateway MAY be co-located with the trusted runtime, edge-hosted on a separate provider, or run in-process with a transport adapter. The deployment choice is an operational decision owned by XGN-27.

| Topology | What it changes | What it does not change |
|---|---|---|
| Co-located with the runtime | No network hop. The relay protections still apply to the transport surface. | The authority split. The runtime remains the only component that decides. |
| Edge-hosted | Adds a network hop and an operator in the path. The trusted-relay assumption applies (section 12). | The authority split, and the prohibition on holding state. |
| In-process with a transport adapter | The gateway and the adapter share a process. The protections MUST still be applied, because the transport surface is still agent-reachable. | The authority split, and the separation between transport framing and request governance. |

**GW-64**: Whatever the topology, the invariant is unchanged: **the component that receives agent traffic holds no authoritative state and makes no authorisation decision**. A deployment that collapses the gateway and the runtime into one process MUST still keep the two roles separable in code, so that the prohibitions in section 4 are testable.

**GW-65**: The operator plane MUST be authenticated independently of the agent plane, and MUST NOT be reachable through a gateway route that an agent-plane capability can address.

## 12. Required verification

Before a gateway is deployed, the following MUST be verified and recorded.

| ID | Check | Method |
|---|---|---|
| GW-66 | URL and query-string logging is disabled for the capability-bearing route. | Inspect the effective edge and platform configuration. Where the platform does not allow it, the residual risk MUST be recorded. |
| GW-67 | The gateway's request logs, traces and crash dumps contain no query string, body or content. | Submit a session with agent-authored content and inspect the gateway's stored artefacts. |
| GW-68 | The operator plane is authenticated independently. | Attempt an operator-plane action using an agent-plane capability. It MUST fail. |
| GW-69 | The trusted-relay assumption is documented in operator-facing text. | The operator-facing documentation states that the relay is trusted to deliver the payload unchanged, is not trusted for authority, and that it may observe content in transit. |
| GW-70 | The runtime's record wins on disagreement. | The gateway is made to believe a delivery was accepted when the runtime did not record it. The session MUST follow the runtime's record, and the probe defined in XGN-30 as `PRB-26` MUST pass. |
| GW-71 | The gateway holds no content. | Inspect the gateway's stores, caches, buffers and telemetry after a content-bearing session. |
| GW-72 | One in-flight delivery per session. | Two concurrent deliveries for one session: exactly one MUST be forwarded. |
| GW-73 | The runtime-unreachable path fails fast. | Make the runtime unreachable and measure the time to the `DEVICE_OFFLINE` reply. The gateway MUST NOT hold the delivery. |

**GW-74**: The deployment MUST fail the verification step if any check fails. A gateway that has not passed these checks MUST NOT be placed on an agent-reachable route.

## 13. Decisions recorded

| ID | Decision | Source |
|---|---|---|
| `XGN-DEC-027` | The gateway MUST NOT cache a reply that carries agent-authored content. The single-reply idempotency cache MUST NOT hold a content-bearing reply. | `XGN-DEC-027`, owned by XGN-20 |
| `XGN-DEC-007` | Idempotency window is 90 seconds; the caller identity is the session capability plus the exact link and body digest. A network fingerprint is a risk signal only, never authentication. | `XGN-DEC-007`, owned by XGN-29 |

**Open decisions.**

| ID | Question | Effect if unresolved |
|---|---|---|
| GW-OD-01 | Whether the gateway may terminate TLS itself, or must sit behind a terminating proxy, in each supported deployment. | Deployment checklist GW-66 must state the case it covers. |
| GW-OD-02 | The maximum wall-clock time the gateway waits for the runtime before returning `DEVICE_OFFLINE`. The working value is 5 seconds; the value is not yet fixed across deployments. | Affects the agent's backoff only. Not blocking. |

## 14. Related specifications

- **Depends on:** XGN-01 (identifiers and terminology), XGN-02 (shared contracts and the Error Contract), XGN-03 (discovery and the panel surface the gateway forwards).
- **Referenced by:** XGN-09, XGN-10, XGN-11, XGN-12, XGN-13, XGN-14, XGN-21, XGN-22, XGN-23, XGN-25, XGN-27, XGN-29.
- **Predecessor material:** the relay role, the logging rules and the deployment checklist of the *Navigation Transport Specification* (0.1-draft, 25 November 2026). Those items are restated here as XGN-native requirements owned by this module.
