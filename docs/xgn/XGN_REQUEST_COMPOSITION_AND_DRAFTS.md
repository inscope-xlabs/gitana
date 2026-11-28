# XGN-07: XGN Request Composition and Drafts

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-07 | **Group:** B — Session and request lifecycle
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-06 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 12

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines how an agent composes a request: the **XGN Draft**, the part model behind long fields, the field states the agent sees, the draft operations, progressive validation on arrival, and the conversion of a draft into an immutable pending envelope.

**Out of scope:** the request envelope shape and lifecycle (XGN-06), canonicalisation of values (XGN-08), the request digest and approval binding (XGN-31), the command wire forms (XGN-28), the panel layout (XGN-14), the operation catalogue and provider preconditions (XGN-19), and the error catalogue (XGN-23).

### 1.1 The inherited inconsistency this module resolves

The inherited material stated that a draft is discarded on commit, and separately required the same content to be shown to the operator for approval and then dispatched to the external service. Both cannot hold for one object. XGN resolves this by defining **two distinct objects** with separate retention rules: a mutable draft that the agent edits, and an immutable pending envelope that carries the committed content through approval and dispatch.

## 2. The two composition objects

| Property | XGN Draft | Pending envelope |
|---|---|---|
| Mutability | Mutable; the agent edits it | Immutable once committed |
| Held by | Trusted runtime | Trusted runtime |
| Content | Agent-authored values and parts | Complete content bytes, part order and all normalised arguments |
| Created by | `new` | An accepted `commit` |
| Lifecycle owner | XGN-07 | XGN-06 |
| Deleted when | Committed, cancelled, expired, superseded, session suspended or revoked, or runtime restart | Request resolved, or request invalidated |
| May be presented for approval | MUST NOT | MUST |
| May be dispatched | MUST NOT | MUST |

| ID | Requirement |
|---|---|
| D-01 | A draft MUST NOT be presented to the operator as the subject of an approval. An approval MUST reference the pending envelope's request digest (XGN-31). |
| D-02 | On an accepted `commit`, the runtime MUST freeze the draft, copy the canonical operation, normalised arguments, part order and complete content bytes into a pending envelope, compute the draft digest and the envelope digest (XGN-31), and then discard the draft object. |
| D-03 | The pending envelope MUST retain the complete content bytes until the request is resolved, because the operator MUST be shown the exact content that will be dispatched and the execution layer MUST dispatch exactly those bytes (INV-06). |

### 2.1 Retention, expiry and deletion

| Object | Retained until | Expiry | Deletion |
|---|---|---|---|
| Open draft | 15 minutes after the last accepted command that modified it | Transitions to `EXPIRED` | Content bytes deleted; the draft record and its digest are retained as an audit event |
| Pending envelope awaiting approval | The approval is decided, or `expires_at` is reached, whichever is first | `expires_at` MUST NOT exceed the session expiry (XGN-02 §4.1); an approval expires 10 minutes after it is granted | Content deleted on decline or on envelope expiry; envelope metadata and digest retained |
| Dispatched request | The outcome is recorded or reconciled | — | Content deleted once the outcome is recorded and the audit event is written; envelope metadata and digests retained for the audit retention period (XGN-25) |

### 2.2 Runtime restart

| ID | Requirement |
|---|---|
| D-04 | On runtime restart, draft content MUST be discarded and the session MUST return to the hub with no open draft (XGN-DEC-028). |
| D-05 | On runtime restart, a pending envelope that has not been dispatched MUST be invalidated. Its content MUST NOT be carried across the restart. The agent MUST re-commit, which produces a new request id, a new envelope digest and a new approval. |
| D-06 | On runtime restart, a request that was already dispatched MUST retain its durable execution record so that the outcome can be reconciled (XGN-22). Its approval MUST NOT authorise any further dispatch. |
| D-07 | Session, grant, request, approval and execution records MUST survive a runtime restart. |
| D-08 | If the runtime cannot determine whether a request was dispatched, it MUST NOT dispatch it again and MUST move the session to the wait state for reconciliation (XGN-22). |

## 3. Draft lifecycle

| State | Meaning | Agent may | Exit |
|---|---|---|---|
| `OPEN` | Created by `new`; fields may be missing | edit, preview, show, cancel | `COMPLETE` on preview; `CANCELLED`; `EXPIRED`; `SUPERSEDED` |
| `COMPLETE` | Every required field is present and validated | edit, preview, show, cancel | `PREVIEWED` on preview; `EXPIRED`; `SUPERSEDED` |
| `PREVIEWED` | A preview was produced and a draft digest issued | show, commit, cancel, or an edit, which returns the draft to `OPEN` and voids the digest | `COMMITTED`; `OPEN` on edit; `CANCELLED`; `EXPIRED`; `SUPERSEDED` |
| `COMMITTED` | Converted to a pending envelope | status only | Terminal for the draft; the envelope continues under XGN-06 |
| `CANCELLED` | Discarded by the agent | none | Terminal |
| `EXPIRED` | Idle timeout reached | none | Terminal |
| `SUPERSEDED` | Replaced by a newer draft | none | Terminal |

| ID | Requirement |
|---|---|
| D-09 | The runtime MUST transition a draft to `EXPIRED` 15 minutes after the last accepted command that modified it. |
| D-10 | When a draft is superseded, the runtime MUST discard the superseded draft's content and MUST NOT merge it into the new draft. |
| D-11 | A draft in `COMMITTED` state MUST NOT be edited, previewed, shown or cancelled. |
| D-12 | The runtime MUST reject an operation that is not legal for the draft's current state, with `STATE_INVALID` and the legal states listed (XGN-03 §3.2). |

## 4. The part model

A long field (`content`, `body`) is carried as an ordered list of parts.

| Property | Rule |
|---|---|
| Part identifier | `prt_` + 8 base64url characters, minted by the runtime when the part is accepted. It is stable: it does not change when the part is replaced, and it is independent of the session sequence number that created it (XGN-DEC-025) |
| Ordering | Explicit. The draft stores the parts in order of acceptance, and a replaced part keeps its position |
| Max parts per field | 64 (XGN-DEC-012) |
| Max decoded bytes per field | 65536 |
| Max total draft bytes | 65536 across all fields and all parts |
| Part payload | The decoded bytes of the part. Each part MUST be valid UTF-8 on its own when the field is textual |

| ID | Requirement |
|---|---|
| D-13 | The value of a long field MUST be reconstructed by concatenating the payloads of its live parts, in stored order. |
| D-14 | `replace` with a non-empty payload MUST replace the payload of the named part, keeping that part's identifier and position (XGN-DEC-051). |
| D-15 | `replace` with an empty payload MUST remove the named part from the reconstructed value. The identifiers of the surviving parts MUST keep their relative order. A removed part's identifier MUST be retired and MUST NOT be reused. |
| D-16 | `unset` on a long field MUST remove the field and all of its parts. |
| D-17 | A `replace` that names a part belonging to another field, or a part that does not exist, MUST be rejected with `VALIDATION_FAILED` and MUST leave the draft unchanged. |
| D-18 | The runtime MUST record the ordered list of live part identifiers in the draft, and that list MUST be the value digested as the part order (XGN-31). |

## 5. Field states and labels

| State | Meaning | Agent may change it |
|---|---|---|
| `FIXED` | The value is fixed by the grant and is not agent-writable | No |
| `MISSING` | A required field that has no value yet | Yes |
| `SET` | A short field carrying one complete value | Yes |
| `OPEN` | A long field whose value is carried as parts | Yes, by parts |

| ID | Requirement |
|---|---|
| D-19 | Every field of the open draft MUST be reported in exactly one of the four states `FIXED`, `MISSING`, `SET`, `OPEN`. |
| D-20 | A field in state `FIXED` MUST be shown without its value. Its value is a grant attribute and is shown by the task block, not by the draft block. |
| D-21 | A field in state `SET` or `OPEN` MUST be shown with its decoded byte count and MUST NOT show its value. |
| D-22 | The draft block MUST NOT echo the value of any agent-written field. |

### 5.1 Label grammar and the ambiguity this module removes

The draft block renders each field as:

```text
<field> <state>[, <n> bytes][, parts: <count>][, part ids: <id>,<id>,...]
```

| ID | Requirement |
|---|---|
| D-23 | The state word MUST be exactly one of `fixed`, `missing`, `set`, `open`. |
| D-24 | `parts:` MUST always carry the **count** of live parts. |
| D-25 | Part identifiers MUST always be listed separately under `part ids:`. A part identifier MUST NOT be abbreviated and MUST NOT be rendered as a bare number. |
| D-26 | A session sequence number MUST NOT be used as a part identifier and MUST NOT be rendered as a part count. |
| D-27 | A count and an identifier MUST NOT share one token. |

The inherited label `parts: 7` was ambiguous: it read both as "seven parts" and as "the part created by sequence 7", and the inherited worked example rendered `content open, 9 bytes, parts: 7` after a single append of nine bytes. Under this module the same state renders as:

```text
  content open, 9 bytes, parts: 1, part ids: prt_7Kd2mQ1x
```

## 6. Progressive validation on arrival

| ID | Requirement |
|---|---|
| D-28 | Every field value MUST be validated when the command that carries it arrives, and not only at preview. |
| D-29 | Charset, control-character and size validation MUST run on every value and every part at arrival (XGN-08 §3). |
| D-30 | Path, branch and repository canonicalisation and containment MUST run when those values arrive, not when the draft is previewed. |
| D-31 | The runtime MUST enforce the part-count limit and the total-draft limit incrementally, at the arrival of each part. |
| D-32 | The runtime MUST re-run the full field validation set at preview, so that a value invalidated by a later change to another field is caught. |
| D-33 | A rejected command MUST leave the draft, the pending envelope and the next session sequence number unchanged, and the runtime MUST issue a fresh capability (XGN-DEC-024, INV-11). |

## 7. Draft operations

| Operation | Parameters | Effect | Legal in |
|---|---|---|---|
| `new` | `op` | Opens an empty draft for one operation | hub, result |
| `set` | `field`, `value` | Sets a short field | draft, preview |
| `append` | `field`, `data`, optional `enc` | Adds a part to a long field | draft, preview |
| `replace` | `part`, `data`, optional `enc` | Replaces or removes a part | draft, preview |
| `unset` | `field` | Clears a field and its parts | draft, preview |
| `batch` | `ops` | Applies a bounded list of changes | draft, preview |
| `preview` | — | Validates completeness; returns sizes, canonical values and the draft digest | draft, preview |
| `show` | `field`, `page` | Explicit read of the agent's own draft content | per the command matrix in XGN-03 and XGN-14 |
| `cancel` | — | Discards the draft | draft, preview |

| ID | Requirement |
|---|---|
| D-34 | `new` MUST open an empty draft and MUST NOT carry short fields (XGN-DEC-005). Efficiency is obtained with `batch`. |
| D-35 | `new` MUST fail if a draft is already open (INV-07). |
| D-36 | `batch` MUST apply all of its changes or none of them, in the order listed (XGN-DEC-021). A batch MUST carry at most 32 entries, and each entry MUST be within the per-command data limit. |
| D-37 | `preview` MUST validate that every required field is present, that every field has passed arrival validation, and that the step dependencies hold (XGN-08 §8). |
| D-38 | `preview` MUST return the draft digest, the canonical path, the decoded byte counts and the part count. It MUST NOT return field values. |
| D-39 | `commit` is owned by XGN-28 and the envelope lifecycle by XGN-06. This module defines only the conversion described in D-02. |
| D-40 | `cancel` MUST discard the draft's content and transition the draft to `CANCELLED`. It MUST NOT cancel a request that is already committed. |

## 8. The `show` read

| Property | Value |
|---|---|
| Unit | Bytes of the reconstructed field value |
| Page size | 512 bytes |
| First page | Page 1, byte offset 0 |
| Final page | May be shorter than 512 bytes |
| Ordering | Byte order of the reconstructed field value |
| End-of-file marker | `END` after the data block on the final page; `MORE` on every other page |
| Max pages | 128 pages per field (65536 / 512) |
| Rate limit | The same read rate limit as `status` (XGN-23) |
| Part boundaries | Reported in the page header, never inside the data |

```text
READ field=content page=1 bytes=0-511 of=900 parts=prt_7Kd2mQ1x:0-255,prt_4Pw9zT7q:256-511
DATA
<bytes>
MORE
```

| ID | Requirement |
|---|---|
| D-41 | `show` MUST return exactly the requested page and MUST NOT return bytes from any other page. |
| D-42 | The data block MUST contain only the content bytes and no markers, so that a page is byte-exact. |
| D-43 | The page header MUST name the part identifiers that contributed bytes to that page and the byte range each contributed. |
| D-44 | The runtime MUST mark the block as untrusted data and MUST NOT interpret, execute or summarise the bytes. |
| D-45 | Content MUST NOT appear in any other part of a reply. Outside an explicit read, only sizes, counts and digests appear (INV-13). |
| D-46 | A `show` reply MUST NOT be cached by the gateway (XGN-DEC-027), and its content MUST NOT be written to the gateway's idempotency cache (XGN-00 §5.1). |

## 9. Session constraints

| ID | Requirement |
|---|---|
| D-47 | A session MUST hold at most one open draft and at most one unresolved request (INV-07). |
| D-48 | The runtime MUST reject `new` when a draft is open, and MUST reject a commit when an unresolved request exists. |
| D-49 | The runtime MUST reject `show` for a field that is not a long field of the current draft, and MUST reject a page number below 1. |

## 10. Content and the gateway

| ID | Requirement |
|---|---|
| D-50 | The gateway MUST NOT hold, reconstruct or retain a draft. The gateway relays individual command chunks and holds none. |
| D-51 | The assembled draft, the pending envelope and all content state MUST exist only on the trusted runtime (XGN-00 §5.1, XGN-20). |
| D-52 | The gateway MUST NOT cache a reply that carries agent-authored content (XGN-DEC-027), and MUST NOT interpret field values or decide that an operation is permitted (XGN-20). |

## 11. Decisions recorded

| ID | Subject | Decision (authoritative text in XGN-01 §7) | Owner |
|---|---|---|---|
| `XGN-DEC-005` | `new` carrying short fields | `new` opens an empty draft for one operation and carries no short fields. | XGN-07, XGN-28 |
| `XGN-DEC-018` | Draft content lifetime | The draft is discarded on commit, cancel, expiry, suspension, revocation or runtime restart. The committed envelope survives in immutable form until the request is resolved. | XGN-07 |
| `XGN-DEC-021` | Command batching | `batch` carries a bounded list of field changes, applied atomically, in one command, with one sequence number. | XGN-28 |
| `XGN-DEC-024` | Rejected commands and sequence numbers | A rejected command does not consume a sequence number and does not change the next sequence number. | XGN-28 |
| `XGN-DEC-025` | Part identity | Parts carry stable identifiers independent of the sequence number that created them. Ordering is explicit. | XGN-07, XGN-28 |
| `XGN-DEC-028` | Runtime restart behaviour | Draft content is discarded; session, grant, request, approval and execution records survive. A request pending approval at restart is invalidated and requires a new commit. | XGN-07, XGN-21 |
| `XGN-DEC-050` | Two composition objects | A draft is mutable, agent-editable and discarded at commit, cancel, expiry or restart. Its committed form is an immutable pending envelope that retains the complete content until the request is resolved. A draft is never an approval artefact. | XGN-07 |
| `XGN-DEC-051` | Part replacement and removal | A replaced part keeps its identifier and its position. A part replaced with an empty payload is removed from the reconstructed value; the identifiers of the surviving parts keep their relative order. | XGN-07 |
| `XGN-DEC-052` | Field state labels | A draft field is reported in exactly one of four states: `FIXED`, `MISSING`, `SET`, `OPEN`. A part count is always labelled `parts:`; part identifiers are always listed separately under `part ids:`. A sequence number is never a part identifier or a part count. | XGN-07 |

## 12. Open decisions

| ID | Question | Current position |
|---|---|---|
| XGN-07-OD-01 | Should `show` also be offered in the draft state, not only in preview? | The command matrix in XGN-03 offers `show` in preview only. Offering it in draft would remove a preview/show cycle. Not settled. |
| XGN-07-OD-02 | Is 15 minutes the right idle timeout for a draft? | The value is specified here as a concrete limit. It has not been measured against real sessions. |
| XGN-07-OD-03 | May a suspended session resume its open draft instead of discarding it? | Suspension discards the draft, matching XGN-DEC-018. A resume rule would need a retention rule for draft content across suspension. |
| XGN-07-OD-04 | May `new` ever carry short fields? | XGN-DEC-005 says no. Reopening it requires prompt-count evidence from the channel qualification probes (XGN-30). |

## 13. Related specifications

- **Depends on:** XGN-01 (vocabulary and identifiers), XGN-02 (Draft Contract and envelope shape), XGN-03 (surface map and command matrix), XGN-06 (request envelope and lifecycle).
- **Referenced by:** XGN-08, XGN-15, XGN-16, XGN-17, XGN-21, XGN-26, XGN-28, XGN-31, and every transport module.
- **Predecessor material:** the draft, part, `replace` and `show` concepts from the *Gitana Surface Map (Composed GET): Proposal* and from the *Navigation Transport Specification* (0.1-draft) §9.4. The two-object split and the part labelling rules are new in this set.
- **Out of scope:** envelope shape and lifecycle (XGN-06), canonicalisation (XGN-08), digests (XGN-31), panel layout (XGN-14), provider operations (XGN-19).