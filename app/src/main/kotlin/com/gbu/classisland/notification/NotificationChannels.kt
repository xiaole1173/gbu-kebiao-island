// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/** 通知渠道。 */
object NotificationChannels {

    // v2 频道：带闹钟铃声 + 震动（旧 course_reminder 频道创建时无声音，系统不允许更新，故换 id 重建）
    const val CHANNEL_REMINDER = "course_reminder_alarm"
    /** 全屏触发专用频道：高优先级但无声（铃声由全屏 Activity 播放，避免双响；且不受通知级别压制） */
    const val CHANNEL_REMINDER_FSI = "course_reminder_fsi"
    const val CHANNEL_NOW = "course_now"
    const val CHANNEL_SYNC = "course_sync"

    /** 旧的提醒频道 id（无闹钟铃声的版本），ensure 时删除避免残留。 */
    private const val CHANNEL_REMINDER_LEGACY = "course_reminder"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        // 清理旧的无闹钟铃声频道
        nm.deleteNotificationChannel(CHANNEL_REMINDER_LEGACY)

        // 上课提醒：高优先级 + 闹钟铃声 + 震动（闹钟式提醒，全屏触发的必要条件）
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课程开始前的闹钟式提醒"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setShowBadge(true)
        }

        nm.createNotificationChannels(
            listOf(
                reminder,
                // 全屏触发专用：HIGH 重要性（满足 fullScreenIntent 触发条件）+ 无声
                NotificationChannel(
                    CHANNEL_REMINDER_FSI,
                    "上课提醒（全屏）",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "全屏提醒触发通道（无声，铃声由全屏界面播放）"
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_NOW,
                    "当前/下一节课",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "持续显示当前或即将开始的课（可被超级岛捕获）" },
                NotificationChannel(
                    CHANNEL_SYNC,
                    "课表同步",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "课表同步结果与变更提醒" }
            )
        )
    }
}
