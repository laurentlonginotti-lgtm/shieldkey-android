package com.shieldkey.vault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SkColorScheme = darkColorScheme(
    primary = SkEmerald,
    onPrimary = SkOnPrimary,
    secondary = SkEmerald2,
    tertiary = SkGold,
    background = SkBg,
    onBackground = SkText,
    surface = SkBgTop,
    onSurface = SkText,
)

@Composable
fun ShieldKeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SkColorScheme,
        typography = SkTypography,
        content = content
    )
}
