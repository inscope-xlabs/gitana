package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.GitHubCloneRepository
import com.example.data.GitPktLineCodec
import com.example.ui.RepoCloneScreen
import com.example.ui.RepoCloneViewModel
import com.example.ui.theme.RepoCloneTheme
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches RepoClone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("RepoClone", context.getString(R.string.app_name))
    }

    @Test
    fun `cloning valid repo displays success confirmation card`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeClient = buildFakeGitHubClient(failWithExistingTarget = false)
        val repo = GitHubCloneRepository(
            cacheDir = context.cacheDir,
            httpClient = fakeClient,
        )
        val viewModel = RepoCloneViewModel(repo)

        composeTestRule.setContent {
            RepoCloneTheme {
                RepoCloneScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clone_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("source_repo_input")
            .performTextInput("octocat/Hello-World")
        composeTestRule.onNodeWithTag("new_repo_name_input")
            .performTextInput("hello-clone")
        composeTestRule.onNodeWithTag("token_input")
            .performTextInput("ghp_testtoken123456")

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clone_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("confirmation_success_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("confirmation_success_card")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `cloning into existing repo name displays failure confirmation card`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeClient = buildFakeGitHubClient(failWithExistingTarget = true)
        val repo = GitHubCloneRepository(
            cacheDir = context.cacheDir,
            httpClient = fakeClient,
        )
        val viewModel = RepoCloneViewModel(repo)

        composeTestRule.setContent {
            RepoCloneTheme {
                RepoCloneScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("source_repo_input")
            .performTextInput("https://github.com/octocat/Hello-World")
        composeTestRule.onNodeWithTag("new_repo_name_input")
            .performTextInput("existing-repo")
        composeTestRule.onNodeWithTag("token_input")
            .performTextInput("ghp_testtoken123456")

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("clone_button")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("confirmation_failure_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("confirmation_failure_card")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun buildFakeGitHubClient(failWithExistingTarget: Boolean): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            val json = "application/json".toMediaType()

            val (code, bodyBytes, contentType) = when {
                url.endsWith("/user") -> Triple(
                    200,
                    """{"login":"alice","name":"Alice"}""".toByteArray(),
                    json,
                )
                url.endsWith("/repos/octocat/Hello-World") -> Triple(
                    200,
                    """
                    {
                      "name":"Hello-World",
                      "full_name":"octocat/Hello-World",
                      "description":"Sample repo",
                      "private":false,
                      "html_url":"https://github.com/octocat/Hello-World",
                      "clone_url":"https://github.com/octocat/Hello-World.git",
                      "default_branch":"main",
                      "stargazers_count":42,
                      "language":"Kotlin",
                      "size":12,
                      "owner":{"login":"octocat"}
                    }
                    """.trimIndent().toByteArray(),
                    json,
                )
                url.endsWith("/repos/alice/hello-clone") -> Triple(
                    404,
                    """{"message":"Not Found"}""".toByteArray(),
                    json,
                )
                url.endsWith("/repos/alice/existing-repo") -> Triple(
                    if (failWithExistingTarget) 200 else 404,
                    """{"name":"existing-repo","full_name":"alice/existing-repo","private":false,"html_url":"https://github.com/alice/existing-repo","clone_url":"https://github.com/alice/existing-repo.git","default_branch":"main","owner":{"login":"alice"}}""".toByteArray(),
                    json,
                )
                url.endsWith("/user/repos") -> Triple(
                    201,
                    """
                    {
                      "name":"hello-clone",
                      "full_name":"alice/hello-clone",
                      "description":"Sample repo",
                      "private":false,
                      "html_url":"https://github.com/alice/hello-clone",
                      "clone_url":"https://github.com/alice/hello-clone.git",
                      "default_branch":"main",
                      "owner":{"login":"alice"}
                    }
                    """.trimIndent().toByteArray(),
                    json,
                )
                url.contains("Hello-World.git/info/refs?service=git-upload-pack") -> {
                    val out = ByteArrayOutputStream()
                    out.write(GitPktLineCodec.encodePktLine("# service=git-upload-pack\n"))
                    out.write(GitPktLineCodec.flushPacket())
                    out.write(
                        GitPktLineCodec.encodePktLine(
                            "1111111111111111111111111111111111111111 refs/heads/main\u0000side-band-64k ofs-delta\n",
                        ),
                    )
                    out.write(GitPktLineCodec.flushPacket())
                    Triple(200, out.toByteArray(), "application/x-git-upload-pack-advertisement".toMediaType())
                }
                url.endsWith("Hello-World.git/git-upload-pack") -> {
                    val out = ByteArrayOutputStream()
                    out.write(GitPktLineCodec.encodePktLine("NAK\n"))
                    val packBytes = "PACK\u0000\u0000\u0000\u0002\u0000\u0000\u0000\u0000".toByteArray()
                    val sidebandPayload = byteArrayOf(1) + packBytes
                    val lenHeader = String.format("%04x", sidebandPayload.size + 4).toByteArray()
                    out.write(lenHeader)
                    out.write(sidebandPayload)
                    out.write(GitPktLineCodec.flushPacket())
                    Triple(200, out.toByteArray(), "application/x-git-upload-pack-result".toMediaType())
                }
                url.contains("hello-clone.git/info/refs?service=git-receive-pack") -> {
                    val out = ByteArrayOutputStream()
                    out.write(GitPktLineCodec.encodePktLine("# service=git-receive-pack\n"))
                    out.write(GitPktLineCodec.flushPacket())
                    out.write(
                        GitPktLineCodec.encodePktLine(
                            "${GitPktLineCodec.ZERO_SHA} capabilities^{}\u0000report-status\n",
                        ),
                    )
                    out.write(GitPktLineCodec.flushPacket())
                    Triple(200, out.toByteArray(), "application/x-git-receive-pack-advertisement".toMediaType())
                }
                url.endsWith("hello-clone.git/git-receive-pack") -> {
                    val out = ByteArrayOutputStream()
                    out.write(GitPktLineCodec.encodePktLine("unpack ok\n"))
                    out.write(GitPktLineCodec.encodePktLine("ok refs/heads/main\n"))
                    out.write(GitPktLineCodec.flushPacket())
                    Triple(200, out.toByteArray(), "application/x-git-receive-pack-result".toMediaType())
                }
                else -> Triple(404, """{"message":"Unhandled fake route: $url"}""".toByteArray(), json)
            }

            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Mock")
                .body(bodyBytes.toResponseBody(contentType))
                .build()
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }
}
