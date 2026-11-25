package com.inscopexlabs.gitana.gitbridge.core.ports

import com.inscopexlabs.gitana.gitbridge.contracts.audit.AuditEvent

interface AuditSinkPort {
    fun record(event: AuditEvent)
}
