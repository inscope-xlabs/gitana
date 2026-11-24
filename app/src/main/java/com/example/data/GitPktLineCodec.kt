package com.example.data

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

sealed interface PktLine {
    data object Flush : PktLine
    data class Data(val payload: ByteArray) : PktLine {
        fun asUtf8(): String = String(payload, StandardCharsets.UTF_8)
    }
}

data class GitRef(
    val refName: String,
    val sha: String,
)

data class RefAdvertisement(
    val refs: List<GitRef>,
    val capabilities: Set<String>,
)

data class ReceivePackReport(
    val unpackStatus: String,
    val refResults: Map<String, String>,
) {
    val isSuccess: Boolean
        get() = unpackStatus == "ok" && refResults.values.all { it == "ok" }
}

object GitPktLineCodec {

    const val ZERO_SHA = "0000000000000000000000000000000000000000"

    fun encodePktLine(text: String): ByteArray {
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        val totalLength = payload.size + 4
        val header = String.format("%04x", totalLength).toByteArray(StandardCharsets.US_ASCII)
        return header + payload
    }

    fun flushPacket(): ByteArray = "0000".toByteArray(StandardCharsets.US_ASCII)

    fun readPktLine(input: InputStream): PktLine? {
        val headerBytes = ByteArray(4)
        var readSoFar = 0
        while (readSoFar < 4) {
            val n = input.read(headerBytes, readSoFar, 4 - readSoFar)
            if (n == -1) {
                if (readSoFar == 0) return null
                throw EOFException("Unexpected EOF while reading pkt-line header")
            }
            readSoFar += n
        }
        val header = String(headerBytes, StandardCharsets.US_ASCII)
        if (header == "0000") return PktLine.Flush

        val totalLength = header.toIntOrNull(16)
            ?: throw IOException("Malformed pkt-line header: '$header'")
        if (totalLength < 4) {
            throw IOException("Invalid pkt-line length: $totalLength")
        }

        val payloadLength = totalLength - 4
        val payload = ByteArray(payloadLength)
        var payloadRead = 0
        while (payloadRead < payloadLength) {
            val n = input.read(payload, payloadRead, payloadLength - payloadRead)
            if (n == -1) {
                throw EOFException("Unexpected EOF while reading pkt-line payload ($payloadRead/$payloadLength)")
            }
            payloadRead += n
        }
        return PktLine.Data(payload)
    }

    fun parseRefAdvertisement(input: InputStream, expectedService: String): RefAdvertisement {
        val refs = mutableListOf<GitRef>()
        val capabilities = mutableSetOf<String>()
        var seenServiceHeader = false
        var waitingForServiceFlush = false

        while (true) {
            val line = readPktLine(input) ?: break
            when (line) {
                PktLine.Flush -> {
                    if (waitingForServiceFlush) {
                        waitingForServiceFlush = false
                        continue
                    }
                    break
                }
                is PktLine.Data -> {
                    val raw = line.asUtf8()
                    if (!seenServiceHeader && raw.startsWith("# service=")) {
                        val advertisedService = raw.removePrefix("# service=").trim()
                        if (advertisedService != expectedService) {
                            throw IOException("Unexpected Git service '$advertisedService' (expected '$expectedService')")
                        }
                        seenServiceHeader = true
                        waitingForServiceFlush = true
                        continue
                    }

                    val nullIndex = raw.indexOf('\u0000')
                    val refPart: String
                    if (nullIndex >= 0) {
                        refPart = raw.substring(0, nullIndex).trim()
                        val capsPart = raw.substring(nullIndex + 1).trim()
                        if (capsPart.isNotEmpty()) {
                            capabilities.addAll(capsPart.split(' ').filter { it.isNotBlank() })
                        }
                    } else {
                        refPart = raw.trim()
                    }

                    val spaceIdx = refPart.indexOf(' ')
                    if (spaceIdx <= 0) continue
                    val sha = refPart.substring(0, spaceIdx).trim()
                    val refName = refPart.substring(spaceIdx + 1).trim()

                    if (sha == ZERO_SHA || refName == "capabilities^{}") continue
                    if (refName.endsWith("^{}")) continue
                    if (refName.startsWith("refs/heads/") || refName.startsWith("refs/tags/")) {
                        refs.add(GitRef(refName = refName, sha = sha))
                    }
                }
            }
        }

        return RefAdvertisement(refs = refs, capabilities = capabilities)
    }

    fun buildUploadPackRequest(refs: List<GitRef>, advertisedCapabilities: Set<String>): ByteArray {
        val uniqueShas = refs.map { it.sha }.distinct()
        require(uniqueShas.isNotEmpty()) { "Cannot build upload-pack request for empty ref list" }

        val requestedCaps = buildList {
            if ("side-band-64k" in advertisedCapabilities) {
                add("side-band-64k")
            } else if ("side-band" in advertisedCapabilities) {
                add("side-band")
            }
            if ("ofs-delta" in advertisedCapabilities) {
                add("ofs-delta")
            }
            add("agent=RepoClone/1.0")
        }.joinToString(" ")

        val out = ByteArrayOutputStream()
        uniqueShas.forEachIndexed { index, sha ->
            val line = if (index == 0 && requestedCaps.isNotEmpty()) {
                "want $sha $requestedCaps\n"
            } else {
                "want $sha\n"
            }
            out.write(encodePktLine(line))
        }
        out.write(flushPacket())
        out.write(encodePktLine("done\n"))
        return out.toByteArray()
    }

    fun extractPackFromUploadResponse(
        input: InputStream,
        packOutput: OutputStream,
        useSideBand: Boolean,
        onProgressMessage: (String) -> Unit = {},
    ): Long {
        var totalPackBytes = 0L
        var nakConsumed = false

        if (!useSideBand) {
            while (!nakConsumed) {
                val pkt = readPktLine(input) ?: throw EOFException("Unexpected EOF before NAK")
                if (pkt is PktLine.Data && pkt.asUtf8().trim() == "NAK") {
                    nakConsumed = true
                }
            }
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                packOutput.write(buffer, 0, n)
                totalPackBytes += n
            }
            packOutput.flush()
            return totalPackBytes
        }

        while (true) {
            val pkt = readPktLine(input) ?: break
            when (pkt) {
                PktLine.Flush -> {
                    if (nakConsumed) break
                }
                is PktLine.Data -> {
                    if (!nakConsumed) {
                        val text = pkt.asUtf8().trim()
                        if (text == "NAK" || text.startsWith("ACK ")) {
                            nakConsumed = true
                            continue
                        }
                    }
                    if (pkt.payload.isEmpty()) continue
                    when (val channel = pkt.payload[0].toInt() and 0xFF) {
                        1 -> {
                            val dataLen = pkt.payload.size - 1
                            if (dataLen > 0) {
                                packOutput.write(pkt.payload, 1, dataLen)
                                totalPackBytes += dataLen
                            }
                        }
                        2 -> {
                            val msg = String(pkt.payload, 1, pkt.payload.size - 1, StandardCharsets.UTF_8).trim()
                            if (msg.isNotEmpty()) {
                                onProgressMessage(msg)
                            }
                        }
                        3 -> {
                            val err = String(pkt.payload, 1, pkt.payload.size - 1, StandardCharsets.UTF_8).trim()
                            throw IOException("Git server error: $err")
                        }
                        else -> {
                            throw IOException("Unknown Git side-band channel: $channel")
                        }
                    }
                }
            }
        }
        packOutput.flush()
        return totalPackBytes
    }

    fun buildReceivePackCommandHeader(refs: List<GitRef>): ByteArray {
        require(refs.isNotEmpty()) { "Cannot build receive-pack commands for empty ref list" }
        val out = ByteArrayOutputStream()
        refs.forEachIndexed { index, ref ->
            val line = if (index == 0) {
                "$ZERO_SHA ${ref.sha} ${ref.refName}\u0000report-status\n"
            } else {
                "$ZERO_SHA ${ref.sha} ${ref.refName}\n"
            }
            out.write(encodePktLine(line))
        }
        out.write(flushPacket())
        return out.toByteArray()
    }

    fun parseReceivePackReport(input: InputStream): ReceivePackReport {
        var unpackStatus = ""
        val refResults = linkedMapOf<String, String>()

        while (true) {
            val pkt = readPktLine(input) ?: break
            when (pkt) {
                PktLine.Flush -> break
                is PktLine.Data -> {
                    val line = pkt.asUtf8().trim()
                    when {
                        line.startsWith("unpack ") -> {
                            unpackStatus = line.removePrefix("unpack ").trim()
                        }
                        line.startsWith("ok ") -> {
                            val refName = line.removePrefix("ok ").trim()
                            refResults[refName] = "ok"
                        }
                        line.startsWith("ng ") -> {
                            val rest = line.removePrefix("ng ").trim()
                            val spaceIdx = rest.indexOf(' ')
                            if (spaceIdx > 0) {
                                val refName = rest.substring(0, spaceIdx)
                                val reason = rest.substring(spaceIdx + 1)
                                refResults[refName] = reason
                            } else {
                                refResults[rest] = "rejected"
                            }
                        }
                    }
                }
            }
        }

        return ReceivePackReport(
            unpackStatus = unpackStatus.ifEmpty { "unknown" },
            refResults = refResults,
        )
    }
}
