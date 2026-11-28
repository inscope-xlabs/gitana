# XGN-08: XGN Validation and Canonicalization

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-08 | **Group:** B — Session and request lifecycle
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-04, XGN-07, XGN-19 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 11

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the validation boundary, the canonicalisation rules for every value an agent can supply, the accepted encodings, the size limits, the canonical representation that is digested, the fail-closed mapping from a malformation to an error code, the step dependency checks, and the revalidation that precedes dispatch.

**Out of scope:** the draft and its parts (XGN-07), the request digest and approval binding (XGN-31), the operation catalogue and provider preconditions (XGN-19), the policy decision (XGN-15), the command wire forms (XGN-28), the error catalogue (XGN-23), the grant and scope model (XGN-04), and the transport framing (XGN-09 to XGN-14, XGN-28 to XGN-30).

## 2. The validation boundary

| ID | Requirement |
|---|---|
| V-01 | Every value an agent supplies MUST be validated exactly once, at the authoritative validation boundary, on the trusted runtime (XGN-00 §5). |
| V-02 | The gateway MUST perform shape and size checks only. It MUST NOT decode, canonicalise, interpret or normalise a value (XGN-20). |
| V-03 | Decoding MUST happen exactly once, at this boundary, and MUST NOT be repeated anywhere downstream (XGN-01 §6.1). |
| V-04 | The canonical form MUST be the only representation that reaches the policy engine, the request digest, the approval screen and the execution layer. |
| V-05 | The canonical form MUST be a pure function of the normalised envelope. It MUST NOT depend on the transport, the device locale, the order in which fields were set, or any state outside the envelope. |

## 3. Canonicalisation rules

### 3.1 Paths

| ID | Rule |
|---|---|
| V-06 | A path MUST be a relative path. It MUST NOT begin with `/` or `\`. |
| V-07 | A path MUST NOT be empty. |
| V-08 | A segment MUST NOT be empty. Repeated separators MUST be rejected, not collapsed. |
| V-09 | A `.` segment MUST be removed. |
| V-10 | A `..` segment MUST be rejected. The validator MUST NOT resolve a `..` segment. |
| V-11 | A single trailing separator MUST be removed. `docs/` and `docs` denote the same path and MUST canonicalise to the same value. |
| V-12 | A path MUST NOT contain NUL, a C0 or C1 control character, or U+2028 or U+2029. |
| V-13 | A path MUST already be in Unicode Normalization Form C. The validator MUST NOT normalise silently; a value that changes under normalisation MUST be rejected as ambiguous. |
| V-14 | A path MUST NOT contain a code point in the Unicode categories Cc, Cf, Cs, Co or Cn. |
| V-15 | Path comparison MUST be case-sensitive. |
| V-16 | A canonical path MUST be contained in the granted directory, compared segment by segment. A string prefix comparison MUST NOT be used: `docs-evil/x.md` is not inside the granted directory `docs/`. The same rule governs the scope check itself (XGN-DEC-031, XGN-15). |
| V-17 | A canonical path MUST NOT exceed 1024 decoded bytes, 64 segments, or 255 bytes per segment. |

### 3.2 Branch names

| ID | Rule |
|---|---|
| V-18 | A branch name MUST match `^[A-Za-z0-9._/-]{1,255}$` and MUST NOT exceed 255 bytes. |
| V-19 | A branch name MUST NOT begin or end with `/`, MUST NOT contain `//`, MUST NOT contain `..`, MUST NOT end with `.lock`, MUST NOT contain `@{`, MUST NOT be `@`, and MUST NOT contain a space, a control character, or any of `~ ^ : ? * [ \`. |
| V-20 | Branch names are case-sensitive. The canonical value MUST preserve the case exactly and MUST be compared exactly against the grant. |
| V-21 | A branch name under the reserved task prefix `agent/<task-id>` MUST use the prefix granted for that task (XGN-DEC-010). |

### 3.3 Repository names

| ID | Rule |
|---|---|
| V-22 | A repository identifier MUST be `<owner>/<name>` with exactly one separator. |
| V-23 | The owner and the name MUST each match `^[A-Za-z0-9._-]{1,100}$`, MUST NOT be `.` or `..`, and the name MUST NOT end with `.git`. |
| V-24 | Repository names are matched case-insensitively against the grant. The canonical value MUST be the grant's exact spelling, so that the request digest does not vary with the spelling the agent used. |
| V-25 | The head and base of a pull request MUST be in the same repository (XGN-19). |

### 3.4 Argument values

| Type | Rule |
|---|---|
| Text | MUST be valid UTF-8 with no unpaired surrogate and no C0 control character other than TAB, LF and CR. MUST NOT be trimmed: leading and trailing whitespace are significant and MUST be preserved. |
| Integer | Decimal digits only. No sign unless the operation allows one, no leading zeros except the single digit `0`, no thousands separators. The canonical form is the minimal decimal representation. |
| Boolean | Exactly `true` or `false` in lowercase. `1`, `0`, `yes`, `no` and `TRUE` MUST be rejected. |
| Enumeration | Lowercase `snake_case`, exactly one of the values in the catalogue. |
| Optional and absent | An optional argument that is not supplied MUST be absent. `null` MUST be rejected. An empty string is a value and MUST be distinct from absence. |
| Provider digest | Exactly 40 or 64 lowercase hexadecimal characters, as the operation requires. |
| Long field | The concatenation of the live parts of the field, under the rules of XGN-07 §4. |

| ID | Requirement |
|---|---|
| V-26 | The validator MUST reject a value whose canonical form differs from the value the agent wrote, rather than silently rewriting it, except for the repository spelling rule in V-24. |

### 3.5 Ordering

| ID | Requirement |
|---|---|
| V-27 | The canonical order of arguments MUST be ascending Unicode code point order of the argument name. |
| V-28 | The canonical form's top-level fields MUST use the fixed order in section 5 and MUST NOT be sorted. |
| V-29 | The canonical part order MUST be the explicit stored order of the live parts (XGN-07 §4). |
| V-30 | Any implementation that iterates a map MUST sort it before rendering. |

## 4. Encoding rules

| Form | Rule |
|---|---|
| base64url | The default for every value. Unpadded, URL-safe alphabet (RFC 4648 §5). |
| `enc=text` | Permitted only for short simple text: at most 140 decoded bytes, and only characters U+0020 to U+007E plus TAB, LF and CR. It MUST be marked `enc=text` (XGN-DEC-026, XGN-28 Q-E2). |
| Percent-encoding | Per XGN-01 §6.1: a space is `%20`, `+` is a literal plus sign, and the reserved set `/ ? # & = + %` and every character outside `A-Za-z0-9-._~` MUST be encoded. |

| ID | Requirement |
|---|---|
| V-31 | A value that is not short simple text MUST be rejected when `enc=text` is used, with reason `enc_text_not_permitted`, and MUST be sent as base64url. |
| V-32 | Because a literal `%` MUST arrive encoded as `%25`, a `%` followed by two hexadecimal digits remaining after the single decode MUST be rejected with reason `double_encoded`. This is stricter than XGN-28 Q-E7: a doubly encoded value is refused, not decoded once and accepted. |
| V-33 | base64url input MUST be unpadded and MUST use the URL-safe alphabet. `+` and `/` MUST be rejected, and a wrong length MUST be rejected. |
| V-34 | The `enc` parameter MUST NOT appear on `set` or on any short field, and `enc` MUST be one of `text` or `b64` (XGN-28 Q-E3). Any other value MUST be rejected. |
| V-35 | An unknown parameter MUST be rejected, a repeated parameter MUST be rejected, and the parameters of a command MUST appear in the canonical order for that command (XGN-01 §6.1, XGN-28). |

## 5. The canonical representation

The canonical representation of an operation is a byte sequence. Every request digest in XGN is taken over exactly this byte sequence (XGN-DEC-053).

### 5.1 Rendering rule

- Each field is rendered as `name` `=` `byte-length` `:` `value`.
- The byte length is the decimal count of the bytes in the value, with no leading zeros.
- Fields are joined by a single LF (`0x0A`) and the sequence ends with a final LF.
- A nested map is rendered as the concatenation of its members, each rendered as `name=len:value`, joined by LF, with members ordered by ascending Unicode code point of the member name. A nested map carries no wrapper.
- A nested list is rendered as the concatenation of its elements, each rendered as `len:value`, joined by LF, in the list's order.
- The value of a text field is its UTF-8 bytes. The value of a content field is its raw bytes. The canonical representation is therefore not necessarily printable text.
- Length prefixes make the rendering unambiguous: no separator can be confused with content, because each value's extent is known.

### 5.2 Field order

| # | Field | # | Field | # | Field |
|---|---|---|---|---|---|
| 1 | `contract_version` | 8 | `policy_version` | 15 | `content` |
| 2 | `request_id` | 9 | `operation` | 16 | `expect_blob_sha` |
| 3 | `session_id` | 10 | `repository` | 17 | `expect_head_sha` |
| 4 | `grant_id` | 11 | `branch` | 18 | `expect_base_head_sha` |
| 5 | `grant_digest` | 12 | `base_branch` | 19 | `issued_at` |
| 6 | `step_id` | 13 | `arguments` | 20 | `expires_at` |
| 7 | `principal_id` | 14 | `part_order` | | |

The three `expect_*` fields are the expected provider state. They are rendered at fixed positions and MUST NOT also appear in the `arguments` map, so that a precondition cannot be reordered or omitted. Their names are those of the operation profile (XGN-19): `expect_blob_sha`, `expect_head_sha` and `expect_base_head_sha`.

### 5.3 The canonical grant form

The grant in force is digested over a canonical grant form, as required by XGN-04 S-07. It uses the rendering rule of section 5.1, with these fields in this order: `repository`, `branch`, `directory`, `base_branch`, `steps`, `gates`, `limits`, `budgets`.

| ID | Requirement |
|---|---|
| V-39a | The canonical grant form MUST cover the authority-bearing grant fields only: the scope triple, the base branch, the ordered steps, the gates, the limits and the budgets. It MUST NOT cover timestamps, identifiers, the policy version or the lifetime (XGN-04 S-08). |
| V-39b | The grant digest MUST be computed over the canonical grant form with the algorithm and exposed length of XGN-DEC-003, and MUST be an input to the request digest (XGN-04 S-09, XGN-31). |

### 5.4 Example

Illustrative values, using the identifier forms of XGN-01 §4:

```text
contract_version=3:1.0
request_id=20:req_7Qm2x0Zr4n8Vc1A
session_id=20:ses_1Ab2Cd3Ef4Gh5Ij6Kl
grant_id=20:grt_9Zx8Yw7Vu6Ts5Rq4P
grant_digest=64:3f9a1c2b4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f8
step_id=11:stp_4Mn5Op6
principal_id=16:prn_3Kq9Lr7Nm4Xb
policy_version=16:pol_2026_11_27
operation=10:write_file
repository=8:acme/app
branch=14:agent/task-42
base_branch=4:main
arguments=52:message=13:Add notes&path=19:docs/notes.md
part_order=11:prt_7Kd2mQ1x
content=9:<nine raw bytes>
expected_blob_sha=40:e69de29bb2d1d6434b8b29ae775ad8c2e48c5391
expected_head_sha=40:3c4f5e6a7b8c9d0e1f2a3b4c5d6e7f8091a2b3c4
issued_at=20:2026-11-27T16:04:11Z
expires_at=20:2026-11-27T18:04:11Z
```

| ID | Requirement |
|---|---|
| V-36 | Two implementations given the same normalised envelope MUST produce identical canonical bytes. |
| V-37 | The canonical representation MUST be a pure function of the normalised envelope. |
| V-38 | A field that is absent from the normalised envelope MUST NOT appear in the canonical representation, and its absence MUST be observable. |
| V-39 | The canonical representation MUST NOT include a transport name, a host, a capability, a timestamp other than `issued_at` and `expires_at`, or any value the agent did not supply that is not listed in section 5.2. |

## 6. Size limits

| Limit | Value | Enforced at |
|---|---|---|
| Max encoded URL characters per command (Composed GET) | 900 | Gateway, before routing |
| Max encoded data characters per command | 2048 | Gateway, before routing (XGN-DEC-012) |
| Max decoded bytes per command value | 1536 | Runtime, on arrival |
| Max decoded bytes per long field | 65536 | Runtime, on arrival |
| Max total draft bytes | 65536 | Runtime, on arrival |
| Max parts per field | 64 | Runtime, on append |
| Max decoded path bytes | 1024 | Runtime |
| Max path segments | 64 | Runtime |
| Max path segment bytes | 255 | Runtime |
| Max branch name bytes | 255 | Runtime |
| Max repository owner bytes | 100 | Runtime |
| Max repository name bytes | 100 | Runtime |
| Max commit message bytes | 4096 | Runtime |
| Max pull request title bytes | 1024 | Runtime |
| Max entries in one batch | 32 | Runtime |
| Max batch entry encoded bytes | 2048 | Runtime |
| Max `enc=text` decoded bytes | 512 | Runtime |
| Max commands per session | 512 | Runtime (XGN-DEC-012) |
| Max open drafts per session | 1 | Runtime (INV-07) |
| Max unresolved requests per session | 1 | Runtime (INV-07) |
| Max `show` page size | 512 | Runtime (XGN-07 §8) |

| ID | Requirement |
|---|---|
| V-40 | Every limit in this table MUST be enforced at the earliest practical validation boundary and MUST be re-checked by the runtime (XGN-03 CD-09). |
| V-41 | A transport adapter MAY impose a smaller limit than the table. It MUST publish the effective limit in the capability description and MUST enforce the effective limit (XGN-03 CD-05). |
| V-42 | A value that exceeds a limit MUST be rejected with `DRAFT_LIMIT`. It MUST NOT be truncated or partially accepted. |

## 7. Malformed input

The runtime MUST NOT guess, repair or complete a malformed value. The rejection MUST name the field and the reason and MUST NOT echo the value (XGN-02 §10).

| ID | Malformation | Reason | Error code |
|---|---|---|---|
| V-43 | Unknown parameter | `unknown_parameter` | `VALIDATION_FAILED` |
| V-44 | Repeated parameter | `repeated_parameter` | `VALIDATION_FAILED` |
| V-45 | Parameters not in canonical order | `parameter_order` | `VALIDATION_FAILED` |
| V-46 | Invalid percent escape | `bad_percent_escape` | `VALIDATION_FAILED` |
| V-47 | Double-encoded value | `double_encoded` | `VALIDATION_FAILED` |
| V-48 | Invalid base64url | `bad_base64url` | `VALIDATION_FAILED` |
| V-49 | Non-UTF-8 or unpaired surrogate | `bad_utf8` | `VALIDATION_FAILED` |
| V-50 | Control character present | `control_character` | `VALIDATION_FAILED` |
| V-51 | Normalisation would change the value | `ambiguous_unicode` | `VALIDATION_FAILED` |
| V-52 | Absolute path | `absolute_path` | `VALIDATION_FAILED` |
| V-53 | `..` segment present | `path_traversal` | `VALIDATION_FAILED` |
| V-54 | Path outside the granted directory | `path_out_of_scope` | `SCOPE_VIOLATION` |
| V-55 | Branch outside the grant | `branch_out_of_scope` | `SCOPE_VIOLATION` |
| V-56 | Repository outside the grant | `repo_out_of_scope` | `SCOPE_VIOLATION` |
| V-57 | Field not in the operation | `unknown_field` | `VALIDATION_FAILED` |
| V-58 | Field not writable by the agent | `field_not_writable` | `SCOPE_VIOLATION` |
| V-59 | Argument value out of range | `value_out_of_range` | `VALIDATION_FAILED` |
| V-60 | Wrong value type | `bad_type` | `VALIDATION_FAILED` |
| V-61 | Required field missing at preview | `missing_required_field` | `VALIDATION_FAILED` |
| V-62 | Step dependency not satisfied | `step_dependency` | `VALIDATION_FAILED` |
| V-63 | A size limit exceeded | `limit_exceeded` | `DRAFT_LIMIT` |
| V-64 | The agent supplied a runtime-stamped field | `agent_set_stamped_field` | `MALFORMED_REQUEST` |
| V-65 | `enc=text` used for a value that is not short simple text | `enc_text_not_permitted` | `VALIDATION_FAILED` |

| ID | Requirement |
|---|---|
| V-66 | A rejected value MUST leave the draft, the pending envelope and the next session sequence number unchanged (XGN-DEC-024, INV-11). |
| V-67 | A request that is out of scope MUST be rejected WITHOUT the external service being contacted (INV-03). |

## 8. Step dependency validation

The grant defines ordered steps (XGN-04). The dependencies below MUST be checked at preview and again at commit.

| ID | Requirement |
|---|---|
| V-68 | `create_branch.base` MUST equal the granted base branch. |
| V-69 | `create_branch.new_branch` MUST equal the granted working branch, exactly or under the granted reserved prefix (XGN-DEC-010). |
| V-70 | `write_file.branch` MUST equal the branch created by the immediately preceding step, when the preceding step creates one. |
| V-71 | `create_pull_request.head` MUST equal the working branch, and `create_pull_request.base` MUST equal the granted base branch. |
| V-72 | The granted base branch MUST be used only as the start of the new branch and as the target of the pull request. |
| V-73 | A dependency violation MUST be rejected before the external service is contacted (INV-03), with reason `step_dependency`. |

## 9. Revalidation before dispatch

| ID | Requirement |
|---|---|
| V-74 | Immediately before dispatch, the execution layer MUST re-run canonicalisation over the envelope arguments and MUST confirm that the resulting canonical representation is byte-identical to the representation that was digested and approved (XGN-31). |
| V-75 | The repository privacy check MUST be re-run immediately before dispatch and not only at commit. A repository that is no longer private MUST cause the dispatch to be refused with `REPO_NOT_PRIVATE` and the session to be suspended. |
| V-76 | The repository permission check for the acting credential MUST be re-run immediately before dispatch. A change of permission MUST cause the dispatch to be refused. |
| V-77 | The provider preconditions, the expected blob digest and the expected head digest, MUST be re-read immediately before dispatch (XGN-19, XGN-DEC-020). |
| V-78 | The policy decision MUST be re-evaluated if the policy version in force differs from the version recorded on the decision (XGN-15). |
| V-79 | A revalidation failure MUST NOT be repaired by re-canonicalising or re-approving automatically. The agent MUST re-preview and re-commit, which produces a new request digest and a new approval. |

## 10. Decisions recorded

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-012` | Draft and chunk limits | 2 KB of encoded data per command, 64 KB total draft, 64 parts per field, 512 commands per session. | XGN-08, XGN-28, XGN-04 |
| `XGN-DEC-026` | Default content encoding | base64url for arbitrary content. Percent-encoded text is permitted only for short, simple text and MUST be marked `enc=text`. | XGN-08, XGN-28 |
| `XGN-DEC-053` | Canonical representation | The canonical representation of an operation is the length-prefixed, LF-separated rendering of the fixed field order defined in XGN-08 section 5. Every request digest in XGN is taken over that byte sequence. | XGN-08 |

## 11. Open decisions

| ID | Question | Current position |
|---|---|---|
| XGN-08-OD-01 | Does XGN-04 define a grant digest? | Section 5.2 requires a `grant_digest` input. If XGN-04 does not define one, the runtime MUST compute it over the grant identifier, scope, step list and limits using the rendering rule in section 5.1. To be confirmed when XGN-04 is written. |
| XGN-08-OD-02 | Should `enc=text` be retained? | It is permitted for short simple text only, because the inherited size estimates assumed compact data and did not hold for text with spaces and newlines. Retiring it in favour of base64url alone has not been decided. |
| XGN-08-OD-03 | Is the 900-character URL budget final? | It is the value in the shared capability description. It must be re-checked against the composed command URLs of the channel qualification probes (XGN-30). |
| XGN-08-OD-04 | Are path comparisons always case-sensitive? | They are here. A provider with a case-insensitive path model would need a provider-specific rule in XGN-19. |

## 12. Related specifications

- **Depends on:** XGN-01 (encodings and identifier forms), XGN-02 (shared contracts and error shape), XGN-04 (grant, scope and steps), XGN-07 (parts and field model), XGN-19 (operation catalogue and preconditions).
- **Referenced by:** XGN-15, XGN-16, XGN-17, XGN-21, XGN-23, XGN-26, XGN-28, XGN-31.
- **Predecessor material:** the path, branch and scope normalisation requirements of the *Navigation Transport Specification* (0.1-draft) §4.1, and the progressive-validation and encoding requirements identified in technical review B §4.2 and technical review A deficiency 9. The canonical representation and the fail-closed table are new in this set.
- **Out of scope:** digest inputs and binding (XGN-31), policy evaluation (XGN-15), transport framing (XGN-09 to XGN-14), provider semantics (XGN-19).
