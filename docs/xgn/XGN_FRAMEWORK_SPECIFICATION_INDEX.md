# Extended Gitana Navigation (XGN) Framework

## Main Specification Overview and Module Index

**Document type:** Framework index / executive overview\
**Canonical filename:** `XGN_FRAMEWORK_SPECIFICATION_INDEX.md`\
**Proposed location:** `docs/xgn/`\
**Framework:** Extended Gitana Navigation (XGN)\
**Status:** Architecture index for specification development; not, by
itself, an approved implementation contract\
**Version:** 0.1\
**Date:** 27 November 2026

------------------------------------------------------------------------

## 1. Executive overview

The **Extended Gitana Navigation (XGN) Framework** is Gitana's native
framework for allowing AI agents and other permitted clients to discover
Gitana capabilities, navigate supported interaction surfaces, compose
structured requests, and hand those requests to Gitana for
policy-controlled review and execution.

XGN provides a common navigation and interaction architecture across
supported agent communication channels. It is intended to make Gitana's
capabilities discoverable and usable by agents without giving those
agents direct authority over protected operations, credentials, user
approval, or execution boundaries.

The framework separates three concerns:

1.  **Navigation and request transport** --- how an agent discovers
    available actions and communicates a request to Gitana.
2.  **Request governance and orchestration** --- how Gitana interprets,
    validates, scopes, tracks, and presents a request.
3.  **Approval and execution authority** --- how Gitana's trusted
    runtime obtains user authorization, executes permitted work, and
    records or reconciles the result.

All supported transports should converge on the same shared request
contract and the same Gitana-controlled policy, approval, execution, and
audit lifecycle. A transport must not create a parallel authorization
model or bypass the phone-side authority.

XGN is a framework, not a single endpoint, URL format, chat integration,
or GitHub workflow. Its modular specification set defines the shared
architecture and the independently maintainable subsystems that
implement it.

## 2. Name change and canonical terminology

The framework's adopted name is:

**Extended Gitana Navigation (XGN) Framework**

This name replaces the earlier working labels and descriptions:

-   **Navigation Transport specification**
-   **Composed GET**
-   **Surface Map**

These labels referred to the same evolving Gitana navigation
specification and its concepts. They must not be interpreted as three
separate frameworks, independent subsystems, or competing
specifications.

Going forward:

-   **XGN Framework** means the complete navigation architecture.
-   **XGN Specification Set** means this index and all subordinate XGN
    module specifications.
-   **XGN Surface Map** means the discoverable set of panels, states,
    actions, and commands available within an XGN session.
-   **Composed GET** means the URL-based command-composition transport
    mechanism within XGN. It is not another name for the entire
    framework.
-   **XGN Session** means a bounded, authorized interaction context
    governed by a grant and session lifecycle.
-   **XGN Request** means a structured request submitted through an
    XGN-supported transport and represented using the shared request
    contract.
-   **XGN Gateway** means the logical entry point that receives and
    routes XGN navigation or request traffic. Its physical deployment is
    defined by the relevant implementation specifications.

The canonical title for the overarching technical specification set is:

**Extended Gitana Navigation (XGN) Framework --- Architecture and
Technical Specification**

This index is the entry point to that specification set.

## 3. Purpose of this index

This document is designed to be the main navigation file for human
reviewers, developers, and AI coding agents.

It should allow a reader or agent to:

-   Understand what XGN is and what it is responsible for.
-   Identify the specification module governing a particular concern.
-   Follow dependencies without loading every detailed specification.
-   Distinguish shared contracts from transport-specific behaviour.
-   Locate the authoritative source for policy, state, approval,
    execution, and recovery rules.
-   Understand which modules are foundational and which are optional or
    transport-specific.
-   Determine whether a specification is proposed, approved,
    implemented, tested, or deprecated.
-   Avoid duplicating requirements across modules or introducing
    conflicting definitions.

The index intentionally contains **architecture-level descriptions and
pointers**, not detailed endpoint definitions, schemas, algorithms,
parameter lists, code, or implementation instructions. Those belong in
the subordinate specifications.

## 4. Framework objectives

XGN is intended to provide the following high-level capabilities:

1.  **Capability discovery:** agents can discover what Gitana can do,
    what a session permits, and which actions are currently available.
2.  **Structured navigation:** agents can move through defined
    interaction surfaces and states rather than relying on undocumented
    UI assumptions.
3.  **Request composition:** agents can assemble well-formed,
    machine-readable requests from supported operations and arguments.
4.  **Transport independence:** different communication channels can
    deliver requests into one shared Gitana request and governance
    pipeline.
5.  **Bounded authorization:** every session operates under an explicit
    grant defining the permitted task, resources, operations, and
    limits.
6.  **Human control:** protected actions remain subject to Gitana's
    designated approval authority.
7.  **Trusted execution:** execution occurs through Gitana-controlled
    components, not through credentials or privileged access delegated
    to an agent.
8.  **Integrity and traceability:** requests, approvals, execution
    attempts, and outcomes can be bound together and audited.
9.  **Reliable recovery:** timeouts, duplicate delivery, interrupted
    sessions, and uncertain outcomes have defined handling.
10. **Extensibility:** new transports, operations, and capability
    surfaces can be introduced without changing the framework's
    foundational authority model.

## 5. Architectural model

At the highest level, XGN follows this conceptual flow:

``` text
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

This is a logical model, not a deployment diagram. The detailed
specifications determine process boundaries, Android module ownership,
relay responsibilities, interfaces, and runtime placement.

### 5.1 Architectural authority

The framework must preserve the following separation of responsibility:

-   **Agent/client:** discovers capabilities, navigates permitted
    surfaces, and submits requests. It is not a trusted authority.
-   **Transport adapter:** carries requests and responses. It does not
    grant permissions or independently authorize execution.
-   **Relay/gateway:** provides ingress, routing, and transport-related
    protections. It cannot widen the user's grant or override phone-side
    policy.
-   **Gitana phone/runtime:** retains the authoritative policy and
    approval boundary and controls protected execution.
-   **User/operator:** grants the session's scope and makes approval
    decisions where required.
-   **External service:** receives only authorized operations through
    Gitana's execution integration.

The precise division between the gateway, relay, Android runtime, and
execution adapters must be defined in their respective modules and
shared contracts. No module may silently assume authority assigned to
another.

### 5.2 Shared request pipeline

Every supported transport must ultimately produce the same logical XGN
request representation. Transport-specific data must be normalized into
the shared contract before policy evaluation and execution.

A new transport is not a new execution path. It is another way to
deliver a request into the common pipeline.

## 6. Specification module catalogue

The following catalogue defines the recommended modular specification
architecture. It contains **28 subordinate modules**, exceeding the
requested minimum of 16 to keep responsibilities focused and
specifications independently queryable.

The module numbers are stable catalogue identifiers. They are not
implementation phase numbers or a required build sequence. Filenames are
proposed canonical names and may be adjusted to repository conventions
without changing module identity.

### Group A --- Framework foundation

  -----------------------------------------------------------------------------------------------
  ID                      Proposed specification file                     Responsibility and
                                                                          scope
  ----------------------- ----------------------------------------------- -----------------------
  XGN-00                  `XGN_FRAMEWORK_ARCHITECTURE.md`                 Detailed framework
                                                                          architecture: component
                                                                          boundaries, logical
                                                                          topology, design
                                                                          principles,
                                                                          responsibility
                                                                          allocation, and
                                                                          system-level
                                                                          invariants. This is the
                                                                          architectural authority
                                                                          beneath this index.

  XGN-01                  `XGN_TERMINOLOGY_AND_CONVENTIONS.md`            Canonical vocabulary,
                                                                          naming rules,
                                                                          identifiers, notation,
                                                                          version conventions,
                                                                          normative language, and
                                                                          terminology mapping
                                                                          from legacy names.

  XGN-02                  `XGN_SHARED_CONTRACTS.md`                       Common interfaces and
                                                                          contract boundaries
                                                                          shared by transports,
                                                                          gateway/relay, Android
                                                                          runtime, policy,
                                                                          approval, and execution
                                                                          components. Defines
                                                                          contract ownership and
                                                                          compatibility
                                                                          expectations.

  XGN-03                  `XGN_CAPABILITY_DISCOVERY_AND_SURFACE_MAP.md`   How agents discover
                                                                          capabilities, available
                                                                          commands, panels,
                                                                          session state,
                                                                          permitted actions, and
                                                                          next navigation
                                                                          options. Defines the
                                                                          logical XGN Surface
                                                                          Map.
  -----------------------------------------------------------------------------------------------

### Group B --- Session, identity, and request lifecycle

  ------------------------------------------------------------------------------------------
  ID                      Proposed specification file                Responsibility and
                                                                     scope
  ----------------------- ------------------------------------------ -----------------------
  XGN-04                  `XGN_SESSION_AND_GRANT_MODEL.md`           Session creation, grant
                                                                     definition, permitted
                                                                     operations, resource
                                                                     boundaries, session
                                                                     binding, expiry,
                                                                     suspension, resumption,
                                                                     and revocation.

  XGN-05                  `XGN_IDENTITY_AND_TRUST_MODEL.md`          Principals, operator
                                                                     identity, agent/client
                                                                     trust assumptions,
                                                                     session credentials,
                                                                     PIN or confirmation
                                                                     semantics, credential
                                                                     handling, and trust
                                                                     boundaries.

  XGN-06                  `XGN_REQUEST_ENVELOPE_AND_LIFECYCLE.md`    Canonical request
                                                                     envelope, request
                                                                     identifiers,
                                                                     timestamps, principal
                                                                     stamping, lifecycle
                                                                     states, and
                                                                     relationship between
                                                                     draft, submitted
                                                                     request, approval,
                                                                     execution, and result.

  XGN-07                  `XGN_REQUEST_COMPOSITION_AND_DRAFTS.md`    Draft creation and
                                                                     editing, operation
                                                                     fields, staged content,
                                                                     preview, correction,
                                                                     commit-for-approval,
                                                                     cancellation, and draft
                                                                     lifecycle.

  XGN-08                  `XGN_VALIDATION_AND_CANONICALIZATION.md`   Input validation,
                                                                     normalization, encoding
                                                                     and decoding rules,
                                                                     canonical paths and
                                                                     values, request
                                                                     integrity, hash
                                                                     binding, malformed
                                                                     input handling, and
                                                                     deterministic
                                                                     representation.
  ------------------------------------------------------------------------------------------

### Group C --- Navigation and transport channels

  ----------------------------------------------------------------------------------------------
  ID                      Proposed specification file                    Responsibility and
                                                                         scope
  ----------------------- ---------------------------------------------- -----------------------
  XGN-09                  `XGN_TRANSPORT_ABSTRACTION_AND_SELECTION.md`   Transport-neutral
                                                                         model, transport
                                                                         capability
                                                                         declarations, channel
                                                                         selection, per-session
                                                                         transport binding,
                                                                         fallback rules, and
                                                                         convergence on the
                                                                         shared request
                                                                         contract.

  XGN-10                  `XGN_COMPOSED_GET_TRANSPORT.md`                URL-based Composed GET
                                                                         mechanism: rolling
                                                                         links, command
                                                                         parameters, sequencing,
                                                                         payload limits, link
                                                                         lifecycle, response
                                                                         panels, and
                                                                         transport-specific
                                                                         constraints. Successor
                                                                         module to the earlier
                                                                         Navigation Transport /
                                                                         Composed GET / Surface
                                                                         Map proposal material.

  XGN-11                  `XGN_CONNECTOR_AND_TOOL_TRANSPORT.md`          Delivery through
                                                                         supported agent
                                                                         connectors, tool calls,
                                                                         or structured
                                                                         integrations, including
                                                                         mapping tool inputs and
                                                                         outputs to the shared
                                                                         XGN contract.

  XGN-12                  `XGN_SHELL_AND_AUTHENTICATED_TRANSPORT.md`     Authenticated shell,
                                                                         command-line, or
                                                                         Gitana-mediated request
                                                                         delivery. Defines how
                                                                         shell-capable agents
                                                                         communicate without
                                                                         bypassing Gitana's
                                                                         authority model.

  XGN-13                  `XGN_COPY_PASTE_HANDOFF_TRANSPORT.md`          Human-mediated request
                                                                         handoff for chat-only
                                                                         environments, including
                                                                         request packaging,
                                                                         validation,
                                                                         confirmation, and
                                                                         response correlation.

  XGN-14                  `XGN_NAVIGATION_PANELS_AND_COMMANDS.md`        Agent-facing panel
                                                                         structure, state
                                                                         presentation, command
                                                                         vocabulary, command
                                                                         availability,
                                                                         correction guidance,
                                                                         and rules for
                                                                         presenting untrusted
                                                                         content. Defines common
                                                                         surface behaviour;
                                                                         transport modules
                                                                         define delivery
                                                                         mechanics.
  ----------------------------------------------------------------------------------------------

### Group D --- Policy, approval, and execution

  ------------------------------------------------------------------------------------------
  ID                      Proposed specification file                Responsibility and
                                                                     scope
  ----------------------- ------------------------------------------ -----------------------
  XGN-15                  `XGN_POLICY_AND_SCOPE_ENFORCEMENT.md`      Authorization
                                                                     decisions, operation
                                                                     allowlists,
                                                                     repository/resource
                                                                     scope, path
                                                                     restrictions, grant
                                                                     enforcement, policy
                                                                     revalidation, and
                                                                     fail-closed behaviour.

  XGN-16                  `XGN_APPROVAL_AND_OPERATOR_CONTROL.md`     Human approval UX and
                                                                     workflow, approval
                                                                     evidence,
                                                                     approve/decline
                                                                     semantics,
                                                                     operator-only actions,
                                                                     approval expiry, and
                                                                     binding approval to the
                                                                     exact request.

  XGN-17                  `XGN_EXECUTION_AUTHORITY_AND_RUNTIME.md`   Trusted execution
                                                                     boundary, execution
                                                                     ownership, dispatch
                                                                     rules, credential
                                                                     custody, runtime
                                                                     responsibilities, and
                                                                     prohibition on
                                                                     agent-controlled
                                                                     privileged execution.

  XGN-18                  `XGN_EXTERNAL_SERVICE_ADAPTERS.md`         Adapters to external
                                                                     services. Defines how
                                                                     approved XGN operations
                                                                     map to provider APIs
                                                                     while preserving shared
                                                                     contracts, least
                                                                     privilege, and
                                                                     provider-specific
                                                                     isolation.

  XGN-19                  `XGN_GITHUB_OPERATION_PROFILE.md`          GitHub-specific
                                                                     operation profile,
                                                                     including supported
                                                                     repository operations,
                                                                     branch and pull-request
                                                                     workflows, provider
                                                                     preconditions, and
                                                                     GitHub result mapping.
                                                                     It is a provider
                                                                     profile, not the
                                                                     definition of XGN as a
                                                                     whole.
  ------------------------------------------------------------------------------------------

### Group E --- Relay, state, and reliability

  ---------------------------------------------------------------------------------------------
  ID                      Proposed specification file                   Responsibility and
                                                                        scope
  ----------------------- --------------------------------------------- -----------------------
  XGN-20                  `XGN_GATEWAY_AND_RELAY.md`                    Gateway/relay
                                                                        responsibilities,
                                                                        ingress, routing, phone
                                                                        communication, request
                                                                        forwarding, rate
                                                                        enforcement,
                                                                        availability behaviour,
                                                                        and explicit limits on
                                                                        relay authority and
                                                                        data retention.

  XGN-21                  `XGN_STATE_MACHINE_AND_TRANSITIONS.md`        Authoritative session,
                                                                        draft, request,
                                                                        approval, execution,
                                                                        and recovery state
                                                                        machines; legal
                                                                        transitions; invalid
                                                                        transitions; restart
                                                                        and timeout behaviour.

  XGN-22                  `XGN_RESULT_RECONCILIATION_AND_RECOVERY.md`   Result delivery,
                                                                        idempotency, duplicate
                                                                        handling, unknown
                                                                        outcomes, provider-side
                                                                        reconciliation,
                                                                        interrupted workflows,
                                                                        and safe recovery
                                                                        without blind
                                                                        re-execution.

  XGN-23                  `XGN_ERRORS_RETRIES_AND_RATE_LIMITS.md`       Error taxonomy, stable
                                                                        error codes,
                                                                        retryability, retry
                                                                        windows, backoff,
                                                                        polling limits, offline
                                                                        handling, and
                                                                        agent/operator recovery
                                                                        instructions.
  ---------------------------------------------------------------------------------------------

### Group F --- Security, privacy, and assurance

  -----------------------------------------------------------------------------------------------------
  ID                      Proposed specification file                        Responsibility and scope
  ----------------------- -------------------------------------------------- --------------------------
  XGN-24                  `XGN_SECURITY_AND_THREAT_MODEL.md`                 Threat model, trust
                                                                             boundaries, attack
                                                                             surfaces, replay and link
                                                                             theft risks, prefetch
                                                                             behaviour, credential
                                                                             exposure, injection, abuse
                                                                             cases, and required
                                                                             mitigations.

  XGN-25                  `XGN_PRIVACY_AUDIT_AND_DATA_RETENTION.md`          Data classification,
                                                                             content handling, secrets,
                                                                             transient and durable
                                                                             storage, audit events, log
                                                                             redaction, retention,
                                                                             deletion, and privacy
                                                                             responsibilities across
                                                                             components.

  XGN-26                  `XGN_COMPATIBILITY_TESTING_AND_CONFORMANCE.md`     Transport probes, protocol
                                                                             conformance, test vectors,
                                                                             security tests,
                                                                             integration tests,
                                                                             acceptance criteria,
                                                                             supported-client
                                                                             declarations, and release
                                                                             qualification.

  XGN-27                  `XGN_OPERATIONS_OBSERVABILITY_AND_GOVERNANCE.md`   Operational diagnostics,
                                                                             health and telemetry
                                                                             boundaries,
                                                                             deployment/configuration
                                                                             governance, version
                                                                             support, change control,
                                                                             deprecation, and extension
                                                                             approval.
  -----------------------------------------------------------------------------------------------------

## 7. Module boundaries and ownership rules

The module catalogue is intended to prevent specification overlap and
contradictory sources of truth.

1.  **One authoritative definition per concern.** A rule must be defined
    in one owning module. Other modules may reference it but should not
    redefine it.
2.  **Shared contracts govern interfaces.** Modules may define their
    internal behaviour, but cross-component communication must use the
    shared contract definitions in XGN-02.
3.  **Transport modules do not define authority.** XGN-10 through XGN-13
    describe how requests are carried. They cannot redefine grants,
    policy, approval, or execution authority.
4.  **Policy and approval are distinct.** Policy determines whether an
    action is permitted; approval records the operator's decision for
    actions requiring human authorization.
5.  **Execution is not transport.** A successful URL fetch, tool call,
    relay delivery, or shell request is not proof that an operation was
    authorized or executed.
6.  **Provider logic stays behind adapters.** GitHub-specific behaviour
    belongs in XGN-19 and the relevant adapter contracts, not in generic
    navigation or request lifecycle rules.
7.  **State transitions are centralized.** Other modules may describe
    their state-related requirements, but XGN-21 owns the authoritative
    lifecycle and transition model.
8.  **Recovery is explicit.** XGN-22 owns outcome reconciliation. A
    transport retry must never be interpreted as permission to repeat a
    protected side effect.
9.  **Security and privacy requirements are cross-cutting.** Each module
    must reference applicable controls in XGN-24 and XGN-25.
10. **Testability is part of the specification.** Each module must state
    its externally observable requirements and point to the relevant
    conformance tests in XGN-26.

## 8. Dependency map

The following map is a logical dependency guide, not a strict
build-order graph.

  -----------------------------------------------------------------------
  Module group            Primary dependencies    What it enables
  ----------------------- ----------------------- -----------------------
  Foundation (XGN-00 to   None; establishes the   All other modules
  XGN-03)                 common model            

  Session and request     Foundation and shared   Consistent sessions,
  lifecycle (XGN-04 to    contracts               requests, drafts,
  XGN-08)                                         validation, and
                                                  integrity

  Transport (XGN-09 to    Shared contracts,       Multiple ways to
  XGN-14)                 session model, request  navigate and submit
                          envelope, discovery     requests
                          model                   

  Policy and execution    Session/grant, request  Controlled approval and
  (XGN-15 to XGN-19)      lifecycle, validation,  trusted execution
                          identity/trust          

  Relay and reliability   Contracts, transport    Secure delivery, state
  (XGN-20 to XGN-23)      abstraction, state      consistency, and safe
                          model, security         recovery
                          requirements            

  Assurance (XGN-24 to    All modules             Security, privacy,
  XGN-27)                                         testing, operations,
                                                  and governance across
                                                  the framework
  -----------------------------------------------------------------------

### 8.1 Critical dependency principles

-   XGN-02 is a foundational dependency for every component that
    communicates across a boundary.
-   XGN-04, XGN-06, and XGN-15 jointly define the relationship between a
    session grant, a request, and permission to perform an operation.
-   XGN-16 and XGN-17 define the separation between operator approval
    and trusted execution.
-   XGN-09 defines transport selection; individual transport modules
    define their own mechanics.
-   XGN-21 and XGN-22 are essential to reliable lifecycle handling and
    must be designed before transport-specific retry behaviour is
    finalized.
-   XGN-24 and XGN-25 constrain the design of every module, not just the
    security and privacy modules themselves.

## 9. Recommended reading paths

AI agents should load only the modules needed for the task, together
with their dependencies.

  -----------------------------------------------------------------------
  Task                    Read first              Then consult
  ----------------------- ----------------------- -----------------------
  Understand XGN as a     This index, XGN-00      XGN-01, XGN-02, XGN-03
  whole                                           

  Add or change a         XGN-00, XGN-02, XGN-09  Relevant transport
  transport                                       module, XGN-06, XGN-15,
                                                  XGN-21, XGN-26

  Change Composed GET     XGN-10                  XGN-02, XGN-03, XGN-04,
                                                  XGN-06, XGN-07, XGN-08,
                                                  XGN-21 to XGN-26

  Add a new agent-facing  XGN-03, XGN-14          XGN-02, XGN-06, XGN-07,
  command                                         XGN-08, XGN-15, XGN-21,
                                                  XGN-26

  Add or modify an        XGN-06, XGN-07, XGN-15  XGN-16 to XGN-19,
  operation                                       XGN-21, XGN-22, XGN-24,
                                                  XGN-26

  Change session          XGN-04, XGN-05, XGN-15  XGN-16, XGN-21, XGN-24,
  permissions                                     XGN-25, XGN-26

  Change approval         XGN-16                  XGN-02, XGN-04, XGN-06,
  behaviour                                       XGN-15, XGN-17, XGN-21,
                                                  XGN-22

  Change GitHub           XGN-18, XGN-19          XGN-02, XGN-06, XGN-15
  integration                                     to XGN-17, XGN-22,
                                                  XGN-24, XGN-26

  Change relay behaviour  XGN-20                  XGN-02, XGN-05, XGN-09,
                                                  XGN-21 to XGN-25

  Investigate duplicate   XGN-21, XGN-22, XGN-23  XGN-02, XGN-06,
  or uncertain execution                          relevant transport and
                                                  provider adapter
                                                  modules

  Perform a security      XGN-24, XGN-25          XGN-02, XGN-04 to
  review                                          XGN-08, XGN-15 to
                                                  XGN-23

  Qualify a release       XGN-26, XGN-27          All affected modules
                                                  and their dependencies
  -----------------------------------------------------------------------

## 10. Normative rules that apply to the entire framework

The detailed modules must refine these framework-level rules without
contradicting them:

-   **One framework, one request model:** all supported transports
    converge on the shared XGN request contract.
-   **No transport-based privilege:** using a different channel cannot
    grant additional permissions.
-   **Phone-side authority:** the designated Gitana trusted runtime
    retains the authoritative policy and approval boundary.
-   **Explicit bounded grants:** sessions are constrained by an explicit
    grant and cannot widen their own scope.
-   **No agent-held GitHub credentials:** agents do not receive Gitana's
    long-lived provider credentials as a means of executing protected
    operations.
-   **Approval binds to the request:** the approved request must be the
    request that is dispatched; any material change invalidates the
    prior approval.
-   **Fail closed:** malformed, stale, unauthorized, out-of-scope, or
    integrity-invalid requests are rejected.
-   **No blind retries:** an uncertain result is reconciled against
    authoritative state before any further side effect is attempted.
-   **Least privilege and minimum data:** components receive only the
    data and authority required for their role.
-   **Auditable lifecycle:** relevant grant, request, approval,
    execution, and recovery events are traceable without exposing
    secrets.
-   **Contract-first integration:** cross-component communication is
    defined through shared contracts, not undocumented direct
    dependencies.
-   **No silent fallback:** a transport or execution fallback must
    preserve the same scope, policy, approval, and audit guarantees.

## 11. Specification status and maturity

The module catalogue is a proposed decomposition of the XGN
specification set. It is not evidence that every module has been
written, approved, implemented, or tested.

Each subordinate specification should carry a visible status block using
a consistent lifecycle, for example:

-   **Proposed** --- drafted for review; not authoritative for
    implementation.
-   **Under review** --- actively being assessed; unresolved decisions
    remain.
-   **Approved** --- accepted as the current normative specification.
-   **Implemented** --- corresponding implementation exists.
-   **Verified** --- required conformance and acceptance tests have
    passed.
-   **Deprecated** --- superseded; retained for historical traceability.

A module should also record its version, owner, last review date,
dependencies, superseded documents, open decisions, and
implementation/test references.

The original *Gitana Surface Map (Composed GET): Proposal* explicitly
described itself as proposed and untested. Its claims, assumptions, and
open decisions must therefore remain proposals until separately
reviewed, resolved, and verified. Renaming and indexing the material
under XGN does not, by itself, approve those claims or make them
implementation-ready.

## 12. Rules for AI agents and specification consumers

This index is intended to be the first document loaded when an AI agent
is asked to work on XGN.

Agents consuming the specification set should follow these rules:

1.  Read this index first to identify the relevant module IDs and
    dependencies.
2.  Retrieve the smallest sufficient set of subordinate specifications
    for the requested task.
3.  Treat each module's stated owning responsibility as authoritative
    for that concern.
4.  Follow references to shared contracts rather than inventing or
    duplicating interfaces.
5.  Do not infer that an item is implemented merely because it appears
    in this index.
6.  Do not treat proposed, unapproved, or untested material as a binding
    implementation requirement.
7.  Identify conflicts between modules and report them rather than
    silently choosing one.
8.  Preserve existing module IDs and canonical terminology when
    proposing changes.
9.  Update this index whenever modules are added, removed, renamed,
    split, merged, or materially re-scoped.
10. When producing implementation changes, identify all affected modules
    and any corresponding tests or repository documentation that must
    also change.

### 12.1 Suggested agent retrieval procedure

``` text
1. Read XGN_FRAMEWORK_SPECIFICATION_INDEX.md.
2. Classify the requested work by XGN module ID.
3. Load the target module and its declared dependencies.
4. Check the status and version of each loaded specification.
5. Identify applicable shared contracts and framework invariants.
6. Implement or review only within the authorized scope.
7. Run or specify the relevant conformance tests.
8. Report affected modules, unresolved decisions, and required index updates.
```

## 13. Repository organization recommendation

Keep the XGN specification set in one dedicated documentation directory
so agents can discover it as a coherent collection without loading
unrelated Gitana documentation.

Recommended structure:

``` text
docs/
└── xgn/
    ├── XGN_FRAMEWORK_SPECIFICATION_INDEX.md
    ├── XGN_FRAMEWORK_ARCHITECTURE.md
    ├── XGN_TERMINOLOGY_AND_CONVENTIONS.md
    ├── XGN_SHARED_CONTRACTS.md
    ├── XGN_CAPABILITY_DISCOVERY_AND_SURFACE_MAP.md
    ├── XGN_SESSION_AND_GRANT_MODEL.md
    ├── XGN_IDENTITY_AND_TRUST_MODEL.md
    ├── XGN_REQUEST_ENVELOPE_AND_LIFECYCLE.md
    ├── XGN_REQUEST_COMPOSITION_AND_DRAFTS.md
    ├── XGN_VALIDATION_AND_CANONICALIZATION.md
    ├── XGN_TRANSPORT_ABSTRACTION_AND_SELECTION.md
    ├── XGN_COMPOSED_GET_TRANSPORT.md
    ├── XGN_CONNECTOR_AND_TOOL_TRANSPORT.md
    ├── XGN_SHELL_AND_AUTHENTICATED_TRANSPORT.md
    ├── XGN_COPY_PASTE_HANDOFF_TRANSPORT.md
    ├── XGN_NAVIGATION_PANELS_AND_COMMANDS.md
    ├── XGN_POLICY_AND_SCOPE_ENFORCEMENT.md
    ├── XGN_APPROVAL_AND_OPERATOR_CONTROL.md
    ├── XGN_EXECUTION_AUTHORITY_AND_RUNTIME.md
    ├── XGN_EXTERNAL_SERVICE_ADAPTERS.md
    ├── XGN_GITHUB_OPERATION_PROFILE.md
    ├── XGN_GATEWAY_AND_RELAY.md
    ├── XGN_STATE_MACHINE_AND_TRANSITIONS.md
    ├── XGN_RESULT_RECONCILIATION_AND_RECOVERY.md
    ├── XGN_ERRORS_RETRIES_AND_RATE_LIMITS.md
    ├── XGN_SECURITY_AND_THREAT_MODEL.md
    ├── XGN_PRIVACY_AUDIT_AND_DATA_RETENTION.md
    ├── XGN_COMPATIBILITY_TESTING_AND_CONFORMANCE.md
    └── XGN_OPERATIONS_OBSERVABILITY_AND_GOVERNANCE.md
```

The index should remain concise relative to the detailed modules. It
should contain links, responsibility summaries, dependencies, and
status---not copied technical content.

## 14. Change management and consistency

Changes to XGN should be managed as changes to a coordinated
specification set.

-   A new capability should be assigned to an existing module or a newly
    registered module.
-   A new transport should extend the transport abstraction and shared
    contract rather than create a separate policy or execution path.
-   A change to a shared contract requires an impact review across every
    dependent module.
-   A change to session, request, approval, or execution states requires
    a coordinated update to the state machine, affected workflows, error
    handling, and tests.
-   A change to security assumptions requires review of affected
    transport, relay, runtime, privacy, and conformance modules.
-   Removed or superseded requirements should be marked explicitly; do
    not silently delete architectural history.
-   The index and module catalogue must be updated in the same change
    set as any module restructuring.

Every module should include a short **Related specifications** section
listing its direct dependencies and affected consumers. Avoid
reproducing entire sections from another module.

## 15. Initial source mapping and transition

The earlier document **Gitana Surface Map (Composed GET): Proposal** is
the initial source material for the XGN navigation and Composed GET
workstream.

Its concepts should be redistributed into the relevant modules rather
than retained as a competing top-level specification. In particular:

-   Framework-wide architecture and trust separation → XGN-00, XGN-02,
    XGN-05, XGN-17, XGN-20, and XGN-24.
-   Surface Map, panels, and commands → XGN-03 and XGN-14.
-   Session, grant, and operator gates → XGN-04, XGN-05, and XGN-16.
-   Request envelope, drafts, commands, and integrity → XGN-06, XGN-07,
    and XGN-08.
-   Composed GET URL mechanics, rolling links, sequencing, and
    app-specific limits → XGN-10.
-   Transport selection and alternative channels → XGN-09 and XGN-11 to
    XGN-13.
-   Relay responsibilities → XGN-20.
-   State, retry, unknown outcomes, and reconciliation → XGN-21 to
    XGN-23.
-   Threats, privacy, probes, and acceptance tests → XGN-24 to XGN-26.
-   Deployment, support, and lifecycle governance → XGN-27.

This mapping is a documentation transition guide. It does not resolve
the proposal's open decisions, validate its untested claims, or silently
promote proposed behaviour to approved requirements.

## 16. Completion criteria for the XGN specification set

The XGN specification set should be considered structurally complete
when:

1.  Every module in this index has an authoritative document or an
    explicitly approved decision to defer/remove it.
2.  Every module has a defined owner, scope, status, version, and
    dependency list.
3.  Shared contracts are defined once and referenced consistently.
4.  Transport modules converge on the same request, policy, approval,
    execution, and audit lifecycle.
5.  Session, request, approval, execution, and recovery state machines
    are complete and mutually consistent.
6.  Security, privacy, and failure-handling requirements are addressed
    across all modules.
7.  Every normative requirement has an identifiable verification method
    or test reference.
8.  Open decisions are recorded and cannot be mistaken for settled
    requirements.
9.  The index accurately reflects the current module set and links to
    each subordinate specification.
10. An AI agent can use this index to identify the minimum relevant
    specification set for a defined task without needing to ingest the
    entire framework.

------------------------------------------------------------------------

**End of main specification overview.**
