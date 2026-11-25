# Tool Bridge v2: Tasks to Leave Draft Status

Companion to [remote_repository_tool_bridge_v2_specification.md](remote_repository_tool_bridge_v2_specification.md).
The specification is currently **Review draft (2.0-draft)**. It may move to an approved baseline only when every
task below is complete. Source references point to sections of the specification.

## 1. Owner decisions (spec §14, §15)

- [ ] Choose the local HTTP port policy (configurable, non-privileged) and document the default.
- [ ] Choose the local authentication scheme and pairing/bootstrap flow (token format, entropy, lifetime, storage).
- [ ] Decide on IPv6 loopback (`::1`) support.
- [ ] Select the relay security profile (trusted-relay vs end-to-end protected) and record the trust assumption.
- [ ] Confirm confirmation defaults: `delete_file` and `create_pull_request`.
- [ ] Confirm the offline queue policy (default: no write payload queueing).
- [ ] Set numeric limits after device testing: request body, file size, response size, WebSocket frame, concurrency, timeouts, rate limits, backoff, audit retention.
- [ ] Choose Android minSdk/targetSdk and the foreground service type.
- [ ] Pin the GitHub REST API version header.
- [ ] Record all decisions in the spec (decision log) and remove the corresponding "open decision" wording.

## 2. Contract definitions (spec §4.3, §8.2, §11.1, Appendix A/B)

- [ ] Publish machine-readable schemas (JSON Schema or OpenAPI) for the Tool Contract: all six operations, inputs, outputs, side effects.
- [ ] Define the Local Transport Contract: base path, headers, auth, limits, timeouts, and whether submission is synchronous or accepted-then-poll (Appendix B).
- [ ] Define the canonical request/response envelope and replace the "illustrative" Appendix A.
- [ ] Define the full error code catalog and mapping to `category`, `http_status`, `retryable`, `execution_state`.
- [ ] Define the Execution Lifecycle Contract, including `MANUAL_REVIEW`, `CANCELLED` and a per-operation delivery guarantee (spec §8.1). Reconcile the state table with the contract list.
- [ ] Define the Authorization Contract, Credential Contract and Audit Event Contract schemas.
- [ ] Define contract versioning and unknown-field compatibility rules.
- [ ] Define the Relay Transport Contract: device identity, session binding, expiry, replay detection, sequence/correlation, acknowledgements.
- [ ] If the end-to-end profile is chosen: specify key agreement, identity binding, nonce/sequence rules, authenticated encryption, rotation and recovery, then obtain independent cryptographic review.

## 3. Policy and GitHub behavior (spec §7)

- [ ] Specify path canonicalization rules and the default protected-path/branch lists.
- [ ] Define `create_pull_request` head owner/branch syntax and existing-PR detection.
- [ ] Specify handling of empty repositories, binary and oversized files, and Base64 edge cases.
- [ ] Specify rate-limit and secondary-rate-limit backoff bounds.
- [ ] Specify upstream error normalization and what may be surfaced.

## 4. Validation required by release gates (spec §13, §13.1)

- [ ] Validate loopback reachability and authentication from each target shell runtime (e.g. Termux) on target Android versions.
- [ ] Verify loopback-only binding (IPv4 and IPv6) and absence of LAN/public exposure.
- [ ] Test duplicate and unknown-outcome behavior under forced disconnects, plus reconciliation.
- [ ] Pass repository allow-list and path-normalization tests, including traversal and encoding bypasses.
- [ ] Test credential backup exclusion, rotation and revocation, independently per credential class.
- [ ] Verify credential separation (local/relay/PAT) per the §13 acceptance table.
- [ ] Test prompt-injection resistance (malicious README/config cannot alter policy or bypass approval).
- [ ] Test Android lifecycle: foreground service, Doze, process death, reboot, force-stop, notifications.
- [ ] Test resource-limit rejection and privacy (no secrets or content in logs/telemetry).

## 5. Document hygiene

- [ ] Reconcile status wording: §15 calls the document "review-ready" while the header says "Review draft".
- [ ] Complete a technical review and security review; record reviewers and outcomes.
- [ ] Add a changelog and change the status/version header from `2.0-draft`.
- [ ] Re-verify Appendix C external references against pinned versions.
