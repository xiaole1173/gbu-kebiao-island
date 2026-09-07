// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gbu.classisland.update.UpdateManager

/**
 * 全屏新手引导（首次进入显示，不是窗口弹窗）：
 * 第一步：登录教务统一身份认证；第二步：授权必要权限。
 * 均可跳过，稍后在设置页自行完成。
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    var page by remember { mutableIntStateOf(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(420)) { it } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(420)) { -it / 3 } + fadeOut(tween(300)))
                } else {
                    (slideInHorizontally(tween(420)) { -it } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(420)) { it / 3 } + fadeOut(tween(300)))
                }
            },
            label = "onboarding"
        ) { p ->
            when (p) {
                0 -> LoginStep(viewModel, onNext = { page = 1 })
                1 -> PermissionStep(viewModel, onFinish = { page = 2 }, onBack = { page = 0 })
                else -> DateStep(viewModel, onFinish = onFinished, onBack = { page = 1 })
            }
        }
    }
}

/** 第一步：登录大湾区大学统一身份认证（可选，不登录也能下一步）。 */
@Composable
private fun LoginStep(
    viewModel: SettingsViewModel,
    onNext: () -> Unit
) {
    val hasCredentials by viewModel.hasCredentials.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    var userName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "您好，让我们来帮您完成初始设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("第一步：登录大湾区大学统一身份认证", style = MaterialTheme.typography.titleMedium)
        Text(
            "这是为了获取您的课表信息。您可以先选择跳过，再在应用的设置页自行登录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            label = { Text("账号（手机号/学号）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // 密码框始终显示（输错后可重输；已有凭据时留空沿用原密码）
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (hasCredentials) "密码（留空使用原密码）" else "密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.saveCredentialsAndSync(userName, password) },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (hasCredentials) "更新密码并同步" else "登录并同步", fontSize = 16.sp)
        }
        when (val s = syncState) {
            is SyncUiState.Syncing -> Text("正在同步…", style = MaterialTheme.typography.bodySmall)
            is SyncUiState.Success -> Text(
                if (s.changed) "同步完成：共 ${s.count} 条课程" else "课表无变化（${s.count} 条）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            is SyncUiState.Error -> Text(
                s.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            is SyncUiState.Idle -> if (hasCredentials) {
                Text("账号已配置，可直接下一步", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onNext) { Text("下一步") }
        }
    }
}

/** 第二步：授权必要权限。 */
@Composable
private fun PermissionStep(
    viewModel: SettingsViewModel,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val checks by viewModel.reminderChecks.collectAsState()
    val fullScreen by viewModel.fullScreenEnabled.collectAsState()
    val batteryIgnored by viewModel.batteryOptimizationIgnored.collectAsState()
    // 安装未知来源应用：从系统设置返回后刷新状态
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }

    // 从系统设置返回时刷新状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshReminderChecks()
        canInstall = context.packageManager.canRequestPackageInstalls()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshReminderChecks() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "第二步：完成所有必要的权限设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "请依次点击下方按钮授权权限，保证应用的所有功能能正常运行。您可以跳过，在设置页自行授权。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))

        PermissionGrantItem(
            title = "通知权限",
            desc = "上课提醒弹窗与响铃",
            granted = checks.notificationsEnabled,
            onGrant = {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.refreshReminderChecks()
                }
            }
        )
        PermissionGrantItem(
            title = "精确闹钟",
            desc = "确保上课提醒准时触发",
            granted = checks.exactAlarmGranted,
            onGrant = { viewModel.openExactAlarmSettings() }
        )
        PermissionGrantItem(
            title = "全屏提醒",
            desc = "锁屏时全屏弹出上课提醒",
            granted = fullScreen,
            onGrant = { viewModel.openFullScreenSettings() }
        )
        PermissionGrantItem(
            title = "电池优化",
            desc = "设为「无限制」，避免后台被杀导致提醒失效",
            granted = batteryIgnored,
            onGrant = { viewModel.openBatteryOptimizationSettings() }
        )
        PermissionGrantItem(
            title = "安装未知来源应用",
            desc = "允许应用内自动更新时安装新版（需去系统设置开启）",
            granted = canInstall,
            onGrant = { UpdateManager.openInstallPermissionSettings(context) }
        )
        // 自启动：小米私有权限，无法自动检测状态，仅引导跳转开启
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("自启动", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "开启后开机/后台自动运行，提醒更可靠（小米需手动开启）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    "无法自动检测",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Button(
                onClick = { viewModel.openAutostartSettings() },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("去开启自启动")
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("上一步") }
            Button(onClick = onFinish) { Text("完成设置") }
        }
    }
}

/** 单条权限授权项：状态 + 授权按钮。 */
@Composable
private fun PermissionGrantItem(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                if (granted) "已授权" else "未授权",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = onGrant,
            enabled = !granted,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(if (granted) "已开启" else "去授权")
        }
    }
}

/** 第三步：设置开学日期（可选，稍后可改）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(
    viewModel: SettingsViewModel,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            java.time.LocalDate.parse(settings?.termStartDate ?: "")
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "第三步：设置开学日期",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "开学日期（第 1 周周一）用于计算当前周次与上课提醒时间。您可以稍后在应用的设置页校历中修改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(settings?.termStartDate?.ifBlank { "选择开学日期" } ?: "选择开学日期")
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("上一步") }
            Button(onClick = onFinish) { Text("完成设置") }
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        viewModel.setTermStartDate(date.toString())
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
