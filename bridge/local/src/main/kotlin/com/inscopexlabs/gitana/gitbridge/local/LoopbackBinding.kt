package com.inscopexlabs.gitana.gitbridge.local

/** Loopback-only binding rules (spec 5.1). Wildcard, LAN and public addresses are never allowed. */
object LoopbackBinding {
    const val IPV4: String = "127.0.0.1"
    const val IPV6: String = "::1"

    /** `DV-RTB-PORT` recommended starting default. */
    const val DEFAULT_PORT: Int = 18765

    val ALLOWED_HOSTS: Set<String> = setOf(IPV4, IPV6)

    fun isAllowed(host: String): Boolean = host in ALLOWED_HOSTS

    fun requireLoopback(host: String): String {
        require(isAllowed(host)) { "Refusing to bind to non-loopback address" }
        return host
    }
}
