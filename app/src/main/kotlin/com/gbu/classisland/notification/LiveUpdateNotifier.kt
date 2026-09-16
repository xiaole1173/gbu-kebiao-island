// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gbu.classisland.MainActivity
import com.gbu.classisland.R
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 灵动岛 / 上课进度常驻通知（Android 16 Live Updates）。
 *
 * 机制：上课前 lead 分钟出现"候课倒计时"、上课中转为"课程进度条"，并提升为状态栏胶囊
 * （setRequestPromotedOngoing，小米等设备显示为顶部灵动胶囊）。倒计时由系统驱动
 * （chronometer countdown），进度条由 tick 闹钟链每 15 秒刷新；通知自带系统超时
 * （timeoutAfter）——到下课时刻由系统直接移除，即使 Doze 限流或未授权精确闹钟
 * 也不会有负数倒计时。
 *
 * 声音/震动不在此频道（IMPORTANCE_DEFAULT 静默），由"提前提醒"频道负责。
 */
object LiveUpdateNotifier {

    const val CHANNEL_ID = "live_updates"
    const val ACTION_TICK = "com.gbu.classisland.action.LIVE_TICK"
    /** 「关闭」灵动岛动作：取消通知并终止整条 tick 链。 */
    const val ACTION_CLOSE = "com.gbu.classisland.action.LIVE_CLOSE"

    const val EXTRA_NAME = "live_name"
    const val EXTRA_ROOM = "live_room"
    const val EXTRA_TEACHER = "live_teacher"
    const val EXTRA_START = "live_start"
    const val EXTRA_END = "live_end"
    const val EXTRA_ANCHOR = "live_anchor"
    const val EXTRA_SCALE = "live_scale"
    const val EXTRA_WAIT_START = "live_wait_start"

    private const val NOTIF_ID = 4713
    /** 运行中进度链的 alarm 请求码（self-sustaining，同一时刻只有一条链）。 */
    private const val TICK_RC = 9981
    private const val ACTION_RC = 4714
    // 品牌配色（课表小岛）：海洋蓝=上课进度 / 青绿=候课倒计时 / 沙金=最后冲刺+里程碑
    private val OCEAN = 0xFF1B5E9E.toInt()
    private val TEAL = 0xFF2E7D6B.toInt()
    private val AMBER = 0xFFB06A00.toInt()
    /** 大图标课程首字圆标配色：品牌色系（含深浅变体），按课程名稳定取色。 */
    private val ICON_COLORS = intArrayOf(
        0xFF1B5E9E.toInt(), 0xFF2E7D6B.toInt(), 0xFFB06A00.toInt(), 0xFF0D3A61.toInt(),
        0xFF82B1FF.toInt(), 0xFF9CD9C9.toInt(), 0xFFFFB85C.toInt(), 0xFF455A64.toInt(),
    )

    /** 进度条最后一段（绿色「快下课」）的时长。 */
    private const val LAST_STAGE_MS = 10 * 60_000L

    /** 课长超过该值才显示绿色冲刺段 + 里程碑，避免短课拥挤。 */
    private const val MILESTONE_MIN_TOTAL_MS = 20 * 60_000L

    /** 候课阶段 tick 被系统延迟时的兜底宽限：开课（虚拟）+2 分钟后由系统移除候课通知。 */
    private const val WAIT_OVERDUE_GRACE_MS = 2 * 60_000L

    /** 每次进度跳步对应的虚拟时长（5 秒），除以倍率得真实 tick 间隔：真实模式每 5 秒刷一次进度，更平滑。 */
    private const val PROGRESS_STEP_VIRTUAL_MS = 5_000L

    private const val NOTIF_CHANNEL_NAME = "灵动岛 · 上课进度"
    private const val NOTIF_WAIT_TITLE = "即将上课：%s"       // %s = 课程名
    private const val NOTIF_IN_CLASS_TITLE = "正在上课：%s"    // %s = 课程名
    private const val NOTIF_ACTION_OPEN = "打开"
    private const val NOTIF_ACTION_CLOSE = "关闭"

    /** 进程内只做一次通道检查；tick 高频调用时必须是 no-op，否则删通道会导致通知销毁重建。 */
    private var channelReady = false

    fun ensureChannel(context: Context) {
        if (channelReady) return
        val nm = NotificationManagerCompat.from(context)
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.importance >= NotificationManagerCompat.IMPORTANCE_DEFAULT) {
            channelReady = true
            return
        }
        if (existing != null) nm.deleteNotificationChannel(CHANNEL_ID)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(NOTIF_CHANNEL_NAME)
                .setDescription("上课前后的灵动岛倒计时与进度（静默，铃声/震动见「上课提醒」）")
                .build()
        )
        channelReady = true
    }

    /** 测试流程的时间倍率。 */
    private const val TEST_SCALE = 12

    /** 当前是否有灵动岛通知正在显示。 */
    fun isActive(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == NOTIF_ID }

    /** 「关闭」灵动岛：取消通知并终止当前运行链。 */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
        cancelTick(context, TICK_RC)
    }

    /**
     * 丢弃尚未触发的进度 tick 闹钟。
     *
     * 用途是清理幽灵闹钟：tick 链只应在「岛正在显示」时存在，一旦岛没了，任何排着队的 tick
     * 都是无主残留（早期版本把岛的唤起做成独立 kickoff 闹钟，升级后会在系统里留下这种残留）。
     * 注意调用方必须先确认 `!isActive(context)`，否则会打断正在跑的进度链。
     */
    fun cancelPendingTick(context: Context) = cancelTick(context, TICK_RC)

    /**
     * 立即开始 / 接管一节课的灵动岛：先结束上一条链，再显示本节课，随后由 tick 链自我维持到下课。
     * 候课阶段（now < start）显示倒计时，上课中显示进度；到下课由 timeoutAfter 自动移除。
     *
     * 岛的开始由「提前提醒触发 / 上一节课结束」两处驱动，不再单独排 kickoff 闹钟——
     * 之前 kickoff 与提醒共用请求码，提醒触发的重排会把还没触发的 kickoff 顶掉，导致岛永不出现。
     */
    fun startIsland(
        context: Context,
        name: String,
        room: String,
        teacher: String,
        start: LocalDateTime,
        end: LocalDateTime,
        now: LocalDateTime,
    ) {
        if (!end.isAfter(now)) return
        cancel(context) // 接管：结束上一条链，避免两条链互相顶掉
        val startMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMs = end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val t = System.currentTimeMillis()
        post(context, name, room, teacher, startMs, endMs, anchorMs = startMs, scale = 1, waitStartMs = t)
        scheduleTick(context, name, room, teacher, startMs, endMs, t + tickIntervalMs(1), anchorMs = startMs, scale = 1, waitStartMs = t)
    }

    private fun cancelTick(context: Context, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.gbu.classisland.reminder.ReminderScheduler.ReminderReceiver::class.java)
            .setAction(ACTION_TICK)
        am.cancel(
            PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    /** 测试按钮：12 倍速完整流程演示 —— 虚拟 15 分钟候课（实际 75 秒）+ 虚拟 45 分钟上课（实际 3.75 分钟）。 */
    fun showTest(context: Context) {
        val now = System.currentTimeMillis()
        val scale = TEST_SCALE
        val startMs = now + 15 * 60_000L
        val endMs = startMs + 45 * 60_000L
        val name = "测试灵动岛"
        val room = "示例教室"
        val teacher = "示例教师"
        cancel(context)
        post(context, name, room, teacher, startMs, endMs, anchorMs = now, scale = scale, waitStartMs = now)
        scheduleTick(context, name, room, teacher, startMs, endMs, now + tickIntervalMs(scale), anchorMs = now, scale = scale, waitStartMs = now)
    }

    /** 每 tick 刷新进度并续排下一次；返回 true 表示本节课已下课（链自然结束）。 */
    fun onTick(
        context: Context,
        name: String,
        room: String,
        teacher: String,
        startMs: Long,
        endMs: Long,
        anchorMs: Long,
        scale: Int,
        waitStartMs: Long,
    ): Boolean {
        val realNow = System.currentTimeMillis()
        val now = anchorMs + (realNow - anchorMs) * scale
        if (now >= endMs) return true // 到下课：通知由 timeoutAfter 自动移除，tick 链自然终止
        // 用户已手动划掉通知 → 不再重发，终止整条 tick 链（否则每个 tick 都会把它重新弹出来）
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.activeNotifications.none { it.id == NOTIF_ID }) return false
        post(context, name, room, teacher, startMs, endMs, anchorMs, scale, waitStartMs)
        val boundary = if (now < startMs) startMs else endMs
        val stepMs = minOf(tickIntervalMs(scale), ((boundary - now) / scale).coerceAtLeast(1L))
        scheduleTick(context, name, room, teacher, startMs, endMs, realNow + stepMs, anchorMs, scale, waitStartMs)
        return false
    }

    private fun tickIntervalMs(scale: Int) = (PROGRESS_STEP_VIRTUAL_MS / scale).coerceAtLeast(1_000L)

    private fun post(
        context: Context,
        name: String,
        room: String,
        teacher: String,
        startMs: Long,
        endMs: Long,
        anchorMs: Long,
        scale: Int,
        waitStartMs: Long,
    ) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val now = anchorMs + (System.currentTimeMillis() - anchorMs) * scale
        val total = (endMs - startMs).coerceAtLeast(1L)
        val inClass = now >= startMs
        val elapsed = if (inClass) (now - startMs).coerceIn(0L, total) else 0L
        val pct = (elapsed * 100 / total).toInt()

        val dueAtMs = if (inClass) endMs else startMs + WAIT_OVERDUE_GRACE_MS
        val timeoutMs = ((dueAtMs - now) / scale).coerceAtLeast(1L)

        val style = NotificationCompat.ProgressStyle()
        val segs = mutableListOf<NotificationCompat.ProgressStyle.Segment>()
        when {
            !inClass -> {
                if (waitStartMs > 0L && waitStartMs < startMs) {
                    val waitTotal = startMs - waitStartMs
                    val waitElapsed = (now - waitStartMs).coerceIn(0L, waitTotal)
                    style.setProgress(waitElapsed.toInt())
                    segs += NotificationCompat.ProgressStyle.Segment(waitElapsed.toInt()).setColor(TEAL)
                    segs += NotificationCompat.ProgressStyle.Segment((waitTotal - waitElapsed).toInt().coerceAtLeast(1))
                } else {
                    style.setProgress(0)
                    segs += NotificationCompat.ProgressStyle.Segment(total.toInt())
                }
            }
            total > MILESTONE_MIN_TOTAL_MS -> {
                style.setProgress(elapsed.toInt())
                val milestone = total - LAST_STAGE_MS
                if (elapsed < milestone) {
                    if (elapsed > 0L) segs += NotificationCompat.ProgressStyle.Segment(elapsed.toInt()).setColor(OCEAN)
                    segs += NotificationCompat.ProgressStyle.Segment((milestone - elapsed).toInt())
                    segs += NotificationCompat.ProgressStyle.Segment(LAST_STAGE_MS.toInt()).setColor(AMBER)
                } else {
                    segs += NotificationCompat.ProgressStyle.Segment(elapsed.toInt()).setColor(OCEAN)
                    segs += NotificationCompat.ProgressStyle.Segment((total - elapsed).toInt().coerceAtLeast(1)).setColor(AMBER)
                }
            }
            else -> {
                style.setProgress(elapsed.toInt())
                if (elapsed > 0L) segs += NotificationCompat.ProgressStyle.Segment(elapsed.toInt()).setColor(OCEAN)
                segs += NotificationCompat.ProgressStyle.Segment((total - elapsed).toInt().coerceAtLeast(1))
            }
        }
        style.setProgressSegments(segs)

        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val endText = Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)
        val startText = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)
        val remainMin = ((endMs - now) / 60_000L).coerceAtLeast(0L)
        val untilStartMin = ((startMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L
        // 正文：老师 · 教室 · 时间（展开详情卡完整信息）
        val info = listOf(teacher, room).filter { it.isNotBlank() }.joinToString(" · ")
        val text = if (inClass) {
            listOfNotNull(info.takeIf { it.isNotBlank() }, "$endText 下课 · $pct%").joinToString(" · ")
        } else {
            listOfNotNull(info.takeIf { it.isNotBlank() }, "$startText 开始 · 还有 $untilStartMin 分钟").joinToString(" · ")
        }
        // 收起态文本：时间（胶囊/摄像头右侧）
        val shortText = if (inClass) "正在上课 · $endText 下课" else "即将上课 $startText"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo) // App 图标（贴边版，避免留白导致显示过小）
            .setLargeIcon(largeIcon(name))
            .setColor(OCEAN)
            .setContentTitle(
                if (inClass) String.format(NOTIF_IN_CLASS_TITLE, name)
                else String.format(NOTIF_WAIT_TITLE, name)
            )
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setRequestPromotedOngoing(true)
            .setTimeoutAfter(timeoutMs)
            // 收起态短文本（胶囊显示"即将上课 10:30"）
            .setShortCriticalText(shortText)
        if (scale == 1) {
            // 真实时间：系统驱动的平滑倒计时（状态栏 chip 每秒自动跳动，不依赖 re-post）
            builder.setWhen(if (inClass) endMs else startMs)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_stat_class, NOTIF_ACTION_OPEN, mainPendingIntent(context)
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_stat_class, NOTIF_ACTION_CLOSE, closePendingIntent(context)
            )
        )
        NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
    }

    private fun scheduleTick(
        context: Context,
        name: String,
        room: String,
        teacher: String,
        startMs: Long,
        endMs: Long,
        atMillis: Long,
        anchorMs: Long,
        scale: Int,
        waitStartMs: Long,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = tickPending(context, name, room, teacher, startMs, endMs, anchorMs, scale, waitStartMs)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun tickPending(
        context: Context,
        name: String?,
        room: String?,
        teacher: String?,
        startMs: Long,
        endMs: Long,
        anchorMs: Long,
        scale: Int,
        waitStartMs: Long,
    ): PendingIntent {
        val intent = Intent(context, com.gbu.classisland.reminder.ReminderScheduler.ReminderReceiver::class.java).apply {
            action = ACTION_TICK
            name?.let { putExtra(EXTRA_NAME, it) }
            room?.let { putExtra(EXTRA_ROOM, it) }
            teacher?.let { putExtra(EXTRA_TEACHER, it) }
            putExtra(EXTRA_START, startMs)
            putExtra(EXTRA_END, endMs)
            putExtra(EXTRA_ANCHOR, anchorMs)
            putExtra(EXTRA_SCALE, scale)
            putExtra(EXTRA_WAIT_START, waitStartMs)
        }
        // 单请求码：同一时刻只有一条进度链，每个 tick 覆写下一次；cancel 也用它匹配取消。
        return PendingIntent.getBroadcast(
            context, TICK_RC, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun mainPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, ACTION_RC,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 「关闭」：取消灵动岛通知并终止 tick 链。 */
    private fun closePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.gbu.classisland.reminder.ReminderScheduler.ReminderReceiver::class.java)
            .setAction(ACTION_CLOSE)
        return PendingIntent.getBroadcast(
            context, ACTION_RC + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 课程首字彩色圆标，颜色按课程名稳定取色。 */
    private fun largeIcon(name: String): Bitmap {
        val px = 192
        val bmp = androidx.core.graphics.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ICON_COLORS[(name.hashCode().let { if (it < 0) -it else it }) % ICON_COLORS.size]
        }
        canvas.drawCircle(px / 2f, px / 2f, px / 2f, fill)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = px * 0.44f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val fm = tp.fontMetrics
        canvas.drawText(name.firstOrNull()?.toString() ?: "课", px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, tp)
        return bmp
    }
}
