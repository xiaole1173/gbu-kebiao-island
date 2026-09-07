// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.data.credits.CourseCatalog
import com.gbu.classisland.data.security.SecureStore
import com.gbu.classisland.data.settings.AppSettings
import com.gbu.classisland.data.settings.SettingsRepository
import com.gbu.classisland.edu.EduApi
import com.gbu.classisland.edu.SyncRepository
import com.gbu.classisland.reminder.ReminderScheduler
import com.gbu.classisland.sync.SyncWorker
import com.gbu.classisland.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 同步状态。 */
sealed class SyncUiState {
    data object Idle : SyncUiState()
    data object Syncing : SyncUiState()
    data class Success(val changed: Boolean, val count: Int) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

/** 应用更新状态。 */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Available(val info: UpdateManager.UpdateInfo) : UpdateUiState()
    data class Downloading(val progress: Float) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val secureStore = SecureStore(app)
    private val db = AppDatabase.get(app)

    val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** 是否已配置教务凭据。 */
    val hasCredentials = MutableStateFlow(!secureStore.read(KEY_USER).isNullOrBlank())

    val syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)

    /** 应用更新状态（Gitee 发行版）。 */
    val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)

    /**
     * 检查更新：自动检查受 6 小时间隔限制（[force]=true 手动检查不受限）。
     */
    fun checkForUpdate(force: Boolean = false) {
        val app = getApplication<Application>()
        if (updateState.value == UpdateUiState.Checking) return
        if (!force && !UpdateManager.shouldAutoCheck(app)) return
        viewModelScope.launch {
            updateState.value = UpdateUiState.Checking
            val info = UpdateManager.checkLatest(app)
            updateState.value = when {
                info == null -> UpdateUiState.Error("检查更新失败，请稍后重试")
                info.versionCode > UpdateManager.localVersionCode(app) ->
                    UpdateUiState.Available(info)
                else -> UpdateUiState.UpToDate
            }
        }
    }

    /** 下载并安装新版本；未授权"安装未知来源"时引导去设置。 */
    fun downloadAndInstall(info: UpdateManager.UpdateInfo) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            updateState.value = UpdateUiState.Downloading(0f)
            runCatching {
                val file = UpdateManager.download(app, info) { p ->
                    updateState.value = UpdateUiState.Downloading(p)
                }
                if (UpdateManager.install(app, file)) {
                    // 已交给系统安装器，等用户确认
                } else {
                    updateState.value = UpdateUiState.Error("请先允许「安装未知来源应用」")
                    UpdateManager.openInstallPermissionSettings(app)
                }
            }.onFailure { e ->
                updateState.value = UpdateUiState.Error("下载失败：${e.message}")
            }
        }
    }

    /** 本科累计学分统计（跨学期叠加，课程库驱动：学分/性质/类别取自教务课程库）。 */
    // 计算放 Default 线程、去重、常驻：避免主线程被占用导致触摸/滚动输入延迟（卡顿感）
    val creditSummary: StateFlow<CourseCatalog.CreditSummary> =
        combine(db.courseDao().observeAll(), db.libraryCourseDao().observeAll()) { courses, lib ->
            CourseCatalog.summarize(courses, lib)
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, CourseCatalog.CreditSummary())

    /** 本学期学分明细（仅当前学期，用于展示"本学期"而非累计）。 */
    val currentSemesterCredits: StateFlow<CourseCatalog.CreditSummary> =
        combine(db.courseDao().observeAll(), db.libraryCourseDao().observeAll(), settings) { all, lib, s ->
            val sem = s?.currentSemesterId
            CourseCatalog.summarize(
                if (sem.isNullOrBlank()) all else all.filter { it.semesterId == sem },
                lib
            )
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, CourseCatalog.CreditSummary())

    /** 电池优化是否已豁免（小米需设"无限制"否则后台被杀、提醒失效）。 */
    val batteryOptimizationIgnored = MutableStateFlow(checkBatteryOptimization())

    /** 灵动岛常驻通知权限（Android 16+ 需 POST_PROMOTED_NOTIFICATIONS）。 */
    val promotedNotifEnabled = MutableStateFlow(checkPromotedNotif())

    /** 允许安装未知来源应用（应用内更新安装 APK）。 */
    val installPackagesGranted = MutableStateFlow(checkInstallPackages())

    /** 提醒链路自检状态。 */
    data class ReminderChecks(
        val notificationsEnabled: Boolean = true,
        val exactAlarmGranted: Boolean = true
    )

    val reminderChecks = MutableStateFlow(ReminderChecks())

    /** 刷新提醒相关权限/设置状态。 */
    fun refreshReminderChecks() {
        val ctx = getApplication<Application>()
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        reminderChecks.value = ReminderChecks(
            notificationsEnabled = nm?.areNotificationsEnabled() ?: true,
            exactAlarmGranted = com.gbu.classisland.reminder.ReminderScheduler.canScheduleExact(ctx)
        )
        batteryOptimizationIgnored.value = checkBatteryOptimization()
        promotedNotifEnabled.value = checkPromotedNotif()
        installPackagesGranted.value = checkInstallPackages()
    }

    /** 打开"精确闹钟"设置页（Android 12+，否则提醒可能延迟）。 */
    fun openExactAlarmSettings() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        val context = getApplication<Application>()
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun checkBatteryOptimization(): Boolean {
        val pm = getApplication<Application>().getSystemService(android.os.PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) == true
    }

    /** 灵动岛常驻通知权限（Android 16 起为运行时权限；16 以下视为已授权）。 */
    private fun checkPromotedNotif(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 36) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication<Application>(),
            "android.permission.POST_PROMOTED_NOTIFICATIONS"
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun checkInstallPackages(): Boolean =
        getApplication<Application>().packageManager.canRequestPackageInstalls()

    fun refreshPermissionStatus() {
        batteryOptimizationIgnored.value = checkBatteryOptimization()
        promotedNotifEnabled.value = checkPromotedNotif()
        installPackagesGranted.value = checkInstallPackages()
    }

    /** 打开本应用通知设置页（调整通知级别：须为"所有通知/高"才能响铃+全屏）。 */
    fun openNotificationSettings() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).putExtra(
            android.provider.Settings.EXTRA_APP_PACKAGE,
            context.packageName
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 打开系统"电池优化"设置页（引导用户设为不限制）。 */
    fun openBatteryOptimizationSettings() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 打开本应用系统设置页（自启动/权限等，小米常在此管理）。 */
    fun openAppSystemSettings() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 直达小米自启动管理页（MIUI/HyperOS 私有页面，无检测 API 只能跳转引导）。
     * 各版本 activity 路径可能不同，逐个尝试；失败回退应用详情页。
     */
    fun openAutostartSettings() {
        val context = getApplication<Application>()
        val pkgName = context.packageName
        val candidates = listOf(
            // 新版 HyperOS/MIUI 自启动管理
            android.content.Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ).putExtra("extra_pkgname", pkgName),
            android.content.Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            // 旧版 MIUI
            android.content.Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartActivity"
                )
            )
        )
        for (intent in candidates) {
            runCatching {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        // 全部失败：回退到应用详情页（自启动设置通常也在里面）
        openAppSystemSettings()
    }

    /** 发送一条测试提醒通知。 */
    fun sendTestReminder() {
        com.gbu.classisland.notification.ClassNotifier.sendTestReminder(getApplication())
    }

    /** 测试提醒的提示文本（延迟触发前显示）。 */
    val testReminderHint = MutableStateFlow<String?>(null)

    /**
     * 带延迟的"提前上课提醒"测试。
     * 直接设置 5 秒后的系统闹钟（AlarmManager），走与正式提醒完全相同的
     * 广播链路 → 发高优先级通知（声音 + 震动），可验证完整链路。
     */
    fun sendTestReminderWithHint() {
        val context = getApplication<Application>()
        val course = com.gbu.classisland.data.Course(
            name = "测试上课提醒",
            location = "示例教室",
            teacher = "课表小岛",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = "1",
            id = -1
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val intent = android.content.Intent(context, com.gbu.classisland.reminder.ReminderScheduler.ReminderReceiver::class.java)
            .setAction(com.gbu.classisland.reminder.ReminderScheduler.ACTION_REMIND)
            .putExtra(com.gbu.classisland.reminder.ReminderScheduler.EXTRA_COURSE, json.encodeToString(com.gbu.classisland.data.Course.serializer(), course))
            .putExtra(com.gbu.classisland.reminder.ReminderScheduler.EXTRA_DATE, "示例日期")
            .putExtra(com.gbu.classisland.reminder.ReminderScheduler.EXTRA_START, "08:00")
        val pi = android.app.PendingIntent.getBroadcast(
            context, 999, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarm = context.getSystemService(android.app.AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + 5000
        if (com.gbu.classisland.reminder.ReminderScheduler.canScheduleExact(context)) {
            alarm.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarm.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        testReminderHint.value = "全屏提醒将在 5 秒后触发，现在可以锁屏或回到桌面（系统闹钟保证触发）"
    }

    /** 保存凭据（Keystore 加密）并立即同步。 */
    fun saveCredentialsAndSync(userName: String, password: String) {
        if (userName.isBlank()) {
            syncState.value = SyncUiState.Error("账号不能为空")
            return
        }
        // 密码留空则沿用已保存的密码（用于修改账号/重试场景）
        val pass = if (password.isBlank()) secureStore.read(KEY_PASS) else password
        if (pass.isNullOrBlank()) {
            syncState.value = SyncUiState.Error("请输入密码")
            return
        }
        viewModelScope.launch {
            secureStore.save(KEY_USER, userName.trim())
            secureStore.save(KEY_PASS, pass)
            settingsRepo.setEduUserName(userName.trim())
            hasCredentials.value = true
            runSync(userName.trim(), pass)
        }
    }

    /** 用已保存凭据重新同步。 */
    fun resync() {
        val user = secureStore.read(KEY_USER) ?: return
        val pass = secureStore.read(KEY_PASS) ?: return
        viewModelScope.launch { runSync(user, pass) }
    }

    private suspend fun runSync(user: String, pass: String) {
        if (syncState.value is SyncUiState.Syncing) return
        syncState.value = SyncUiState.Syncing
        try {
            val api = com.gbu.classisland.edu.EduApi()
            val repo = SyncRepository(api, db.courseDao(), db.libraryCourseDao())
            val result = repo.sync(userName = user, password = pass)
            result.semesterId?.let { settingsRepo.setCurrentSemester(it) }
            tryAutoDetectTermStart(api)
            // 重排提醒/刷新小组件失败不影响同步成功（如未设置开学日期等）
            runCatching { ReminderScheduler.reschedule(getApplication()) }
            runCatching { com.gbu.classisland.widget.TodayWidgetProvider.refreshAll(getApplication()) }
            syncState.value = SyncUiState.Success(result.changed, result.syncedCount)
        } catch (e: SyncRepository.SyncError) {
            syncState.value = SyncUiState.Error(when (e) {
                SyncRepository.SyncError.LoginFailed -> "登录失败：账号或密码错误，请检查后重试"
                SyncRepository.SyncError.FetchFailed -> "获取课表失败，请稍后重试"
                is SyncRepository.SyncError.Network -> e.msg
                is SyncRepository.SyncError.Message -> e.msg
                SyncRepository.SyncError.NeedLogin -> "需要登录"
            })
        } catch (e: Exception) {
            syncState.value = SyncUiState.Error("同步失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 从教务系统反推开学日期（仅开学后有效）：
     * 第 1 周周一 = 今天 - (当前周-1)周 - (周几-1)天。
     * 未开学（当前周=0）时放弃，保留用户手动设置。
     */
    private suspend fun tryAutoDetectTermStart(api: com.gbu.classisland.edu.EduApi) {
        val week = api.fetchCurrentWeek() ?: return
        if (week <= 0) return
        val today = java.time.LocalDate.now()
        val monday = today
            .minusDays((today.dayOfWeek.value - 1).toLong())
            .minusWeeks((week - 1).toLong())
        settingsRepo.setTermStart(monday.toString())
    }

    fun clearCredentials() {
        secureStore.delete(KEY_USER)
        secureStore.delete(KEY_PASS)
        hasCredentials.value = false
        viewModelScope.launch { settingsRepo.setEduUserName("") }
    }

    fun setRemindBefore(minutes: Int) = viewModelScope.launch {
        settingsRepo.setRemindBefore(minutes)
        ReminderScheduler.reschedule(getApplication())
    }

    fun setRemindAfter(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setRemindAfter(enabled)
    }

    fun setAutoSync(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAutoSync(enabled)
        if (enabled) SyncWorker.schedulePeriodic(getApplication())
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        settingsRepo.setThemeMode(mode)
    }

    /** 开学日期（校历）：决定当前周次与提醒时间计算。 */
    fun setTermStartDate(date: String) = viewModelScope.launch {
        settingsRepo.setTermStart(date)
        ReminderScheduler.reschedule(getApplication())
        com.gbu.classisland.widget.TodayWidgetProvider.refreshAll(getApplication())
    }

    // ── 导出 ────────────────────────────────────────────────────────────────

    fun exportIcs(uri: android.net.Uri) = viewModelScope.launch {
        runCatching {
            val s = settingsRepo.settings.first()
            if (s.currentSemesterId.isBlank()) return@launch
            val courses = db.courseDao().getBySemester(s.currentSemesterId)
            val termStart = java.time.LocalDate.parse(s.termStartDate)
            val content = com.gbu.classisland.data.export.IcalExporter.buildIcs(courses, termStart, s.sections)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            }
        }.onFailure { syncState.value = SyncUiState.Error("导出失败：${it.message}") }
    }

    fun exportCsv(uri: android.net.Uri) = viewModelScope.launch {
        runCatching {
            val s = settingsRepo.settings.first()
            if (s.currentSemesterId.isBlank()) return@launch
            val courses = db.courseDao().getBySemester(s.currentSemesterId)
            val content = com.gbu.classisland.data.export.CsvExporter.buildCsv(courses)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            }
        }.onFailure { syncState.value = SyncUiState.Error("导出失败：${it.message}") }
    }

    companion object {
        private const val KEY_USER = "edu_user"
        private const val KEY_PASS = "edu_pass"
    }
}
