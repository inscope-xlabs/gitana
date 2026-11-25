# Remote Repository Tool Bridge — Version 2

**Contract-First Android GitHub Executor**

**Status:** Review draft  |  **Date:** 24 November 2026

Remote Repository Tool Bridge

Version 2 — Contract-First Android GitHub Executor

Architecture and implementation specification
Version: 2.0-draft  |  Status: Review draft  |  Date: 24 November 2026

Purpose: define a self-contained Android component that exposes a stable, contract-governed local interface and optional remote transports, while keeping GitHub credentials, repository authorization, and execution policy inside the Android component.

Design decision: the component MUST bind a local HTTP interface to loopback for same-device shell agents. This local interface is independent of the Cloudflare relay and MUST NOT reuse relay credentials or agent-side relay credentials.

## Document control

| Item | Value |
| --- | --- |
| Document | Remote Repository Tool Bridge — Version 2 |
| Supersedes | Version 1 framework overview |
| Status | Draft for technical review; not an implementation sign-off |
| Primary platform | Android |
| Remote Git provider | GitHub REST API |
| Optional remote transport | Cloudflare Worker + Durable Object relay |
| Local transport | Loopback HTTP interface; optional local WebSocket only if separately specified |
| Normative language | MUST / MUST NOT / SHOULD / SHOULD NOT / MAY are requirements keywords |

## 1. Executive summary

Version 2 retains the original architecture's core idea: an Android device acts as a narrowly scoped GitHub API executor. It does not clone repositories, run Git, maintain a working tree, or expose a shell. It reads and changes remote repository state through explicitly supported GitHub REST operations.

The component is self-contained by contract. Its core does not depend directly on Cloudflare, an AI agent, a shell runtime, or a particular agent protocol. External communication is implemented by adapters that consume and produce versioned shared contracts. The same application service and authorization policy are used regardless of transport.

The Android component provides a loopback-bound local interface for agents running locally via shell. A separate optional relay adapter provides remote access. Local-agent authentication, relay-device authentication, and remote-agent authentication are distinct trust domains with distinct credentials and lifecycle controls.

The phrase “self-contained” means independently deployable and governed by its own published contracts, configuration, authorization policy, and lifecycle. It does not mean that all transport or protocol code must be in one class or that external dependencies are eliminated.

## 2. Goals and non-goals

### 2.1 Goals

Expose a small, explicit, versioned tool surface for reading branches/files and creating branches, commits, deletions, and pull requests.

Support same-device shell agents through a loopback-only interface.

Optionally support remote agents through a relay adapter without coupling the core to Cloudflare.

Keep GitHub PATs and repository authorization inside the Android component.

Make every external boundary contract-driven and independently testable.

Prevent unauthorized repository, branch, path, and operation access even when an agent is compromised or prompt-injected.

Provide deterministic request tracking, duplicate suppression, explicit retry semantics, auditability, and bounded resource use.

Keep no local Git working tree and no persistent repository-content cache.

### 2.2 Non-goals

Running Git, cloning repositories, checking out branches, executing repository scripts, or providing shell access.

Arbitrary HTTP proxying or arbitrary GitHub API passthrough.

Guaranteeing uninterrupted Android execution or permanent WebSocket connectivity.

Guaranteeing that an external agent, its provider, or its orchestration platform does not retain returned repository content.

Treating the relay as trusted or untrusted by implication; its trust level is an explicit deployment choice.

Supporting every GitHub API feature in the initial release.

## 3. Architectural principles

| Principle | Requirement |
| --- | --- |
| Contract-first boundaries | Every external interface MUST be defined by a versioned, transport-neutral contract. Adapters MUST translate transport messages to internal application commands; core logic MUST NOT import relay or agent SDK types. |
| Self-contained component | The Android component owns configuration, credentials, authorization policy, request lifecycle, GitHub client, audit policy, and its published local/remote contract implementations. |
| Loopback availability | A local HTTP endpoint MUST bind only to 127.0.0.1 and, where supported and needed, ::1. It MUST NOT bind to 0.0.0.0, ::, a LAN interface, or a public interface. |
| Credential separation | Local client credentials, relay-device credentials, remote-agent credentials, and GitHub PATs MUST be independent credentials. No credential may be reused across trust domains. |
| Least privilege | The phone MUST enforce repository, operation, branch, and path policy before contacting GitHub. |
| No working tree | The component MUST use GitHub APIs directly and MUST NOT clone, checkout, or maintain a Git working tree. |
| No persistent content cache | Repository file contents and API response bodies MUST NOT be written to persistent storage except where the user explicitly enables a separately specified feature. |
| Fail closed | Unknown contract versions, unknown operations, missing authentication, invalid authorization, malformed payloads, and policy uncertainty MUST fail closed. |
| Observable but private | Audit events MUST record action metadata and outcomes without recording credentials, content, or complete request/response bodies. |

## 4. Component boundary and contract model

## 4.1 Logical component

The Android application is one independently deployable component with internal modules. Internal modules may be separate Gradle modules, packages, or processes, but their interfaces are defined by internal contracts. No external component is allowed to reach into internal implementation details.

| Internal module | Responsibility | Depends on |
| --- | --- | --- |
| Contract Registry | Owns schemas, version negotiation, compatibility rules, error model, and protocol documentation. | No transport or provider SDK |
| Application Core | Validates commands, coordinates authorization, request lifecycle, execution, and audit events. | Internal interfaces only |
| Policy Engine | Enforces approved repositories, operation permissions, branch rules, path rules, size limits, and confirmation requirements. | Policy contract |
| Credential Vault | Encrypts and retrieves secrets using Android Keystore-backed cryptographic keys. | Android Keystore and private app storage |
| GitHub Adapter | Maps approved internal commands to specific GitHub REST calls and normalizes results. | GitHub REST API |
| Localhost Adapter | Exposes the local contract over loopback HTTP for same-device clients. | Shared external contract |
| Relay Adapter (optional) | Maintains outbound connection to a configured relay and maps relay envelopes to the same internal contract. | Shared external contract; relay-specific transport contract |
| Audit/Diagnostics | Records bounded metadata and operational diagnostics. | Audit event contract |

## 4.2 Contract-first rule

All communication crossing a component boundary MUST use a published contract. A contract defines: identity, version, authentication context, request envelope, response envelope, error taxonomy, limits, timeout behavior, retry semantics, and compatibility rules. The Android core MUST NOT contain Cloudflare-specific concepts, agent-vendor-specific tool schemas, or shell-agent-specific code.

Transport adapters MAY use different wire encodings, but they MUST map to the same canonical command and result model. The contract repository/specification is the source of truth; generated schemas, SDKs, and tool definitions are derived artifacts.

## 4.3 Contract set

| Contract | Purpose | Required fields / topics |
| --- | --- | --- |
| Tool Contract | Canonical operation definitions. | Operation names, inputs, output schemas, constraints, side effects. |
| Local Transport Contract | Loopback HTTP request/response behavior. | Base path, version, auth, envelope, limits, errors, timeout. |
| Relay Transport Contract | Phone-to-relay and agent-to-relay message envelope. | Device identity, session binding, request ID, correlation, expiry, integrity, acknowledgements. |
| Authorization Contract | Policy decisions for every operation. | Principal, repo, operation, branch, path, decision, reason, confirmation requirement. |
| Credential Contract | Credential types and lifecycle. | Issuer, subject, scope, expiry, rotation, revocation, storage class. |
| Execution Lifecycle Contract | Request states and delivery guarantees. | Accepted, executing, succeeded, failed, outcome-unknown, cancelled, expired. |
| Audit Event Contract | Local audit and optional sanitized telemetry. | Timestamp, principal, request ID, operation, target, outcome, commit/PR identifiers. |

Contract versions MUST be explicit (for example, /v1 or a schema version field). A breaking change requires a new major contract version. Minor additions MUST be backward-compatible and unknown fields MUST be handled according to the schema's compatibility rules.

## 5. Deployment and networking

## 5.1 Localhost interface — mandatory

The Android component MUST expose a local HTTP interface for same-device agents that communicate through shell or local tool adapters. The server MUST bind to loopback only. Recommended default: 127.0.0.1 on a configurable, non-privileged port. IPv6 loopback (::1) MAY also be supported, but the server MUST NOT bind to wildcard addresses.

Default listener: http://127.0.0.1:<configured-port>/api/v1/.

Health/readiness endpoints MUST reveal no credentials, repository contents, or sensitive configuration.

Only explicitly documented routes and methods are allowed; no generic proxy or arbitrary path forwarding.

Use strict request-size limits, read/write timeouts, connection limits, and rate limits.

Reject Host headers and origin patterns not permitted by the local transport contract where relevant.

Do not enable permissive CORS. Shell clients do not require browser CORS; browser access, if ever supported, requires a separate threat review.

Authentication MUST be required even on loopback. Loopback is a network boundary, not proof of user identity.

Use a local-client credential distinct from all relay and GitHub credentials. It MUST be provisioned and revocable independently.

Do not put credentials in URLs, query strings, command-line arguments, shell history, or logs. Prefer an OS-protected local credential file/secret channel or a short-lived token provisioned through an explicit pairing flow.

Where the Android environment supports OS-level peer identity or Unix-domain socket permissions, these MAY supplement application authentication but MUST NOT be assumed available across all shell runtimes.

Important platform limitation: 127.0.0.1 is scoped to a network namespace/device environment. A shell agent running in Termux or another app may or may not be able to reach the Android app's loopback listener depending on Android isolation, VPNs, user profiles, and runtime arrangement. This MUST be validated on target devices. The component MUST NOT solve reachability by silently binding to the LAN; any non-loopback exposure would require a separate opt-in design, threat model, and contract.

## 5.2 Remote relay interface — optional and isolated

The relay is an optional transport adapter. Its absence MUST NOT prevent the local interface from operating. The core MUST NOT require a relay session to accept local requests.

The Android device initiates the outbound TLS WebSocket connection; the phone does not open an inbound public port.

The relay adapter uses a device-specific relay credential, not the local-client credential or GitHub PAT.

Remote-agent authentication is owned by the relay/agent boundary and MUST NOT be passed into the core as a raw bearer secret. The relay adapter receives a verified principal assertion under the relay contract.

Device/session routing, authorization of the remote principal, message expiry, replay detection, and request correlation MUST be explicit in the relay contract.

The relay may maintain transient connection/session state. It MUST NOT persist repository content, GitHub credentials, or full request/response bodies.

Unless end-to-end message integrity/confidentiality is implemented and independently reviewed, the relay MUST be treated as trusted to see and potentially alter the frames it terminates.

Relay transport failure MUST NOT disable or alter localhost operation.

## 5.3 Local and remote separation

| Concern | Localhost path | Remote relay path |
| --- | --- | --- |
| Transport credential | Local-client credential | Device relay credential plus remote-agent identity assertion |
| GitHub PAT | Never exposed; retrieved internally | Never exposed; retrieved internally |
| Authentication authority | Local pairing/provisioning authority | Relay/agent identity authority |
| Network path | Loopback only | Outbound WSS from phone; agent to relay |
| Failure independence | Must work without relay | May be unavailable without affecting local path |
| Core command model | Canonical Tool Contract | Same canonical Tool Contract |

## 6. Security architecture

## 6.1 Credential classes

| Credential | Purpose | Storage / handling | Must not be shared with |
| --- | --- | --- | --- |
| GitHub fine-grained PAT | GitHub API authorization. | Encrypted at rest using a Keystore-protected key; plaintext only briefly in memory; never logged or returned. | Any agent, relay, local client. |
| Local-client credential | Authenticate same-device shell clients. | Independently provisioned, scoped, revocable; store securely outside command history. | Relay and GitHub. |
| Relay-device credential | Authenticate phone to relay. | Keystore-backed encryption; rotation and revocation supported. | Local clients and GitHub. |
| Remote-agent credential | Authenticate agent to relay. | Owned by agent/relay deployment; not stored as a GitHub secret on the phone. | Local clients and GitHub. |
| Derived session key (if E2E enabled) | Protect message integrity/confidentiality across relay. | Short-lived, bound to device and agent identities; rotate on session renewal. | Other sessions or credential classes. |

Android Keystore protects cryptographic key material; it is not a general-purpose string database. The PAT and relay secret MUST be encrypted using a Keystore-backed key and stored as ciphertext in private app storage. Exclude this storage from Android backup and device-transfer paths. Minimize plaintext lifetime and avoid immutable copies where practical.

## 6.2 Authentication and authorization

Authenticate the transport principal at the adapter boundary.

Map that principal to a canonical principal identity and permitted trust domain.

Validate the request envelope, contract version, expiry, request ID, and schema.

Resolve and canonicalize the repository identifier, branch, and path.

Evaluate repository allow-list and operation-level policy.

Evaluate branch and path restrictions, including protected paths and workflow files.

Determine whether user confirmation is required; if so, suspend execution pending explicit approval.

Only then dispatch the approved command to the GitHub adapter.

Authorization MUST be enforced on the phone for every operation. The agent's tool schema, relay routing, and PAT permissions are defense-in-depth, not substitutes for local policy.

## 6.3 Prompt injection and agent compromise

Repository content is untrusted data, never policy or executable instruction.

The phone MUST NOT infer authorization from text returned by GitHub or from agent-generated explanations.

Read and write permissions MUST be independently configurable.

Destructive operations (delete_file, branch deletion if later added, and other destructive tools) SHOULD require user confirmation by default.

Pull-request creation SHOULD be separately configurable for automatic versus confirmation-required execution.

Default-deny sensitive paths, including .github/workflows/**, repository secrets/configuration locations, and policy-defined protected files.

Branch restrictions MUST be evaluated against normalized branch names; protected branches are non-writable unless explicitly and narrowly authorized.

Agent principals SHOULD have per-agent scopes, quotas, and revocation.

## 6.4 Relay trust model

TLS/WSS protects the transport between each endpoint and the relay, but does not provide end-to-end protection through a relay that terminates both connections. Version 2 therefore defines two deployment profiles:

| Profile | Security property | Requirement |
| --- | --- | --- |
| Trusted relay profile | Relay can see and potentially modify messages; it is trusted as a transport intermediary. | Document this trust assumption, restrict relay operators/access, minimize logging, and authenticate phone-side requests and responses. |
| End-to-end protected profile | Relay forwards opaque, authenticated/encrypted envelopes and cannot forge valid messages or read protected payloads. | Define key agreement, identity binding, nonce/sequence rules, authenticated encryption, key rotation, replay defense, and recovery. Require independent cryptographic review before production. |

Do not claim that a relay cannot forge results unless the end-to-end protected profile is implemented and verified.

## 7. Canonical tool surface

The initial public tool surface remains intentionally small. All tool definitions are transport-neutral and published through the Tool Contract.

| Operation | Inputs | Behavior and GitHub mapping |
| --- | --- | --- |
| list_branches | repo; optional page and per_page | GET /repos/{owner}/{repo}/branches; paginate; enforce repository allow-list. |
| create_branch | repo, base, new_branch | Resolve base branch to commit SHA, then POST /repos/{owner}/{repo}/git/refs with ref=refs/heads/{new_branch} and sha=<resolved SHA>. |
| read_file | repo, path, ref | GET /repos/{owner}/{repo}/contents/{path}; decode supported text content; enforce file/response limits. |
| write_file | repo, path, content, branch, message | Resolve existing file SHA when updating; omit SHA only for creation; encode content as required by API; handle conflicts and stale branch heads. |
| delete_file | repo, path, branch, message | Resolve current file SHA, then DELETE contents endpoint; handle conflict and unknown completion. |
| create_pull_request | repo, head, base, title; optional body | POST /repos/{owner}/{repo}/pulls; define head owner/branch syntax, detect existing equivalent PRs, and handle permission errors. |

The phone MUST resolve repository owner/name from a canonical approved-repository record rather than accepting arbitrary URL fragments. No operation may accept an arbitrary endpoint, host, HTTP method, or raw GitHub API path.

## 7.1 GitHub correctness requirements

File updates require the current blob/file SHA; file creation does not. Resolve internally unless the public contract explicitly exposes a conditional-update token.

File deletion requires the current file SHA.

Branch creation requires a commit SHA. Resolve the selected base branch to its current commit SHA before creating refs/heads/<new_branch>.

Handle empty repositories, missing branches, missing files, non-text/binary files, and oversized content explicitly.

Contents API responses and requests require correct Base64 handling where applicable.

Paginate list operations and enforce bounded page sizes.

Handle rate limits and secondary rate limits with bounded backoff and Retry-After where supplied.

Normalize upstream errors; do not return raw upstream messages without review for sensitive details.

Pin and document the GitHub REST API version header and review it during upgrades.

Serialize or otherwise coordinate conflicting writes to the same repository/path; handle GitHub conflict responses.

## 7.2 Repository and path policy

At provisioning time, the user selects approved repositories and configures operation permissions. Each request MUST be checked against that allow-list before GitHub is contacted. Path policy MUST use canonical path normalization and reject traversal, ambiguous encodings, invalid separators, and policy bypasses. Prefix-only string checks are insufficient.

Default policy: deny writes to protected branches; deny .github/workflows/**; deny policy/configuration paths unless explicitly enabled; allow only configured branches and operations. Policy changes require explicit user action and are auditable.

## 8. Request lifecycle, delivery, and idempotency

## 8.1 Delivery model

The system MUST NOT claim exactly-once delivery across a network connection. The achievable design is at-least-once transport with deduplication and explicit outcome tracking, or at-most-once dispatch with possible unknown outcomes. The contract MUST state which behavior applies to each operation.

## 8.2 Request envelope

Every command MUST include a globally unique request_id, contract_version, operation, issued_at, expires_at, principal, and typed arguments. Relay envelopes additionally include device/session binding and transport sequence/correlation data. Authentication material MUST be outside the ordinary tool arguments.

## 8.3 State machine

| State | Meaning | Allowed transition |
| --- | --- | --- |
| RECEIVED | Envelope accepted and schema-validated. | AUTHORIZED, REJECTED, EXPIRED |
| AUTHORIZED | Policy passed; confirmation may still be pending. | AWAITING_APPROVAL, EXECUTING, REJECTED |
| AWAITING_APPROVAL | User confirmation required. | EXECUTING, REJECTED, EXPIRED |
| EXECUTING | GitHub operation dispatched. | SUCCEEDED, FAILED, OUTCOME_UNKNOWN |
| SUCCEEDED | GitHub result confirmed and recorded. | Terminal |
| FAILED | Operation definitively failed. | Terminal |
| OUTCOME_UNKNOWN | Request may have succeeded but acknowledgement/result is unavailable. | RECONCILING, MANUAL_REVIEW |
| RECONCILING | Safe read-back or provider lookup is being used to determine outcome. | SUCCEEDED, FAILED, MANUAL_REVIEW |
| EXPIRED / REJECTED | Not executed. | Terminal |

Persist only the minimum request metadata needed to prevent duplicate side effects and reconcile uncertain outcomes. Do not persist request bodies containing file content unless an explicitly approved, encrypted, bounded queue design is added. A queue must have expiry, size limits, deletion rules, and user-visible status.

## 8.4 Retry rules

Requests not yet dispatched may be retried if still valid and authorized.

Requests dispatched to GitHub but lacking a confirmed response MUST enter OUTCOME_UNKNOWN; do not blindly replay.

Before retrying a write, reconcile by reading the target branch/file/commit state or checking the relevant pull request.

Use request_id and an operation fingerprint for duplicate detection. Do not treat a client-supplied idempotency key as authorization.

Retries MUST be bounded and use exponential backoff with jitter.

Expired requests MUST NOT execute after reconnection.

## 9. Android runtime and lifecycle

## 9.1 Foreground service

The component uses a user-started foreground service when continuous local/remote availability is required. A foreground service improves execution continuity but does not guarantee uninterrupted operation. Implement the service type and permissions required by the target Android API level and the actual use case; do not declare an unrelated service type merely to bypass platform restrictions.

Start through an explicit user action and show a persistent notification with meaningful controls.

Handle notification permission behavior and foreground-service start restrictions for supported Android versions.

Handle process death, force-stop, reboot, app update, permission changes, and user stop distinctly.

Do not promise automatic restart after force-stop; explain that user action may be required.

Treat local HTTP availability and relay connectivity as separate service health states.

## 9.2 Network and Doze

Observe network availability and validate actual connectivity; network callbacks alone do not prove internet reachability.

Use heartbeat and timeout values configurable for the relay deployment.

Detect half-open WebSockets using ping/pong or application-level heartbeat.

Use exponential backoff with jitter and a maximum interval.

Do not assume immediate callbacks or immediate reconnection when Doze ends.

Handle Wi-Fi/mobile transitions, DNS errors, TLS errors, captive portals, and temporary offline states.

Localhost requests may remain usable even when the remote relay is disconnected.

## 9.3 Ktor roles

Ktor is used as the local HTTP server implementation, bound to loopback only. The remote relay connection is a separate outbound WebSocket client. These are separate adapters and must not be described as one server socket. The local HTTP server is not exposed through the relay and is not required for the relay adapter to function.

## 9.4 Local shell-agent compatibility

Provide a small documented CLI example or shell-compatible HTTP usage contract. The CLI, if supplied, is a client of the localhost contract and is not part of the GitHub execution core. It must obtain the local credential through a secure provisioning mechanism and must not embed secrets in scripts, command-line arguments, shell history, or environment dumps. Validate loopback reachability from each supported shell runtime before claiming support.

## 10. Resource limits and data handling

| Resource | Initial policy requirement |
| --- | --- |
| Request body | Hard configurable maximum; reject before parsing oversized bodies. |
| File content | Hard configurable maximum; reject unsupported binary or oversized content. |
| Response body | Bounded; do not buffer unbounded GitHub responses. |
| WebSocket frame | Bounded maximum frame size; reject oversized frames. |
| Concurrency | Bounded global and per-repository concurrency; serialize conflicting path writes. |
| Timeouts | Separate connect, GitHub request, local request, and end-to-end deadlines. |
| Queue | Bounded count/bytes/age; no unbounded offline queue. |
| Logs | No PATs, relay/local tokens, file content, full bodies, or authorization headers. |
| Temporary memory | Minimize copies; clear mutable secret buffers where practical; never claim guaranteed zeroization on managed runtimes. |

Repository content necessarily exists transiently in phone memory while being processed and in transit through the relay when remote transport is used. The guarantee is no persistent content cache or working tree, not zero memory residency. Returned content may be retained by the requesting agent under that agent's own policies.

## 11. Audit, diagnostics, and privacy

Maintain a local audit record for each accepted operation. Recommended fields: timestamp, request_id, principal identifier, transport class (local/relay), repository, operation, normalized branch/path where applicable, decision, outcome, GitHub status category, commit SHA, and pull-request number. Do not record file contents, credentials, full request bodies, or complete upstream response bodies.

Audit retention MUST be configurable and documented. Users MUST be able to inspect and clear audit history, subject to any separately disclosed security retention requirements. Diagnostic telemetry, if enabled, must be opt-in or clearly disclosed, minimized, and separately governed.

## 11.1 Error contract

| Field | Meaning |
| --- | --- |
| code | Stable internal error code, e.g. AUTH_FAILED, POLICY_DENIED, RATE_LIMITED, CONFLICT, UPSTREAM_UNAVAILABLE, OUTCOME_UNKNOWN. |
| category | Validation, authentication, authorization, policy, transport, provider, resource-limit, or internal. |
| http_status | Relevant local HTTP status where applicable. |
| retryable | Whether retry is safe and recommended. |
| execution_state | Not started, executing, succeeded, failed, or outcome unknown. |
| message | Sanitized user-readable explanation. |
| correlation_id | Request correlation identifier; never a credential. |

## 12. Provisioning and administration

Install and launch the Android application.

Create or unlock the credential vault using device-protected authentication where available.

Add a fine-grained GitHub PAT with the minimum repository selection and permissions required.

Select the exact repositories the component may access; store canonical owner/repository identifiers.

Configure per-repository read/write permissions, branch rules, protected paths, and confirmation policy.

Enable localhost interface and create/pair a local-client credential independently of relay setup.

Optionally configure the relay URL and provision a distinct device relay credential.

Run a local self-test and, if enabled, a relay connectivity test.

Display local endpoint, supported contract version, service status, and credential rotation/revocation controls without displaying secret values.

Credential rotation MUST support replacing a PAT, local-client credential, and relay-device credential independently. Revocation of one credential class MUST NOT require rotating the others. Lost-device response must include revoking the PAT and relay-device identity.

## 13. Testing and acceptance criteria

| Test area | Acceptance criteria |
| --- | --- |
| Loopback binding | Listener is reachable only through loopback; no LAN/public interface is open; verify IPv4 and IPv6 behavior. |
| Local credential separation | Local credential cannot authenticate to relay; relay credential cannot authenticate to localhost; PAT is accepted by neither transport. |
| Relay independence | Local operations continue when relay is disabled or unreachable. |
| Authorization | Unapproved repository, branch, path, or operation is rejected before any GitHub request. |
| Prompt injection | Malicious README/config content cannot change phone-side policy or bypass approval. |
| GitHub API correctness | Create/update/delete file and branch operations succeed with correct SHA handling; conflicts are reported correctly. |
| Duplicate execution | Disconnect after GitHub commit but before response; reconnect does not blindly execute duplicate side effects. |
| Outcome reconciliation | Unknown write outcome is reconciled safely or escalated for manual review. |
| Android lifecycle | Test supported API levels for foreground service, Doze, process death, reboot, notification behavior, and force-stop. |
| Resource limits | Oversized requests/files/frames are rejected without OOM or unbounded buffering. |
| Privacy | Automated tests and log inspection show no secrets or file content in logs/telemetry. |
| Audit | Every accepted operation has a local audit outcome, including rejection and unknown outcome. |

## 13.1 Release gates

No production release until the local loopback authentication and cross-app reachability model is validated on the actual target Android versions and shell runtimes.

No production release until duplicate/unknown-outcome behavior is tested under forced disconnects.

No production release until repository allow-list and path normalization tests pass.

No production release until the relay trust profile is explicitly selected and documented.

No production release until credential backup exclusion, rotation, and revocation are tested.

## 14. Resolved owner decisions

All items previously listed as open decisions have been resolved. The final choices are recorded in the Decision Log (Appendix D). Corresponding “open decision” / “to be finalized” language has been removed from this document.

## 15. Version 2 summary

Version 2 defines the Android Remote Repository Tool Bridge as a self-contained, contract-first GitHub execution component. It MUST expose a loopback-only local interface for same-device shell agents, and MAY expose an optional outbound relay transport. Both adapters use the same canonical tool and authorization contracts; neither adapter is allowed to bypass the phone-side policy engine.

The local interface is intentionally independent of Cloudflare and remote-agent credentials. Local-client, relay-device, remote-agent, and GitHub credentials are separate identities. The component owns credential handling, repository allow-lists, branch/path rules, approval policy, request lifecycle, GitHub API correctness, and audit behavior.

The system makes no unsupported exactly-once or uninterrupted-service guarantees. It uses request IDs, deduplication, explicit lifecycle states, safe reconciliation of uncertain writes, bounded retries, and clear outcome reporting. It stores no Git working tree and no persistent repository-content cache.

This document is a review-ready architecture specification. Owner decisions (port policy, local authentication, IPv6 loopback, relay security profile, confirmation defaults, offline queue policy, numeric limits starting points, Android SDK levels, and GitHub API version pin) are recorded in Appendix D. Remaining work before an approved baseline is limited to contract schemas, policy detail, validation gates, and document hygiene listed in the companion exit-tasks document.

## Appendix A — Example canonical request envelope

Illustrative JSON shape; final field names and schemas are owned by the Tool Contract.

{
  "contract_version": "1.0",
  "request_id": "urn:uuid:<unique-id>",
  "issued_at": "2026-11-24T20:00:00Z",
  "expires_at": "2026-11-24T20:01:00Z",
  "principal": {
    "id": "local-client:<paired-client-id>",
    "transport": "localhost"
  },
  "operation": "read_file",
  "arguments": {
    "repo": "owner/repository",
    "path": "README.md",
    "ref": "main"
  }
}

## Appendix B — Example localhost routes

| Method | Route | Purpose |
| --- | --- | --- |
| GET | /api/v1/health | Non-sensitive liveness. |
| GET | /api/v1/capabilities | Contract version and supported operations; no secrets. |
| POST | /api/v1/tools/{operation} | Submit a canonical tool request. |
| GET | /api/v1/requests/{request_id} | Read request lifecycle status. |
| POST | /api/v1/requests/{request_id}/cancel | Request cancellation where safe and supported. |

The final Local Transport Contract must define whether request submission is synchronous or returns an accepted request ID for later status polling. Long-running or approval-gated operations SHOULD use accepted-request semantics.

## Appendix C — External reference material

Android Keystore system: https://developer.android.com/privacy-and-security/keystore

Android foreground services: https://developer.android.com/develop/background-work/services/foreground-services

Android Doze and App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby

GitHub REST API — Repository contents: https://docs.github.com/en/rest/repos/contents

GitHub REST API — Git references: https://docs.github.com/en/rest/git/refs

GitHub REST API — Pull requests: https://docs.github.com/en/rest/pulls/pulls

GitHub REST API — Rate limits: https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api

Cloudflare Durable Objects — WebSockets: https://developers.cloudflare.com/durable-objects/best-practices/websockets/

## Appendix D — Decision Log (owner decisions, 25 November 2026)

| # | Decision | Chosen value / policy |
| --- | --- | --- |
| 1 | Local HTTP port | Configurable non-privileged port. Actual bound address:port always shown in UI. Never bind <1024. |
| 2 | Local authentication & pairing | Independently provisioned high-entropy bearer token (≥256 bits entropy, e.g. 32-byte random → base64url). Short-lived pairing/bootstrap flow that issues the long-lived token. Stored in Keystore-backed private app storage. Independently revocable/rotatable. Never placed in URLs, query strings, command-line args, shell history, or logs. |
| 3 | IPv6 loopback | Support `::1` when the server stack and target shell runtimes can use it. Still bind only to loopback addresses (never `::` or any non-loopback interface). |
| 4 | Relay security profile | **Trusted-relay** profile for initial release. Explicitly document that the relay can see and potentially modify frames it terminates. End-to-end protected profile deferred until full design + independent cryptographic review. |
| 5 | Confirmation defaults | `delete_file`: require user confirmation by default.<br>`create_pull_request`: require user confirmation by default (user-configurable later). |
| 6 | Offline queue policy | **No** write-payload queueing. Retain only bounded lifecycle metadata; reconcile unknown outcomes on reconnect. |
| 7 | Numeric limits (starting conservative defaults; tune after device testing) | Request body / file size: 1–4 MiB<br>Response size: 4–8 MiB<br>WebSocket frame: 1 MiB<br>Concurrent in-flight requests: 2–4<br>Read/write timeouts: 30–60 s<br>Rate limits: modest per-principal + global (e.g. \~30 req/min local)<br>Backoff: exponential with jitter, capped<br>Audit retention: 7–30 days of metadata only (no content) |
| 8 | Android SDK & foreground-service type | minSdk 26 (or 28 if Keystore / FGS features require it); targetSdk latest stable (34/35). Use the appropriate foreground-service type for a long-running local HTTP + optional WebSocket service (document the exact type chosen; likely `specialUse` or closest matching type). |
| 9 | GitHub REST API version header | Pin to a concrete, currently supported date-based version (record the exact value at implementation time). Do not float on “latest”. |
