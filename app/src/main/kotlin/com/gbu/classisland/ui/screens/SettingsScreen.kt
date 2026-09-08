// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gbu.classisland.data.credits.Category
import com.gbu.classisland.data.credits.CourseCatalog
import com.gbu.classisland.model.DefaultSections

/** 设置页：教务同步 / 学分 / 校历 / 提醒 / 后台自检 / 节次时间 / 数据管理（懒加载，避免首屏卡顿）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val hasCredentials by viewModel.hasCredentials.collectAsState()
    val batteryIgnored by viewModel.batteryOptimizationIgnored.collectAsState()
    val promotedNotif by viewModel.promotedNotifEnabled.collectAsState()
    val installPackages by viewModel.installPackagesGranted.collectAsState()
    val credits by viewModel.creditSummary.collectAsState()
    val semCredits by viewModel.currentSemesterCredits.collectAsState()
    val testHint by viewModel.testReminderHint.collectAsState()
    val checks by viewModel.reminderChecks.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    // 进入设置页 / 从系统设置返回（onResume）时刷新权限状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshReminderChecks() }

    var userName by remember { mutableStateOf(settings?.eduUserName ?: "") }
    var password by remember { mutableStateOf("") }
    var eduUrl by remember { mutableStateOf(settings?.eduBaseUrl ?: "") }
    // 提醒分钟数：拖动时只改本地，松手才提交（避免高频写 DataStore/重排提醒卡顿）
    var remindBefore by remember { mutableStateOf(settings?.remindBeforeMinutes ?: 10) }

    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri -> uri?.let { viewModel.exportIcs(it) } }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    // 通知权限请求（Android 13+ 必须授权才能弹通知）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.sendTestReminderWithHint() }
    // 灵动岛常驻通知权限（Android 16+）
    val promotedNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshReminderChecks() }

    val context = LocalContext.current
    fun requestOrSendTestReminder() {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.sendTestReminderWithHint()
        } else {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            java.time.LocalDate.parse(settings?.termStartDate ?: "")
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    )

    // 懒加载列表：只组合可见卡片，避免首屏卡顿
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // ── 教务同步 ──────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("教务同步", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "登录学校统一身份认证，仅同步你的课表（不涉及任何他人数据）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    // 教务系统地址：学生手动填写（不在 App 内置任何学校域名）
                    OutlinedTextField(
                        value = eduUrl,
                        onValueChange = { eduUrl = it },
                        label = { Text("教务系统地址（如 https://jwxt.学校域名）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.setEduBaseUrl(eduUrl) }) { Text("保存地址") }
                        if ((settings?.eduBaseUrl ?: "").isNotBlank()) {
                            Text(
                                "当前：${settings?.eduBaseUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("账号（手机号/学号）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 密码框始终显示（失败时可重输；已有凭据时留空沿用原密码）
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (hasCredentials) "密码（留空使用原密码）" else "密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasCredentials) {
                            if (password.isNotBlank()) {
                                // 重输了密码 → 更新密码并同步
                                Button(onClick = { viewModel.saveCredentialsAndSync(userName, password, eduUrl) }) {
                                    Text("更新密码并同步")
                                }
                            } else {
                                Button(onClick = { viewModel.resync() }) { Text("立即同步") }
                            }
                            OutlinedButton(onClick = { viewModel.clearCredentials() }) {
                                Text("清除账号")
                            }
                        } else {
                            Button(onClick = { viewModel.saveCredentialsAndSync(userName, password, eduUrl) }) {
                                Text("保存并同步")
                            }
                        }
                    }
                    when (val s = syncState) {
                        is SyncUiState.Idle -> if (hasCredentials) {
                            Text("已配置账号，点「立即同步」更新课表", style = MaterialTheme.typography.bodySmall)
                        }
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
                    }
                }
            }
        }

        item {
            // ── 学分统计 ──────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("学分统计（本科累计）", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本学期已选课程", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${semCredits.courseCount} 门",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本学期学分", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${fmtCredit(semCredits.totalCredits)} 分（必修 ${fmtCredit(semCredits.requiredCredits)} + 选修 ${fmtCredit(semCredits.electiveCredits)}）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    HorizontalDivider()
                    Text(
                        "通识选修要求（本科四年累计）：至少 12 学分，且覆盖至少 3 类",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本科累计选修", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${fmtCredit(credits.electiveCredits)} / ${fmtCredit(CourseCatalog.REQUIRED_ELECTIVE_CREDITS)} 分" +
                                if (credits.creditRequirementMet) " · 已达标"
                                else " · 还差 ${fmtCredit(CourseCatalog.REQUIRED_ELECTIVE_CREDITS - credits.electiveCredits)} 分",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (credits.creditRequirementMet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    LinearProgressIndicator(
                        progress = credits.creditProgress,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Text(
                        "各学期选修学分会自动累加，12 分为本科阶段（四年）累计要求，不要求大一就修满",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "类别覆盖（累计 ${credits.categoriesCovered.size}/${Category.entries.size}）",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (credits.categoryRequirementMet) "已覆盖至少 3 类"
                            else "需再覆盖 ${CourseCatalog.REQUIRED_CATEGORY_COUNT - credits.categoriesCovered.size} 类",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (credits.categoryRequirementMet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Category.entries.forEach { cat ->
                            val v = credits.electiveByCategory[cat] ?: 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${cat.label} ${cat.desc}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                                Text(
                                    if (v > 0) "${fmtCredit(v)} 分" else "未选",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = if (v > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // ── 校历 ──────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("校历", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "开学日期（第 1 周周一），用于计算当前周次与提醒时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("开学日期")
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Text(settings?.termStartDate?.ifBlank { "选择日期" } ?: "选择日期")
                        }
                    }
                }
            }
        }

        item {
            // ── 提醒 ──────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("提醒", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("提前提醒", modifier = Modifier.weight(1f))
                        Text(
                            "${settings?.remindBeforeMinutes ?: 10} 分钟",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Slider(
                        value = remindBefore.toFloat(),
                        onValueChange = { remindBefore = it.toInt() },
                        onValueChangeFinished = { viewModel.setRemindBefore(remindBefore) },
                        valueRange = 1f..30f,
                        steps = 8
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动同步（每日一次）")
                        Switch(
                            checked = settings?.autoSyncEnabled ?: true,
                            onCheckedChange = viewModel::setAutoSync
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { requestOrSendTestReminder() }) {
                            Text("测试提醒")
                        }
                        OutlinedButton(onClick = {
                            com.gbu.classisland.notification.LiveUpdateNotifier.showTest(context)
                        }) {
                            Text("测试灵动岛")
                        }
                    }
                    testHint?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            // ── 权限总览 ──────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("权限总览", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "上课提醒需要「通知 + 声音 + 震动」，灵动岛需「常驻通知」，应用内更新需「安装未知来源」。逐项授权后可一键自检。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = "通知权限",
                        desc = "上课提醒（声音 + 震动）与灵动岛展示",
                        granted = checks.notificationsEnabled,
                        actionLabel = "去授权",
                        onAction = {
                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.openNotificationSettings()
                            }
                        }
                    )
                    PermissionRow(
                        title = "灵动岛常驻通知",
                        desc = "上课前后的状态栏胶囊 / 灵动岛倒计时",
                        granted = promotedNotif,
                        actionLabel = "去授权",
                        onAction = {
                            if (android.os.Build.VERSION.SDK_INT >= 36) {
                                promotedNotifLauncher.launch("android.permission.POST_PROMOTED_NOTIFICATIONS")
                            }
                        }
                    )
                    PermissionRow(
                        title = "精确闹钟",
                        desc = "确保上课提醒准时触发",
                        granted = checks.exactAlarmGranted,
                        actionLabel = "去授权",
                        onAction = { viewModel.openExactAlarmSettings() }
                    )
                    PermissionRow(
                        title = "电池优化",
                        desc = "设为「无限制」，避免后台被杀提醒失效",
                        granted = batteryIgnored,
                        actionLabel = "去设置",
                        onAction = { viewModel.openBatteryOptimizationSettings() }
                    )
                    PermissionRow(
                        title = "安装未知来源应用",
                        desc = "允许应用内自动更新时安装新版",
                        granted = installPackages,
                        actionLabel = "去开启",
                        onAction = {
                            com.gbu.classisland.update.UpdateManager.openInstallPermissionSettings(context)
                        }
                    )
                    PermissionRow(
                        title = "自启动",
                        desc = "开机/后台自动重排提醒（小米需手动开启）",
                        granted = null,
                        actionLabel = "去设置",
                        onAction = { viewModel.openAutostartSettings() }
                    )
                    HorizontalDivider()
                    Text(
                        "小米注意：通知 → 课表小岛 →「上课提醒」类别需设为「所有通知/高」才能响铃+震动；自启动需手动允许。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            // ── 节次时间表 ────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("节次时间表", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "每节 35 分钟：周一三五 2 节/大节（75 分钟），周二四 3 节/大节（115 分钟）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text("周一三五", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        DefaultSections.list.filter { it.section % 2 == 1 }.forEach { s ->
                            val end = DefaultSections.list.find { it.section == s.section + 1 }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("第 ${s.section}-${s.section + 1} 节", fontSize = 13.sp)
                                Text(
                                    "${s.start} - ${end?.end ?: ""}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    Text("周二四", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        DefaultSections.tueThu.filter { it.section in setOf(1, 4, 7, 10, 13, 16) }.forEach { s ->
                            val end = DefaultSections.tueThu.find { it.section == s.section + 2 }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("第 ${s.section}-${s.section + 2} 节", fontSize = 13.sp)
                                Text(
                                    "${s.start} - ${end?.end ?: ""}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // ── 数据管理 ──────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "导出到系统日历 / Excel，作为课表备份与双重提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { icsLauncher.launch("课表.ics") }) {
                            Text("导出 iCal")
                        }
                        OutlinedButton(onClick = { csvLauncher.launch("课表.csv") }) {
                            Text("导出 CSV")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "课表小岛 v0.1 · 本地数据加密存储，凭据不上传任何服务器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            // ── 关于 ──────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    val versionName = remember {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: ""
                    }
                    AboutRow("版本", if (versionName.isBlank()) "" else "v$versionName")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("检查更新（发行版）", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { viewModel.checkForUpdate(force = true) }) {
                            Text("检查", fontSize = 13.sp)
                        }
                    }
                    // 更新状态
                    when (val u = updateState) {
                        is UpdateUiState.Checking -> Text(
                            "正在检查更新…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        is UpdateUiState.UpToDate -> Text(
                            "已是最新版本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is UpdateUiState.Available -> {
                            Text(
                                "发现新版本 ${u.info.versionName}：${u.info.changelog}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = { viewModel.downloadAndInstall(u.info) },
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) { Text("立即更新") }
                        }
                        is UpdateUiState.Downloading -> Text(
                            "正在下载… ${(u.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        is UpdateUiState.Error -> Text(
                            u.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        is UpdateUiState.Idle -> {}
                    }
                    HorizontalDivider()
                    AboutRow("版权", "© 2026 影 / Shadow / xiaole1173")
                    AboutRow("开源协议", "MIT License")
                    Text(
                        "本项目为开源软件，源码已开源（点击打开仓库）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val uriHandler = LocalUriHandler.current
                    Text(
                        "GitHub · github.com/xiaole1173/gbu-kebiao-island",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/xiaole1173/gbu-kebiao-island")
                        }
                    )
                    Text(
                        "Gitee · gitee.com/xiaole1173/gbu-kebiao-island",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://gitee.com/xiaole1173/gbu-kebiao-island")
                        }
                    )
                }
            }
        }
    }

    // 开学日期选择
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        viewModel.setTermStartDate(date.toString())
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 学分显示：去掉整数的小数位（23.0 → 23）。 */
private fun fmtCredit(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/**
 * 权限行：标题 + 说明在左，状态按钮在右（与各权限齐平）。
 * granted=true → 按钮禁用显示"已开启"；false → 按钮可点"去授权"；null → 无法检测，按钮"去设置"。
 */
@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean?,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
        }
        val grantedNow = granted ?: false
        OutlinedButton(
            onClick = onAction,
            enabled = !grantedNow,
            modifier = Modifier.height(40.dp)
        ) {
            Text(
                if (granted == null) actionLabel
                else if (grantedNow) "已开启" else actionLabel,
                fontSize = 12.sp
            )
        }
    }
}

/** 关于行：标签 + 值（右对齐加粗）。 */
@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
