# XGN-09: XGN Transport Abstraction and Selection

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-09 | **Group:** C — Transport and command surface
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 9

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module defines the transport-neutral model every XGN transport adapter MUST satisfy: the services a transport must provide, the services it must not provide, the capabilities a transport declares, the channel tier taxonomy, the deterministic selection procedure, the conditions for changing transport inside a live session, and the convergence properties that make one transport interchangeable with another.

A transport is a **carrier**. It changes how a request travels, never what the request may do (XGN-00 DP-10, INV-20).

In scope: transport adapter obligations; the capability declaration vocabulary; the channel tier taxonomy; the selection procedure and the operator's confirmation of it; transport fallback within a session; the convergence and conformance requirements.

Out of scope: the framing of any individual transport (XGN-10 to XGN-14, XGN-28 to XGN-30); the shared contracts (XGN-02); command capabilities and replay rules (XGN-29); grant, policy, approval and execution semantics (XGN-04, XGN-15, XGN-16, XGN-17); the state machine (XGN-21); the error catalogue (XGN-23); audit event types (XGN-25); probe identifiers and probe procedures (XGN-30).

## 2. The transport-neutral model

### 2.1 What every transport MUST provide

| ID | Requirement |
|---|---|
| G-01 | A transport MUST deliver a request into the shared request contract of XGN-02 §4 without interpreting it. Framing, encoding and carrying are its only concern. |
| G-02 | A transport MUST carry the session binding — `session_id`, `grant_id`, `step_id` and the session sequence number — sufficient to route the request to the correct session and step. |
| G-03 | A transport MUST carry a status read, so the agent can obtain the current panel without issuing a mutating command. A status read MUST be non-consuming and rate limited (XGN-DEC-023). |
| G-04 | A transport MUST return a next surface: the reply to every command MUST contain the current panel and the commands legal in the resulting state (XGN-03 §3.2). |
| G-05 | A transport MUST support an explicit content read, and MUST return agent-written or repository-derived content only through it, marked as data (XGN-02 §11, INV-13). |
| G-06 | A transport MUST preserve request identity across carriers: the same logical operation on two transports MUST produce envelopes differing only as XGN-02 §4.2 permits. |

### 2.2 What every transport MUST NOT do

| ID | Requirement |
|---|---|
| G-07 | A transport MUST NOT decide permissions, evaluate policy, capture an approval or cause execution. |
| G-08 | A transport MUST NOT hold authoritative state. Sessions, grants, drafts, envelopes, policy decisions, approvals, execution records and audit records live on the trusted runtime (XGN-00 §5.1). A transport MAY hold only bounded, non-authoritative state: rate counters, a short-lived capability registry, and a single-reply idempotency cache whose contents MUST NOT include agent-authored content (XGN-DEC-027). |
| G-09 | A transport MUST NOT widen a grant, including by supplying an argument value the grant does not permit (INV-17). |
| G-10 | A transport MUST NOT treat a successful delivery as authorisation or as evidence of execution (XGN-00 §5). |
| G-11 | A transport MUST NOT present a command illegal in the current state, and MUST NOT offer a command above the session's channel tier (INV-12). |
| G-12 | A transport MUST NOT place a long-lived credential in a URL, a link, a reply, a log, an error message or an event attribute (INV-14). |
| G-13 | A transport MUST NOT return agent-written or repository-derived content outside an explicit read, and MUST NOT label such content as an instruction (XGN-00 AR-08). |
| G-14 | A transport MUST NOT fail open. Unknown, malformed, stale, out-of-scope or integrity-invalid input MUST be rejected (INV-15). |

## 3. Transport capability declarations

### 3.1 The declaration

Each transport family MUST publish a declaration of the capabilities below. The declaration MUST be truthful, MUST be static for the family, and MUST reach the agent in the session's capability description as `transport_caps` (XGN-02 §5).

| Capability | Meaning |
|---|---|
| `read` | The transport can return a panel, a status read or a capability description. |
| `compose_query` | The agent can build a request by adding parameters to a value it holds. |
| `extend_link` | The agent can extend a link that arrived in a reply with parameters of its own. |
| `no_headers` | The transport cannot carry request headers; what would be a header is carried inside the request. |
| `can_set_content_type` | The transport can set the request content type. |
| `set_header` | The transport can carry an authentication header. |
| `post_body` | The transport can carry a request body. |
| `tool_call` | The transport delivers through an agent connector tool call. |
| `capability_in_url` | The transport places a short-lived command capability in the request URL. XGN-29 owns the capability contract; this is the only credential a URL may ever carry. |
| `bulk_content` | The transport can carry agent-authored content beyond a short field. |
| `offline` | The transport delivers no machine-to-machine request; a human carries it. |
| `out_of_band` | The transport can deliver output to a stream other than the request reply. |

| ID | Requirement |
|---|---|
| G-15 | A transport MUST publish its declaration before it is offered to an operator, and MUST publish the same declaration to every agent it serves. |
| G-16 | A transport MUST NOT rely on a caller inferring a capability from a successful call. A call that depends on an undeclared capability MUST be rejected with `VALIDATION_FAILED`. |
| G-17 | A transport MUST NOT use its declaration to infer permission. The declaration describes the channel; the grant describes the authority. |

### 3.2 Declared capabilities by transport family

| Capability | Connector / tool (XGN-11) | Composed GET (XGN-10) | Shell (XGN-12) | Copy/paste handoff (XGN-13) |
|---|---|---|---|---|
| `read` | declared | declared | declared | declared, by operator relay |
| `compose_query` | declared | declared | declared | not declared |
| `extend_link` | declared | declared | not declared | not declared |
| `no_headers` | not declared | declared | not declared | declared |
| `can_set_content_type` | declared | not declared | declared | not declared |
| `set_header` | declared | not declared | declared | not declared |
| `post_body` | declared | not declared | declared | not declared |
| `tool_call` | declared | not declared | not declared | not declared |
| `capability_in_url` | not declared | declared | not declared | not declared |
| `bulk_content` | conditional, channel tier C only | not declared | conditional, channel tier C only | not declared |
| `offline` | not declared | not declared | not declared | declared |
| `out_of_band` | not declared | not declared | declared | not declared |

A conditional entry is unavailable until the channel tier is enabled for the session (§4.2).

## 4. Channel tiers

### 4.1 The taxonomy

| Channel tier | What it may carry | Who authors the arguments | Approval |
|---|---|---|---|
| A | Reads and navigation: onboarding, panel, status, capability description, and the reads the grant permits. | Nobody. | Not required; a read never executes. |
| B | Mutations whose operation and arguments are fixed by the grant. | Nobody, except selection among the values the grant declares for that operation. | Required for every mutating operation (XGN-DEC-006). |
| C | Mutations whose arguments, and optionally content, are authored by the agent. | The agent. | Required for every mutating operation (XGN-DEC-006). |

| ID | Requirement |
|---|---|
| G-18 | The channel tier MUST be recorded on the session and MUST be visible to the agent in the capability description. |
| G-19 | A transport MUST reject a command the session's channel tier does not carry, with `STATE_INVALID` and a remediation naming the commands that are available. |
| G-20 | A command rejected under G-19 MUST NOT consume a capability, MUST NOT advance the session sequence number and MUST NOT alter the draft (XGN-DEC-024, INV-11). |
| G-21 | Raising the channel tier MUST NOT widen the grant and MUST NOT create a second approval path. |

### 4.2 Channel tier availability

Channel tier C is off by default. It MUST be enabled per session and per channel, and only after that channel has passed qualification. The enablement rule and the qualification criteria are owned by XGN-10 (XGN-DEC-013) and the probes are owned by XGN-30; this module states only the consequences for selection.

| ID | Requirement |
|---|---|
| G-22 | A transport MUST NOT offer channel tier C for a session in which it is not enabled. |
| G-23 | The operator MAY enable a lower tier for a channel that did not qualify for a higher one. A channel that fails qualification MUST NOT be offered that tier. |
| G-24 | Only an operator-plane action may enable or disable a channel tier. An agent request is not an enablement (INV-01). |

## 5. Channel selection

### 5.1 Order

The order is fixed: **Connector, then Composed GET, then Shell, then Copy/paste handoff** (XGN-DEC-001).

| Order | Channel | Preferred because |
|---|---|---|
| 1 | Connector | The agent receives structured results and structured errors, and no URL is exposed to an intermediary's log. |
| 2 | Composed GET | The agent needs only a fetch capability, the most widely available primitive. |
| 3 | Shell | Needs a shell and a network path; useful where no fetch tool exists but a process can run. |
| 4 | Copy/paste handoff | Always available, because a human can always carry a package. The lowest capability tier. |

### 5.2 Who decides

Gitana proposes. The operator confirms at session creation (XGN-DEC-001).

| ID | Requirement |
|---|---|
| G-25 | The proposal MUST list the qualified channels in order, the recommended channel, the recommended channel tier, and the probe outcomes supporting the recommendation. |
| G-26 | The operator's confirmation MUST be an operator-plane action on the trusted device, recorded as an event. It MUST NOT be inferred from anything an agent says (INV-02). |
| G-27 | The confirmed channel MUST be one the proposal permits. An operator MAY confirm a lower-ranked qualified channel. |

### 5.3 When selection happens

Selection happens at **session establishment**. It MUST NOT be re-decided at execution time, and the runtime MUST NOT choose or switch a transport to satisfy an individual request. Changing transport inside a live session is a fallback under §6 and requires operator consent.

| ID | Requirement |
|---|---|
| G-28 | A transport MUST NOT be selected per request. The confirmed channel MUST remain in force until the session ends, the operator revokes it, or a fallback under §6 occurs. |

### 5.4 The selection outcome vector

Selection MUST be a function of the qualification outcome. Probe identifiers are assigned in XGN-30; this table names the probe function, so the mapping stays in one place.

| Outcome | Probe function | True means |
|---|---|---|
| O-TOOL | The agent platform exposes a Gitana connector. | Tool calls are available. |
| O-SHELL | The agent can run a command with network access to Gitana. | Shell is available. |
| O-OPEN | The agent can open a URL that is delivered to it. | A fetch path exists. |
| O-REPLY-LINK | The agent can open a link that appeared only inside a fetched reply. | The runtime can drive the agent. |
| O-EXTEND | The agent can add parameters to such a link. | The agent can compose commands. |
| O-PAYLOAD | The largest intact composed request URL reaches at least 900 characters. | The encoded payload ceiling is sufficient. |
| O-ENCODING | Reserved and non-ASCII characters survive a round trip byte for byte. | Command encoding is exact. |
| O-PREFETCH | The application does not automatically open links that appear inside a reply. | No prefetch can consume a command capability. |
| O-CONTINUITY | The newest link still opens after the chat turns once. | The session survives a turn. |
| O-REPEAT | A repeated identical delivery is refused or answered identically. | Idempotency is available on the channel. |

### 5.5 The selection decision table

The table is evaluated top to bottom. The first row whose condition holds determines the recommendation. Exactly one row holds for every outcome vector, so the recommendation is deterministic for every combination, including partial failures.

| Order | Condition | Recommended channel | Channel tier ceiling |
|---|---|---|---|
| 1 | O-TOOL | Connector | channel tier B, or channel tier C when the channel has qualified for it |
| 2 | not O-TOOL, and O-OPEN, O-REPLY-LINK, O-EXTEND, O-PAYLOAD, O-ENCODING, O-PREFETCH, O-CONTINUITY and O-REPEAT are all true | Composed GET | channel tier C |
| 3 | not O-TOOL, and O-OPEN and O-REPLY-LINK are true, and row 2 does not hold | Composed GET | channel tier B |
| 4 | not O-TOOL, not O-REPLY-LINK, O-SHELL | Shell | channel tier B, or channel tier C when the channel has qualified for it |
| 5 | not O-TOOL, not O-REPLY-LINK, not O-SHELL | Copy/paste handoff | channel tier B |
| 6 | The operator declines the recommendation | Copy/paste handoff | channel tier B |

Row 6 is the operator's decision rather than a probe outcome. It is the terminal row and guarantees the table is total.

| ID | Requirement |
|---|---|
| G-29 | The recommendation MUST be a pure function of the outcome vector, and the confirmed channel MUST be one the recommendation permits. |
| G-30 | A channel that failed qualification MUST NOT be recommended, and MUST NOT be confirmed for a tier it did not qualify for. |
| G-31 | When O-PREFETCH is false, the channel tier MUST be capped at channel tier B until qualification records the inert-capability-base property as satisfied (XGN-29, XGN-DEC-017). |
| G-32 | The session MUST record the outcome vector, the recommended channel, the confirmed channel, the confirmed channel tier, the time of confirmation and the qualification run identifier. The record MUST NOT contain a capability or a credential. |

## 6. Transport fallback within a session

A session MAY move to another transport without widening scope, skipping approval or invalidating an existing approval (XGN-DEC-030, INV-20).

### 6.1 Conditions and consent

| ID | Requirement |
|---|---|
| G-33 | A fallback MAY be proposed when the confirmed channel has become unusable: the application can no longer open links, the connector has been revoked, the shell is gone, the agent has moved to another application, or the channel cannot refresh an expired credential. |
| G-34 | Expiry or revocation of the **session** is not a fallback. The session ends and a new one MUST be created. |
| G-35 | An agent MAY report that the channel is unusable. Reporting is not a change; only an operator-plane action changes the channel (INV-01). |
| G-36 | A fallback MUST be confirmed by the operator on the trusted device. The runtime MUST record the confirmation as an event of type `transport.changed`, carrying the previous channel, the new channel, the operator principal and the time. |
| G-37 | The operator MUST confirm the new channel tier as well as the new channel. A fallback MUST NOT raise the channel tier by itself. |

### 6.2 What is preserved

| Preserved across a fallback | Why |
|---|---|
| The grant, its scope, its steps and its expiry | The grant belongs to the session, not to the channel (INV-17). |
| The open draft and its parts | A draft is runtime state, not channel state (XGN-07). |
| The committed request and its envelope digest | The approved request is the dispatched request (INV-06). |
| The policy decision already taken | Policy does not take a transport as an input (XGN-02 §4.1). |
| Any approval already given | A fallback MUST NOT invalidate an approval (XGN-DEC-030). |
| The session sequence number | The next command uses the number already issued (XGN-28). |
| The command and request budget, and the audit trail | The session's limits do not reset; the change is added to the trail. |

### 6.3 What is re-issued, and what is forbidden

| ID | Requirement |
|---|---|
| G-38 | A fallback MUST issue a fresh channel credential for the new channel and a fresh capability base bound to the same session and the same next sequence number. |
| G-39 | The capability description MUST be reissued with the new `transport` and `transport_caps` values (XGN-02 §5). |
| G-40 | The reply that announces the change MUST state the new channel, the new credential presentation method and the unchanged session sequence number, so the agent can continue without guessing. |
| G-41 | A fallback MUST NOT widen scope, change the grant, skip an approval, invalidate an approval already given, or permit a command above the confirmed channel tier. |
| G-42 | A fallback MUST NOT alter a committed envelope, and therefore MUST NOT change the `transport` value recorded on a request already committed. |
| G-43 | A fallback MUST NOT re-deliver a command already accepted, unless the exact-retry rule applies (XGN-DEC-007, INV-08). |
| G-44 | A fallback MUST NOT cause the policy engine to be re-entered with a different transport as an input, and MUST NOT change a policy decision already taken. |

## 7. Convergence requirements

### 7.1 The convergence set

Two sessions that request the same operation with the same arguments on different transports MUST converge on the same pipeline.

| Element | Requirement across transports | What may differ |
|---|---|---|
| Shared request contract | The same field set, operation and normalised arguments (XGN-02 §4) | `transport`, `request_id`, `issued_at`, `expires_at`, `draft_digest`, `envelope_digest` |
| Grant | The same `grant_id` and the same scope | nothing |
| Policy decision | The same `decision`, `policy_version` and check set. The transport MUST NOT be an input to the policy engine | nothing |
| Approval requirement | The same operation requires approval on every transport (XGN-DEC-006) | nothing |
| Approval | The approval is bound to the envelope digest of the committed request; within one request the approved and the dispatched envelope are byte-identical (INV-06) | `approval_id` and timestamps between separate requests |
| Execution | The same provider adapter, preconditions and outcome semantics (XGN-17) | `execution_id` |
| Audit | The same event types and attribute set (XGN-25) | the `transport` attribute value |

### 7.2 Transport conformance checklist

A transport MUST satisfy every clause below. XGN-26 MUST provide a conformance test for each of them.

| ID | Requirement | Cross-reference |
|---|---|---|
| G-45 | The transport publishes a capability declaration that matches its behaviour | XGN-03 §5 |
| G-46 | The transport delivers requests as the shared request contract, supplies no runtime-stamped field and adds no authority field of its own | XGN-02 §4 |
| G-47 | Every command is bound to the session, the grant, the step and the session sequence number; an out-of-sequence command is rejected without advancing the sequence number | XGN-04, XGN-DEC-024 |
| G-48 | Only the commands legal in the current state are presented and accepted, and the channel tier ceiling is enforced | XGN-03 §3.2, §4 |
| G-49 | Content is returned only in an explicit read, marked as data | XGN-02 §11 |
| G-50 | No long-lived credential appears in a URL, a link, a reply, a log or an error | INV-14 |
| G-51 | Errors are returned in the Error Contract shape, with a stable code and a remediation | XGN-02 §10, XGN-23 |
| G-52 | The audit events of XGN-25 are emitted for the commands the transport carries, carrying the `transport` attribute | XGN-25 |
| G-53 | An exact retry of the same request identity inside the idempotency window returns the original reply and produces no second effect; any other reuse of a consumed identity is recorded and suspends the session | XGN-DEC-007, INV-08, INV-09 |
| G-54 | A status read consumes no capability, advances no sequence number, mutates nothing and is rate limited | XGN-DEC-023 |
| G-55 | A reply carrying agent-authored content is not cached | XGN-DEC-027 |
| G-56 | The same capability description content is exposed as on every other transport | XGN-03 §5 |
| G-57 | Unknown, malformed, stale, out-of-scope and integrity-invalid input is rejected | INV-15 |
| G-58 | A transport change preserves the grant, policy, approval, execution and audit guarantees | INV-20, XGN-DEC-030 |
| G-59 | The transport passes the conformance tests in XGN-26 that apply to its channel tier | XGN-26 |

## 8. Decisions recorded

| ID | Subject | How this module records it | Owner |
|---|---|---|---|
| `XGN-DEC-001` | Channel order | §5.1 fixes the order; §5.2 fixes who confirms; §5.4 and §5.5 fix the recommendation for every probe outcome combination | XGN-09 |
| `XGN-DEC-030` | Transport fallback | §6 fixes the conditions, the operator-consent requirement, what is preserved and what is re-issued | XGN-09 |
| `XGN-DEC-013` | Channel tier availability | Referenced in §4.2. The enablement rule is owned by XGN-10; only its effect on selection is stated here | XGN-10 |
| `XGN-DEC-016` | Supported chat applications | Referenced in §5.4. Qualification is by probe outcome, not by product name; the named list remains open | XGN-30 |
| `XGN-DEC-002` | "Curl / git" meaning | Referenced in §2.2 and §5.5. A channel is a way to reach Gitana; direct git with agent-held credentials is not one | XGN-12 |
| `XGN-DEC-007` | Idempotency window and caller identity | Referenced in §6.3 and §7.2 as the retry rule every transport MUST honour | XGN-29 |

## 9. Open decisions

| ID | Question | Owner |
|---|---|---|
| OD-09-01 | Which chat applications are in scope for qualification is not settled. Qualification is by probe outcome, not by name. | XGN-30 (`XGN-DEC-016`) |
| OD-09-02 | Whether the gateway may relay a shell transport session that does not run on the trusted device is not settled. | XGN-20 |
| OD-09-03 | The list of transport families that may be admitted in future is open. This module defines the admission criteria and the declaration vocabulary, not the list. | XGN-09 |

## 10. Related specifications

- **Depends on:** XGN-01, XGN-02, XGN-03.
- **Referenced by:** XGN-10, XGN-11, XGN-12, XGN-13, XGN-20, XGN-26, XGN-27, XGN-30.
- **Sibling:** XGN-10 owns the Composed GET transport and the channel tier enablement rule; XGN-11, XGN-12 and XGN-13 own their own framing; XGN-28 and XGN-29 own the command protocol and the capability contract; XGN-30 owns the probes.
- **Predecessor material:** the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026), which carried the channel table and the open decisions on channel order and on who selects; and the *Navigation Transport Specification* (0.1-draft, 25 November 2026), which framed a transport as an adapter in front of one shared request contract. Their claims and open decisions remain proposals.
- **Out of scope:** the framing of any individual transport, and every rule owned by the modules listed in section 1.
