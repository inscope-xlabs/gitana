# XGN-22: XGN Result Reconciliation and Recovery

**Status:** Proposed | **Version:** 1.0 | **Date:** 27 November 2026
**Module ID:** XGN-22 | **Group:** E — Relay and reliability
**Owner:** Gitana Architecture
**Depends on:** XGN-01, XGN-02, XGN-21 | **Supersedes:** none
**Conformance:** XGN-26 | **Open decisions:** none outstanding at this level

**Normative language:** MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as in RFC 2119.

## 1. Purpose and scope

This module owns **reconciliation and recovery**: the behaviour of the system when the result of a dispatched operation is not known, and the behaviour when a multi-step workflow fails part way through.

The governing rule is `INV-16`: an uncertain outcome is reconciled against authoritative state before any further side effect is attempted. Blind re-execution is forbidden. The reasoning is simple and is not negotiable. A dispatch that is repeated after an unknown outcome is not a retry; it is a second operation, chosen without the operator's knowledge and possibly without the operator's approval. `create_branch` and `create_pull_request` are the worst cases: a repeated `create_branch` can collide, and a repeated `create_pull_request` can produce a duplicate pull request.

**In scope:** the detection of each distinct uncertainty, the reconciliation procedure for each, the reconciliation flow, idempotency and the window that bounds it, partial workflow failure, what the operator is told, and the suspension that holds a session while a recovery is unresolved.

**Out of scope:** the state transitions themselves (XGN-21 owns them), the error codes (XGN-23), the provider adapter contracts (XGN-18), the GitHub operation catalogue and its preconditions (XGN-19), the audit event contents (XGN-25), and the retention of reconciliation records (XGN-25).

## 2. Recovery principles

| ID | Rule |
|---|---|
| RC-01 | The trusted runtime owns reconciliation. Only the runtime may read provider state for the purpose of resolving an outcome. |
| RC-02 | A reconciliation read MUST be a read-only provider operation. It MUST NOT create, modify or delete provider state (section 6). |
| RC-03 | A reconciliation MUST NOT dispatch an operation. There is no code path from the recovery machine to the execution machine. |
| RC-04 | A reconciliation MUST NOT consume an approval. An approval authorises one dispatch, and the dispatch already happened or never happened. |
| RC-05 | Reconciliation MUST conclude with a definitive outcome or with an explicit escalation to the operator. A recovery activity MUST NOT be left open indefinitely. |
| RC-06 | The runtime MUST record every reconciliation read, its result and its conclusion as audit events (XGN-25). |
| RC-07 | The agent MUST NOT be able to trigger a reconciliation by re-sending a command. The agent MAY re-read state; the runtime decides whether that constitutes an unresolved outcome. |
| RC-08 | Every conclusion MUST be traceable to a provider read or to the operator's recorded decision. A conclusion with neither is not a conclusion. |

## 3. Entering recovery

| ID | Rule |
|---|---|
| RC-09 | The request machine enters `OUTCOME_UNKNOWN` when a dispatched operation produces no definitive result: a timeout, an absent response, a dropped connection, a crash during dispatch, or a provider response that cannot be interpreted (XGN-21 SM-39). |
| RC-10 | The request machine enters `RECONCILING` when the runtime begins reconciliation. The session remains in `WAIT` throughout (XGN-21 §8). |
| RC-11 | An operation that the provider has definitively refused is `FAILED`, not `OUTCOME_UNKNOWN`. A definitive refusal needs no reconciliation. |
| RC-12 | A request that was never dispatched needs no reconciliation. It is closed as `FAILED` with the runtime's own record as evidence (section 4, case A). |
| RC-13 | Reconciliation MUST NOT begin while an approval is pending. A request awaiting approval has had no side effect, so there is nothing to reconcile. |

## 4. The distinct uncertainty cases

Technical Review B §3.5 requires the recovery subsystem to distinguish six cases. This table is that distinction. Each case has a detection method, a reconciliation procedure, the outcome states it can reach, and whether the operator must review it.

| Case | Detection method | Reconciliation procedure | Outcome states | Operator review |
|---|---|---|---|---|
| **A. Request accepted but not executed** | The durable execution record is still `PENDING`, or the runtime restarted between writing the record and the dispatch attempt (XGN-21 SM-31, SM-45). | No provider read is needed to establish that nothing happened: the runtime's own record establishes it. The runtime MUST perform one confirmation read to establish that the effect is absent, then close the request. | `FAILED` | No. The step stays current and the agent rebuilds the request. |
| **B. Request dispatched but no response received** | The execution record is `RUNNING` and the provider call timed out at 30 seconds, or the connection dropped, or the runtime crashed mid-dispatch. | Read provider state for the operation's identifier and compare it with the expected post-condition. | `COMPLETED`, `FAILED`, or `OUTCOME_UNKNOWN` | Only if the read cannot identify the operation (case F). |
| **C. Provider operation succeeded but the acknowledgement was lost** | The reconciliation read finds the expected effect present. | No further read is needed. The runtime MUST record the provider reference and the observed post-condition, and close the request as successful. | `COMPLETED` | No. |
| **D. Provider operation failed** | The provider returned a definitive error, or the reconciliation read establishes that the effect is absent and no partial effect exists. | Confirm by read that no partial effect exists, then record the failure with the provider's stable error code. | `FAILED` | No. A provider conflict is a rejection, not an incident. |
| **E. Provider operation succeeded but verification failed** | The provider reported success, but the post-condition check disagrees: the file digest differs, the branch head is not the expected value, or the pull request is not in the expected state. | Read the authoritative object again. Establish whether the effect is present with a different value than expected, or absent. | `FAILED`, with `PROVIDER_CONFLICT` | No, unless the operator wishes to decide. The runtime MUST NOT re-dispatch. |
| **F. The operation result cannot be determined** | Every read is inconclusive: the identifier is not unique, the state has moved on, or the reconciliation window expires with the outcome still ambiguous. | Escalate. Present the operator with what is known, what was checked and what they may decide. | `OUTCOME_UNKNOWN` | **Yes.** The session moves to `SUSPENDED`. |

**RC-14**: Cases A, D and E are resolved without an operator decision, because the evidence is decisive. Cases B and C are resolved by a read. Only case F requires the operator.

**RC-15**: The runtime MUST classify the case before it acts, and MUST record the classification as `recovery.classified` (XGN-21 §8). An unclassified uncertainty MUST be treated as case F.

### 4.1 Identifying an operation

**RC-16**: To identify a dispatched operation, the runtime MUST use an identifier that is unique within the repository and that the operation is designed to produce.

| Operation | Identifying evidence | Read used |
|---|---|---|
| `create_branch` | The branch name, which the grant reserves for the task. | Read the branch ref and its head digest. |
| `write_file` | The file path and the expected head digest bound by the envelope. | Read the file blob digest at the expected head, and the head digest itself. |
| `create_pull_request` | The head and base pair together with the pull request title. | List the open pull requests for the base and match head, base and title. |

**RC-17**: Where two candidate objects match the identifying evidence, the operation is not identifiable and the runtime MUST escalate to case F. It MUST NOT pick the most recent, and it MUST NOT pick the closest match.

**RC-18**: An identifier that is not unique MUST NOT be relied on. Where an operation cannot be identified uniquely, the operation profile in XGN-19 MUST define a stronger identifier, and the absence of one is a defect in that profile.

## 5. The reconciliation flow

The flow below is the same flow as the recovery diagram in Technical Review B §3.5, expressed as text so that it is readable without a renderer.

```text
commit submitted
      |
      v
operator approval                     (XGN-16; every mutating operation)
      |
      v
execution begins                      (durable record written before dispatch)
      |
      v
definitive result?
      |--- yes, success  --> record success  --> session RESULT
      |--- yes, failure  --> record failure  --> session RESULT
      |--- no            --> enter reconciliation
                                  |
                                  v
                          read provider state        (read-only)
                                  |
                                  v
                          operation identifiable?
                                  |--- yes --> reconcile the request
                                  |                  |
                                  |                  v
                                  |           resume the workflow
                                  |                  |
                                  |                  v
                                  |           session RESULT
                                  |
                                  |--- no  --> operator review
                                                  |
                                                  v
                                          suspend the session
```

| Step | Action | Owner | Evidence | Notes |
|---|---|---|---|---|
| 1 | The commit is accepted and the envelope is immutable. | Runtime | `request.received` | The draft is discarded. |
| 2 | The operator approves the request digest. | Operator | `request.approved` | No approval means no dispatch. |
| 3 | The execution record is made durable, then dispatch begins. | Runtime | `execution.started` | The record is durable before the dispatch attempt. |
| 4 | The provider returns a result, or does not. | Provider | — | A missing result is `OUTCOME_UNKNOWN`, not `FAILED`. |
| 5 | On an unknown result, the request enters `RECONCILING` and the session enters `WAIT`. | Runtime | `recovery.started` | No dispatch may follow this point. |
| 6 | The runtime reads provider state. Every read is read-only. | Runtime | `recovery.reconciled` | The read set is bounded by the reconciliation window. |
| 7 | The runtime decides whether the operation is identifiable. | Runtime | `recovery.classified` | Unidentifiable means case F. |
| 8 | When identifiable, the runtime reconciles the request against the read result and resumes the workflow. | Runtime | `recovery.resolved` | The request reaches `COMPLETED` or `FAILED`. |
| 9 | When not identifiable, the runtime escalates to operator review and suspends the session. | Runtime, then Operator | `recovery.operator_review`, `recovery.resolved` | The session moves to `SUSPENDED`. |

**RC-19**: Steps 6 to 8 MUST NOT be reachable from a path that dispatches. The reconciliation subsystem and the execution subsystem MUST be separate components or separately guarded code paths, so that a defect in one cannot cause a dispatch.

**RC-20**: The reconciliation window is 15 minutes from the first read (XGN-21 SM-40). When it expires, the outcome is case F and the session is suspended.

**RC-21**: A single reconciliation read times out after 10 seconds (XGN-21 SM-41). A read that times out is inconclusive, not a negative result.

## 6. Reconciliation reads are read-only

| ID | Rule |
|---|---|
| RC-22 | A reconciliation read MUST use a provider operation that does not change provider state. It MUST NOT create, update or delete a branch, file, pull request, comment, label or release. |
| RC-23 | A reconciliation MUST NOT use an operation that can be read as an invocation. It MUST NOT open a pull request to observe one, and it MUST NOT push an empty commit. |
| RC-24 | A reconciliation MUST NOT widen the grant, and MUST NOT read outside the granted repository, branch or directory. A read outside the grant is a policy failure, not a reconciliation. |
| RC-25 | A reconciliation MUST NOT use the agent's credentials. It MUST use the runtime's provider credentials, under the same restrictions as an authorised execution (XGN-17, XGN-18). |
| RC-26 | Reconciliation reads MUST be rate limited by the provider adapter. A reconciliation MUST NOT become a scan of the provider's contents. |
| RC-27 | The provider MUST NOT be asked to perform the operation again under any circumstance, including a read that the provider documents as creating state. |

**RC-28**: Where an operation has no read-only counterpart that can identify it, the runtime MUST escalate to case F. It MUST NOT fall back to re-dispatching.

## 7. Idempotency

| ID | Rule |
|---|---|
| RC-29 | An exact retry inside the idempotency window of 90 seconds (`XGN-DEC-007`) returns the original reply and produces no second effect (INV-08). |
| RC-30 | The retry MUST be byte-identical: the same command, the same parameters, the same sequence number and the same body. A retry with any change is not a retry. |
| RC-31 | Outside the window, a re-use of a consumed command capability is a replay. It MUST be recorded as `LINK_REUSE` and MUST suspend the session (INV-09). |
| RC-32 | After the idempotency window, an agent that has lost a reply MUST NOT re-send the command. It MUST read authoritative state with `status` and act on what it finds. |
| RC-33 | A lost reply on a mutating command is recovered by reading provider state through the recovery subsystem, not by re-sending. |
| RC-34 | The gateway's idempotency cache MUST NOT hold a content-bearing reply (XGN-DEC-027, XGN-20 §6). A lost reply on a content-bearing command is therefore recovered by reading authoritative state, because the original reply cannot be reconstructed by the gateway. |
| RC-35 | Where the gateway and the runtime disagree about whether a command was accepted, the runtime's record is authoritative, and the probe `PRB-26` in XGN-30 MUST pass (XGN-20 §3). |

**RC-36**: The idempotency window bounds the gateway's ability to return an original reply. It does not bound the runtime's ability to reconcile. A request whose outcome is unknown remains reconcilable for the life of the request record, which is 3600 seconds (XGN-21 SM-38).

## 8. Partial workflow failure

A grant contains an ordered list of steps (XGN-04). A workflow fails partially when one step succeeds and a later step fails.

| ID | Rule |
|---|---|
| RC-37 | A step that completed successfully is complete. A later failure MUST NOT un-complete it, and MUST NOT cause it to be dispatched again (XGN-DEC-029). |
| RC-38 | The session MUST remain at the failed step. The steps before it stay marked complete; the steps after it stay pending. |
| RC-39 | The failed step is recoverable. The agent MAY rebuild the request for that step and commit it again. That is a new request with a new request identifier and a new approval, not a retry of the failed one. |
| RC-40 | A step that failed with an uncertain outcome MUST be recovered through reconciliation before any new request for that step is composed. Otherwise the workflow may contain a duplicate effect. |
| RC-41 | There is no rollback and no compensation. Gitana does not undo a completed step. The audit trail records what happened. |
| RC-42 | The panel MUST show, for each granted step, whether it is complete, current, pending, failed or uncertain. |
| RC-43**: The probe `PRB-23` in XGN-30 MUST pass: completed steps are preserved and the remaining steps are recoverable. |

**RC-44**: An agent that reaches a failed step MUST NOT re-compose earlier steps. The capability description and the panel expose the step list, so the agent knows which steps are already complete.

## 9. Operator-visible recovery

While a recovery is unresolved, the operator MUST be able to see what is uncertain and what they may decide.

| Information | Rule |
|---|---|
| What is uncertain | The operation, the target, the time the dispatch was attempted, and the state the request is in. |
| What is known | The provider reads already performed and what each one showed. |
| What is being checked | The reconciliation read that is in progress or the next read that will be made. |
| What the operator may decide | Confirm that the operation completed, confirm that it did not, cancel the request, or revoke the session. |
| What the operator may not do | Approve the request, widen the scope, or cause a re-dispatch. The request was already dispatched; an approval would authorise a second operation. |

**RC-45**: The operator's decision MUST be recorded as an approval-plane event against the request identifier, and MUST be bound to the provider read that informed it.

**RC-46**: The operator's view MUST NOT be derived from repository content or agent-authored content. The approval summary is generated by the runtime from the envelope (XGN-16).

**RC-47**: The agent MUST NOT see the provider's raw response text. The agent sees the outcome state, a sanitised reason code and the remediation defined in XGN-23.

## 10. Suspension during recovery

The conditions that suspend a session are listed in XGN-21 SM-46. This section defines what the agent sees when the suspension is a recovery suspension.

| ID | Rule |
|---|---|
| RC-48 | The agent's view is a `WAIT` panel carrying the session state, the wait cause, the reason code, the fact that a reconciliation is in progress, and the fact that the operator has been notified. |
| RC-49 | The agent MUST NOT be told the provider's raw error text, the provider's internal identifiers beyond a provider reference, or the content of any file involved. |
| RC-50 | The agent MUST be told what it may do: read status, wait, and ask the operator. It MUST NOT be told that it may re-commit the same operation, because the operation may have taken effect. |
| RC-51 | While the session is suspended, the agent-plane commands are limited to reads. `status` remains legal (XGN-21 §12). |
| RC-52 | The session leaves the suspension by an operator action: resume, or revoke. The agent MUST NOT cause either (INV-01). |
| RC-53 | A session suspended for recovery holds no open draft. The agent MUST rebuild from the current step, and the rebuild MUST be a new request. |

## 11. Decisions recorded

| ID | Decision | Source |
|---|---|---|
| `XGN-DEC-029` | Partial workflow failure: completed steps are preserved; a failed step leaves the session at that step; recovery is by read, never by blind repetition. | `XGN-DEC-029`, owned by XGN-22 |
| `XGN-DEC-027` | The gateway MUST NOT cache a reply that carries agent-authored content, so a lost content-bearing reply is recovered by reading authoritative state. | `XGN-DEC-027`, owned by XGN-20 |
| `XGN-DEC-007` | The idempotency window is 90 seconds; the caller identity is the session capability plus the exact link and body digest. | `XGN-DEC-007`, owned by XGN-29 |

## 12. Related specifications

- **Depends on:** XGN-01 (identifiers and the decision register), XGN-02 (the shared contracts, the execution contract and the event shape), XGN-21 (the request, execution and recovery machines, and the suspension rules).
- **Referenced by:** XGN-06, XGN-14, XGN-17, XGN-18, XGN-19, XGN-20, XGN-23, XGN-25, XGN-26, XGN-28, XGN-29.
- **Sibling:** XGN-23 owns the error codes an agent sees after a recovery concludes; XGN-25 owns the retention of reconciliation records.
- **Predecessor material:** the recovery flow diagram and the six distinct uncertainty cases in Technical Review B §3.5, the recovery requirements of Technical Review A item 15, and the error-recovery table of the *Gitana Surface Map (Composed GET): Proposal*. These are restated here as XGN-native requirements owned by this module.
