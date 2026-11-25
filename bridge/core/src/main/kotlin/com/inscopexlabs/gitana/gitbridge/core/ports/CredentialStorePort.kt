package com.inscopexlabs.gitana.gitbridge.core.ports

import com.inscopexlabs.gitana.gitbridge.contracts.credential.CredentialClass

/** Port implemented by the Keystore-backed vault. Secrets never leave as String or in logs. */
interface CredentialStorePort {
    /** Runs [block] with the GitHub PAT; the plaintext is only valid inside the block. */
    fun <T> withGitHubToken(block: (CharArray) -> T): T

    /** Constant-time check of a presented credential against its own class only. */
    fun verify(credentialClass: CredentialClass, presented: CharArray): Boolean

    fun revoke(credentialClass: CredentialClass)
}
