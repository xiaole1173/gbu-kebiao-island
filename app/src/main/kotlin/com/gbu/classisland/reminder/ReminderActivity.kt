// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.reminder

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.gbu.classisland.data.Course
import com.gbu.classisland.notification.ClassNotifier
import com.gbu.classisland.ui.theme.ClassIslandTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 闹钟式全屏上课提醒：
 * - 锁屏/黑屏时由通知 fullScreenIntent 拉起（亮屏 + 锁屏可见）
 * - 播放闹钟铃声，保持屏幕常亮
 * - 必须点击「关闭提醒」才停止（声音 + 通知 + 退出）
 */
class ReminderActivity : ComponentActivity() {

    private val json = Json { ignoreUnknownKeys = true }
    private var course: Course? = null
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 闹钟式：亮屏 + 锁屏也显示 + 保持唤醒（兼容窗口 flags + API 方法双保险）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        // 尝试解锁 keyguard，确保全屏提醒显示在锁屏之上
        runCatching {
            val km = getSystemService(android.app.KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        }

        course = intent.getStringExtra(EXTRA_COURSE)
            ?.let { runCatching { json.decodeFromString<Course>(it) }.getOrNull() }
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: ""
        val startText = intent.getStringExtra(EXTRA_START) ?: ""

        // 闹钟铃声（USAGE_ALARM，可靠且不受通知级别压制）
        startAlarmSound()

        // 闹钟触发后自动重排下一个上课提醒
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ReminderScheduler.reschedule(this@ReminderActivity) }
        }

        setContent {
            ClassIslandTheme(dynamicColor = false) {
                ReminderAlarmScreen(
                    course = course,
                    dateText = dateText,
                    startText = startText,
                    onDismiss = { dismiss() }
                )
            }
        }
    }

    private fun startAlarmSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone?.play()
    }

    /** 用户点击关闭：停止铃声 + 取消提醒通知 + 退出。 */
    private fun dismiss() {
        ringtone?.stop()
        course?.let { NotificationManagerCompat.from(this).cancel(ClassNotifier.reminderNotificationId(it)) }
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_COURSE = "course"
        const val EXTRA_DATE = "date"
        const val EXTRA_START = "start"
    }
}

@Composable
private fun ReminderAlarmScreen(
    course: Course?,
    dateText: String,
    startText: String,
    onDismiss: () -> Unit
) {
    // 弹出动画：缩放 + 淡入（闹钟式，从挖孔下方弹出感）
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(420)) + scaleIn(initialScale = 0.86f, animationSpec = tween(420, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "上课提醒",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            Spacer(Modifier.height(28.dp))
            Text(
                text = course?.name ?: "课程",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = listOf(dateText, startText).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.titleMedium
            )
            if (!course?.location.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    course?.location ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (!course?.teacher.isNullOrBlank()) {
                Text(
                    course?.teacher ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Text("关闭提醒", fontSize = 18.sp)
            }
            }
        }
    }
}
