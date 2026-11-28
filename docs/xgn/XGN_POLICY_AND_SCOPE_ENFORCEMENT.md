# XGN-15: XGN Policy and Scope Enforcement

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-15 | **Group:** D — Policy, approval, and execution
**Owner:** Gitana Architecture
**Depends on:** XGN-02, XGN-04, XGN-08, XGN-19, XGN-31 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** K-OD-01, K-OD-02 (section 1.3)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

### 1.1 Purpose

This module defines how the trusted runtime decides whether a proposed operation is permitted: the policy decision set, the check set, the scope rules for repository, branch and directory, the operation allowlist, the fail-closed behaviour for ambiguous input, the points at which policy is re-evaluated, and the ownership, versioning and persistence of policy configuration.

Policy decides whether an action is permitted. It does not record the operator's decision; approval does that, and is owned by XGN-16. It does not execute anything; that is owned by XGN-17.

### 1.2 In scope and out of scope

| In scope | Out of scope |
|---|---|
| The policy decision set and the Policy Decision Contract fields | The approval record and the approval screen — XGN-16 |
| The check set evaluated against a request | The dispatch, credential custody and execution record — XGN-17 |
| Repository, branch and directory scope, and the path boundary rule | The operation arguments, preconditions and result mapping — XGN-19 |
| The operation allowlist and its classification | The provider API mapping and error translation — XGN-18 |
| Fail-closed resolution of malformed, stale, unknown and out-of-scope input | The canonical form and digest computation — XGN-08, XGN-31 |
| Policy versioning, configuration ownership and persistence | The state machine and lifecycle transitions — XGN-21 |

### 1.3 Open decisions

| ID | Question | Interim position |
|---|---|---|
| K-OD-01 | Whether policy configuration may be scoped per repository as well as globally | Global. A per-repository override is not defined until a use case is stated. |
| K-OD-02 | Whether a policy narrowing applies to a request already approved but not yet dispatched | It applies. The policy in force at dispatch governs, and a deny at dispatch stops the dispatch. |

## 2. Policy decisions

### 2.1 The decision set

The policy engine MUST return exactly one decision for every evaluation.

| ID | Decision | Meaning | Consequence |
|---|---|---|---|
| K-01 | The policy engine MUST evaluate a request and return exactly one of `ALLOW`, `DENY`, `REQUIRE_APPROVAL` or `RECHECK`, expressed through the Policy Decision Contract (XGN-02 section 6). | — | —  |
| K-02 | `ALLOW` | The operation is permitted and no operator decision is required. | The request MAY proceed to dispatch, subject to the pre-dispatch checklist (XGN-17).  |
| K-03 | `DENY` | The operation is not permitted. | The request MUST NOT be dispatched, and the external service MUST NOT be contacted for it (INV-03).  |
| K-04 | `REQUIRE_APPROVAL` | The operation is permitted only with an operator decision. | The request MUST enter the approval state and MUST NOT be dispatched before an approval is recorded (XGN-16).  |
| K-05 | `RECHECK` | The evaluation could not be completed, because authoritative state was unavailable, unreachable or changed during evaluation. | The request MUST NOT be dispatched. It returns to a state that requires a fresh commit. `RECHECK` MUST NOT be treated as `ALLOW`, and MUST NOT be presented to the operator as a denial of the operation itself.  |
| K-06 | A decision MUST carry a stable reason code for every failed check. A reason MUST NOT contain content, secrets or capabilities. | — | —  |
| K-07 | Only the trusted runtime produces a policy decision. The gateway MUST NOT produce, cache or assert one (XGN-02 section 6, XGN-20). | — | —  |

### 2.2 The Policy Decision Contract fields

The schema is owned by XGN-02 section 6. This module fixes which fields the engine populates and what each MUST contain.

| Field | Requirement |
|---|---|
| `decision` | One of `ALLOW`, `DENY`, `REQUIRE_APPROVAL`, `RECHECK`. |
| `policy_version` | The version of the policy configuration in force at the moment of evaluation. |
| `grant_id` | The grant the request was evaluated against. |
| `request_id` | The request evaluated. |
| `evaluated_at` | Stamped by the runtime. Never supplied by an agent. |
| `checks[]` | One entry per check in the check set of section 2.3, in the order given, each with `check` and `result` (`PASS` or `FAIL`). |
| `reasons[]` | One stable reason code per failed check. Empty when the decision is `ALLOW`. |

### 2.3 The check set

A decision is the conjunction of the checks below. Every check MUST be evaluated; a check that cannot be evaluated yields `FAIL` with reason `CHECK_UNAVAILABLE` and a decision of `RECHECK`.

| ID | Check | Evaluates | Fails when |
|---|---|---|---|
| K-08 | `grant_active` | The grant bound to the session | The grant is expired, revoked, superseded or suspended  |
| K-09 | `grant_digest_match` | The digest of the grant in force | It differs from the digest bound to the session (section 8)  |
| K-10 | `repo_in_grant` | The canonical repository identifier | It is not the granted repository  |
| K-11 | `branch_in_grant` | The canonical branch name | It is not the granted branch and does not begin with the granted branch prefix  |
| K-12 | `path_in_grant` | The canonical path against the granted directory | The path is outside the granted directory under the rule of section 3.3  |
| K-13 | `operation_allowed` | The operation name | The operation is not in the catalogue (XGN-19), not permitted by the grant, or not carryable by the current channel tier  |
| K-14 | `arguments_valid` | The normalised arguments | A required argument is missing, an unknown argument is present, or an argument fails its type, size or format rule (XGN-08)  |
| K-15 | `repo_private` | The current visibility of the repository | The repository is not private (section 7)  |
| K-16 | `limits_within_grant` | Sizes, part counts, command counts, byte budgets, approval budgets | A limit of the grant or of the policy is exceeded  |
| K-17 | `step_dependency` | The consistency of the operation with the session's step sequence | The operation contradicts an earlier step of the same session (section 9)  |
| K-18 | `provider_preconditions` | The expected provider state named by the operation | The expected blob digest, head digest or existing-object precondition does not hold  |
| K-19 | `channel_tier_available` | The tier qualification of the transport in use | The transport is not qualified for the operation's channel tier  |

Checks `repo_private` and `provider_preconditions` MAY require a read-only call to the external service. No other check may cause the external service to be contacted.

## 3. Scope enforcement

### 3.1 The scope triple

Every session runs under one scope triple fixed at session creation (XGN-04).

| Element | Rule | ID |
|---|---|---|
| Repository | Exactly one repository, referenced by canonical owner and name. It MUST be private. | K-20 |
| Branch | Exactly one branch name, or one branch-name prefix reserved for the task (XGN-DEC-010). | K-21 |
| Directory | One canonical directory path. The repository root is represented by the empty string. | K-22 |

### 3.2 Canonical scope values

The canonical form of a path, a branch name and a repository identifier is defined by XGN-08 section 3 (V-06 to V-25). This module depends on that form and MUST NOT define a second one.

| ID | Requirement |
|---|---|
| K-23 | The scope triple MUST be stored in canonical form as defined by XGN-08. A scope value that is not canonical, or that would change under normalisation, MUST be refused when the grant is created, with reason `ambiguous_unicode` (XGN-08 V-13).  |
| K-24 | The scope rules of this module apply only to canonical values. A raw, partially decoded or agent-supplied spelling MUST NOT be compared against the grant.  |
| K-25 | A repository identifier MUST be compared case-insensitively against the grant, and a branch name case-sensitively, as XGN-08 V-20 and V-24 require. The canonical spelling of the grant is the one displayed and digested.  |

### 3.3 The path boundary rule

This is the scope-enforcement rule the boundary check MUST implement. XGN-08 V-16 requires the same rule at the validation boundary: a canonical path is compared against the granted directory segment by segment, and a string prefix comparison MUST NOT be used. The rule is stated here in full because a weaker check is a scope escape.

Let `G` be the canonical granted directory and `P` the canonical candidate path.

| ID | Rule |
|---|---|
| K-26 | If `G` is empty, `P` is in scope if and only if `P` is canonical and does not begin with `/`.  |
| K-27 | If `G` is not empty, `P` is in scope if and only if `P == G`, or `P` begins with `G` followed by the separator `/`.  |
| K-28 | The boundary check MUST be performed on the canonical form only. A raw or partially decoded string MUST NOT be compared.  |
| K-29 | The boundary check MUST NOT be implemented as a bare string prefix test. A bare prefix test admits `docs-archive/notes.md` for a granted directory of `docs`, and MUST be treated as a defect.  |
| K-30 | The comparison MUST use `/` as the only separator. A path containing a backslash MUST be rejected at canonicalisation, because a provider may interpret it as a separator.  |

Worked cases for `G = "docs"`:

| Candidate path `P` | In scope | Note |
|---|---|---|
| `docs` | Yes | Equal to the granted directory |
| `docs/notes.md` | Yes | Separator boundary present |
| `docs/notes/deep.md` | Yes | Boundary is on the first segment, not a character count |
| `docs-archive/notes.md` | No | A bare prefix test would admit this |
| `docsite/notes.md` | No | A bare prefix test would admit this |
| `src/docs/notes.md` | No | The prefix must be at the start of the path |
| `docs/../src/x.md` | No | Not canonical; rejected under XGN-08 V-10 |
| `/docs/notes.md` | No | Absolute; rejected under XGN-08 V-06 |
| `docs//notes.md` | No | Empty segment; rejected under XGN-08 V-08 |

### 3.4 Rejection before the external service

| ID | Requirement |
|---|---|
| K-31 | A request that fails any scope check MUST be rejected without contacting the external service for the operation (INV-03).  |
| K-32 | A scope rejection MUST name the scope element that failed and MUST NOT disclose the contents or existence of anything outside the scope.  |

## 4. The operation allowlist

The catalogue of operations, their arguments, preconditions, success criteria and result mapping is owned by XGN-19. This section classifies the operations for policy purposes only.

| Operation | Mutating | Operator approval | Channel tier | Grant-fixed arguments |
|---|---|---|---|---|
| `create_branch` | Yes | Required (XGN-DEC-006) | B or C | `repository`, `base`; `new_branch` is fixed by the grant or allocated by the runtime inside the granted branch prefix |
| `write_file` | Yes | Required (XGN-DEC-006) | C | `repository`, `branch` |
| `create_pull_request` | Yes | Required (XGN-DEC-006) | B or C | `repository`, `head`, `base` |
| `delete_file` | Yes | Required when enabled (XGN-DEC-006) | C | `repository`, `branch` |
| `read_file` | No | Not required | A | `repository` |
| `list_branches` | No | Not required | A | `repository` |

| ID | Rule |
|---|---|
| K-33 | The set of operations a session may use is the intersection of the operation catalogue, the operations named in the grant, and the operations carryable by the qualified channel tier.  |
| K-34 | An operation outside that intersection MUST be denied. There is no default-allow, and an operation not present MUST NOT be disclosed as existing.  |
| K-35 | A read operation MAY be denied by policy, and the operator MAY require approval for one. Policy MAY raise the approval requirement above the catalogue default but MUST NOT lower it below the floor in section 4.1.  |
| K-36 | `delete_file` is not enabled in the initial surface (XGN-DEC-047). While disabled, a request naming it MUST be denied with reason `OPERATION_NOT_ENABLED`.  |

### 4.1 The approval floor

| ID | Rule |
|---|---|
| K-37 | Every mutating operation MUST require an operator approval, on every transport (XGN-DEC-006). No configuration may reduce a mutating operation to automatic dispatch.  |
| K-38 | Clearing a turn gate MUST NOT satisfy the approval requirement of a mutating operation (XGN-DEC-011).  |

## 5. Fail-closed behaviour

Every ambiguity resolves to a denial or to a recheck. The engine MUST NOT infer permission from an unrecognised input.

| ID | Input condition | Decision | Error code | External service contacted |
|---|---|---|---|---|
| K-39 | Unknown operation name | `DENY` | `OPERATION_NOT_PERMITTED` | No  |
| K-40 | Operation not permitted by the grant | `DENY` | `OPERATION_NOT_PERMITTED` | No  |
| K-41 | Operation not enabled by policy | `DENY` | `OPERATION_NOT_ENABLED` | No  |
| K-42 | Repository is not the granted repository | `DENY` | `SCOPE_VIOLATION` | No  |
| K-43 | Branch is outside the granted branch | `DENY` | `SCOPE_VIOLATION` | No  |
| K-44 | Path is outside the granted directory | `DENY` | `SCOPE_VIOLATION` | No  |
| K-45 | Path or branch is not canonicalisable | `DENY` | `VALIDATION_FAILED` | No  |
| K-46 | Argument is missing, unknown or malformed | `DENY` | `VALIDATION_FAILED` | No  |
| K-47 | Grant is expired, revoked or suspended | `DENY` | `SESSION_EXPIRED`, `SESSION_REVOKED` | No  |
| K-48 | Grant digest does not match the grant in force | `DENY` | `INTEGRITY_MISMATCH` | No  |
| K-49 | Repository is not private | `DENY` | `REPO_NOT_PRIVATE` | Visibility read only  |
| K-50 | Expected blob digest, head digest or existing-object precondition does not hold | `DENY` | `PRECONDITION_FAILED` | Precondition read only  |
| K-51 | A limit is exceeded | `DENY` | `DRAFT_LIMIT`, `RATE_LIMITED` | No  |
| K-52 | A stale command capability or a sequence number mismatch | `DENY` | `LINK_EXPIRED`, `SEQ_INVALID` | No  |
| K-53 | Policy, provider or device state is unavailable, or changed during evaluation | `RECHECK` | `DEVICE_OFFLINE`, `PROVIDER_UNAVAILABLE` | No  |
| K-54 | A read operation, scope valid, policy permits | `ALLOW` | — | Read only, at dispatch  |
| K-55 | A mutating operation, scope valid, policy permits, approval recorded and unexpired | `ALLOW` | — | Only at dispatch, per XGN-17  |

Error codes are defined by XGN-23; this table fixes which code accompanies which input condition. A condition not listed here is resolved by the fail-closed rule: it is treated as `DENY`.

## 6. Revalidation points

Policy is not evaluated once. It is evaluated at three points, and the third is immediately before dispatch.

| ID | Point | Trigger | Evaluated against | Failure outcome |
|---|---|---|---|---|
| K-56 | R1 — every command | Arrival of any command from the agent plane | The command's operation and arguments, against the grant in force | The command is rejected; the draft, the pending envelope and the next sequence number are unchanged (INV-11)  |
| K-57 | R2 — commit | The draft is committed into an envelope | The committed envelope, against the grant in force and the current provider state | The request is not created. The draft is retained so the agent can correct it  |
| K-58 | R3 — immediately before dispatch | After an approval is recorded and before the provider call | The envelope, against the grant in force, the current repository visibility, the current permissions and the current provider state | The dispatch is refused. The request is closed as not dispatched, and the session is suspended if the failure is a privacy or permission failure (sections 7 and 8)  |

| ID | Rule |
|---|---|
| K-59 | R3 MUST NOT be skipped because an approval exists. An approval authorises a request; it does not certify that the world has not changed.  |
| K-60 | The reason for R3 is that repository visibility, repository permissions and branch state can all change between approval and dispatch. An evaluation performed at R1 or R2 is therefore stale by the time of dispatch.  |
| K-61 | The revalidation at R3 MUST be performed by the trusted runtime, not by the provider adapter and not by the gateway.  |
| K-62 | The result of each evaluation MUST be recorded as an audit event carrying the point identifier, the decision, the policy version and the failed check identifiers. It MUST NOT carry content (XGN-25).  |

The mechanical re-checks performed at R3 — re-canonicalisation, the repository privacy check, the permission check and the provider precondition re-read — are defined in XGN-08 section 9 (V-74 to V-79) and are executed under the dispatch checklist of XGN-17 section 4. This section defines the **policy** evaluation points and their outcomes. It does not redefine those checks.

## 7. Repository privacy

| ID | Requirement |
|---|---|
| K-63 | The repository MUST be private when the session is created. A grant naming a non-private repository MUST be refused.  |
| K-64 | The repository MUST be re-checked for privacy before every mutating commit (R2) and again immediately before dispatch (R3).  |
| K-65 | A visibility check that finds the repository is not private MUST deny the commit, MUST NOT dispatch the request, and MUST suspend the session (XGN-DEC-042). The operator is notified (XGN-16).  |
| K-66 | The privacy check MUST be a read. It MUST NOT be used to perform, complete or advance a mutation.  |
| K-67 | A repository that is not private MUST never be contacted for a mutating operation, at any point in the session.  |

## 8. Grant enforcement

The grant, its scope triple and its digest are defined by XGN-04 (S-07 to S-10). This section owns the **check**: when it runs, against what, and what a mismatch produces.

| ID | Requirement |
|---|---|
| K-68 | The policy engine MUST resolve the grant in force by `grant_id` and MUST compare the grant digest of that grant with the grant digest bound to the session and carried in the request digest inputs (XGN-04 S-07, XGN-31 section 2).  |
| K-69 | A mismatch MUST be denied with `INTEGRITY_MISMATCH`, before the external service is contacted, and MUST be recorded (XGN-04 S-10). The engine MUST NOT continue with the grant it read.  |
| K-70 | An envelope whose `grant_id` differs from the grant bound to the session MUST be rejected as `MALFORMED_REQUEST` (XGN-02 section 4.2).  |
| K-71 | The scope of a live session MUST NOT be widened (XGN-DEC-015, INV-17). The party a grant bounds MUST NOT be able to widen it.  |
| K-72 | A request that matches the grant in every respect but carries an argument the grant does not permit MUST be denied. A grant is a boundary, not a suggestion.  |
| K-73 | The grant check MUST be evaluated at R1, R2 and R3. It MUST NOT be evaluated once and then reused.  |

## 9. Step-dependency policy

The structural dependency checks are performed at validation and are owned by XGN-08 section 8 (V-68 to V-73). This section states the **policy** consequence of a violation.

| ID | Requirement |
|---|---|
| K-74 | The grant defines an ordered sequence of steps. Each step names the operation and the branch it writes to (XGN-04).  |
| K-75 | The branch created by the first step MUST be the branch written by the later steps. A `write_file` whose `branch` is not the branch created earlier in the same session MUST be denied with `STEP_DEPENDENCY_VIOLATION` (XGN-08 V-70).  |
| K-76 | The pull request head MUST equal the working branch of the session, and the pull request base MUST equal the base branch named by the grant. A pull request with any other head or base MUST be denied with `STEP_DEPENDENCY_VIOLATION` (XGN-08 V-71).  |
| K-77 | The check MUST use the branch actually created, taken from the execution result of the earlier step, and not a value supplied by the agent.  |
| K-78 | A step dependency violation MUST be denied, MUST be rejected before the external service is contacted (XGN-08 V-73), and MUST be reported to the operator as a scope failure, not as a provider error.  |

## 10. Policy versioning

| ID | Requirement |
|---|---|
| K-79 | Every envelope carries `policy_version`, stamped by the runtime (XGN-02 section 4.1). The format is `pol_<YYYY>_<MM>_<DD>_<n>`, where `n` increments on each change. |
| K-80 | A policy change MUST increment the version. The runtime MUST NOT evaluate a request against a policy configuration whose version does not match the version recorded on the envelope. |
| K-81 | For a request that has not been committed, the policy in force at commit governs. The agent MUST be told that the request was re-evaluated against a newer policy. |
| K-82 | For a request that is committed but not yet dispatched, the policy in force at dispatch governs. A policy that denies it stops the dispatch, and the operator is informed. |
| K-83 | An approval already granted remains valid for the envelope digest it is bound to, and MUST NOT override a denial produced by a later evaluation. |
| K-84 | A policy change MUST NOT retroactively validate a request that an earlier policy denied, and MUST NOT retroactively approve anything. |

## 11. Policy configuration

| ID | Requirement |
|---|---|
| K-85 | Policy configuration is owned by the operator and MUST be edited on the trusted device. It MUST NOT be editable from the agent plane, MUST NOT be delivered by a transport, and MUST NOT be sourced from a repository, a chat message or a provider. |
| K-86 | Policy configuration MUST be persisted in the device's durable store, MUST survive a runtime restart, and MUST carry its version. |
| K-87 | The operator MAY narrow the policy at any time. A narrowing takes effect at the next evaluation point, including R3 for a request already approved. |
| K-88 | The operator MUST NOT widen the scope of a live session. A wider scope requires a new session and a new grant (XGN-DEC-015). |
| K-89 | A widening of the policy itself MUST NOT validate a request that a narrower policy had denied, and MUST NOT widen a live session's grant. |
| K-90 | An operator configuration that would reduce the approval floor in section 4.1 MUST be rejected, and the previous configuration MUST remain in force. |
| K-91 | Every policy change MUST be recorded as an audit event carrying the old version, the new version and the operator identifier (XGN-25). |

## 12. Decisions recorded

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-006` | Approval on mutating commits | Yes, on every transport, for every mutating operation, including `create_pull_request`. | XGN-16 |
| `XGN-DEC-011` | Clearing a turn versus approval | Clearing a turn does not replace per-request approval. Both are retained. | XGN-16 |
| `XGN-DEC-014` | Reads against other refs | None by default. A read outside the scoped branch is rejected. | XGN-19 |
| `XGN-DEC-015` | Mid-session scope change | Not permitted. A change of scope requires a new session. | XGN-04 |
| `XGN-DEC-020` | Overwrite protection | Writing an existing file requires the expected blob digest; a branch operation requires the expected head digest. A mismatch is a rejection. | XGN-19 |
| `XGN-DEC-040` | Scope boundary check | A path is in scope only when its canonical form equals the canonical granted directory, or begins with it followed by `/`. A bare string prefix is not a scope check. | XGN-15 |
| `XGN-DEC-041` | Policy revalidation points | Policy is evaluated on every command, again at commit, and again immediately before dispatch. | XGN-15, XGN-17 |
| `XGN-DEC-042` | Repository privacy failure | A repository that is not private denies the commit and suspends the session. | XGN-15 |
| `XGN-DEC-048` | Step dependency | The branch created in the first step is the branch written in later steps. A pull request head equals the working branch and its base equals the granted base branch. | XGN-15 |

## 13. Related specifications

- **Depends on:** XGN-02 (Policy Decision Contract), XGN-04 (grant and scope model), XGN-08 (canonicalisation), XGN-19 (operation catalogue), XGN-31 (digest and binding).
- **Referenced by:** XGN-16, XGN-17, XGN-18, XGN-21, XGN-22, XGN-26, XGN-27.
- **Predecessor material:** the scope triple, the private-only rule and the policy re-checks described in the *Navigation Transport Specification* (0.1-draft) section 4, and the scope-validation recommendations in technical review A, deficiency 11, and technical review B, H-05 and H-06.
- **Out of scope:** the approval decision and the approval screen, the execution path, and the provider operation catalogue.
