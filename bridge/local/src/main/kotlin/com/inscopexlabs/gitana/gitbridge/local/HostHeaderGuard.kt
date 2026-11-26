package com.inscopexlabs.gitana.gitbridge.local

/**
 * Rejects Host/Origin values not permitted by the Local Transport Contract (RTB-21,
 * `local-transport.v1.json` `RequestHeaders`). Anything unrecognised is rejected.
 */
class HostHeaderGuard {
    /**
     * @param hosts every `Host` header value received; exactly one is required.
     * @param origins every `Origin` header value received; none, or one `http` loopback origin.
     * @return true only if the request may proceed.
     */
    fun isPermitted(hosts: List<String>, origins: List<String>): Boolean {
        val host = hosts.singleOrNull() ?: return false
        if (!matches(HOST, host)) return false
        return when (origins.size) {
            0 -> true
            1 -> matches(ORIGIN, origins.single())
            else -> false
        }
    }

    private fun matches(pattern: Regex, value: String): Boolean {
        val match = pattern.matchEntire(value) ?: return false
        val port = match.groups[PORT_GROUP]?.value ?: return true
        return port.toInt() <= MAX_PORT
    }

    private companion object {
        const val LOOPBACK = """(?:127\.0\.0\.1|\[::1\]|localhost)(?::([0-9]{1,5}))?"""
        const val PORT_GROUP = 1
        const val MAX_PORT = 65535
        val HOST = Regex(LOOPBACK)
        val ORIGIN = Regex("http://$LOOPBACK")
    }
}
