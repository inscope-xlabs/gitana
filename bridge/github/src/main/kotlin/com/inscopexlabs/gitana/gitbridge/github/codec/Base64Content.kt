package com.inscopexlabs.gitana.gitbridge.github.codec

/**
 * Contents API Base64 handling (spec 7.1).
 *
 * Decision closed (RTB-D08, RTB-D18): Android `minSdk` is 26 across `:app` and Android bridge
 * modules, so `java.util.Base64` (`getDecoder()` after stripping ASCII whitespace; single-line
 * `getEncoder()`) is natively supported without desugaring or a custom codec.
 */
class Base64Content
