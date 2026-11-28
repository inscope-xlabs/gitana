# XGN-28: XGN Composed GET Command Protocol

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-28 | **Group:** C — Transport
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-07, XGN-10 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** none outstanding at this level

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the command protocol carried by the Composed GET transport: the complete command catalogue, the fixed parameter order, the session sequence number semantics, command batching, the `FIX` template, the field metadata per operation, the encoding rules, and the `show` read.

In scope: the eleven commands `new`, `set`, `append`, `replace`, `unset`, `batch`, `preview`, `show`, `commit`, `cancel` and `status`; the parameters each accepts; the states in which each is offered; the effect of each; whether it consumes the capability; whether it advances the session sequence number; whether it is idempotent; its failure codes; the parameter order; the outcome table for every sequence scenario; the batch grammar; and the encoding rules.

Out of scope: the capability format and lifecycle (XGN-29), the HTTP surface and the transport limits (XGN-10), the panel block semantics (XGN-14), the operation catalogue and its argument semantics (XGN-19), the draft model (XGN-07), validation and canonicalisation rules (XGN-08), request integrity and digests (XGN-31), the error catalogue (XGN-23), and the state machine (XGN-21).

## 2. The command catalogue

A command is a single agent-issued instruction. The catalogue below is authoritative for the Composed GET transport and MUST be generated from the same definitions the validator uses (XGN-03 CD-01). The capability description's `commands[]` array and the `COMMANDS` block of a panel MUST agree with this catalogue for the current state (XGN-03 CD-04).

| Command | Parameters, in fixed order | Offered in | Effect | Consumes capability | Advances `seq` | Idempotent | Failure codes |
|---|---|---|---|---|---|---|---|
| `new` | `op` | `HUB`, `RESULT` | Opens an empty draft for the operation named by `op`, for the current step. | Yes | Yes | No | `STATE_INVALID`, `OPERATION_NOT_PERMITTED`, `DRAFT_EXISTS`, `SCOPE_VIOLATION`, `VALIDATION_FAILED` |
| `set` | `field`, `value` | `DRAFT`, `PREVIEW` | Sets one short field to a complete value, replacing any previous value. | Yes | Yes | Yes, for the same field and value | `STATE_INVALID`, `FIELD_UNKNOWN`, `FIELD_FIXED`, `VALIDATION_FAILED`, `SCOPE_VIOLATION`, `DRAFT_LIMIT` |
| `append` | `field`, `enc`, `data` | `DRAFT`, `PREVIEW` | Adds one part to a long field. `enc` is optional and defaults to base64url. | Yes | Yes | No | `STATE_INVALID`, `FIELD_UNKNOWN`, `FIELD_NOT_LONG`, `VALIDATION_FAILED`, `DRAFT_LIMIT`, `PART_LIMIT` |
| `replace` | `part`, `enc`, `data` | `DRAFT`, `PREVIEW` | Replaces the content of one existing part, keeping the part identifier and its position. | Yes | Yes | Yes, for the same part and data | `STATE_INVALID`, `PART_UNKNOWN`, `VALIDATION_FAILED`, `DRAFT_LIMIT` |
| `unset` | `field` | `DRAFT`, `PREVIEW` | Clears a field. The field returns to `MISSING`. | Yes | Yes | Yes | `STATE_INVALID`, `FIELD_UNKNOWN`, `FIELD_FIXED`, `VALIDATION_FAILED` |
| `batch` | `ops` | `DRAFT`, `PREVIEW` | Applies a bounded list of draft changes atomically, in one command, under one sequence number. Section 5. | Yes | Yes | Yes, for the same `ops` | `STATE_INVALID`, `VALIDATION_FAILED`, `BATCH_LIMIT`, `DRAFT_LIMIT`, `SCOPE_VIOLATION` |
| `preview` | none | `DRAFT`, `PREVIEW` | Checks completeness and validity. Returns field sizes, the normalised path, per-field digests, the draft digest and the applicable limits. Echoes no content. | Yes | Yes | Yes | `STATE_INVALID`, `DRAFT_INCOMPLETE`, `SCOPE_VIOLATION`, `VALIDATION_FAILED` |
| `show` | `field`, `page` | `PREVIEW` | Explicit read of the agent's own draft content, in bounded, marked data pages. Section 9. | Yes | Yes | Yes, for the same field and page | `STATE_INVALID`, `FIELD_UNKNOWN`, `PAGE_OUT_OF_RANGE`, `RATE_LIMITED` |
| `commit` | `hash` | `PREVIEW` | Submits the draft for approval. `hash` MUST equal the draft digest returned by the last `preview`. Creates the request envelope (XGN-06) and moves the session to `APPROVAL` or `RESULT`. | Yes | Yes | Yes, for the same draft digest, inside the idempotency window | `STATE_INVALID`, `DRAFT_INCOMPLETE`, `DRAFT_HASH_MISMATCH`, `REQUEST_EXISTS`, `SCOPE_VIOLATION`, `REPO_NOT_PRIVATE`, `SESSION_SUSPENDED` |
| `cancel` | none | `DRAFT`, `PREVIEW` | Discards the draft and returns the session to `HUB`. The draft content is destroyed. | Yes | Yes | Yes | `STATE_INVALID`, `SESSION_SUSPENDED` |
| `status` | none | every state | Reads the authoritative session and request state. A read, not a command. | **No** | **No** | Yes | `SESSION_EXPIRED`, `SESSION_REVOKED`, `SESSION_SUSPENDED`, `RATE_LIMITED`, `DEVICE_OFFLINE` |

**Rules.**

- A command not offered in the current state MUST be refused as `STATE_INVALID`, with the legal states listed.
- A command offered in the current state but issued with an illegal parameter combination MUST be refused as `VALIDATION_FAILED`.
- `new` MUST NOT carry short fields (XGN-DEC-005). Efficiency is achieved with `batch`, not by widening `new`.
- `status` MUST NOT carry a `seq` and MUST NOT consume or advance anything (XGN-DEC-023).
- A command MUST NOT be issued with a parameter that its definition does not list, and MUST NOT repeat a parameter (XGN-01 section 6.1).

## 3. Parameter order and the canonical command form

The complete form of a command is:

```text
<base>&cmd=<command>[&<param>…]&seq=<n>
```

| Rule | Requirement |
|---|---|
| Q-O1 | `cmd` is always the first parameter after `t`. |
| Q-O2 | The command's own parameters follow `cmd` in the fixed order given in section 2. |
| Q-O3 | `seq` is always the last parameter. |
| Q-O4 | A command whose parameters appear in any other order MUST be refused as `VALIDATION_FAILED`. The order is checked before decoding. |
| Q-O5 | An optional parameter that is not supplied MUST be omitted, not supplied empty. |
| Q-O6 | An unknown parameter MUST be refused as `VALIDATION_FAILED`. A repeated parameter MUST be refused as `VALIDATION_FAILED`. |
| Q-O7 | Decoding happens exactly once, at the authoritative validation boundary (XGN-01 section 6.1). |

**Truncation detection.** Because `seq` is last, a link cut short at the end is detectable: it carries no `seq`. Such a request MUST be refused as `SEQ_MISSING`, and MUST NOT consume the capability and MUST NOT advance the sequence number (Q-13).

**Note on the capability description.** The `commands[]` array in the capability description example in XGN-02 section 5 lists `append` with parameters `["field","data"]`. The canonical order for `append` is `field`, `enc`, `data`, where `enc` is optional; the canonical order for `replace` is `part`, `enc`, `data`. **This module is authoritative for parameter order.** The capability description MUST publish the full canonical order, including optional parameters, and MUST be generated from the same definitions the validator uses (XGN-03 CD-01).

## 4. Session sequence number semantics

The session sequence number (`seq`) is the sequence number of the next command the session expects. It is issued by the runtime in every panel. The scenarios below are exhaustive for a single session.

| # | Scenario | Capability consumed | `seq` advances | Next `seq` | Capability after the command | Note |
|---|---|---|---|---|---|---|
| 1 | Command accepted | Yes | Yes | previous + 1 | A fresh capability is issued | The effect is exactly what the command specifies. |
| 2 | Command refused (validation, scope, state, limit, integrity) | Yes, retired without effect | **No** | Unchanged | A fresh capability is issued | XGN-DEC-024. The draft, the pending envelope and the next sequence number are unchanged (INV-11). |
| 3 | `seq` greater than expected (a gap) | Yes, retired without effect | No | Unchanged | A fresh capability is issued | Refused as `SEQ_INVALID`. A gap never skips a sequence number. |
| 4 | `seq` lower than expected and not the immediately preceding accepted value | Yes, retired without effect | No | Unchanged | A fresh capability is issued | Refused as `SEQ_INVALID`. |
| 5 | `seq` duplicated: the same command re-issued at the same sequence number with the same capability | Already consumed | No | Unchanged | The capability carried by the cached reply is returned | Inside the idempotency window this is an exact retry and produces no second effect (XGN-DEC-007). Outside the window it is a reuse and the session is suspended (XGN-29). |
| 6 | Exact retry: byte-identical command, same capability, same body digest, inside the 90-second window | No second effect | No | Unchanged | The capability carried by the cached reply is returned | The cached reply is returned byte for byte. |
| 7 | Exact retry after the 90-second window | Already consumed | No | Unchanged | None | Refused as `LINK_CONSUMED`. Recorded as `LINK_REUSE`; the session is suspended. Not recoverable by the agent alone. |
| 8 | `status` | No | No | Unchanged | Unchanged; the current base remains valid | A read. Rate limited (XGN-DEC-023). |
| 9 | Capability base with no `cmd` | No | No | Unchanged | Unchanged | An inert read returning the current panel (XGN-DEC-017). |
| 10 | `cmd` present, `seq` absent | Yes, retired without effect | No | Unchanged | A fresh capability is issued | Refused as `SEQ_MISSING`. A truncated link is detected this way. |
| 11 | Concurrent command on a capability already in flight | No | No | Unchanged | Unchanged for the refused request | Refused as `CONCURRENT_COMMAND`. Exactly one transition occurs (XGN-10 P-18). |

**Q-A1.** A refused command retires the capability it was presented with, without producing an effect, and the reply carries a fresh capability. This satisfies INV-08 (a capability has exactly one effect) and makes the refusal path uniform with the acceptance path: in both cases the presented capability is spent and a new one is issued.

**Q-A2.** Because a refusal changes nothing except issuing a fresh capability, the lost-reply path works for a refused command: the agent re-sends the identical URL inside the window and receives the cached refusal reply, which carries the capability it needs to continue (XGN-29).

**Q-A3.** The next sequence number changes only on acceptance. It MUST NOT be consumed by a refusal, and it MUST NOT be skipped (XGN-DEC-024, INV-10).

**Q-A4.** A duplicate sequence number never produces a new effect. A gap is always refused. A sequence number MUST NOT be reused after the session resumes (XGN-01 section 4).

## 5. Batching

`batch` carries a bounded list of draft changes in one command, under one sequence number (XGN-DEC-021).

**Grammar.** The `ops` parameter value is parsed after the single decoding step:

```text
ops    := item ("," item)*
item   := verb ":" name "=" value
verb   := "set" | "append" | "replace" | "unset"
name   := field name, for set / append / unset
        | part identifier, for replace
value  := base64url payload without padding; empty for unset
```

| Rule | Requirement |
|---|---|
| Q-B1 | A batch MUST contain at most 32 operations. A larger batch MUST be refused as `BATCH_LIMIT`. |
| Q-B2 | A batch MUST be atomic. If any operation in the batch fails validation, MUST fail a precondition, or MUST exceed a limit, then NO operation in the batch is applied, the draft is unchanged, and the next sequence number is unchanged. There MUST be no partial application. |
| Q-B3 | The operations MUST be applied in the order given. The resulting state of a field touched more than once is the state produced by the last operation that touched it. |
| Q-B4 | A batch consumes one capability and advances the sequence number by exactly one, whatever its length. |
| Q-B5 | A batch produces exactly one reply and exactly one next capability. |
| Q-B6 | A batch MUST contain only draft-mutating operations: `set`, `append`, `replace`, `unset`. It MUST NOT contain `new`, `preview`, `show`, `commit`, `cancel` or `status`. A batch containing any other verb MUST be refused as `VALIDATION_FAILED`. |
| Q-B7 | The total decoded bytes of all items MUST NOT exceed 2048 bytes, and the `ops` value MUST NOT exceed the applicable encoded-data limit (XGN-10 section 6). |
| Q-B8 | Inside a batch, every payload MUST be base64url without padding. `enc=text` is available only for the top-level `append` and `replace` commands, so that the batch grammar needs no nested encoding. |
| Q-B9 | A part identifier MUST NOT collide with a field name. Field names are drawn from the operation catalogue (XGN-19); part identifiers are opaque values minted by the runtime (XGN-DEC-025). |

**Example.**

```text
GET <base>&cmd=batch&ops=set:path%3DZG9jcy9ub3Rlcy5tZA,set:message%3DQWRkIG5vdGVz,append:content%3DYWRkIG5vdGVz&seq=8
```

The `set` of `path` and `set` of `message` and the first `append` of `content` are one command, one sequence number, one reply. If any of the three fails, none is applied.

## 6. The `FIX` template

When a command is refused, the reply carries a `FIX` template in the `LAST` block. It tells the agent what was wrong and what the permitted correction is (XGN-28 resolves the inherited gap where the template was referenced but never defined).

| Element | Content |
|---|---|
| `code` | The stable error code from the error catalogue (XGN-23). |
| `field` | The parameter the correction applies to, or `-` when the correction is not field-specific. |
| `cause` | One generated sentence naming the rule that was broken. It MUST NOT quote the rejected value. |
| `current` | The current state of the affected field: `fixed`, `missing`, `set <n> bytes`, `open <n> bytes, <m> parts`, or `unset`. Never a value. |
| `allowed` | The permitted correction: the permitted values, the permitted range, or the permitted next command. |
| `retry` | The corrected command template, on the newest capability and the current next sequence number. |

**Rules.**

- Q-F1 | The `FIX` template MUST be present for every refused command and MUST NOT be present for an accepted command.
- Q-F2 | Every element MUST be generated by Gitana. No element MUST be derived from agent-written content or repository content.
- Q-F3 | The template MUST NOT contain a capability value and MUST NOT contain a link.
- Q-F4 | The permitted corrections are limited to: change the named field's value within the stated constraint; change the named parameter; use a different command from the legal set; or cancel the draft. It MUST NOT suggest widening scope, changing the grant, or any operator-plane action.
- Q-F5 | The `cause` element MUST NOT reproduce the rejected value, even a length or a shape that would let the value be reconstructed.

## 7. Field and operation metadata

Each operation of the catalogue declares its fields. Some are fixed by the grant and the agent MUST NOT write them; the others are written by the agent. The authoritative argument list of each operation is owned by XGN-19; this table is the mapping onto the command surface.

| Operation | Fields fixed by the grant | Fields written by the agent | Long field | Preconditions that apply |
|---|---|---|---|---|
| `create_branch` | `repo`, `base`, `new_branch` | none | — | `new_branch` MUST fall inside the granted branch scope (XGN-DEC-010); `base` MUST be the named base branch |
| `write_file` | `repo`, `branch` | `path`, `message`, `content` | `content` | Expected existing-file blob digest when the path exists; expected branch head digest (XGN-DEC-020) |
| `create_pull_request` | `repo`, `head`, `base` | `title`, `body` (optional) | `body` | `head` MUST equal the working branch; head and base MUST be in the same scoped repository |

**Rules.**

- Q-M1 | A `set` on a grant-fixed field MUST be refused as `FIELD_FIXED`.
- Q-M2 | A `set` or `append` on a field the operation does not declare MUST be refused as `FIELD_UNKNOWN`.
- Q-M3 | An `append` on a field that is not a long field MUST be refused as `FIELD_NOT_LONG`.
- Q-M4 | An `unset` on a grant-fixed field MUST be refused as `FIELD_FIXED`.
- Q-M5 | Step dependencies MUST be enforced: the branch written by `write_file` MUST be the branch created by `create_branch`, and the pull request head MUST be that branch (XGN-19).
- Q-M6 | The panel MUST mark a grant-fixed field as fixed and MUST NOT invite the agent to write it (XGN-14).

## 8. Encoding rules

| Rule | Requirement |
|---|---|
| Q-E1 | The default encoding for arbitrary content MUST be base64url without padding (XGN-DEC-026). |
| Q-E2 | `enc=text` is permitted only where the command defines an `enc` parameter, and only for short, simple text. The decoded value MUST be at most 140 bytes, MUST be valid UTF-8, and MUST contain no control character other than TAB, LF and CR. |
| Q-E3 | `enc` MUST be one of `text` or `b64`. Any other value MUST be refused as `VALIDATION_FAILED`. |
| Q-E4 | `enc` MUST appear in its fixed position: after `field` for `append`, after `part` for `replace`. It MUST NOT be moved to the end or made to follow `data`. |
| Q-E5 | Every text part MUST be valid UTF-8 on its own. A multi-byte character MUST NOT be split across two parts. A part that ends mid-character MUST be refused as `VALIDATION_FAILED`. |
| Q-E6 | Percent-encoding MUST follow XGN-01 section 6.1. A space is `%20`. `+` is a literal plus sign and MUST NOT be treated as a space. Reserved characters MUST be percent-encoded in a value. |
| Q-E7 | Decoding happens exactly once. A doubly encoded value MUST decode once and MUST NOT be decoded again. |
| Q-E8 | A value that is not valid percent-encoding, or that decodes to invalid UTF-8, MUST be refused as `VALIDATION_FAILED` and MUST NOT be repaired (XGN-08, XGN-30 PRB-21). |

## 9. The `show` read

`show` is the only way agent-written content leaves the runtime. It reads the agent's own draft content back to the agent.

| Rule | Requirement |
|---|---|
| Q-S1 | `show` MUST read only the agent's own draft. It MUST NOT read repository content, and it MUST NOT read content belonging to another session or another draft. |
| Q-S2 | `field` is optional. When it is supplied, only that field is returned. When it is omitted, every field is returned, in the canonical field order of the operation. `page` is optional and defaults to 1. |
| Q-S3 | A page carries at most 512 bytes of decoded content. The runtime MUST split a field across pages at part boundaries where it can, and MUST NOT split a multi-byte character. |
| Q-S4 | Content is returned in ascending part order, and within a part in byte order. The page number is 1-based. |
| Q-S5 | The reply states the total number of pages for the field and marks the final page with an end-of-file marker. A field that fits in one page reports `of 1`. |
| Q-S6 | A `show` command returns at most 16 pages. A request for a page beyond the end of a field MUST be refused as `PAGE_OUT_OF_RANGE`. A page beyond 16 MUST be refused as `VALIDATION_FAILED`. |
| Q-S7 | The content MUST appear only inside a marked data region, with an explicit byte count, as defined in XGN-14 section 8. Content MUST NOT be escaped, re-encoded or summarised. |
| Q-S8 | The runtime MUST NOT cache a content-bearing reply. The reply is generated for that request only (XGN-DEC-027, XGN-29). |
| Q-S9 | `show` MUST be rate limited on the same allowance as `status` where the reply carries content. |

## 10. Command protocol requirements

| ID | Requirement |
|---|---|
| Q-01 | The command catalogue in section 2 MUST be the authoritative list for this transport, and MUST be generated from the same definitions the validator uses. |
| Q-02 | A command offered in the current state MUST be executable; a command not offered MUST NOT be executable. |
| Q-03 | `cmd` MUST be first after `t`, the command's own parameters MUST follow in the fixed order, and `seq` MUST be last. |
| Q-04 | A command whose parameters are in a different order MUST be refused. |
| Q-05 | A request carrying `cmd` without `seq` MUST be refused as `SEQ_MISSING`, and MUST NOT consume the capability. |
| Q-06 | An unknown parameter and a repeated parameter MUST both be refused. |
| Q-07 | A refused command MUST NOT consume the sequence number and MUST NOT change the next sequence number. |
| Q-08 | The next sequence number MUST advance only on acceptance, and MUST NOT be skipped. |
| Q-09 | A duplicate sequence number MUST NOT produce a second effect. |
| Q-10 | `status` MUST NOT consume a capability, MUST NOT advance the sequence number, MUST NOT alter a draft, a request or the grant, and MUST be rate limited. |
| Q-11 | An exact retry inside the 90-second window MUST return the cached reply byte for byte and MUST produce no second effect. |
| Q-12 | An exact retry outside the window MUST NOT be recoverable by the agent alone, MUST be recorded as a reuse, and MUST suspend the session. |
| Q-13 | A batch MUST carry at most 32 operations, MUST be atomic, MUST be applied in a deterministic order, and MUST consume one capability and one sequence number. |
| Q-14 | A failed batch MUST leave the draft and the next sequence number unchanged. |
| Q-15 | Every refused command MUST carry a `FIX` template whose elements are generated and whose permitted corrections are bounded. |
| Q-16 | Grant-fixed fields MUST NOT be writable, and a write to one MUST be refused. |
| Q-17 | Content MUST be base64url by default; `enc=text` MUST be restricted to short, simple text and MUST be marked. |
| Q-18 | Decoding MUST happen exactly once, at the validation boundary. |
| Q-19 | `show` MUST return only the agent's own draft content, in pages of at most 512 bytes, in a marked data region, and MUST NOT be cached. |
| Q-20 | A part identifier MUST be stable, independent of the sequence number that created it, and MUST keep its position when replaced (XGN-DEC-025). |
| Q-21 | `new` MUST NOT carry short fields. |
| Q-22 | The panel and the capability description MUST agree with this catalogue for the current state. |

## 11. Decisions recorded

| ID | Decision | Where it is stated |
|---|---|---|
| `XGN-DEC-005` | `new` opens an empty draft for one operation. It does not carry short fields. | Section 2. |
| `XGN-DEC-012` | Draft and chunk limits: 2 KB of encoded data per command, 64 KB total draft, 64 parts per field, 512 commands per session. | Section 5, XGN-10 section 6. |
| `XGN-DEC-021` | Command batching: `batch` carries a bounded list of field changes, applied atomically, in one command, with one sequence number. | Section 5. |
| `XGN-DEC-023` | Status polling: `status` is a read. It consumes nothing, advances nothing, and is rate limited. | Section 2, section 4. |
| `XGN-DEC-024` | Rejected commands and sequence numbers: a rejected command does not consume a sequence number and does not change the next sequence number. | Section 4. |
| `XGN-DEC-025` | Part identity: parts carry stable identifiers independent of the sequence number that created them, and ordering is explicit. | Section 2, section 5. |
| `XGN-DEC-026` | Default content encoding: base64url for arbitrary content; percent-encoded text is permitted only for short, simple text and MUST be marked `enc=text`. | Section 8. |

## 12. Open decisions

None outstanding at this level.

## 13. Related specifications

- **Depends on:** XGN-01 (vocabulary, identifier scheme, percent-encoding conventions, decision register), XGN-02 (shared contracts), XGN-03 (surface map and command/state matrix), XGN-07 (draft model), XGN-10 (transport surface and limits).
- **Referenced by:** XGN-14, XGN-29, XGN-30, XGN-08, XGN-21, XGN-26.
- **Predecessor material:** the typewriter command table and encoding rules of the *Navigation Transport Specification* (0.1-draft, 25 November 2026) section 9.4, and the command table, encoding section, worked example and error table of the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026). The `replace`, `show`, `status`, `enc=text`, `body` long field, parameter order and `batch` proposals are adopted here; the part-identification and size-estimate defects noted in technical review A are corrected here.
- **Out of scope:** the capability lifecycle, the HTTP surface, the panel rendering, the operation catalogue, and every question of grant, policy, approval and execution authority.
