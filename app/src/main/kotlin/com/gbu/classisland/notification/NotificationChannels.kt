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

    // 上课提醒：高优先级 + 普通通知铃声 + 震动（提前 x 分钟 = 通知 + 声音 + 震动，非闹钟声）
    const val CHANNEL_REMINDER = "course_reminder_alarm"
    /** 灵动岛 / 上课进度（静默：声音震动归提醒频道） */
    const val CHANNEL_LIVE = "live_updates"
    const val CHANNEL_SYNC = "course_sync"

    /** 旧的提醒频道 id（无闹钟铃声的版本），ensure 时删除避免残留。 */
    private const val CHANNEL_REMINDER_LEGACY = "course_reminder"
    /** 旧全屏提醒频道，已废除。 */
    private const val CHANNEL_REMINDER_FSI_LEGACY = "course_reminder_fsi"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        // 清理废弃频道（旧无铃声提醒 + 旧全屏频道）
        nm.deleteNotificationChannel(CHANNEL_REMINDER_LEGACY)
        nm.deleteNotificationChannel(CHANNEL_REMINDER_FSI_LEGACY)

        // 上课提醒：高优先级 + 普通通知铃声 + 震动（提前 x 分钟 → 通知 + 声音 + 震动）
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "上课提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课程开始前提前提醒（通知 + 声音 + 震动）"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
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
                // 灵动岛：DEFAULT（静默）——进度/倒计时由状态栏胶囊呈现，不打扰
                NotificationChannel(
                    CHANNEL_LIVE,
                    "灵动岛 · 上课进度",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "上课前后的灵动岛倒计时与进度（静默）" },
                NotificationChannel(
                    CHANNEL_SYNC,
                    "课表同步",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "课表同步结果与变更提醒" }
            )
        )
    }
}
