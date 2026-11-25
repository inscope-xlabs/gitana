package com.inscopexlabs.gitana.gitbridge.core.ports

interface ClockPort {
    fun nowMillis(): Long
}
