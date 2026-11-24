package com.example.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface GitHubApiService {

    @GET("user")
    suspend fun getAuthenticatedUser(
        @Header("Authorization") authorization: String,
    ): Response<GitHubUserDto>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String? = null,
    ): Response<GitHubRepoDto>

    @POST("user/repos")
    suspend fun createUserRepository(
        @Header("Authorization") authorization: String,
        @Body request: CreateRepoRequest,
    ): Response<GitHubRepoDto>

    @PATCH("repos/{owner}/{repo}")
    suspend fun updateRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") authorization: String,
        @Body request: UpdateRepoRequest,
    ): Response<GitHubRepoDto>
}
