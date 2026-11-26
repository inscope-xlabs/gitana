# 01. Vision, Scope, Principles, Tiers, Glossary & References

> Part of [`GTN-PCS-001`](./README.md) · Covers Sections 1, 2, 3, 8, 17, and 18.

---

## 1. Executive Summary and Product Positioning

Gitana is not defined by a particular AI model, agent, relay, chat interface, or software-development workflow. It is defined by the work unit it manages: a repository-associated task with an explicit specification, controlled execution, artifacts, verification, and an inspectable outcome.

The product should be discipline-neutral at its core and discipline-aware at its edges. Its core understands repositories, tasks, workers, execution, artifacts, permissions, evidence, and outcomes. Discipline-specific requirements enter through profiles, templates, schemas, workflow packs, validators, and extensions.

### 1.1 Product promise

- Give a user a stable place to define and organize work around a repository.
- Execute that work through any compatible worker, without making the task depend on a specific worker or provider.
- Control what workers can access and do, and require human authorization for consequential actions.
- Return tangible deliverables with verification evidence and provenance, not merely a conversation transcript.
- Make tasks, workflows, and outputs portable across compatible environments where policy permits.

### 1.2 Product differentiation

Gitana's differentiator is the combination of repository-based organization, a universal task contract, worker neutrality, artifact-centric outcomes, verification, and user-governed execution. AI-assisted software development is an initial mature profile, not the architectural identity of the product.

### 1.3 Success outcomes

- A user can define and execute a task without being locked to one worker.
- A task can be inspected independently of the worker's private memory or conversation.
- A completed task has identifiable outputs and evidence mapped to acceptance criteria.
- Repository boundaries, permissions, and persistence rules are enforced consistently.
- A new discipline profile can add domain-specific workflows without forking the core task engine.

---

## 2. Scope, Principles, and Terminology

### 2.1 In scope

- Android application experience and local repository workspace management.
- Task definition, lifecycle, orchestration, worker contracts, artifacts, verification, and reporting.
- Local and remote Git repository support, subject to explicit provider integrations.
- Worker-agnostic execution through defined contracts, including local shell-based workers and remote workers.
- Security, authorization, isolation, audit, provenance, retention, portability, and recovery.
- Cross-disciplinary profiles and extension mechanisms.

### 2.2 Out of scope for this PCS

- Selecting or endorsing a particular AI model, agent vendor, or relay implementation.
- Specifying detailed Android package/module layout, class design, API payload schemas, or database schema.
- Claiming legal, medical, financial, engineering, or other regulated professional certification.
- Guaranteeing deterministic replay for nondeterministic workers or external services.
- Treating a conversation transcript as the authoritative task record.

### 2.3 Normative language

`MUST` indicates a requirement necessary for product correctness, security, or conformance. `SHOULD` indicates a strong default that may be departed from only with a documented reason. `MAY` indicates an optional capability. This PCS is a review draft; normative language becomes binding only after approval and allocation to implementation specifications.

### 2.4 Core principles

- **Repository as workspace boundary:** Every task is associated with a repository or an explicitly declared non-repository workspace; repository isolation is enforced.
- **Task-first, not agent-first:** The task specification and lifecycle belong to Gitana. Workers are replaceable execution providers.
- **Contract-driven interoperability:** All component and external boundaries use versioned, explicit contracts.
- **Artifacts over chat:** Deliverables, evidence, and outcomes are first-class records. Conversation is an interaction mechanism, not the system of record.
- **Human authority:** The user controls permissions, approvals, persistence, and consequential operations.
- **Verifiable completion:** A worker claiming success does not by itself make a task complete.
- **Portable by design:** Task specifications and eligible artifacts can move without secrets or implicit session state.
- **Discipline-neutral core:** Domain-specific behavior is added through profiles and extensions.
- **Secure defaults:** Least privilege, fail-closed behavior, data minimization, and explicit authorization are defaults.

---

## 3. Product Capability Model

The catalogue contains 166 numbered capabilities, organized into primary, secondary, and utility families. IDs are stable references for product discussion and future traceability; they are not implementation module names.

- **Primary (`GTN-PRI`):** the minimum capabilities that make Gitana an independent task execution unit. See [`02-primary-capabilities.md`](./02-primary-capabilities.md).
- **Secondary (`GTN-SEC`):** advanced productivity, research, collaboration, interoperability, and orchestration capabilities. See [`03-secondary-capabilities.md`](./03-secondary-capabilities.md).
- **Utility (`GTN-UTL`):** user interface, administration, reporting, reliability, and operational support. See [`04-utility-capabilities.md`](./04-utility-capabilities.md).

### 3.1 Priority definitions

- **P0 — Foundation:** Required for a coherent, safe, useful initial product. P0 does not imply all features must ship in one release.
- **P1 — Professional baseline:** Strongly expected for reliable professional use; may follow the first foundational release.
- **P2 — Advanced:** Adds significant automation, collaboration, or specialist capability.
- **P3 — Ecosystem / enterprise:** Advanced scale, distributed operation, or mature ecosystem capability.

### 3.2 Capability record convention

Each catalogue record contains an ID, title, intent, and priority. During detailed design, each record should additionally receive an owner, release target, dependencies, risks, detailed requirements, and executable acceptance tests. This PCS supplies cross-cutting acceptance gates ([`06-security-governance-and-nfrs.md`](./06-security-governance-and-nfrs.md#14-cross-cutting-acceptance-gates)) and a per-capability verification prompt in the catalogue.

---

## 8. Product Tiers and Capability Maturity

Tiers describe capability maturity and deployment context, not mandatory subscription packaging. Tier 5 is orthogonal: a specialized profile may be used at T1, T2, T3, or T4 if its dependencies and governance requirements are met.

- **T1 — Personal / Essential:** Single-user independent repository work; task specification, worker connection, execution, artifacts, verification, basic history, local-first controls.
- **T2 — Professional:** Advanced decomposition, templates, workflows, research/evidence, multiple worker types, validation, reporting, analytics.
- **T3 — Team / Collaborative:** Multi-user access, roles, human assignment, shared visibility, review, approvals, multi-worker coordination, integrations.
- **T4 — Enterprise / Governed:** Organization-wide policies, advanced identity/access, audit/compliance reporting, managed execution environments, retention and recovery controls.
- **T5 — Specialized / Domain Extensions:** Discipline-specific profiles, schemas, tools, validation, quality rules, and workflow packs; orthogonal to the other tiers.

### 8.1 Tier design rule

Do not artificially withhold foundational safety, portability, or user-control features to create a higher tier. Commercial packaging, if any, should be decided separately from the product capability model.

---

## 17. Glossary

- **Artifact:** A file, dataset, document, model, report, or other identifiable deliverable or input.
- **Discipline profile:** A versioned extension providing domain-specific task types, templates, formats, validators, and workflows.
- **Execution:** A bounded attempt to perform a task or task stage using a worker.
- **Provenance:** Information describing the origins, inputs, activities, agents, and derivation of an output.
- **Repository:** A Git repository used as a primary workspace and organizational boundary.
- **Task:** A structured unit of work with an objective, scope, inputs, constraints, outputs, and acceptance criteria.
- **Task contract:** The machine-readable, versioned representation of a task and its lifecycle requirements.
- **Worker:** A compatible executor that performs task work under Gitana-defined context and permissions.
- **Worker-agnostic:** Not dependent on a specific worker, model, vendor, or execution provider.
- **Workspace:** A controlled working context; it may be one repository or an explicitly configured grouping where approved.

---

## 18. References and Research Basis

These sources inform the design principles in this PCS. They are references, not a claim that Gitana conforms to or is certified against any cited standard. Full standards may require purchase or separate access.

- **[1] ISO 21502:2020** — Project, programme and portfolio management — Guidance on project management. Project governance, planning, delivery, and lifecycle practices. <https://www.iso.org/standard/74947.html>
- **[2] W3C PROV-DM** — The PROV Data Model. A model for representing entities, activities, agents, and provenance relationships. <https://www.w3.org/TR/prov-dm/>
- **[3] NIST AI Risk Management Framework 1.0** — Governance, context, measurement, risk management, documentation, and oversight for AI-enabled systems. <https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.100-1.pdf>
- **[4] ISO/IEC 42001:2023** — Artificial intelligence management system. Management-system approach to responsible AI governance and continual improvement. <https://www.iso.org/standard/42001>
- **[5] FAIR Guiding Principles** for scientific data management and stewardship. Findability, accessibility, interoperability, and reuse of data. <https://www.nature.com/articles/sdata201618>
- **[6] NIST Research Data Framework** — Research data lifecycle, stewardship, and reproducibility considerations. <https://www.nist.gov/itl/ssd/information-systems-group/research-data-framework>
- **[7] UN Transparency Protocol — Requirements** — Protocol-first interoperability, decentralization, reuse of standards, and machine-implementable specifications. <https://untp.unece.org/docs/about/Requirements/>
