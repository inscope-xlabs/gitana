package com.inscopexlabs.gitana.gitbridge.local

/** Loopback-only binding rules (spec 5.1). Wildcard, LAN and public addresses are never allowed. */
object LoopbackBinding {
    /** Mandatory listener address: the server does not start without it (RTB-03). */
    const val IPV4: String = "127.0.0.1"

    /** Additional listener address, bound where supported; its failure is non-fatal (RTB-03). */
    const val IPV6: String = "::1"

    /** `DV-RTB-PORT` recommended starting default. */
    const val DEFAULT_PORT: Int = 18765

    private const val MIN_PORT = 1024
    private const val MAX_PORT = 65535

    /** The only addresses a listener may bind to. */
    val ALLOWED_HOSTS: Set<String> = setOf(IPV4, IPV6)

    /** Whether [host] is exactly one of [ALLOWED_HOSTS]. Host names are not accepted. */
    fun isAllowed(host: String): Boolean = host in ALLOWED_HOSTS

    /** Whether [port] is a non-privileged TCP port (`DV-RTB-PORT`: never below 1024). */
    fun isAllowedPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

    /**
     * Returns [host] unchanged.
     *
     * @throws IllegalArgumentException if [host] is not a permitted loopback address.
     */
    fun requireLoopback(host: String): String {
        require(isAllowed(host)) { "Refusing to bind to non-loopback address" }
        return host
    }
}
