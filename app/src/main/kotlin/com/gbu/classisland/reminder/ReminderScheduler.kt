// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.data.Course
import com.gbu.classisland.data.settings.SettingsRepository
import com.gbu.classisland.model.SectionTime
import com.gbu.classisland.notification.ClassNotifier
import com.gbu.classisland.util.TimetableEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 上课提醒调度：找到最近的"上课时间 - 提前分钟"闹钟，AlarmManager 精确触发。
 *
 * 触发链路（标准闹钟方案，保证前后台/锁屏都能弹出）：
 *   AlarmManager → ReminderReceiver（广播）
 *     ├─ 前台：直接 startActivity 拉起全屏提醒（前台启动无限制）
 *     └─ 后台/锁屏：发 fullScreenIntent 通知，由系统保证立即全屏
 *   每次触发后自动重排下一个，提醒链路不断。
 */
object ReminderScheduler {

    const val ACTION_REMIND = "com.gbu.classisland.ACTION_CLASS_REMINDER"

    const val EXTRA_COURSE = "course"
    const val EXTRA_START = "start"
    const val EXTRA_DATE = "date"

    private val dateFmt = DateTimeFormatter.ofPattern("M月d日")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val json = Json { ignoreUnknownKeys = true }

    /** 由上层（同步完成/开机/设置变更后）调用：基于当前课表与设置重排下一个提醒。 */
    suspend fun reschedule(context: Context) {
        val db = AppDatabase.get(context)
        val settings = SettingsRepository(context).settings
        val s = settings.first()
        val courses = db.courseDao().getBySemester(s.currentSemesterId)
        if (courses.isEmpty()) return
        if (s.termStartDate.isBlank()) return // 未设置开学日期，跳过重排（避免空串解析异常）
        val sections = s.sections
        val termStart = LocalDate.parse(s.termStartDate)
        scheduleNext(context, courses, sections, termStart, s.remindBeforeMinutes)
    }

    /** 精确闹钟是否可用（Android 12+ 需 SCHEDULE_EXACT_ALARM 授权）。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarm = context.getSystemService(AlarmManager::class.java)
        return alarm.canScheduleExactAlarms()
    }

    fun scheduleNext(
        context: Context,
        courses: List<Course>,
        sections: List<SectionTime>,
        termStart: LocalDate,
        remindMinutes: Int
    ) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val week = TimetableEngine.currentWeek(termStart, now.toLocalDate())
        if (week <= 0) return

        var nearest: Pair<LocalDateTime, Course>? = null
        for (dayOffset in 0..7) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            val dayCourses = TimetableEngine.coursesOn(courses, date, week)
            for (c in dayCourses) {
                val (start, _) = TimetableEngine.sessionTimes(c, date, sections) ?: continue
                val remindAt = start.minusMinutes(remindMinutes.toLong())
                if (remindAt.isAfter(now)) {
                    if (nearest == null || remindAt.isBefore(nearest!!.first)) {
                        nearest = remindAt to c
                    }
                }
            }
        }

        val (trigger, course) = nearest ?: return
        val atEpoch = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 闹钟触发 → 广播接收器（Receiver 负责前台直启 + 后台 fullScreenIntent 通知）
        val pi = remindBroadcastPendingIntent(context, course, trigger)
        if (canScheduleExact(context)) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpoch, pi)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, atEpoch, pi) // 无精确权限时降级
        }

        // 顺带刷新"当前/下一节课"常驻通知（供岛/胶囊显示）
        val upcoming = TimetableEngine.upcoming(courses, now, sections, termStart)
        ClassNotifier.showNowClass(context, upcoming)
    }

    private fun remindBroadcastPendingIntent(
        context: Context,
        course: Course,
        trigger: LocalDateTime
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_COURSE, json.encodeToString(Course.serializer(), course))
            .putExtra(EXTRA_START, trigger.format(timeFmt))
            .putExtra(EXTRA_DATE, trigger.toLocalDate().format(dateFmt))
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 提醒广播接收器：前台直接全屏提醒；后台/锁屏 fullScreenIntent 通知（响铃+全屏），并自动重排。 */
    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_REMIND) return
            val result = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val courseJson = intent.getStringExtra(EXTRA_COURSE)
                    val course = courseJson
                        ?.let { runCatching { json.decodeFromString<Course>(it) }.getOrNull() }
                    if (course != null) {
                        val dateText = intent.getStringExtra(EXTRA_DATE) ?: ""
                        val startText = intent.getStringExtra(EXTRA_START) ?: ""
                        if (com.gbu.classisland.ForegroundTracker.isForeground) {
                            // 前台：直接全屏拉起提醒界面（前台无后台启动限制）
                            runCatching {
                                val actIntent = Intent(context, ReminderActivity::class.java)
                                    .putExtra(ReminderActivity.EXTRA_COURSE, courseJson)
                                    .putExtra(ReminderActivity.EXTRA_DATE, dateText)
                                    .putExtra(ReminderActivity.EXTRA_START, startText)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                context.startActivity(actIntent)
                            }
                        } else {
                            // 后台/锁屏：fullScreenIntent 闹钟式通知（响铃 + 系统拉起全屏）
                            ClassNotifier.sendClassReminder(context, course, dateText, startText)
                        }
                    }
                    reschedule(context)
                } finally {
                    result.finish()
                }
            }
        }
    }
}
