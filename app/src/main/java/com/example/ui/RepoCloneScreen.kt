package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.ParseResult
import com.example.domain.RepoNameValidation
import com.example.ui.theme.LocalRepoCloneColors
import kotlinx.coroutines.launch

@Composable
fun RepoCloneScreen(
    viewModel: RepoCloneViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val copiedMessage = stringResource(R.string.copied_url_snackbar)

    val copyUrl: (String) -> Unit = { url ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("GitHub Repository URL", url))
        coroutineScope.launch {
            snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    val openUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("No browser found to open $url")
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            val isExpandedLayout = maxWidth >= 840.dp

            if (isExpandedLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeroHeaderCard()
                        SourceRepoCard(
                            state = state,
                            onSourceChange = viewModel::onSourceInputChange,
                            onClear = viewModel::clearSourceInput,
                            onInspect = {
                                focusManager.clearFocus()
                                viewModel.inspectSourceRepository()
                            },
                        )
                        DestinationRepoCard(
                            state = state,
                            onNameChange = viewModel::onNewRepoNameChange,
                            onVisibilityChange = viewModel::onVisibilityChange,
                            onDescriptionChange = viewModel::onCustomDescriptionChange,
                        )
                        AuthTokenCard(
                            state = state,
                            onTokenChange = viewModel::onTokenChange,
                            onToggleVisibility = viewModel::toggleTokenVisibility,
                            onToggleHelp = viewModel::toggleTokenHelp,
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.startClone()
                            },
                        )
                        CloneActionButton(
                            enabled = state.canSubmitClone,
                            isCloning = state.isCloning,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.startClone()
                            },
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CloneStatusSection(
                            cloneResult = state.cloneResult,
                            showIdlePlaceholder = true,
                            onCopyUrl = copyUrl,
                            onOpenUrl = openUrl,
                            onCloneAnother = viewModel::resetForAnotherClone,
                            onRetry = viewModel::startClone,
                            onDismiss = viewModel::dismissCloneResult,
                        )
                    }
                }
            } else {
                val compactScrollState = rememberScrollState()
                LaunchedEffect(state.cloneResult) {
                    if (state.cloneResult !is CloneResultState.Idle) {
                        compactScrollState.animateScrollTo(compactScrollState.maxValue)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(compactScrollState)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeroHeaderCard()

                        SourceRepoCard(
                            state = state,
                            onSourceChange = viewModel::onSourceInputChange,
                            onClear = viewModel::clearSourceInput,
                            onInspect = {
                                focusManager.clearFocus()
                                viewModel.inspectSourceRepository()
                            },
                        )

                        DestinationRepoCard(
                            state = state,
                            onNameChange = viewModel::onNewRepoNameChange,
                            onVisibilityChange = viewModel::onVisibilityChange,
                            onDescriptionChange = viewModel::onCustomDescriptionChange,
                        )

                        AuthTokenCard(
                            state = state,
                            onTokenChange = viewModel::onTokenChange,
                            onToggleVisibility = viewModel::toggleTokenVisibility,
                            onToggleHelp = viewModel::toggleTokenHelp,
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.startClone()
                            },
                        )

                        CloneActionButton(
                            enabled = state.canSubmitClone,
                            isCloning = state.isCloning,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.startClone()
                            },
                        )

                        CloneStatusSection(
                            cloneResult = state.cloneResult,
                            showIdlePlaceholder = false,
                            onCopyUrl = copyUrl,
                            onOpenUrl = openUrl,
                            onCloneAnother = viewModel::resetForAnotherClone,
                            onRetry = viewModel::startClone,
                            onDismiss = viewModel::dismissCloneResult,
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeaderCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_repo_clone_hero),
            contentDescription = stringResource(R.string.hero_banner_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xE80B1120),
                            Color(0xB80B1120),
                            Color(0x900B1120),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRepoCard(
    state: RepoCloneUiState,
    onSourceChange: (String) -> Unit,
    onClear: () -> Unit,
    onInspect: () -> Unit,
) {
    val ext = LocalRepoCloneColors.current
    val parseError = (state.sourceParseResult as? ParseResult.Invalid)?.reason
    val parsedSlug = state.parsedSlug

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                icon = Icons.Filled.AccountTree,
                title = stringResource(R.string.section_source_title),
            )

            OutlinedTextField(
                value = state.sourceInput,
                onValueChange = onSourceChange,
                enabled = !state.isCloning,
                singleLine = true,
                label = { Text(stringResource(R.string.source_repo_label)) },
                placeholder = { Text(stringResource(R.string.source_repo_placeholder)) },
                isError = parseError != null,
                supportingText = parseError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.sourceInput.isNotEmpty() && !state.isCloning) {
                            IconButton(
                                onClick = onClear,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_source_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.clear_input_cd),
                                )
                            }
                        }
                        IconButton(
                            onClick = onInspect,
                            enabled = parsedSlug != null && !state.isCloning && state.sourceInspection !is SourceInspectionState.Loading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("inspect_source_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.inspect_repo_cd),
                                tint = if (parsedSlug != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("source_repo_input"),
            )

            AnimatedVisibility(visible = parsedSlug != null) {
                if (parsedSlug != null) {
                    Surface(
                        color = ext.codeSurface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.source_parsed_prefix),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = parsedSlug.fullName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            when (val inspection = state.sourceInspection) {
                SourceInspectionState.Idle -> Unit
                SourceInspectionState.Loading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.inspecting_repo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is SourceInspectionState.Loaded -> {
                    val repo = inspection.repo
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!repo.description.isNullOrBlank()) {
                                Text(
                                    text = repo.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RepoMetaChip(
                                    icon = if (repo.private) Icons.Filled.Lock else Icons.Filled.Public,
                                    label = if (repo.private) "Private" else "Public",
                                )
                                RepoMetaChip(
                                    icon = Icons.Filled.AccountTree,
                                    label = repo.defaultBranch,
                                )
                                RepoMetaChip(
                                    icon = Icons.Filled.Star,
                                    label = "${repo.stargazersCount}",
                                )
                                if (!repo.language.isNullOrBlank()) {
                                    RepoMetaChip(
                                        icon = Icons.Filled.Code,
                                        label = repo.language,
                                    )
                                }
                            }
                        }
                    }
                }
                is SourceInspectionState.Error -> {
                    Text(
                        text = inspection.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.errorAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationRepoCard(
    state: RepoCloneUiState,
    onNameChange: (String) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    val nameError = (state.newRepoNameValidation as? RepoNameValidation.Invalid)?.reason

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                icon = Icons.Filled.DriveFileRenameOutline,
                title = stringResource(R.string.section_destination_title),
            )

            OutlinedTextField(
                value = state.newRepoName,
                onValueChange = onNameChange,
                enabled = !state.isCloning,
                singleLine = true,
                label = { Text(stringResource(R.string.new_repo_name_label)) },
                placeholder = { Text(stringResource(R.string.new_repo_name_placeholder)) },
                isError = nameError != null,
                supportingText = nameError?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_repo_name_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.visibility_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !state.isPrivateRepo,
                    enabled = !state.isCloning,
                    onClick = { onVisibilityChange(false) },
                    label = { Text(stringResource(R.string.visibility_public)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("visibility_public_chip"),
                )
                FilterChip(
                    selected = state.isPrivateRepo,
                    enabled = !state.isCloning,
                    onClick = { onVisibilityChange(true) },
                    label = { Text(stringResource(R.string.visibility_private)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("visibility_private_chip"),
                )
            }

            OutlinedTextField(
                value = state.customDescription,
                onValueChange = onDescriptionChange,
                enabled = !state.isCloning,
                singleLine = true,
                label = { Text(stringResource(R.string.description_label)) },
                placeholder = { Text(stringResource(R.string.description_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("description_input"),
            )
        }
    }
}

@Composable
private fun AuthTokenCard(
    state: RepoCloneUiState,
    onTokenChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleHelp: () -> Unit,
    onDone: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionHeader(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.section_auth_title),
                )
                IconButton(
                    onClick = onToggleHelp,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_token_help_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Token permission info",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedTextField(
                value = state.githubToken,
                onValueChange = onTokenChange,
                enabled = !state.isCloning,
                singleLine = true,
                label = { Text(stringResource(R.string.token_label)) },
                placeholder = { Text(stringResource(R.string.token_placeholder)) },
                visualTransformation = if (state.isTokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                trailingIcon = {
                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_token_visibility_button"),
                    ) {
                        Icon(
                            imageVector = if (state.isTokenVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(R.string.toggle_token_visibility_cd),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("token_input"),
            )

            AnimatedVisibility(visible = state.showTokenHelp || state.githubToken.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.token_scope_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CloneActionButton(
    enabled: Boolean,
    isCloning: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("clone_button"),
    ) {
        if (isCloning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_cloning),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_clone),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RepoMetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
