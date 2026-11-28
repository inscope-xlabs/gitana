# XGN-18: XGN External Service Adapters

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-18 | **Group:** D — Policy, approval, and execution
**Owner:** Gitana Architecture
**Depends on:** XGN-02, XGN-15, XGN-17, XGN-19, XGN-23 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** EA-OD-01, EA-OD-02 (section 1.3)

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

### 1.1 Purpose

This module defines the provider adapter contract: the boundary between the provider-neutral XGN pipeline and a provider's own API. It specifies the adapter's responsibilities, the least privilege each adapter holds, the rules that keep provider-specific behaviour from leaking into generic navigation, policy or lifecycle rules, the credential handling inside the adapter, the procedure for adding a provider, and the read-only capability the adapter must expose for reconciliation.

### 1.2 In scope and out of scope

| In scope | Out of scope |
|---|---|
| The adapter contract and its result shape | The policy decision and the check set — XGN-15 |
| Adapter responsibilities, including error normalisation, rate limits and pagination | The approval decision — XGN-16 |
| Least privilege and credential handling inside the adapter | The dispatch, the pre-dispatch checklist and the execution record — XGN-17 |
| Provider isolation rules and the procedure for adding a provider | The operation catalogue for one provider — XGN-19 |
| The read-only reconciliation surface | The error taxonomy itself — XGN-23 |

### 1.3 Open decisions

| ID | Question | Interim position |
|---|---|---|
| EA-OD-01 | Whether one adapter may hold credentials for several repositories | No. One adapter holds one credential scope, and the scope names the granted repositories. |
| EA-OD-02 | Whether an adapter may be shared between sessions of different operators | No. An adapter instance is bound to one runtime and one credential scope. |

## 2. The provider adapter contract

### 2.1 Position

| ID | Requirement |
|---|---|
| EA-01 | A provider adapter maps one approved XGN operation onto a provider API call, and maps the provider's response back into an XGN result. |
| EA-02 | The adapter MUST be invoked only by the trusted runtime, with an envelope that has passed the pre-dispatch checklist (XGN-17). |
| EA-03 | The adapter MUST NOT evaluate policy, MUST NOT request or interpret an approval, and MUST NOT decide scope. It receives an envelope that is already permitted. |
| EA-04 | The adapter MUST NOT widen an argument, substitute an argument, or supply a default that the envelope does not carry. |
| EA-05 | The adapter MUST NOT retain the envelope, the arguments or the provider response after the call returns, except for the values it returns in the result. |

### 2.2 The result shape

| Field | Requirement |
|---|---|
| `operation` | The XGN operation that was performed. |
| `provider` | The provider identifier, for example `github`. |
| `status` | `SUCCESS`, `FAILURE`, `REJECTED_BY_PROVIDER` or `OUTCOME_UNKNOWN` (XGN-02 section 8). |
| `provider_reference` | The provider's own identifier for the affected object, for example `refs/heads/agent/task-42`. |
| `result_digest` | The digest of the canonical result body, computed with the algorithm of XGN-DEC-003. |
| `details` | Sizes, counts, digests and timestamps. It MUST NOT contain file content, a pull request body or a provider response body. |
| `error` | The normalised error object (XGN-02 section 10), present when the status is not `SUCCESS`. |

| ID | Requirement |
|---|---|
| EA-06 | The result shape MUST be provider-neutral. The same field names apply to every adapter. |
| EA-07 | The result MUST NOT contain a credential, a capability, an endpoint URL with embedded credentials, or repository content. |
| EA-08 | The result MUST be sufficient for the runtime to build the audit event and the agent-visible outcome without re-reading the provider. |

## 3. Adapter responsibilities

### 3.1 Request translation

| ID | Requirement |
|---|---|
| EA-09 | The adapter MUST translate the normalised arguments into exactly one provider API call, using the mapping declared in the provider profile (XGN-19). |
| EA-10 | The translation MUST NOT require the caller to know provider field names, endpoint paths or verbs. Those are adapter-internal. |
| EA-11 | The adapter MUST bind the provider request to the envelope digest, so that a request cannot be re-pointed after translation. |

### 3.2 Response normalisation

| ID | Requirement |
|---|---|
| EA-12 | The adapter MUST map the provider response onto the result shape of section 2.2. |
| EA-13 | The adapter MUST return the provider's own identifier for the affected object. It MUST NOT invent one, and it MUST NOT substitute a value that the provider did not return. |
| EA-14 | The adapter MUST compute the result digest over the canonical result body and MUST return the 32 hexadecimal characters exposed for confirmation (XGN-DEC-003). |

### 3.3 Error normalisation

| ID | Requirement |
|---|---|
| EA-15 | The adapter MUST map every provider error response onto the XGN error taxonomy (XGN-23), with the `code`, `category`, `retryable`, `message` and `remediation` fields of the Error Contract (XGN-02 section 10). |
| EA-16 | The adapter MUST NOT pass provider text through verbatim. The `message` MUST be written by Gitana. The provider's status code MAY be recorded in `details` as a number. |
| EA-17 | The adapter MUST NOT leak provider diagnostics that may contain repository content, a credential, a token or an internal identifier. |
| EA-18 | A provider error that cannot be mapped MUST be reported as `PROVIDER_UNAVAILABLE` with `retryable` false, and MUST NOT be reported as a success. |

### 3.4 Rate limits and secondary rate limits

| ID | Requirement |
|---|---|
| EA-19 | The adapter MUST detect a provider rate-limit response and a provider secondary rate-limit response, and MUST map them to the `RATE_LIMITED` code with the provider's retry interval in seconds. |
| EA-20 | The adapter MUST NOT retry a mutating operation, in any circumstance, including a rate-limit response. |
| EA-21 | A rate-limit response that indicates the request was not processed MAY be reported as `FAILURE` with `RATE_LIMITED`. A response that leaves the outcome unclear MUST be reported as `OUTCOME_UNKNOWN`. |
| EA-22 | The retry policy for read operations is owned by XGN-23. The adapter MUST NOT retry more often than that policy allows. |

### 3.5 Pagination

| ID | Requirement |
|---|---|
| EA-23 | An adapter performing a list operation MUST follow the provider's pagination. It MUST return at most 10 pages and at most 1000 items. |
| EA-24 | When the limit is reached, the adapter MUST set `details.truncated` to true. It MUST NOT return a partial set silently. |
| EA-25 | A list operation MUST NOT be used to enumerate anything outside the granted scope. The adapter MUST filter the result to the granted scope before returning it (XGN-15). |

### 3.6 Retry semantics

| ID | Requirement |
|---|---|
| EA-26 | The adapter MUST NOT retry a mutating operation. A retry of a mutating operation risks a second side effect. |
| EA-27 | A read operation MAY be retried at most twice on a transport failure, with a backoff of 1 second and then 4 seconds. |
| EA-28 | The adapter MUST NOT retry on a precondition failure, an authorisation failure, a scope failure or a validation failure. |
| EA-29 | Every retry MUST be recorded as an audit event naming the attempt number and the delay (XGN-25). |

## 4. Least privilege per adapter

| ID | Requirement |
|---|---|
| EA-30 | Each adapter MUST declare an operation allowlist: the set of XGN operations it may execute. A dispatch of an operation outside that set MUST be refused before the provider is contacted. |
| EA-31 | Each adapter MUST hold its own credential scope, restricted to the granted repositories and to the permissions its allowed operations need. |
| EA-32 | The adapter's credential scope MUST NOT exceed the session grant, and the provider-side restriction MUST be verified when the credential is created or rotated (XGN-17 section 3). |
| EA-33 | An adapter MUST NOT be able to reach a repository outside the grant. This is enforced twice: by the provider-side credential scope and by the runtime-side scope check before the call. |
| EA-34 | One provider's failure MUST NOT affect another provider's adapter, another session's execution, or the runtime's ability to evaluate policy. A provider outage is contained in the adapter. |
| EA-35 | A credential failure in one adapter MUST NOT disable other adapters. The failing session is suspended and the operator is notified (XGN-17 section 3). |

## 5. Provider preconditions and the operation profile

| ID | Requirement |
|---|---|
| EA-36 | A provider profile (XGN-19) declares preconditions in provider-neutral terms. The adapter binds each precondition to the provider mechanism that enforces it. |
| EA-37 | A precondition MUST be implemented as a real comparison against current provider state, and MUST be evaluated immediately before the mutating call. |
| EA-38 | A precondition mismatch MUST be reported as `PRECONDITION_FAILED`. It MUST NOT be reported as a warning, and the operation MUST NOT proceed with the mismatched value. |
| EA-39 | A precondition check that cannot be completed because provider state is unavailable MUST be reported as `OUTCOME_UNKNOWN` for a mutating operation, and the operation MUST NOT proceed. |
| EA-40 | The provider-specific mechanism behind a precondition, such as a blob identifier comparison or an open pull request listing, is adapter-internal and MUST NOT appear in the envelope, the policy decision or the approval screen. |

## 6. Provider isolation

| ID | Requirement |
|---|---|
| EA-41 | Provider-specific behaviour MUST be confined to the adapter and to the provider profile (XGN-DEC-046). |
| EA-42 | The following MUST NOT be provider-specific: the error shape and the error taxonomy; the request envelope and its field names; the digest algorithm; the audit event shape; the policy decision shape; the approval record shape; the execution record shape; the transport framing. |
| EA-43 | Operation names carried in an envelope MUST be XGN-level names. A provider API path, an endpoint identifier or a provider-specific verb MUST NOT appear as an operation name. |
| EA-44 | An existing XGN operation name MUST NOT be redefined by a second provider with different semantics. A provider profile declares which XGN operations it supports and how they map; it does not rename them. |
| EA-45 | A provider-specific failure reason MUST be mapped to a stable XGN reason code before it leaves the adapter. |
| EA-46 | The capability description (XGN-03) MUST be generated from the operation catalogue and the grant, so that an operation absent from the current provider's profile MUST NOT be advertised. |

## 7. Credential handling inside the adapter

| ID | Requirement |
|---|---|
| EA-47 | The adapter holds the credential. The credential is injected by the trusted runtime at dispatch time and MUST NOT be retained between dispatches. |
| EA-48 | The adapter MUST NOT be reachable from the gateway, from a transport adapter or from the agent plane, directly or indirectly. |
| EA-49 | The adapter MUST redact the `Authorization` header, any credential-bearing query parameter and any credential-bearing URL fragment from every diagnostic it emits. |
| EA-50 | A diagnostic emitted by an adapter MUST NOT contain repository content. It MAY contain identifiers, sizes, counts, digests, timings and status codes. |
| EA-51 | The operator MUST be able to rotate and revoke the credential the adapter holds (XGN-17 section 3). Revocation MUST take effect before the next dispatch. |
| EA-52 | An adapter MUST NOT retry with a cached credential after an authentication failure. |

## 8. Adding a provider

### 8.1 What must be added

| ID | Item |
|---|---|
| EA-53 | A provider adapter implementing the contract in section 2, with the responsibilities of section 3. |
| EA-54 | A provider operation profile in the form of XGN-19, declaring the supported operations, their arguments, preconditions, success criteria, result mapping and failure semantics. |
| EA-55 | The adapter's operation allowlist and its least-privilege credential scope declaration. |
| EA-56 | The mapping from the provider's error responses to the XGN error taxonomy. |
| EA-57 | The rate-limit, pagination and retry handling for that provider. |
| EA-58 | Positive and negative test vectors for the adapter and the profile (XGN-02 VC-08, XGN-26). |
| EA-59 | A provider isolation test demonstrating that a failure in this provider does not affect another provider or another session. |
| EA-60 | A reconciliation read for each mutating operation the provider supports (section 9). |

### 8.2 What must not change

| ID | Item |
|---|---|
| EA-61 | The request envelope, the canonical form and the digest algorithm. |
| EA-62 | The error shape, the error taxonomy and the audit event shape. |
| EA-63 | The policy decision shape, the check set and the scope rules. |
| EA-64 | The approval record, the approval screen layout and the dispatch checklist. |
| EA-65 | The session, grant, draft and request lifecycle, and the state machine. |
| EA-66 | The transport contracts and the transport selection rules. |
| EA-67 | The semantics of every operation name already used by an existing provider. |

| ID | Requirement |
|---|---|
| EA-68 | Adding a provider MUST NOT require a change to any other module's normative text. If one is required, the addition is out of the scope of the provider abstraction and MUST be raised as a specification change (XGN-02 VC-06). |

## 9. The reconciliation surface

| ID | Requirement |
|---|---|
| EA-69 | The adapter MUST expose a read-only reconciliation surface that, given the operation and the provider reference of a request, returns the current provider state sufficient to decide whether the operation took effect. |
| EA-70 | The reconciliation surface MUST NOT create, modify or delete anything. It MUST expose no mutating method. |
| EA-71 | The reconciliation surface MUST be restricted to the granted scope, and MUST NOT be used to enumerate anything outside it. |
| EA-72 | The reconciliation surface MUST be reachable only by the trusted runtime. It MUST NOT be exposed to the agent plane as a distinct capability. Reads the agent needs are performed through the ordinary read operations (XGN-19). |
| EA-73 | Every reconciliation read MUST be recorded as a distinct audit event naming the request it is reconciling (XGN-22, XGN-25). |
| EA-74 | A reconciliation read MUST NOT be used to obtain content for display to the agent. It establishes an outcome, and its result is reduced to identifiers, counts and digests. |

## 10. Decisions recorded

| ID | Subject | Decision | Owner |
|---|---|---|---|
| `XGN-DEC-003` | Digest algorithm | SHA-256 over the canonical envelope. Full digest held internally; 32 hex characters (128 bits) exposed for confirmation. | XGN-31 |
| `XGN-DEC-029` | Partial workflow failure | Completed steps are preserved. A failed step leaves the session at that step. Recovery is by read, never by blind repetition. | XGN-22 |
| `XGN-DEC-046` | Provider isolation | Provider-specific behaviour is confined to the provider adapter and the provider profile. The envelope, digest, error shape and event shape are provider-neutral. | XGN-18 |

## 11. Related specifications

- **Depends on:** XGN-02 (Provider Adapter Contract, Error Contract), XGN-15 (scope and policy), XGN-17 (dispatch and credential custody), XGN-19 (provider profile), XGN-23 (error taxonomy and retry policy).
- **Referenced by:** XGN-17, XGN-19, XGN-22, XGN-23, XGN-25, XGN-26, XGN-27.
- **Predecessor material:** the separation of transport, orchestration and execution recommended in technical review B section 3.1, and the operation-specific conflict handling in technical review B section 3.5.
- **Out of scope:** the policy decision, the approval decision, the dispatch checklist and the provider-specific operation catalogue of a single provider.
