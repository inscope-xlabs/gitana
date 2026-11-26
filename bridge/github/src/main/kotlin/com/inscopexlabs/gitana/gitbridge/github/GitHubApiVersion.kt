// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/GitHubApiVersion.kt
package com.inscopexlabs.gitana.gitbridge.github

/**
 * Standard 1 – Stage 1: Every outbound GitHub request carries a pinned REST
 *                       API version, so the happy path targets a known API
 *                       surface instead of drifting with "latest".
 * Standard 1 – Stage 2: The pin is a single shared constant applied by the
 *                       transport; it is not configurable per call site and
 *                       it is never derived from the response.
 * Standard 1 – Stage 3: The pin is validated so a malformed value fails
 *                       loudly here instead of producing opaque 4xx responses
 *                       upstream.
 *
 * Spec 7.1, RTB-58, RTB-D09, `DV-RTB-GITHUB-API-VERSION`.
 */
object GitHubApiVersion {

    /** Header that carries the pinned API version on every request. */
    const val HEADER_NAME: String = "X-GitHub-Api-Version"

    /**
     * Concrete date-based API version, recorded at implementation time on
     * 26 November 2026 (RTB-D09). It must be reviewed and re-pinned whenever
     * the GitHub API version is upgraded (RTB-58).
     */
    const val HEADER_VALUE: String = "2022-11-28"

    /** Date-based API versions have the shape `YYYY-MM-DD`. */
    private val DATE_BASED: Regex = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** True when [HEADER_VALUE] is a well-formed date-based API version. */
    val isValidPin: Boolean = DATE_BASED.matches(HEADER_VALUE)
}
