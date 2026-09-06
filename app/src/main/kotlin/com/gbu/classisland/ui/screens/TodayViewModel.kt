// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.data.Course
import com.gbu.classisland.data.settings.AppSettings
import com.gbu.classisland.data.settings.SettingsRepository
import com.gbu.classisland.util.TimetableEngine
import com.gbu.classisland.util.UpcomingClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 今日视图：可切换查看任意一天的课表（默认今天）。
 * 开学前也能选 9/7 等未来日期预览开学第一天的课程。
 */
class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)

    private val settings: StateFlow<AppSettings?> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val courses: StateFlow<List<Course>> =
        combine(db.courseDao().observeAll(), settings) { all, s ->
            val sem = s?.currentSemesterId
            if (sem.isNullOrBlank()) all else all.filter { it.semesterId == sem }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 当前查看的日期（默认今天）。 */
    val selectedDate = MutableStateFlow(LocalDate.now())

    /** 日期切换方向：1=去未来（内容从右滑入），-1=回过去（内容从左滑入）。 */
    val navDirection = MutableStateFlow(1)

    /** 所选日期所在教学周（开学前=0）。 */
    val currentWeek: StateFlow<Int> = combine(settings, selectedDate) { s, d ->
        runCatching { LocalDate.parse(s?.termStartDate ?: "") }.getOrNull()
            ?.let { TimetableEngine.currentWeek(it, d) } ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    /** 所选日期的课程（按开始时间排序），空=那天没课。 */
    val daySessions: StateFlow<List<UpcomingClass>> =
        combine(courses, settings, selectedDate) { cs, s, d ->
            val sections = s?.sections ?: return@combine emptyList()
            val week = runCatching { LocalDate.parse(s.termStartDate ?: "") }.getOrNull()
                ?.let { TimetableEngine.currentWeek(it, d) } ?: return@combine emptyList()
            if (week <= 0) return@combine emptyList()
            TimetableEngine.coursesOn(cs, d, week).mapNotNull { c ->
                TimetableEngine.sessionTimes(c, d, sections)
                    ?.let { (start, end) -> UpcomingClass(c, d, start, end) }
            }.sortedBy { it.start }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * 所选日期的"当前/下一节"：
     * - 看今天：正在进行 或 今天接下来的第一节
     * - 看其他日期：该天第一节（预览用）
     */
    val nextUpcoming: StateFlow<UpcomingClass?> =
        combine(courses, settings, selectedDate) { cs, s, d ->
            val sections = s?.sections ?: return@combine null
            val termStart = runCatching { LocalDate.parse(s.termStartDate ?: "") }.getOrNull()
                ?: return@combine null
            val week = TimetableEngine.currentWeek(termStart, d)
            if (week <= 0) return@combine null
            val list = TimetableEngine.coursesOn(cs, d, week).mapNotNull { c ->
                TimetableEngine.sessionTimes(c, d, sections)
                    ?.let { (start, end) -> UpcomingClass(c, d, start, end) }
            }.sortedBy { it.start }
            if (d == LocalDate.now()) {
                val now = LocalDateTime.now()
                list.firstOrNull { it.start <= now && now < it.end }
                    ?: list.firstOrNull { it.start > now }
            } else {
                list.firstOrNull()
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun previousDay() {
        navDirection.value = -1
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        navDirection.value = 1
        selectedDate.value = selectedDate.value.plusDays(1)
    }

    fun backToToday() {
        val now = LocalDate.now()
        navDirection.value = if (now >= selectedDate.value) 1 else -1
        selectedDate.value = now
    }
}
