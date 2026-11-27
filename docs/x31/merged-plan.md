# Gitana Tool Bridge: Merged Deficiency Plan

Merges the two plans in this directory (`info-6a.txt`, an ordered task list, and `info-6b.txt`, a phased plan with
decisions first) into one. Source references are given as `6a#n` / `6b#n`.

**Goal:** make the sample task (`example-org/sandbox`: `read_file`, `create_branch`, `write_file`,
`create_pull_request`) executable on Path A (local loopback) and Path B (trusted relay), with the five failure
paths covered by tests.

## Ground rules

1. Work only in `inscope-xlabs/gitana`. Do not invent behaviour beyond the v2 specification, the schemas and the
   exit-tasks document.
2. **Decisions before code.** A code task whose decision is not yet recorded must not start on a guess. Record the
   decision in the spec and schema first (Phase A).
3. Edit the existing files named below. Do not create files at guessed paths.
4. Fail closed. Stubs that currently deny everything stay that way until their task is complete and tested.
5. A task is done only when its acceptance check passes and, where relevant, the exit-tasks checkbox is ticked.

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

> **Status: COMPLETED & CLOSED (26 November 2026).** All Phase A decisions (`A1`–`A8`) are recorded in `remote_repository_tool_bridge_v2_specification.md` (`RTB-D10`–`RTB-D17`), `cloudflare_relay_specification.md`, `bridge/contracts/SCHEMAS.md`, `bridge/contracts/src/main/resources/schemas/*.json` (including `path-normalization-test-vectors.v1.json`), `bridge/contracts/src/main/resources/openapi/bridge.v1.yaml`, and synced Kotlin contract twins (`RequestState.kt`, `ErrorCode.kt`, `ToolResult.kt`). Phases B–E are unblocked.

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

> **D1 status (26 November 2026):** implemented to Standard 1 (Stages 1 to 3 of
> [`docs/Coding Standards.md`](../Coding%20Standards.md)); D2 and D3 are not started. Proposed dependency decision,
> pending owner approval: Ktor **3.6.1** with the CIO engine. Open: Appendix D defines no connection limit and CIO
> offers no maximum connection count (RTB-20); the rate limit is a single global bucket until D2 supplies a principal;
> `app` wiring is Phase F.

**D1. Server, binding, guards and limits** (6a#7, 6b#18) `LocalServer.kt`, `LoopbackBinding.kt`,
`HostHeaderGuard.kt`, `limits/LocalLimits.kt`; add the HTTP server dependency in `bridge/local/build.gradle.kts` (a version
decision is needed; none exists yet). Bind `127.0.0.1:18765` (mandatory) and `::1` when supported; IPv6 failure is
logged and non-fatal; IPv4 failure prevents start; refuse non-loopback. Appendix D limits. Extend
`LoopbackBindingTest.kt`. Depends on A2.

**D2. Authentication** (6b#19) `LocalAuthPlugin.kt`. Local-client bearer (43 characters) from the `Authorization`
header only, on every route except health. Missing or malformed gives 401; a token in the query string gives 400;
never logged (RTB-22, RTB-23, RTB-04).

**D3. Routes and envelope mapping** (6a#7, 6b#20, 6b#21) `routes/ToolRoute.kt`, `HealthRoute.kt`,
`CapabilitiesRoute.kt`, `RequestStatusRoute.kt` (and cancel if A3 keeps it), `mapping/LocalEnvelopeMapper.kt`.
Path operation must equal the envelope's `operation`; only the six operations are routable; unknown fields rejected;
RFC 3339 converted to epoch millis; oversize body rejected before parsing; `SUCCEEDED` with an error is rejected.

---

## Phase E: Relay (`bridge/relay`)

**E1. Client and connection** (6a#8, 6b#22) `RelayClient.kt`, `RelayConnectionManager.kt`, `RelayConfig.kt`,
`FrameLimits.kt`; add a WebSocket dependency (version decision needed). Outbound WSS only, no inbound listener,
reconnect with backoff, relay-device credential only. Relay failure must not affect local operation (RTB-29, RTB-53).

**E2. Envelope mapping** (6b#23) `RelayEnvelopeMapper.kt`. Frame to `RequestEnvelope` and back with matching
`correlation_id`; malformed frames never reach `BridgeService`.

**E3. Principal mapping** (6b#24) `RelayPrincipalMapper.kt`. The assertion is input only and never grants
authorization; the phone re-runs the policy engine (RTB-32).

**E4. Replay guard** (6b#25) `ReplayGuard.kt`. Monotonic `seq` per device session; duplicate or out-of-window values
dropped; `expires_at` enforced so nothing executes after reconnect (RTB-47). Depends on A7.

---

## Phase F: Wiring and audit

**F1. Compose the app** (6a#9, 6b#26) `app/…/di/BridgeGraph.kt` (`app:di/BridgeGraph.kt`). Both transports build the same
`BridgeService` with the same policy objects. Acceptance: one test asserts the two transports share one policy graph.

**F2. Audit emission** (6a#9, 6b#27) `storage:audit/RoomAuditSink.kt`, `AuditDao.kt`, `AuditEntity.kt`,
`core:ports/AuditSinkPort.kt`. An event is written for every accepted request, metadata only. Validate against
`audit-event.v1.json`; a test attempting to attach content, a token or a body must fail; `WriteFileArgs.toString()`
masks content in pipeline logs (RTB-09, RTB-54). Include audit retention and clear (RTB-56).

---

## Phase G: Verification and close-out

**G1. Path A end-to-end** (6a#11, 6b#28). Four-step sample task against a mocked GitHub server with
`example-org/sandbox` approved. All four reach `SUCCEEDED`; `create_pull_request` passes through approval; audit
records exist; no content, token or body in logs.

**G2. Path B end-to-end** (6a#11, 6b#29). Same task over a fake relay. A relay assertion claiming broader scope
cannot widen access.

**G3. The five failure paths** (6a#11, 6b#29), on both paths where applicable: unapproved repository; write to a
protected branch; expired request (including after reconnect); user declines approval; lost acknowledgement on
`write_file` (`OUTCOME_UNKNOWN`, `RECONCILING`, then `SUCCEEDED` if the commit is present or `FAILED` if absent).
Add duplicate `seq` and replay of a finished `request_id`.

**G4. Close out** (6a#12). Tick every exit-task the work closes; add a note to `docs/…exit_tasks.md` and
`bridge/README.md` that the sample task is executable on both paths once a device is provisioned. Remove the
"scaffold state" claims in the README that are no longer true. Leave only pure device validation (foreground
service type, Keystore, Doze, loopback reachability, `androidTest` suites) as open.

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
| E4 | A7 |
| D1 to D3, E1 to E4, C1, C2 | B1 (all call `BridgeService.submit`) |
| F1 | B1 to B10, C1, D1 to D3, E1 to E4 |
| G1 to G3 | F1, F2 |

Safe to parallelize once Phase A and B1 are done: Phase C, Phase D and Phase E. A8 and the spec-only parts of A1 to A7
can start immediately.

## Open decisions that need the owner (not code)

1. Sync 200 versus 202-plus-poll, per operation (A2, A3).
2. Whether `CANCELLED` and `MANUAL_REVIEW` edges and the cancel route are in scope (A3).
3. `create_pull_request` head syntax and existing-PR behaviour (A5).
4. Repository name canonicalization and rename handling (A6).
5. Ktor and OkHttp WebSocket dependency and version choices (D1, E1).
