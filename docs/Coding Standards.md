# Android Kotlin Code-Level Standards

Agentic instruction: given a Kotlin Android file, classify it against the three standards below and advance it exactly one stage within its current standard. A standard is not met until every stage inside it is complete. Do not skip standards.

---

## Standard 1: Functional Correctness
*The file does what it is supposed to do under real conditions: correct data flow, all states handled, failures covered.*

### Stage 1 — Happy-Path Logic
- Core behavior implemented with hard-coded or dummy data (`listOf(...)`, fake delays).
- No repository injection; no error handling; UI renders only the success path.
- `TODO()` permitted only for not-yet-reached subtasks.

### Stage 2 — State and Data Wired
- Real data source connected via a Repository (interface + implementation, Hilt/Koin injected).
- UI state exposed from ViewModel as `StateFlow<T>` (Compose) or `LiveData` (View system), using a sealed UI state type: `Loading | Success | Error | Empty`.
- State collected with `collectAsStateWithLifecycle` or `repeatOnLifecycle(Lifecycle.State.STARTED)`; never `observeForever`.
- User actions dispatched through ViewModel functions, not mutated in the UI layer.
- Loading and empty states rendered, even if crudely.

### Stage 3 — Error and Edge-Case Handling
- All failure modes handled: network errors, HTTP error codes, empty/null results, malformed input, missing runtime permissions.
- `try/catch` around suspend calls in the ViewModel (or a `Result`/`Either` wrapper); never crash on `HttpException` or `IOException`.
- User-visible feedback for every failure: Snackbar/toast or inline error state, with retry where applicable.
- Edge cases covered: empty lists, pagination end, stale/invalid IDs, process death (`SavedStateHandle` for nav args).

---

## Standard 2: Structural Quality
*The file is built to last: proper lifecycle and coroutine discipline, injected dependencies, testable design, and actual test coverage.*

### Stage 4 — Async and Lifecycle Correctness
- All coroutines launched in ViewModel `viewModelScope`; no `GlobalScope`, no `runBlocking` on the main thread.
- Flows collected with `WhileSubscribed(5_000)` / lifecycle-aware collectors; UI survives configuration change without unintended refetching.
- Long-running work moved to `WorkManager`; `SavedStateHandle` for process death.
- No leaks: no Activity/View references held in ViewModel; no `lifecycleScope` misuse for app-scoped work.
- Cancellation: in-flight requests cancelled on screen exit; debounce applied to search/text inputs.

### Stage 5 — Testability and Tests
- Constructor injection throughout; no `Application.appContext` globals, no static singletons.
- Interfaces for data sources; ViewModel testable with a fake repository, no Robolectric required for logic.
- Unit tests: ViewModel state transitions, repository mapping (success + failure), util/extension functions.
- UI tests: Compose `createComposeRule` or Espresso for critical flows.
- Dispatchers injected (`@Dispatcher(IO)` qualifiers); `runTest` + `UnconfinedTestDispatcher` in tests.
- No `TODO()` remaining; all tests green.

---

## Standard 3: Maintainability and Hardening
*The file is safe to ship and easy to live with: documentation, lint-clean, observability, no debug code, conventions followed.*

### Stage 6 — Polish
- KDoc on public classes/functions; parameters and thrown exceptions documented.
- Lint and ktlint/ktfmt clean; any `@Suppress` carries a justification comment.
- Accessibility: `contentDescription` set, touch targets ≥ 48dp, Compose semantics in place.
- Strings/resources externalized (no hard-coded UI strings); colors/dimens from theme.
- Team conventions: naming, module boundaries, import order per project style guide.

### Stage 7 — Production-Ready
- Code reviewed and approved; CI green (build, lint, unit, UI tests).
- All UI states verified on device: rotation, process death, offline, empty, error, permission-denied.
- Observability: Timber (or agreed logger) for errors, no PII in logs; analytics where required.
- Release hardening: ProGuard/R8 keep rules, no plaintext HTTP (`networkSecurityConfig`), debug code gated by `BuildConfig.DEBUG`.

---

**Stage-gate rules for agents:**
1. Classify first; report the current standard and stage before writing any code.
2. Complete exactly one stage per pass; re-classify afterward.
3. A stage is incomplete if any listed requirement is unmet, even if the code runs.
4. Never regress a standard (e.g., do not introduce `GlobalScope` while working in Standard 2 or 3).
5. Cite the requirement (e.g., "Standard 1, Stage 3: retry on network error") in commit messages or review comments when closing a gap.
