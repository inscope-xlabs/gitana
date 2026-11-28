# XGN-19: XGN GitHub Operation Profile

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-19 | **Group:** D — Policy, approval, and execution
**Owner:** Gitana Architecture
**Depends on:** XGN-02, XGN-08, XGN-15, XGN-18, XGN-23, XGN-31 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** GH-OD-01, GH-OD-02 (section 1.3)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

### 1.1 Purpose

This module is the **provider profile for GitHub**. It binds the provider-neutral XGN operation names used in the request envelope onto GitHub semantics: the arguments of each operation, the preconditions that must hold, the criteria for success, the failure semantics, and the mapping from a GitHub response onto an XGN result.

### 1.2 What this module is not

This module is a **provider profile**. It is not the definition of XGN.

| Concern | Owner |
|---|---|
| The request envelope, the canonical form and the digest | XGN-02, XGN-08, XGN-31 |
| The grant, the scope and the policy decision | XGN-04, XGN-15 |
| The approval and the approval screen | XGN-16 |
| The dispatch, the credential custody and the execution record | XGN-17 |
| The provider API mapping, the error mapping and the rate limits | XGN-18, XGN-23 |
| The state machine and the lifecycle | XGN-21 |
| The reconciliation procedure | XGN-22 |

Where this profile and an owning module both speak to the same subject, the owning module's rule prevails. This profile never widens what a session may do.

### 1.3 Open decisions

| ID | Question | Interim position |
|---|---|---|
| GH-OD-01 | Whether `create_pull_request` may target a base branch other than the granted base branch | No. The base is fixed by the grant. |
| GH-OD-02 | Whether `write_file` may carry content larger than the draft limit | No. The draft limit of 64 KB governs; larger content is out of scope. |

## 2. The operation catalogue

| Operation | Mutating | Arguments written by the agent | Channel tier | Approval | In the initial surface |
|---|---|---|---|---|---|
| `create_branch` | Yes | None; `new_branch` is grant-fixed or runtime-allocated | B or C | Required | Yes |
| `write_file` | Yes | `path`, `message`, `content`, `mode` | C | Required | Yes |
| `create_pull_request` | Yes | `title`, optional `body` | B or C | Required | Yes |
| `delete_file` | Yes | `path` | C | Required when enabled | No |
| `read_file` | No | `path`, optional `page` | A | Not required | Yes, as a read-only link |
| `list_branches` | No | None | A | Not required | Yes, as a read-only link |

| ID | Requirement |
|---|---|
| GH-01 | The operation names in this table are the names carried in the envelope. They are XGN-level names, not GitHub API paths or verbs (XGN-18 EA-43). |
| GH-02 | The argument set, the preconditions and the result mapping of each operation are declared in this profile. The provider-side mechanism behind each precondition is adapter-internal (XGN-18 EA-40). |
| GH-03 | An operation not in this table MUST NOT be dispatched. An adapter MUST NOT implement an operation that is not in the profile (XGN-18 EA-30). |
| GH-04 | The arguments marked as fixed are supplied by the grant or allocated by the runtime. The agent MUST NOT supply them, and an envelope that carries a different value MUST be rejected as `MALFORMED_REQUEST` (XGN-02 section 4.2). |
| GH-05 | Every mutating operation in this profile requires an operator approval, on every transport (XGN-DEC-006). |

Argument names follow the canonical envelope fields of XGN-08 section 5.2 and the digest input list of XGN-31 section 2: `repository`, `branch`, `base_branch`, `expected_blob_sha` and `expected_head_sha`. The illustrative envelope in XGN-02 section 4 spells some of these differently; the canonical names govern, because the canonical representation and the request digest are taken over them.

### 2.1 The initial surface

| ID | Requirement |
|---|---|
| GH-06 | The initial Composed GET surface consists of `create_branch`, `write_file` and `create_pull_request` as mutating operations, and `read_file` and `list_branches` as read-only links (XGN-DEC-047). |
| GH-07 | `delete_file` is not enabled in the initial surface. A request naming it MUST be denied with `OPERATION_NOT_ENABLED` until policy enables it (XGN-15 K-36). |
| GH-08 | Out of scope for this profile: arbitrary GitHub API calls, binary files, partial file edits, files above the draft limit, public repositories, forks, cross-repository pull requests, and any scope change during a session. |

## 3. `create_branch`

### 3.1 Arguments

| Argument | Source | Required | Value |
|---|---|---|---|
| `repository` | Grant | Yes | The granted repository |
| `base` | Grant | Yes | The granted base branch |
| `new_branch` | Grant or runtime allocation | Yes | An exact branch name, or a name allocated inside the granted branch prefix (XGN-DEC-010) |
| `expected_head_sha` | Agent | Yes | The expected head commit of the base branch |

| ID | Requirement |
|---|---|
| GH-09 | `new_branch` MUST either be the exact name fixed by the grant, or be allocated by the runtime inside the granted branch prefix. The agent MUST NOT choose a name when the grant specifies a prefix. |
| GH-10 | `expected_head_sha` MUST be present. A branch creation without a base head precondition MUST be rejected. |
| GH-11 | For `create_branch`, `expected_head_sha` is the expected head commit of the **base branch**, because the new branch does not yet exist. It MUST be the 40-character hexadecimal head commit, as observed by the agent in the same session. |

### 3.2 Preconditions, success and failure

| ID | Aspect | Requirement |
|---|---|---|
| GH-12 | Precondition | The base branch exists and its head commit equals `expected_head_sha`. |
| GH-13 | Precondition | `new_branch` does not already exist in the repository. |
| GH-14 | Precondition | The repository is private and the credential may write to it. |
| GH-15 | Success | The branch exists with `new_branch` as its name. The provider reference is `refs/heads/<new_branch>`. |
| GH-16 | Failure | The base branch does not exist: `NOT_FOUND`. The base head differs: `PRECONDITION_FAILED`. The branch already exists: `CONFLICT`. The repository is not private: `REPO_NOT_PRIVATE`. |
| GH-17 | Failure | A failed branch creation MUST NOT leave the session's step advanced. The step remains current (XGN-DEC-029). |

## 4. `write_file`

### 4.1 Arguments

| Argument | Source | Required | Value |
|---|---|---|---|
| `repository` | Grant | Yes | The granted repository |
| `branch` | Grant or step | Yes | The working branch of the session, which is the branch created by the first step (XGN-DEC-048) |
| `path` | Agent | Yes | A canonical path inside the granted directory (XGN-15 section 3.3) |
| `message` | Agent | Yes | The commit message. Rendered as data on the approval screen. |
| `content` | Agent | Yes | The complete file content. A long field, carried in parts. |
| `mode` | Agent | Yes | One of `create`, `update`, `overwrite` (section 4.2) |
| `expected_head_sha` | Agent | Yes | The expected head commit of the target branch |
| `expected_blob_sha` | Agent | Conditional | The expected blob digest of the target path. Required when the path exists. |

| ID | Requirement |
|---|---|
| GH-18 | `expected_head_sha` MUST be present for every `write_file`. A write without a branch head precondition MUST be rejected. |
| GH-19 | `expected_blob_sha` MUST be present whenever the target path exists at the expected head. Its absence in that case is a rejection, not a warning. |
| GH-20 | The content of `write_file` is the complete file content. A partial edit is not expressible in this profile and MUST NOT be attempted. |

### 4.2 Create, update and overwrite

| ID | Requirement |
|---|---|
| GH-21 | The mode MUST be explicit. An operation without a mode MUST be rejected. |

| ID | `mode` | Precondition | Approval screen |
|---|---|---|---|
| GH-22 | `create` | The path does not exist at `expected_head_sha`. `expected_blob_sha` MUST be absent. | The overwrite block states that no existing content is replaced. |
| GH-23 | `update` | The path exists at `expected_head_sha` and its blob digest equals `expected_blob_sha`. Both values are required. | The change summary shows added and removed line counts against the existing content. The overwrite block states that the existing content is replaced. |
| GH-24 | `overwrite` | The path may exist. When it exists, `expected_blob_sha` is required and MUST match. When it does not, the operation creates the file. | The overwrite block MUST carry an explicit overwrite warning naming the path. |

| ID | Requirement |
|---|---|
| GH-25 | A `create` against an existing path MUST be rejected with `PRECONDITION_FAILED`. It MUST NOT silently become an update. |
| GH-26 | An `update` without `expected_blob_sha`, or with a digest that does not match, MUST be rejected with `PRECONDITION_FAILED`. The write MUST NOT proceed (XGN-DEC-020). |
| GH-27 | A blob digest mismatch MUST NOT be downgraded to a warning, and MUST NOT be resolved by overwriting. The agent MUST read the current content, re-read the digest and commit again. |
| GH-28 | A head digest mismatch MUST be rejected with `PRECONDITION_FAILED`. The agent MUST re-read the branch head. |
| GH-29 | The overwrite warning MUST be visible on the approval screen before the operator can approve (XGN-16 A-10). |

### 4.3 Success and failure

| ID | Aspect | Requirement |
|---|---|---|
| GH-30 | Success | The commit exists on `branch` and contains the file with the submitted content. The provider reference is the new head commit identifier. The result details carry the new blob digest, the new head commit and the content size. |
| GH-31 | Failure | Precondition failure: `PRECONDITION_FAILED`. The path is invalid: `VALIDATION_FAILED`. The content exceeds the limit: `DRAFT_LIMIT`. The branch does not exist: `NOT_FOUND`. The repository is not private: `REPO_NOT_PRIVATE`. |
| GH-32 | A rejected write MUST NOT modify the repository. The content is not committed under any circumstance. |

## 5. `create_pull_request`

### 5.1 Arguments

| Argument | Source | Required | Value |
|---|---|---|---|
| `repository` | Grant | Yes | The granted repository |
| `head` | Grant or step | Yes | The working branch of the session |
| `base` | Grant | Yes | The granted base branch |
| `title` | Agent | Yes | The pull request title. Rendered as data. |
| `body` | Agent | No | The pull request body. A long field, carried in parts. |
| `expected_head_sha` | Agent | Yes | The expected head commit of the head branch |

### 5.2 Preconditions, success and failure

| ID | Aspect | Requirement |
|---|---|---|
| GH-33 | Precondition | The head and the base are in the same repository as `repository`. A fork or a cross-repository pull request MUST be rejected with `SCOPE_VIOLATION`. |
| GH-34 | Precondition | The head branch exists and its head commit equals `expected_head_sha`. |
| GH-35 | Precondition | The base branch exists. |
| GH-36 | Precondition | There is no existing open pull request from the head branch to the base branch. The adapter MUST list the open pull requests for that head and base before creating one, and MUST reject the operation with `CONFLICT` if one exists. |
| GH-37 | Scope | A pull request whose head is not the working branch, or whose base is not the granted base branch, MUST be rejected with `STEP_DEPENDENCY_VIOLATION` (XGN-15 K-76). |
| GH-38 | Success | The pull request exists with the submitted title and body. The provider reference is the pull request identifier. The result details carry the pull request number, its state, the head branch and the base branch. |
| GH-39 | Failure | The base or head branch does not exist: `NOT_FOUND`. The head digest differs: `PRECONDITION_FAILED`. An open pull request already exists: `CONFLICT`. The repository is not private: `REPO_NOT_PRIVATE`. |
| GH-40 | The duplicate-pull-request precondition is required to prevent an unintended duplicate operation, and is verified by the acceptance test for duplicate pull request creation (technical review B, test P25; conformance in XGN-26). The check MUST be a precondition and MUST NOT be a best-effort warning. |

## 6. `delete_file`

### 6.1 Arguments

| ID | Aspect | Requirement |
|---|---|---|
| GH-41 | Arguments | `repository` and `branch` from the grant; `path` from the agent; `expected_head_sha` and `expected_blob_sha` from the agent. Both preconditions are required. |
| GH-42 | Preconditions | The path exists at `expected_head_sha` and its blob digest equals `expected_blob_sha`. |
| GH-43 | Success | The path does not exist at `expected_head_sha` and the commit records the removal. The provider reference is the new head commit identifier. |
| GH-44 | Failure | A precondition mismatch: `PRECONDITION_FAILED`. The path does not exist: `NOT_FOUND`. |
| GH-45 | Availability | The operation is not enabled in the initial surface (GH-07). Where it is enabled, it requires an operator approval on every transport (XGN-DEC-006), and the approval screen carries an explicit overwrite-style warning that content will be removed. |

## 7. `read_file`

### 7.1 Arguments and behaviour

| ID | Aspect | Requirement |
|---|---|---|
| GH-46 | Arguments | `repository` from the grant; `path` from the agent; optional `ref`, which MUST equal the scoped branch; optional `page`. |
| GH-47 | Ref restriction | A read that targets a ref other than the scoped branch MUST be rejected by default with `SCOPE_VIOLATION` (XGN-DEC-014). The agent MUST NOT read the base branch, a tag or a commit identifier. |
| GH-48 | Path | The path MUST be inside the granted directory and MUST be canonical. |
| GH-49 | Channel tier | `read_file` is a channel tier A read-only link. It is issued as a complete link and carries no agent-composed arguments. |
| GH-50 | Content | The content MUST be returned only inside an explicit read, marked as data (XGN-24). It MUST NOT be returned in a panel that also carries commands. |
| GH-51 | Size | A single read returns at most 65536 bytes. A larger file is returned in pages of 8192 bytes, with the page count stated. |
| GH-52 | Success | The provider reference is the blob identifier. The result details carry the blob digest, the byte size and the encoding. |
| GH-53 | Failure | The path does not exist: `NOT_FOUND`. The path names a directory: `VALIDATION_FAILED`. The path is outside the granted directory: `SCOPE_VIOLATION`. The ref is not the scoped branch: `SCOPE_VIOLATION`. |

## 8. `list_branches`

| ID | Aspect | Requirement |
|---|---|
| GH-54 | Arguments | `repository` from the grant; optional `limit`, default 100, maximum 1000. |
| GH-55 | Channel tier | `list_branches` is a channel tier A read-only link. |
| GH-56 | Scope | The result MUST be filtered to the granted scope: the scoped branch, and any branch under the granted branch prefix. A branch outside the grant MUST NOT be disclosed, and its existence MUST NOT be inferred from a count (XGN-03 section 2.2). |
| GH-57 | Pagination | At most 10 pages and 1000 items. Where the limit is reached, the result states that it is truncated (XGN-18 EA-24). |
| GH-58 | Success | The provider reference is `refs/heads`. The result details carry the branch count, the truncation flag and, for each branch, its name and head commit digest. |
| GH-59 | Failure | The repository does not exist: `NOT_FOUND`. The repository is not private: `REPO_NOT_PRIVATE`. |

## 9. Result mapping

| ID | Requirement |
|---|---|
| GH-60 | A GitHub response MUST be mapped onto the result shape of XGN-18 section 2.2. The mapping for each operation is declared in this section. |
| GH-61 | The provider reference MUST be the provider's own identifier, unmodified. It MUST NOT be a synthetic value. |
| GH-62 | The result digest MUST be computed over the canonical result body with the algorithm of XGN-DEC-003, and the 32 hexadecimal characters MUST be exposed for confirmation. |
| GH-63 | The result details MUST NOT contain file content, a pull request body or a pull request title. Sizes, counts, digests, identifiers and states are permitted. |

| Operation | `provider_reference` | Details |
|---|---|---|
| `create_branch` | `refs/heads/<new_branch>` | The head commit digest of the created branch |
| `write_file` | The new head commit identifier | The new blob digest, the new head commit, the content size, the added and removed line counts |
| `create_pull_request` | The pull request identifier | The pull request number, its state, the head branch, the base branch |
| `delete_file` | The new head commit identifier | The removed blob digest, the new head commit |
| `read_file` | The blob identifier | The blob digest, the byte size, the encoding, the page count |
| `list_branches` | `refs/heads` | The branch count, the truncation flag, and the name and head commit of each branch in scope |

## 10. Error normalisation

| ID | Requirement |
|---|---|
| GH-64 | Every GitHub error response MUST be mapped onto the XGN error taxonomy (XGN-23) by the adapter. The mapping table is owned by XGN-18 and XGN-23; this profile states which failures each operation can produce. |
| GH-65 | The GitHub status code MAY be recorded in `details` as a number. The GitHub message body MUST NOT be passed through verbatim (XGN-02 section 10, XGN-18 EA-16). |
| GH-66 | A GitHub error that indicates the request was not processed MAY be reported as `FAILURE` with the mapped code. An error after the request was sent that leaves the outcome unclear MUST be reported as `OUTCOME_UNKNOWN` (XGN-17 X-39). |

| Operation | Expected failures |
|---|---|
| `create_branch` | `NOT_FOUND`, `PRECONDITION_FAILED`, `CONFLICT`, `REPO_NOT_PRIVATE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE` |
| `write_file` | `NOT_FOUND`, `PRECONDITION_FAILED`, `VALIDATION_FAILED`, `DRAFT_LIMIT`, `REPO_NOT_PRIVATE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE` |
| `create_pull_request` | `NOT_FOUND`, `PRECONDITION_FAILED`, `CONFLICT`, `SCOPE_VIOLATION`, `STEP_DEPENDENCY_VIOLATION`, `REPO_NOT_PRIVATE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE` |
| `delete_file` | `NOT_FOUND`, `PRECONDITION_FAILED`, `REPO_NOT_PRIVATE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE` |
| `read_file` | `NOT_FOUND`, `VALIDATION_FAILED`, `SCOPE_VIOLATION`, `REPO_NOT_PRIVATE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE` |
| `list_branches` | `NOT_FOUND`, `REPO_NOT_PRIVATE`, `PERMISSION_DENIED`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE` |

## 11. Scope rules that apply to every operation

| ID | Requirement |
|---|---|
| GH-67 | The repository MUST be private. A non-private repository MUST NOT be contacted for a mutating operation, and a session naming one MUST NOT be created (XGN-15 section 7). |
| GH-68 | Every path MUST be canonical and inside the granted directory, under the boundary rule of XGN-15 section 3.3. |
| GH-69 | Every branch MUST be the granted branch, or a branch created by an earlier step of the same session. |
| GH-70 | A pull request MUST have its head and its base in the same scoped repository. Forks and cross-repository pull requests are out of scope. |
| GH-71 | The provider MUST NOT be contacted for an out-of-scope request. A scope rejection happens before the provider call (INV-03). |

## 12. Decisions recorded

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-003` | Digest algorithm | SHA-256 over the canonical envelope. Full digest held internally; 32 hex characters (128 bits) exposed for confirmation. | XGN-31 |
| `XGN-DEC-010` | Branch scope | An exact branch name, or a task-reserved prefix `agent/<task-id>`. Both are permitted; the grant names one. | XGN-04 |
| `XGN-DEC-014` | Reads against other refs | None by default. A read outside the scoped branch is rejected. | XGN-19 |
| `XGN-DEC-020` | Overwrite protection | Writing an existing file requires the expected blob digest; a branch operation requires the expected head digest. A mismatch is a rejection. | XGN-19 |
| `XGN-DEC-047` | Initial GitHub operation surface | The initial surface is `create_branch`, `write_file` and `create_pull_request`, with `read_file` and `list_branches` as read-only links. `delete_file` is not enabled. | XGN-19 |

## 13. Related specifications

- **Depends on:** XGN-02 (Request Envelope Contract, Error Contract), XGN-08 (canonicalisation of paths, branches and arguments), XGN-15 (scope and the operation allowlist), XGN-18 (adapter contract and result shape), XGN-23 (error taxonomy), XGN-31 (digest and binding).
- **Referenced by:** XGN-15, XGN-17, XGN-18, XGN-21, XGN-22, XGN-26, XGN-27.
- **Predecessor material:** the operations, fields and boundaries in the *Gitana Surface Map (Composed GET): Proposal*; the version preconditions recommended in technical review B section 3.3; the overwrite and concurrency protection in technical review A deficiency 5; and the ref restriction in the *Navigation Transport Specification* (0.1-draft) section 15, decision N7.
- **Out of scope:** the grant and policy rules, the approval, the dispatch path, the provider API mechanism and the state machine.
