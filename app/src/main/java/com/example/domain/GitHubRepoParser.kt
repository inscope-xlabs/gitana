package com.example.domain

data class GitHubRepoSlug(
    val owner: String,
    val name: String,
) {
    val fullName: String get() = "$owner/$name"
    val httpsCloneUrl: String get() = "https://github.com/$owner/$name.git"
    val htmlUrl: String get() = "https://github.com/$owner/$name"
}

sealed class ParseResult {
    data class Valid(val slug: GitHubRepoSlug) : ParseResult()
    data object Empty : ParseResult()
    data class Invalid(val reason: String) : ParseResult()
}

sealed class RepoNameValidation {
    data object Valid : RepoNameValidation()
    data object Empty : RepoNameValidation()
    data class Invalid(val reason: String) : RepoNameValidation()
}

object GitHubRepoParser {

    private val OWNER_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$")
    private val REPO_REGEX = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val HTTPS_GITHUB_REGEX =
        Regex("^https?://(?:www\\.)?github\\.com/([^/\\s?#]+)/([^/\\s?#]+?)(?:\\.git)?(?:/(?:tree|blob|releases|issues|pulls|actions|commits)/.*)?/?(?:[?#].*)?$", RegexOption.IGNORE_CASE)
    private val SSH_GITHUB_REGEX =
        Regex("^git@github\\.com:([^/\\s]+)/([^/\\s]+?)(?:\\.git)?/?$", RegexOption.IGNORE_CASE)
    private val BARE_GITHUB_REGEX =
        Regex("^(?:www\\.)?github\\.com/([^/\\s?#]+)/([^/\\s?#]+?)(?:\\.git)?/?(?:[?#].*)?$", RegexOption.IGNORE_CASE)
    private val SLUG_REGEX =
        Regex("^([^/\\s?#]+)/([^/\\s?#]+?)(?:\\.git)?/?$")

    fun parseSourceRepo(rawInput: String): ParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ParseResult.Empty

        val match = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> {
                HTTPS_GITHUB_REGEX.matchEntire(trimmed)
                    ?: return ParseResult.Invalid("Enter a valid GitHub URL (https://github.com/owner/repo)")
            }
            trimmed.startsWith("git@", ignoreCase = true) -> {
                SSH_GITHUB_REGEX.matchEntire(trimmed)
                    ?: return ParseResult.Invalid("Enter a valid GitHub SSH URL (git@github.com:owner/repo.git)")
            }
            trimmed.contains("github.com", ignoreCase = true) -> {
                BARE_GITHUB_REGEX.matchEntire(trimmed)
                    ?: return ParseResult.Invalid("Enter a valid GitHub path (github.com/owner/repo)")
            }
            else -> {
                SLUG_REGEX.matchEntire(trimmed)
                    ?: return ParseResult.Invalid("Enter owner/repo (e.g. octocat/Hello-World) or a GitHub URL")
            }
        }

        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")

        if (!OWNER_REGEX.matches(owner)) {
            return ParseResult.Invalid("Invalid GitHub owner name: '$owner'")
        }
        val repoValidation = validateNewRepoName(repo)
        if (repoValidation is RepoNameValidation.Invalid) {
            return ParseResult.Invalid(repoValidation.reason)
        }

        return ParseResult.Valid(GitHubRepoSlug(owner = owner, name = repo))
    }

    fun validateNewRepoName(rawName: String): RepoNameValidation {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return RepoNameValidation.Empty
        if (trimmed.length > 100) {
            return RepoNameValidation.Invalid("Repository name must be 100 characters or fewer")
        }
        if (trimmed == "." || trimmed == "..") {
            return RepoNameValidation.Invalid("Repository name cannot be '.' or '..'")
        }
        if (trimmed.endsWith(".git", ignoreCase = true)) {
            return RepoNameValidation.Invalid("Repository name should not end with '.git'")
        }
        if (!REPO_REGEX.matches(trimmed)) {
            return RepoNameValidation.Invalid("Only letters, numbers, '-', '_', and '.' are allowed")
        }
        return RepoNameValidation.Valid
    }
}
