package com.inscopexlabs.gitana.gitbridge.contracts.credential

/** Independent credential classes (spec 6.1). No credential may be reused across classes. */
enum class CredentialClass {
    GITHUB_PAT,
    LOCAL_CLIENT,
    RELAY_DEVICE,
    REMOTE_AGENT,
    SESSION_KEY,
}
