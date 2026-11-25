package com.inscopexlabs.gitana.gitbridge.github.client

/** HTTP client fixed to the GitHub API host; no caller-supplied URL, host or method. */
class GitHubHttpClient {
    companion object {
        const val BASE_URL = "https://api.github.com"
    }
}
