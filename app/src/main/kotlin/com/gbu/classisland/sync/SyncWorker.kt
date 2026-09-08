// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.data.security.SecureStore
import com.gbu.classisland.data.settings.SettingsRepository
import com.gbu.classisland.edu.EduApi
import com.gbu.classisland.edu.SyncRepository
import com.gbu.classisland.notification.ClassNotifier
import com.gbu.classisland.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 课表自动同步（WorkManager）。
 * 仅同步本人课表；失败重试有退避，遵守"绝不批量爬取"的低频原则。
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val secureStore = SecureStore(applicationContext)
        val user = secureStore.read(KEY_USER) ?: return Result.success() // 未配置，跳过
        val pass = secureStore.read(KEY_PASS) ?: return Result.success()
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settings
        // 未开启自动同步且非手动触发时跳过（手动触发用不同 tag 判断）
        if (!settings.first().autoSyncEnabled && !inputData.getBoolean(INPUT_MANUAL, false)) {
            return Result.success()
        }

        val db = AppDatabase.get(applicationContext)
        val baseUrl = settings.first().eduBaseUrl
        if (baseUrl.isBlank()) return Result.success() // 未配置教务地址，跳过自动同步
        val api = EduApi(baseUrl = baseUrl)
        val repo = SyncRepository(api, db.courseDao(), db.libraryCourseDao())

        return try {
            val result = repo.sync(userName = user, password = pass)
            // 记录当前学期与开学日期（用于周次计算）
            result.semesterId?.let { settingsRepo.setCurrentSemester(it) }
            tryAutoDetectTermStart(api, settingsRepo)
            // 重排提醒
            ReminderScheduler.reschedule(applicationContext)
            // 刷新桌面小组件
            com.gbu.classisland.widget.TodayWidgetProvider.refreshAll(applicationContext)
            // 变更通知（仅真正变化时）
            if (result.changed) {
                ClassNotifier.sendSyncResult(
                    applicationContext, true,
                    result.added.size, result.removed.size, result.modified.size
                )
            }
            Result.success()
        } catch (e: Exception) {
            // 登录失败等：允许重试，但限制次数由 WorkManager 退避策略处理
            Result.retry()
        }
    }

    companion object {
        /**
         * 从教务系统反推开学日期（仅开学后有效），未开学放弃。
         * 第 1 周周一 = 今天 - (当前周-1)周 - (周几-1)天。
         */
        private suspend fun tryAutoDetectTermStart(
            api: EduApi,
            settingsRepo: SettingsRepository
        ) {
            val week = api.fetchCurrentWeek() ?: return
            if (week <= 0) return
            val today = java.time.LocalDate.now()
            val monday = today
                .minusDays((today.dayOfWeek.value - 1).toLong())
                .minusWeeks((week - 1).toLong())
            settingsRepo.setTermStart(monday.toString())
        }

        const val WORK_NAME_PERIODIC = "edu_sync_periodic"
        private const val WORK_NAME_ONE_TIME = "edu_sync_now"
        private const val KEY_USER = "edu_user"
        private const val KEY_PASS = "edu_pass"
        private const val INPUT_MANUAL = "manual"

        /** 注册每日自动同步。 */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 立即手动同步一次。 */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(androidx.work.Data.Builder().putBoolean(INPUT_MANUAL, true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** 保存教务凭据（Keystore 加密）。 */
        fun saveCredentials(context: Context, user: String, pass: String) {
            val secureStore = SecureStore(context)
            secureStore.save(KEY_USER, user)
            secureStore.save(KEY_PASS, pass)
        }
    }
}
