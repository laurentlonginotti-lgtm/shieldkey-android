/*
 * ShieldKey — coffre-fort numérique 100 % hors-ligne
 * Copyright (C) 2026 Laurent Longinotti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
