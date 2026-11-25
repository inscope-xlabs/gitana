# Remote Repository Tool Bridge: Cloudflare Relay Specification

**Status:** Review draft | **Version:** 0.1-draft | **Date:** 24 November 2026
**Companion to:** [Remote Repository Tool Bridge v2](remote_repository_tool_bridge_v2_specification.md)
**Normative language:** MUST / MUST NOT / SHOULD / SHOULD NOT / MAY are requirements keywords.

## 1. Purpose and scope

This document specifies the optional relay that lets a remote agent reach the Android component (the "phone") without the phone opening an inbound port. It implements the **Relay Adapter** and **Relay Transport Contract** defined in v2 §4.1, §4.3 and §5.2, using a Cloudflare Worker and a Durable Object.

In scope: agent and device authentication at the relay, session routing, message envelope, replay and expiry handling, limits, logging, deployment, and operations.

Out of scope: the tool surface, authorization policy, GitHub execution and audit (owned by the phone per v2), the localhost interface, and end-to-end encryption (see §9; deferred to the E2E profile).

The relay is a transport intermediary. It MUST NOT make authorization decisions that substitute for the phone's Policy Engine, and the phone MUST NOT rely on the relay for repository, branch, path or operation policy (v2 §6.2).

## 2. Design principles

| Principle | Requirement |
| --- | --- |
| Dumb pipe | The relay authenticates, routes, rate-limits and correlates. It does not interpret tool arguments or hold GitHub data. |
| No content at rest | The relay MUST NOT persist repository content, GitHub credentials, tool arguments, or request/response bodies (v2 §5.2). |
| No GitHub secrets | No GitHub PAT or local-client credential ever exists in Worker code, secrets, logs or storage. |
| Fail closed | Unknown versions, unauthenticated peers, malformed frames, expired or replayed messages are rejected. |
| Independent of local path | Relay outages or misconfiguration MUST NOT affect localhost operation (v2 §5.2). |
| Trusted-relay by default | Until the E2E profile is reviewed, the relay is trusted to see and potentially alter the frames it terminates (v2 §6.4). This document MUST NOT claim otherwise. |

## 3. Architecture

```
Remote agent --HTTPS/WSS--> [ Worker (edge) ] --routes to--> [ Durable Object: one per device ] <--WSS-- Phone (outbound)
```

- **Worker**: public entrypoint. Terminates TLS, authenticates the agent, applies coarse rate limits, resolves the target device, and forwards to that device's Durable Object. Also handles device WebSocket upgrades.
- **Durable Object (`DeviceSession`)**: one instance per device ID. Holds the device's WebSocket, validates envelope structure, enforces ordering/replay/expiry, correlates requests with responses, and exposes presence state. It is the single serialization point for a device.
- **Phone**: initiates the outbound WSS connection and sends heartbeat; executes requests through the common application core.

The relay MUST use the Durable Object WebSocket Hibernation API (or an equivalent that avoids billing and holding memory for idle sockets) and MUST be designed so that DO eviction or restart is a normal event (see §8).

## 4. Trust domains and credentials

Aligned with v2 §6.1 and §5.3. All credentials are independent.

| Credential | Held by | Verified by | Notes |
| --- | --- | --- | --- |
| Relay-device credential | Phone (Keystore-encrypted) | Relay on WebSocket upgrade | Per device. Never accepted for agent routes. |
| Remote-agent credential | Agent deployment | Relay Worker | Per agent. Never accepted for device routes. Never forwarded to the phone. |
| Principal assertion | Issued by relay | Phone | Derived from a verified agent credential; contains no secret. |
| Operator/admin credential | Relay operator | Relay admin routes | Separate from all of the above. Used only for provisioning and revocation. |

Requirements:
- The relay MUST NOT accept a local-client credential or GitHub PAT in any role and MUST NOT be configured with either.
- Agent credentials MUST be revocable per agent without affecting others; device credentials MUST be revocable per device without affecting others.
- Credentials MUST NOT appear in URLs or query strings. They are sent in an `Authorization` header or, for WebSocket upgrade where headers are unavailable to the client, in an agreed subprotocol/first-message mechanism specified in the transport contract (open decision D3).
- Secrets live in Worker secrets/bindings, never in source or logs. Stored device/agent credentials SHOULD be stored as salted hashes or public keys, not plaintext.

## 5. Endpoints

All routes are versioned under `/gitana/v1/`. Only the routes below are allowed; there is no generic proxying.

| Method | Route | Caller | Purpose |
| --- | --- | --- | --- |
| GET (Upgrade) | `/gitana/v1/device/connect` | Phone | Establish the device WebSocket. Auth: relay-device credential. |
| POST | `/gitana/v1/agent/devices/{device_id}/requests` | Agent | Submit a canonical tool request envelope. Returns `202` with a relay correlation ID, or a synchronous result if the contract selects sync mode (D4). |
| GET | `/gitana/v1/agent/devices/{device_id}/requests/{request_id}` | Agent | Poll status/result of a request. |
| POST | `/gitana/v1/agent/devices/{device_id}/requests/{request_id}/cancel` | Agent | Request cancellation where supported. |
| GET | `/gitana/v1/agent/devices/{device_id}/status` | Agent | Device presence (`online`/`offline`) and contract version. No secrets. |
| GET | `/gitana/v1/health` | Anyone | Non-sensitive liveness. |
| Admin | `/gitana/v1/admin/...` | Operator | Register/revoke devices and agents, set per-agent device scopes. Separate auth. |

An agent MUST only reach devices it has been explicitly granted. Device IDs MUST be unguessable identifiers and MUST NOT be treated as authorization.

## 6. Message model

### 6.1 Principle

The relay envelope wraps the canonical Tool Contract request from v2 §8.2 without altering its meaning. The relay adds transport fields only. The phone validates the inner command independently.

### 6.2 Relay envelope (illustrative; schema to be published)

```json
{
  "relay_version": "1.0",
  "type": "request",
  "device_id": "dev_<opaque>",
  "session_id": "<server-generated>",
  "seq": 42,
  "correlation_id": "<relay-generated>",
  "issued_at": "2026-11-24T20:00:00Z",
  "expires_at": "2026-11-24T20:01:00Z",
  "principal_assertion": {
    "agent_id": "agent_<opaque>",
    "scopes": ["read", "write"],
    "asserted_by": "relay",
    "asserted_at": "2026-11-24T20:00:00Z"
  },
  "payload": { "contract_version": "1.0", "request_id": "urn:uuid:...", "operation": "read_file", "arguments": { } }
}
```

Message types: `request`, `response`, `ack`, `status`, `cancel`, `ping`/`pong` (if not using protocol-level ping), `error`, `session_hello`.

### 6.3 Rules

- **Principal assertion.** Built by the relay from the verified agent identity. It MUST NOT include the agent credential. The phone maps the asserted agent to a canonical principal and applies its own per-agent policy (v2 §6.2, §6.3). The assertion's scopes are advisory to the phone, never a grant.
- **Request ID uniqueness.** The agent-supplied `request_id` MUST be globally unique per agent. The relay rejects a `request_id` it has already accepted within the replay window and returns the existing status instead (§7).
- **Expiry.** Every message carries `expires_at`. The relay MUST NOT deliver an expired request and MUST NOT forward a late response as a fresh success. Maximum lifetime is bounded (default proposal: 5 minutes; D6). The phone also enforces expiry independently (v2 §8.4).
- **Ordering and sequence.** The Durable Object assigns a monotonically increasing `seq` per device session. The phone rejects out-of-window or duplicate sequence values.
- **Acknowledgements.** The phone sends an `ack` when it has accepted a request into its lifecycle (RECEIVED), and a `response` when it reaches a terminal or outcome-unknown state. The relay maps these to agent-visible states and MUST preserve `OUTCOME_UNKNOWN` rather than converting it to failure or success (v2 §8.3, §8.4).
- **Delivery semantics.** The relay provides at-most-once delivery to the phone per `seq`. It MUST NOT automatically re-deliver a request that may have reached the phone (v2 §8.1, §8.4). Only requests never confirmed as sent over a live socket MAY be retried within their validity window.
- **Unknown fields/versions.** Unknown major versions fail closed. Unknown minor fields are handled per the schema compatibility rules.

## 7. Request lifecycle at the relay

| Relay state | Meaning |
| --- | --- |
| `ACCEPTED` | Agent request authenticated, valid, and queued for the device. |
| `SENT` | Written to the device socket. After this point it MUST NOT be blindly re-sent. |
| `ACKED` | Phone confirmed receipt. |
| `COMPLETED` | Phone returned a terminal response (success or definitive failure), passed through unmodified. |
| `OUTCOME_UNKNOWN` | Sent but no confirmed result (disconnect, timeout). Reported as such; agent must reconcile via read-back, not replay. |
| `DEVICE_OFFLINE` | Device not connected; request not sent. |
| `EXPIRED` / `REJECTED` / `CANCELLED` | Not executed, or not delivered. |

- If the device is offline at submission, the relay SHOULD fail fast with `DEVICE_OFFLINE` (retryable) rather than queue. Any queueing (D7) MUST be bounded by count, bytes and age, MUST hold only requests that carry no repository file content unless separately approved, and MUST expire before delivery.
- The relay keeps request lifecycle metadata (IDs, state, timestamps, agent ID, operation name) for a bounded retention window to support polling and duplicate detection. It MUST NOT retain `arguments` content or response bodies beyond the in-flight delivery needed to return the result (see §10 for how results are handed back).
- Returning a result to the agent requires holding the response briefly. The retention window for an undelivered result MUST be short (default proposal: 60 seconds after completion; D8) and the bytes MUST be dropped on delivery, expiry or revocation.

## 8. Durable Object behavior and failure handling

- One DO per device. All state mutation for that device is serialized in the DO.
- Do not rely on in-memory state surviving. Hibernation or eviction can drop memory; per-socket metadata that must survive hibernation (device ID, session ID, last seq, connect time) is kept in WebSocket attachments (size-limited) and/or minimal DO storage containing metadata only.
- On DO restart with in-flight requests, the relay MUST mark them `OUTCOME_UNKNOWN` if they were `SENT`, and `EXPIRED`/`REJECTED` otherwise. It MUST NOT re-send them.
- **One live device socket per device.** A new authenticated connection replaces the old one; the old socket is closed with a defined code. Reconnect MUST NOT replay previously sent requests.
- **Heartbeat.** Protocol-level or application ping/pong at a configurable interval (default proposal: 30 s ping, 90 s dead-peer timeout; D9). Half-open sockets are closed and the device marked offline (v2 §9.2). Prefer auto-response ping handling so idle hibernated objects are not woken for heartbeats.
- **Backpressure.** Bounded per-device and per-agent in-flight requests (default proposal: small concurrency, e.g. 4 per device); excess returns `429`/`RATE_LIMITED` with `Retry-After`.
- **Durable Object limits.** Message size, storage size, request rate, and WebSocket count limits are platform-defined and MUST be verified against current Cloudflare documentation during implementation; this document does not assert specific platform numbers.

## 9. Security

### 9.1 Trust profile

Default is the **trusted relay profile** (v2 §6.4): the relay can read and alter frames. Therefore:
- Restrict who can administer and deploy the Worker; require MFA and least-privilege Cloudflare API tokens.
- Minimize logging (§10) and disable any platform log or analytics sinks that capture payloads.
- Treat the relay account as part of the trusted computing base for repository content in transit.

### 9.2 Phone-side authentication of relay messages (hardening, D10)

v2 requires phone-side authentication of requests and responses under the trusted-relay profile. Proposed minimum: a per-session MAC over each envelope using a session key derived from the device credential exchange, so a network attacker or confused deputy cannot inject frames, while acknowledging the relay itself is still trusted. This MUST NOT be described as end-to-end protection. A true E2E profile (key agreement between agent and phone, opaque payloads through the relay) requires its own design and independent cryptographic review before production.

### 9.3 Other requirements

- TLS only. The relay MUST NOT accept plaintext HTTP/WS for any route.
- Reject browser-originated cross-origin use: no permissive CORS; strict `Origin` handling for WebSocket upgrades where a browser origin would be unexpected.
- Authenticate before upgrading the WebSocket; unauthenticated upgrades are rejected.
- Per-agent and per-device rate limits, plus coarse abuse limits at the Worker. Authentication failures are rate-limited and not distinguishable between "unknown device" and "not permitted".
- Replay defense: bounded cache of recently seen `(agent_id, request_id)` pairs and per-session `seq` checks. Cache entries hold identifiers only.
- Input validation: reject oversized bodies before buffering; enforce frame size limit; validate JSON against the schema; reject unknown routes and methods.
- Prompt-injection posture: the relay treats `payload` as opaque data and never executes or interprets it.
- Revocation takes effect on the next request and closes any live socket for a revoked device. Lost-device response MUST include disabling the relay-device identity (v2 §12).

## 10. Logging, privacy and data handling

Allowed in logs/metrics: timestamps, device ID, agent ID, request ID, correlation ID, operation name, relay state, status/error code, sizes, durations, rate-limit events.

Forbidden anywhere: credentials or Authorization headers, principal secrets, `arguments`, file contents, commit messages, response bodies, repository paths if the deployment chooses to treat them as sensitive (D11).

Retention for relay metadata is configurable, documented, and bounded. Operators MUST be able to purge metadata for a device or agent. Any analytics or tracing integration must be reviewed for payload capture before enablement.

## 11. Provisioning and operations

1. Create the Worker and Durable Object binding in a dedicated Cloudflare account/project with restricted access.
2. Configure secrets (admin credential, signing/derivation keys) via the platform secret mechanism.
3. Register a device: generate a device ID and relay-device credential, deliver the credential to the phone through a secure pairing flow (never by chat, URL or command line), and store only a verifier on the relay.
4. Register an agent: issue an agent credential, record its allowed devices and scopes.
5. Verify with a connectivity test and a negative test (wrong device/agent credential).
6. Rotation: device and agent credentials rotate independently; rotation does not require touching the local-client credential or PAT.
7. Monitoring: alerting on auth-failure spikes, DO errors, reconnect storms, and rate-limit hits, using metadata-only telemetry.
8. Deployment: infrastructure as code, staged environments (dev/staging/prod), versioned rollout, and a documented rollback. Pin the Workers compatibility date and review it during upgrades.

## 12. Error model

The relay uses the v2 §11.1 error contract, with these relay-specific codes (stable names to be finalized):

| Code | Category | Retryable | Meaning |
| --- | --- | --- | --- |
| `RELAY_AUTH_FAILED` | authentication | no | Agent or device credential invalid. |
| `RELAY_FORBIDDEN` | authorization | no | Agent not granted to this device. |
| `DEVICE_OFFLINE` | transport | yes | Device socket not connected. |
| `REQUEST_EXPIRED` | validation | no | Past `expires_at`. |
| `REQUEST_REPLAYED` | validation | no | Duplicate `request_id`; existing status returned. |
| `RATE_LIMITED` | resource-limit | yes | Limits exceeded; `Retry-After` supplied. |
| `PAYLOAD_TOO_LARGE` | resource-limit | no | Body or frame over limit. |
| `OUTCOME_UNKNOWN` | transport | no (reconcile) | Sent, result not confirmed. |
| `RELAY_INTERNAL` | internal | yes | Relay fault before dispatch. |

## 13. Testing and acceptance criteria

| Area | Acceptance criteria |
| --- | --- |
| Authentication | Wrong, expired, revoked and cross-role credentials rejected; agent credential cannot open a device socket and vice versa. |
| Routing isolation | An agent cannot reach a device it was not granted; device IDs are not sufficient for access. |
| Credential separation | No route accepts a PAT or local-client credential; none is present in relay config or logs. |
| Local independence | Disabling or breaking the relay does not change localhost behavior. |
| Replay | Duplicate `request_id`, stale `seq` and expired messages are rejected without side effects. |
| No blind replay | Disconnect after `SENT` yields `OUTCOME_UNKNOWN`; reconnect does not resend. |
| DO eviction | Eviction/restart with in-flight requests yields correct states and no content persisted. |
| Limits | Oversized bodies/frames rejected before buffering; rate limits enforced with `Retry-After`. |
| Privacy | Log and storage inspection shows no credentials, arguments or content. |
| Heartbeat | Half-open sockets detected and device marked offline within the configured timeout. |
| Revocation | Revoked device/agent loses access immediately and live sockets are closed. |
| Load/soak | Reconnect storms and many idle sockets behave within platform limits. |

## 14. Open decisions

| ID | Decision | Recommended default |
| --- | --- | --- |
| D1 | Cloudflare account topology and operator access model | Dedicated account/project, MFA, least-privilege tokens |
| D2 | Agent authentication mechanism | Per-agent long-random bearer token or signed JWT; stored as verifier |
| D3 | How WebSocket clients present credentials without query strings | Header if client supports it; otherwise authenticated first message before any command is accepted |
| D4 | Sync vs. accepted-then-poll for agent requests | Accepted-then-poll (consistent with v2 Appendix B for approval-gated ops) |
| D5 | Device ID format and registry location | Opaque random IDs; registry in DO/KV metadata only |
| D6 | Maximum request lifetime | 5 minutes, configurable |
| D7 | Offline queueing | None by default; fail fast with `DEVICE_OFFLINE` |
| D8 | Result hand-back retention | 60 seconds, dropped on delivery |
| D9 | Heartbeat and timeout values | 30 s ping / 90 s timeout, configurable |
| D10 | Per-message MAC under trusted-relay profile | Adopt; reviewed before production |
| D11 | Whether repository paths count as sensitive for logs | Treat as sensitive; do not log |
| D12 | E2E profile timeline | Deferred until independent cryptographic review |

## 15. Tasks required to leave draft status

- [ ] Resolve decisions D1-D12 and record them in a decision log.
- [ ] Publish machine-readable schemas (relay envelope, error model, admin API) and an OpenAPI/AsyncAPI description.
- [ ] Specify the credential presentation, pairing flow and rotation procedure in detail.
- [ ] Verify all Cloudflare Workers / Durable Objects / Hibernation API behaviors and limits against current official documentation and record the versions reviewed. Statements about platform behavior in this draft are design intent, not verified facts.
- [ ] Define the session-key derivation and per-message MAC (D10) and have it reviewed.
- [ ] Write the threat model (relay compromise, malicious agent, stolen device credential, DO eviction races, replay).
- [ ] Prototype and test hibernation, eviction and reconnect behavior; produce results for §13.
- [ ] Align state names and error codes with the v2 Execution Lifecycle and Error contracts, and update v2 §5.2 / Appendix B accordingly.
- [ ] Define observability, alerting and incident-response runbooks.
- [ ] Complete technical and security review, add a changelog, and update the status header.
