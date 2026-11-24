package com.example

import com.example.data.GitPktLineCodec
import com.example.data.GitRef
import com.example.domain.GitHubRepoParser
import com.example.domain.GitHubRepoSlug
import com.example.domain.ParseResult
import com.example.domain.RepoNameValidation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GitHubCloneLogicTest {

    @Test
    fun `parseSourceRepo supports owner_repo and full GitHub URLs`() {
        assertEquals(
            ParseResult.Valid(GitHubRepoSlug("octocat", "Hello-World")),
            GitHubRepoParser.parseSourceRepo("octocat/Hello-World"),
        )
        assertEquals(
            ParseResult.Valid(GitHubRepoSlug("octocat", "Hello-World")),
            GitHubRepoParser.parseSourceRepo("https://github.com/octocat/Hello-World"),
        )
        assertEquals(
            ParseResult.Valid(GitHubRepoSlug("octocat", "Hello-World")),
            GitHubRepoParser.parseSourceRepo("https://github.com/octocat/Hello-World.git"),
        )
        assertEquals(
            ParseResult.Valid(GitHubRepoSlug("octocat", "Hello-World")),
            GitHubRepoParser.parseSourceRepo("https://github.com/octocat/Hello-World/tree/main"),
        )
        assertEquals(
            ParseResult.Valid(GitHubRepoSlug("octocat", "Hello-World")),
            GitHubRepoParser.parseSourceRepo("git@github.com:octocat/Hello-World.git"),
        )
    }

    @Test
    fun `parseSourceRepo rejects non-GitHub hosts and invalid slugs`() {
        assertTrue(GitHubRepoParser.parseSourceRepo("https://gitlab.com/user/repo") is ParseResult.Invalid)
        assertTrue(GitHubRepoParser.parseSourceRepo("just-a-word") is ParseResult.Invalid)
        assertEquals(ParseResult.Empty, GitHubRepoParser.parseSourceRepo("   "))
    }

    @Test
    fun `validateNewRepoName enforces GitHub naming constraints`() {
        assertEquals(RepoNameValidation.Valid, GitHubRepoParser.validateNewRepoName("my-new_repo.v2"))
        assertEquals(RepoNameValidation.Empty, GitHubRepoParser.validateNewRepoName("  "))
        assertTrue(GitHubRepoParser.validateNewRepoName("bad name") is RepoNameValidation.Invalid)
        assertTrue(GitHubRepoParser.validateNewRepoName("repo.git") is RepoNameValidation.Invalid)
        assertTrue(GitHubRepoParser.validateNewRepoName("..") is RepoNameValidation.Invalid)
    }

    @Test
    fun `parseRefAdvertisement filters heads and tags and skips pull and peeled refs`() {
        val out = ByteArrayOutputStream()
        out.write(GitPktLineCodec.encodePktLine("# service=git-upload-pack\n"))
        out.write(GitPktLineCodec.flushPacket())
        out.write(
            GitPktLineCodec.encodePktLine(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa HEAD\u0000side-band-64k ofs-delta\n",
            ),
        )
        out.write(
            GitPktLineCodec.encodePktLine(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa refs/heads/main\n",
            ),
        )
        out.write(
            GitPktLineCodec.encodePktLine(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb refs/tags/v1.0.0\n",
            ),
        )
        out.write(
            GitPktLineCodec.encodePktLine(
                "cccccccccccccccccccccccccccccccccccccccc refs/tags/v1.0.0^{}\n",
            ),
        )
        out.write(
            GitPktLineCodec.encodePktLine(
                "dddddddddddddddddddddddddddddddddddddddd refs/pull/1/head\n",
            ),
        )
        out.write(GitPktLineCodec.flushPacket())

        val parsed = GitPktLineCodec.parseRefAdvertisement(
            ByteArrayInputStream(out.toByteArray()),
            "git-upload-pack",
        )

        assertEquals(2, parsed.refs.size)
        assertEquals(GitRef("refs/heads/main", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), parsed.refs[0])
        assertEquals(GitRef("refs/tags/v1.0.0", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), parsed.refs[1])
        assertTrue("side-band-64k" in parsed.capabilities)
    }

    @Test
    fun `extractPackFromUploadResponse extracts channel 1 pack bytes`() {
        val rawPack = byteArrayOf(0x50, 0x41, 0x43, 0x4B, 0x00, 0x00, 0x00, 0x02)
        val streamOut = ByteArrayOutputStream()
        streamOut.write(GitPktLineCodec.encodePktLine("NAK\n"))

        val progressPayload = byteArrayOf(2) + "Counting objects: 3, done.\n".toByteArray()
        streamOut.write(String.format("%04x", progressPayload.size + 4).toByteArray())
        streamOut.write(progressPayload)

        val packPayload = byteArrayOf(1) + rawPack
        streamOut.write(String.format("%04x", packPayload.size + 4).toByteArray())
        streamOut.write(packPayload)
        streamOut.write(GitPktLineCodec.flushPacket())

        val extracted = ByteArrayOutputStream()
        val progressMessages = mutableListOf<String>()
        val bytesCount = GitPktLineCodec.extractPackFromUploadResponse(
            input = ByteArrayInputStream(streamOut.toByteArray()),
            packOutput = extracted,
            useSideBand = true,
            onProgressMessage = { progressMessages.add(it) },
        )

        assertEquals(rawPack.size.toLong(), bytesCount)
        assertArrayEquals(rawPack, extracted.toByteArray())
        assertEquals(listOf("Counting objects: 3, done."), progressMessages)
    }

    @Test
    fun `parseReceivePackReport handles ok and ng ref rejections`() {
        val out = ByteArrayOutputStream()
        out.write(GitPktLineCodec.encodePktLine("unpack ok\n"))
        out.write(GitPktLineCodec.encodePktLine("ok refs/heads/main\n"))
        out.write(GitPktLineCodec.encodePktLine("ng refs/tags/v1.0 protected tag\n"))
        out.write(GitPktLineCodec.flushPacket())

        val report = GitPktLineCodec.parseReceivePackReport(ByteArrayInputStream(out.toByteArray()))
        assertFalse(report.isSuccess)
        assertEquals("ok", report.unpackStatus)
        assertEquals("ok", report.refResults["refs/heads/main"])
        assertEquals("protected tag", report.refResults["refs/tags/v1.0"])
    }
}
