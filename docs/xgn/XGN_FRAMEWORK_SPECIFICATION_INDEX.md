# Extended Gitana Navigation (XGN) Framework

## Main Specification Overview and Module Index

**Document type:** Framework index / executive overview
**Canonical filename:** `XGN_FRAMEWORK_SPECIFICATION_INDEX.md`
**Location:** `docs/xgn/`
**Framework:** Extended Gitana Navigation (XGN)
**Status:** Architecture index for specification development; not, by itself, an approved implementation contract
**Version:** 0.2 | **Date:** 27 November 2026

---

## 1. Executive overview

The **Extended Gitana Navigation (XGN) Framework** is Gitana's native framework for allowing AI agents and other permitted clients to discover Gitana capabilities, navigate supported interaction surfaces, compose structured requests, and hand those requests to Gitana for policy-controlled review and execution.

XGN provides a common navigation and interaction architecture across supported agent communication channels. It makes Gitana's capabilities discoverable and usable by agents **without** giving those agents direct authority over protected operations, credentials, user approval, or execution boundaries.

The framework separates three concerns:

1. **Navigation and request transport** — how an agent discovers available actions and communicates a request to Gitana.
2. **Request governance and orchestration** — how Gitana interprets, validates, scopes, tracks, and presents a request.
3. **Approval and execution authority** — how Gitana's trusted runtime obtains user authorization, executes permitted work, and records or reconciles the result.

All supported transports converge on the same shared request contract and the same Gitana-controlled policy, approval, execution, and audit lifecycle. **A transport must not create a parallel authorization model or bypass the phone-side authority.**

XGN is a framework, not a single endpoint, URL format, chat integration, or GitHub workflow. Its modular specification set defines the shared architecture and the independently maintainable subsystems that implement it.

## 2. Authority and canonical terminology

### 2.1 Authority

**XGN is the authoritative source for agent ingress to Gitana.** Any document, guide, contract, or implementation describing how an agent reaches Gitana is subordinate to this specification set. A component implementing agent ingress MUST conform to XGN; an implementation that diverges is not a conforming implementation of Gitana agent ingress, regardless of the status of any other document in the repository.

Documents describing agent ingress that are not part of this set are **out of scope for XGN**. They MUST NOT be cited as the authority for a request contract, envelope, lifecycle, policy decision, approval, execution path, or audit record, and no XGN module may depend on one for those definitions. Where an out-of-scope document conflicts with XGN, XGN wins and the conflict MUST be reported rather than silently resolved.

### 2.2 Adopted names

| Term | Meaning |
|---|---|
| **XGN Framework** | The complete navigation architecture. |
| **XGN Specification Set** | This index and all subordinate XGN module specifications. |
| **XGN Surface Map** | The discoverable set of panels, states, actions, and commands available within an XGN session. |
| **Composed GET** | The URL-based command-composition transport within XGN. Not another name for the framework. |
| **XGN Session** | A bounded, authorized interaction context governed by a grant and session lifecycle. |
| **XGN Request** | A structured request submitted through an XGN-supported transport, represented by the shared request contract. |
| **XGN Gateway** | The logical entry point that receives and routes XGN navigation or request traffic. |

The earlier working labels **Navigation Transport**, **Composed GET**, and **Surface Map** referred to the same evolving specification and its concepts. They are not three frameworks, subsystems, or competing specifications. **XGN Framework** is the complete architecture; **XGN Specification Set** is this index and its subordinate modules; **XGN Surface Map** is the discoverable set of panels, states, actions, and commands within a session; **Composed GET** is the URL-based command-composition transport within it; **XGN Session** is a bounded, authorized interaction context governed by a grant; **XGN Request** is a structured request represented by the shared request contract; **XGN Gateway** is the logical entry point that receives and routes XGN traffic. Retired terms and their XGN replacements are listed in XGN-01 §2.1.

## 3. Purpose of this index

This index is the main navigation file for human reviewers, developers, and AI coding agents. It lets a reader or agent:

- Understand what XGN is and what it is responsible for.
- Identify the specification module governing a particular concern.
- Follow dependencies without loading every detailed specification.
- Distinguish shared contracts from transport-specific behaviour.
- Locate the authoritative source for policy, state, approval, execution, and recovery rules.
- Understand which modules are foundational and which are optional or transport-specific.
- Determine whether a specification is proposed, approved, implemented, tested, or deprecated.
- Avoid duplicating requirements across modules or introducing conflicting definitions.

The index intentionally contains **architecture-level descriptions and pointers** — not endpoint definitions, schemas, algorithms, parameter lists, code, or implementation instructions. Those belong in the subordinate specifications.

## 4. Architectural model

XGN separates three concerns and follows this logical flow:

```text
AI Agent / Permitted Client
  -> Capability Discovery and Navigation Surface      [XGN-03, XGN-14]
  -> Selected Transport Adapter                     [XGN-09 to XGN-14, XGN-28 to XGN-30]
  -> XGN Gateway / Ingress                          [XGN-20]
  -> Shared Request Contract                        [XGN-02, XGN-06]
  -> Request Validation and Policy Enforcement      [XGN-08, XGN-15, XGN-31]
  -> Session State and Orchestration               [XGN-04, XGN-07, XGN-21]
  -> Human Approval Boundary                        [XGN-05, XGN-16]
  -> Gitana Trusted Execution Runtime               [XGN-17, XGN-18]
  -> Result, Audit and Reconciliation               [XGN-22, XGN-25]
  -> Agent-visible Status / Next Navigation Surface [XGN-03, XGN-14]
```

This is a logical model, not a deployment diagram. The detailed specifications determine process boundaries, module ownership, gateway responsibilities, interfaces, and runtime placement.

### 4.1 Architectural authority

| Party | Authority | Never |
|---|---|---|
| Agent / client | Discovers capabilities, navigates permitted surfaces, submits requests | Is a trusted authority |
| Transport adapter | Carries requests and responses | Grants permission or authorizes execution |
| Gateway / relay | Ingress, routing, rate enforcement, transport protections | Widens a grant, overrides device-side policy, interprets operation arguments |
| Gitana trusted runtime | Authoritative policy and approval boundary; controls protected execution | Is reachable by an agent without a grant |
| Operator | Grants scope, makes approval decisions, revokes | Acts through the agent's links |
| External service | Receives only authorized operations through Gitana's execution integration | Is contacted for an out-of-scope request |

No module may silently assume authority assigned to another. Where the gateway and the runtime disagree about whether a request was accepted, **the runtime's record is authoritative**.

### 4.2 The shared request pipeline

Every supported transport must ultimately produce the same logical XGN request representation. Transport-specific data must be normalized into the shared contract before policy evaluation and execution. **A new transport is not a new execution path** — it is another way to deliver a request into the common pipeline.

## 5. Specification module catalogue

The catalogue defines the modular specification architecture: **32 subordinate modules**. Module numbers are stable catalogue identifiers, not implementation phase numbers or a required build sequence. Filenames are canonical.

### Group A — Framework foundation

| ID | File | Responsibility |
|---|---|---|
| XGN-00 | `XGN_FRAMEWORK_ARCHITECTURE.md` | Component boundaries, logical topology, design principles, responsibility allocation, system-level invariants. The architectural authority beneath this index. |
| XGN-01 | `XGN_TERMINOLOGY_AND_CONVENTIONS.md` | Canonical vocabulary, naming rules, identifiers, notation, normative language, decision register, legacy-name mapping. |
| XGN-02 | `XGN_SHARED_CONTRACTS.md` | Common interfaces and contract boundaries shared by transports, gateway, runtime, policy, approval, and execution. Contract ownership and compatibility expectations. |
| XGN-03 | `XGN_CAPABILITY_DISCOVERY_AND_SURFACE_MAP.md` | How agents discover capabilities, commands, panels, session state, permitted actions, and next navigation options. Defines the logical XGN Surface Map. |

### Group B — Session, identity, and request lifecycle

| ID | File | Responsibility |
|---|---|---|
| XGN-04 | `XGN_SESSION_AND_GRANT_MODEL.md` | Session creation, grant definition, permitted operations, resource boundaries, session binding, expiry, suspension, resumption, revocation. |
| XGN-05 | `XGN_IDENTITY_AND_TRUST_MODEL.md` | Principals, operator identity, agent trust assumptions, session credentials, pairing challenge semantics, credential handling, trust boundaries. |
| XGN-06 | `XGN_REQUEST_ENVELOPE_AND_LIFECYCLE.md` | Canonical request envelope, identifiers, timestamps, principal stamping, lifecycle states, and the relation between draft, submitted request, approval, execution, and result. |
| XGN-07 | `XGN_REQUEST_COMPOSITION_AND_DRAFTS.md` | Draft creation and editing, operation fields, staged content, parts, preview, correction, commit-for-approval, cancellation, draft lifecycle. |
| XGN-08 | `XGN_VALIDATION_AND_CANONICALIZATION.md` | Input validation, normalization, encoding and decoding rules, canonical paths and values, malformed input handling, deterministic representation. |
| XGN-31 | `XGN_REQUEST_INTEGRITY_AND_BINDING.md` | Canonical digest input, digest algorithm and length, approval binding, signing, and time-of-check/time-of-use preconditions. |

### Group C — Navigation and transport channels

| ID | File | Responsibility |
|---|---|---|
| XGN-09 | `XGN_TRANSPORT_ABSTRACTION_AND_SELECTION.md` | Transport-neutral model, capability declarations, channel selection, per-session transport binding, fallback rules, convergence on the shared request contract. |
| XGN-10 | `XGN_COMPOSED_GET_TRANSPORT.md` | The URL-based Composed GET mechanism: single path, anti-prefetch protections, channel tiers, transport limits, link lifecycle, response panels. |
| XGN-11 | `XGN_CONNECTOR_AND_TOOL_TRANSPORT.md` | Delivery through supported agent connectors, tool calls, or structured integrations, mapping tool inputs and outputs to the shared contract. |
| XGN-12 | `XGN_SHELL_AND_AUTHENTICATED_TRANSPORT.md` | Authenticated shell, command-line, or Gitana-mediated request delivery without bypassing Gitana's authority model. |
| XGN-13 | `XGN_COPY_PASTE_HANDOFF_TRANSPORT.md` | Human-mediated request handoff for chat-only environments: packaging, validation, confirmation, response correlation. |
| XGN-14 | `XGN_NAVIGATION_PANELS_AND_COMMANDS.md` | Agent-facing panel structure, state presentation, command vocabulary, command availability, correction guidance, and rules for presenting untrusted content. |
| XGN-28 | `XGN_COMPOSED_GET_COMMAND_PROTOCOL.md` | The command catalogue, parameter order, session sequence semantics, field and part model, batching, and the correction template. |
| XGN-29 | `XGN_COMPOSED_GET_LINK_AND_REPLAY_SECURITY.md` | Capability format and binding, rotation and lifetime, exact-retry idempotency, theft and fork detection, revocation. |
| XGN-30 | `XGN_CHANNEL_QUALIFICATION_PROBES.md` | The probe suite for qualifying a client channel, the deterministic channel decision matrix, and the mapping from probes to claims. |

### Group D — Policy, approval, and execution

| ID | File | Responsibility |
|---|---|---|
| XGN-15 | `XGN_POLICY_AND_SCOPE_ENFORCEMENT.md` | Authorization decisions, operation allowlists, repository and resource scope, path restrictions, grant enforcement, policy revalidation, fail-closed behaviour. |
| XGN-16 | `XGN_APPROVAL_AND_OPERATOR_CONTROL.md` | Human approval UX and workflow, approval evidence, approve/decline semantics, operator-only actions, approval expiry, binding approval to the exact request. |
| XGN-17 | `XGN_EXECUTION_AUTHORITY_AND_RUNTIME.md` | Trusted execution boundary, execution ownership, dispatch rules, credential custody, runtime responsibilities, prohibition on agent-controlled privileged execution. |
| XGN-18 | `XGN_EXTERNAL_SERVICE_ADAPTERS.md` | Adapters to external services: how approved operations map to provider APIs while preserving shared contracts, least privilege, and provider isolation. |
| XGN-19 | `XGN_GITHUB_OPERATION_PROFILE.md` | GitHub-specific operation profile: supported operations, arguments, version preconditions, scope rules, and GitHub result mapping. A provider profile, not the definition of XGN. |

### Group E — Relay, state, and reliability

| ID | File | Responsibility |
|---|---|---|
| XGN-20 | `XGN_GATEWAY_AND_RELAY.md` | Gateway responsibilities, ingress, routing, device communication, request forwarding, rate enforcement, availability behaviour, limits on gateway authority and data retention. |
| XGN-21 | `XGN_STATE_MACHINE_AND_TRANSITIONS.md` | Authoritative session, draft, request, approval, execution, and recovery state machines; legal transitions; invalid transitions; restart and timeout behaviour. |
| XGN-22 | `XGN_RESULT_RECONCILIATION_AND_RECOVERY.md` | Result delivery, idempotency, duplicate handling, unknown outcomes, provider-side reconciliation, interrupted workflows, safe recovery without blind re-execution. |
| XGN-23 | `XGN_ERRORS_RETRIES_AND_RATE_LIMITS.md` | Error taxonomy, stable error codes, retryability, retry windows, backoff, polling limits, offline handling, agent and operator recovery instructions. |

### Group F — Security, privacy, and assurance

| ID | File | Responsibility |
|---|---|---|
| XGN-24 | `XGN_SECURITY_AND_THREAT_MODEL.md` | Threat model, trust boundaries, attack surfaces, replay and link theft risks, prefetch behaviour, credential exposure, injection, abuse cases, required mitigations. |
| XGN-25 | `XGN_PRIVACY_AUDIT_AND_DATA_RETENTION.md` | Data classification, content handling, secrets, transient and durable storage, audit events, log redaction, retention, deletion, privacy responsibilities. |
| XGN-26 | `XGN_COMPATIBILITY_TESTING_AND_CONFORMANCE.md` | Transport probes, protocol conformance, test vectors, security and integration tests, acceptance criteria, supported-client declarations, release qualification. |
| XGN-27 | `XGN_OPERATIONS_OBSERVABILITY_AND_GOVERNANCE.md` | Operational diagnostics, health and telemetry boundaries, deployment and configuration governance, version support, change control, deprecation, extension approval. |

## 6. Module boundaries and ownership rules

The catalogue exists to prevent specification overlap and contradictory sources of truth.

1. **One authoritative definition per concern.** A rule is defined in one owning module. Other modules reference it but do not redefine it.
2. **Shared contracts govern interfaces.** Cross-component communication uses the shared contract definitions in XGN-02.
3. **Transport modules do not define authority.** XGN-09 to XGN-14 and XGN-28 to XGN-30 describe how requests are carried. They cannot redefine grants, policy, approval, or execution authority.
4. **Policy and approval are distinct.** Policy determines whether an action is permitted; approval records the operator's decision for actions requiring human authorization.
5. **Execution is not transport.** A successful URL fetch, tool call, relay delivery, or shell request is not proof that an operation was authorized or executed.
6. **Provider logic stays behind adapters.** GitHub-specific behaviour belongs in XGN-19 and XGN-18, not in generic navigation or request lifecycle rules.
7. **State transitions are centralized.** XGN-21 owns the authoritative lifecycle and transition model.
8. **Recovery is explicit.** XGN-22 owns outcome reconciliation. A transport retry is never permission to repeat a protected side effect.
9. **Security and privacy requirements are cross-cutting.** Every module references the applicable controls in XGN-24 and XGN-25.
10. **Testability is part of the specification.** Every module states its externally observable requirements and points to the conformance tests in XGN-26.

## 7. Dependency map

| Module group | Primary dependencies | Enables |
|---|---|---|
| Foundation (XGN-00 to XGN-03) | None; establishes the common model | All other modules |
| Session and request lifecycle (XGN-04 to XGN-08, XGN-31) | Foundation, shared contracts | Consistent sessions, requests, drafts, validation, integrity |
| Transport (XGN-09 to XGN-14, XGN-28 to XGN-30) | Shared contracts, session model, request envelope, discovery model | Multiple ways to navigate and submit requests |
| Policy and execution (XGN-15 to XGN-19) | Session and grant, request lifecycle, validation, identity and trust | Controlled approval and trusted execution |
| Relay and reliability (XGN-20 to XGN-23) | Contracts, transport abstraction, state model, security requirements | Secure delivery, state consistency, safe recovery |
| Assurance (XGN-24 to XGN-27) | All modules | Security, privacy, testing, operations, governance |

**Critical dependency principles.**

- XGN-02 is a foundational dependency for every component that communicates across a boundary.
- XGN-04, XGN-06, and XGN-15 jointly define the relationship between a session grant, a request, and permission to perform an operation.
- XGN-16 and XGN-17 define the separation between operator approval and trusted execution.
- XGN-09 defines transport selection; individual transport modules define their own mechanics.
- XGN-21 and XGN-22 are essential to reliable lifecycle handling and must be designed before transport-specific retry behaviour is finalized.
- XGN-31 sits between validation and approval: XGN-08 produces a canonical form, XGN-31 digests and binds it, XGN-16 approves exactly what XGN-31 bound.
- XGN-24 and XGN-25 constrain the design of every module, not just the security and privacy modules themselves.

## 8. Recommended reading paths

Agents should load only the modules needed for the task, together with their dependencies.

| Task | Read first | Then consult |
|---|---|---|
| Understand XGN as a whole | This index, XGN-00 | XGN-01, XGN-02, XGN-03 |
| Add or change a transport | XGN-00, XGN-02, XGN-09 | The relevant transport module, XGN-06, XGN-15, XGN-21, XGN-26 |
| Change Composed GET | XGN-10 | XGN-02, XGN-03, XGN-04, XGN-06, XGN-07, XGN-08, XGN-21 to XGN-26, XGN-28 to XGN-30 |
| Add an agent-facing command | XGN-03, XGN-14, XGN-28 | XGN-02, XGN-06, XGN-07, XGN-08, XGN-15, XGN-21, XGN-26 |
| Add or modify an operation | XGN-06, XGN-07, XGN-15, XGN-19 | XGN-16 to XGN-18, XGN-21, XGN-22, XGN-24, XGN-26 |
| Change session permissions | XGN-04, XGN-05, XGN-15 | XGN-16, XGN-21, XGN-24, XGN-25, XGN-26 |
| Change approval behaviour | XGN-16 | XGN-02, XGN-04, XGN-06, XGN-15, XGN-17, XGN-21, XGN-22, XGN-31 |
| Change the GitHub integration | XGN-18, XGN-19 | XGN-02, XGN-06, XGN-15 to XGN-17, XGN-22, XGN-24, XGN-26 |
| Change gateway behaviour | XGN-20 | XGN-02, XGN-05, XGN-09, XGN-21 to XGN-25, XGN-29 |
| Investigate duplicate or uncertain execution | XGN-21, XGN-22, XGN-23 | XGN-02, XGN-06, the relevant transport and provider adapter modules |
| Perform a security review | XGN-24, XGN-25 | XGN-02, XGN-04 to XGN-08, XGN-15 to XGN-23, XGN-29 to XGN-31 |
| Qualify a release | XGN-26, XGN-27 | All affected modules and their dependencies, XGN-30 |

## 9. Framework-wide normative rules

The detailed modules refine these framework-level rules without contradicting them.

- **One framework, one request model:** all supported transports converge on the shared XGN request contract.
- **No transport-based privilege:** using a different channel cannot grant additional permissions.
- **Runtime-side authority:** the designated Gitana trusted runtime retains the authoritative policy and approval boundary.
- **Explicit bounded grants:** sessions are constrained by an explicit grant and cannot widen their own scope.
- **No agent-held provider credentials:** agents do not receive Gitana's long-lived provider credentials as a means of executing protected operations.
- **Approval binds to the request:** the approved request must be the request that is dispatched; any material change invalidates the prior approval.
- **Fail closed:** malformed, stale, unauthorized, out-of-scope, or integrity-invalid requests are rejected.
- **No blind retries:** an uncertain result is reconciled against authoritative state before any further side effect is attempted.
- **Least privilege and minimum data:** components receive only the data and authority required for their role.
- **Auditable lifecycle:** grant, request, approval, execution, and recovery events are traceable without exposing secrets.
- **Contract-first integration:** cross-component communication is defined through shared contracts, not undocumented direct dependencies.
- **No silent fallback:** a transport or execution fallback must preserve the same scope, policy, approval, and audit guarantees.

## 10. Inherited decisions

The proposal that seeded this set left sixteen decisions open — eight from the proposal (`S1`–`S8`) and eight from the navigation transport specification (`N1`–`N8`). **Fifteen are now settled.** They are recorded in the decision register in **XGN-01 §7**, where each entry names the owner module holding the authoritative statement.

| ID | Decision | Resolution | Owner |
|---|---|---|---|
| S1 | Supported chat applications | **Still open.** A product decision, not settled by this set. Qualification is by probe, not by name. | XGN-30 |

Forty-four further decisions were taken while building the set — covering capability format, pairing, integrity coverage, batching, retry semantics, partial-workflow recovery, transport fallback, contract versioning and the authority of this specification set. All are listed in XGN-01 §7, and the modules that own them state them in full.

## 11. Specification status and maturity

The module catalogue is a decomposition of the XGN specification set. **It is not evidence that every module has been written, approved, implemented, or tested.** All subordinate modules currently carry status `Proposed`.

Each module carries a visible status block using this lifecycle: **Proposed** (drafted for review, not authoritative for implementation) → **Under review** → **Approved** → **Implemented** → **Verified** → **Deprecated**.

Each module also records its version, owner, last review date, dependencies, superseded documents, open decisions, and implementation or test references.

The proposal that seeded this set described itself as proposed and untested. Its claims C1–C6 remain untested; renaming and indexing the material under XGN does not approve those claims or make them implementation-ready. The status of each claim is tracked in XGN-01 §8.

## 12. Rules for AI agents and specification consumers

This index is intended to be the first document loaded when an AI agent is asked to work on XGN.

1. Read this index first to identify the relevant module IDs and dependencies.
2. Retrieve the smallest sufficient set of subordinate specifications for the requested task.
3. Treat each module's stated owning responsibility as authoritative for that concern.
4. Follow references to shared contracts rather than inventing or duplicating interfaces.
5. Do not infer that an item is implemented merely because it appears in this index.
6. Do not treat proposed, unapproved, or untested material as a binding implementation requirement.
7. Identify conflicts between modules and report them rather than silently choosing one.
8. Preserve existing module IDs and canonical terminology when proposing changes.
9. Update this index whenever modules are added, removed, renamed, split, merged, or materially re-scoped.
10. When producing implementation changes, identify all affected modules and any corresponding tests or repository documentation that must also change.

**Suggested agent retrieval procedure.** Read this index → classify the requested work by module ID → load the target module and its declared dependencies → check the status and version of each loaded specification → identify applicable shared contracts and framework invariants → implement or review only within the authorized scope → run or specify the relevant conformance tests → report affected modules, unresolved decisions, and required index updates.

## 13. Repository organization

The XGN specification set lives in one dedicated documentation directory so agents can discover it as a coherent collection without loading unrelated Gitana documentation.

```text
docs/xgn/
  XGN_FRAMEWORK_SPECIFICATION_INDEX.md   this index
  XGN_FRAMEWORK_ARCHITECTURE.md          XGN-00
  XGN_TERMINOLOGY_AND_CONVENTIONS.md     XGN-01
  XGN_SHARED_CONTRACTS.md                XGN-02
  XGN_CAPABILITY_DISCOVERY_AND_SURFACE_MAP.md  XGN-03
  ... 29 further modules, one file per module ID, named in section 5
```

Every module MUST stay under 32768 bytes, and this index MUST stay under 350 lines. Modules are split further rather than allowed to grow past that limit. The index should remain concise relative to the detailed modules: links, responsibility summaries, dependencies, and status — not copied technical content.

## 14. Change management and consistency

Changes to XGN are managed as changes to a coordinated specification set.

- A new capability is assigned to an existing module or a newly registered module.
- A new transport extends the transport abstraction and shared contract rather than creating a separate policy or execution path.
- A change to a shared contract requires an impact review across every dependent module.
- A change to session, request, approval, or execution states requires a coordinated update to the state machine, affected workflows, error handling, and tests.
- A change to security assumptions requires review of affected transport, relay, runtime, privacy, and conformance modules.
- Removed or superseded requirements are marked explicitly; architectural history is never silently deleted.
- The index and module catalogue are updated in the same change set as any module restructuring.
- Every module includes a short **Related specifications** section listing its direct dependencies and affected consumers, without reproducing entire sections from another module.

## 15. Source mapping and transition

The earlier **Gitana Surface Map (Composed GET): Proposal** (27 November 2026) and the **Navigation Transport Specification** (0.1-draft, 25 November 2026) are the initial source material for this workstream. Their concepts were redistributed into the modules above rather than retained as competing top-level specifications:

| Source area | Redistributed to |
|---|---|
| Framework-wide architecture and trust separation | XGN-00, XGN-02, XGN-05, XGN-17, XGN-20, XGN-24 |
| Surface map, panels, and commands | XGN-03, XGN-14 |
| Session, grant, and operator gates | XGN-04, XGN-05, XGN-16 |
| Request envelope, drafts, commands, and integrity | XGN-06, XGN-07, XGN-08, XGN-31 |
| Composed GET URL mechanics, rolling links, sequencing | XGN-10, XGN-28, XGN-29 |
| Transport selection and alternative channels | XGN-09, XGN-11 to XGN-13 |
| Gateway responsibilities | XGN-20 |
| State, retry, unknown outcomes, and reconciliation | XGN-21 to XGN-23 |
| Threats, privacy, probes, and acceptance tests | XGN-24 to XGN-26, XGN-30 |
| Deployment, support, and lifecycle governance | XGN-27 |

This mapping is a documentation transition guide. It does not resolve the proposal's open decisions on its own, validate its untested claims, or promote proposed behaviour to approved requirements.

## 16. Completion criteria for the XGN specification set

The set is structurally complete when:

1. Every module in this index has an authoritative document or an explicitly approved decision to defer or remove it.
2. Every module has a defined owner, scope, status, version, and dependency list.
3. Shared contracts are defined once and referenced consistently.
4. Transport modules converge on the same request, policy, approval, execution, and audit lifecycle.
5. Session, request, approval, execution, and recovery state machines are complete and mutually consistent.
6. Security, privacy, and failure-handling requirements are addressed across all modules.
7. Every normative requirement has an identifiable verification method or test reference.
8. Open decisions are recorded and cannot be mistaken for settled requirements.
9. The index accurately reflects the current module set and links to each subordinate specification.
10. An AI agent can use this index to identify the minimum relevant specification set for a defined task without ingesting the entire framework.

---

**End of main specification overview.**
