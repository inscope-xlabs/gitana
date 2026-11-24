package com.example.ui

import com.example.data.CloneStep
import com.example.data.CloneSuccessResult
import com.example.data.GitHubRepoDto
import com.example.domain.GitHubRepoSlug
import com.example.domain.ParseResult
import com.example.domain.RepoNameValidation

sealed interface SourceInspectionState {
    data object Idle : SourceInspectionState
    data object Loading : SourceInspectionState
    data class Loaded(val repo: GitHubRepoDto) : SourceInspectionState
    data class Error(val message: String) : SourceInspectionState
}

sealed interface CloneResultState {
    data object Idle : CloneResultState

    data class InProgress(
        val currentStep: CloneStep,
        val detailMessage: String,
        val completedSteps: Set<CloneStep>,
    ) : CloneResultState

    data class Success(
        val result: CloneSuccessResult,
    ) : CloneResultState

    data class Failure(
        val failedStep: CloneStep,
        val title: String,
        val reason: String,
        val suggestion: String,
    ) : CloneResultState
}

data class RepoCloneUiState(
    val sourceInput: String = "",
    val sourceParseResult: ParseResult = ParseResult.Empty,
    val sourceInspection: SourceInspectionState = SourceInspectionState.Idle,
    val newRepoName: String = "",
    val newRepoNameValidation: RepoNameValidation = RepoNameValidation.Empty,
    val isPrivateRepo: Boolean = false,
    val customDescription: String = "",
    val githubToken: String = "",
    val isTokenVisible: Boolean = false,
    val showTokenHelp: Boolean = false,
    val cloneResult: CloneResultState = CloneResultState.Idle,
) {
    val parsedSlug: GitHubRepoSlug?
        get() = (sourceParseResult as? ParseResult.Valid)?.slug

    val isCloning: Boolean
        get() = cloneResult is CloneResultState.InProgress

    val canSubmitClone: Boolean
        get() = sourceParseResult is ParseResult.Valid &&
            newRepoNameValidation is RepoNameValidation.Valid &&
            githubToken.trim().isNotEmpty() &&
            !isCloning
}
