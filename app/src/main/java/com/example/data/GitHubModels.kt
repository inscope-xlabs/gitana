package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubUserDto(
    val login: String,
    val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubOwnerDto(
    val login: String,
)

@JsonClass(generateAdapter = true)
data class GitHubRepoDto(
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val description: String? = null,
    val private: Boolean = false,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "clone_url") val cloneUrl: String,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    val language: String? = null,
    val size: Long = 0L,
    val owner: GitHubOwnerDto,
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @Json(name = "auto_init") val autoInit: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UpdateRepoRequest(
    @Json(name = "default_branch") val defaultBranch: String,
)

@JsonClass(generateAdapter = true)
data class GitHubApiErrorDto(
    val message: String? = null,
    val errors: List<GitHubValidationErrorDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GitHubValidationErrorDto(
    val message: String? = null,
    val field: String? = null,
    val code: String? = null,
)
