# Remote Repository Tool Bridge v2: Contract-First Android GitHub Executor

**Boundary:** The only agent ingress to the Android component that executes GitHub operations. The component owns credentials, authorization policy and execution. An agent proposes; the phone decides.
**Protocol version:** RTB/2
**Spec version:** 2.0-draft
**Status:** Review draft. Not an implementation sign-off.
**Reviews:** Technical review and security review pending, tracked in the [companion exit-tasks document](remote_repository_tool_bridge_v2_draft_exit_tasks.md).
**Companions:** [Cloudflare relay](cloudflare_relay_specification.md) (the optional relay transport), [Navigation transport](navigation_transport_specification.md) (the optional navigation transport), [Draft exit tasks](remote_repository_tool_bridge_v2_draft_exit_tasks.md) (tasks required to leave draft status)
**Authority:** This guide is authoritative for the Remote Repository Tool Bridge v2 contract. Where the Version 1 framework overview disagrees, this guide wins.
**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119. No default value is stated in this guide; each is a `DV-RTB-...` ID defined only in Appendix D.

---

## 1. Purpose and scope

### 1.1 Purpose

The Android device acts as a narrowly scoped GitHub API executor. It does not clone repositories, run Git, maintain a working tree, or expose a shell. It reads and changes remote repository state through explicitly supported GitHub REST operations.

The component is self-contained by contract. Its core does not depend directly on Cloudflare, an AI agent, a shell runtime, or a particular agent protocol. External communication is implemented by adapters that consume and produce versioned shared contracts. The same application service and authorization policy are used regardless of transport.

The component provides a loopback-bound local interface for agents running locally via shell. A separate optional relay adapter provides remote access. Local-agent authentication, relay-device authentication, and remote-agent authentication are distinct trust domains with distinct credentials and lifecycle controls.

**Self-contained** means independently deployable and governed by its own published contracts, configuration, authorization policy, and lifecycle. It does not mean that all transport or protocol code must be in one class, or that external dependencies are eliminated.

**Design decision:** the component MUST bind a local HTTP interface to loopback for same-device shell agents (RTB-03, RTB-16). This local interface is independent of the Cloudflare relay and MUST NOT reuse relay credentials or agent-side relay credentials (RTB-04).

### 1.2 Scope

**In scope:** the local loopback interface and its contract; the optional relay adapter and its contract; the canonical tool surface and its GitHub mapping; phone-side authorization policy; request lifecycle, idempotency and audit; credential storage, separation and lifecycle; the Android runtime behavior required to host the component.

**Out of scope:** Git execution, cloning, checkout and working trees; arbitrary GitHub API passthrough and generic HTTP proxying; the content of the optional relay and navigation transports, which are specified separately; any guarantee about what an external agent does with content it receives.

### 1.3 Goals

- Expose a small, explicit, versioned tool surface for reading branches and files, and for creating branches, commits, deletions and pull requests.
- Support same-device shell agents through a loopback-only interface.
- Optionally support remote agents through a relay adapter, without coupling the core to Cloudflare.
- Keep GitHub PATs and repository authorization inside the Android component.
- Make every external boundary contract-driven and independently testable.
- Prevent unauthorized repository, branch, path and operation access, even when an agent is compromised or prompt-injected.
- Provide deterministic request tracking, duplicate suppression, explicit retry semantics, auditability and bounded resource use.
- Keep no local Git working tree and no persistent repository-content cache.

### 1.4 Non-goals

- Running Git, cloning repositories, checking out branches, executing repository scripts, or providing shell access.
- Arbitrary HTTP proxying or arbitrary GitHub API passthrough.
- Guaranteeing uninterrupted Android execution or permanent WebSocket connectivity.
- Guaranteeing that an external agent, its provider, or its orchestration platform does not retain returned repository content.
- Treating the relay as trusted or untrusted by implication; its trust level is an explicit deployment choice (§6.4).
- Supporting every GitHub API feature in the initial release.

## 2. Terms

| Term | Meaning |
|---|---|
| **Allow-list** | The set of canonical `owner/repository` identifiers the component may access, configured at provisioning time (§7.2). |
| **Approved repository record** | The stored canonical record of an allow-listed repository, from which `owner` and `repo` are resolved. No operation accepts an arbitrary URL fragment (§7). |
| **Canonical** | The single normalized form of a repository identifier, branch name or path, produced before any comparison (§7.2). |
| **Component** | The Android application: one independently deployable unit with internal modules (§4.1). |
| **Contract** | A published, versioned, transport-neutral definition of an external boundary (§4.2). |
| **Contract version** | The explicit major version of a contract, carried as a path segment or a schema version field. A breaking change requires a new major version (§4.3). |
| **Credential class** | One of the four independent credential types: GitHub PAT, local-client, relay-device, remote-agent (§6.1). |
| **Derived session key** | Short-lived key protecting message integrity and confidentiality across the relay. Used only in the end-to-end protected profile (§6.1, §6.4). |
| **Dispatch** | The point at which an approved command is sent to GitHub. After dispatch, an unacknowledged request is `OUTCOME_UNKNOWN` (§8.3, §8.4). |
| **End-to-end protected profile** | The deployment profile in which the relay forwards opaque, authenticated envelopes and cannot forge valid messages or read protected payloads (§6.4). |
| **Operation fingerprint** | A hash of the operation and its canonicalized arguments, used with `request_id` for duplicate detection (§8.4). |
| **Outcome unknown** | The state of a request that may have reached GitHub but whose result could not be confirmed (§8.3). |
| **Principal** | The canonical identity of the caller, derived from the transport credential and mapped to a trust domain (§6.2). |
| **Reconciliation** | Safe read-back of target branch, file or commit state to determine the outcome of an uncertain write (§8.4). |
| **Relay** | The optional Cloudflare Worker and Durable Object transport intermediary (§5.2). |
| **Relay adapter** | The outbound WebSocket adapter inside the component that maps relay envelopes to the canonical command model (§4.1). |
| **Self-contained** | Independently deployable and governed by its own published contracts, configuration, authorization policy and lifecycle (§1.1). |
| **Trust domain** | A credential and authorization boundary: local client, relay device, remote agent, or the component itself (§6.1). |
| **Trusted-relay profile** | The deployment profile in which the relay may see and alter the frames it terminates (§6.4). |
| **Working tree** | A local checkout of a repository. The component never maintains one (§3.1). |

## 3. Architecture rules

Every normative statement in this guide carries an identifier. The tables below are the index; the detail sections state the rule in context and cite the identifier. Conformance tests in §17 reference these identifiers.

### 3.1 Architectural principles

| ID | Rule | Detail |
|---|---|---|
| RTB-01 | Every external interface MUST be defined by a versioned, transport-neutral contract. Adapters MUST translate transport messages to internal application commands; core logic MUST NOT import relay or agent SDK types. | §4.2 |
| RTB-02 | The component owns configuration, credentials, authorization policy, request lifecycle, GitHub client, audit policy, and its published local and remote contract implementations. | §4.1 |
| RTB-03 | The local HTTP server MUST bind exclusively to the IPv4 loopback address `127.0.0.1`. It MUST also bind to the IPv6 loopback address `::1` whenever the Android runtime and network stack support IPv6 loopback binding. If IPv6 loopback binding is unavailable or fails, the server MUST continue operating on `127.0.0.1` and MUST record the IPv6 binding failure in its diagnostic log. The server MUST NOT bind to wildcard addresses (`0.0.0.0` or `::`), LAN interfaces, or any other non-loopback address. Failure to establish the IPv4 loopback listener MUST prevent the local HTTP server from starting. | §5.1 |
| RTB-04 | Local-client credentials, relay-device credentials, remote-agent credentials and GitHub PATs MUST be independent credentials. No credential may be reused across trust domains. | §6.1 |
| RTB-05 | The phone MUST enforce repository, operation, branch and path policy before contacting GitHub. | §6.2 |
| RTB-06 | The component MUST use GitHub APIs directly and MUST NOT clone, checkout, or maintain a Git working tree. | §7.1 |
| RTB-07 | The Android bridge MUST NOT persist bridge request data, tool input, response data, or tool output to durable storage. The sole exception is the explicitly user-activated SAVE-SESSION-CONTENT feature. This feature MUST be implemented exclusively within the Android bridge tool and MUST NOT be available to, implemented by, or independently activated by the relay component. When SAVE-SESSION-CONTENT is disabled, the bridge MUST NOT persist any session content. When enabled, the bridge MAY persist only the session content explicitly covered by that feature's specification and user authorization. The relay MUST NOT persist bridge data or responses under any circumstances. No other feature, configuration, or implicit behavior may override this requirement. | §10 |
| RTB-08 | Unknown contract versions, unknown operations, missing authentication, invalid authorization, malformed payloads and policy uncertainty MUST fail closed. | §8.3 |
| RTB-09 | Audit events MUST record action metadata and outcomes without recording credentials, content, or complete request and response bodies. | §11 |

### 3.2 Contract rules

| ID | Rule | Detail |
|---|---|---|
| RTB-10 | All communication crossing a component boundary MUST use a published contract. | §4.2 |
| RTB-11 | The Android core MUST NOT contain Cloudflare-specific concepts, agent-vendor-specific tool schemas, or shell-agent-specific code. | §4.2 |
| RTB-12 | Transport adapters MAY use different wire encodings, but they MUST map to the same canonical command and result model. | §4.2 |
| RTB-13 | Contract versions MUST be explicit, as a path segment (`/v1`) or a schema version field. A breaking change requires a new major contract version. | §4.3 |
| RTB-14 | Minor contract additions MUST be backward compatible, and unknown fields MUST be handled according to the schema's compatibility rules. | §4.3 |
| RTB-15 | No operation may accept an arbitrary endpoint, host, HTTP method or raw GitHub API path. The phone MUST resolve repository owner and name from an approved-repository record. | §7 |

### 3.3 Transport rules

| ID | Rule | Detail |
|---|---|---|
| RTB-16 | The component MUST expose a local HTTP interface for same-device agents that communicate through shell or local tool adapters. | §5.1 |
| RTB-17 | The server MUST bind to loopback only. The local interface MUST NOT be exposed through the relay, and the relay adapter MUST NOT be required for the local interface to function. | §5.1, §5.2 |
| RTB-18 | Health and readiness endpoints MUST reveal no credentials, repository contents or sensitive configuration. | §5.1 |
| RTB-19 | Only explicitly documented routes and methods are allowed. There MUST be no generic proxy and no arbitrary path forwarding. | §5.1, App. B |
| RTB-20 | The local listener MUST enforce strict request-size limits, read and write timeouts, connection limits and rate limits. | §5.1, §10 |
| RTB-21 | The local listener MUST reject `Host` headers and origin patterns not permitted by the Local Transport Contract. It MUST NOT enable permissive CORS. | §5.1 |
| RTB-22 | Authentication MUST be required on the loopback interface. Loopback is a network boundary, not proof of user identity. | §5.1 |
| RTB-23 | The local-client credential MUST be provisioned and revocable independently of every other credential class, and MUST NOT be placed in URLs, query strings, command-line arguments, shell history or logs. | §5.1, §6.1 |
| RTB-24 | The Android device MUST initiate the outbound TLS WebSocket connection to the relay. The phone MUST NOT open an inbound public port. | §5.2 |
| RTB-25 | The relay adapter MUST use a device-specific relay credential, not the local-client credential and not the GitHub PAT. | §5.2, §6.1 |
| RTB-26 | Remote-agent authentication is owned by the relay and agent boundary and MUST NOT be passed into the core as a raw bearer secret. | §5.2 |
| RTB-27 | Device and session routing, remote-principal authorization, message expiry, replay detection and request correlation MUST be explicit in the Relay Transport Contract. | §5.2 |
| RTB-28 | The relay MUST NOT persist repository content, GitHub credentials, or full request and response bodies. | §5.2 |
| RTB-29 | Relay transport failure MUST NOT disable or alter localhost operation. | §5.2, §5.3 |

### 3.4 Security rules

| ID | Rule | Detail |
|---|---|---|
| RTB-30 | Android Keystore protects cryptographic key material; it is not a general-purpose string database. The PAT and relay secret MUST be encrypted using a Keystore-backed key and stored as ciphertext in private app storage. | §6.1 |
| RTB-31 | Secret storage MUST be excluded from Android backup and device-transfer paths. Plaintext lifetime MUST be minimized and immutable copies avoided where practical. | §6.1 |
| RTB-32 | Authorization MUST be enforced on the phone for every operation. Agent tool schemas, relay routing and PAT permissions are defense in depth and MUST NOT substitute for local policy. | §6.2 |
| RTB-33 | Repository content is untrusted data, never policy and never executable instruction. The phone MUST NOT infer authorization from text returned by GitHub or from agent-generated explanations. | §6.3 |
| RTB-34 | Read and write permissions MUST be independently configurable. | §6.3 |
| RTB-35 | Destructive operations SHOULD require user confirmation by default. Pull-request creation SHOULD be separately configurable for automatic or confirmation-required execution. | §6.3, App. D |
| RTB-36 | Sensitive paths MUST be denied by default, including `.github/workflows/**`, repository secrets and configuration locations, and policy-defined protected files. | §6.3, §7.2 |
| RTB-37 | Branch restrictions MUST be evaluated against normalized branch names. Protected branches are non-writable unless explicitly and narrowly authorized. | §6.3, §7.2 |
| RTB-38 | Agent principals SHOULD have per-agent scopes, quotas and revocation. | §6.3 |
| RTB-39 | The relay MUST be treated as trusted to see and potentially alter the frames it terminates, unless the end-to-end protected profile is implemented and independently reviewed. | §6.4 |
| RTB-40 | The end-to-end protected profile MUST define key agreement, identity binding, nonce and sequence rules, authenticated encryption, key rotation, replay defense and recovery, and MUST receive independent cryptographic review before production. | §6.4 |

### 3.5 Execution rules

| ID | Rule | Detail |
|---|---|---|
| RTB-41 | The system MUST NOT claim exactly-once delivery across a network connection. The contract MUST state the delivery guarantee that applies to each operation. | §8.1 |
| RTB-42 | Every command MUST include a globally unique `request_id`, `contract_version`, `operation`, `issued_at`, `expires_at`, `principal` and typed arguments. Authentication material MUST be outside the ordinary tool arguments. | §8.2 |
| RTB-43 | Requests dispatched to GitHub but lacking a confirmed response MUST enter `OUTCOME_UNKNOWN` and MUST NOT be blindly replayed. | §8.4 |
| RTB-44 | Before retrying a write, the component MUST reconcile by reading the target branch, file or commit state, or by checking the relevant pull request. | §8.4 |
| RTB-45 | Duplicate detection MUST use `request_id` and an operation fingerprint. A client-supplied idempotency key MUST NOT be treated as authorization. | §8.4 |
| RTB-46 | Retries MUST be bounded and MUST use exponential backoff with jitter. | §8.4, §9.2 |
| RTB-47 | Expired requests MUST NOT execute after reconnection. | §8.4 |
| RTB-48 | The component MUST serialize or otherwise coordinate conflicting writes to the same repository path, and MUST handle GitHub conflict responses. | §7.1 |
| RTB-49 | All filesystem paths received, constructed, compared, authorized, or used by the bridge MUST be normalized using the following deterministic algorithm before any filesystem operation or policy decision: (1) reject empty input, NUL, and C0/DEL control characters; (2) reject any backslash `\`; (3) do NOT percent-decode raw repository-relative paths, while URI-derived paths MUST reject invalid percent-encodings and percent-decode at most once; (4) normalize to Unicode NFC; (5) preserve case and treat containment checks as case-sensitive even on case-insensitive host filesystems; (6) reject leading `/`, collapse empty segments (`//`) and `.`, resolve in-bounds `..` segments, strip trailing `/`, and reject immediately on root escape or empty result; (7) for real-filesystem targets, canonicalize root and target, verify strict case-sensitive containment inside the canonical repository root (rejecting symlink escapes), and operate only on that verified target; (8) join canonical segments with `/`. See §7.2 and `schemas/path-normalization-test-vectors.v1.json`. Any platform-specific implementation MUST preserve these security properties and MUST NOT weaken the normalization or root-containment requirements. | §7.2 |
| RTB-50 | Policy changes MUST require explicit user action and MUST be auditable. | §7.2 |
| RTB-51 | The foreground service MUST be started by explicit user action and MUST show a persistent notification with meaningful controls. The component MUST NOT promise automatic restart after force-stop. | §9.1 |
| RTB-52 | The component MUST NOT declare an unrelated foreground-service type merely to bypass platform restrictions. | §9.1 |
| RTB-53 | The component MUST treat local HTTP availability and relay connectivity as separate service health states. | §9.1, §9.2 |

### 3.6 Data-handling rules

| ID | Rule | Detail |
|---|---|---|
| RTB-54 | Logs MUST NOT contain PATs, relay or local tokens, file content, full bodies, or authorization headers. | §10 |
| RTB-55 | The component MUST NOT claim guaranteed zeroization of secret buffers on managed runtimes. | §10 |
| RTB-56 | Audit retention MUST be configurable and documented. Users MUST be able to inspect and clear audit history, subject to any separately disclosed security retention requirements. | §11 |
| RTB-57 | Diagnostic telemetry, if enabled, MUST be opt-in or clearly disclosed, minimized and separately governed. | §11 |
| RTB-58 | The GitHub REST API version header MUST be pinned and documented, and MUST be reviewed during upgrades. | §7.1 |

## 4. Component boundary and contract model

### 4.1 Logical component

The Android application is one independently deployable component with internal modules. Internal modules may be separate Gradle modules, packages or processes, but their interfaces are defined by internal contracts. No external component is allowed to reach into internal implementation details.

| Internal module | Responsibility | Depends on |
|---|---|---|
| Contract Registry | Owns schemas, version negotiation, compatibility rules, error model and protocol documentation. | No transport or provider SDK |
| Application Core | Validates commands, coordinates authorization, request lifecycle, execution and audit events. | Internal interfaces only |
| Policy Engine | Enforces approved repositories, operation permissions, branch rules, path rules, size limits and confirmation requirements. | Policy contract |
| Credential Vault | Encrypts and retrieves secrets using Android Keystore-backed cryptographic keys. | Android Keystore and private app storage |
| GitHub Adapter | Maps approved internal commands to specific GitHub REST calls and normalizes results. | GitHub REST API |
| Localhost Adapter | Exposes the local contract over loopback HTTP for same-device clients. | Shared external contract |
| Relay Adapter (optional) | Maintains the outbound connection to a configured relay and maps relay envelopes to the same internal contract. | Shared external contract; relay-specific transport contract |
| Audit/Diagnostics | Records bounded metadata and operational diagnostics. | Audit event contract |

The component owns all of these (RTB-02), and uses the GitHub APIs directly without a working tree (RTB-06).

### 4.2 Contract-first rule

All communication crossing a component boundary MUST use a published contract (RTB-10). A contract defines identity, version, authentication context, request envelope, response envelope, error taxonomy, limits, timeout behavior, retry semantics and compatibility rules.

The Android core MUST NOT contain Cloudflare-specific concepts, agent-vendor-specific tool schemas or shell-agent-specific code (RTB-11). Transport adapters MAY use different wire encodings, but they MUST map to the same canonical command and result model (RTB-12).

The contract repository and this specification are the source of truth; generated schemas, SDKs and tool definitions are derived artifacts.

### 4.3 Contract set

| Contract | Purpose | Required fields / topics |
|---|---|---|
| Tool Contract | Canonical operation definitions. | Operation names, inputs, output schemas, constraints, side effects, delivery guarantees. |
| Local Transport Contract | Loopback HTTP request and response behavior. | Base path, version, auth, host/origin guards, envelope, sync-200 vs 202-poll model, limits, errors, timeout. |
| Relay Transport Contract | Phone-to-relay and agent-to-relay message envelope. | Device identity (`dev_<opaque>`), session binding, monotonic `seq` (window `DV-RTB-RELAY-SEQ-WINDOW`), request ID, correlation, expiry (`DV-RTB-RELAY-EXPIRY-S`), replay defense, acknowledgements, advisory `principal_assertion`. |
| Authorization Contract | Policy decisions for every operation. | Principal, repo, operation, branch, path, decision, reason, confirmation requirement. |
| Credential Contract | Credential types and lifecycle. | Issuer, subject, scope, expiry, rotation, revocation, storage class. |
| Execution Lifecycle Contract | Request states and delivery guarantees. | Canonical `RequestState` values (`RECEIVED` through terminal `SUCCEEDED`/`FAILED`/`EXPIRED`/`REJECTED`/`CANCELLED`). |
| Audit Event Contract | Local audit and optional sanitized telemetry. | Timestamp, principal, request ID, operation, target, outcome, commit and pull-request identifiers. |

**Lifecycle naming reconciliation (§4.3 prose to `RequestState`):**

| §4.3 prose term | Canonical `RequestState` enum value(s) |
|---|---|
| Accepted | `RECEIVED`, `AUTHORIZED`, `AWAITING_APPROVAL` |
| Executing | `EXECUTING` |
| Succeeded | `SUCCEEDED` |
| Failed | `FAILED` |
| Outcome-unknown | `OUTCOME_UNKNOWN`, `RECONCILING`, `MANUAL_REVIEW` |
| Cancelled | `CANCELLED` |
| Expired | `EXPIRED` |
| Rejected (policy / approval) | `REJECTED` |

> **Navigation Transport boundary:** The [Navigation Transport](navigation_transport_specification.md) is a separate adapter plane; the sample task (`example-org/sandbox`) does **not** inherit Navigation Transport session rules.

Contract versions MUST be explicit, as a path segment such as `/v1` or a schema version field. A breaking change requires a new major contract version (RTB-13). Minor additions MUST be backward compatible, and unknown fields MUST be handled according to the schema's compatibility rules (RTB-14).

## 5. Deployment and networking

### 5.1 Localhost interface — mandatory

The component MUST expose a local HTTP interface for same-device agents that communicate through shell or local tool adapters (RTB-16). The server MUST bind to loopback only (RTB-17).

- The default listener is `http://127.0.0.1:<configured-port>/api/v1/`, where the port follows `DV-RTB-PORT`.
- IPv6 loopback (`::1`) MUST also be bound whenever the Android runtime and network stack support it under `DV-RTB-IPV6`; if IPv6 loopback binding fails, the server MUST continue on `127.0.0.1` and log the failure. The server MUST NOT bind to wildcard addresses (`0.0.0.0` or `::`) or non-loopback interfaces (RTB-03).
- Response model: `list_branches` and `read_file` return synchronous `200 OK` with `ResponseEnvelope`; mutating operations (`create_branch`, `write_file`, `delete_file`, `create_pull_request`) return `202 Accepted` with `AcceptedResponse` and are polled via `GET /api/v1/requests/{request_id}`. `POST /api/v1/requests/{request_id}/cancel` is supported and idempotent for cancellable or already-cancelled requests.
- Error envelopes: errors occurring after a request is identified return `ResponseEnvelope` with `error`; pre-request transport/authentication/guard failures (e.g., `401`, `403` Host/Origin, `413` before parsing, `404` unknown route) return `TransportError` (`{"error": BridgeError}`).
- Health and readiness endpoints MUST reveal no credentials, repository contents or sensitive configuration (RTB-18).
- Only explicitly documented routes and methods are allowed. There MUST be no generic proxy and no arbitrary path forwarding (RTB-19, App. B).
- The listener MUST enforce strict request-size limits, read and write timeouts, connection limits and rate limits (RTB-20).
- Required headers (`RequestHeaders`): `Authorization: Bearer <token>`, `Content-Type: application/json`, and loopback `Host` (`127.0.0.1`, `[::1]`, or `localhost` with optional port). `Host` headers and `Origin` patterns not permitted by the Local Transport Contract MUST be rejected. Permissive CORS MUST NOT be enabled (RTB-21). Shell clients do not require browser CORS; browser access, if ever supported, requires a separate threat review.
- Authentication MUST be required even on loopback (RTB-22). Loopback is a network boundary, not proof of user identity.
- The local-client credential MUST be distinct from all relay and GitHub credentials, independently provisioned and independently revocable (RTB-23).
- Credentials MUST NOT be placed in URLs, query strings, command-line arguments, shell history or logs. Prefer an OS-protected local credential file or secret channel, or a short-lived token provisioned through an explicit pairing flow (RTB-23).
- Where the Android environment supports OS-level peer identity or Unix-domain socket permissions, these MAY supplement application authentication, but MUST NOT be assumed available across all shell runtimes.

> **Platform limitation.** `127.0.0.1` is scoped to a network namespace or device environment. A shell agent running in Termux or another app may or may not reach the Android app's loopback listener, depending on Android isolation, VPNs, user profiles and runtime arrangement. This MUST be validated on target devices. The component MUST NOT solve reachability by silently binding to the LAN; any non-loopback exposure requires a separate opt-in design, threat model and contract.

### 5.2 Remote relay interface — optional and isolated

The relay is an optional transport adapter. Its absence MUST NOT prevent the local interface from operating, and the core MUST NOT require a relay session to accept local requests (RTB-17).

- The Android device initiates the outbound TLS WebSocket connection; the phone does not open an inbound public port (RTB-24).
- The relay adapter uses a device-specific relay credential, not the local-client credential and not the GitHub PAT (RTB-25).
- Remote-agent authentication is owned by the relay and agent boundary and MUST NOT be passed into the core as a raw bearer secret (RTB-26). The relay adapter receives a verified `principal_assertion` (`agent_id`, advisory `scopes`, `asserted_by: "relay"`, `asserted_at`) under the Relay Transport Contract (`schemas/relay-envelope.v1.json`).
- Device identity (`dev_<opaque>`), per-connection `session_id` binding, monotonic `seq` within window `DV-RTB-RELAY-SEQ-WINDOW` (`64`), `correlation_id`, maximum request expiry `DV-RTB-RELAY-EXPIRY-S` (`300` seconds / 5 minutes), mandatory replay detection, and `ack`/`response` acknowledgements are defined in `schemas/relay-envelope.v1.json` and the [Cloudflare Relay specification](cloudflare_relay_specification.md) (RTB-27). Expired requests MUST NOT execute after reconnection (RTB-47).
- The relay may maintain transient connection and session state. It MUST NOT persist repository content, GitHub credentials, or full request and response bodies (RTB-28).
- The initial release uses the trusted-relay profile (`DV-RTB-RELAY-PROFILE`); the relay is trusted to see and potentially alter frames it terminates (RTB-39). The end-to-end protected profile remains deferred pending design and independent cryptographic review.
- Relay transport failure MUST NOT disable or alter localhost operation (RTB-29).

### 5.3 Local and remote separation

| Concern | Localhost path | Remote relay path |
|---|---|---|
| Transport credential | Local-client credential | Device relay credential plus remote-agent identity assertion |
| GitHub PAT | Never exposed; retrieved internally | Never exposed; retrieved internally |
| Authentication authority | Local pairing and provisioning authority | Relay and agent identity authority |
| Network path | Loopback only | Outbound WSS from phone; agent to relay |
| Failure independence | Must work without relay | May be unavailable without affecting local path |
| Core command model | Canonical Tool Contract | Same canonical Tool Contract |

## 6. Security architecture

### 6.1 Credential classes

| Credential | Purpose | Storage / handling | Must not be shared with |
|---|---|---|---|
| GitHub fine-grained PAT | GitHub API authorization. | Encrypted at rest using a Keystore-protected key; plaintext only briefly in memory; never logged or returned. | Any agent, relay or local client. |
| Local-client credential | Authenticate same-device shell clients. | Independently provisioned, scoped, revocable; stored securely outside command history. | Relay and GitHub. |
| Relay-device credential | Authenticate phone to relay. | Keystore-backed encryption; rotation and revocation supported. | Local clients and GitHub. |
| Remote-agent credential | Authenticate agent to relay. | Owned by the agent and relay deployment; not stored as a GitHub secret on the phone. | Local clients and GitHub. |
| Derived session key (if E2E enabled) | Protect message integrity and confidentiality across relay. | Short-lived, bound to device and agent identities; rotated on session renewal. | Other sessions or credential classes. |

All four credential classes are independent, and no credential may be reused across trust domains (RTB-04).

Android Keystore protects cryptographic key material; it is not a general-purpose string database. The PAT and relay secret MUST be encrypted using a Keystore-backed key and stored as ciphertext in private app storage (RTB-30). That storage MUST be excluded from Android backup and device-transfer paths. Plaintext lifetime MUST be minimized and immutable copies avoided where practical (RTB-31).

### 6.2 Authentication and authorization

Authorization is enforced on the phone for every operation (RTB-32). The agent's tool schema, relay routing and PAT permissions are defense in depth, not substitutes for local policy.

The authorization sequence runs in this order:

1. Authenticate the transport principal at the adapter boundary.
2. Map that principal to a canonical principal identity and permitted trust domain.
3. Validate the request envelope, contract version, expiry, `request_id` and schema.
4. Resolve and canonicalize the repository identifier, branch and path.
5. Evaluate the repository allow-list and operation-level policy.
6. Evaluate branch and path restrictions, including protected paths and workflow files.
7. Determine whether user confirmation is required. If so, suspend execution pending explicit approval.
8. Only then dispatch the approved command to the GitHub adapter.

### 6.3 Prompt injection and agent compromise

- Repository content is untrusted data, never policy and never executable instruction (RTB-33).
- The phone MUST NOT infer authorization from text returned by GitHub, or from agent-generated explanations (RTB-33).
- Read and write permissions MUST be independently configurable (RTB-34).
- Destructive operations, including `delete_file` and branch deletion if later added, SHOULD require user confirmation by default (RTB-35).
- Pull-request creation SHOULD be separately configurable for automatic versus confirmation-required execution (RTB-35).
- Sensitive paths MUST be denied by default, including `.github/workflows/**`, repository secrets and configuration locations, and policy-defined protected files (RTB-36).
- Branch restrictions MUST be evaluated against normalized branch names. Protected branches are non-writable unless explicitly and narrowly authorized (RTB-37).
- Agent principals SHOULD have per-agent scopes, quotas and revocation (RTB-38).

### 6.4 Relay trust model

TLS and WSS protect the transport between each endpoint and the relay, but do not provide end-to-end protection through a relay that terminates both connections. Version 2 therefore defines two deployment profiles.

| Profile | Security property | Requirement |
|---|---|---|
| Trusted relay profile | Relay can see and potentially modify messages; it is trusted as a transport intermediary. | Document this trust assumption, restrict relay operators and access, minimize logging, and authenticate phone-side requests and responses. |
| End-to-end protected profile | Relay forwards opaque, authenticated or encrypted envelopes and cannot forge valid messages or read protected payloads. | Define key agreement, identity binding, nonce and sequence rules, authenticated encryption, key rotation, replay defense and recovery. Require independent cryptographic review before production. (RTB-40) |

Do not claim that a relay cannot forge results unless the end-to-end protected profile is implemented and verified (RTB-39).

## 7. Canonical tool surface

The initial public tool surface remains intentionally small. All tool definitions are transport-neutral and published through the Tool Contract.

| Operation | Inputs | Behavior and GitHub mapping |
|---|---|---|
| `list_branches` | `repo`; optional `page` and `per_page` | `GET /repos/{owner}/{repo}/branches`; paginate; enforce the repository allow-list. |
| `create_branch` | `repo`, `base`, `new_branch` | Resolve the base branch to a commit SHA, then `POST /repos/{owner}/{repo}/git/refs` with `ref=refs/heads/{new_branch}` and `sha=<resolved SHA>`. |
| `read_file` | `repo`, `path`, `ref` | `GET /repos/{owner}/{repo}/contents/{path}`; decode supported text content; enforce file and response limits. |
| `write_file` | `repo`, `path`, `content`, `branch`, `message` | Resolve the existing file SHA when updating; omit the SHA only for creation; encode content as required by the API; handle conflicts and stale branch heads. |
| `delete_file` | `repo`, `path`, `branch`, `message` | Resolve the current file SHA, then call the contents delete endpoint; handle conflict and unknown completion. |
| `create_pull_request` | `repo`, `head`, `base`, `title`; optional `body` | `POST /repos/{owner}/{repo}/pulls`; `head` accepts `branch` (resolved to the approved repo owner) or `owner:branch`; if an equivalent open PR exists for `(head, base)`, return `SUCCEEDED` idempotently with `already_existed: true` and a `note`. |

The phone MUST resolve repository owner and name from a canonical approved-repository record, rather than accepting arbitrary URL fragments. No operation may accept an arbitrary endpoint, host, HTTP method or raw GitHub API path (RTB-15).

### 7.1 GitHub correctness requirements

- File updates require the current blob or file SHA; file creation does not. Resolve internally, unless the public contract explicitly exposes a conditional-update token.
- File deletion requires the current file SHA.
- Branch creation requires a commit SHA. Resolve the selected base branch to its current commit SHA before creating `refs/heads/<new_branch>`.
- Pull-request `head` accepts bare `branch` (interpreted as `{approved_owner}:{branch}`) or qualified `owner:branch`. Before or on `POST /pulls`, check for an equivalent open PR matching `(head, base)`; if found, return `SUCCEEDED` with the existing PR `number`, `url`, `already_existed: true`, and an explanatory `note`.
- Empty repositories return `NOT_FOUND` on `read_file` and an empty `BranchList` on `list_branches`; writes/branch creation against a missing base branch return `NOT_FOUND` or `CONFLICT`.
- Binary or non-UTF-8 files on `read_file` fail with `UNSUPPORTED_CONTENT`; content exceeding `DV-RTB-REQUEST-MAX-BYTES` or `DV-RTB-RESPONSE-MAX-BYTES` fails with `PAYLOAD_TOO_LARGE` before buffering.
- Contents API Base64 handling MUST strip ASCII whitespace/newlines when decoding GitHub responses using strict RFC 4648 decoding, and MUST encode outgoing `write_file` payloads as single-line Base64 without embedded newlines.
- Paginate list operations and enforce bounded page sizes.
- Handle rate limits and secondary rate limits (`403`/`429` with rate-limit headers) with bounded backoff (`DV-RTB-BACKOFF`), honouring `Retry-After` capped at the maximum backoff/deadline. `409`/`422` conflicts return `CONFLICT` with no auto-retry.
- Normalize upstream errors per §11.1 and `schemas/error.v1.json`. Never return raw upstream bodies or credentials in sanitized error messages.
- The GitHub REST API version header MUST be pinned and documented, and MUST be reviewed during upgrades (RTB-58).
- Serialize or otherwise coordinate conflicting writes to the same repository path, and handle GitHub conflict responses (RTB-48).

### 7.2 Repository and path policy

At provisioning time, the user selects approved repositories and configures operation permissions. Each request MUST be checked against that allow-list before GitHub is contacted.

- **Repository allow-list canonicalization:** `owner/repository` names are normalized to lowercase before comparison against the allow-list (e.g., `Example-Org/Sandbox` and `example-org/sandbox` resolve to the same canonical record). Only the current canonical name is accepted; the component MUST NOT automatically follow repository renames or GitHub redirects. Any non-canonical or unapproved repository identifier MUST fail closed with a typed error (`VALIDATION_FAILED` or `POLICY_DENIED`).
- **Default protected branches and paths:** Protected branches default to `main` and `master` (`DV-RTB-PROTECTED-BRANCHES`). Protected paths default to `.github/workflows/**`, `.git/**`, `.gitana/**`, and policy/configuration files unless explicitly enabled (`DV-RTB-PROTECTED-PATHS`). All other branches and paths are writable subject to allow-list and confirmation rules. Policy changes require explicit user action and are auditable (RTB-50).

**RTB-49 canonical path normalization algorithm:**

All path comparisons and filesystem operations MUST run this deterministic pipeline before any policy decision or execution; prefix-only string checks are insufficient:

1. **Reject empty and control characters:** Reject empty/blank strings, NUL (`\u0000`), and C0/DEL control characters (`\u0000`–`\u001f`, `\u007f`).
2. **Reject backslashes:** Reject any path containing `\` (do not translate `\` to `/`).
3. **Percent-encoding boundary:** Raw repository-relative paths (`RepoPath` tool arguments) MUST NOT be percent-decoded (e.g. `%2e%2e/secret` stays literal `%2e%2e/secret`). URI-derived paths MUST reject invalid or truncated percent-encoding, percent-decode at most once, and then undergo steps 1–2 and 4–8.
4. **Unicode normalization:** Normalize the string to Unicode **NFC**.
5. **Case preservation and containment semantics:** Preserve exact character case (`Readme` ≠ `README`). All containment checks MUST treat paths as case-sensitive even if the underlying host filesystem is case-insensitive.
6. **Separator and dot-segment normalization:** Reject leading `/` (absolute paths are invalid). Split on `/`, discard empty segments (`//`) and `.` segments, and resolve `..` against a segment stack; if `..` encounters an empty stack, **reject immediately** as root escape (`../../outside`). Strip any trailing `/`. If the resulting canonical segment list is empty, **reject**.
7. **Symlink containment and TOCTOU safety:** Where filesystem symlinks exist, canonicalize both the repository root and the target real path, verify using case-sensitive prefix containment that the resolved target resides strictly inside the canonical repository root (rejecting any symlink escape), and execute the operation against that same verified target.
8. **Canonical join:** Join remaining segments with `/` and evaluate protected-path rules against the resulting canonical string. Conformance test vectors are published in `schemas/path-normalization-test-vectors.v1.json`.

## 8. Request lifecycle, delivery, and idempotency

### 8.1 Delivery model

The system MUST NOT claim exactly-once delivery across a network connection. The achievable design is at-least-once transport with deduplication and explicit outcome tracking, or at-most-once dispatch with possible unknown outcomes. The contract defines the model for each operation (RTB-41):

| Operation | Delivery guarantee | Local HTTP model | Reconciliation entry point on `OUTCOME_UNKNOWN` |
|---|---|---|---|
| `list_branches` | At-least-once (idempotent read) | Synchronous `200` | Safe re-read `GET /repos/{owner}/{repo}/branches` |
| `read_file` | At-least-once (idempotent read) | Synchronous `200` | Safe re-read `GET /repos/{owner}/{repo}/contents/{path}` |
| `create_branch` | Reconcilable (at-most-once dispatch) | `202 Accepted` + poll | Read `GET /repos/{owner}/{repo}/git/ref/heads/{new_branch}` and verify SHA matches resolved `base` commit SHA |
| `write_file` | Reconcilable (at-most-once dispatch) | `202 Accepted` + poll | Read `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` and verify blob/commit hash without persisting content |
| `delete_file` | Reconcilable (at-most-once dispatch) | `202 Accepted` + poll | Read `GET /repos/{owner}/{repo}/contents/{path}?ref={branch}` and verify file absence after pre-existing SHA |
| `create_pull_request` | Reconcilable (at-most-once dispatch) | `202 Accepted` + poll | Query `GET /repos/{owner}/{repo}/pulls?state=open&head={owner}:{head_branch}&base={base}` |

### 8.2 Request envelope

Every command MUST include a globally unique `request_id`, `contract_version`, `operation`, `issued_at`, `expires_at`, `principal` and typed arguments (RTB-42). Relay envelopes additionally include device and session binding, and transport sequence and correlation data. Authentication material MUST be outside the ordinary tool arguments.

### 8.3 State machine

| State | Meaning | Allowed transition |
|---|---|---|
| `RECEIVED` | Envelope accepted and schema-validated. | `AUTHORIZED`, `REJECTED`, `EXPIRED`, `CANCELLED` |
| `AUTHORIZED` | Policy passed; confirmation may still be pending. | `AWAITING_APPROVAL`, `EXECUTING`, `REJECTED`, `EXPIRED`, `CANCELLED` |
| `AWAITING_APPROVAL` | User confirmation required. | `EXECUTING`, `REJECTED`, `EXPIRED`, `CANCELLED` |
| `EXECUTING` | GitHub operation dispatched. | `SUCCEEDED`, `FAILED`, `OUTCOME_UNKNOWN` |
| `SUCCEEDED` | GitHub result confirmed and recorded. | Terminal |
| `FAILED` | Operation definitively failed. | Terminal |
| `OUTCOME_UNKNOWN` | Request may have succeeded, but the acknowledgement or result is unavailable. | `RECONCILING`, `MANUAL_REVIEW` |
| `RECONCILING` | Safe read-back or provider lookup is being used to determine the outcome. | `SUCCEEDED`, `FAILED`, `MANUAL_REVIEW` |
| `MANUAL_REVIEW` | Automated reconciliation could not decide; awaiting human resolution. | `SUCCEEDED`, `FAILED`, `CANCELLED` |
| `EXPIRED` / `REJECTED` / `CANCELLED` | Not executed or cancelled before dispatch / via review dismissal. | Terminal |

Persist only the minimum request metadata needed to prevent duplicate side effects and reconcile uncertain outcomes. Do not persist request bodies containing file content, unless an explicitly approved, encrypted, bounded queue design is added. A queue must have expiry, size limits, deletion rules and user-visible status.

Unknown contract versions, unknown operations, missing authentication, invalid authorization, malformed payloads and policy uncertainty all fail closed (RTB-08).

### 8.4 Retry rules

- Requests not yet dispatched may be retried, if still valid and authorized.
- Requests dispatched to GitHub but lacking a confirmed response MUST enter `OUTCOME_UNKNOWN`, and MUST NOT be blindly replayed (RTB-43).
- Before retrying a write, reconcile by reading the target branch, file or commit state, or by checking the relevant pull request (RTB-44).
- Use `request_id` and an operation fingerprint for duplicate detection. Do not treat a client-supplied idempotency key as authorization (RTB-45).
- Retries MUST be bounded and MUST use exponential backoff with jitter (RTB-46).
- Expired requests MUST NOT execute after reconnection (RTB-47).

## 9. Android runtime and lifecycle

### 9.1 Foreground service

The component uses a user-started foreground service when continuous local and remote availability is required. A foreground service improves execution continuity but does not guarantee uninterrupted operation.

- The service type and permissions MUST match the target Android API level and the actual use case. The component MUST NOT declare an unrelated service type merely to bypass platform restrictions (RTB-52).
- Start through an explicit user action and show a persistent notification with meaningful controls (RTB-51).
- Handle notification permission behavior and foreground-service start restrictions for supported Android versions.
- Handle process death, force-stop, reboot, app update, permission changes and user stop distinctly.
- Do not promise automatic restart after force-stop; explain that user action may be required (RTB-51).
- Treat local HTTP availability and relay connectivity as separate service health states (RTB-53).

### 9.2 Network and Doze

- Observe network availability and validate actual connectivity; network callbacks alone do not prove internet reachability.
- Use heartbeat and timeout values configurable for the relay deployment.
- Detect half-open WebSockets using ping/pong or an application-level heartbeat.
- Use exponential backoff with jitter and a maximum interval (RTB-46).
- Do not assume immediate callbacks or immediate reconnection when Doze ends.
- Handle Wi-Fi and mobile transitions, DNS errors, TLS errors, captive portals and temporary offline states.
- Localhost requests may remain usable even when the remote relay is disconnected (RTB-29).

### 9.3 Ktor roles

Ktor is used as the local HTTP server implementation, bound to loopback only. The remote relay connection is a separate outbound WebSocket client. These are separate adapters and must not be described as one server socket. The local HTTP server is not exposed through the relay, and is not required for the relay adapter to function.

### 9.4 Local shell-agent compatibility

Provide a small documented CLI example or shell-compatible HTTP usage contract. The CLI, if supplied, is a client of the localhost contract and is not part of the GitHub execution core. It MUST obtain the local credential through a secure provisioning mechanism and MUST NOT embed secrets in scripts, command-line arguments, shell history or environment dumps. Validate loopback reachability from each supported shell runtime before claiming support.

## 10. Resource limits and data handling

| Resource | Requirement |
|---|---|
| Request body | Hard configurable maximum; reject before parsing oversized bodies. |
| File content | Hard configurable maximum; reject unsupported binary or oversized content. |
| Response body | Bounded; do not buffer unbounded GitHub responses. |
| WebSocket frame | Bounded maximum frame size; reject oversized frames. |
| Concurrency | Bounded global and per-repository concurrency; serialize conflicting path writes. |
| Timeouts | Separate connect, GitHub request, local request and end-to-end deadlines. |
| Queue | Bounded count, bytes and age; no unbounded offline queue. |
| Logs | No PATs, relay or local tokens, file content, full bodies or authorization headers (RTB-54). |
| Temporary memory | Minimize copies; clear mutable secret buffers where practical; never claim guaranteed zeroization on managed runtimes (RTB-55). |

Numeric starting points are held in Appendix D as `DV-RTB-...` IDs and are tuned after device testing.

Repository content necessarily exists transiently in phone memory while being processed, and in transit through the relay when remote transport is used. The guarantee is no persistent content cache and no working tree, not zero memory residency. Returned content may be retained by the requesting agent under that agent's own policies.

## 11. Audit, diagnostics, and privacy

Maintain a local audit record for each accepted operation. Recommended fields: timestamp, `request_id`, principal identifier, transport class (`local` or `relay`), repository, operation, normalized branch and path where applicable, decision, outcome, GitHub status category, commit SHA, and pull-request number. Do not record file contents, credentials, full request bodies or complete upstream response bodies (RTB-09).

Audit retention MUST be configurable and documented. Users MUST be able to inspect and clear audit history, subject to any separately disclosed security retention requirements (RTB-56). Diagnostic telemetry, if enabled, MUST be opt-in or clearly disclosed, minimized and separately governed (RTB-57).

### 11.1 Error contract

| Field | Meaning |
|---|---|
| `code` | Stable internal error code from `schemas/error.v1.json` / `ErrorCode.kt`. |
| `category` | One of `VALIDATION`, `AUTHENTICATION`, `AUTHORIZATION`, `POLICY`, `TRANSPORT`, `PROVIDER`, `RESOURCE_LIMIT`, `INTERNAL`. |
| `http_status` | The relevant local HTTP status, where applicable. |
| `retryable` | Whether retry is safe and recommended (`OUTCOME_UNKNOWN` MUST remain `false`). |
| `execution_state` | One of `NOT_STARTED`, `EXECUTING`, `SUCCEEDED`, `FAILED`, or `OUTCOME_UNKNOWN`. |
| `message` | Sanitized user-readable explanation (never contains credentials, file content, or raw upstream bodies). |
| `correlation_id` | Request correlation identifier; never a credential. |

**Canonical error code mapping (`schemas/error.v1.json`):**

| Code | Category | HTTP status | Retryable | Execution state | Meaning |
|---|---|---|---|---|---|
| `AUTH_FAILED` | `AUTHENTICATION` | `401` | `false` | `NOT_STARTED` | Missing or invalid transport credential. |
| `FORBIDDEN` | `AUTHORIZATION` | `403` | `false` | `NOT_STARTED` | Disallowed `Host`/`Origin` header or unauthorized scope. |
| `POLICY_DENIED` | `POLICY` | `403` | `false` | `NOT_STARTED` | Unapproved repository, protected branch/path, or denied operation. |
| `APPROVAL_DECLINED` | `AUTHORIZATION` | `403` | `false` | `NOT_STARTED` | User explicitly declined required confirmation. |
| `VALIDATION_FAILED` | `VALIDATION` | `400` | `false` | `NOT_STARTED` | Malformed envelope, unknown fields, invalid path, or operation mismatch. |
| `UNSUPPORTED_VERSION` | `VALIDATION` | `400` | `false` | `NOT_STARTED` | Unknown or unsupported major `contract_version`. |
| `EXPIRED` | `VALIDATION` | `400` | `false` | `NOT_STARTED` | Request exceeded `expires_at` before dispatch. |
| `REPLAY_DETECTED` | `VALIDATION` | `409` | `false` | `NOT_STARTED` | Duplicate `seq` or `request_id` reuse with conflicting fingerprint. |
| `NOT_FOUND` | `PROVIDER` | `404` | `false` | `FAILED` | Unknown `request_id`, route, repository, branch, or file. |
| `CONFLICT` | `PROVIDER` | `409` | `false` | `FAILED` | GitHub SHA/head conflict or non-cancellable terminal state. |
| `UNSUPPORTED_CONTENT` | `VALIDATION` | `422` | `false` | `FAILED` | Binary or non-UTF-8 file content on text `read_file`. |
| `PAYLOAD_TOO_LARGE` | `RESOURCE_LIMIT` | `413` | `false` | `NOT_STARTED` | Request, response, or frame exceeds Appendix D limits. |
| `RATE_LIMITED` | `RESOURCE_LIMIT` | `429` | `true` | `NOT_STARTED` | Local or upstream rate limit exceeded; retry with `DV-RTB-BACKOFF` / `Retry-After`. |
| `UPSTREAM_UNAVAILABLE` | `PROVIDER` | `503` | `true` | `FAILED` | Transient upstream GitHub failure before confirmed dispatch; retry with `DV-RTB-BACKOFF`. |
| `TIMEOUT` | `TRANSPORT` | `504` | `false` | `FAILED` | Pre-dispatch deadline exceeded. (Post-dispatch timeouts emit `OUTCOME_UNKNOWN`.) |
| `OUTCOME_UNKNOWN` | `TRANSPORT` | `504` | `false` | `OUTCOME_UNKNOWN` | Dispatched without confirmed outcome; MUST reconcile before any action (RTB-43, RTB-44). |
| `NOT_IMPLEMENTED` | `INTERNAL` | `501` | `false` | `NOT_STARTED` | Operation or adapter capability not implemented. |
| `INTERNAL` | `INTERNAL` | `500` | `false` | `FAILED` | Unexpected internal bridge failure. |

Retryable errors (`RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`) MUST obey `DV-RTB-BACKOFF` (exponential with full jitter: initial `1 s`, multiplier `2`, maximum `60 s`, maximum `5` attempts or until deadline). `OUTCOME_UNKNOWN` MUST remain `retryable: false`.

## 12. Provisioning and administration

1. Install and launch the Android application.
2. Create or unlock the credential vault using device-protected authentication where available.
3. Add a fine-grained GitHub PAT with the minimum repository selection and permissions required.
4. Select the exact repositories the component may access; store canonical `owner/repository` identifiers.
5. Configure per-repository read and write permissions, branch rules, protected paths and confirmation policy.
6. Enable the localhost interface and create or pair a local-client credential, independently of relay setup.
7. Optionally configure the relay URL and provision a distinct device relay credential.
8. Run a local self-test and, if enabled, a relay connectivity test.
9. Display the local endpoint, supported contract version, service status, and credential rotation and revocation controls, without displaying secret values.

Credential rotation MUST support replacing a PAT, a local-client credential and a relay-device credential independently. Revocation of one credential class MUST NOT require rotating the others. Lost-device response MUST include revoking the PAT and the relay-device identity.

## 13. Testing and acceptance criteria

| Test area | Acceptance criteria |
|---|---|
| Loopback binding | The listener is reachable only through loopback; no LAN or public interface is open; verify IPv4 and IPv6 behaviour. |
| Local credential separation | The local credential cannot authenticate to the relay; the relay credential cannot authenticate to localhost; the PAT is accepted by neither transport. |
| Relay independence | Local operations continue when the relay is disabled or unreachable. |
| Authorization | An unapproved repository, branch, path or operation is rejected before any GitHub request. |
| Prompt injection | Malicious README or config content cannot change phone-side policy or bypass approval. |
| GitHub API correctness | Create, update and delete file and branch operations succeed with correct SHA handling; conflicts are reported correctly. |
| Duplicate execution | Disconnect after the GitHub commit but before the response; reconnect does not blindly execute duplicate side effects. |
| Outcome reconciliation | An unknown write outcome is reconciled safely, or escalated for manual review. |
| Android lifecycle | Test supported API levels for foreground service, Doze, process death, reboot, notification behaviour and force-stop. |
| Resource limits | Oversized requests, files and frames are rejected without OOM or unbounded buffering. |
| Privacy | Automated tests and log inspection show no secrets or file content in logs or telemetry. |
| Audit | Every accepted operation has a local audit outcome, including rejection and unknown outcome. |

### 13.1 Release gates

- No production release until the local loopback authentication and cross-app reachability model is validated on the actual target Android versions and shell runtimes.
- No production release until duplicate and unknown-outcome behavior is tested under forced disconnects.
- No production release until repository allow-list and path normalization tests pass.
- No production release until the relay trust profile is explicitly selected and documented.
- No production release until credential backup exclusion, rotation and revocation are tested.

## 14. Resolved owner decisions

All items previously listed as open decisions have been resolved. The final choices are recorded in the Decision Log ([Appendix E](#appendix-e-decision-log)). Corresponding "open decision" and "to be finalized" language has been removed from this document.

## 15. Summary

Version 2 defines the Android Remote Repository Tool Bridge as a self-contained, contract-first GitHub execution component. It MUST expose a loopback-only local interface for same-device shell agents, and MAY expose an optional outbound relay transport. Both adapters use the same canonical tool and authorization contracts; neither adapter is allowed to bypass the phone-side policy engine.

The local interface is intentionally independent of Cloudflare and remote-agent credentials. Local-client, relay-device, remote-agent and GitHub credentials are separate identities. The component owns credential handling, repository allow-lists, branch and path rules, approval policy, request lifecycle, GitHub API correctness, and audit behaviour.

The system makes no unsupported exactly-once or uninterrupted-service guarantees. It uses request IDs, deduplication, explicit lifecycle states, safe reconciliation of uncertain writes, bounded retries and clear outcome reporting. It stores no Git working tree and no persistent repository-content cache.

This document is a review draft architecture specification. Owner decisions, including port policy, local authentication, IPv6 loopback, relay security profile, confirmation defaults, offline queue policy, numeric limit starting points, Android SDK levels, the GitHub API version pin, and Phase A contract decisions (`RTB-D10` … `RTB-D17`), are recorded in Appendix E. Remaining work before an approved baseline is limited to validation gates and remaining document hygiene tracked in the [companion exit-tasks document](remote_repository_tool_bridge_v2_draft_exit_tasks.md).

## 16. Versioning and compatibility

The component carries a protocol version in the path segment and the contract version in each envelope. Each contract listed in §4.3 has its own version, and a breaking change to any of them requires a new major contract version (RTB-13).

| Change | Effect |
|---|---|
| A new operation added to the Tool Contract | Minor version bump. Existing clients MUST ignore unknown operations (RTB-14). |
| A new optional field added to a request or response envelope | Minor version bump. Unknown fields MUST be handled per the schema's compatibility rules (RTB-14). |
| A renamed or removed field, a changed semantic, or a new required field | Major version bump. The previous major version MUST remain resolvable for the migration window. |
| A new contract introduced between the component and a transport | Independent version. The core MUST NOT require it to operate (RTB-17). |
| A new GitHub API behaviour | Pinned API version header is reviewed and re-pinned (RTB-58). |

An unknown major contract version MUST fail closed (RTB-08).

## 17. Conformance tests

| ID | Test |
|---|---|
| RTB-T01 | The local listener is bound only to loopback addresses. No LAN or public interface is open (RTB-03). |
| RTB-T02 | The listener rejects `Host` headers and origin patterns outside the Local Transport Contract, and serves no permissive CORS policy (RTB-21). |
| RTB-T03 | The listener enforces the request-size, timeout, connection and rate limits, and rejects oversized bodies before parsing them (RTB-20). |
| RTB-T04 | The health and readiness endpoints reveal no credentials, repository contents or sensitive configuration (RTB-18). |
| RTB-T05 | A request without a valid local-client credential is rejected, and the same credential is refused by the relay (RTB-22, RTB-23). |
| RTB-T06 | The PAT is accepted by neither transport, and no credential class authenticates to another class's boundary (RTB-04). |
| RTB-T07 | Local operations continue unchanged when the relay is disabled, unreachable or mid-reconnect (RTB-29). |
| RTB-T08 | Only the documented routes and methods are served. There is no proxy and no arbitrary path forwarding (RTB-19). |
| RTB-T09 | An unapproved repository, branch, path or operation is rejected before any GitHub request is issued (RTB-05). |
| RTB-T10 | A traversal, ambiguous-encoding, invalid-separator or prefix-bypass path is rejected by the canonical normalizer (RTB-49). Required cases: `src/./main` normalizes to `src/main`; `src/lib/../main` normalizes to `src/main`; `../../outside` is rejected; NFC and decomposed Unicode equivalents normalize to the same NFC representation; `Readme` and `README` preserve case and are compared using filesystem semantics; `%2e%2e/secret` as a raw filesystem path is not percent-decoded; a symlink pointing outside the repository root is rejected; invalid percent encoding in a URI-derived path is rejected. |
| RTB-T11 | Writes to a protected branch and to `.github/workflows/**` are denied by default (RTB-36, RTB-37). |
| RTB-T12 | Malicious README or config content cannot alter policy or bypass approval (RTB-33). |
| RTB-T13 | Read and write permissions are configurable independently, and a write-only agent cannot read (RTB-34). |
| RTB-T14 | `delete_file` and `create_pull_request` require confirmation by default (RTB-35). |
| RTB-T15 | An unknown contract version, unknown operation, malformed payload or missing authentication fails closed with no side effect (RTB-08). |
| RTB-T16 | No operation accepts an arbitrary endpoint, host, HTTP method or raw GitHub API path (RTB-15). |
| RTB-T17 | `create_branch` resolves the base branch to a commit SHA before creating the ref, and `write_file` and `delete_file` use the current file SHA (RTB-48). |
| RTB-T18 | Create, update and delete file and branch operations succeed against GitHub, and conflicts are reported correctly (RTB-48). |
| RTB-T19 | An empty repository, a missing branch, a missing file, a binary file and an oversized file each produce the documented outcome (RTB-58). |
| RTB-T20 | A disconnect after the GitHub commit but before the response leaves the request in `OUTCOME_UNKNOWN`, and reconnect does not duplicate the side effect (RTB-43). |
| RTB-T21 | Before any write retry, the component reconciles by reading the target branch, file or commit state (RTB-44). |
| RTB-T22 | A duplicate `request_id` with the same operation fingerprint is detected, and a client-supplied idempotency key grants nothing (RTB-45). |
| RTB-T23 | Retries are bounded, use exponential backoff with jitter, and an expired request does not execute after reconnection (RTB-46, RTB-47). |
| RTB-T24 | The relay receives only a device credential and a verified principal assertion, never a raw agent secret (RTB-25, RTB-26). |
| RTB-T25 | The relay persists no repository content, no GitHub credential and no full request or response body (RTB-28). |
| RTB-T26 | The deployment states the trusted-relay trust assumption in operator-facing text, and makes no claim that the relay cannot forge results (RTB-39). |
| RTB-T27 | Secrets are stored as ciphertext in private app storage and are excluded from backup and device-transfer paths (RTB-30, RTB-31). |
| RTB-T28 | A PAT, a relay or local token, file content, a full body and an authorization header never appear in logs or telemetry (RTB-54, RTB-57). |
| RTB-T29 | Every accepted operation has a local audit outcome, including rejection and unknown outcome, and audit retention is configurable and clearable (RTB-09, RTB-56). |
| RTB-T30 | The foreground service starts only by explicit user action, shows a notification, and does not claim automatic restart after force-stop (RTB-51, RTB-52). |
| RTB-T31 | The component stores no working tree and no persistent repository-content cache (RTB-06, RTB-07). |
| RTB-T32 | Concurrent conflicting writes to the same repository path are serialized, and a GitHub conflict response is handled (RTB-48). |
| RTB-T33 | A breaking contract change bumps the major version, and an unknown major version fails closed (RTB-13, RTB-08). |

## 18. Changelog

| Version | Date | Change |
|---|---|---|
| 2.0-draft | 25 November 2026 | Restructured to the house format: metadata header, terms glossary, identifier-bearing architecture rules, conformance tests, and named defaults. No technical decision changed. Owner decisions are recorded in Appendix E. |
| 2.0-draft | 26 November 2026 | Closed Phase A contract decisions (`RTB-D10`–`RTB-D17`): RTB-49 canonical path algorithm and test vectors, Local Transport response model and `::1` alignment, lifecycle edges and delivery table, full 18-code error catalog, `create_pull_request` head/idempotency rules, allow-list lowercase canonicalization and default protected lists, relay window/expiry defaults, and §4.3/§15 consistency cleanup. |

## Appendix A: Canonical request envelope

Normative JSON conforming to `schemas/tool-contract.v1.json#/$defs/RequestEnvelope`:

```json
{
  "contract_version": "1.0",
  "request_id": "urn:uuid:550e8400-e29b-41d4-a716-446655440000",
  "issued_at": "2026-11-26T15:00:00Z",
  "expires_at": "2026-11-26T15:05:00Z",
  "principal": {
    "id": "local-client:paired-01",
    "transport": "localhost"
  },
  "operation": "read_file",
  "arguments": {
    "repo": "example-org/sandbox",
    "path": "README.md",
    "ref": "main"
  }
}
```

## Appendix B: Localhost routes

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/api/v1/health` | Non-sensitive liveness. |
| `GET` | `/api/v1/capabilities` | Contract version and supported operations; no secrets. |
| `POST` | `/api/v1/tools/{operation}` | Submit a canonical tool request (`200` sync for `list_branches`, `read_file`; `202` + poll for `create_branch`, `write_file`, `delete_file`, `create_pull_request`). |
| `GET` | `/api/v1/requests/{request_id}` | Read request lifecycle status. |
| `POST` | `/api/v1/requests/{request_id}/cancel` | Idempotent cancellation of cancellable states (`RECEIVED`, `AUTHORIZED`, `AWAITING_APPROVAL`, `MANUAL_REVIEW`) to `CANCELLED`. |

Errors returned for an established request use `ResponseEnvelope`; pre-request transport/authentication/guard failures use `TransportError` (`schemas/local-transport.v1.json`).

## Appendix C: External references

- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- [Android foreground services](https://developer.android.com/develop/background-work/services/foreground-services)
- [Android Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [GitHub REST API — Repository contents](https://docs.github.com/en/rest/repos/contents)
- [GitHub REST API — Git references](https://docs.github.com/en/rest/git/refs)
- [GitHub REST API — Pull requests](https://docs.github.com/en/rest/pulls/pulls)
- [GitHub REST API — Rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
- [Cloudflare Durable Objects — WebSockets](https://developers.cloudflare.com/durable-objects/best-practices/websockets/)

## Appendix D: Defaults

No default value is stated anywhere else in this specification. Each default is defined here and referenced by its `DV-RTB-...` ID. Numeric limits are conservative starting points intended to be tuned after device testing on representative hardware and shell runtimes.

| ID | Default | Value |
|---|---|---|
| `DV-RTB-PORT` | Local HTTP port policy | Configurable non-privileged port (≥ 1024). Recommended starting default: **18765**. The bound address and port MUST always be shown in the UI. Never bind below 1024. |
| `DV-RTB-TOKEN-ENTROPY-BITS` | Local-client credential entropy | At least **256 bits**. |
| `DV-RTB-TOKEN-ENCODING` | Local-client credential format | 32-byte cryptographically secure random value, **base64url**-encoded (no padding). |
| `DV-RTB-IPV6` | IPv6 loopback | Support `::1` where the server stack and target shell runtimes can use it. Bind **only** to loopback addresses (`127.0.0.1` and, if enabled, `::1`). Never bind to `::`, `0.0.0.0`, or any non-loopback interface. |
| `DV-RTB-RELAY-PROFILE` | Relay security profile | **Trusted-relay** profile for the initial release. The relay can see and potentially modify the frames it terminates. An end-to-end protected profile is deferred until full design and independent cryptographic review. |
| `DV-RTB-CONFIRM-DELETE` | Confirmation default for `delete_file` | User confirmation **required**. |
| `DV-RTB-CONFIRM-PR` | Confirmation default for `create_pull_request` | User confirmation **required**; later user-configurable. |
| `DV-RTB-QUEUE` | Offline queue policy | **No write-payload queueing**. Retain only bounded lifecycle metadata and reconcile unknown outcomes on reconnect. |
| `DV-RTB-REQUEST-MAX-BYTES` | Maximum request body / file content size | **2 097 152** bytes (2 MiB). Reject before parsing. |
| `DV-RTB-RESPONSE-MAX-BYTES` | Maximum response body size | **4 194 304** bytes (4 MiB). Do not buffer unbounded GitHub responses. |
| `DV-RTB-WS-FRAME-MAX-BYTES` | Maximum WebSocket frame size | **1 048 576** bytes (1 MiB). Reject oversized frames. |
| `DV-RTB-MAX-INFLIGHT` | Concurrent in-flight requests (global) | **2**. Per-repository concurrency also bounded; conflicting path writes MUST be serialized. |
| `DV-RTB-TIMEOUT-CONNECT-S` | Outbound connect timeout | **15** seconds. |
| `DV-RTB-TIMEOUT-GITHUB-S` | GitHub request timeout | **45** seconds. |
| `DV-RTB-TIMEOUT-LOCAL-S` | Local HTTP request timeout | **60** seconds. |
| `DV-RTB-TIMEOUT-E2E-S` | End-to-end deadline (accepted → terminal) | **120** seconds (or earlier cancellation). |
| `DV-RTB-RATE-LOCAL` | Local path rate limit | **30** requests per minute per principal (token-bucket or equivalent); modest global ceiling of the same order. |
| `DV-RTB-BACKOFF` | Retry backoff | Exponential with full jitter. Initial interval **1** s, multiplier **2**, maximum interval **60** s, maximum attempts **5** (or until deadline). |
| `DV-RTB-AUDIT-RETENTION-DAYS` | Audit retention | **14** days of metadata only; no credentials, content, or full request/response bodies. |
| `DV-RTB-MIN-SDK` | Minimum Android SDK | **26** (raise to 28 if required by chosen Keystore or foreground-service features). |
| `DV-RTB-TARGET-SDK` | Target Android SDK | Latest stable at implementation time (**34** or **35**). |
| `DV-RTB-FGS-TYPE` | Foreground-service type | The type appropriate for a long-running local HTTP service with an optional outbound WebSocket (`specialUse` or the closest matching type). Exact type recorded at implementation time. |
| `DV-RTB-GITHUB-API-VERSION` | GitHub REST API version header | A concrete, currently supported date-based version (e.g. `2022-11-28` or later supported equivalent). Recorded at implementation time; never floats on “latest”. |
| `DV-RTB-RELAY-SEQ-WINDOW` | Relay monotonic `seq` window | **64** sequence numbers per device session (`RTB-D17`). |
| `DV-RTB-RELAY-EXPIRY-S` | Relay maximum request lifetime | **300** seconds (5 minutes) (`RTB-D17`). |
| `DV-RTB-PROTECTED-BRANCHES` | Default protected branches | `main`, `master` (`RTB-D15`). |
| `DV-RTB-PROTECTED-PATHS` | Default protected paths | `.github/workflows/**`, `.git/**`, `.gitana/**`, and policy/configuration files unless explicitly enabled (`RTB-D15`). |

**Notes**

- All byte limits are hard ceilings. Implementations MUST reject oversized inputs before parsing or buffering.
- Timeouts are independent; the tightest applicable deadline wins.
- Rate limits, concurrency, and retention values are starting points. Raise or lower them only after measurement on target devices and after confirming they do not create denial-of-service or resource-exhaustion risks.
- Port, credential, confirmation, queue, and relay-profile defaults implement the corresponding owner decisions recorded in Appendix E (`RTB-D01` … `RTB-D17`).

## Appendix E: Decision log

Owner decisions recorded on 25 November 2026 (`RTB-D01`–`RTB-D09`) and 26 November 2026 (`RTB-D10`–`RTB-D17`). Each decision has an identifier so that companion specifications can cite it without restating it.

| ID | Decision | Chosen value / policy |
|---|---|---|
| `RTB-D01` | Local HTTP port | Configurable non-privileged port. Actual bound address and port always shown in the UI. Never bind below 1024. See `DV-RTB-PORT`. |
| `RTB-D02` | Local authentication and pairing | Independently provisioned high-entropy bearer token (at least 256 bits of entropy, 32-byte random, base64url). A short-lived pairing and bootstrap flow issues the long-lived token. Stored in Keystore-backed private app storage. Independently revocable and rotatable. Never placed in URLs, query strings, command-line arguments, shell history or logs. See `DV-RTB-TOKEN-ENTROPY-BITS`, `DV-RTB-TOKEN-ENCODING`. |
| `RTB-D03` | IPv6 loopback | Support `::1` when the server stack and target shell runtimes can use it. Still bind only to loopback addresses, never `::` or any non-loopback interface. See `DV-RTB-IPV6`. |
| `RTB-D04` | Relay security profile | Trusted-relay profile for the initial release. Explicitly document that the relay can see and potentially modify the frames it terminates. The end-to-end protected profile is deferred until full design and independent cryptographic review. See `DV-RTB-RELAY-PROFILE`. |
| `RTB-D05` | Confirmation defaults | `delete_file` requires user confirmation by default. `create_pull_request` requires user confirmation by default, user-configurable later. See `DV-RTB-CONFIRM-DELETE`, `DV-RTB-CONFIRM-PR`. |
| `RTB-D06` | Offline queue policy | No write-payload queueing. Retain only bounded lifecycle metadata and reconcile unknown outcomes on reconnect. See `DV-RTB-QUEUE`. |
| `RTB-D07` | Numeric limits | Conservative starting defaults, tuned after device testing. Request body and file size 1–4 MiB. Response size 4–8 MiB. WebSocket frame 1 MiB. Concurrent in-flight requests 2–4. Read and write timeouts 30–60 s. Rate limits modest per-principal and global, approximately 30 requests per minute for the local path. Backoff exponential with jitter, capped. Audit retention 7–30 days of metadata only, no content. See Appendix D. |
| `RTB-D08` | Android SDK and foreground-service type | `minSdk` 26, or 28 if Keystore or foreground-service features require it. `targetSdk` the latest stable (34 or 35). Use the foreground-service type that matches a long-running local HTTP service with an optional WebSocket; the exact type chosen is documented. See `DV-RTB-MIN-SDK`, `DV-RTB-TARGET-SDK`, `DV-RTB-FGS-TYPE`. |
| `RTB-D09` | GitHub REST API version header | Pin to a concrete, currently supported date-based version and record the exact value at implementation time. Do not float on "latest". See `DV-RTB-GITHUB-API-VERSION`. |
| `RTB-D10` | Local transport response model | `list_branches` and `read_file` return synchronous `200` (`ResponseEnvelope`); `create_branch`, `write_file`, `delete_file`, and `create_pull_request` return `202` + poll (`AcceptedResponse`). Transport-level failures without a `request_id` return `TransportError`. |
| `RTB-D11` | Lifecycle states & cancel route | `MANUAL_REVIEW` and `CANCELLED` are first-class states; `POST /api/v1/requests/{request_id}/cancel` is supported and idempotent for pre-dispatch states (`RECEIVED`, `AUTHORIZED`, `AWAITING_APPROVAL`) and `MANUAL_REVIEW`. `OUTCOME_UNKNOWN` transitions to `RECONCILING` or `MANUAL_REVIEW`. |
| `RTB-D12` | `create_pull_request` rules | `head` accepts `branch` and `owner:branch`. Equivalent open PRs return `SUCCEEDED` idempotently with `already_existed: true` and a `note`. |
| `RTB-D13` | Repository name canonicalization | Normalize `owner/repo` to lowercase; accept current canonical name only (no automatic rename following); reject any other form with a typed error. |
| `RTB-D14` | RTB-49 path normalization | Deterministic 8-step normalization algorithm with root-escape rejection and case-sensitive containment checks; verified against `schemas/path-normalization-test-vectors.v1.json`. |
| `RTB-D15` | Default protected branches & paths | Protected branches: `main`, `master`. Protected paths: `.github/workflows/**`, `.git/**`, `.gitana/**`, and policy/config files unless explicitly enabled. |
| `RTB-D16` | Error catalog & retry bounds | Full 18-code catalog in `schemas/error.v1.json` and `ErrorCode.kt`; backoff `1 s` initial, `60 s` max, exponential with full jitter (`DV-RTB-BACKOFF`); `OUTCOME_UNKNOWN` stays `retryable: false`. |
| `RTB-D17` | Relay transport parameters | Trusted-relay profile only (E2E deferred); `seq` window `64` (`DV-RTB-RELAY-SEQ-WINDOW`); max request expiry `5 minutes` / `300 s` (`DV-RTB-RELAY-EXPIRY-S`); replay detection mandatory; expired requests never execute after reconnect. |
