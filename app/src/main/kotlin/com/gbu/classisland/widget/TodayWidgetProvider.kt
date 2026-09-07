// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gbu.classisland.MainActivity
import com.gbu.classisland.R
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.data.settings.SettingsRepository
import com.gbu.classisland.util.TimetableEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 桌面小组件：显示今天日期 / 教学周 / 当前·下一节课。
 * 更新时机：系统周期刷新 + 同步完成/提醒触发后手动刷新（见 [refreshAll]）。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshAll(context, appWidgetManager, appWidgetIds)
            } finally {
                result.finish()
            }
        }
    }

    companion object {

        private val dateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        /** 供同步完成 / 提醒触发后调用：刷新所有已放置的小组件。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TodayWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                refreshAll(context, manager, ids)
            }
        }

        private suspend fun refreshAll(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            if (ids.isEmpty()) return
            val settings = SettingsRepository(context).settings.first()
            val courses = if (settings.currentSemesterId.isBlank()) emptyList()
            else AppDatabase.get(context).courseDao().getBySemester(settings.currentSemesterId)

            val today = LocalDate.now()
            val holidayName = com.gbu.classisland.data.calendar.AcademicCalendar.holidayName(today)
            val isHoliday = com.gbu.classisland.data.calendar.AcademicCalendar.isHoliday(today)
            val week = runCatching { LocalDate.parse(settings.termStartDate) }.getOrNull()
                ?.let { TimetableEngine.currentWeek(it, today) } ?: 0
            val next = if (week > 0 && !isHoliday)
                TimetableEngine.upcoming(courses, LocalDateTime.now(), today)
            else null

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_today)
                views.setTextViewText(R.id.widget_date, today.format(dateFmt))
                views.setTextViewText(
                    R.id.widget_week,
                    if (week > 0) "第 $week 周" else "未开学"
                )
                if (isHoliday) {
                    views.setTextViewText(
                        R.id.widget_next_title,
                        if (holidayName != null) "$holidayName 放假" else "今日放假"
                    )
                    views.setTextViewText(R.id.widget_next_detail, "校历节假日/停课，无课程")
                } else if (next == null) {
                    views.setTextViewText(R.id.widget_next_title, "暂无课程")
                    views.setTextViewText(
                        R.id.widget_next_detail,
                        if (courses.isEmpty()) "到「课表小岛」设置中同步课表" else "今天没有课"
                    )
                } else {
                    val c = next.course
                    views.setTextViewText(
                        R.id.widget_next_title,
                        (if (next.isOngoing) "正在上：" else "下一节：") + c.name
                    )
                    views.setTextViewText(
                        R.id.widget_next_detail,
                        "${next.start.format(timeFmt)} - ${next.end.format(timeFmt)}" +
                            (if (c.location.isNotBlank()) " · ${c.location}" else "")
                    )
                }
                views.setOnClickPendingIntent(
                    R.id.widget_date,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                manager.updateAppWidget(id, views)
            }
        }
    }
}
