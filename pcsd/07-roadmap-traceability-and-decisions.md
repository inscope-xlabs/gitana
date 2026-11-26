# 07. Roadmap, Traceability, Risks & Open Decisions

> Part of [`GTN-PCS-001`](./README.md) · Covers Sections 13, 15, and 16.

---

## 13. Phased Implementation Roadmap

### Phase 0 — Product contracts and architecture decisions
Approve product identity, task lifecycle, Universal Task Contract, worker contract, repository/workspace boundary, artifact model, permission model, persistence policy, and component boundaries. Produce schema examples and contract conformance tests.

### Phase 1 — Foundational execution unit
Implement repository registration/isolation, task creation and lifecycle, worker registration/invocation, execution control, artifact capture, acceptance criteria, basic validation, audit events, safe recovery, and essential Android UX.

### Phase 2 — Professional workflow
Add task decomposition, templates, repository indexing, evidence/research workspace, advanced artifact validation, reporting, multiple worker adapters, Git provider integration, and configurable policy profiles.

### Phase 3 — Collaboration and orchestration
Add multi-user roles, review/approval workflows, human assignment, multi-worker orchestration, event/webhook integrations, workload visibility, and advanced workflow designer.

### Phase 4 — Domain ecosystem and enterprise
Add specialized discipline profiles, managed execution fleets, distributed execution, enterprise identity/governance, advanced analytics, extension ecosystem, and regulated-workflow support.

### 13.1 Release gate rule
A phase is not complete merely because its UI exists. Its contracts, security behavior, failure handling, observability, and acceptance evidence must be implemented and tested. No later-phase automation should bypass foundational authorization, isolation, or persistence rules.

---

## 15. Dependencies and Traceability

### 15.1 Critical dependency chain

- Repository identity and isolation underpin task context, artifact paths, and permissions.
- The Universal Task Contract underpins task lifecycle, worker invocation, workflow portability, and task handoff.
- The worker contract depends on task identity, capability declarations, execution context, permissions, and artifact contracts.
- Artifact identity and versioning underpin verification, provenance, reporting, and portable delivery.
- Authorization and policy enforcement underpin every worker invocation, external integration, and consequential operation.
- Audit and provenance depend on stable identifiers for tasks, workers, activities, artifacts, and approvals.
- Discipline profiles depend on the stable core contracts and cannot substitute for them.

### 15.2 Traceability model

Each capability should be traceable through:

`Product Capability ID` → `User story / use case` → `Contract or domain requirement` → `Architecture component owner` → `Implementation issue` → `Test case` → `Release evidence`

A capability must not be marked delivered until its acceptance evidence is linked.

### 15.3 Suggested ownership domains

- Workspace and repository domain
- Task and workflow domain
- Worker and execution domain
- Artifact and evidence domain
- Security and governance domain
- Android experience and device integration
- Profiles, extensions, and external integrations

---

## 16. Risks, Assumptions, and Open Decisions

| Risk | Description / mitigation |
|---|---|
| R-01 — Scope breadth | A cross-disciplinary platform can become unfocused. Mitigation: stabilize the universal core and ship a small number of high-quality profiles incrementally. |
| R-02 — Contract complexity | An over-general task schema can become difficult to implement. Mitigation: keep a small required core, namespaced extensions, explicit versioning, and conformance fixtures. |
| R-03 — Worker capability claims | Workers may overstate capabilities or return untrusted outputs. Mitigation: treat declarations as claims, validate outputs, and enforce permissions independently. |
| R-04 — Domain risk | Some disciplines are regulated or safety-critical. Mitigation: define profile boundaries, mandatory human review, and no unsupported claims of professional certification. |
| R-05 — Android constraints | Background execution, memory, battery, storage, and process lifecycle can constrain long tasks. Mitigation: explicit execution modes, resumability, resource budgets, and supported external execution environments. |
| R-06 — Data retention ambiguity | Logs, artifacts, indexes, and session content can be confused. Mitigation: classify data types and define separate retention rules for each; preserve the bridge/relay boundary. |
| R-07 — Repository as universal boundary | Some tasks span multiple repositories or have no Git repository. Mitigation: allow an explicit workspace abstraction and multi-repository task references without weakening isolation. |

### 16.1 Decisions requiring product-owner confirmation

- **DP-01** — Is every task required to belong to exactly one repository, or may a task target a workspace containing multiple repositories?
- **DP-02** — What is the canonical persistence model for task metadata, artifact references, audit records, and optional saved session content?
- **DP-03** — Which Git operations may be executed automatically, and which always require explicit confirmation?
- **DP-04** — What is the minimum supported worker contract and versioning policy?
- **DP-05** — Are local shell workers first-class in the initial release, and what sandboxing guarantees are required?
- **DP-06** — Which artifact formats must be supported natively versus through external viewers/editors?
- **DP-07** — What task states and terminal-state semantics are mandatory?
- **DP-08** — Is multi-user collaboration a core product direction or an optional later deployment mode?
- **DP-09** — Which discipline profiles should be first after software engineering?
- **DP-10** — What data may be included in audit records, diagnostic exports, backups, and task portability packages?

### 16.2 Explicit assumptions

- Gitana's Android application is the user-facing product and owns its task lifecycle.
- The bridge and relay are supporting components, not the product identity.
- The Android bridge is self-contained and communicates with external components through shared contracts.
- The bridge binds to loopback only; IPv4 `127.0.0.1` is mandatory and IPv6 `::1` is used when supported.
- Bridge session content is not persisted unless the user activates `SAVE-SESSION-CONTENT`; that feature is exclusive to the Android bridge, never the relay.
- The catalogue is a product direction and prioritization proposal, not a claim that these capabilities currently exist.
