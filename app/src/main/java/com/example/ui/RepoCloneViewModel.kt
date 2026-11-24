package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CloneFailureException
import com.example.data.CloneStep
import com.example.data.GitHubCloneRepository
import com.example.domain.GitHubRepoParser
import com.example.domain.ParseResult
import com.example.domain.RepoNameValidation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RepoCloneViewModel(
    private val repository: GitHubCloneRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepoCloneUiState())
    val uiState: StateFlow<RepoCloneUiState> = _uiState.asStateFlow()

    private var inspectJob: Job? = null
    private var cloneJob: Job? = null

    fun onSourceInputChange(value: String) {
        val parseResult = GitHubRepoParser.parseSourceRepo(value)
        inspectJob?.cancel()
        _uiState.update { state ->
            val currentResult = state.cloneResult
            state.copy(
                sourceInput = value,
                sourceParseResult = parseResult,
                sourceInspection = SourceInspectionState.Idle,
                cloneResult = if (currentResult is CloneResultState.Failure) CloneResultState.Idle else currentResult,
            )
        }
    }

    fun clearSourceInput() {
        inspectJob?.cancel()
        _uiState.update {
            it.copy(
                sourceInput = "",
                sourceParseResult = ParseResult.Empty,
                sourceInspection = SourceInspectionState.Idle,
            )
        }
    }

    fun onNewRepoNameChange(value: String) {
        val validation = GitHubRepoParser.validateNewRepoName(value)
        _uiState.update { state ->
            val currentResult = state.cloneResult
            state.copy(
                newRepoName = value,
                newRepoNameValidation = validation,
                cloneResult = if (currentResult is CloneResultState.Failure) CloneResultState.Idle else currentResult,
            )
        }
    }

    fun onVisibilityChange(isPrivate: Boolean) {
        _uiState.update { it.copy(isPrivateRepo = isPrivate) }
    }

    fun onCustomDescriptionChange(value: String) {
        _uiState.update { it.copy(customDescription = value) }
    }

    fun onTokenChange(value: String) {
        _uiState.update { state ->
            val currentResult = state.cloneResult
            state.copy(
                githubToken = value,
                cloneResult = if (currentResult is CloneResultState.Failure) CloneResultState.Idle else currentResult,
            )
        }
    }

    fun toggleTokenVisibility() {
        _uiState.update { it.copy(isTokenVisible = !it.isTokenVisible) }
    }

    fun toggleTokenHelp() {
        _uiState.update { it.copy(showTokenHelp = !it.showTokenHelp) }
    }

    fun inspectSourceRepository() {
        val slug = _uiState.value.parsedSlug ?: return
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _uiState.update { it.copy(sourceInspection = SourceInspectionState.Loading) }
            val result = repository.inspectRepository(slug, _uiState.value.githubToken)
            result.fold(
                onSuccess = { repo ->
                    _uiState.update { state ->
                        val suggestedName = if (state.newRepoName.isBlank()) {
                            "${repo.name}-clone"
                        } else {
                            state.newRepoName
                        }
                        state.copy(
                            sourceInspection = SourceInspectionState.Loaded(repo),
                            newRepoName = suggestedName,
                            newRepoNameValidation = GitHubRepoParser.validateNewRepoName(suggestedName),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceInspection = SourceInspectionState.Error(
                                error.message ?: "Unable to inspect repository.",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun startClone() {
        val snapshot = _uiState.value
        val slug = (snapshot.sourceParseResult as? ParseResult.Valid)?.slug ?: return
        if (snapshot.newRepoNameValidation !is RepoNameValidation.Valid) return
        if (snapshot.githubToken.trim().isEmpty()) return
        if (snapshot.isCloning) return

        cloneJob?.cancel()
        cloneJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cloneResult = CloneResultState.InProgress(
                        currentStep = CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                        detailMessage = "Starting clone pipeline…",
                        completedSteps = emptySet(),
                    ),
                )
            }

            val result = repository.cloneRepository(
                source = slug,
                newRepoName = snapshot.newRepoName,
                isPrivate = snapshot.isPrivateRepo,
                customDescription = snapshot.customDescription,
                token = snapshot.githubToken,
            ) { progress ->
                _uiState.update {
                    it.copy(
                        cloneResult = CloneResultState.InProgress(
                            currentStep = progress.step,
                            detailMessage = progress.detail,
                            completedSteps = progress.completedSteps,
                        ),
                    )
                }
            }

            result.fold(
                onSuccess = { success ->
                    _uiState.update {
                        it.copy(cloneResult = CloneResultState.Success(success))
                    }
                },
                onFailure = { throwable ->
                    val failure = throwable as? CloneFailureException
                    _uiState.update {
                        it.copy(
                            cloneResult = CloneResultState.Failure(
                                failedStep = failure?.failedStep ?: CloneStep.VERIFYING_ACCOUNT_AND_SOURCE,
                                title = failure?.title ?: "Clone Failed",
                                reason = failure?.message ?: (throwable.message ?: "Unknown error"),
                                suggestion = failure?.suggestion
                                    ?: "Verify the repository name and token permissions, then try again.",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun dismissCloneResult() {
        _uiState.update { it.copy(cloneResult = CloneResultState.Idle) }
    }

    fun resetForAnotherClone() {
        inspectJob?.cancel()
        _uiState.update { state ->
            state.copy(
                sourceInput = "",
                sourceParseResult = ParseResult.Empty,
                sourceInspection = SourceInspectionState.Idle,
                newRepoName = "",
                newRepoNameValidation = RepoNameValidation.Empty,
                customDescription = "",
                cloneResult = CloneResultState.Idle,
            )
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repo = GitHubCloneRepository(cacheDir = application.cacheDir)
                    return RepoCloneViewModel(repo) as T
                }
            }
    }
}
