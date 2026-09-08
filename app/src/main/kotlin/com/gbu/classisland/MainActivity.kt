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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gbu.classisland.navigation.AppNavHost
import com.gbu.classisland.ui.screens.AddressSetupScreen
import com.gbu.classisland.ui.screens.OnboardingScreen
import com.gbu.classisland.ui.theme.ClassIslandTheme
import com.gbu.classisland.update.UpdateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    // 更新用户升级后首次打开：地址未配置且未引导过 → 显示专门引导页
    var showAddressSetup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (guided && !prefs.getBoolean("address_setup_shown", false)) {
            val repo = com.gbu.classisland.data.settings.SettingsRepository(context)
            val s = repo.settings.first()
            if (s.eduBaseUrl.isBlank() || s.eduAuthBaseUrl.isBlank()) {
                showAddressSetup = true
            }
        }
    }

    // 启动自动检查更新（受 6 小时间隔限制，完成新手引导后才检查）
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    // 等待授权"安装未知来源应用"后继续的更新
    var pendingUpdate by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    LaunchedEffect(guided) {
        if (guided && UpdateManager.shouldAutoCheck(context)) {
            val info = UpdateManager.checkLatest(context)
            if (info != null && info.versionCode > UpdateManager.localVersionCode(context)) {
                updateInfo = info
            }
        }
    }

    // 下载（本地同版本 APK 直接复用，不重复下载）→ 拉起安装器
    val runUpdate: suspend (android.content.Context, UpdateManager.UpdateInfo) -> Unit = { ctx, info ->
        updateDownloading = true
        runCatching {
            val file = UpdateManager.downloadedApk(ctx, info.versionCode)
                ?: UpdateManager.download(ctx, info) { }
            if (!UpdateManager.install(ctx, file)) {
                pendingUpdate = info
                UpdateManager.openInstallPermissionSettings(ctx)
            }
        }
        updateDownloading = false
    }

    // 从"安装未知来源应用"设置页返回后自动续装（权限状态及时刷新）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = pendingUpdate
                if (pending != null) {
                    pendingUpdate = null
                    if (UpdateManager.canInstall(context)) {
                        scope.launch { runUpdate(context, pending) }
                    } else {
                        // 用户仍未授权：弹回更新对话框，避免丢失入口
                        updateInfo = pending
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 发现新版本弹窗
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 ${info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (info.changelog.isBlank()) "有可用更新，是否立即更新？" else info.changelog,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (updateDownloading) {
                        Text(
                            "正在下载新版本…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateInfo = null
                    // 先检查"安装未知来源应用"权限：未授权先跳设置页，避免白下载
                    if (!UpdateManager.canInstall(context)) {
                        pendingUpdate = info
                        UpdateManager.openInstallPermissionSettings(context)
                    } else {
                        scope.launch { runUpdate(context, info) }
                    }
                }) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("稍后") }
            }
        )
    }

    if (!guided) {
        OnboardingScreen(
            onFinished = {
                prefs.edit().putBoolean("guided", true).apply()
                guided = true
            }
        )
    } else if (showAddressSetup) {
        // 更新用户升级后首次打开：专门引导填写统一认证 + 教务地址（可跳过，稍后在设置页填）
        AddressSetupScreen(
            initialAuthUrl = "",
            initialEduUrl = "",
            onSave = { authUrl, eduUrl ->
                val repo = com.gbu.classisland.data.settings.SettingsRepository(context)
                scope.launch {
                    repo.setEduAuthBaseUrl(authUrl)
                    repo.setEduBaseUrl(eduUrl)
                }
                prefs.edit().putBoolean("address_setup_shown", true).apply()
                showAddressSetup = false
            },
            onSkip = {
                prefs.edit().putBoolean("address_setup_shown", true).apply()
                showAddressSetup = false
            }
        )
    } else {
        AppNavHost()
    }
}
