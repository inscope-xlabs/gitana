package com.inscopexlabs.gitana.gitbridge.github.codec

/**
 * Contents API Base64 handling (spec 7.1).
 *
 * Note: java.util.Base64 requires API 26 but the app minSdk is 24. Decide between raising minSdk,
 * enabling core library desugaring, or a small in-module codec.
 */
class Base64Content
