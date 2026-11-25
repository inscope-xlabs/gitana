package com.inscopexlabs.gitana.gitbridge.contracts.audit

import com.inscopexlabs.gitana.gitbridge.contracts.authz.TransportKind

/** Audit Event Contract (spec 11). Never carries file content, credentials or full bodies. */
data class AuditEvent(
    val timestampMillis: Long,
    val requestId: String,
    val principalId: String,
    val transport: TransportKind,
    val repo: String?,
    val operation: String?,
    val branch: String?,
    val path: String?,
    val decision: String,
    val outcome: String,
    val upstreamStatusCategory: String? = null,
    val commitSha: String? = null,
    val pullRequestNumber: Int? = null,
)
