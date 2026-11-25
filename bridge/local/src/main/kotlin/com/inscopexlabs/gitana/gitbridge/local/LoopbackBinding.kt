package com.inscopexlabs.gitana.gitbridge.local

/** Loopback-only binding rules (spec 5.1). Wildcard, LAN and public addresses are never allowed. */
object LoopbackBinding {
    val ALLOWED_HOSTS: Set<String> = setOf("127.0.0.1", "::1")

    fun isAllowed(host: String): Boolean = host in ALLOWED_HOSTS

    fun requireLoopback(host: String): String {
        require(isAllowed(host)) { "Refusing to bind to non-loopback address" }
        return host
    }
}
