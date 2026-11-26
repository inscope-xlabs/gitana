# Gitana

**Gitana** is an independent, self-contained, repository-based, worker-agnostic task execution unit. It accepts structured work, executes it through compatible workers under user-governed controls, and returns verifiable, portable outcomes.

Gitana is not tied to any AI model, agent, relay, chat interface, or software-development workflow. It is defined by the work unit it manages: a repository-associated task with an explicit specification, controlled execution, artifacts, verification, and an inspectable outcome.

Full specification: [Gitana_Product_Capability_Specification_v0.1.md](./Gitana_Product_Capability_Specification_v0.1.md)

## Product promise

- A stable place to define and organize work around a repository
- Execution through any compatible worker, with no lock-in to one worker or provider
- Controlled worker access, with human authorization required for consequential actions
- Tangible deliverables with verification evidence and provenance, not just a conversation transcript
- Portable tasks, workflows, and outputs wherever policy permits

## Core principles

- **Repository as workspace boundary**: every task belongs to a repository (or declared workspace); isolation is enforced
- **Task-first, not agent-first**: the task spec and lifecycle belong to Gitana; workers are replaceable
- **Contract-driven interoperability**: all boundaries use versioned, explicit contracts
- **Artifacts over chat**: deliverables and evidence are first-class records; conversation is not the system of record
- **Human authority**: users control permissions, approvals, persistence, and consequential operations
- **Verifiable completion**: a worker claiming success does not make a task complete
- **Portable by design**, **secure defaults** (least privilege, fail-closed, data minimization), **discipline-neutral core**

## Capability model

166 numbered capabilities in three families, prioritized P0 (foundation) through P3 (ecosystem/enterprise):

| Family | IDs | Focus |
|---|---|---|
| Primary | `GTN-PRI` | Minimum capabilities for an independent task execution unit |
| Secondary | `GTN-SEC` | Planning, research, collaboration, orchestration, interoperability |
| Utility | `GTN-UTL` | UI, administration, reporting, reliability, operations |

### Primary capability groups (P0 core)

- **Repository & workspace management**: registration, isolation, structure awareness, state management
- **Task management**: creation, structured specifications, decomposition, acceptance criteria, lifecycle
- **Worker-agnostic execution**: worker abstraction and registration, assignment, orchestration and isolation, failure handling, worker-independent records
- **Artifacts & deliverables**: universal artifact management, versioning, validation
- **Verification & completion**: acceptance verification, automated validation, human review, evidence-based completion, outcome reporting
- **Security, control & governance**: permission management, least-privilege execution, user authorization, secret isolation, policy enforcement, audit trail, retention controls, recovery/rollback

### Secondary capabilities (selection)

Planning and workflow intelligence (planning assistant, multi-stage execution, human-in-the-loop checkpoints, task queues), plus knowledge and research (research workspace, source management, evidence management, knowledge base). Advanced tiers add workflow design, parallel execution, semantic search, and ecosystem/enterprise scale.

## Discipline profiles

AI-assisted software development is an initial mature profile, not the product's architectural identity. Domain-specific behavior is added through profiles, templates, schemas, workflow packs, validators, and extensions, without forking the core task engine.

## Key contracts

- **Universal Task Contract (UTC)**: versioned, explicit specification of objective, scope, constraints, inputs, outputs, and completion criteria for every task
- **Worker Interoperability Contract**: common worker interface independent of vendor, model, agent, or provider; workers register declaring capabilities and limitations

## Security and governance

- Least privilege and fail-closed behavior by default
- Explicit user authorization for sensitive or consequential operations
- Secret and credential isolation across workers, tasks, repositories, and logs
- Full audit trail and provenance tracking
- Session content is not persisted by default (per the SAVE-SESSION-CONTENT exception rules); retention and deletion rules are user-governed

## Roadmap

The specification defines a phased implementation plan with cross-cutting acceptance gates:

- **P0, Foundation**: coherent, safe, useful initial product (repository + task + worker + artifact + verification + security core)
- **P1, Professional baseline**: reliable professional use (profiles, context indexing, scheduling, knowledge base, multi-stage execution)
- **P2, Advanced**: workflow design, parallel execution, semantic search, recurring automation
- **P3, Ecosystem/enterprise**: distributed operation, knowledge graphs, advanced scale

Note: this PCS is a review draft (v0.1); normative requirements become binding only after approval.

## Repository status

Docs-first: the product capability specification drives implementation. Capability IDs (`GTN-PRI-001` … `GTN-UTL-166`) are stable references for discussion and traceability.
