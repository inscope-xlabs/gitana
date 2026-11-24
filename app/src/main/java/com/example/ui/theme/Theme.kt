package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class RepoCloneExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val errorAccent: Color,
    val errorContainerAccent: Color,
    val onErrorContainerAccent: Color,
    val warning: Color,
    val cardElevated: Color,
    val codeSurface: Color,
)

private val DarkExtendedColors = RepoCloneExtendedColors(
    success = EmeraldSuccessDark,
    successContainer = EmeraldSuccessContainerDark,
    onSuccessContainer = EmeraldOnSuccessContainerDark,
    errorAccent = CoralErrorDark,
    errorContainerAccent = CoralErrorContainerDark,
    onErrorContainerAccent = CoralOnErrorContainerDark,
    warning = AmberWarningDark,
    cardElevated = Color(0xFF172033),
    codeSurface = Color(0xFF0A0F1C),
)

private val LightExtendedColors = RepoCloneExtendedColors(
    success = EmeraldSuccessLight,
    successContainer = EmeraldSuccessContainerLight,
    onSuccessContainer = EmeraldOnSuccessContainerLight,
    errorAccent = CoralErrorLight,
    errorContainerAccent = CoralErrorContainerLight,
    onErrorContainerAccent = CoralOnErrorContainerLight,
    warning = AmberWarningLight,
    cardElevated = Color(0xFFFFFFFF),
    codeSurface = Color(0xFFF1F5F9),
)

val LocalRepoCloneColors = staticCompositionLocalOf { DarkExtendedColors }

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = CyanOnPrimaryDark,
    primaryContainer = CyanPrimaryContainerDark,
    onPrimaryContainer = CyanOnPrimaryContainerDark,
    secondary = IndigoSecondaryDark,
    onSecondary = IndigoOnSecondaryDark,
    secondaryContainer = IndigoSecondaryContainerDark,
    onSecondaryContainer = IndigoOnSecondaryContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = CoralErrorDark,
    errorContainer = CoralErrorContainerDark,
    onErrorContainer = CoralOnErrorContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = CyanOnPrimaryLight,
    primaryContainer = CyanPrimaryContainerLight,
    onPrimaryContainer = CyanOnPrimaryContainerLight,
    secondary = IndigoSecondaryLight,
    onSecondary = IndigoOnSecondaryLight,
    secondaryContainer = IndigoSecondaryContainerLight,
    onSecondaryContainer = IndigoOnSecondaryContainerLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = CoralErrorLight,
    errorContainer = CoralErrorContainerLight,
    onErrorContainer = CoralOnErrorContainerLight,
)

val RepoCloneShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RepoCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalRepoCloneColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RepoCloneTypography,
            shapes = RepoCloneShapes,
            content = content,
        )
    }
}
