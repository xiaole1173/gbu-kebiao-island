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
import com.gbu.classisland.notification.LiveUpdateNotifier
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
 * 上课提醒 + 灵动岛调度。
 *
 * - 上课提醒：找到最近的"上课时间 - 提前分钟"闹钟，AlarmManager 精确触发 →
 *   发一条高优先级通知（声音 + 震动，由提醒频道保证）。每次触发后自动重排下一个。
 * - 灵动岛：为最近一节课调用 [LiveUpdateNotifier.scheduleForClass]，上课前候课
 *   倒计时、上课中进度条，提升为状态栏胶囊。
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
            // 校历节假日/停课日：当天无课，不提醒、不开灵动岛
            if (com.gbu.classisland.data.calendar.AcademicCalendar.isHoliday(date)) continue
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

        // 上课提醒闹钟 → 广播接收器（发"提前 X 分钟"通知，声音+震动）
        val pi = remindBroadcastPendingIntent(context, course, trigger)
        if (canScheduleExact(context)) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpoch, pi)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, atEpoch, pi) // 无精确权限时降级
        }

        // 灵动岛：为最近一节课安排候课倒计时 + 上课进度
        val (start, end) = TimetableEngine.sessionTimes(course, trigger.toLocalDate(), sections)
            ?: return
        LiveUpdateNotifier.scheduleForClass(
            context, course.name, course.location, course.teacher, start, end, now, leadMinutes = remindMinutes
        )
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

    /** 提醒 + 灵动岛 tick 接收器：发提前提醒通知；刷新灵动岛进度；并自动重排。 */
    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LiveUpdateNotifier.ACTION_CLOSE -> {
                    // 「关闭」：取消灵动岛通知并终止 tick 链
                    LiveUpdateNotifier.cancel(context)
                    return
                }
                LiveUpdateNotifier.ACTION_TICK -> {
                    // 灵动岛进度 tick
                    LiveUpdateNotifier.onTick(
                        context,
                        intent.getStringExtra(LiveUpdateNotifier.EXTRA_NAME) ?: return,
                        intent.getStringExtra(LiveUpdateNotifier.EXTRA_ROOM).orEmpty(),
                        intent.getStringExtra(LiveUpdateNotifier.EXTRA_TEACHER).orEmpty(),
                        intent.getLongExtra(LiveUpdateNotifier.EXTRA_START, 0L),
                        intent.getLongExtra(LiveUpdateNotifier.EXTRA_END, 0L),
                        intent.getLongExtra(LiveUpdateNotifier.EXTRA_ANCHOR, 0L),
                        intent.getIntExtra(LiveUpdateNotifier.EXTRA_SCALE, 1),
                        intent.getLongExtra(LiveUpdateNotifier.EXTRA_WAIT_START, 0L),
                    )
                    return
                }
                ACTION_REMIND -> { /* 提前提醒通知，见下 */ }
                else -> return
            }

            val result = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val courseJson = intent.getStringExtra(EXTRA_COURSE)
                    val course = courseJson
                        ?.let { runCatching { json.decodeFromString<Course>(it) }.getOrNull() }
                    if (course != null) {
                        val dateText = intent.getStringExtra(EXTRA_DATE) ?: ""
                        val startText = intent.getStringExtra(EXTRA_START) ?: ""
                        // 提前 X 分钟提醒：高优先级通知（声音 + 震动，由频道保证）
                        ClassNotifier.sendClassReminder(context, course, dateText, startText)
                    }
                    reschedule(context)
                } finally {
                    result.finish()
                }
            }
        }
    }
}
