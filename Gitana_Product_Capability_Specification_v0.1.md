GITANA

Product Capability Specification

Cross-disciplinary capability catalogue, requirements, and phased product plan


|Document ID|GTN-PCS-001|
|---|---|
|Version / status|0.1 — Review draft; not yet approved|
|Date|26 November 2026|
|Product identity|Independent, self-contained, repository-based, worker-agnostic task execution unit|
|Audience|Product owner, architecture, engineering, UX, security, and prospective extension authors|

PRODUCT DEFINITION

Gitana is an independent, self-contained, repository-based, worker-agnostic task execution unit.

Gitana accepts structured work, executes it through compatible workers under user-governed controls, and returns verifiable, portable outcomes.


# Document control

|Field|Value|Notes|
|---|---|---|
|Document owner|Product owner / Gitana maintainers|To be assigned|
|Status|Review draft|Feedback requested before baseline approval|
|Version|0.1|Initial product capability specification|
|Normative language|MUST / SHOULD / MAY|Used only where requirements are intended to be normative|
|Scope|Product capabilities and acceptance direction|Not a detailed software design or implementation specification|
|Change control|Versioned review and approval|Changes to product identity or core contracts require explicit approval|


# How to review this specification
- Review the product identity and scope first. These define what Gitana is and what it is not.
- Review P0 capabilities for completeness. P0 denotes the minimum coherent product foundation, not a promise that all P0 features ship in one release.
- Review the Universal Task Contract and worker boundary. These are the central interoperability decisions.
- Review security and persistence rules against the existing Android bridge specification, including the SAVE-SESSION-CONTENT exception.
- Review discipline profiles as extension packs, not separate products or separate task engines.
- Mark each capability as accepted, changed, deferred, or rejected during product review. Capability IDs are intended to remain stable even when titles or descriptions evolve.

# Contents
- 1. Executive summary and product positioning
- 2. Scope, principles, and terminology
- 3. Product capability model
- 4. Primary capabilities (GTN-PRI)
- 5. Secondary capabilities (GTN-SEC)
- 6. Utility capabilities (GTN-UTL)
- 7. Discipline profiles
- 8. Product tiers and capability maturity
- 9. Universal Task Contract
- 10. Worker interoperability contract
- 11. Security, privacy, and governance requirements
- 12. Non-functional requirements
- 13. Phased implementation roadmap
- 14. Cross-cutting acceptance gates
- 15. Dependencies and traceability
- 16. Risks, assumptions, and open decisions
- 17. Glossary
- 18. References

# 1. Executive summary and product positioning
Gitana is not defined by a particular AI model, agent, relay, chat interface, or software-development workflow. It is defined by the work unit it manages: a repository-associated task with an explicit specification, controlled execution, artifacts, verification, and an inspectable outcome.

The product should be discipline-neutral at its core and discipline-aware at its edges. Its core understands repositories, tasks, workers, execution, artifacts, permissions, evidence, and outcomes. Discipline-specific requirements enter through profiles, templates, schemas, workflow packs, validators, and extensions.


## 1.1 Product promise
- Give a user a stable place to define and organize work around a repository.
- Execute that work through any compatible worker, without making the task depend on a specific worker or provider.
- Control what workers can access and do, and require human authorization for consequential actions.
- Return tangible deliverables with verification evidence and provenance, not merely a conversation transcript.
- Make tasks, workflows, and outputs portable across compatible environments where policy permits.

## 1.2 Product differentiation
Gitana's differentiator is the combination of repository-based organization, a universal task contract, worker neutrality, artifact-centric outcomes, verification, and user-governed execution. AI-assisted software development is an initial mature profile, not the architectural identity of the product.


## 1.3 Success outcomes
- A user can define and execute a task without being locked to one worker.
- A task can be inspected independently of the worker's private memory or conversation.
- A completed task has identifiable outputs and evidence mapped to acceptance criteria.
- Repository boundaries, permissions, and persistence rules are enforced consistently.
- A new discipline profile can add domain-specific workflows without forking the core task engine.

# 2. Scope, principles, and terminology

## 2.1 In scope
- Android application experience and local repository workspace management.
- Task definition, lifecycle, orchestration, worker contracts, artifacts, verification, and reporting.
- Local and remote Git repository support, subject to explicit provider integrations.
- Worker-agnostic execution through defined contracts, including local shell-based workers and remote workers.
- Security, authorization, isolation, audit, provenance, retention, portability, and recovery.
- Cross-disciplinary profiles and extension mechanisms.

## 2.2 Out of scope for this PCS
- Selecting or endorsing a particular AI model, agent vendor, or relay implementation.
- Specifying detailed Android package/module layout, class design, API payload schemas, or database schema.
- Claiming legal, medical, financial, engineering, or other regulated professional certification.
- Guaranteeing deterministic replay for nondeterministic workers or external services.
- Treating a conversation transcript as the authoritative task record.

## 2.3 Normative language
MUST indicates a requirement necessary for product correctness, security, or conformance. SHOULD indicates a strong default that may be departed from only with a documented reason. MAY indicates an optional capability. This PCS is a review draft; normative language becomes binding only after approval and allocation to implementation specifications.


## 2.4 Core principles
Repository as workspace boundary: Every task is associated with a repository or an explicitly declared non-repository workspace; repository isolation is enforced.

Task-first, not agent-first: The task specification and lifecycle belong to Gitana. Workers are replaceable execution providers.

Contract-driven interoperability: All component and external boundaries use versioned, explicit contracts.

Artifacts over chat: Deliverables, evidence, and outcomes are first-class records. Conversation is an interaction mechanism, not the system of record.

Human authority: The user controls permissions, approvals, persistence, and consequential operations.

Verifiable completion: A worker claiming success does not by itself make a task complete.

Portable by design: Task specifications and eligible artifacts can move without secrets or implicit session state.

Discipline-neutral core: Domain-specific behavior is added through profiles and extensions.

Secure defaults: Least privilege, fail-closed behavior, data minimization, and explicit authorization are defaults.


# 3. Product capability model
The catalogue contains 166 numbered capabilities, organized into primary, secondary, and utility families. IDs are stable references for product discussion and future traceability; they are not implementation module names.

- Primary (GTN-PRI): the minimum capabilities that make Gitana an independent task execution unit.
- Secondary (GTN-SEC): advanced productivity, research, collaboration, interoperability, and orchestration capabilities.
- Utility (GTN-UTL): user interface, administration, reporting, reliability, and operational support.

## 3.1 Priority definitions
P0 — Foundation: Required for a coherent, safe, useful initial product. P0 does not imply all features must ship in one release.

P1 — Professional baseline: Strongly expected for reliable professional use; may follow the first foundational release.

P2 — Advanced: Adds significant automation, collaboration, or specialist capability.

P3 — Ecosystem / enterprise: Advanced scale, distributed operation, or mature ecosystem capability.


## 3.2 Capability record convention
Each catalogue record contains an ID, title, intent, and priority. During detailed design, each record should additionally receive an owner, release target, dependencies, risks, detailed requirements, and executable acceptance tests. This PCS supplies cross-cutting acceptance gates in Section 14 and a per-capability verification prompt in the catalogue.


# 4–6. Numbered capability catalogue
The following catalogue preserves the feature set discussed during product definition. P0 items are candidates for the foundational release; the remaining priorities indicate maturity and sequencing, not commercial entitlements.


## A. Repository and workspace management
GTN-PRI-001  |  Repository identity and registration  |  P0

Create, import, clone, register, identify, and manage repositories as independent workspaces.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-002  |  Repository profiles  |  P1

Maintain purpose, discipline, ownership, description, status, and operational metadata.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-003  |  Repository isolation  |  P0

Isolate files, task context, configuration, credentials, permissions, history, and outputs by repository.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-004  |  Repository discovery  |  P1

Discover local, remote, and imported Git repositories through supported providers.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-005  |  Repository lifecycle management  |  P1

Initialize, activate, suspend, archive, restore, export, and safely remove repositories.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-006  |  Repository structure awareness  |  P0

Inspect directory structures, file types, manifests, documentation, schemas, and configuration.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-007  |  Repository context indexing  |  P1

Build searchable indexes of repository content and relationships with configurable exclusions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-008  |  Repository state management  |  P0

Track branches, commits, tags, working-tree changes, and untracked files.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-009  |  Repository conventions and rules  |  P1

Define instructions, naming conventions, directory policies, formats, quality standards, and constraints.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-010  |  Repository portability  |  P1

Export and reopen repository workspaces without dependence on a particular worker.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## B. Task management and definition
GTN-PRI-011  |  Task creation  |  P0

Create tasks manually, conversationally, from templates, imports, or an external contract.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-012  |  Structured task specifications  |  P0

Represent objective, scope, constraints, inputs, outputs, and completion criteria explicitly.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-013  |  Task classification  |  P1

Categorize tasks by discipline, type, priority, complexity, risk, status, and capabilities.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-014  |  Task decomposition  |  P0

Break objectives into subtasks, phases, milestones, and independently verifiable deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-015  |  Task dependencies  |  P1

Define prerequisites, sequencing, blocking conditions, and dependency graphs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-016  |  Task acceptance criteria  |  P0

Define objective conditions required for completion.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-017  |  Task lifecycle management  |  P0

Support draft, ready, queued, assigned, executing, awaiting input/approval, completed, failed, cancelled, and archived states.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-018  |  Task prioritization and scheduling  |  P1

Set priority, deadlines, due dates, execution windows, and scheduling policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-019  |  Task context management  |  P0

Associate relevant files, prior outputs, references, datasets, and supporting material.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-020  |  Task revision and change management  |  P1

Retain requirement revisions, rationale, and impact on existing work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-021  |  Task templates  |  P1

Provide reusable task definitions with inputs, outputs, constraints, and verification steps.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-022  |  Task cloning and reuse  |  P2

Duplicate tasks, reuse specifications, and create repeatable task sequences.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## C. Worker-agnostic execution
GTN-PRI-023  |  Worker abstraction  |  P0

Define a common worker contract independent of vendor, model, agent, shell process, or provider.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-024  |  Worker registration and discovery  |  P0

Register compatible workers and discover declared capabilities, limitations, and interfaces.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-025  |  Capability-based worker selection  |  P1

Match tasks to workers by capabilities, formats, tools, resources, and constraints.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-026  |  Worker assignment  |  P0

Assign tasks manually or through configurable selection policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-027  |  Execution orchestration  |  P0

Coordinate invocation, stages, outputs, handoffs, and completion.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-028  |  Execution context provisioning  |  P0

Provide only the repository context, files, tools, instructions, and permissions needed.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-029  |  Execution isolation  |  P0

Prevent improper interference between tasks, workers, repositories, and execution contexts.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-030  |  Execution lifecycle control  |  P0

Start, pause, resume, cancel, retry, terminate, and inspect executions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-031  |  Execution timeout and resource controls  |  P0

Apply deadlines, resource budgets, concurrency limits, and cancellation policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-032  |  Worker failure handling  |  P0

Detect failures, capture diagnostics, retry safely, and support reassignment.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-033  |  Worker substitution  |  P1

Allow compatible workers to continue or repeat work without changing the task definition.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-034  |  Worker-independent task records  |  P0

Keep specifications, results, and history in Gitana's domain model, not worker-private memory.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## D. Artifact and deliverable management
GTN-PRI-035  |  Universal artifact management  |  P0

Treat documents, code, spreadsheets, datasets, diagrams, media, models, and other files as artifacts.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-036  |  Artifact registration and identification  |  P0

Track artifact identity, type, version, location, ownership, task association, and metadata.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-037  |  Artifact input/output contracts  |  P1

Define expected formats, schemas, validation rules, and relationships for inputs and outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-038  |  Artifact versioning  |  P0

Maintain versions and change history for generated or modified deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-039  |  Artifact comparison  |  P1

Compare supported text, code, structured data, document, and media formats.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-040  |  Artifact validation  |  P0

Validate outputs against schemas, formatting, technical constraints, and acceptance criteria.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-041  |  Artifact relationships  |  P1

Link artifacts to tasks, subtasks, source material, evidence, revisions, and deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-042  |  Artifact packaging  |  P1

Package results, supporting material, manifests, reports, and metadata.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-043  |  Artifact integrity  |  P1

Use checksums and integrity checks to identify missing, modified, or corrupted outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## E. Verification and completion
GTN-PRI-044  |  Acceptance verification  |  P0

Evaluate whether a task satisfies its declared completion criteria.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-045  |  Automated validation  |  P0

Run tests, checks, rules, calculations, schema validation, linting, and verification procedures.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-046  |  Human review  |  P0

Allow inspection, comments, change requests, and approval of deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-047  |  Evidence-based completion  |  P0

Attach supporting evidence to important completion claims.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-048  |  Task outcome reporting  |  P0

Explain what was requested, performed, produced, and left incomplete.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-049  |  Partial completion support  |  P0

Distinguish completed, partially completed, blocked, rejected, and unverified work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-050  |  Completion certification record  |  P1

Create a structured record linking specification, execution, worker, outputs, verification, and approvals.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## F. Security, control, and governance
GTN-PRI-051  |  Permission management  |  P0

Define read, write, execute, modify, publish, and delete permissions for actors and integrations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-052  |  Least-privilege execution  |  P0

Grant workers only the minimum permissions needed for a task.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-053  |  User authorization and confirmation  |  P0

Require explicit authorization for sensitive or consequential operations under policy.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-054  |  Secret and credential isolation  |  P0

Protect tokens, keys, and credentials across workers, tasks, repositories, and logs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-055  |  Execution policy enforcement  |  P0

Enforce repository rules, operation allowlists, network limits, file restrictions, and resource limits.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-056  |  Audit trail  |  P0

Record significant task, authorization, configuration, worker, execution, and artifact events.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-057  |  Provenance tracking  |  P1

Track inputs, people, workers, activities, tools, and versions contributing to outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-058  |  Data retention controls  |  P0

Define retention and deletion rules; session content is not persisted by default.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-059  |  Risk classification  |  P1

Classify tasks and operations by potential impact and required controls.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-PRI-060  |  Recovery and rollback  |  P0

Recover from interruption, failure, accidental changes, and corrupted task outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## G. Planning and workflow intelligence
GTN-SEC-061  |  Task planning assistant  |  P1

Turn broad objectives into structured, executable plans.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-062  |  Automatic task analysis  |  P1

Identify missing requirements, ambiguity, conflicts, and incomplete inputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-063  |  Dependency analysis  |  P2

Identify critical paths, parallelizable work, and blockers.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-064  |  Workflow designer  |  P2

Construct reusable workflows from tasks, conditions, approvals, assignments, and validation.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-065  |  Conditional workflows  |  P2

Branch or retry based on approval, validation, or clarification outcomes.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-066  |  Multi-stage execution  |  P1

Separate planning, research, production, review, validation, approval, and delivery.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-067  |  Parallel task execution  |  P2

Execute independent subtasks concurrently within resource and dependency limits.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-068  |  Human-in-the-loop checkpoints  |  P1

Pause at defined checkpoints for user intervention or approval.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-069  |  Automatic progress evaluation  |  P2

Compare execution progress with plans and identify deviations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-070  |  Task impact analysis  |  P2

Identify tasks, outputs, and repository components affected by a proposed change.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-071  |  Recurring task automation  |  P2

Schedule recurring tasks with explicit execution policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-072  |  Task queues and workload management  |  P1

Organize pending, active, blocked, and completed work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## H. Knowledge and research
GTN-SEC-073  |  Research workspace  |  P1

Organize source collection, notes, analysis, and findings.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-074  |  Source and reference management  |  P1

Store citations, bibliographic data, URLs, documents, and relationships.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-075  |  Evidence management  |  P1

Associate claims, findings, decisions, and recommendations with evidence.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-076  |  Knowledge base  |  P1

Maintain reusable repository-specific knowledge, procedures, and decisions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-077  |  Knowledge extraction  |  P2

Extract structured information from supported documents and datasets.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-078  |  Semantic search  |  P2

Search content by meaning, metadata, and contextual relevance.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-079  |  Knowledge graphs and relationships  |  P3

Represent relationships among tasks, concepts, documents, people, and evidence.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-080  |  Research methodology templates  |  P2

Support questions, hypotheses, methods, limitations, and conclusions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-081  |  Research reproducibility records  |  P2

Capture methods, datasets, tools, versions, parameters, and conditions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-082  |  Source credibility assessment  |  P2

Assist assessment of authority, currency, bias, and relevance without treating it as definitive.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-083  |  Research synthesis  |  P1

Combine findings into structured summaries, comparisons, reports, and conclusions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## I. Collaboration and team productivity
GTN-SEC-084  |  Multi-user access  |  P2

Allow authorized users to work with shared repositories and tasks.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-085  |  User roles  |  P2

Support owners, administrators, contributors, reviewers, approvers, and observers.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-086  |  Task assignment to people  |  P2

Assign work to human contributors as well as compatible workers.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-087  |  Comments and discussions  |  P2

Attach discussions to tasks, artifacts, decisions, and execution stages.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-088  |  Review and approval workflows  |  P2

Provide structured review requests, feedback, revisions, and approvals.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-089  |  Shared task visibility  |  P2

Show authorized participants status, progress, blockers, and deliverables.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-090  |  Notifications and alerts  |  P1

Notify users about completion, failures, approvals, deadlines, and changes.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-091  |  Team workload visibility  |  P3

Show distribution, capacity, workload, and bottlenecks.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-092  |  Conflict detection  |  P2

Identify conflicting edits, overlapping work, and artifact collisions.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-093  |  Delegation  |  P2

Delegate tasks to people, workers, or compatible Gitana instances.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## J. Productivity and quality intelligence
GTN-SEC-094  |  Task effort estimation  |  P2

Estimate time, complexity, resources, and execution costs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-095  |  Quality scoring  |  P2

Evaluate outputs against transparent quality dimensions and criteria.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-096  |  Quality profiles  |  P2

Set quality thresholds for drafts, internal work, professional, or regulated work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-097  |  Automated quality assurance  |  P1

Execute domain-specific quality checks and validation procedures.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-098  |  Review assistance  |  P2

Highlight omissions, inconsistencies, errors, unsupported claims, and deviations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-099  |  Continuous improvement  |  P3

Use outcomes and feedback to improve templates, workflows, and policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-100  |  Lessons learned  |  P2

Capture useful findings from completed and failed tasks.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-101  |  Decision management  |  P1

Record decisions, alternatives, rationale, ownership, and consequences.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-102  |  Metrics and performance analysis  |  P2

Measure completion, failures, turnaround, rework, quality, and utilization.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-103  |  Cost and resource tracking  |  P2

Track worker usage, API consumption, storage, compute, and other costs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## K. External connectivity and interoperability
GTN-SEC-104  |  Git provider integrations  |  P1

Integrate with GitHub, GitLab, Bitbucket, and compatible Git services.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-105  |  Issue tracker integrations  |  P2

Import and synchronize work items from issue and project systems.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-106  |  Document and storage integrations  |  P2

Access authorized external documents, file stores, and knowledge repositories.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-107  |  Communication integrations  |  P2

Support authorized notifications and task-related communication.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-108  |  Data source integrations  |  P2

Connect to databases, APIs, datasets, and scientific repositories.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-109  |  Publishing and delivery integrations  |  P2

Deliver completed artifacts to authorized destinations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-110  |  External service contracts  |  P1

Define stable interfaces without coupling the core to individual providers.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-111  |  Import/export interoperability  |  P1

Transfer portable task, workflow, metadata, evidence, and execution records.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-112  |  Webhook and event support  |  P2

Allow external systems to initiate tasks or respond to lifecycle events.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## L. Advanced execution
GTN-SEC-113  |  Worker capability negotiation  |  P2

Negotiate supported operations, formats, limits, and execution requirements.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-114  |  Multi-worker collaboration  |  P2

Coordinate workers with explicit responsibilities and handoffs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-115  |  Worker specialization  |  P1

Support workers optimized for particular disciplines or task types.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-116  |  Independent verification workers  |  P2

Use a separate worker to inspect and validate another worker's output.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-117  |  Worker evaluation  |  P2

Assess reliability, success rates, performance, and task suitability.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-118  |  Fallback execution  |  P2

Route eligible work to an alternative compatible worker after failure or unavailability.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-119  |  Workflow portability  |  P2

Move compatible workflows between supported execution environments.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-120  |  Distributed execution  |  P3

Execute across devices or external compute environments when configured.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-121  |  Offline task preparation  |  P1

Create, organize, specify, and prepare tasks without network access.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-122  |  Deferred execution  |  P2

Queue eligible work until required workers, connectivity, or resources are available.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-123  |  Execution replay  |  P3

Reproduce work using captured inputs and conditions, subject to nondeterminism.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-124  |  Execution simulation  |  P2

Preview operations, permissions, resource requirements, and effects before execution.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-SEC-166  |  Task portability and handoff  |  P1

Transfer a task between compatible Gitana installations, environments, or users with its specification, authorized context, dependencies, artifacts, and relevant execution history; exclude credentials and unauthorized session content.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## M. User interface and interaction
GTN-UTL-125  |  Dashboard  |  P0

Show repository status, tasks, recent activity, approvals, and execution health.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-126  |  Repository switcher  |  P0

Switch quickly between independent workspaces.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-127  |  Task inbox  |  P0

Present new, assigned, pending, blocked, and completed tasks.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-128  |  Task detail view  |  P0

Show objectives, context, history, artifacts, evidence, and status.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-129  |  Activity timeline  |  P1

Present a chronological view of significant events.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-130  |  Global search  |  P1

Search repositories, tasks, artifacts, references, and permitted records.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-131  |  Quick actions  |  P1

Provide shortcuts for common operations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-132  |  Command interface  |  P2

Offer a command palette or equivalent fast-action interface.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-133  |  Conversational interface  |  P1

Allow natural-language interaction without making it the only control surface.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-134  |  File preview and viewer  |  P1

Preview common document, source, data, image, and media formats.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-135  |  Document editing  |  P2

Provide basic editing or external editor integration for common formats.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-136  |  Mobile-first interaction  |  P0

Support touch-friendly navigation and Android lifecycle behavior.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-137  |  Accessibility  |  P1

Support assistive technologies, text scaling, keyboard navigation, and sufficient contrast.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## N. Configuration and administration
GTN-UTL-138  |  Central settings  |  P1

Manage application, repository, task, worker, execution, and integration preferences.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-139  |  Repository-specific configuration  |  P0

Allow each repository to define its own rules and defaults.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-140  |  Configuration profiles  |  P2

Support reusable configurations for different work types.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-141  |  Environment management  |  P1

Define environment variables, tool requirements, runtime settings, and dependencies securely.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-142  |  Policy presets  |  P1

Offer predefined security and execution policies.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-143  |  User preferences  |  P1

Configure language, timezone, display, notifications, and interaction preferences.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-144  |  Backup and restore  |  P1

Back up and recover repository metadata, tasks, configuration, and authorized retained data.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-145  |  Application diagnostics  |  P1

Provide actionable troubleshooting information.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-146  |  Health monitoring  |  P1

Show worker, connection, integration, storage, and execution health.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-147  |  Storage management  |  P1

Show repository size, artifact use, cache consumption, and cleanup opportunities.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-148  |  Resource management  |  P1

Display relevant battery, memory, CPU, network, and storage constraints.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## O. Communication and reporting
GTN-UTL-149  |  Task notifications  |  P1

Provide configurable notifications for important lifecycle events.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-150  |  Execution logs viewer  |  P0

Present readable, filterable logs with sensitive-data redaction.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-151  |  Execution summaries  |  P0

Summarize completed, failed, or interrupted work.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-152  |  Report generator  |  P1

Generate task, progress, research, audit, and project reports.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-153  |  Exportable reports  |  P1

Export reports in common document and structured formats.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-154  |  Shareable deliverables  |  P1

Share authorized artifacts and task results.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-155  |  Copy and export actions  |  P1

Copy results, references, file paths, and structured information.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## P. Reliability and maintenance
GTN-UTL-156  |  Crash recovery  |  P0

Restore the application to a safe state after unexpected termination.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-157  |  Interrupted task detection  |  P0

Identify executions interrupted by app termination, restart, or connectivity loss.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-158  |  Safe retry controls  |  P0

Prevent unsafe duplicate operations when retrying interrupted tasks.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-159  |  Background execution management  |  P0

Use appropriate Android background-work mechanisms and foreground services.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-160  |  Update management  |  P2

Manage updates, compatibility, migration, and rollback where supported.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-161  |  Storage cleanup  |  P1

Identify removable temporary files, stale caches, and abandoned intermediate artifacts.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-162  |  Diagnostic export  |  P1

Export troubleshooting information while excluding secrets and protected content.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-163  |  Compatibility checks  |  P1

Check Android version, storage, supported formats, and runtime capabilities.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-164  |  User guidance  |  P1

Provide contextual help, onboarding, examples, and operational explanations.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.

GTN-UTL-165  |  Demo and sample workspaces  |  P2

Provide example repositories, tasks, workflows, and outputs.

Verification prompt: Demonstrate the capability in a representative repository/task; verify the stated outcome, permissions, failure behavior, and retained evidence.


## Additional cross-cutting capability
GTN-SEC-166  |  Task portability and handoff  |  P1

Transfer a task between compatible Gitana installations, environments, or users with its specification, authorized context, dependencies, artifacts, and relevant execution history; exclude credentials and unauthorized session content.

Verification prompt: Export a task package, import it into a compatible environment, verify task identity and authorized artifacts, and confirm that credentials and excluded session content are absent.


# 7. Discipline profiles
Profiles extend the universal task lifecycle with domain-specific terminology, templates, artifact types, schemas, validators, workflow packs, and quality criteria. A profile MUST NOT fork or replace the core task engine or silently weaken core security controls.

DCP-01 — Software engineering and IT. Coding, debugging, refactoring, testing, architecture, DevOps, infrastructure, security reviews, APIs, technical documentation, maintenance.

DCP-02 — Scientific research and academia. Literature reviews, proposals, hypotheses, experimental design, data collection, analysis, reproducibility, publications.

DCP-03 — Data science and analytics. Data preparation, statistics, visualization, modeling, data quality, reporting, ETL, analytical reproducibility.

DCP-04 — Business and management. Business plans, feasibility studies, market research, SOPs, strategy, performance reporting, process improvement.

DCP-05 — Engineering and technical disciplines. Requirements, calculations, specifications, simulations, design reviews, verification, validation, change management.

DCP-06 — Design and creative production. Creative briefs, branding, graphics, illustration, video, audio, story development, content, asset management.

DCP-07 — Education and learning. Curricula, lesson plans, learning materials, exercises, assessments, study guides, portfolios.

DCP-08 — Legal and compliance support. Document organization, contract comparison, regulatory research, policy drafting, checklists, evidence organization; professional judgment remains with qualified practitioners.

DCP-09 — Finance and accounting. Budgets, analysis, bookkeeping support, reconciliation, forecasting, reporting, expense classification, audit preparation.

DCP-10 — Publishing, communications, and media. Journalism research, editorial planning, writing, fact-checking, copyediting, translation, publishing workflows.

DCP-11 — Project and operations management. Plans, work breakdown structures, scheduling, resource planning, risk registers, procedures, performance and delivery reports.

DCP-12 — Geography and environmental sciences. GIS, mapping, environmental assessments, spatial datasets, fieldwork records, climate analysis, conservation research.

DCP-13 — Healthcare administration. Administrative documentation, scheduling workflows, policy and procedure support, non-clinical reporting; no autonomous clinical decisions.

DCP-14 — Architecture and construction. Project briefs, specifications, drawing/document coordination, quantity documentation, design review support, change tracking.

DCP-15 — Manufacturing and quality operations. Work instructions, inspection records, process documentation, nonconformance tracking, quality reports, production analysis.

DCP-16 — Agriculture and logistics. Farm records, crop and supply-chain plans, inventory, route and operations analysis, field reports, traceability.

DCP-17 — Public administration and nonprofit operations. Program plans, grant documentation, policy research, service reports, procurement documentation, accountability records.


## 7.1 Profile conformance requirements
- Every profile declares its version, scope, supported task types, required capabilities, artifact formats, validators, and known limitations.
- Every profile uses the universal task and worker contracts; profile-specific fields are namespaced and versioned.
- Profiles declare whether outputs are advisory, draft, reviewed, or suitable for a defined operational use.
- Profiles cannot grant permissions, enable persistence, or bypass approval requirements on their own.
- Profiles must include sample tasks and test fixtures for their supported task types.
- High-impact and regulated-domain profiles must explicitly define human review requirements and professional responsibility boundaries.

# 8. Product tiers and capability maturity
Tiers describe capability maturity and deployment context, not mandatory subscription packaging. Tier 5 is orthogonal: a specialized profile may be used at T1, T2, T3, or T4 if its dependencies and governance requirements are met.

T1 — Personal / Essential: Single-user independent repository work; task specification, worker connection, execution, artifacts, verification, basic history, local-first controls.

T2 — Professional: Advanced decomposition, templates, workflows, research/evidence, multiple worker types, validation, reporting, analytics.

T3 — Team / Collaborative: Multi-user access, roles, human assignment, shared visibility, review, approvals, multi-worker coordination, integrations.

T4 — Enterprise / Governed: Organization-wide policies, advanced identity/access, audit/compliance reporting, managed execution environments, retention and recovery controls.

T5 — Specialized / Domain Extensions: Discipline-specific profiles, schemas, tools, validation, quality rules, and workflow packs; orthogonal to the other tiers.


## 8.1 Tier design rule
Do not artificially withhold foundational safety, portability, or user-control features to create a higher tier. Commercial packaging, if any, should be decided separately from the product capability model.


# 9. Universal Task Contract
The Universal Task Contract (UTC) is the most important proposed product contract. It is the stable, machine-readable representation of work that allows Gitana to remain worker-agnostic and discipline-neutral.


## 9.1 Required logical fields

|Logical group|Minimum content|
|---|---|
|Identity|task_id, contract_version, created_at, updated_at, created_by, repository_id or workspace_id.|
|Intent|title, objective, description, discipline_profile, task_type, scope, out_of_scope.|
|Inputs and context|input_artifacts, repository references, external references, context selection, provenance, freshness requirements.|
|Expected outputs|deliverable definitions, artifact types, formats, destinations, metadata, and output constraints.|
|Constraints|policy references, allowed operations, forbidden operations, resource limits, deadlines, network requirements, data handling.|
|Plan|subtasks, stages, dependencies, ordering, parallelization constraints, checkpoints.|
|Worker requirements|required capabilities, compatible contract versions, execution environment requirements; never a hard-coded vendor dependency.|
|Acceptance|acceptance criteria, validation procedures, reviewer requirements, completion thresholds.|
|Authorization|required approvals, permission scope, user confirmation requirements, expiry and revocation rules.|
|Lifecycle|state, state transitions, attempts, timestamps, pause/block reasons, cancellation and failure details.|
|Outcome|result summary, artifact references, validation results, evidence, unresolved items, final disposition.|
|Provenance|worker identity/type, tool versions, inputs used, activities, generated artifacts, approvals, and relevant execution events.|


## 9.2 Task lifecycle
Recommended baseline states: DRAFT → READY → QUEUED → ASSIGNED → EXECUTING. Execution may transition to AWAITING_INPUT, AWAITING_APPROVAL, BLOCKED, or PAUSED. Terminal states are COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED, and REJECTED. Exact transitions, retry rules, and terminal-state semantics must be defined in the detailed task lifecycle specification.


## 9.3 Completion rule
A task MUST NOT be marked COMPLETED solely because a worker reports success. Gitana must evaluate required acceptance criteria, required validators, required human approvals, and artifact availability. If some criteria remain unmet, the outcome must be represented as partial, blocked, failed, or awaiting review as appropriate.


## 9.4 Contract evolution
- Contracts are explicitly versioned and machine-readable.
- Backward-compatible optional additions should be preferred; breaking changes require a new major contract version or an explicit migration.
- Unknown required fields or unsupported major versions must fail safely rather than be silently ignored.
- Task packages must declare schema and profile versions and support validation before import.

# 10. Worker interoperability contract
Workers are execution providers, not owners of Gitana tasks. A worker may be a local process, shell-based agent, remote agent, model-backed service, deterministic tool, or human-operated executor, provided it implements a compatible contract.


## 10.1 Worker declaration
- Stable worker identifier and implementation/version metadata.
- Supported worker-contract versions and declared capabilities.
- Accepted input and output artifact formats.
- Execution environment and resource requirements.
- Permission requirements and operations the worker may request.
- Timeout, cancellation, progress, and status-reporting behavior.
- Network, data-handling, and persistence declarations.
- Known limitations and failure modes.

## 10.2 Invocation and response
Gitana supplies a task-scoped execution request containing the task contract, selected context, authorized inputs, policy envelope, and correlation identifiers. The worker returns structured progress, output artifact references or payloads, validation claims, diagnostics, and a terminal status. Gitana independently validates outputs and applies task-state transitions.


## 10.3 Worker independence requirements
- Task identity, task state, acceptance criteria, and canonical outputs remain under Gitana's control.
- Worker-specific conversations or memory are not authoritative task records.
- A task can be re-assigned to another compatible worker without rewriting its core specification.
- Workers cannot enlarge their own permissions or alter user authorization.
- Worker responses are treated as untrusted data until validated.
- Worker contracts must not require direct knowledge of relay internals or unrelated component credentials.

## 10.4 Local and remote workers
The product should support local shell-based workers and remote workers through adapters. The execution boundary must be explicit in both cases. Network endpoints, credentials, and transport choices are adapter concerns and must not leak into the universal task model.


# 11. Security, privacy, and governance requirements
SEC-01 — Repository isolation: A task or worker MUST access only the repository/workspace and resources explicitly authorized for that execution.

SEC-02 — Least privilege: Workers MUST receive the minimum permissions and context required. Permissions MUST be scoped and revocable.

SEC-03 — Explicit consequential authorization: Destructive, external-write, publication, credential, or other high-impact actions MUST follow explicit policy and user-approval requirements.

SEC-04 — Secret separation: Credentials MUST be isolated from task content, worker-visible context, ordinary logs, exported task packages, and unauthorized artifacts.

SEC-05 — Fail-closed policy: If authorization, path validation, policy evaluation, or artifact validation is ambiguous or fails, the operation MUST be denied or held for review.

SEC-06 — No implicit persistence: Bridge request data, tool inputs, responses, and outputs MUST NOT be persisted by default. The sole specified exception is user-activated SAVE-SESSION-CONTENT, implemented exclusively in the Android bridge tool. The relay MUST NOT persist this content.

SEC-07 — Audit minimization: Audit events SHOULD record security-relevant metadata and outcomes without copying sensitive payloads unnecessarily.

SEC-08 — Provenance integrity: Provenance records SHOULD identify source inputs, worker, activities, tools, versions, and generated outputs; unverified worker claims must be distinguished from Gitana-verified facts.

SEC-09 — Safe import: Imported task packages, profiles, templates, and artifacts MUST be treated as untrusted until validated.

SEC-10 — Safe recovery: Recovery and retry MUST be idempotent where possible and MUST prevent duplicate consequential side effects.

SEC-11 — Retention and deletion: Retention periods and deletion behavior MUST be explicit, inspectable, and consistent with user authorization and applicable obligations.

SEC-12 — Human oversight: High-impact or domain-regulated workflows MUST define human review and approval checkpoints; Gitana must not imply professional certification.


## 11.1 Existing bridge decisions to preserve
- Loopback service binding: IPv4 loopback 127.0.0.1 is mandatory; IPv6 loopback ::1 is also bound when the runtime supports it. No wildcard or non-loopback binding is permitted.
- Persistence: SAVE-SESSION-CONTENT is the only exception to the default no-persistence rule; it belongs exclusively to the Android bridge and is unavailable to the relay.
- Path handling: canonical path normalization must be deterministic, NFC-aware, preserve case, avoid inappropriate URI decoding, resolve/contain symlinks safely, and fail closed on root escape.
- Component boundaries: bridge, relay, and other components communicate through shared versioned contracts rather than importing one another's private implementation details or credentials.

# 12. Non-functional requirements

|ID / quality attribute|Requirement|
|---|---|
|NFR-01 — Responsiveness|Common navigation and local metadata operations should remain responsive under representative device load. Long-running work must be asynchronous and visibly cancellable.|
|NFR-02 — Reliability|Task state transitions and artifact registration must survive process interruption without silently losing or duplicating committed state.|
|NFR-03 — Recoverability|The app must detect interrupted executions and present safe resume, retry, inspect, or abandon actions.|
|NFR-04 — Portability|Task contracts and eligible artifacts must use documented, versioned formats; export must not embed secrets or unauthorized session content.|
|NFR-05 — Interoperability|Core task and worker contracts must be independent of a specific model, agent vendor, relay, or Git hosting provider.|
|NFR-06 — Security|Enforce isolation, least privilege, input validation, secure secret handling, and fail-closed authorization.|
|NFR-07 — Privacy|Collect and retain only what is needed for the user-authorized purpose; default to no session-content persistence.|
|NFR-08 — Observability|Expose understandable status, diagnostics, and event history without leaking protected payloads.|
|NFR-09 — Accessibility|Support Android accessibility services, scalable text, adequate contrast, and accessible touch targets.|
|NFR-10 — Extensibility|Profiles, worker adapters, validators, and integrations must be versioned and isolated behind contracts.|
|NFR-11 — Maintainability|Product domains and modules should have clear ownership, testable interfaces, and low coupling.|
|NFR-12 — Resource awareness|Execution and background activity must respect device battery, memory, storage, thermal, and OS restrictions.|
|NFR-13 — Internationalization|User-facing text, date/time, number, and locale behavior should be localizable; task data should avoid locale-dependent ambiguity.|
|NFR-14 — Testability|Critical state transitions, permission decisions, contract validation, and recovery paths must be covered by automated tests.|


# 13. Phased implementation roadmap
Phase 0 — Product contracts and architecture decisions

Approve product identity, task lifecycle, Universal Task Contract, worker contract, repository/workspace boundary, artifact model, permission model, persistence policy, and component boundaries. Produce schema examples and contract conformance tests.

Phase 1 — Foundational execution unit

Implement repository registration/isolation, task creation and lifecycle, worker registration/invocation, execution control, artifact capture, acceptance criteria, basic validation, audit events, safe recovery, and essential Android UX.

Phase 2 — Professional workflow

Add task decomposition, templates, repository indexing, evidence/research workspace, advanced artifact validation, reporting, multiple worker adapters, Git provider integration, and configurable policy profiles.

Phase 3 — Collaboration and orchestration

Add multi-user roles, review/approval workflows, human assignment, multi-worker orchestration, event/webhook integrations, workload visibility, and advanced workflow designer.

Phase 4 — Domain ecosystem and enterprise

Add specialized discipline profiles, managed execution fleets, distributed execution, enterprise identity/governance, advanced analytics, extension ecosystem, and regulated-workflow support.


## 13.1 Release gate rule
A phase is not complete merely because its UI exists. Its contracts, security behavior, failure handling, observability, and acceptance evidence must be implemented and tested. No later-phase automation should bypass foundational authorization, isolation, or persistence rules.


# 14. Cross-cutting acceptance gates
AC-01 — Repository boundary: Given two registered repositories, a task and worker authorized for repository A cannot read or modify repository B unless a separate explicit authorization is granted.

AC-02 — Worker substitution: Given a task contract supported by two workers, Gitana can assign the task to either worker without changing the task's objective, acceptance criteria, or canonical identity.

AC-03 — Task ownership: After worker completion or failure, Gitana retains the authoritative task state, task specification, execution record, and artifact references.

AC-04 — Verifiable completion: A worker success response with missing required outputs or failed acceptance checks cannot transition the task to COMPLETED.

AC-05 — Human authorization: A protected operation is blocked until the required user authorization is obtained; a worker cannot self-approve.

AC-06 — No implicit session persistence: With SAVE-SESSION-CONTENT disabled, bridge request and response content is not written to durable storage. The relay never stores that content.

AC-07 — Explicit save mode boundary: When SAVE-SESSION-CONTENT is enabled by the user, only the Android bridge feature may persist the content within its separately specified scope; relay persistence remains prohibited.

AC-08 — Safe interruption: After process termination during an execution, Gitana identifies the interruption and offers a safe recovery decision without blindly repeating non-idempotent side effects.

AC-09 — Portable task package: An exported task package validates on import and excludes credentials, unauthorized context, and content excluded by retention policy.

AC-10 — Provenance: For a completed task, a reviewer can identify the task inputs, worker, execution activity, output artifacts, validators, and approvals to the extent captured by policy.

AC-11 — Extension isolation: A profile or worker adapter cannot bypass core permission checks, repository isolation, or user authorization.

AC-12 — Fail-closed validation: Malformed contracts, unsupported required schema versions, invalid paths, or ambiguous authorization decisions are rejected or held for review.


# 15. Dependencies and traceability

## 15.1 Critical dependency chain
- Repository identity and isolation underpin task context, artifact paths, and permissions.
- The Universal Task Contract underpins task lifecycle, worker invocation, workflow portability, and task handoff.
- The worker contract depends on task identity, capability declarations, execution context, permissions, and artifact contracts.
- Artifact identity and versioning underpin verification, provenance, reporting, and portable delivery.
- Authorization and policy enforcement underpin every worker invocation, external integration, and consequential operation.
- Audit and provenance depend on stable identifiers for tasks, workers, activities, artifacts, and approvals.
- Discipline profiles depend on the stable core contracts and cannot substitute for them.

## 15.2 Traceability model
Each capability should be traceable through: Product Capability ID → User story / use case → Contract or domain requirement → Architecture component owner → Implementation issue → Test case → Release evidence. A capability must not be marked delivered until its acceptance evidence is linked.


## 15.3 Suggested ownership domains
- Workspace and repository domain
- Task and workflow domain
- Worker and execution domain
- Artifact and evidence domain
- Security and governance domain
- Android experience and device integration
- Profiles, extensions, and external integrations

# 16. Risks, assumptions, and open decisions

|Risk|Description / mitigation|
|---|---|
|R-01 — Scope breadth|A cross-disciplinary platform can become unfocused. Mitigation: stabilize the universal core and ship a small number of high-quality profiles incrementally.|
|R-02 — Contract complexity|An over-general task schema can become difficult to implement. Mitigation: keep a small required core, namespaced extensions, explicit versioning, and conformance fixtures.|
|R-03 — Worker capability claims|Workers may overstate capabilities or return untrusted outputs. Mitigation: treat declarations as claims, validate outputs, and enforce permissions independently.|
|R-04 — Domain risk|Some disciplines are regulated or safety-critical. Mitigation: define profile boundaries, mandatory human review, and no unsupported claims of professional certification.|
|R-05 — Android constraints|Background execution, memory, battery, storage, and process lifecycle can constrain long tasks. Mitigation: explicit execution modes, resumability, resource budgets, and supported external execution environments.|
|R-06 — Data retention ambiguity|Logs, artifacts, indexes, and session content can be confused. Mitigation: classify data types and define separate retention rules for each; preserve the bridge/relay boundary.|
|R-07 — Repository as universal boundary|Some tasks span multiple repositories or have no Git repository. Mitigation: allow an explicit workspace abstraction and multi-repository task references without weakening isolation.|


## 16.1 Decisions requiring product-owner confirmation
- DP-01 — Is every task required to belong to exactly one repository, or may a task target a workspace containing multiple repositories?
- DP-02 — What is the canonical persistence model for task metadata, artifact references, audit records, and optional saved session content?
- DP-03 — Which Git operations may be executed automatically, and which always require explicit confirmation?
- DP-04 — What is the minimum supported worker contract and versioning policy?
- DP-05 — Are local shell workers first-class in the initial release, and what sandboxing guarantees are required?
- DP-06 — Which artifact formats must be supported natively versus through external viewers/editors?
- DP-07 — What task states and terminal-state semantics are mandatory?
- DP-08 — Is multi-user collaboration a core product direction or an optional later deployment mode?
- DP-09 — Which discipline profiles should be first after software engineering?
- DP-10 — What data may be included in audit records, diagnostic exports, backups, and task portability packages?

## 16.2 Explicit assumptions
- Gitana's Android application is the user-facing product and owns its task lifecycle.
- The bridge and relay are supporting components, not the product identity.
- The Android bridge is self-contained and communicates with external components through shared contracts.
- The bridge binds to loopback only; IPv4 127.0.0.1 is mandatory and IPv6 ::1 is used when supported.
- Bridge session content is not persisted unless the user activates SAVE-SESSION-CONTENT; that feature is exclusive to the Android bridge, never the relay.
- The catalogue is a product direction and prioritization proposal, not a claim that these capabilities currently exist.

# 17. Glossary
Artifact: A file, dataset, document, model, report, or other identifiable deliverable or input.

Discipline profile: A versioned extension providing domain-specific task types, templates, formats, validators, and workflows.

Execution: A bounded attempt to perform a task or task stage using a worker.

Provenance: Information describing the origins, inputs, activities, agents, and derivation of an output.

Repository: A Git repository used as a primary workspace and organizational boundary.

Task: A structured unit of work with an objective, scope, inputs, constraints, outputs, and acceptance criteria.

Task contract: The machine-readable, versioned representation of a task and its lifecycle requirements.

Worker: A compatible executor that performs task work under Gitana-defined context and permissions.

Worker-agnostic: Not dependent on a specific worker, model, vendor, or execution provider.

Workspace: A controlled working context; it may be one repository or an explicitly configured grouping where approved.


# 18. References and research basis
These sources inform the design principles in this PCS. They are references, not a claim that Gitana conforms to or is certified against any cited standard. Full standards may require purchase or separate access.

[1] ISO 21502:2020 — Project, programme and portfolio management — Guidance on project management. Project governance, planning, delivery, and lifecycle practices. https://www.iso.org/standard/74947.html

[2] W3C PROV-DM — The PROV Data Model. A model for representing entities, activities, agents, and provenance relationships. https://www.w3.org/TR/prov-dm/

[3] NIST AI Risk Management Framework 1.0. Governance, context, measurement, risk management, documentation, and oversight for AI-enabled systems. https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.100-1.pdf

[4] ISO/IEC 42001:2023 — Artificial intelligence management system. Management-system approach to responsible AI governance and continual improvement. https://www.iso.org/standard/42001

[5] FAIR Guiding Principles for scientific data management and stewardship. Findability, accessibility, interoperability, and reuse of data. https://www.nature.com/articles/sdata201618

[6] NIST Research Data Framework. Research data lifecycle, stewardship, and reproducibility considerations. https://www.nist.gov/itl/ssd/information-systems-group/research-data-framework

[7] UN Transparency Protocol — Requirements. Protocol-first interoperability, decentralization, reuse of standards, and machine-implementable specifications. https://untp.unece.org/docs/about/Requirements/

END OF REVIEW DRAFT

