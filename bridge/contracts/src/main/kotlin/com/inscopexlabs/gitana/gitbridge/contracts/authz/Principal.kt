package com.inscopexlabs.gitana.gitbridge.contracts.authz

enum class TransportKind { LOCALHOST, RELAY }

/** Canonical principal produced by an adapter after authenticating its transport credential. */
data class Principal(val id: String, val transport: TransportKind)
