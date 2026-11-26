// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/client/UpstreamErrorMapper.kt
package com.inscopexlabs.gitana.gitbridge.github.client

import com.inscopexlabs.gitana.gitbridge.contracts.error.BridgeError
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCategory
import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import com.inscopexlabs.gitana.gitbridge.contracts.error.ExecutionState

/**
 * Standard 1 – Stage 1: A successful GitHub response maps to no error at all.
 * Standard 1 – Stage 2: The mapper is a pure function over (status, headers);
 *                       it holds no state, so it can be replaced or exercised
 *                       in isolation.
 * Standard 1 – Stage 3: Every status the adapter can observe maps to exactly
 *                       one catalogue code, category, HTTP status, retry flag
 *                       and execution state. Upstream bodies are never copied
 *                       into the message (spec 11.1, RTB-54).
 *
 * Spec 11.1, `schemas/error.v1.json`, RTB-D16.
 */
object UpstreamErrorMapper {

    /** Header GitHub uses to state how long the caller should wait. */
    const val RETRY_AFTER_HEADER: String = "retry-after"

    /** Header GitHub uses for the remaining core-request budget. */
    const val RATE_LIMIT_REMAINING_HEADER: String = "x-ratelimit-remaining"

    /** Header GitHub uses for the core-limit reset time (epoch seconds). */
    const val RATE_LIMIT_RESET_HEADER: String = "x-ratelimit-reset"

    /**
     * Maps an upstream response to a sanitized, typed error.
     *
     * Rate limiting is detected before the plain `403` branch so a throttled
     * request is never mistaken for a scope error.
     */
    fun map(status: Int, headers: Map<String, String>): BridgeError = when {
        status == 401 -> BridgeError(
            code = ErrorCode.AUTH_FAILED,
            category = ErrorCategory.AUTHENTICATION,
            httpStatus = 401,
            retryable = false,
            executionState = ExecutionState.NOT_STARTED,
            message = "GitHub rejected the stored credential",
        )

        isRateLimited(status, headers) -> BridgeError(
            code = ErrorCode.RATE_LIMITED,
            category = ErrorCategory.RESOURCE_LIMIT,
            httpStatus = 429,
            retryable = true,
            executionState = ExecutionState.NOT_STARTED,
            message = "GitHub rate limit exceeded; retry with bounded backoff",
        )

        status == 403 -> BridgeError(
            code = ErrorCode.FORBIDDEN,
            category = ErrorCategory.AUTHORIZATION,
            httpStatus = 403,
            retryable = false,
            executionState = ExecutionState.NOT_STARTED,
            message = "GitHub denied the request for the current credential scope",
        )

        status == 404 -> BridgeError(
            code = ErrorCode.NOT_FOUND,
            category = ErrorCategory.PROVIDER,
            httpStatus = 404,
            retryable = false,
            executionState = ExecutionState.FAILED,
            message = "GitHub has no such repository, branch, file or pull request",
        )

        // 409 and 422 are both conflicts and are never retried automatically.
        status == 409 || status == 422 -> BridgeError(
            code = ErrorCode.CONFLICT,
            category = ErrorCategory.PROVIDER,
            httpStatus = 409,
            retryable = false,
            executionState = ExecutionState.FAILED,
            message = "GitHub reported a conflict for this operation",
        )

        status == 400 -> BridgeError(
            code = ErrorCode.VALIDATION_FAILED,
            category = ErrorCategory.VALIDATION,
            httpStatus = 400,
            retryable = false,
            executionState = ExecutionState.NOT_STARTED,
            message = "GitHub rejected the request as invalid",
        )

        status in 500..599 -> BridgeError(
            code = ErrorCode.UPSTREAM_UNAVAILABLE,
            category = ErrorCategory.PROVIDER,
            httpStatus = 503,
            retryable = true,
            executionState = ExecutionState.FAILED,
            message = "GitHub is temporarily unavailable",
        )

        // Redirects are never followed, so a renamed repository must not be
        // resolved implicitly (spec 7.2, RTB-D13).
        status in 300..399 -> BridgeError(
            code = ErrorCode.NOT_FOUND,
            category = ErrorCategory.PROVIDER,
            httpStatus = 404,
            retryable = false,
            executionState = ExecutionState.FAILED,
            message = "GitHub redirected the request; repository renames are not followed",
        )

        else -> BridgeError(
            code = ErrorCode.INTERNAL,
            category = ErrorCategory.INTERNAL,
            httpStatus = 500,
            retryable = false,
            executionState = ExecutionState.FAILED,
            message = "Unmapped upstream response (HTTP $status)",
        )
    }

    /**
     * Builds the internal failure for a response the adapter cannot accept.
     *
     * The failure is marked as dispatched: GitHub answered, so a write may
     * already have been applied. The adapter decides what that means for the
     * operation it is running (RTB-43, RTB-44).
     */
    internal fun failure(response: GitHubResponse): GitHubFailure = GitHubFailure(
        error = map(response.status, response.headers),
        retryAfterSeconds = retryAfterSeconds(response.headers),
        dispatched = true,
    )

    /** True when the response indicates rate limiting rather than a scope error. */
    fun isRateLimited(status: Int, headers: Map<String, String>): Boolean =
        status == 429 ||
            headers[RETRY_AFTER_HEADER] != null ||
            (status == 403 && headers[RATE_LIMIT_REMAINING_HEADER] == "0")

    /**
     * Seconds to wait before retrying, or null when the response states none.
     *
     * Only the delta-seconds form of `Retry-After` is understood; GitHub uses
     * that form for both primary and secondary rate limits.
     */
    fun retryAfterSeconds(headers: Map<String, String>): Long? =
        headers[RETRY_AFTER_HEADER]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
}

/**
 * Internal carrier for a typed failure plus the transport detail the backoff
 * policy needs. The adapter converts it into the public `BridgeException` at
 * the port boundary.
 *
 * [dispatched] records whether the request could have reached GitHub. A
 * failure that was never dispatched is always safe to retry; a failure after
 * dispatch may have applied a side effect, so it must be reconciled first
 * (RTB-43, RTB-44).
 */
internal class GitHubFailure(
    val error: BridgeError,
    val retryAfterSeconds: Long? = null,
    val dispatched: Boolean = false,
) : RuntimeException(error.message) {

    companion object {

        /** The request was malformed or violated a documented limit. */
        fun validation(message: String): GitHubFailure = GitHubFailure(
            BridgeError(
                code = ErrorCode.VALIDATION_FAILED,
                category = ErrorCategory.VALIDATION,
                httpStatus = 400,
                retryable = false,
                executionState = ExecutionState.NOT_STARTED,
                message = message,
            ),
        )

        /** The response could not be understood. */
        fun malformed(what: String): GitHubFailure = GitHubFailure(
            BridgeError(
                code = ErrorCode.UPSTREAM_UNAVAILABLE,
                category = ErrorCategory.PROVIDER,
                httpStatus = 503,
                retryable = true,
                executionState = ExecutionState.FAILED,
                message = "GitHub returned an unreadable $what response",
            ),
            dispatched = true,
        )

        /** The target content is binary or otherwise not readable as text. */
        fun unsupportedContent(message: String): GitHubFailure = GitHubFailure(
            BridgeError(
                code = ErrorCode.UNSUPPORTED_CONTENT,
                category = ErrorCategory.VALIDATION,
                httpStatus = 422,
                retryable = false,
                executionState = ExecutionState.FAILED,
                message = message,
            ),
        )

        /** The request or the content exceeded a documented size limit. */
        fun payloadTooLarge(message: String): GitHubFailure = GitHubFailure(
            BridgeError(
                code = ErrorCode.PAYLOAD_TOO_LARGE,
                category = ErrorCategory.RESOURCE_LIMIT,
                httpStatus = 413,
                retryable = false,
                executionState = ExecutionState.NOT_STARTED,
                message = message,
            ),
        )

        /** The repository, branch, file or pull request does not exist. */
        fun notFound(message: String): GitHubFailure = GitHubFailure(
            BridgeError(
                code = ErrorCode.NOT_FOUND,
                category = ErrorCategory.PROVIDER,
                httpStatus = 404,
                retryable = false,
                executionState = ExecutionState.FAILED,
                message = message,
            ),
        )

        /** GitHub refused the write because of a concurrent change. */
        fun conflict(message: String): GitHubFailure = GitHubFailure(
            BridgeError(
                code = ErrorCode.CONFLICT,
                category = ErrorCategory.PROVIDER,
                httpStatus = 409,
                retryable = false,
                executionState = ExecutionState.FAILED,
                message = message,
            ),
        )
    }
}
