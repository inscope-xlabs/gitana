# XGN-00: XGN Framework Architecture

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-00 | **Group:** A — Framework foundation
**Owner:** Gitana Architecture
**Depends on:** XGN-01 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** none outstanding at this level

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and authority

This module is the architectural authority beneath the XGN Framework Specification Index. It defines component boundaries, trust boundaries, responsibility allocation and the system-level invariants that every other XGN module MUST preserve.

**XGN is the authoritative source for agent ingress to Gitana.** Any document, guide, contract or implementation that describes how an agent reaches Gitana is subordinate to this specification set. A component that implements agent ingress MUST conform to XGN; an implementation that diverges from XGN is not a conforming implementation of Gitana agent ingress, regardless of the status of any other document in the repository.

Documents that describe agent ingress but are not part of this specification set are **out of scope for XGN**. They MUST NOT be cited as the authority for a request contract, an envelope, a lifecycle, a policy decision, an approval, an execution path or an audit record, and XGN modules MUST NOT depend on them for any of those definitions. Where such a document conflicts with XGN, XGN wins and the conflict MUST be reported, not silently resolved in favour of the other document.

## 2. Scope

In scope:

- The logical topology of the framework and the responsibilities of each component.
- The separation of navigation and request transport, request governance and orchestration, and approval and execution authority.
- The authority model: which component may decide what.
- The system-level invariants that apply in every state of every session.
- The architectural relationship between XGN modules.

Out of scope:

- Endpoint definitions, wire formats and schemas — XGN-02 and the transport modules.
- GitHub-specific operation semantics — XGN-19.
- Deployment topology, hosting and provider selection — XGN-20 and XGN-27.
- The content of any single transport — XGN-09 to XGN-14, XGN-28 to XGN-30.

## 3. Architectural objectives

XGN exists so that an AI agent or other permitted client can discover what Gitana can do, navigate permitted surfaces, compose a well-formed request, and hand that request to Gitana — **without the agent ever holding authority over protected operations, credentials, user approval or execution boundaries**.

The framework is organised around ten objectives. Each is a design target, and the module that owns it MUST be able to show how it is met.

| ID | Objective | Primary owning modules |
|---|---|---|
| AO-01 | Capability discovery: an agent can learn what Gitana can do, what its session permits and which actions are available now. | XGN-03, XGN-14 |
| AO-02 | Structured navigation: an agent moves through defined surfaces and states rather than guessing at undocumented UI. | XGN-03, XGN-14 |
| AO-03 | Request composition: an agent assembles a machine-readable request from supported operations and arguments. | XGN-07, XGN-28 |
| AO-04 | Transport independence: different channels deliver into one shared request and governance pipeline. | XGN-09, XGN-02 |
| AO-05 | Bounded authorization: every session runs under an explicit grant defining task, resources, operations and limits. | XGN-04, XGN-15 |
| AO-06 | Human control: protected actions remain subject to the designated approval authority. | XGN-16, XGN-05 |
| AO-07 | Trusted execution: execution runs through Gitana-controlled components, never through credentials or privileged access delegated to an agent. | XGN-17, XGN-05 |
| AO-08 | Integrity and traceability: request, approval, execution attempt and outcome can be bound together and audited. | XGN-31, XGN-25 |
| AO-09 | Reliable recovery: timeouts, duplicate delivery, interrupted sessions and uncertain outcomes have defined handling. | XGN-21, XGN-22, XGN-23 |
| AO-10 | Extensibility: new transports, operations and surfaces are introduced without altering the foundational authority model. | XGN-09, XGN-18, XGN-27 |

## 4. Architectural model

The framework separates three concerns. A change that blurs them is a defect, not an enhancement.

1. **Navigation and request transport** — how an agent discovers capabilities and carries a request to Gitana.
2. **Request governance and orchestration** — how Gitana interprets, validates, scopes, tracks and presents a request.
3. **Approval and execution authority** — how the trusted runtime obtains user authorization, executes permitted work, and records or reconciles the result.

```text
AI Agent / Permitted Client
          |
          v
Capability Discovery and Navigation Surface
          |
          v
Selected Transport Adapter
          |
          v
XGN Gateway / Ingress
          |
          v
Shared Request Contract
          |
          v
Request Validation and Policy Enforcement
          |
          v
Session State and Orchestration
          |
          v
Human Approval Boundary
          |
          v
Gitana Trusted Execution Runtime
          |
          v
External Service Adapter(s)
          |
          v
Result, Audit and Reconciliation
          |
          v
Agent-visible Status / Next Navigation Surface
```

This is a logical model, not a deployment diagram. Process boundaries, module ownership, relay responsibilities, interfaces and runtime placement are determined by the detailed modules.

### 4.1 The single-pipeline rule

Every supported transport MUST ultimately produce the same logical XGN request representation. Transport-specific data MUST be normalised into the shared contract before policy evaluation and execution.

**A new transport is not a new execution path.** It is another way to deliver a request into the common pipeline. A transport MUST NOT introduce its own grant semantics, its own policy model, its own approval concept, its own execution route or its own audit format.

### 4.2 The two-plane rule

XGN recognises exactly two control planes.

| Plane | Controller | May |
|---|---|---|
| **Agent plane** | The AI agent or permitted client | Discover capabilities, navigate permitted surfaces, read its own session and draft state, compose and submit a request, read status and results. |
| **Operator plane** | The human operator on the trusted device | Create sessions and grants, confirm pairing, clear turns and gates, approve or decline, widen or revoke scope, suspend, resume and revoke sessions, manage policy and credentials. |

- No agent-plane response may contain a link, token, parameter or instruction that performs an operator-plane action.
- Operator-plane authentication MUST be independent of every agent-plane credential.
- Conversation between an agent and an operator is **outside** the system. No chat message is ever authorisation. A message may inform an operator; it may never substitute for an operator-plane action on the trusted device.
- An agent MUST NOT be able to unlock a session, clear a gate, approve a request, widen scope or cause execution by any means.

## 5. Component responsibilities

| Component | Authoritative for | Explicitly not authoritative for |
|---|---|---|
| **Agent / client** | Its own requests and its own view of the session | Permissions, approval, execution, scope, credentials |
| **Transport adapter** | Carrying requests and responses; transport-specific framing | Granting permission, authorising execution, widening scope |
| **XGN Gateway / relay** | Ingress, routing, rate enforcement, transport protections, shape and size validation | Widening a grant, overriding device-side policy, deciding whether an operation is permitted |
| **Gitana trusted runtime (device)** | Session and grant state, draft state, policy decisions, approval capture, execution authority, audit | Being reachable by an agent without a grant |
| **Operator** | Grant scope, pairing confirmation, gate clearance, approval decisions, revocation | — |
| **External service adapter** | Provider-specific mapping of an approved operation | Receiving anything that was not approved and dispatched by the runtime |

Two rules follow, and they are the most commonly violated in practice:

- The gateway is a **dumb pipe with protections**. It authenticates, routes, rate-limits, correlates and bounds size. It MUST NOT interpret operation arguments, MUST NOT decide that an operation is permitted, and MUST NOT widen the grant.
- The device is the **only** component that may decide whether a protected operation proceeds. A successful URL fetch, tool call, relay delivery or shell request is **not** proof that an operation was authorised or executed.

### 5.1 State ownership

The device owns all authoritative state: sessions, grants, drafts, pending envelopes, policy decisions, approvals, execution records, reconciliation records and audit events.

The gateway MAY hold only bounded, non-authoritative state: rate counters, a short-lived link token registry, and a single-reply idempotency cache whose contents MUST NOT include agent-authored content.

If the gateway and the device disagree about whether a request was accepted, the **device's record is authoritative**. The gateway MUST NOT assert that an operation was accepted, and MUST NOT assert that an operation was executed, unless the device has said so.

This split is what makes the transport adapters replaceable: the parts of a session that matter are all on the device.

## 6. System-level invariants

These hold in every state, in every transport, for every agent. Each MUST be testable; the test references are given in XGN-26.

| ID | Invariant |
|---|---|
| INV-01 | No agent action unlocks a session, clears a gate, approves a request, widens scope or causes execution. |
| INV-02 | A conversation between agent and operator never authorises anything. |
| INV-03 | A request outside the granted repository, branch or directory is rejected **without contacting the external service**. |
| INV-04 | The agent supplies only the operation and its arguments. Identity, timing, scope and version are stamped for it. |
| INV-05 | Nothing changes on an external service until a request is committed, approved, and dispatched by the trusted runtime. |
| INV-06 | The approved request is byte-for-byte the dispatched request; any material change invalidates the prior approval. |
| INV-07 | A session holds at most one open draft and at most one unresolved request. |
| INV-08 | A command capability has exactly one effect. An exact retry inside the idempotency window returns the original reply and produces no second effect. |
| INV-09 | Any other reuse of a command capability is recorded and suspends the session. |
| INV-10 | A session sequence number only increases. A duplicate has no new effect; a gap is rejected. |
| INV-11 | A rejected command leaves the draft, the pending envelope and the next sequence number unchanged, and issues a fresh capability. |
| INV-12 | A panel offers only commands that are legal in the current state. |
| INV-13 | Agent-written or repository-derived content appears in a reply only inside an explicit read, clearly marked as data, and otherwise only as sizes and digests. |
| INV-14 | No long-lived credential ever appears in a URL, a link, a reply, a log or an error message. |
| INV-15 | Unknown, malformed, stale, out-of-scope or integrity-invalid input fails closed. |
| INV-16 | An uncertain outcome is reconciled against authoritative state before any further side effect is attempted. Blind re-execution is forbidden. |
| INV-17 | The grant is explicit, bounded, and cannot be widened by the party it bounds. |
| INV-18 | Every grant, request, approval, execution attempt, outcome and recovery action is traceable to a stable identifier, and no audit record contains a secret or agent content. |
| INV-19 | Cross-component communication uses the shared contracts. No undocumented direct dependency between components is permitted. |
| INV-20 | A transport or execution fallback preserves the same scope, policy, approval and audit guarantees as the primary path. |

## 7. Design principles

| ID | Principle | Requirement |
|---|---|---|
| DP-01 | **Agent proposes, device decides** | The agent states an intent. The device decides whether and how it happens. |
| DP-02 | **One contract, many transports** | Transport adapters differ only in framing and delivery. |
| DP-03 | **Fail closed** | Anything ambiguous, stale or unverifiable is rejected, not guessed. |
| DP-04 | **Detection by design** | Replay, theft and misuse are made observable, because prevention alone is not achievable over a bearer-capability channel. |
| DP-05 | **Least privilege, minimum data** | A component receives only the data and authority its role requires. |
| DP-06 | **Bounded and explicit** | Scope, limits, lifetimes and budgets are declared, not inferred. |
| DP-07 | **Bind what is approved to what is dispatched** | Integrity is cryptographic, not clerical. |
| DP-08 | **Recover, do not repeat** | An uncertain result is resolved by reading authoritative state. |
| DP-09 | **Self-describing** | An agent can learn the legal commands, fields, limits and transitions from the session itself. |
| DP-10 | **The transport does not define authority** | Adding a transport never widens what an agent can do. |

## 8. Module boundaries and ownership rules

The module catalogue exists to prevent specification overlap and contradictory sources of truth.

1. **One authoritative definition per concern.** A rule is defined in one owning module. Other modules reference it; they do not restate it as their own.
2. **Shared contracts govern interfaces.** Modules define their internal behaviour, but cross-component communication uses the shared contracts in XGN-02.
3. **Transport modules do not define authority.** XGN-10 to XGN-14 and XGN-28 to XGN-30 describe how a request is carried. They MUST NOT redefine grants, policy, approval or execution authority.
4. **Policy and approval are distinct.** Policy decides whether an action is permitted. Approval records the operator's decision for actions requiring human authorisation.
5. **Execution is not transport.** A successful fetch is not evidence of authorisation or execution.
6. **Provider logic stays behind adapters.** GitHub-specific behaviour lives in XGN-19 and the adapter contracts, not in generic navigation or lifecycle rules.
7. **State transitions are centralised.** XGN-21 owns the authoritative lifecycle and transition model.
8. **Recovery is explicit.** XGN-22 owns outcome reconciliation. A transport retry is never permission to repeat a protected side effect.
9. **Security and privacy are cross-cutting.** Every module references the applicable controls in XGN-24 and XGN-25.
10. **Testability is part of the specification.** Every module states its externally observable requirements and points to the conformance tests in XGN-26.

## 9. Dependency map

| Module group | Primary dependencies | Enables |
|---|---|---|
| Foundation (XGN-00 to XGN-03) | None; establishes the common model | All other modules |
| Session and request lifecycle (XGN-04 to XGN-08, XGN-31) | Foundation, shared contracts | Consistent sessions, requests, drafts, validation, integrity |
| Transport (XGN-09 to XGN-14, XGN-28 to XGN-30) | Shared contracts, session model, request envelope, discovery model | Multiple ways to navigate and submit requests |
| Policy and execution (XGN-15 to XGN-19) | Session and grant, request lifecycle, validation, identity and trust | Controlled approval and trusted execution |
| Relay and reliability (XGN-20 to XGN-23) | Contracts, transport abstraction, state model, security requirements | Secure delivery, state consistency, safe recovery |
| Assurance (XGN-24 to XGN-27) | All modules | Security, privacy, testing, operations, governance |

Critical dependency principles:

- XGN-02 is a foundational dependency for every component that communicates across a boundary.
- XGN-04, XGN-06 and XGN-15 jointly define the relationship between a session grant, a request, and permission to perform an operation.
- XGN-16 and XGN-17 define the separation between operator approval and trusted execution.
- XGN-09 defines transport selection; individual transport modules define their own mechanics.
- XGN-21 and XGN-22 MUST be settled before any transport-specific retry behaviour is finalised.
- XGN-24 and XGN-25 constrain every module, not only the security and privacy modules.
- XGN-31 sits between validation and approval: XGN-08 produces a canonical form, XGN-31 digests and binds it, XGN-16 approves exactly what XGN-31 bound.

## 10. Framework-wide normative rules

Detailed modules refine these rules and MUST NOT contradict them.

- **One framework, one request model.** All transports converge on the shared XGN request contract.
- **No transport-based privilege.** Using a different channel cannot grant additional permission.
- **Device-side authority.** The trusted Gitana runtime retains the authoritative policy and approval boundary.
- **Explicit bounded grants.** Sessions are constrained by an explicit grant and cannot widen their own scope.
- **No agent-held provider credentials.** An agent never receives Gitana's long-lived provider credentials as a means of executing protected operations.
- **Approval binds to the request.** The approved request is the dispatched request; any material change invalidates the approval.
- **Fail closed.** Malformed, stale, unauthorised, out-of-scope or integrity-invalid requests are rejected.
- **No blind retries.** An uncertain result is reconciled against authoritative state before any further side effect.
- **Least privilege and minimum data.** Components receive only the data and authority their role requires.
- **Auditable lifecycle.** Grant, request, approval, execution and recovery events are traceable without exposing secrets.
- **Contract-first integration.** Cross-component communication is defined through shared contracts.
- **No silent fallback.** A transport or execution fallback preserves the same scope, policy, approval and audit guarantees.

## 11. Architectural risks carried by this framework

| ID | Risk | Treatment |
|---|---|---|
| AR-01 | A bearer capability in a URL is exposed by chat history, provider logs, proxies and link scanners. | Short lifetime, single-use capabilities, scope binding, revocation, fork detection, no long-lived credential ever in a URL. XGN-29, XGN-24. |
| AR-02 | An automated prefetch or link scanner consumes a capability before the agent does. | Bare base links are inert; a capability is consumed only by a complete valid command; prefetch probes gate the channel. XGN-10, XGN-30. |
| AR-03 | A GET-shaped command is executed by something the agent did not intend. | A capability is consumed only by a complete valid command carrying the expected sequence number and a matching capability digest. XGN-28, XGN-29. |
| AR-04 | Approval covers a subset of what is dispatched. | The digest covers the complete immutable envelope; the runtime verifies the digest at dispatch. XGN-31. |
| AR-05 | The agent silently overwrites content that changed underneath it. | Version preconditions on file blob and branch head; conflict is a rejection, not a warning. XGN-19, XGN-31. |
| AR-06 | A lost reply causes a duplicate side effect. | Exact-retry idempotency inside a bounded window; beyond it, reconcile by reading authoritative state. XGN-22, XGN-29. |
| AR-07 | Adding a transport quietly creates a second authorisation model. | XGN-09 requires a transport to declare capabilities and converge on the shared contract; XGN-26 tests convergence. |
| AR-08 | A repository, a chat transcript or a file becomes a prompt-injection channel into operator decisions. | Operator-plane only actions, content marked as data, approval summary generated by the runtime, never derived from content. XGN-16, XGN-24. |
| AR-09 | The gateway becomes a single point of authority. | The gateway holds no authoritative state; the device record wins every disagreement. XGN-20. |

## 12. Decisions recorded

| ID | Decision | Owner |
|---|---|---|
| `XGN-DEC-059` | XGN is authoritative for agent ingress. A document describing agent ingress that is not part of this set is out of scope and MUST NOT be cited as the authority for a request contract, envelope, lifecycle, policy decision, approval, execution path or audit record. Where such a document conflicts with XGN, XGN wins and the conflict MUST be reported rather than silently resolved. | XGN-00 |

## 13. Related specifications

- **Depends on:** XGN-01 (terminology and identifiers used throughout).
- **Referenced by:** every module in the set.
- **Predecessor material:** the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026) and the *Navigation Transport Specification* (0.1-draft, 25 November 2026). These are **source material**, redistributed across XGN-03, XGN-05, XGN-07, XGN-08, XGN-10, XGN-14, XGN-16, XGN-20, XGN-24 and XGN-28 to XGN-30. Their claims and open decisions remain proposals until separately reviewed and verified.
- **Out of scope:** any other document describing agent ingress. See section 1.
