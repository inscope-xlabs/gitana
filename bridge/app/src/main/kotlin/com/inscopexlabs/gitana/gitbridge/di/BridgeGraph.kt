package com.inscopexlabs.gitana.gitbridge.di

import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyConfig
import com.inscopexlabs.gitana.gitbridge.core.policy.PolicyEngine
import com.inscopexlabs.gitana.gitbridge.core.ports.ClockPort

/** Manual DI: the only place that knows concrete adapter, vault and storage types. */
class BridgeGraph {
    val clock: ClockPort = object : ClockPort {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }

    // Scaffold: empty policy config denies everything.
    val policyEngine: PolicyEngine = PolicyEngine(PolicyConfig())

    // TODO: vault, storage, GitHubAdapter, BridgeService, LocalServer, RelayClient.
}
