package com.inscopexlabs.gitana.gitbridge.local

/** Rejects Host/Origin values not permitted by the Local Transport Contract (RTB-21). */
class HostHeaderGuard {
    fun isPermitted(host: String, origin: String?): Boolean =
        HOST.matches(host) && (origin == null || ORIGIN.matches(origin))

    private companion object {
        const val LOOPBACK = """(127\.0\.0\.1|\[::1\]|localhost)(:[0-9]{1,5})?"""
        val HOST = Regex(LOOPBACK)
        val ORIGIN = Regex("http://$LOOPBACK")
    }
}
