// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gbu.classisland.MainActivity
import com.gbu.classisland.R
import com.gbu.classisland.data.Course
import com.gbu.classisland.util.UpcomingClass
import java.time.format.DateTimeFormatter

/** 构建并发送通知。 */
object ClassNotifier {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    /** 上课提醒通知 ID（供移除时取消）。 */
    fun reminderNotificationId(course: Course): Int =
        NOTIF_ID_REMINDER_BASE + course.id.hashCode() % 100

    /**
     * 提前上课提醒（高优先级 + 闹钟铃声 + 震动，来自 CHANNEL_REMINDER）：
     * 普通通知，点击进入 App，可自动取消。声音/震动由频道保证。
     */
    fun sendClassReminder(context: Context, course: Course, dateText: String, startText: String) {
        val content = buildString {
            append(dateText).append(' ').append(startText).append(" 开始 · ")
            append(course.location.ifBlank { "地点待定" })
        }
        notify(context, NotificationChannels.CHANNEL_REMINDER, reminderNotificationId(course)) {
            setSmallIcon(R.drawable.ic_stat_class)
            setContentTitle("上课提醒：${course.name}")
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(content))
            setAutoCancel(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
        }
    }

    /**
     * 持续显示"当前/下一节课"（低优先级常驻通知）。
     * 该通知为标准系统通知，可被 HyperBridge 等超级岛工具捕获上岛。
     */
    fun showNowClass(context: Context, upcoming: UpcomingClass?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (upcoming == null) {
            nm.cancel(NOTIF_ID_NOW)
            return
        }
        val c = upcoming.course
        val whenText = "${upcoming.start.format(timeFmt)} - ${upcoming.end.format(timeFmt)}"
        val title = if (upcoming.isOngoing) "正在上课：${c.name}" else "下一节：${c.name}"
        val text = buildString {
            append(if (upcoming.isOngoing) "上到 " else "开始于 ")
            append(whenText)
            if (c.location.isNotBlank()) append(" · ").append(c.location)
            if (c.teacher.isNotBlank()) append(" · ").append(c.teacher)
        }
        notify(context, NotificationChannels.CHANNEL_NOW, NOTIF_ID_NOW) {
            setSmallIcon(R.drawable.ic_stat_class)
            setContentTitle(title)
            setContentText(text)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setOngoing(true)
            setPriority(NotificationCompat.PRIORITY_LOW)
        }
    }

    /** 测试提醒：发一条"上课提醒"（声音 + 震动，无全屏）。 */
    fun sendTestReminder(context: Context) {
        val testCourse = Course(
            name = "测试上课提醒",
            location = "示例教室",
            teacher = "课表小岛",
            dayOfWeek = 1,
            startSection = 1,
            endSection = 2,
            weeks = "1",
            id = -1
        )
        sendClassReminder(context, testCourse, "示例日期", "08:00")
    }

    /** 同步结果通知。 */
    fun sendSyncResult(context: Context, changed: Boolean, added: Int, removed: Int, modified: Int) {
        if (!changed) return
        val title = "课表已更新"
        val text = buildString {
            if (added > 0) append("新增 $added 门 · ")
            if (removed > 0) append("取消 $removed 门 · ")
            if (modified > 0) append("调整 $modified 门")
            if (isBlank()) append("内容有变化")
        }
        notify(context, NotificationChannels.CHANNEL_SYNC, NOTIF_ID_SYNC) {
            setSmallIcon(R.drawable.ic_stat_class)
            setContentTitle(title)
            setContentText(text.trimEnd(' ', '·'))
            setAutoCancel(true)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private inline fun notify(
        context: Context,
        channel: String,
        id: Int,
        build: NotificationCompat.Builder.() -> Unit
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(context, channel)
            .setContentIntent(launchIntent(context))
            .apply(build)
        nm.notify(id, builder.build())
    }

    private fun launchIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private const val NOTIF_ID_REMINDER_BASE = 1000
    private const val NOTIF_ID_NOW = 2000
    private const val NOTIF_ID_SYNC = 3000
    private const val NOTIF_ID_TEST = 4000
}
