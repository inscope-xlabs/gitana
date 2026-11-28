# Remote Repository Tool Bridge: Navigation Transport Specification

> **Status note (27 November 2026): predecessor material.** The Extended Gitana Navigation (XGN) Framework is now the authoritative source for agent ingress to Gitana. This document is retained as source material for the Composed GET workstream and is redistributed across the XGN specification set — principally [`docs/xgn/XGN_COMPOSED_GET_TRANSPORT.md`](../xgn/XGN_COMPOSED_GET_TRANSPORT.md), [`XGN_COMPOSED_GET_COMMAND_PROTOCOL.md`](../xgn/XGN_COMPOSED_GET_COMMAND_PROTOCOL.md) and [`XGN_COMPOSED_GET_LINK_AND_REPLAY_SECURITY.md`](../xgn/XGN_COMPOSED_GET_LINK_AND_REPLAY_SECURITY.md). Where this document and an XGN module disagree, XGN wins. Its "v2" references are out of scope for XGN and have no authority there. See [`docs/xgn/XGN_FRAMEWORK_SPECIFICATION_INDEX.md`](../xgn/XGN_FRAMEWORK_SPECIFICATION_INDEX.md).

**Status:** Review draft | **Version:** 0.1-draft | **Date:** 25 November 2026
**Companions:** [Tool Bridge v2](remote_repository_tool_bridge_v2_specification.md), [Cloudflare Relay](cloudflare_relay_specification.md)
**Normative language:** MUST / MUST NOT / SHOULD / SHOULD NOT / MAY are requirements keywords.

## 1. Purpose and scope

Some AI agents can only perform HTTPS GET, cannot set request headers, and cannot run `curl`. This document specifies an optional **Navigation Transport**: an agent is given exactly one session URL and drives a single, narrowly scoped task by following server-provided links and issuing small GET commands. It is a new adapter in front of the same canonical Tool Contract, Policy Engine, lifecycle and audit defined in v2. It does not replace the localhost POST routes or the relay envelope.

Out of scope: the tool surface (v2 §7), GitHub execution, and the relay's WebSocket protocol (relay spec). This transport is served by the relay Worker and executes on the phone.

## 2. Design principles

| Principle | Requirement |
| --- | --- |
| One URL | The agent is given only the session surface URL. Every later link is issued by the server. |
| Two planes | Agent controls and operator controls are strictly separate (§3). The agent plane contains no operator verbs. |
| One task, one scope | A session is bound to one task and one scope triple (§4). Sessions are isolated from each other. |
| Phone decides | Policy, approval and execution stay on the phone (v2 §6.2). This transport only narrows access further. |
| Fail closed | Unknown commands, stale links, scope mismatches, locked sessions and malformed parameters are rejected. |
| Detection by design | Single-use rolling links make session theft and replay observable (§6.3). |
| No hidden state in the agent | The agent holds no secret other than the current link. It cannot change its own scope, phase or approvals. |

## 3. Planes

| Plane | Who | May do |
| --- | --- | --- |
| **Agent plane** | The AI agent | Read onboarding docs; traverse the map; read operations; build a draft with typewriter commands; request commit; read status. |
| **Operator plane** | The human operator on the phone (and any separately authenticated admin API) | Create, scope, pause, extend and revoke sessions; set phases and gates; clear turns; verify the PIN; approve or deny commits; change policy; rotate credentials. |

- No agent-plane response may contain a link or parameter that performs an operator-plane action.
- Operator-plane authentication is independent of every agent-plane credential and of the PAT.
- Chat between agent and operator is outside the system. The server MUST NOT treat any chat message as authorization; only operator-plane actions on the phone change session state.

## 4. Session scope

### 4.1 Scope triple

A session is created with a scope triple that is fixed for its lifetime:

| Element | Rule |
| --- | --- |
| Repository | Exactly one approved repository, referenced by canonical owner/name. MUST be private. |
| Branch | Exactly one branch name, or one branch-name prefix reserved for this task (open decision N3). Normalized per v2 §6.3. |
| Directory | One canonical path prefix. All paths are normalized (v2 §7.2) and MUST fall within it. |

All three must match on every command. A request outside any element is rejected as `POLICY_DENIED` without contacting GitHub.

### 4.2 Private-only rule

- The component MUST NOT accept a public repository in a session scope.
- The phone MUST verify repository visibility from GitHub at session creation and again before each mutating commit, because visibility can change. If the repository is not private, the commit is denied and the session is suspended and flagged.
- Pull requests MUST have head and base in the same scoped repository. Cross-repository or fork pull requests are out of scope for this transport.
- The scoped PAT must remain a fine-grained token restricted to approved repositories (v2 §6.1). The PAT and the policy framework are never exposed on this plane.

### 4.3 What scoping does and does not give

Scoping and policy limit what a holder of the session URL can do. They do not make the URL secret-proof: until it is revoked or expires, whoever holds a valid current link can act inside the scope. Protection comes from short lifetime, single-use rolling links, approval gates and audit (§6, §9), which bound damage and make misuse detectable. This document does not claim that misuse is prevented.

## 5. Session lifecycle

| State | Meaning | Agent-visible map |
| --- | --- | --- |
| `CREATED` | Operator created the task; URL not yet used. | n/a |
| `AWAITING_PIN` | URL used; docs fetched; operator has not confirmed the PIN. | Onboarding docs and a status link only. |
| `ACTIVE` | A turn is cleared; commands allowed within the current phase and step. | Nodes allowed for the current step. |
| `WAITING_FOR_TURN` | A gate was reached; operator has not cleared the next turn. | WAIT node only. |
| `SUSPENDED` | Paused by operator, or flagged by detection. | WAIT node only. |
| `COMPLETED` | Task finished. | Terminal status only. |
| `EXPIRED` / `REVOKED` | Time limit reached, or operator revoked. | Rejected. |

Transitions out of `AWAITING_PIN`, `WAITING_FOR_TURN` and `SUSPENDED` happen only by operator-plane action. The agent cannot cause any of them.

## 6. Credential model

### 6.1 Task-session capability

Agents that cannot set headers must carry their authority in the URL. v2 §5.1 forbids credentials in URLs, so this transport introduces a new credential class, `TASK_SESSION`, to be added to the Credential Contract:

- Issued per session, scoped to the scope triple, never reusable across sessions.
- Short lifetime (default proposal: 2 hours, operator-extendable) and bounded step count.
- Rotated on every response (§6.2). Only the newest link is valid.
- Not derived from, and never equal to, the GitHub PAT, local-client credential, relay-device credential or remote-agent credential.
- The PAT and other long-lived credentials MUST NOT appear in any URL.

### 6.2 Rolling single-use links

Each response contains the next link(s); each link carries a token bound to the session, the node, the next sequence number and a hash of its allowed parameters. Using a link consumes it. Properties:

- Defeats fetch-tool caching: every URL is unique.
- Prevents replay and duplicate `append` effects.
- Idempotency: re-sending the same consumed link returns the cached result of the original call for a short window, then `LINK_CONSUMED`.

### 6.3 Theft and replay detection

If a consumed or superseded link is used, the server MUST record a `LINK_REUSE` event. Unless it is an exact retry within the idempotency window from the same session fingerprint, the session MUST be suspended and the operator alerted. When a thief and the legitimate agent both use the session, one of them hits a consumed link, which surfaces the fork. This detects use; it does not prevent the first use by an attacker.

### 6.4 Logging

URLs carry the capability, so intermediaries may log it. Relay and edge configuration MUST NOT record full URLs or query strings for this route where the platform allows it, and the spec's deployment checklist (§13) MUST verify this. Because links are single-use and short-lived, a logged link is low-value once consumed, but this remains a residual risk (§10).

## 7. Onboarding docs and PIN

- The first node is operator-authored onboarding documentation, served as plain text. It describes the task rules, allowed commands, the pause-and-report behavior, and a per-session PIN.
- The PIN is generated per session (minimum 6 characters), expires with the session, and is shown to the operator on the phone.
- The agent is instructed to return the PIN in chat to the operator and then stop. The agent MUST NOT be able to submit the PIN to the server.
- The operator compares the chat PIN with the phone and confirms on the phone; only that action moves `AWAITING_PIN` to `ACTIVE`.
- Meaning: the PIN binds the chat counterpart to this session's URL. It is not proof that the agent understood the docs or is trustworthy.
- Onboarding docs are operator content, versioned and hash-recorded in the audit log. They MUST NOT include repository content.

## 8. Phases, steps and turns

- A grant defines ordered phases, each with ordered steps. A step defines: allowed operations, command budget, and a gate.
- Gate types: after N commands; after a named node (for example `preview`); before every mutating commit; phase boundary; operator-initiated pause at any time.
- At a gate the map returns only a WAIT node with these properties: no forward links; a rate-limited re-check link; text instructing the agent to report in chat and wait.
- The re-check link returns WAIT until the operator clears the turn. Polling beyond the rate limit returns `RATE_LIMITED`; excessive polling MAY suspend the session.
- Operator "clear turn" is a phone action. Clearing a turn does not replace per-commit approval for mutating operations (open decision N4).
- The agent's chat report is not verified by the server. The operator judges it.

## 9. Agent-plane surface

### 9.1 Compatibility rule

All commands use one fixed path with parameters only in the query string, because some fetch tools only fetch URLs exactly as provided or flag URLs the agent edits:

```
GET https://relay.example/nav/v1/s?t=<step-token>&cmd=<command>&<params>
```

The server returns the next fully formed link(s). Agents that cannot compose a query string can use only links issued verbatim (Tier A/B below). Each target agent's fetch tool MUST be tested for composing URLs with extra `&` parameters before Tier C is enabled for it (§12).

### 9.2 Response format

Plain UTF-8 text or small JSON with: `state`, `phase`, `step`, `message` (instructions), `next` (list of links with labels), `hashes`, and `error` if any. Responses MUST NOT contain agent-authored content beyond sizes and hashes unless the command is an explicit read. Map labels and messages are generated by the phone/relay only and never derived from repository content, which appears only in clearly delimited data fields.

### 9.3 Tiers

| Tier | Capability | Requirement |
| --- | --- | --- |
| A | Reads and navigation: docs, status, `list_branches`, `read_file` | Links issued verbatim; works with the most restricted fetcher. |
| B | Mutations with arguments fixed at grant time | Links issued verbatim; each mutation needs phone approval. |
| C | Agent-authored arguments and content via the typewriter builder | Fetch tool can compose query strings; explicit opt-in per session. |

### 9.4 Typewriter commands (Tier C)

| Command | Parameters | Effect |
| --- | --- | --- |
| `new` | `op` | Open a draft for one operation within the scope. One open draft per session. |
| `set` | `field`, `value`, `seq` | Set a short field (`path`, `branch`, `message`, `title`, ...). |
| `append` | `field`, `data`, `seq` | Append a base64url chunk to a long field (`content`). |
| `unset` | `field`, `seq` | Clear a field. |
| `preview` | `seq` | Returns sizes, normalized path, and content hash. Does not echo content. |
| `commit` | `hash`, `seq` | Requests dispatch of the draft. Content hash MUST match. |
| `cancel` | `seq` | Discards the draft. |

Rules:

- `seq` is a strictly increasing sequence number issued in the previous response. Duplicates are idempotent; gaps are rejected.
- Field values are validated on arrival: charset, size, path normalization and scope containment. Failures are rejected immediately with a sanitized error.
- Chunk size is bounded (default proposal: 2 KB of encoded data per command). Total draft size is capped per session; this mechanism is for small text edits, not large files.
- The server stamps `request_id`, `issued_at`, `expires_at`, contract version and the principal assertion at commit. The agent can supply only tool arguments.
- The Worker validates shape, size, sequence and rate only. The draft is forwarded to and held by the phone in bounded memory so the relay stores no content (relay spec §7, §10). The phone evaluates policy on each command and again at commit.
- `commit` creates the canonical request which enters the v2 lifecycle at `RECEIVED`. The phone shows the operator the diff against the current file and the content hash; the approved hash is the dispatched hash, so content cannot change between approval and dispatch.
- Drafts are discarded on commit, cancel, expiry, suspension, revocation, or phone restart.

### 9.5 Mapping to v2

| Navigation concept | v2 equivalent |
| --- | --- |
| Draft | Pre-envelope construct inside the navigation adapter; no v2 state |
| `commit` accepted | `RECEIVED` |
| Operator approval | `AWAITING_APPROVAL` then `EXECUTING` |
| Result link | Response from `GET /requests/{request_id}` semantics, via rolling link |
| `OUTCOME_UNKNOWN` | Reported unchanged; agent must reconcile with reads, never re-commit blindly |

## 10. Threat model

| Threat | Mitigation | Residual risk |
| --- | --- | --- |
| Stolen session URL | Short TTL, scope triple, single-use rolling links, approvals, fork detection, revocation | Attacker acting first inside scope before detection |
| Replay of a link | Consumption plus idempotency window | None beyond the window |
| Prefetch or crawler triggering GET | Links are unguessable and single-use; mutations require phone approval; draft changes never touch GitHub | Draft corruption by a stray fetch (fails safe: hash mismatch at commit) |
| Prompt-injected agent | Agent plane has no operator verbs; scope and approval enforced on phone; map text never derived from repo content | Agent can still propose harmful content within scope; operator approval is the control |
| URL logging by relay/edge/fetch provider | Logging disabled where possible; single-use links; small chunk bounds | Content-bearing URLs may be retained by intermediaries and the agent's provider |
| Public repo or visibility flip | Private-only check at grant and before each commit | Window between checks |
| Scope escape via paths | Canonical normalization; prefix checks insufficient (v2 §7.2) | Normalizer bugs; covered by test gates |
| Wrong agent in chat | PIN handshake confirmed on phone | PIN is not trust in the agent |
| Relay compromise | Trusted-relay profile applies (v2 §6.4); phone re-validates everything | Relay can read/alter content in transit |
| Approval fatigue | Command budgets and turn gates reduce volume | Operator judgment |
| TOCTOU between approval and dispatch | Approval bound to content hash and file SHA; conflict at GitHub fails the write | None expected |

## 11. Errors

Adds to v2 §11.1: `SESSION_LOCKED`, `SESSION_SUSPENDED`, `LINK_CONSUMED`, `LINK_EXPIRED`, `SEQ_INVALID`, `SCOPE_VIOLATION`, `REPO_NOT_PRIVATE`, `DRAFT_LIMIT`, `DRAFT_HASH_MISMATCH`, `TURN_NOT_CLEARED`. Error text is sanitized and never contains tokens or content.

## 12. Limits and testing

Limits (all placeholders until measured): URL length, chunk size, draft total size, commands per step, polling rate, session TTL, concurrent sessions per device.

Acceptance criteria:

| Area | Criteria |
| --- | --- |
| Compatibility | Each supported agent fetch tool can fetch a self-composed URL with extra `&` parameters, or is restricted to Tier A/B. |
| Scope | Any request outside repo, branch or directory is denied with no GitHub call. |
| Private-only | Public repo rejected at grant; visibility flip denied at commit. |
| PIN gate | Session stays locked until phone confirmation; the agent cannot unlock it. |
| Turn gates | Agent cannot advance without operator clearance; WAIT exposes no forward link. |
| Replay | Consumed link rejected; fork triggers suspension and alert. |
| Hash binding | Altered draft after preview fails at commit; approved hash equals dispatched hash. |
| Caching | Repeated polls never return stale state. |
| Privacy | No tokens or content in logs; relay config verified. |
| Isolation | One session cannot observe or affect another. |

## 13. Deployment checklist

- Disable URL/query-string logging for the navigation route at the relay and edge where supported; verify in tests.
- Operator plane authenticated independently; no shared secrets with the agent plane.
- Document the trusted-relay assumption (relay spec §9.1) in operator-facing text.

## 14. Required amendments to companion specs

- **v2 §5.1/§6.1:** add the `TASK_SESSION` credential class and an explicit exception stating it, and only it, may appear in a URL.
- **v2 §10/§11:** state that this route's query strings are excluded from logs.
- **Relay spec §5:** add the navigation route group and the phone-held draft forwarding.
- **Relay spec §7, §10:** clarify that draft chunks are forwarded, not retained.
- **v2 §6.3:** note per-task scope as the preferred per-agent scoping mechanism.

## 15. Open decisions

| ID | Decision | Recommended default |
| --- | --- | --- |
| N1 | Session and link lifetimes | 2 h session, links valid until used or 10 min |
| N2 | PIN format and length | 6-8 digits, one per session, re-issued on session restart |
| N3 | Branch scope: exact name or prefix | Exact name, or a task-reserved prefix such as `agent/<task-id>` |
| N4 | Does clearing a turn replace per-commit approval | No; keep both for delete and pull request |
| N5 | Maximum draft size and chunk size | 2 KB chunks, small total cap after device testing |
| N6 | Whether Tier C is available by default | Off; explicit per-session opt-in |
| N7 | Which reads may target refs other than the scoped branch | None by default |
| N8 | Whether the operator may widen scope mid-session | No; create a new session |

## 16. Tasks required to leave draft status

- [ ] Resolve N1-N8 and record decisions.
- [ ] Publish machine-readable schemas for the map response, link token and commands.
- [ ] Define the token format, binding and idempotency-window rules.
- [ ] Write the operator-plane specification (session creation UI, PIN confirmation, turn clearing, revocation, alerts).
- [ ] Test a set of real agent fetch tools for query-string composition and caching behavior; record results per tool.
- [ ] Apply the amendments in §14 to the v2 and relay specs.
- [ ] Validate private-repo visibility checks and PR head/base constraints against the GitHub API.
- [ ] Define the draft-handling protocol between Worker and phone, including offline and restart behavior.
- [ ] Complete the threat model review and an independent security review.
- [ ] Add a changelog and change the status header.
