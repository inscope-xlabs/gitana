package com.inscopexlabs.gitana.gitbridge.service

enum class HealthState { STOPPED, STARTING, HEALTHY, DEGRADED, FAILED }

/** Local HTTP and relay connectivity are separate health states (spec 9.1). */
data class ServiceHealth(
    val localHttp: HealthState = HealthState.STOPPED,
    val relay: HealthState = HealthState.STOPPED,
)
