// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = IslandWhite,
    primaryContainer = OceanBlueContainer,
    onPrimaryContainer = OceanBlueOnContainer,
    secondary = TealGreen,
    onSecondary = IslandWhite,
    secondaryContainer = TealGreenDark.copy(alpha = 0.4f),
    onSecondaryContainer = OceanBlueOnContainer,
    tertiary = SandAmber,
    onTertiary = IslandWhite,
    surface = IslandWhite,
    onSurface = IslandBlack,
    outline = NeutralGray
)

private val DarkColors = darkColorScheme(
    primary = OceanBlueDark,
    onPrimary = OceanBlueOnContainer,
    primaryContainer = OceanBlueOnContainer.copy(alpha = 0.7f),
    onPrimaryContainer = IslandWhite,
    secondary = TealGreenDark,
    onSecondary = OceanBlueOnContainer,
    tertiary = SandAmberDark,
    onTertiary = OceanBlueOnContainer,
    surface = IslandBlack,
    onSurface = IslandWhite,
    outline = NeutralGray
)

@Composable
fun ClassIslandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 默认跟随系统取色；可改为 false 固定品牌色
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
