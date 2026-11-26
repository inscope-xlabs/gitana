# Contract schemas

Machine-readable contracts for the bridge, in `src/main/resources/`. They mirror the Kotlin types in this
module and the v2 specification (§4.3, §7, §8, §11, Appendix A/B). Where they differ, fix the schema or the
Kotlin type in the same change.

| File | Contract |
|---|---|
| `schemas/common.v1.json` | Shared primitives: ids, timestamps, repo, branch, path, SHA, principal |
| `schemas/tool-contract.v1.json` | The six operations (arguments, results, side-effect metadata), request and response envelopes |
| `schemas/error.v1.json` | Error contract (§11.1) |
| `schemas/lifecycle.v1.json` | Request states, allowed transitions, request status |
| `schemas/authorization.v1.json` | Policy decision |
| `schemas/credential.v1.json` | Credential classes, local-client token format, non-secret metadata |
| `schemas/audit-event.v1.json` | Audit event (no content, credentials or bodies) |
| `schemas/local-transport.v1.json` | Loopback listener config, headers, response model, transport error, accepted-request and capabilities bodies |
| `schemas/relay-envelope.v1.json` | Trusted-relay envelope (`seq` window 64, 5-minute max expiry) |
| `schemas/path-normalization-test-vectors.v1.json` | RTB-49 canonical path normalization test vectors (RTB-T10) |
| `openapi/bridge.v1.yaml` | Local HTTP routes (Appendix B) |

Schemas reference each other by `urn:gitbridge:schema:<name>` `$id`, not by relative path.

## Conventions

- **Wire names:** JSON fields are snake_case. Operations (`read_file`) and transports (`localhost`, `relay`) are
  lowercase. Error codes, categories, execution states and request states use the Kotlin constant names.
- **Requests fail closed:** the envelope and every argument object reject unknown fields (RTB-08, RTB-15).
  **Responses are lenient:** consumers ignore unknown fields so minor versions can add them (RTB-14).
- **Major version:** `contract_version` must be `1.x`. A breaking change needs a new schema set.
- **Paths:** `RepoPath` is only a coarse wire check (length, control characters). The canonical normalizer
  (RTB-49) is authoritative for dot segments, traversal, encodings, Unicode and symlink containment.
- **Defaults:** port, size, timeout and rate-limit defaults are the Appendix D `DV-RTB-*` values and stay tunable.
- **Relay:** only the trusted-relay profile is modelled. The end-to-end profile is deferred pending
  cryptographic review.

## Not yet defined

- Policy configuration persistence records (Phase B B11, backed by `PolicyDataStore`).
- A test that fails when a schema enum drifts from its Kotlin enum (needs a JSON library in the catalog).
