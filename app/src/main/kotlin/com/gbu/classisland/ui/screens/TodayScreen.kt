// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gbu.classisland.ui.common.swipeForPage
import com.gbu.classisland.util.UpcomingClass
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/** 今日视图：日期切换（带动画）+ 下一节课卡片 + 当天课程时间线。 */
@Composable
fun TodayScreen(viewModel: TodayViewModel = viewModel()) {
    val date by viewModel.selectedDate.collectAsState()
    val week by viewModel.currentWeek.collectAsState()
    val today = java.time.LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            // 左右滑动切换前后天
            .swipeForPage(
                onPrev = { viewModel.previousDay() },
                onNext = { viewModel.nextDay() }
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 日期导航
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.previousDay() }) { Text("<", fontSize = 20.sp) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · " +
                        DAY_NAME_CN[date.dayOfWeek.value - 1] + if (date == today) " · 今天" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (week > 0) "第 $week 周" else "未开学",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            TextButton(onClick = { viewModel.nextDay() }) { Text(">", fontSize = 20.sp) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { viewModel.backToToday() }) {
                Text("回到今天", style = MaterialTheme.typography.bodySmall)
            }
        }

        // 日期内容：date 变化时按导航方向整体重建并滑入（文字与卡片一体联动）
        val direction by viewModel.navDirection.collectAsState()
        key(date) {
            TodayDayContent(viewModel = viewModel, week = week, fromLeft = direction < 0)
        }
    }
}

/** 某一天的课程内容（下一节卡片 + 课程列表），随日期重建并整体滑入。 */
@Composable
private fun TodayDayContent(viewModel: TodayViewModel, week: Int, fromLeft: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val sessions by viewModel.daySessions.collectAsState()
    val next by viewModel.nextUpcoming.collectAsState()

    AnimatedVisibility(
        visible = visible,
        enter = (
            if (fromLeft) slideInHorizontally(tween(330)) { -it }
            else slideInHorizontally(tween(330)) { it }
            ) + fadeIn(tween(330))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (next != null) {
                NextClassCard(next!!)
            }
            if (sessions.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (week <= 0) "还没开学，可切换到开学后的日期查看课表"
                        else "这一天没有课",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Text("当日课程", style = MaterialTheme.typography.titleMedium)
                sessions.forEachIndexed { index, session ->
                    // 列表项错峰淡入（轻微上移）
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(320, delayMillis = index * 40)) +
                            slideInVertically(tween(320, delayMillis = index * 40)) { it / 3 }
                    ) {
                        SessionRow(session, highlight = session == next)
                    }
                }
            }
        }
    }
}

private val DAY_NAME_CN = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
private fun NextClassCard(upcoming: UpcomingClass) {
    val c = upcoming.course
    val ongoing = upcoming.isOngoing
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ongoing) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (ongoing) "正在上课" else "下一节课",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${upcoming.start.format(timeFmt)} - ${upcoming.end.format(timeFmt)}" +
                    (if (c.location.isNotBlank()) " · ${c.location}" else ""),
                style = MaterialTheme.typography.bodyMedium
            )
            if (c.teacher.isNotBlank()) {
                Text(c.teacher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SessionRow(session: UpcomingClass, highlight: Boolean) {
    val c = session.course
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${session.start.format(timeFmt)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = session.end.format(timeFmt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = (if (c.location.isNotBlank()) c.location + " · " else "") + c.teacher,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 12.sp
            )
        }
    }
}
