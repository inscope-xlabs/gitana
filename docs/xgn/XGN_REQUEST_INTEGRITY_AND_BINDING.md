# XGN-31: XGN Request Integrity and Binding

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-31 | **Group:** B — Session and request lifecycle
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-06, XGN-08 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 10

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the request digest: what it covers, how it is computed, how much of it is exposed, how it binds an approval to a dispatch, how the runtime signs the request, and how the operator can confirm what they approved.

**Out of scope:** the canonical representation (XGN-08), the envelope shape (XGN-02 §4) and its lifecycle (XGN-06), the approval decision and its presentation (XGN-16), the dispatch itself (XGN-17), the key lifecycle (XGN-05), the provider preconditions (XGN-19), and the error catalogue (XGN-23).

### 1.1 The gap this module closes

The inherited material digested the operation and its arguments only. The dispatched request also carries the session, grant, step, principal, policy version, timestamps, part order and the expected provider state. A digest that covers only the arguments therefore does not establish that what the operator approved is what is dispatched, and it cannot detect a branch or a file that moved between preview and dispatch.

This module closes that gap by defining one digest over the **complete immutable envelope**. It is the binding element between composition (XGN-07), validation (XGN-08), approval (XGN-16) and execution (XGN-17).

## 2. Digest inputs

The digest input list is exhaustive and is rendered in the field order of XGN-08 §5.2.

| # | Input | Source |
|---|---|---|
| 1 | `contract_version` | Runtime, from the shared contract |
| 2 | `request_id` | Runtime |
| 3 | `session_id` | Runtime |
| 4 | `grant_id` | Runtime |
| 5 | `grant_digest` | Runtime, over the grant in force (XGN-04) |
| 6 | `step_id` | Runtime |
| 7 | `principal_id` | Runtime |
| 8 | `policy_version` | Runtime, the policy in force at commit |
| 9 | `operation` | Agent, validated against the catalogue |
| 10 | `repository` | Canonical repository identifier |
| 11 | `branch` | Canonical branch name |
| 12 | `base_branch` | Canonical base branch name |
| 13 | `arguments` | All normalised arguments, in canonical order |
| 14 | `part_order` | The ordered identifiers of the live parts |
| 15 | `content` | The complete reconstructed content bytes |
| 16 | `expected_blob_sha` | Expected provider state: the existing file's blob digest |
| 17 | `expected_head_sha` | Expected provider state: the branch head digest |
| 18 | `issued_at` | Runtime |
| 19 | `expires_at` | Runtime |

| ID | Requirement |
|---|---|
| I-01 | The digest input list MUST be exhaustive. A field that is not in the list MUST NOT influence the outcome of a request, and a field in the list MUST NOT be omitted from the digest. |
| I-02 | Every input MUST be the canonical value defined by XGN-08. A digest MUST NOT be taken over raw, non-canonical input. |
| I-03 | An optional field that is absent MUST be absent from the canonical representation, and the absence MUST be observable in the digest input. |
| I-04 | The `content` input MUST be the complete content bytes, not a digest of a digest. The digest therefore covers the content itself. |
| I-05 | The `part_order` input MUST be the ordered list of live part identifiers (XGN-07 §4). Reordering the parts MUST change the digest, and replacing a part's bytes MUST change the digest. |
| I-06 | The `grant_digest` input MUST bind the request to the exact grant in force. A replacement grant MUST produce a different digest. |
| I-07 | The `expected_blob_sha` and `expected_head_sha` inputs MUST be present whenever the operation defines a precondition, and MUST be absent otherwise. |
| I-08 | The envelope's own `envelope_digest`, `draft_digest` and `signing_key_id` fields MUST be excluded from the canonical representation. The first two are digests of values already covered, and the third identifies the attesting key rather than the request. |

## 3. Algorithm and exposed length

| ID | Requirement |
|---|---|
| I-09 | The request digest MUST be SHA-256 over the canonical representation of XGN-08 §5, rendered in the prefixed form `sha256:` followed by 64 lowercase hexadecimal characters (XGN-DEC-003). |
| I-10 | The full 64-character digest MUST be retained internally by the runtime, and that internal value MUST be the value compared at dispatch. A truncated value MUST NOT be used for comparison. |
| I-11 | Exactly 32 lowercase hexadecimal characters, 128 bits, of the request digest MUST be exposed for operator confirmation. They MUST be the first 32 characters of the internal digest, and MUST be rendered unseparated so that they can be compared character by character. |
| I-12 | The gateway, the panel, the approval screen and the audit event MUST NOT expose fewer than 32 characters. The full 64 characters MUST NOT be exposed outside the runtime. |

The inherited proposal exposed 16 hexadecimal characters, which is 64 bits. That is corrected here: 32 hexadecimal characters, 128 bits, are exposed (XGN-DEC-003). A confirmation value that an operator reads from a screen and a system compares must not be 64 bits wide.

## 4. The two digests

| Property | Draft digest | Envelope digest |
|---|---|---|
| Covers | The agent-supplied composition: operation, normalised arguments, part order and content bytes | The complete envelope: all 19 inputs of section 2 |
| Computed | At `preview`, and recomputed at `commit` | Once, at commit |
| Changes when | The draft is edited | Never; the envelope is immutable |
| Presented to the agent | Yes, in the preview reply | Yes, in the commit reply, 32 characters |
| Purpose | Binds a preview to a commit; detects that the draft changed between the two | Binds the approval to the dispatched request |
| Owner | XGN-07 | XGN-31 |

| ID | Requirement |
|---|---|
| I-13 | The draft digest MUST NOT be used as the approval binding. An approval that carries a draft digest MUST be rejected as `MALFORMED_REQUEST`. |
| I-14 | The runtime MUST recompute the envelope digest from the stored envelope before every comparison. It MUST NOT trust a stored digest field without recomputing it. |
| I-15 | The commit MUST fail with `INTEGRITY_MISMATCH` when the digest recomputed at commit differs from the digest presented at preview. |

## 5. Approval binding

| ID | Requirement |
|---|---|
| I-16 | The approval record MUST carry the envelope digest of the request it approves (XGN-02 §7). An approval without an envelope digest MUST be invalid. |
| I-17 | The approval service MUST recompute the envelope digest of the request presented for approval, and MUST refuse to bind an approval to a digest that differs from the recomputed value. |
| I-18 | Immediately before dispatch, the execution layer MUST recompute the envelope digest of the request it is about to dispatch and MUST compare it with the approved digest. A mismatch MUST abort the dispatch with `INTEGRITY_MISMATCH` and the external service MUST NOT be contacted. |
| I-19 | Any change to the envelope after approval, to any field including the timestamps or the part order, MUST invalidate the approval. The request MUST be re-previewed and re-committed, producing a new digest and a new approval. |
| I-20 | A change of grant, session, principal or policy version MUST invalidate every approval bound to the previous values. |
| I-21 | An approval MUST NOT be reusable for a second dispatch of the same request, and a retried dispatch of an already dispatched request MUST follow the reconciliation rules of XGN-22 rather than re-executing. |

The inherited error code `DRAFT_HASH_MISMATCH` covers a preview-to-commit mismatch. The approval-to-dispatch mismatch uses `INTEGRITY_MISMATCH` (XGN-DEC-019). The retired term *draft hash* is not used in this set; the term is *request digest* (XGN-01 §2.1).

## 6. Signing

| ID | Requirement |
|---|---|
| I-22 | The runtime MUST sign the canonical representation with a device key. The algorithm and the key lifecycle are owned by XGN-05. |
| I-23 | The signature MUST cover exactly the canonical representation of XGN-08 §5. A signature over any other byte sequence MUST be rejected. |
| I-24 | The signature MUST be stored in the approval record and MUST NOT be stored in the envelope, so that the envelope stays canonical and the request digest does not depend on the key (XGN-02 §4.1). |
| I-25 | The approval record MUST carry the signature, the identifier of the key that produced it, and the envelope digest. The execution layer MUST verify the signature against the key named in the approval record. |
| I-26 | On signing key rotation, a pending approval MUST remain valid and MUST continue to verify against the retired key's public key. Rotation MUST NOT alter, re-sign or re-approve a pending request. |
| I-27 | The runtime MUST retain the public key of a retired signing key for at least the maximum lifetime of an approval bound to it, and MUST NOT accept a signature made with a key that was retired before the approval was granted. |

## 7. State changes between approval and dispatch

The expected provider state is part of the canonical representation, so it is covered by the request digest. A branch or a file that moves after the approval is therefore detected before any mutation.

| ID | Requirement |
|---|---|
| I-28 | Immediately before dispatch, the runtime MUST re-read the provider state and MUST compare it with the approved expected blob digest and expected head digest. |
| I-29 | A mismatch MUST abort the dispatch before any mutation, with the conflict error defined by XGN-19, and MUST leave the request unresolved for reconciliation (XGN-22). |
| I-30 | A provider state change MUST NOT invalidate the approval record. The approval remains bound to the digest it approved; the dispatch is refused. |
| I-31 | The agent MUST re-preview with the current expected values and re-commit, which produces a new request digest and requires a new approval. |
| I-32 | The dispatch MUST NOT proceed on the basis of a provider state read taken before the approval was granted. |

## 8. Operator confirmation

| ID | Requirement |
|---|---|
| I-33 | The approval screen MUST display the 32 exposed hexadecimal characters of the envelope digest, the operation, the repository, the branch, the base branch, the target path, the content size, the expected blob digest, the expected head digest, and a change summary generated by the runtime (XGN-16). |
| I-34 | The approval screen MUST display exactly the 32 characters that the execution layer will verify at dispatch. |
| I-35 | The runtime MUST NOT ask the operator to retype or copy the digest. The operator confirms the approval action; the digest is shown so that the confirmation is of a specific, recorded value. |
| I-36 | The digest MUST be a runtime value. It MUST NOT be derived from, and MUST NOT be presented as, agent-written content. |
| I-37 | The agent-visible panel MUST expose the same 32 characters for the committed request, so that the agent can correlate the request with the operator's decision. |
| I-38 | The audit event MUST record the 32 exposed characters and the full internal digest, and MUST NOT record any content (XGN-25). |

## 9. Decisions recorded

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-003` | Digest algorithm | SHA-256 over the canonical envelope. Full digest held internally; 32 hex characters (128 bits) exposed for confirmation. | XGN-31 |
| `XGN-DEC-019` | Approval binding | The digest covers the complete immutable envelope, including expected provider state. | XGN-31 |
| `XGN-DEC-020` | Overwrite protection | Writing an existing file requires the expected blob digest; a branch operation requires the expected head digest. A mismatch is a rejection. | XGN-19 |
| `XGN-DEC-057` | Draft digest and envelope digest | Two digests exist. The draft digest covers the agent-supplied composition and binds a preview to a commit. The envelope digest covers the complete immutable envelope and is the approval binding. The draft digest MUST NOT be used as an approval binding. | XGN-31 |
| `XGN-DEC-058` | Envelope signature placement and key rotation | The runtime signs the canonical representation with a device key. The signature is stored in the approval record, not in the envelope, so the envelope stays canonical. Rotation is recorded by the key identifier and MUST NOT invalidate a pending approval. | XGN-31 |

## 10. Open decisions

| ID | Question | Current position |
|---|---|---|
| XGN-31-OD-01 | Does XGN-04 define a grant digest? | The digest input list requires one. If XGN-04 does not define one, the fallback is defined in XGN-08 §11. To be confirmed when XGN-04 is written. |
| XGN-31-OD-02 | May the envelope digest appear in a composed command URL? | It is not required and a digest in a URL is exposed to intermediaries. The current position is that it does not. To be confirmed when XGN-28 is written. |
| XGN-31-OD-03 | Is a second signature by the operator required? | The device key signs the request. The operator's decision is bound by the digest in the approval record (XGN-16). A separate operator signature has not been decided. |
| XGN-31-OD-04 | How long is a retired signing key kept for verification? | It must be at least the maximum lifetime of an approval bound to it. The approval lifetime is 10 minutes by default; the retention period has not been fixed. |

## 11. Related specifications

- **Depends on:** XGN-01 (vocabulary and identifiers), XGN-02 (envelope, approval and execution contracts), XGN-06 (envelope lifecycle), XGN-08 (canonical representation).
- **Referenced by:** XGN-16 (approval), XGN-17 (execution authority), XGN-21 (state machine), XGN-25 (audit and retention), XGN-26 (conformance), XGN-06.
- **Predecessor material:** the draft-hash concept from the *Gitana Surface Map (Composed GET): Proposal* and from the *Navigation Transport Specification* (0.1-draft) §9.4, and the full-envelope binding identified as critical in technical review A deficiency 4 and technical review B issue C-05. The two-digest model, the exposed length and the signature placement are new in this set.
- **Out of scope:** canonical representation (XGN-08), the approval decision (XGN-16), dispatch and reconciliation (XGN-17, XGN-22), key lifecycle (XGN-05).
