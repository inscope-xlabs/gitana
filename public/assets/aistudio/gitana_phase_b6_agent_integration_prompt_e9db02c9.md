# AI Agent Prompt — Integrate Gitana Phase B6 Approval Gating

You are working in `https://github.com/inscope-xlabs/gitana`.

The three Phase B6 target deliverable files have been supplied separately:

1. `bridge/core/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/core/approval/ApprovalGate.kt`
2. `app/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/approval/ApprovalCoordinator.kt`
3. `app/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/approval/ApprovalNotificationReceiver.kt`

Treat those three files as the canonical B6 target files. Do not create alternate approval contracts or duplicate coordinator/receiver implementations. Your task is to make the necessary *resultant integration changes in other repository files* so the three targets work end-to-end and comply with `docs/Coding Standards.md` and the B6 section of `docs/x31/merged-plan.md`.

## Required integration work in other files

### 1. Reconcile the request pipeline

- Inspect `bridge/core/.../RequestPipeline.kt` and remove/reconcile its existing local placeholder `ApprovalGate` and `ApprovalResult` declarations. The canonical contract is `com.inscopexlabs.gitana.gitbridge.core.approval.ApprovalGate` and `ApprovalResult` from the supplied target file.
- Inject the canonical `ApprovalGate` into the pipeline; do not instantiate the Android implementation in core.
- Preserve the required order: policy evaluation → authorization → `AWAITING_APPROVAL` → wait for decision → `EXECUTING` only on approval → dispatch.
- An approval decline must transition to `REJECTED` and return an explicit approval-declined result. It must not invoke the GitHub adapter.
- A timeout must fail closed and must not dispatch. Map it to the repository's established timeout/error contract rather than inventing a second result model.
- Requests that do not require approval must continue through the existing authorized execution path without waiting.

### 2. Wire lifecycle reporting and duplicate protection

- Inspect `BridgeService.kt` and related request-state models. Ensure the observable lifecycle includes the intermediate `AWAITING_APPROVAL` state and the terminal `REJECTED` state, with `EXECUTING` entered only after approval.
- Ensure duplicate request IDs cannot overwrite an active request or its pending approval decision. Reject duplicates deterministically and fail closed.
- Keep state transitions consistent with the existing state model and event/reporting contract; do not add parallel state enums if the current contract can be extended safely.

### 3. Dependency injection and application composition

- Register one shared `ApprovalCoordinator` instance in the app's existing DI framework (the repository currently uses Koin where applicable).
- Bind that instance to the core `ApprovalGate` interface and inject the same coordinator into the notification action handler / Android notification integration.
- Update the app composition root and module registration as needed. Do not make `bridge:core` depend on Android, Compose, notification APIs, or app modules.
- Keep dependency direction valid: app implements core approval contract; core depends only on contracts/core abstractions.

### 4. Android notification integration

- The supplied `ApprovalNotificationReceiver.kt` is a platform-neutral action handler, not by itself an Android `BroadcastReceiver`. Add or update the Android-facing receiver/notification integration in the appropriate app source file(s), delegating actions to the shared `ApprovalCoordinator` (or the supplied handler).
- Create explicit, immutable PendingIntents for Approve and Decline, with unique request/action identity so actions cannot collide.
- Register the Android receiver in `app/src/main/AndroidManifest.xml` as non-exported unless the existing architecture has a documented, safer equivalent.
- Build notification content from safe request metadata and the policy reason; do not expose credentials, tokens, file contents, or other secrets.
- Handle Android 13+ notification permission denial and notification-provider failures without bypassing approval or crashing the request. The in-app approval path must remain usable when notifications are unavailable.

### 5. Approval UI and state wiring

- Add/update the app-facing repository or state adapter required to expose pending approval requests to UI without leaking Android dependencies into core.
- Expose UI state through the project's established `StateFlow`/ViewModel pattern, with Loading, Success, Empty, and Error states where applicable.
- Add an approval screen or integrate with the existing UI. It must present enough request metadata and the policy reason for an informed decision, and provide explicit Approve and Decline actions.
- Collect state lifecycle-aware (use the project's existing lifecycle Compose pattern, e.g. `collectAsStateWithLifecycle`, if Compose is used).
- Surface action failures and permit a safe retry where appropriate.

### 6. Error, timeout, cancellation, and stale-action handling

- Complete the B6 Standard 1 Stage 3 behavior in the integration layer: bounded approval wait/expiry, fail-closed timeout, cancellation propagation, cleanup of pending entries, duplicate request protection, and rejection of blank, unknown, stale, already-completed, or expired action IDs.
- Never convert cancellation into approval or continue dispatch after cancellation.
- Ensure a notification tap after completion/expiry cannot approve a different or newer request.
- Process death must fail closed: an interrupted in-memory approval must not be revived by a stale notification action. If durable approval recovery is not part of B6, document that the interrupted request must be resubmitted; do not claim durable persistence (that belongs to the planned persistence phase, e.g. B11).

### 7. Build configuration and tests

- Update only the necessary Gradle version-catalog and module dependency files for coroutines, lifecycle/ViewModel/Compose, Koin, and Android notification support, using versions and conventions already established in the repository. Avoid duplicate dependencies and version declarations.
- Add focused tests for: approve resumes execution; decline prevents adapter dispatch; timeout prevents dispatch; no-approval-required path proceeds; duplicate IDs fail closed; unknown/stale actions fail closed; cancellation cleans up; and notification failure/permission denial does not bypass the in-app approval flow.
- Use the repository's existing test framework and test conventions. Do not introduce a second testing framework unnecessarily.

## Constraints and execution method

- First inspect the current repository files and classify the B6 implementation against the exact stages in `docs/Coding Standards.md`.
- Preserve the three supplied target files as the canonical files. If a target file requires a necessary correction for compilation or contract consistency, explain the reason and keep the change minimal; do not silently replace the architecture.
- Make changes only in files required to integrate B6. Do not refactor unrelated modules or claim completion of other phases (including B1, B2, B11, or the GitHub adapter work).
- Complete Standard 1 stages in order: Stage 1, then Stage 2, then Stage 3. Keep the stage boundaries explicit and report/re-classify after each stage. Do not skip ahead or combine stage claims.
- Do not claim a stage complete unless every applicable requirement is satisfied. Since this is a core/app integration task, do not add irrelevant UI requirements to the core module; implement UI state requirements in the app layer.
- Run the relevant Gradle compile and unit tests after integration. Report exact commands and results. If Android SDK/build tooling is unavailable, state that verification was not run; do not imply success.
- Do not commit or push unless explicitly authorized.

## Required final report

Return:

1. The list of *other files* changed, grouped by purpose, with one-line reasons.
2. A concise integration summary showing the approval state sequence and proving that decline/timeout never dispatches to GitHub.
3. Standard 1 stage-by-stage classification and evidence.
4. Tests added and exact build/test results.
5. Explicit limitations (especially in-memory/process-local approval and no durable recovery if B11 owns persistence).
