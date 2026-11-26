# Git Bridge (Tool Bridge modules)

Android implementation scaffold for the Remote Repository Tool Bridge.
Specs: [`docs/remote_repository_tool_bridge_v2_specification.md`](../docs/remote_repository_tool_bridge_v2_specification.md)
and [`docs/cloudflare_relay_specification.md`](../docs/cloudflare_relay_specification.md).
Both are still **drafts**; this scaffold is not an implementation sign-off.

Base package and application id: `com.inscopexlabs.gitana.gitbridge`

## Modules

| Gradle path | Type | Depends on | Spec module |
| --- | --- | --- | --- |
| `:bridge:contracts` | Kotlin/JVM | none | Contract Registry |
| `:bridge:core` | Kotlin/JVM | contracts | Application Core, Policy Engine, ports |
| `:bridge:github` | Kotlin/JVM | contracts, core | GitHub Adapter |
| `:bridge:vault` | Android library | contracts, core | Credential Vault |
| `:bridge:storage` | Android library | contracts, core | Audit/Diagnostics, request metadata |
| `:bridge:local` | Android library | contracts, core | Localhost Adapter |
| `:bridge:relay` | Android library | contracts, core | Relay Adapter (optional) |
| `:app` | Android app | all of the above | Composition root, service, UI |

## Dependency rules

- `contracts` and `core` are pure Kotlin and must never import Android, Ktor, OkHttp, Cloudflare or agent SDK types.
- Transport adapters (`local`, `relay`) depend on `core` but never on each other or on `github`.
- `core` defines ports; adapters implement them; only `:app` wires concrete types together.

## Scaffold state

- Security-sensitive stubs **fail closed**: `PolicyEngine` denies everything, `PathNormalizer` rejects every path,
  `ProtectedPaths` treats everything as protected, `ApprovalCoordinator` denies all approvals.
- Implemented and tested: request state machine, contract version parsing, loopback binding rules.
- `@Ignore`d tests in `PathNormalizerTest` record requirements to turn on as the normalizer is built.
- Not yet added: Ktor, OkHttp WebSocket, Room, kotlinx-serialization (no catalog entries or version decisions yet),
  foreground service type, release signing, androidTest suites (loopback reachability, service lifecycle).
- Numeric limits in `ResourceLimits` and `GitHubApiVersion` are placeholders pending the spec's open decisions.
