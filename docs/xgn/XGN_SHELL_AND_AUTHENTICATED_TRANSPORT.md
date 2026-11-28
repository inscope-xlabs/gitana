# XGN-12: XGN Shell and Authenticated Transport

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-12 | **Group:** C — Transport and command surface
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-03, XGN-09 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** see section 11

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module specifies delivery of an XGN Request over an **authenticated shell or command-line channel**. A shell-capable agent runs a process that sends an authenticated request to the Gitana session surface and prints the reply. The request may be delivered to a local loopback listener on the trusted device, or to the XGN Gateway over an authenticated remote connection.

The channel is third in the selection order (XGN-09 §5.1, `XGN-DEC-001`). It is used where an agent can run a process but has no fetch tool and no connector.

In scope: the shell command surface; the agent-plane session credential and how it is presented; local loopback binding; remote authenticated delivery; the boundary between authenticated delivery to Gitana and direct provider access; the case where the shell agent runs on the same device as the runtime.

Out of scope: the Composed GET transport (XGN-10), the connector transport (XGN-11) and the copy/paste handoff transport (XGN-13); the command protocol and encoding (XGN-28); command capabilities and replay rules (XGN-29); the gateway, relay and its deployment (XGN-20); the trust model and credential classes (XGN-05); execution authority (XGN-17).

## 2. The shell transport model

A shell-capable agent runs a process that carries the same commands, the same drafts, the same grant, the same approvals and the same audit records as every other transport. Only the carrier differs.

| ID | Requirement |
|---|---|
| SH-01 | The shell transport MUST deliver requests through the shared request contract of XGN-02 §4 and MUST NOT have a private request format. |
| SH-02 | The shell transport MUST carry the session binding — `session_id`, `grant_id`, `step_id` and the session sequence number — sufficient to route the request to the correct session and step. |
| SH-03 | The shell transport MUST return the panel of XGN-02 §11 as the reply body, so an agent parses one format on every transport. |
| SH-04 | The shell transport MUST expose a status read that is non-consuming, non-advancing and rate limited (XGN-DEC-023). |
| SH-05 | The shell transport MUST NOT decide permissions, evaluate policy, capture an approval or cause execution. |
| SH-06 | The shell transport MUST NOT hold authoritative state. A client MAY hold the session credential and the last reply for the duration of a session (XGN-09 G-08). |
| SH-07 | The shell transport MUST NOT expose the trusted runtime to the agent as a general-purpose remote shell. The agent reaches the session surface and nothing else. |
| SH-08 | The shell transport MUST NOT widen a grant, skip an approval, or invalidate an approval already given (XGN-DEC-030, INV-20). |

## 3. "Curl / git" means authenticated requests to Gitana only

**`XGN-DEC-002` — the shell channel is authenticated requests to Gitana. Direct git with agent-held credentials is not a Gitana channel and MUST NOT be used.**

This is stated explicitly because the two are easily confused and the second one silently removes the entire control model.

| Property | Authenticated request to Gitana | Direct git with an agent-held credential |
|---|---|---|
| What the agent holds | An agent-plane session credential bound to one session | A long-lived provider credential |
| Where policy is evaluated | On the trusted runtime, against the grant (XGN-15) | Nowhere in XGN. The provider applies its own rules |
| Whether operator approval is required | Yes, for every mutating operation (XGN-DEC-006) | No. The write is already authorised by the credential |
| Whether the grant and its scope apply | Yes | No |
| Whether the request is audited by Gitana | Yes (XGN-25) | No. Only the provider's own logs exist |
| What happens to an out-of-scope path | Rejected before the provider is contacted (INV-03) | The provider accepts it if the credential permits |
| Whether the agent can reach content it was not granted | No | Yes, within the credential's own permissions |

| ID | Requirement |
|---|---|
| SH-09 | The shell transport MUST be used to send authenticated requests to Gitana. An agent MUST NOT use it to reach a provider directly with a credential. |
| SH-10 | An agent MUST NOT hold a provider credential, and a transport MUST NOT deliver one to the agent (INV-14, XGN-00 §4.2). |
| SH-11 | The operator MUST NOT give the agent a provider credential. Where an agent attempts provider access directly, that action is outside XGN and MUST NOT be presented to the operator as a Gitana request. |
| SH-12 | A request that enters Gitana over the shell transport MUST receive the same policy evaluation, approval requirement and audit record as the same request on any other transport. |

## 4. The shell command surface

### 4.1 Request form

The agent issues the same logical commands as every other transport — `new`, `set`, `append`, `replace`, `unset`, `batch`, `preview`, `show`, `commit`, `cancel`, `status` — defined by XGN-28. The shell transport carries them as an authenticated request rather than as query parameters.

```text
POST /gitana/nav/v1/s HTTP/1.1
Host: <runtime or gateway host>
Authorization: Bearer <agent-plane session credential>
X-Gitana-Capability: cap_<22 base64url characters>
Content-Type: application/json

{"cmd":"set","field":"path","value":"docs/notes.md","seq":8}
```

The path is the session surface defined in XGN-28 and shown in the panel contract in XGN-02 §11. The body carries the command name, the session sequence number and the command arguments, using the encoding rules of XGN-28.

### 4.2 Reply form

The reply body is the panel of XGN-02 §11: the `STATE`, `TASK`, `DRAFT`, `LAST` and `COMMANDS` blocks, in the same order and with the same semantics as on every other transport. A reply that carries an error carries the Error Contract object of XGN-02 §10 in the `LAST` block and in a machine-readable field.

| ID | Requirement |
|---|---|
| SH-13 | The process exit status MUST be 0 when the reply is a panel and non-zero when the reply carries an error. The exit status MUST NOT be the only machine-readable signal; the reply body is. |
| SH-14 | Diagnostics MAY be written to standard error, and MUST NOT contain agent-authored content, a credential or a capability. |
| SH-15 | The client MUST NOT print, log or echo the session credential or the command capability. |
| SH-16 | The client MUST NOT print a reply that carries content outside an explicit read, and MUST NOT merge a content-bearing reply into a log (XGN-DEC-027). |

### 4.3 Handling of command capabilities

A command capability is still required, is still single-use, is still bound to the session, the next sequence number and the permitted command, and is still consumed on acceptance (XGN-29). Only the carrier changes.

| ID | Requirement |
|---|---|
| SH-17 | The command capability MUST be carried in a request header or a request body field, and MUST NOT be carried in a URL. |
| SH-18 | The `COMMANDS` block presented for this transport MUST show the command templates and the field or header in which the capability is carried. It MUST NOT embed a capability in a URL. |
| SH-19 | A stale capability MUST be rejected with `LINK_EXPIRED` and a consumed capability with `LINK_CONSUMED` (XGN-29). |
| SH-20 | A command that reuses a consumed identity with different arguments MUST be recorded as a reuse event and MUST suspend the session (INV-09). |

## 5. Authentication

### 5.1 The agent-plane session credential

| ID | Requirement |
|---|---|
| SH-21 | A shell client MUST authenticate with an agent-plane session credential bound to the bound session. It MUST NOT present an operator-plane credential (XGN-05, XGN-00 §4.2). |
| SH-22 | The session credential MUST be revocable independently of the session, MUST NOT encode a provider credential, and MUST NOT be reused across sessions. |
| SH-23 | The session credential MUST be replaced when the transport changes, when the session is suspended, and when the operator revokes it (XGN-09 §6.3). |
| SH-24 | The credential MUST be sent only to the runtime or gateway host the session was bound to. A client MUST refuse to send it to any other origin, including a redirect target. |

### 5.2 Presentation

| Form | Requirement |
|---|---|
| Header | `Authorization: Bearer <session credential>`. The credential MUST NOT appear in the request URL. |
| File | The operator places the credential in a file whose path the operator chooses. The file MUST NOT be inside a version-controlled path, MUST have permissions restricted to the account that runs the client, and MUST NOT be committed. |

| ID | Requirement |
|---|---|
| SH-25 | The credential MUST NOT be placed in a URL, a query string or a path segment, because a URL is logged by proxies, servers, browsers and chat platforms (INV-14). |
| SH-26 | The credential MUST NOT be passed as a command-line argument, where it is visible in the process table and in shell history. |
| SH-27 | The credential SHOULD NOT be placed in an environment variable, which is inherited by every child process. The header form, read from a file or from a descriptor the operator opened, is preferred. |
| SH-28 | The client MUST NOT write the credential to a log, a cache, a temporary file or a crash report, and MUST NOT place it in a shell history file. |

### 5.3 Contrast with Composed GET

Composed GET is the one channel in which a credential travels in the URL, because the channel cannot carry headers (`no_headers`, XGN-09 §3.2). XGN-29 owns that capability contract and permits a short-lived, single-use, session-bound command capability — and nothing else — to appear in a URL. It is a narrow exception to the rule of INV-14, scoped to a bearer capability, not an exemption for the shell transport.

| ID | Requirement |
|---|---|
| SH-29 | The shell transport MUST NOT use the URL-carried credential exception. It has headers, so the credential travels in a header or a file. |
| SH-30 | No long-lived credential and no provider credential may appear in a URL on any transport (INV-14). |

## 6. Local loopback binding

Where the shell agent runs on the trusted device, the runtime MAY expose a session listener on the loopback interface.

| ID | Requirement |
|---|---|
| SH-31 | The listener MUST bind only to `127.0.0.1` or `::1`. It MUST NOT bind to a wildcard address, a LAN interface, or any address that is routable from another host. |
| SH-32 | The listener MUST NOT be forwarded, tunnelled, proxied, port-mapped or relayed to any other host, and the deployment MUST provide no firewall exception that exposes it. |
| SH-33 | The listener MUST use a random port drawn from `49152`–`65535`, chosen when the session is created. The port MUST NOT be fixed or well known, and MUST change when the session is re-established. |
| SH-34 | The listener MUST require the session credential on every request, including on loopback. A request without a valid credential MUST be rejected with `POLICY_DENIED`. There MUST NOT be an anonymous or local-trust mode. |
| SH-35 | The listener MUST serve the session surface and the status read only. It MUST NOT serve operator-plane routes, the grant, the policy, the approval surface, the credential store or any provider credential. |
| SH-36 | The listener MUST be terminated when the session ends, expires, is suspended or is revoked, and MUST NOT be restarted automatically with the same port. |
| SH-37 | The port MUST be advertised to the agent as part of the session binding, and MUST NOT be published in a URL that an intermediary logs. |
| SH-38 | The runtime MUST record the listener creation and its termination as audit events, carrying the port and the session identifier, and MUST NOT record the credential. |

## 7. Remote authenticated delivery

Where the shell agent does not run on the trusted device, the request MUST travel through the XGN Gateway (XGN-20) over TLS.

| ID | Requirement |
|---|---|
| SH-39 | A remote shell delivery MUST terminate at the gateway, which routes, rate-limits and bounds the request. The gateway MUST NOT be treated as an authority (XGN-00 §5). |
| SH-40 | The agent plane MUST NOT be able to reach the trusted runtime's private address directly from another host. |
| SH-41 | The remote path MUST use the same agent-plane credential, presented in a header, and the same rules as §5 apply to it. |
| SH-42 | The operator MUST confirm the remote path as part of confirming the channel at session creation (XGN-09 §5.2). |
| SH-43 | A request delivered over the remote path MUST receive the same policy decision, the same approval requirement and the same audit record as a request delivered on any other transport. |

## 8. The shell agent on the same device as the runtime

Running on the trusted device does not move the trust boundary. The agent remains an agent-plane principal.

| ID | Requirement |
|---|---|
| SH-44 | The runtime MUST NOT expose the session, grant, policy, approval, execution and audit stores, the provider credential store, or the signing key to the agent's process identity or to any process the agent can start. |
| SH-45 | The operator plane MUST remain independently authenticated. A local process MUST NOT be treated as an operator because it can read local files (XGN-00 §4.2). |
| SH-46 | The loopback listener MUST expose only the agent-plane surface, as required by SH-35. |
| SH-47 | The agent MUST NOT run the provider's own client against a provider credential held by the device. Doing so is the operator's own action, and an agent performing it is outside XGN and outside the grant, the approval and the audit. |
| SH-48 | The operator MUST NOT make a provider credential readable to the agent's process identity or to any process the agent can start. |

## 9. What the shell transport does not change

| Element | Effect of the shell transport |
|---|---|
| The grant | Unchanged. The grant is fixed for the life of the session (XGN-04). |
| The policy decision | Unchanged. The transport is not an input to the policy engine (XGN-02 §4.1). |
| The approval requirement | Unchanged. Every mutating operation requires operator approval on this transport (XGN-DEC-006). |
| The approval itself | Unchanged, and bound to the same request digest (XGN-31). |
| The execution | Unchanged. The same provider adapter, preconditions and outcome semantics apply (XGN-17). |
| The draft and its parts | Unchanged. A draft is runtime state (XGN-07). |
| The session sequence number | Unchanged. The same next number is issued and consumed (XGN-28). |
| The audit record | Unchanged, apart from the `transport` attribute value (XGN-25). |
| The idempotency rule | Unchanged. An exact retry inside the 90 second window returns the original reply and produces no second effect (XGN-DEC-007). |

| ID | Requirement |
|---|---|
| SH-49 | The shell transport MUST NOT obtain a different result from the runtime than any other transport would obtain for the same request. |
| SH-50 | A request that requires approval on this transport MUST wait for the operator. The shell transport MUST NOT offer a path that completes without an approval record (INV-01). |

## 10. Decisions recorded

| ID | Subject | How this module records it | Owner |
|---|---|---|---|
| `XGN-DEC-002` | "Curl / git" meaning | §3 states the decision and the consequences: authenticated requests to Gitana only; direct git with agent-held credentials is not a Gitana channel | XGN-12 |
| `XGN-DEC-001` | Channel order | The shell channel is third in the order, after Connector and Composed GET | XGN-09 |
| `XGN-DEC-006` | Approval on mutating commits | §3, §7 and §9 keep the approval requirement identical on this transport | XGN-16 |
| `XGN-DEC-007` | Idempotency window and caller identity | §4.3 and §9 apply the 90 second window to shell commands | XGN-29 |
| `XGN-DEC-030` | Transport fallback | §5.1 requires the credential to be replaced when the transport changes, without invalidating an approval | XGN-09 |
| `XGN-DEC-023` | Status polling | §2 requires a non-consuming, non-advancing, rate-limited status read | XGN-28, XGN-23 |

No new decision identifier is introduced by this module. The module-local rules are carried by the `SH-nn` clauses above.

## 11. Open decisions

| ID | Question | Owner |
|---|---|---|
| OD-12-01 | The gateway authentication profile for an agent-plane shell principal — mutual authentication, credential lifetime and rotation — is not settled. | XGN-20, XGN-05 |
| OD-12-02 | Whether the local loopback listener may be replaced by a device-local named pipe or a Unix domain socket is not settled. | XGN-12 |
| OD-12-03 | The maximum size of a shell-carried request body is not settled. The Composed GET ceiling of 2048 bytes per command does not apply here, but no larger figure is fixed here. | XGN-20, XGN-23 |

## 12. Related specifications

- **Depends on:** XGN-01, XGN-02, XGN-03, XGN-09.
- **Referenced by:** XGN-09, XGN-15, XGN-16, XGN-17, XGN-20, XGN-24, XGN-26.
- **Sibling:** XGN-10 owns the URL-carried capability and its narrow exception to INV-14; XGN-29 owns that capability contract; XGN-28 owns the command names and encoding this module carries; XGN-23 owns the error code set; XGN-20 owns the gateway and relay.
- **Predecessor material:** the *Gitana Surface Map (Composed GET): Proposal* (27 November 2026), which listed a "curl / git" channel and left its meaning open; and the *Navigation Transport Specification* (0.1-draft, 25 November 2026), which recorded the constraint that some agents cannot set request headers and cannot run `curl`. Their claims and open decisions remain proposals.
- **Out of scope:** every rule listed in section 1, and the direct provider access that section 3 excludes.
