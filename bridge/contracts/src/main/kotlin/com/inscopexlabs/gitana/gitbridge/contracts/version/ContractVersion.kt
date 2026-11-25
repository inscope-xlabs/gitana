package com.inscopexlabs.gitana.gitbridge.contracts.version

/** Explicit contract version (spec 4.3). A breaking change requires a new major version. */
data class ContractVersion(val major: Int, val minor: Int) {
    fun isCompatibleWith(other: ContractVersion): Boolean = major == other.major

    override fun toString(): String = "$major.$minor"

    companion object {
        val CURRENT = ContractVersion(1, 0)

        fun parse(text: String): ContractVersion? {
            val parts = text.split(".")
            if (parts.size != 2) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            return ContractVersion(major, minor)
        }
    }
}
