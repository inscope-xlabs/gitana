# Gitana Tool Bridge: Merged Deficiency Plan

Merges the two plans in this directory (`info-6a.txt`, an ordered task list, and `info-6b.txt`, a phased plan with
decisions first) into one. Source references are given as `6a#n` / `6b#n`.

**Goal:** make the sample task run end to end on `example-org/sandbox`: the operator approves a Session Plan for a
task; Gitana creates the task branch and task directory; only then does a session exist and agent access open; the
agent runs `read_file`, `write_file` and `create_pull_request` on Path A (local loopback) and Path B (trusted relay,
session surface map URL); the operator approves each mutation; the session closes. Failure paths are covered by tests.

### End-to-end flow

| # | Step | Actor | Exit condition | Tasks |
|---|---|---|---|---|
| 1 | Create the task (`TaskContract`) | Operator | `validate()` returns no issues | S1 |
| 2 | Review and approve the Session Plan | Operator, on device | Approval recorded against the exact plan | S2, F5 |
| 3 | Create the task branch and task directory | Gitana | Both read back from GitHub | S3 |
| 4 | Create the session and grant; issue the session URL (B) or session bearer (A) to the operator | Gitana | Session `LOCKED` | S4 |
| 5 | Hand over the URL or bearer; confirm pairing | Operator | Session `ACTIVE` | S4, E6 |
| 6 | Open the surface map and read | Agent | Replies stay inside the grant | S5, E5 to E7 |
| 7 | Compose, preview, commit | Agent | Request enters the v2 lifecycle at `RECEIVED` | E7 |
| 8 | Evaluate policy, approve, dispatch | Phone, operator | Terminal state or `OUTCOME_UNKNOWN` | B1 to B9, C1 |
| 9 | Read the result; repeat 7 and 8 for the next step | Agent | Pull request from task branch to base | E7 |
| 10 | Complete, expire or revoke the session | Gitana, operator | No link or bearer works; audit records exist | S6, F2 |

**No agent access exists before step 4 succeeds, on any transport.**

## Ground rules

1. Work only in `inscope-xlabs/gitana`. This plan is governed by v2 only: the v2 specification, the Cloudflare relay
   specification, the Navigation Transport Specification (used here as a v2 companion), the schemas and the
   exit-tasks document. Do not invent behaviour beyond them and the decisions in A9 to A12.
2. **Decisions before code.** A code task whose decision is not yet recorded must not start on a guess. Record the
   decision in the spec and schema first (Phase A).
3. Edit the existing files named below. Do not create files at guessed paths.
4. Fail closed. Stubs that currently deny everything stay that way until their task is complete and tested.
5. A task is done only when its acceptance check passes and, where relevant, the exit-tasks checkbox is ticked.
6. **XGN.** `docs/xgn/` does not govern this plan. Two exceptions: the session rule above (plan, branch and directory
   before access), and Phase E, which follows XGN as closely as possible and records each departure in the Phase E
   deviation table. XGN states it is authoritative for agent ingress (XGN-00 §1), and the Navigation Transport
   Specification is marked as XGN predecessor material; this plan departs from both statements by owner decision.

**Path convention.** `<m>:Name.kt` means
`bridge/<m>/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/<m>/…/Name.kt` for bridge modules, and `app:Name.kt`
means `app/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/…/Name.kt` for the `app` module (the subpackage is
shown where it matters). Tests mirror this under `src/test/kotlin`. Schemas live in
`bridge/contracts/src/main/resources/schemas/`, OpenAPI in `bridge/contracts/src/main/resources/openapi/`.

## Corrections to the source plans

The plans were written without sight of the current repository. Reconciled here:

| Issue | Resolution |
|---|---|
| 6b uses `bridge/contracts/schemas/…`, which does not exist | Schemas are in `bridge/contracts/src/main/resources/schemas/` |
| 6b puts the error catalog in `common.v1.json` | The error enum is in `error.v1.json` (`ErrorCode.kt` is its Kotlin twin) |
| 6a and 6b describe PR #8 as pending | #7, #8 and #9 are merged. The `::1` conflict is now spec §5.1 saying "MAY" while RTB-03 says "MUST where supported" |
| 6a uses `…` for paths; 6b said exact paths were unknown | All files named below exist on `main` (verified) |
| 6b task 6 forbids `MANUAL_REVIEW` and `CANCELLED` edges, but 6b task 15 maps to `MANUAL_REVIEW`, and 6a task 7 requires a cancel route | Decide those edges in A3, then implement them consistently |
| 6b task 16 lists four GitHub operations, 6a task 6 lists all six | All six stubs throw `TODO`. Implement all six |
| 6b task 27 says to locate the audit sink | It is `storage:audit/RoomAuditSink.kt` |
| 6b's ordering note refers to task ranges ("Tasks 6–15", "16–17") that do not match its own phases | Replaced by the dependency table at the end |
| Neither plan mentions `ConfirmationRules.kt`, `GitHubApiVersion.kt`, `Base64Content.kt`, `RequestStorePort` or `PolicyDataStore` | Covered in B5, C1 and B11 |
| 6a task 10 (inconsistency cleanup) has no counterpart in 6b | Kept as A8 |

---

## Phase A: Close the open decisions (blocking)

> **Status: COMPLETED & CLOSED (26 November 2026).** All Phase A decisions (`A1`–`A8`) are recorded in `remote_repository_tool_bridge_v2_specification.md` (`RTB-D10`–`RTB-D17`), `cloudflare_relay_specification.md`, `bridge/contracts/SCHEMAS.md`, `bridge/contracts/src/main/resources/schemas/*.json` (including `path-normalization-test-vectors.v1.json`), `bridge/contracts/src/main/resources/openapi/bridge.v1.yaml`, and synced Kotlin contract twins (`RequestState.kt`, `ErrorCode.kt`, `ToolResult.kt`). Phases B–D are unblocked. **A9–A12 are open** and block Phases S and E. A11 amends A4 and A7.

No code dependencies. A1 to A7 may run in parallel. Each ends with the spec, schema, OpenAPI, `SCHEMAS.md` and
exit-tasks checkboxes updated together.

**A1. RTB-49 canonical path algorithm** (6a#3, 6b#1)
- Write the actual algorithm steps into the RTB-49 row and §7.2 (the row currently ends at "the following
  deterministic algorithm" with no steps). Cover dot segments, `..`, empty segments, leading and trailing `/`,
  backslashes, percent-encoding, NFC, case sensitivity and canonicalize-then-verify-containment-then-operate on the
  same resolved target.
- Add `schemas/path-normalization-test-vectors.v1.json`: each vector has `input`, then `canonical` or `reject`.
  Include the RTB-T10 cases (`src/./main`, `src/lib/../main`, `../../outside`, NFC vs decomposed, `Readme` vs
  `README`, raw `%2e%2e/secret` not decoded, symlink escape, invalid percent-encoding in URI-derived paths).
- Acceptance: no vector is ambiguous; every vector is justified by a step of the algorithm.

**A2. Local Transport Contract and Appendix A** (6a#1, 6b#2)
- Decide synchronous 200 versus 202-plus-poll (per operation; see A3), header set, and the error envelope for
  transport-level failures.
- Fix §5.1 so `::1` matches RTB-03 (MUST where supported, non-fatal on failure).
- Remove "illustrative" from Appendix A and make it validate against `tool-contract.v1.json`.
- Update `local-transport.v1.json`, `bridge.v1.yaml`, `SCHEMAS.md`.
- Acceptance: spec, schema and OpenAPI agree; Appendix A passes the schema.

**A3. Delivery guarantee and lifecycle edges** (6b#3, resolves the 6b conflict)
- Table for all six operations: guarantee (at-most-once, at-least-once or reconcilable), sync-or-poll model,
  reconciliation entry point (RTB-41, RTB-43, RTB-44).
- Decide the transitions for `MANUAL_REVIEW` and `CANCELLED`, and whether the cancel route exists. Mirror into
  `RequestState.kt` and `lifecycle.v1.json`.
- Acceptance: every `OUTCOME_UNKNOWN` has a named reconciliation path; no operation is undecided.

**A4. Error catalog and retry bounds** (6a#1, 6b#4, 6b#17)
- Extend `error.v1.json` and `ErrorCode.kt`; full mapping of code to `category`, `http_status`, `retryable` and
  `execution_state`; update the §11.1 table.
- Decide backoff bounds from `DV-RTB-BACKOFF` (RTB-46).
- Acceptance: every code emitted by Phases B to F exists; `OUTCOME_UNKNOWN` stays `retryable: false`.

**A5. `create_pull_request` rules** (6a#3, 6b#5)
- State the accepted `head` syntax (`branch` or `owner:branch`) and behaviour when an equivalent PR exists (error or
  idempotent return). Update the §7 row and `tool-contract.v1.json` (`CreatePullRequestArgs.head`).

**A6. Default protected lists and allow-list canonicalization** (6a#3, 6b#11)
- Default protected branches and paths (at least `main` and `.github/workflows/**`); policy and config paths denied
  unless enabled.
- Decide repository-name handling: normalize case and follow renames, or reject non-canonical forms.

**A7. Relay Transport Contract** (6a#2)
- Specify device identity, session binding, `seq` window, correlation, expiry, replay detection, acknowledgements and
  principal-assertion format. Trusted-relay profile only; E2E stays deferred.
- Update `cloudflare_relay_specification.md`, `relay-envelope.v1.json`, spec §4.3 and §5.2, exit-tasks.

**A8. Documentation inconsistencies** (6a#10)
- Reconcile lifecycle naming between §4.3 prose and `RequestState`, or add a mapping table.
- State that the Navigation Transport is a separate plane and the sample task does not inherit its session rules
  (cross-reference only).
- Clean the status header and §15 wording.

---

### Added decisions (open)

**A9. Session Plan and bootstrap.** Record in the v2 specification:
- The Session Plan is a record separate from `TaskContract` that refers to `taskId` and `revision`. A task is all that
  is needed to start; the plan is derived from it and the operator approves it.
- Fields: repository, base branch, task branch (default `agent/<task-id>`), task directory (default
  `tasks/<task-id>/`), allowed operations, ordered steps, limits, lifetime, channel. Define the slug rule that turns
  `taskId` into a branch and path segment.
- The task directory is the session's only path scope. Git stores no empty directories, so bootstrap commits
  `<task-dir>/TASK.md` (task ID, revision, title, objective; no credentials). It must not sit under a protected path.
  Decide whether it stays in the pull request.
- Plan approval authorizes exactly the two bootstrap writes. Any plan or task revision change voids it.

**A10. Session and grant.** Session states (Navigation Transport §5), grant contents, grant immutability, lifetime
(2 hours, operator-extendable), operator revocation, and the credential per path: a capability URL on Path B, a
per-session bearer on Path A replacing the long-lived local-client bearer (amends RTB-22, RTB-23 and D2). Amend the
v2 "Navigation Transport boundary" note, which says the sample task does not inherit Navigation Transport session
rules (recorded under A8): under this plan the sample task requires a Session Plan and an active session on both
paths. Session,
draft and capability state stays in memory (RTB-07); a restart ends the session.

**A11. Navigation surface over the relay.** Record the Phase E rules: pairing, capability format, commands, relay frame
types, routes under `/gitana/`, capability routing, limits and the added error codes. Update both relay documents,
`relay-envelope.v1.json`, `error.v1.json`, `ErrorCode.kt` and `SCHEMAS.md`.

**A12. Relay service location.** Choose the directory, language, build and deployment method for the Cloudflare Worker
and Durable Objects. The repository has no Worker code.

---

## Phase B: Core (`bridge/core`, plus storage)

> **Pre-Scaffold Status (B1–B8):** Stages **B1 through B8** already have pre-implementation scaffold files in place that are **Standard 1 compliant** (Stages 1–3 of [`docs/Coding Standards.md`](../Coding%20Standards.md) for B1–B5, and Standard 1 pre-implementation scaffolds for B6–B8) wired to `:bridge:contracts` (`BridgeService.kt`, `RequestPipeline.kt`, `RequestLifecycle.kt`, `PathNormalizer.kt`, `PathNormalizerTest.kt`, `ProtectedPaths.kt`, `BranchRules.kt`, `RepositoryAllowList.kt`, `PolicyConfig.kt`, `ConfirmationRules.kt`, `PolicyEngine.kt`, `PolicyEngineTest.kt`, `ApprovalGate.kt`, `ApprovalCoordinator.kt`, `ApprovalNotificationReceiver.kt`, `WriteCoordinator.kt`, `WriteCoordinatorTest.kt`, `DuplicateDetector.kt`). Upcoming Phase B execution completes spec-level acceptance parity and remaining runtime integration (RTB-49 vector alignment, `PolicyEngineTest`/`ConfirmationRules` reconciliation, B6 Android notification/UI/timeout wiring, B7 pipeline acceptance, and B8 pipeline/service/B11 wiring plus owner decision for `retentionWindowMillis`) and advances code into Standard 2/3.

**B1. Request pipeline** (6a#5, 6b#6). `core:application/BridgeService.kt`, `RequestPipeline.kt`, `RequestLifecycle.kt` *(Standard 1 pre-scaffold in place — `BridgeService.submit`/`getStatus`, `RequestPipeline.execute`, `RequestLifecycle`, with B6/C1/storage placeholders remaining in `RequestPipeline.kt`)*.
Order: validate envelope, expiry check, path canonicalize, `PolicyEngine`, `ApprovalGate` if required, GitHub
dispatch, audit, terminal state. Edges must match `RequestState.kt` and `lifecycle.v1.json` after A3.
Acceptance: unit tests for each edge, including lost acknowledgement to `OUTCOME_UNKNOWN`.

**B2. Path normalizer** (6a#3, 6b#8) `core:policy/PathNormalizer.kt`, `PathNormalizerTest.kt` *(Standard 1 pre-scaffold in place)*. Align fully with A1 (`RTB-49` 8-step rules and `path-normalization-test-vectors.v1.json`). Acceptance: every vector passes;
`normalize` rejects only vectors marked reject; verify `PathNormalizerTest.kt` covers all vectors in the vector file.

**B3. Protected paths and branch rules** (6b#9, 6b#10) `ProtectedPaths.kt`, `BranchRules.kt` *(Standard 1 pre-scaffolds in place with glob matching and defaults)*. Use B2 and A6. Acceptance: `docs/hello.md` is not protected;
`.github/workflows/*` is; `docs/hello` is writable; `main` is not (denied/gated before any GitHub call).

**B4. Allow-list and policy config** (6b#11) `RepositoryAllowList.kt`, `PolicyConfig.kt` *(Standard 1 pre-scaffolds in place, including `confirmationRules` and fail-closed `PolicyConfig.default()`)*. Align with the A6 canonicalization decision.
Acceptance: `Example-Org/Sandbox` and `example-org/sandbox` behave consistently.

**B5. Policy engine and confirmation rules** (6a#4, 6b#7) `PolicyEngine.kt`, `ConfirmationRules.kt`, `PolicyEngineTest.kt` *(Standard 1 pre-scaffolds in place and wired into `RequestPipeline.kt` and `BridgeGraph.kt`)*. Order: allow-list, path normalization, protected branch, protected path, operation
permission, confirmation. Returns `Allow`, `Deny(code, reason)` or `RequireApproval`; unknown input denies.
Acceptance: reconcile `ConfirmationRules` operation names with `ToolOperation` wire names, enable the remaining `PolicyEngineTest.kt` cases, and pass all allow and typed-deny cases.

**B6. Approval gating** (6a#9, 6b#12) `core:approval/ApprovalGate.kt`, `app:approval/ApprovalCoordinator.kt`,
`ApprovalNotificationReceiver.kt` *(Standard 1 pre-scaffolds in place and wired in `RequestPipeline.kt` and `BridgeGraph.kt`; Android notification/UI/timeout integration and B6 tests remain for B6 execution)*. Suspend to `AWAITING_APPROVAL`; approve to `EXECUTING`; decline to `REJECTED`
with no GitHub call. Defaults: `delete_file` and `create_pull_request` require approval (DV-RTB-CONFIRM-*);
`write_file` and `create_branch` follow policy.

**B7. Write serialization** (6b#13) `WriteCoordinator.kt`, `WriteCoordinatorTest.kt` *(Standard 1 pre-scaffolds in place — `InMemoryWriteCoordinator` wired into `RequestPipeline.kt` and `BridgeGraph.kt`, with `WriteCoordinatorTest.kt` passing)*. Per-repo, per-path serialization (RTB-48).
Acceptance: a test asserts ordering of two concurrent writes.

**B8. Duplicate detection** (6b#14) `DuplicateDetector.kt` *(Standard 1 pre-scaffold in place — `DuplicateDetector`, `DuplicateRecordStore`, and `InMemoryDuplicateRecordStore`; `RequestPipeline`/`BridgeService`/B11 wiring, B8 tests, and owner decision for `retentionWindowMillis` remain for B8 execution)*. `request_id` plus operation
fingerprint (RTB-45) within the window from A3. Acceptance: replay after `SUCCEEDED` returns the stored result with
no second GitHub call.

**B9. Outcome reconciliation** (6b#15) `OutcomeReconciler.kt` (`reconcile` is `TODO`). Read back the branch, file or
PR and compare by hash without storing content. Maps to `SUCCEEDED`, `FAILED`, or `MANUAL_REVIEW` per A3. No blind
replay (RTB-43, RTB-44). Depends on C1.

**B10. Resource limits** (6a#9) `core:limits/ResourceLimits.kt`. Enforce the Appendix D values (2 MiB request, 4 MiB
response, 1 MiB frame, 2 in-flight, timeouts, backoff). Replace the placeholder values.

**B11. Persistence wiring** (gap in both plans). Confirm `RequestStorePort` has a working implementation over
`storage:request/RequestRecordDao`, and that `PolicyDataStore` backs `PolicyConfig`. Duplicate detection (B8) and
reconciliation (B9) depend on it. Nothing may persist request bodies or file content (RTB-07).

---

## Phase C: GitHub adapter (`bridge/github`)

> **Status (26 November 2026): Standard 1 implemented; acceptance still pending.** C1 and C2 are implemented to
> Standard 1, Stages 1–3 of [`docs/Coding Standards.md`](../Coding%20Standards.md): all six operations, the
> GitHub transport, the Base64 codec (per `RTB-D18`), the rate-limit handler and the upstream error mapper. The
> C1 acceptance criterion — mocked-REST contract tests for success, 409 and 429 for each operation — is Standard
> 2, Stage 5 work and is **not** delivered by this pass, so C1 is not yet closed. `bridge/github` now depends on
> `kotlinx-coroutines-core` and `kotlinx-serialization-json`.

**C1. Six operations** (6a#6, 6b#16). `ops/ReadFile.kt`, `WriteFile.kt`, `CreateBranch.kt`, `DeleteFile.kt`,
`ListBranches.kt`, `CreatePullRequest.kt`, plus `GitHubAdapter.kt`, `client/GitHubHttpClient.kt`,
`codec/Base64Content.kt`. The phone resolves file SHA and base commit SHA itself (RTB-15). Handle empty repos,
non-text and oversized files, Base64 edge cases (using native `java.util.Base64` per `RTB-D08`/`RTB-D18`, with `minSdk = 26` and `targetSdk = 36` reconciled across Gradle and spec). Pin the API version in `GitHubApiVersion.kt` (RTB-58). Never log
bodies or the PAT. Depends on A5.
Acceptance: mocked-REST contract tests for success, 409 conflict and 429 for each operation.

**C2. Rate limits and upstream errors** (6b#17) `client/RateLimitHandler.kt`, `client/UpstreamErrorMapper.kt`.
403/429 with rate-limit headers to a retryable typed error honouring `Retry-After` within the A4 bounds; 409/422 to
conflict with no auto-retry; unmapped to a non-retryable upstream error. Depends on A4.

---

## Phase D: Local transport (`bridge/local`, `app`)

> **Phase D status (D1–D3 — Standard 1 compliant):** **D1, D2, and D3** are implemented to **Standard 1** (Stages 1 to 3 of
> [`docs/Coding Standards.md`](../Coding%20Standards.md)). D2 adds `LocalAuthPlugin` (wired into `KtorListenerFactory`
> after `HostHeaderGuard`, with `LocalAuthPluginTest` and `LoopbackBindingTest`), and D3 adds `HealthRoute`,
> `CapabilitiesRoute`, `ToolRoute`, `RequestStatusRoute`, `LocalEnvelopeMapper`, `BridgeService.cancel`, `LocalRoutes`
> dispatch in `LocalServer.kt`, local `BridgeGraph.kt` wiring, and `LocalEnvelopeMapperTest`/`LocalServerRoutesTest`/
> `BridgeServiceCancelTest`. Proposed dependency decision, pending owner approval: Ktor **3.6.1** with the CIO engine.
> Open for Standard 2/3 & Phase F: Appendix D defines no connection limit and CIO offers no maximum connection count
> (RTB-20), rate limiting remains a single global bucket until per-principal keying is wired, and full app/vault/GitHub/relay
> wiring remains in Phase F.

> **Rework pending (A10, S5):** D2 moves to the per-session bearer, and D1 to D3 routes pass the session access gate.

**D1. Server, binding, guards and limits** (6a#7, 6b#18) `LocalServer.kt`, `LoopbackBinding.kt`,
`HostHeaderGuard.kt`, `limits/LocalLimits.kt`; add the HTTP server dependency in `bridge/local/build.gradle.kts` (a version
decision is needed; none exists yet). Bind `127.0.0.1:18765` (mandatory) and `::1` when supported; IPv6 failure is
logged and non-fatal; IPv4 failure prevents start; refuse non-loopback. Appendix D limits. Extend
`LoopbackBindingTest.kt`. Depends on A2.

**D2. Authentication** (6b#19) `LocalAuthPlugin.kt` *(Standard 1 compliant — Stages 1–3 of `docs/Coding Standards.md`;
wired into `KtorListenerFactory` in `LocalServer.kt`, with `LocalAuthPluginTest.kt` and `LoopbackBindingTest.kt` passing)*.
Local-client bearer (43 characters) from the `Authorization` header only, on every route except health. Missing or
malformed gives 401; a token in the query string gives 400; never logged (RTB-22, RTB-23, RTB-04).

**D3. Routes and envelope mapping** (6a#7, 6b#20, 6b#21) `routes/ToolRoute.kt`, `HealthRoute.kt`,
`CapabilitiesRoute.kt`, `RequestStatusRoute.kt` (and cancel if A3 keeps it), `mapping/LocalEnvelopeMapper.kt`
*(Standard 1 compliant — Stages 1–3 of `docs/Coding Standards.md`; wired via `LocalRoutes` in `LocalServer.kt`,
`BridgeService.cancel` in `bridge/core`, and `BridgeGraph.kt`, with `LocalEnvelopeMapperTest.kt`,
`LocalServerRoutesTest.kt`, and `BridgeServiceCancelTest.kt` passing)*. Path operation must equal the envelope's
`operation`; only the six operations are routable; unknown fields rejected; RFC 3339 converted to epoch millis; oversize
body rejected before parsing; `SUCCEEDED` with an error is rejected.

---

## Phase S: Task, Session Plan, bootstrap and grant

New code. A9 and A10 record module and file locations before any file is created.

**S1. Task intake.** Wire `:task:contract` into the bridge. Only a `TaskContract` that passes `validate()` can produce a
plan. Acceptance: an invalid task produces no plan.

**S2. Session Plan and approval.** Build the plan from the task. Operations are a subset of
`TaskAuthority.effectiveOperations`; `forbiddenOperations` always wins. Reject an unapproved repository, a public
repository, a protected task branch or a protected task directory. The operator approves on the device; record a
digest of the plan. Acceptance: a changed plan or task revision cannot use an earlier approval.

**S3. Bootstrap.** After approval, and with the operator as principal (S5 applies to agent requests only; the plan
approval satisfies the approval gate): verify the repository is private and
allow-listed; read the base head; create the task branch from it; commit `TASK.md`; read both back. Reuse
`CreateBranch` and `WriteFile` through the pipeline, so write serialization, audit and reconciliation apply. The
visibility check is a fixed internal GitHub read, not a new agent operation. Re-running is safe: resume only if the
existing branch matches this plan (at the base head, or one commit ahead containing the expected `TASK.md`);
otherwise stop for the operator. Never delete or force-update a branch. Acceptance: tests for success, existing
foreign branch, public repository, and lost acknowledgement on each write. Any failure creates no session and no URL.
Depends on S2, B1, B9, C1.

**S4. Session and grant.** Only a successful S3 creates them. The grant fixes repository, task branch, task directory,
base branch, operations, steps and limits. Issue the URL or bearer to the operator only. The session stays `LOCKED`
until pairing is confirmed on the device. Acceptance: no agent route succeeds before `ACTIVE`.

**S5. Access gate and grant policy.** Every agent request, on every transport, resolves to one `ACTIVE` session or is
denied before any GitHub call. Added to `PolicyEngine` ahead of the B5 order, and rechecked before dispatch:
- repository equals the grant; reads and writes target the task branch; paths fall inside the task directory
  (segment boundary, after RTB-49 normalization);
- `list_branches` returns only the task branch;
- `create_branch` and `delete_file` are denied to the agent (bootstrap already created the branch);
- `TASK.md` is read-only to the agent;
- `create_pull_request` has head equal to the task branch and base equal to the plan's base;
- every agent mutation requires operator approval (Navigation Transport §9.3), whatever the policy default.
Apply the same gate to the Phase D local routes: the bearer resolves the session, so the request envelope is unchanged.
Acceptance: one test per rule on both paths. D1 to D3 need rework for the session bearer. Because this gate runs
first, it can refuse a request before the B5 rules are reached, so B3 to B5 keep direct tests of the allow-list,
protected branch and protected path rules.

**S6. Session end.** Completion, expiry, suspension and revocation take effect immediately: links and bearer stop
working, drafts are dropped, undispatched requests are cancelled. A dispatched request is reconciled, never recalled.
The branch and pull request remain. Acceptance: tests for each, including revocation during `EXECUTING`.

---

## Phase E: Relay and session surface map

Path B is end to end only when all three parts exist: the relay service, the phone's relay client and the phone's
navigation adapter. XGN module references show the source of each rule.

```
Agent --HTTPS GET--> Worker --> Durable Object (device) <--outbound WSS-- Phone: navigation adapter --> BridgeService
```

**E1. Relay service** (A12) (XGN-20). Worker plus one Durable Object per device. Routes: device connect, health, and
`GET /gitana/nav/v1/s`. It checks form only (route, method, parameter names and order, sizes, rate) and never judges
operations, paths or branches. It persists no content, arguments or replies and logs no query string. One delivery in
flight per session. No queueing: an unreachable phone returns `DEVICE_OFFLINE`. Responses are `no-store`.

**E2. Capability routing** (XGN-20, XGN-29). The phone issues `cap_` plus 22 base64url characters and registers only
its hash, device and expiry with the relay. The relay uses the hash to route; the phone's record is authoritative. An
unknown capability is refused without contacting a device.

**E3. Client and connection** (6a#8, 6b#22) `RelayClient.kt`, `RelayConnectionManager.kt`, `RelayConfig.kt`,
`FrameLimits.kt`; add a WebSocket dependency (version decision needed). Outbound WSS only, no inbound listener,
reconnect with backoff, relay-device credential only. Relay failure does not affect local operation (RTB-29, RTB-53).

**E4. Frames and replay guard** (6b#23, 6b#25) `RelayEnvelopeMapper.kt`, `ReplayGuard.kt`. Add navigation command and
reply frames that carry no principal assertion. Keep the device frame `seq` window (64) and 300 second expiry;
duplicates and expired frames are dropped. Malformed frames never reach the adapter. Depends on A11.

**E5. Principal** (6b#24) `RelayPrincipalMapper.kt` (XGN-02 §4.2). On the navigation path the phone derives principal,
session, `request_id` and timestamps from its own session record. The agent and relay supply none of them.

**E6. Surface map and pairing** (XGN-03, XGN-05, XGN-14). Phone-side. The first open returns onboarding, the
capabilities for this grant, "pairing pending", and a status link. A six-digit challenge shows on the device only and
is never sent to the agent; the operator confirms there. Panels have five blocks (state, task, draft, last result,
commands), are generated by Gitana, and mark repository content as data. This replaces the chat PIN in Navigation
Transport §7.

**E7. Commands and drafts** (XGN-07, XGN-28). Phone-side: `new`, `set`, `append`, `replace`, `unset`, `preview`,
`show`, `commit`, `cancel`, `status`. One draft and one unresolved request per session; 64 KB draft, 2 KB data per
command, 900 character URL, 512 commands per session. A per-session command `seq` advances only on accepted commands.
`preview` returns sizes, normalized path and digest, and records the current file and branch head. `commit` needs that
digest; the approved content is the dispatched content, and a moved head or file fails the write. Reads are links
within the grant. No command cancels a committed request.

**E8. Link security** (XGN-29). Single-use rolling capabilities, 10 minute lifetime. A byte-identical retry within 90
seconds returns the reply cached on the phone, never on the relay. Any other reuse suspends the session and alerts the
operator. Opening the bare current link consumes nothing.

**E9. Verification** (XGN-20 §12, XGN-30). Prove no content or query strings are stored or logged, offline fails fast,
and two concurrent deliveries forward exactly one. Probe each target chat app's fetch tool for composed query strings
before enabling agent-written content for it.

### Phase E departures from XGN

| Area | This plan (v2) | XGN | Reason |
|---|---|---|---|
| Contracts and lifecycle | v2 envelope and `RequestState` | XGN envelope and states | Shared core is v2 |
| Errors | v2 catalog plus navigation codes | XGN-23 catalog | Same |
| Integrity | Draft and content digest, plus head and file check | Canonical envelope digest and signature (XGN-31) | Not in the v2 core |
| Bootstrap | Gitana creates the branch after plan approval | Agent's first step is `create_branch` (XGN-DEC-047, 048) | Owner decision |
| Steps and gates | Ordered steps; per-commit approval; pause and revoke | Configurable turn gates | Deferred |
| `batch` | Not provided | XGN-28 | Deferred |
| Existing pull request | v2 idempotent success (RTB-D12) | `CONFLICT` | Core behaviour |
| Request expiry | At most 300 seconds on the relay (RTB-D17), including approval wait | 1 hour; approval 10 minutes | v2 limit |
| Audit retention | v2 values | 400 days | v2 limit |
| v2 relay agent POST routes | Deferred; not needed for the sample task. If enabled, they pass S5 | No equivalent route | Predates XGN |

---

## Phase F: Wiring and audit

**F1. Compose the app** (6a#9, 6b#26) `app/…/di/BridgeGraph.kt` (`app:di/BridgeGraph.kt`). Both transports build the same
`BridgeService` with the same policy objects. Acceptance: one test asserts the two transports share one policy graph.

**F2. Audit emission** (6a#9, 6b#27) `storage:audit/RoomAuditSink.kt`, `AuditDao.kt`, `AuditEntity.kt`,
`core:ports/AuditSinkPort.kt`. An event is written for every accepted request, metadata only. Validate against
`audit-event.v1.json`; a test attempting to attach content, a token or a body must fail; `WriteFileArgs.toString()`
masks content in pipeline logs (RTB-09, RTB-54). Include audit retention and clear (RTB-56).

**F3. Credential vault** (`bridge/vault`, all files are scaffolds). Keystore-backed GitHub token, relay-device
credential and session secrets. Acceptance: no secret in logs or plain storage.

**F4. Storage** (`bridge/storage`, all files are scaffolds). Policy store and metadata-only audit and request records.
Resolve B11 against RTB-07 before persisting any request record.

**F5. Operator app** (`app:ui`, `app:service`, screens are scaffolds). Credential and repository setup, task entry, plan
review and approval, bootstrap status, copy URL or bearer, pairing confirmation, request approval with diff,
pause, revoke, audit view, and the foreground service that keeps both paths alive. Depends on S2, S4, S6.

---

## Phase G: Verification and close-out

**G1. Path A end-to-end** (6a#11, 6b#28). Against a mocked GitHub server: create the sample task, approve the plan,
bootstrap, pair, then with the session bearer `read_file` on `TASK.md`, `write_file` on
`tasks/<task-id>/hello.md` and `create_pull_request`, approving both mutations; complete the session. All reach
`SUCCEEDED`; audit records exist; no content, token, capability or body in logs.

**G2. Path B end-to-end** (6a#11, 6b#29). The same flow using only HTTPS GET from the session URL, through the real
Worker run locally and the phone navigation adapter. A test client follows only links and command templates it was
given. Nothing in a delivery can widen the grant.

**G3. Failure paths** (6a#11, 6b#29), on both paths where applicable: unapproved plan yields no session; bootstrap
failure yields no URL; request before pairing; unapproved repository; path or branch outside the grant; write to a
protected branch; expired request (including after reconnect); operator declines; revoked or expired session; lost
acknowledgement on `write_file` (`OUTCOME_UNKNOWN`, `RECONCILING`, then `SUCCEEDED` or `FAILED`); replay of a finished
`request_id`. Relay only: duplicate frame `seq`, reused link, command `seq` gap, device offline.

**G4. Close out** (6a#12). Tick every exit-task the work closes; add a note to `docs/…exit_tasks.md` and
`bridge/README.md` that the sample task is executable on both paths once a device is provisioned. Remove the
"scaffold state" claims in the README that are no longer true. Leave only pure device validation (foreground
service type, Keystore, Doze, loopback reachability, `androidTest` suites) as open.

**G5. Device and live check.** On a provisioned phone, with a private test repository and a deployed relay, run G2 once
with a real chat app that passed E9. Record the evidence.

---

## Dependencies and parallelism

| Blocked task | Needs |
|---|---|
| B2 | A1 |
| B3, B4 | A6 (B3 also B2) |
| B1, B6, B9 | A3 |
| B5 | B2, B3, B4 |
| B8 | A3, B11 |
| B9 | C1, B11 |
| C1 | A5 |
| C2 | A4 |
| D1, D3 | A2 |
| S1, S2 | A9 |
| S3 | S2, B1, B9, C1, F3 |
| S4, S6 | A10, S3 |
| S5 | S4, B5 |
| E1, E2 | A11, A12 |
| E3, E4 | A11 |
| E5, E6 | S4, E4 |
| E7, E8 | E6, S5 |
| E9 | E1 to E8 |
| D1 to D3, E5 to E8, C1, C2 | B1 (all call `BridgeService.submit`) |
| F1 | B1 to B11, C1, D1 to D3, S1 to S6, E1 to E8, F3, F4 |
| F5 | S2, S4, S6 |
| G1 | F1, F2, F5 |
| G2, G3 | G1, E9 |
| G4 | G2, G3 |
| G5 | G4, deployed relay, provisioned phone |

Safe to parallelize once A1 to A12 and B1 are done: Phase C, Phase D, S1 to S2 and E1 to E4. A8 and the spec-only parts of A1 to A7
can start immediately.

## Open decisions that need the owner (not code)

1. Sync 200 versus 202-plus-poll, per operation (A2, A3).
2. Whether `CANCELLED` and `MANUAL_REVIEW` edges and the cancel route are in scope (A3).
3. `create_pull_request` head syntax and existing-PR behaviour (A5).
4. Repository name canonicalization and rename handling (A6).
5. Ktor and OkHttp WebSocket dependency and version choices (D1, E3).
6. Task directory default, and whether `TASK.md` stays in the pull request (A9).
7. Session bearer on Path A replacing the long-lived local bearer (A10).
8. Whether 300 seconds is long enough for approval on the relay path (A11).
9. Deferring the v2 relay agent POST routes (A11).
10. Relay service location and deployment (A12).
