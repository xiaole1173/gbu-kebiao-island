// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.gbu.classisland.navigation.AppNavHost
import com.gbu.classisland.ui.screens.OnboardingScreen
import com.gbu.classisland.ui.theme.ClassIslandTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClassIslandTheme {
                FirstRunGateAndNav()
            }
        }
    }
}

/**
 * 首次进入：显示全屏新手引导（登录 + 权限设置），完成/跳过后进入主页。
 */
@Composable
private fun FirstRunGateAndNav() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("first_run", Context.MODE_PRIVATE)
    }
    var guided by remember { mutableStateOf(prefs.getBoolean("guided", false)) }

    if (!guided) {
        OnboardingScreen(
            onFinished = {
                prefs.edit().putBoolean("guided", true).apply()
                guided = true
            }
        )
    } else {
        AppNavHost()
    }
}
