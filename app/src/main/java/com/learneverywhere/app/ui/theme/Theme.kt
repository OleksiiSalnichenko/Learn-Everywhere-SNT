package com.learneverywhere.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = OnCoral,
    secondary = CoralDark,
    background = WarmBackgroundLight,
    onBackground = WarmOnBackgroundLight,
    surface = WarmSurfaceLight,
    onSurface = WarmOnBackgroundLight,
)

private val DarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = OnCoral,
    secondary = CoralDark,
    background = WarmBackgroundDark,
    onBackground = WarmOnBackgroundDark,
    surface = WarmSurfaceDark,
    onSurface = WarmOnBackgroundDark,
)

/**
 * Обидва затверджені Stitch-варіанти (день/ніч) — це одна тема, що сама
 * перемикається за системною темою пристрою (історія 52 / A01), без окремої
 * опції в налаштуваннях застосунку.
 */
@Composable
fun LearnEverywhereTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LearnEverywhereTypography,
        content = content,
    )
}
