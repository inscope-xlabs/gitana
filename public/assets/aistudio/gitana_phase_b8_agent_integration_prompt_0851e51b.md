# Gitana Phase B8 — AI Agent Integration Prompt

Implement Phase B8 (Duplicate detection) in `inscope-xlabs/gitana`.

## Governing requirements

- Follow `docs/x31/merged-plan.md`, Phase B8 (RTB-45).
- Follow `docs/Coding Standards.md`, Standard 1, Stages 1–3, in order; complete exactly one stage per pass and re-classify.
- Follow `docs/remote_repository_tool_bridge_v2_specification.md`, especially RTB-07 and RTB-42–RTB-47, §§8.1–8.4 and Appendix D.
- Use the supplied `DuplicateDetector.kt` as the only B8 target deliverable. Do not create a duplicate class or guessed package path.
- Do not claim exactly-once delivery.

## Contract gap: duplicate-retention duration

The merged plan refers to a duplicate window “from A3”. A3 specifies per-operation delivery guarantees, sync/poll behavior and reconciliation entry points, but does not record a numeric duplicate-retention duration. Do not silently reuse the relay's 300-second request-expiry default or invent a duration. Keep `retentionWindowMillis` explicitly configured and positive; request an owner decision and record it in the appropriate decision/default source before setting a production default.

## Required changes outside the target file

1. **RequestPipeline.kt**
   - Inject one shared `DuplicateDetector`.
   - Validate envelope/expiry, canonicalize repository and path, and preserve existing policy, B6 approval and B7 serialization contracts.
   - Call duplicate detection before GitHub dispatch. Pass the B2-normalized path for `read_file`, `write_file`, and `delete_file`; do not duplicate RTB-49 logic in the detector.
   - `NewRequest`: proceed.
   - `InProgress`: return the accepted/in-progress lifecycle response; never dispatch again.
   - `Replay`: return the cached result; never dispatch again.
   - `RequestIdConflict`: fail closed using the typed error selected by the error contract.
   - `CapacityExceeded`: fail closed with an appropriate typed resource-limit response; never bypass deduplication.
   - Call `recordSuccess` only after confirmed GitHub success.
   - Call `releaseBeforeDispatch` only for definitive failures known to occur before dispatch. Never release on timeout after dispatch, lost acknowledgement, or `OUTCOME_UNKNOWN`.

2. **BridgeService.kt / lifecycle**
   - Prevent repeated request IDs from overwriting records or starting concurrent duplicate pipeline executions.
   - Resolve same-ID races atomically through the store claim, not a non-atomic `find` followed by `insert`.
   - Preserve the original successful response while its permitted cache is available.
   - Preserve `OUTCOME_UNKNOWN` and reconciliation; never convert uncertainty into automatic replay.

3. **RequestStorePort.kt and storage (coordinate with B11)**
   - Current `RequestStorePort` holds request ID, fingerprint, state and update time, but no result. Wire its durable metadata adapter under B11 and add an atomic claim operation if required.
   - Persist only the minimum metadata permitted by RTB-07. Never persist request arguments, request bodies, file content, `ToolResult.FileContent`, or full response envelopes.
   - The supplied `InMemoryDuplicateRecordStore` is process-local; its result cache is intentionally volatile. Durable metadata can prove a request completed after restart, but cannot reconstruct a `read_file` response containing content without violating RTB-07.
   - Do not “solve” this by persisting file content. Document that post-process-death `read_file` result replay is unavailable under the current no-content-persistence rule unless the owner approves a contract change. Preserve the no-second-write-dispatch guarantee.
   - Determine which write-result metadata may safely be retained; keep the policy explicit.

4. **DI / BridgeGraph**
   - Provide one shared `DuplicateRecordStore` and `DuplicateDetector`.
   - Supply the owner-approved retention duration via configuration; no magic constant.
   - Ensure local and relay transports share the same core detector/service graph.

5. **Tests** (use the existing test layout)
   - Identical request ID and identical operation/arguments produce the same fingerprint.
   - Same ID with changed operation or any changed argument is a conflict; different IDs remain distinct.
   - Repository case normalization follows RTB-D13.
   - B2-normalized paths are fingerprinted; dot-segment-equivalent paths match after normalization.
   - Write content changes the fingerprint, but raw content is absent from the fingerprint and logs.
   - Nullable PR body and pagination fields are represented unambiguously.
   - Concurrent claims for one ID produce exactly one `NewRequest`.
   - Successful replay returns the original cached result without another fake GitHub call.
   - Expiry boundary, backward clock movement, capacity exhaustion, pre-dispatch release and no-release-after-dispatch are covered.
   - No durable row or log contains content or full request/response bodies.

## Stage gates

- Stage 1: pure fingerprint for all six typed operations; re-classify.
- Stage 2: injected store interface/implementation and shared DI; re-classify.
- Stage 3: validation, expiry, conflict, capacity, concurrency, retention and safe-release behavior with tests; re-classify.
- Do not advance into Standard 2 or Standard 3.

## Acceptance and reporting

B8 is complete only when replay after `SUCCEEDED` returns the stored result without a second GitHub call for the supported cache lifetime, and relevant tests pass. Explicitly distinguish process-local replay from behavior after process death. Report the missing owner decision for the numeric duplicate window if unresolved. Do not claim tests/build passed unless actually run. Do not commit or push unless separately authorized.
