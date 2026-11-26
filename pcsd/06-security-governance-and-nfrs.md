# 06. Security, Governance, NFRs & Acceptance Gates

> Part of [`GTN-PCS-001`](./README.md) · Covers Sections 11, 12, and 14.

---

## 11. Security, Privacy, and Governance Requirements

- **SEC-01 — Repository isolation:** A task or worker `MUST` access only the repository/workspace and resources explicitly authorized for that execution.
- **SEC-02 — Least privilege:** Workers `MUST` receive the minimum permissions and context required. Permissions `MUST` be scoped and revocable.
- **SEC-03 — Explicit consequential authorization:** Destructive, external-write, publication, credential, or other high-impact actions `MUST` follow explicit policy and user-approval requirements.
- **SEC-04 — Secret separation:** Credentials `MUST` be isolated from task content, worker-visible context, ordinary logs, exported task packages, and unauthorized artifacts.
- **SEC-05 — Fail-closed policy:** If authorization, path validation, policy evaluation, or artifact validation is ambiguous or fails, the operation `MUST` be denied or held for review.
- **SEC-06 — No implicit persistence:** Bridge request data, tool inputs, responses, and outputs `MUST NOT` be persisted by default. The sole specified exception is user-activated `SAVE-SESSION-CONTENT`, implemented exclusively in the Android bridge tool. The relay `MUST NOT` persist this content.
- **SEC-07 — Audit minimization:** Audit events `SHOULD` record security-relevant metadata and outcomes without copying sensitive payloads unnecessarily.
- **SEC-08 — Provenance integrity:** Provenance records `SHOULD` identify source inputs, worker, activities, tools, versions, and generated outputs; unverified worker claims must be distinguished from Gitana-verified facts.
- **SEC-09 — Safe import:** Imported task packages, profiles, templates, and artifacts `MUST` be treated as untrusted until validated.
- **SEC-10 — Safe recovery:** Recovery and retry `MUST` be idempotent where possible and `MUST` prevent duplicate consequential side effects.
- **SEC-11 — Retention and deletion:** Retention periods and deletion behavior `MUST` be explicit, inspectable, and consistent with user authorization and applicable obligations.
- **SEC-12 — Human oversight:** High-impact or domain-regulated workflows `MUST` define human review and approval checkpoints; Gitana must not imply professional certification.

### 11.1 Existing bridge decisions to preserve

- **Loopback service binding:** IPv4 loopback `127.0.0.1` is mandatory; IPv6 loopback `::1` is also bound when the runtime supports it. No wildcard or non-loopback binding is permitted.
- **Persistence:** `SAVE-SESSION-CONTENT` is the only exception to the default no-persistence rule; it belongs exclusively to the Android bridge and is unavailable to the relay.
- **Path handling:** canonical path normalization must be deterministic, NFC-aware, preserve case, avoid inappropriate URI decoding, resolve/contain symlinks safely, and fail closed on root escape.
- **Component boundaries:** bridge, relay, and other components communicate through shared versioned contracts rather than importing one another's private implementation details or credentials.

---

## 12. Non-Functional Requirements

| ID / quality attribute | Requirement |
|---|---|
| NFR-01 — Responsiveness | Common navigation and local metadata operations should remain responsive under representative device load. Long-running work must be asynchronous and visibly cancellable. |
| NFR-02 — Reliability | Task state transitions and artifact registration must survive process interruption without silently losing or duplicating committed state. |
| NFR-03 — Recoverability | The app must detect interrupted executions and present safe resume, retry, inspect, or abandon actions. |
| NFR-04 — Portability | Task contracts and eligible artifacts must use documented, versioned formats; export must not embed secrets or unauthorized session content. |
| NFR-05 — Interoperability | Core task and worker contracts must be independent of a specific model, agent vendor, relay, or Git hosting provider. |
| NFR-06 — Security | Enforce isolation, least privilege, input validation, secure secret handling, and fail-closed authorization. |
| NFR-07 — Privacy | Collect and retain only what is needed for the user-authorized purpose; default to no session-content persistence. |
| NFR-08 — Observability | Expose understandable status, diagnostics, and event history without leaking protected payloads. |
| NFR-09 — Accessibility | Support Android accessibility services, scalable text, adequate contrast, and accessible touch targets. |
| NFR-10 — Extensibility | Profiles, worker adapters, validators, and integrations must be versioned and isolated behind contracts. |
| NFR-11 — Maintainability | Product domains and modules should have clear ownership, testable interfaces, and low coupling. |
| NFR-12 — Resource awareness | Execution and background activity must respect device battery, memory, storage, thermal, and OS restrictions. |
| NFR-13 — Internationalization | User-facing text, date/time, number, and locale behavior should be localizable; task data should avoid locale-dependent ambiguity. |
| NFR-14 — Testability | Critical state transitions, permission decisions, contract validation, and recovery paths must be covered by automated tests. |

---

## 14. Cross-Cutting Acceptance Gates

- **AC-01 — Repository boundary:** Given two registered repositories, a task and worker authorized for repository A cannot read or modify repository B unless a separate explicit authorization is granted.
- **AC-02 — Worker substitution:** Given a task contract supported by two workers, Gitana can assign the task to either worker without changing the task's objective, acceptance criteria, or canonical identity.
- **AC-03 — Task ownership:** After worker completion or failure, Gitana retains the authoritative task state, task specification, execution record, and artifact references.
- **AC-04 — Verifiable completion:** A worker success response with missing required outputs or failed acceptance checks cannot transition the task to `COMPLETED`.
- **AC-05 — Human authorization:** A protected operation is blocked until the required user authorization is obtained; a worker cannot self-approve.
- **AC-06 — No implicit session persistence:** With `SAVE-SESSION-CONTENT` disabled, bridge request and response content is not written to durable storage. The relay never stores that content.
- **AC-07 — Explicit save mode boundary:** When `SAVE-SESSION-CONTENT` is enabled by the user, only the Android bridge feature may persist the content within its separately specified scope; relay persistence remains prohibited.
- **AC-08 — Safe interruption:** After process termination during an execution, Gitana identifies the interruption and offers a safe recovery decision without blindly repeating non-idempotent side effects.
- **AC-09 — Portable task package:** An exported task package validates on import and excludes credentials, unauthorized context, and content excluded by retention policy.
- **AC-10 — Provenance:** For a completed task, a reviewer can identify the task inputs, worker, execution activity, output artifacts, validators, and approvals to the extent captured by policy.
- **AC-11 — Extension isolation:** A profile or worker adapter cannot bypass core permission checks, repository isolation, or user authorization.
- **AC-12 — Fail-closed validation:** Malformed contracts, unsupported required schema versions, invalid paths, or ambiguous authorization decisions are rejected or held for review.
