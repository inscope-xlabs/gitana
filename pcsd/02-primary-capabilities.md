# 02. Primary Capabilities (`GTN-PRI-001` – `GTN-PRI-060`)

> Part of [`GTN-PCS-001`](./README.md) · Covers Section 4, Groups A–F.

The catalogue preserves the feature set discussed during product definition. `P0` items are candidates for the foundational release; remaining priorities (`P1`–`P3`) indicate maturity and sequencing, not commercial entitlements.

---

## A. Repository and Workspace Management

### GTN-PRI-001 | Repository identity and registration | P0
Create, import, clone, register, identify, and manage repositories as independent workspaces.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-002 | Repository profiles | P1
Maintain purpose, discipline, ownership, description, status, and operational metadata.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-003 | Repository isolation | P0
Isolate files, task context, configuration, credentials, permissions, history, and outputs by repository.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-004 | Repository discovery | P1
Discover local, remote, and imported Git repositories through supported providers.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-005 | Repository lifecycle management | P1
Initialize, activate, suspend, archive, restore, export, and safely remove repositories.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-006 | Repository structure awareness | P0
Inspect directory structures, file types, manifests, documentation, schemas, and configuration.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-007 | Repository context indexing | P1
Build searchable indexes of repository content and relationships with configurable exclusions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-008 | Repository state management | P0
Track branches, commits, tags, working-tree changes, and untracked files.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-009 | Repository conventions and rules | P1
Define instructions, naming conventions, directory policies, formats, quality standards, and constraints.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-010 | Repository portability | P1
Export and reopen repository workspaces without dependence on a particular worker.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

---

## B. Task Management and Definition

### GTN-PRI-011 | Task creation | P0
Create tasks manually, conversationally, from templates, imports, or an external contract.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-012 | Structured task specifications | P0
Represent objective, scope, constraints, inputs, outputs, and completion criteria explicitly.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-013 | Task classification | P1
Categorize tasks by discipline, type, priority, complexity, risk, status, and capabilities.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-014 | Task decomposition | P0
Break objectives into subtasks, phases, milestones, and independently verifiable deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-015 | Task dependencies | P1
Define prerequisites, sequencing, blocking conditions, and dependency graphs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-016 | Task acceptance criteria | P0
Define objective conditions required for completion.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-017 | Task lifecycle management | P0
Support draft, ready, queued, assigned, executing, awaiting input/approval, completed, failed, cancelled, and archived states.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-018 | Task prioritization and scheduling | P1
Set priority, deadlines, due dates, execution windows, and scheduling policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-019 | Task context management | P0
Associate relevant files, prior outputs, references, datasets, and supporting material.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-020 | Task revision and change management | P1
Retain requirement revisions, rationale, and impact on existing work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-021 | Task templates | P1
Provide reusable task definitions with inputs, outputs, constraints, and verification steps.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-022 | Task cloning and reuse | P2
Duplicate tasks, reuse specifications, and create repeatable task sequences.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

---

## C. Worker-Agnostic Execution

### GTN-PRI-023 | Worker abstraction | P0
Define a common worker contract independent of vendor, model, agent, shell process, or provider.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-024 | Worker registration and discovery | P0
Register compatible workers and discover declared capabilities, limitations, and interfaces.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-025 | Capability-based worker selection | P1
Match tasks to workers by capabilities, formats, tools, resources, and constraints.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-026 | Worker assignment | P0
Assign tasks manually or through configurable selection policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-027 | Execution orchestration | P0
Coordinate invocation, stages, outputs, handoffs, and completion.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-028 | Execution context provisioning | P0
Provide only the repository context, files, tools, instructions, and permissions needed.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-029 | Execution isolation | P0
Prevent improper interference between tasks, workers, repositories, and execution contexts.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-030 | Execution lifecycle control | P0
Start, pause, resume, cancel, retry, terminate, and inspect executions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-031 | Execution timeout and resource controls | P0
Apply deadlines, resource budgets, concurrency limits, and cancellation policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-032 | Worker failure handling | P0
Detect failures, capture diagnostics, retry safely, and support reassignment.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-033 | Worker substitution | P1
Allow compatible workers to continue or repeat work without changing the task definition.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-034 | Worker-independent task records | P0
Keep specifications, results, and history in Gitana's domain model, not worker-private memory.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

---

## D. Artifact and Deliverable Management

### GTN-PRI-035 | Universal artifact management | P0
Treat documents, code, spreadsheets, datasets, diagrams, media, models, and other files as artifacts.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-036 | Artifact registration and identification | P0
Track artifact identity, type, version, location, ownership, task association, and metadata.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-037 | Artifact input/output contracts | P1
Define expected formats, schemas, validation rules, and relationships for inputs and outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-038 | Artifact versioning | P0
Maintain versions and change history for generated or modified deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-039 | Artifact comparison | P1
Compare supported text, code, structured data, document, and media formats.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-040 | Artifact validation | P0
Validate outputs against schemas, formatting, technical constraints, and acceptance criteria.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-041 | Artifact relationships | P1
Link artifacts to tasks, subtasks, source material, evidence, revisions, and deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-042 | Artifact packaging | P1
Package results, supporting material, manifests, reports, and metadata.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-043 | Artifact integrity | P1
Use checksums and integrity checks to identify missing, modified, or corrupted outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

---

## E. Verification and Completion

### GTN-PRI-044 | Acceptance verification | P0
Evaluate whether a task satisfies its declared completion criteria.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-045 | Automated validation | P0
Run tests, checks, rules, calculations, schema validation, linting, and verification procedures.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-046 | Human review | P0
Allow inspection, comments, change requests, and approval of deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-047 | Evidence-based completion | P0
Attach supporting evidence to important completion claims.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-048 | Task outcome reporting | P0
Explain what was requested, performed, produced, and left incomplete.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-049 | Partial completion support | P0
Distinguish completed, partially completed, blocked, rejected, and unverified work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-050 | Completion certification record | P1
Create a structured record linking specification, execution, worker, outputs, verification, and approvals.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

---

## F. Security, Control, and Governance

### GTN-PRI-051 | Permission management | P0
Define read, write, execute, modify, publish, and delete permissions for actors and integrations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-052 | Least-privilege execution | P0
Grant workers only the minimum permissions needed for a task.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-053 | User authorization and confirmation | P0
Require explicit authorization for sensitive or consequential operations under policy.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-054 | Secret and credential isolation | P0
Protect tokens, keys, and credentials across workers, tasks, repositories, and logs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-055 | Execution policy enforcement | P0
Enforce repository rules, operation allowlists, network limits, file restrictions, and resource limits.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-056 | Audit trail | P0
Record significant task, authorization, configuration, worker, execution, and artifact events.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-057 | Provenance tracking | P1
Track inputs, people, workers, activities, tools, and versions contributing to outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-058 | Data retention controls | P0
Define retention and deletion rules; session content is not persisted by default.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-059 | Risk classification | P1
Classify tasks and operations by potential impact and required controls.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

### GTN-PRI-060 | Recovery and rollback | P0
Recover from interruption, failure, accidental changes, and corrupted task outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.
