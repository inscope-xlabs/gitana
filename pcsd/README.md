# Gitana Product Capability Specification (GTN-PCS-001)

**Independent, self-contained, repository-based, worker-agnostic task execution unit**

Gitana accepts structured work, executes it through compatible workers under user-governed controls, and returns verifiable, portable outcomes.

> **Source baseline:** Split from [`Gitana_Product_Capability_Specification_v0.1.md`](../Gitana_Product_Capability_Specification_v0.1.md) into modular component documents for easier review and maintenance.

## Document Control

| Field | Value | Notes |
|---|---|---|
| Document ID | `GTN-PCS-001` | Stable specification identifier |
| Version / status | `0.1` — Review draft; not yet approved | Feedback requested before baseline approval |
| Date | 26 November 2026 | — |
| Document owner | Product owner / Gitana maintainers | To be assigned |
| Audience | Product owner, architecture, engineering, UX, security, prospective extension authors | — |
| Normative language | `MUST` / `SHOULD` / `MAY` | Binding only after approval and allocation to implementation specs |
| Scope | Product capabilities and acceptance direction | Not a detailed software design or implementation specification |
| Change control | Versioned review and approval | Changes to product identity or core contracts require explicit approval |

## How to Review This Specification

1. Review product identity, scope, principles, and tiers ([`01-vision-scope-and-tiers.md`](./01-vision-scope-and-tiers.md)) first.
2. Review the 166 numbered capabilities across Primary (`P0`–`P1` core), Secondary, and Utility families; mark each capability as accepted, changed, deferred, or rejected (`P0` is the minimum coherent foundation, not a single-release promise).
3. Review the Universal Task Contract, Worker Interoperability Contract, and Discipline Profiles ([`05-contracts-and-discipline-profiles.md`](./05-contracts-and-discipline-profiles.md)) as extension packs on a single core engine.
4. Review security, privacy, and persistence rules against the existing Android bridge specification, including the `SAVE-SESSION-CONTENT` exception ([`06-security-governance-and-nfrs.md`](./06-security-governance-and-nfrs.md)).
5. Review the phased roadmap, dependencies, risks, and open product-owner decisions ([`07-roadmap-traceability-and-decisions.md`](./07-roadmap-traceability-and-decisions.md)).

## Component Documents

| Document | Sections Covered | Focus |
|---|---|---|
| [`01-vision-scope-and-tiers.md`](./01-vision-scope-and-tiers.md) | §§ 1–3, 8, 17–18 | Executive summary, scope & principles, capability model, tiers (`T1`–`T5`), glossary, references |
| [`02-primary-capabilities.md`](./02-primary-capabilities.md) | § 4 (Groups A–F) | Primary capabilities `GTN-PRI-001` – `GTN-PRI-060` (repos, tasks, workers, artifacts, verification, security) |
| [`03-secondary-capabilities.md`](./03-secondary-capabilities.md) | § 5 (Groups G–L, `166`) | Secondary capabilities `GTN-SEC-061` – `GTN-SEC-124` & `GTN-SEC-166` (planning, research, collaboration, quality, connectivity, advanced execution) |
| [`04-utility-capabilities.md`](./04-utility-capabilities.md) | § 6 (Groups M–P) | Utility capabilities `GTN-UTL-125` – `GTN-UTL-165` (UI, configuration/admin, reporting, reliability) |
| [`05-contracts-and-discipline-profiles.md`](./05-contracts-and-discipline-profiles.md) | §§ 7, 9–10 | Universal Task Contract (UTC), Worker Interoperability Contract, Discipline Profiles (`DCP-01` – `DCP-17`) |
| [`06-security-governance-and-nfrs.md`](./06-security-governance-and-nfrs.md) | §§ 11–12, 14 | Security & governance (`SEC-01` – `SEC-12`), bridge constraints, NFRs (`NFR-01` – `NFR-14`), acceptance gates (`AC-01` – `AC-12`) |
| [`07-roadmap-traceability-and-decisions.md`](./07-roadmap-traceability-and-decisions.md) | §§ 13, 15–16 | Phased roadmap (`Phase 0` – `Phase 4`), dependency chain & traceability, risks (`R-01` – `R-07`), open decisions (`DP-01` – `DP-10`), assumptions |

## Capability Catalogue At-a-Glance

| Family | Prefix | ID Range | Groups |
|---|---|---|---|
| Primary | `GTN-PRI` | `001`–`060` | A. Repository & Workspace · B. Task Management · C. Worker Execution · D. Artifacts · E. Verification · F. Security & Governance |
| Secondary | `GTN-SEC` | `061`–`124`, `166` | G. Planning & Workflow · H. Knowledge & Research · I. Collaboration · J. Quality Intelligence · K. External Connectivity · L. Advanced Execution & Handoff |
| Utility | `GTN-UTL` | `125`–`165` | M. UI & Interaction · N. Config & Admin · O. Communication & Reporting · P. Reliability & Maintenance |
