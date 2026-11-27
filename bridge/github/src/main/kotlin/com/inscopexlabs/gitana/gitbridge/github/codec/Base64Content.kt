// bridge/github/src/main/kotlin/com/inscopexlabs/gitana/gitbridge/github/codec/Base64Content.kt
package com.inscopexlabs.gitana.gitbridge.github.codec

import com.inscopexlabs.gitana.gitbridge.contracts.error.ErrorCode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Standard 1 – Stage 1: GitHub Contents API Base64 payloads are encoded and
 *                       decoded on the happy path.
 * Standard 1 – Stage 2: The codec is `java.util.Base64`, exactly as recorded
 *                       in RTB-D18 – no core library desugaring and no
 *                       hand-rolled codec – so the choice is a recorded
 *                       decision rather than an accident of the classpath.
 *                       It is reachable from `minSdk 26` (`DV-RTB-MIN-SDK`).
 * Standard 1 – Stage 3: ASCII whitespace is stripped before decoding,
 *                       malformed Base64 is rejected with a typed reason, and
 *                       payloads that are not valid UTF-8 are reported as
 *                       unsupported content instead of being silently
 *                       replaced with replacement characters.
 *
 * Spec 7.1, RTB-18, RTB-D18.
 */
object Base64Content {

    /** Outcome of decoding a GitHub Base64 payload. */
    sealed interface DecodeResult {

        /** The payload decoded to valid UTF-8 text. */
        data class Decoded(val content: String) : DecodeResult

        /**
         * The payload could not be decoded. [reason] is safe to show to a
         * user and never contains the payload itself.
         */
        data class Rejected(
            val reason: String,
            val errorCode: ErrorCode = ErrorCode.UNSUPPORTED_CONTENT,
        ) : DecodeResult
    }

    /**
     * Encodes [content] as single-line Base64 without embedded newlines
     * (spec 7.1).
     */
    fun encode(content: String): String =
        Base64.getEncoder().encodeToString(content.toByteArray(StandardCharsets.UTF_8))

    /**
     * Decodes a GitHub Base64 payload. ASCII whitespace and newlines are
     * stripped first so GitHub's line-wrapped responses decode cleanly
     * (spec 7.1); the remaining payload is decoded with strict RFC 4648
     * semantics.
     */
    fun decode(encoded: String): DecodeResult {
        val stripped = encoded.filterNot { it.isWhitespace() }
        if (stripped.isEmpty()) return DecodeResult.Decoded("")

        val bytes = try {
            Base64.getDecoder().decode(stripped)
        } catch (_: IllegalArgumentException) {
            return DecodeResult.Rejected("Response contained malformed Base64 content")
        }
        return decodeUtf8(bytes)
    }

    /** Decodes [bytes] as strict UTF-8; binary payloads are rejected. */
    private fun decodeUtf8(bytes: ByteArray): DecodeResult {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            DecodeResult.Decoded(decoder.decode(ByteBuffer.wrap(bytes)).toString())
        } catch (_: CharacterCodingException) {
            DecodeResult.Rejected("File content is binary or not valid UTF-8")
        }
    }
}
