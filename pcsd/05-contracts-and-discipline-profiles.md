# 05. Core Contracts & Discipline Profiles

> Part of [`GTN-PCS-001`](./README.md) · Covers Sections 7, 9, and 10.

---

## 9. Universal Task Contract

The Universal Task Contract (UTC) is the most important proposed product contract. It is the stable, machine-readable representation of work that allows Gitana to remain worker-agnostic and discipline-neutral.

### 9.1 Required logical fields

| Logical group | Minimum content |
|---|---|
| Identity | `task_id`, `contract_version`, `created_at`, `updated_at`, `created_by`, `repository_id` or `workspace_id`. |
| Intent | `title`, `objective`, `description`, `discipline_profile`, `task_type`, `scope`, `out_of_scope`. |
| Inputs and context | `input_artifacts`, `repository references`, `external references`, `context selection`, `provenance`, `freshness requirements`. |
| Expected outputs | `deliverable definitions`, `artifact types`, `formats`, `destinations`, `metadata`, and `output constraints`. |
| Constraints | `policy references`, `allowed operations`, `forbidden operations`, `resource limits`, `deadlines`, `network requirements`, `data handling`. |
| Plan | `subtasks`, `stages`, `dependencies`, `ordering`, `parallelization constraints`, `checkpoints`. |
| Worker requirements | `required capabilities`, `compatible contract versions`, `execution environment requirements`; never a hard-coded vendor dependency. |
| Acceptance | `acceptance criteria`, `validation procedures`, `reviewer requirements`, `completion thresholds`. |
| Authorization | `required approvals`, `permission scope`, `user confirmation requirements`, `expiry and revocation rules`. |
| Lifecycle | `state`, `state transitions`, `attempts`, `timestamps`, `pause/block reasons`, `cancellation and failure details`. |
| Outcome | `result summary`, `artifact references`, `validation results`, `evidence`, `unresolved items`, `final disposition`. |
| Provenance | `worker identity/type`, `tool versions`, `inputs used`, `activities`, `generated artifacts`, `approvals`, and `relevant execution events`. |

### 9.2 Task lifecycle

Recommended baseline states: `DRAFT` → `READY` → `QUEUED` → `ASSIGNED` → `EXECUTING`. Execution may transition to `AWAITING_INPUT`, `AWAITING_APPROVAL`, `BLOCKED`, or `PAUSED`. Terminal states are `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`, and `REJECTED`. Exact transitions, retry rules, and terminal-state semantics must be defined in the detailed task lifecycle specification.

### 9.3 Completion rule

A task `MUST NOT` be marked `COMPLETED` solely because a worker reports success. Gitana must evaluate required acceptance criteria, required validators, required human approvals, and artifact availability. If some criteria remain unmet, the outcome must be represented as partial, blocked, failed, or awaiting review as appropriate.

### 9.4 Contract evolution

- Contracts are explicitly versioned and machine-readable.
- Backward-compatible optional additions should be preferred; breaking changes require a new major contract version or an explicit migration.
- Unknown required fields or unsupported major versions must fail safely rather than be silently ignored.
- Task packages must declare schema and profile versions and support validation before import.

---

## 10. Worker Interoperability Contract

Workers are execution providers, not owners of Gitana tasks. A worker may be a local process, shell-based agent, remote agent, model-backed service, deterministic tool, or human-operated executor, provided it implements a compatible contract.

### 10.1 Worker declaration

- Stable worker identifier and implementation/version metadata.
- Supported worker-contract versions and declared capabilities.
- Accepted input and output artifact formats.
- Execution environment and resource requirements.
- Permission requirements and operations the worker may request.
- Timeout, cancellation, progress, and status-reporting behavior.
- Network, data-handling, and persistence declarations.
- Known limitations and failure modes.

### 10.2 Invocation and response

Gitana supplies a task-scoped execution request containing the task contract, selected context, authorized inputs, policy envelope, and correlation identifiers. The worker returns structured progress, output artifact references or payloads, validation claims, diagnostics, and a terminal status. Gitana independently validates outputs and applies task-state transitions.

### 10.3 Worker independence requirements

- Task identity, task state, acceptance criteria, and canonical outputs remain under Gitana's control.
- Worker-specific conversations or memory are not authoritative task records.
- A task can be re-assigned to another compatible worker without rewriting its core specification.
- Workers cannot enlarge their own permissions or alter user authorization.
- Worker responses are treated as untrusted data until validated.
- Worker contracts must not require direct knowledge of relay internals or unrelated component credentials.

### 10.4 Local and remote workers

The product should support local shell-based workers and remote workers through adapters. The execution boundary must be explicit in both cases. Network endpoints, credentials, and transport choices are adapter concerns and must not leak into the universal task model.

---

## 7. Discipline Profiles

Profiles extend the universal task lifecycle with domain-specific terminology, templates, artifact types, schemas, validators, workflow packs, and quality criteria. A profile `MUST NOT` fork or replace the core task engine or silently weaken core security controls.

- **DCP-01 — Software engineering and IT:** Coding, debugging, refactoring, testing, architecture, DevOps, infrastructure, security reviews, APIs, technical documentation, maintenance.
- **DCP-02 — Scientific research and academia:** Literature reviews, proposals, hypotheses, experimental design, data collection, analysis, reproducibility, publications.
- **DCP-03 — Data science and analytics:** Data preparation, statistics, visualization, modeling, data quality, reporting, ETL, analytical reproducibility.
- **DCP-04 — Business and management:** Business plans, feasibility studies, market research, SOPs, strategy, performance reporting, process improvement.
- **DCP-05 — Engineering and technical disciplines:** Requirements, calculations, specifications, simulations, design reviews, verification, validation, change management.
- **DCP-06 — Design and creative production:** Creative briefs, branding, graphics, illustration, video, audio, story development, content, asset management.
- **DCP-07 — Education and learning:** Curricula, lesson plans, learning materials, exercises, assessments, study guides, portfolios.
- **DCP-08 — Legal and compliance support:** Document organization, contract comparison, regulatory research, policy drafting, checklists, evidence organization; professional judgment remains with qualified practitioners.
- **DCP-09 — Finance and accounting:** Budgets, analysis, bookkeeping support, reconciliation, forecasting, reporting, expense classification, audit preparation.
- **DCP-10 — Publishing, communications, and media:** Journalism research, editorial planning, writing, fact-checking, copyediting, translation, publishing workflows.
- **DCP-11 — Project and operations management:** Plans, work breakdown structures, scheduling, resource planning, risk registers, procedures, performance and delivery reports.
- **DCP-12 — Geography and environmental sciences:** GIS, mapping, environmental assessments, spatial datasets, fieldwork records, climate analysis, conservation research.
- **DCP-13 — Healthcare administration:** Administrative documentation, scheduling workflows, policy and procedure support, non-clinical reporting; no autonomous clinical decisions.
- **DCP-14 — Architecture and construction:** Project briefs, specifications, drawing/document coordination, quantity documentation, design review support, change tracking.
- **DCP-15 — Manufacturing and quality operations:** Work instructions, inspection records, process documentation, nonconformance tracking, quality reports, production analysis.
- **DCP-16 — Agriculture and logistics:** Farm records, crop and supply-chain plans, inventory, route and operations analysis, field reports, traceability.
- **DCP-17 — Public administration and nonprofit operations:** Program plans, grant documentation, policy research, service reports, procurement documentation, accountability records.

### 7.1 Profile conformance requirements

- Every profile declares its version, scope, supported task types, required capabilities, artifact formats, validators, and known limitations.
- Every profile uses the universal task and worker contracts; profile-specific fields are namespaced and versioned.
- Profiles declare whether outputs are advisory, draft, reviewed, or suitable for a defined operational use.
- Profiles cannot grant permissions, enable persistence, or bypass approval requirements on their own.
- Profiles must include sample tasks and test fixtures for their supported task types.
- High-impact and regulated-domain profiles must explicitly define human review requirements and professional responsibility boundaries.
