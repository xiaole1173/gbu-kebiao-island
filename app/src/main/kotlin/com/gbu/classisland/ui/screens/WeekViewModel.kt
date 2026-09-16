// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.data.Course
import com.gbu.classisland.data.settings.SettingsRepository
import com.gbu.classisland.model.SectionTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeekViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)

    // 进程内缓存的设置作为首帧的初始值：避免"先按默认值渲染（第 1 周）→ 读到设置后跳变"
    private val cachedSettings = SettingsRepository.cached

    private val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), cachedSettings)

    /** 首帧即可用的开学日期（来自设置缓存）。 */
    private val initialTermStart: LocalDate? =
        runCatching { LocalDate.parse(cachedSettings?.termStartDate ?: "") }.getOrNull()

    /** 首帧即可用的当前教学周；拿不到缓存时回落到 1。 */
    private val initialWeek: Int = initialTermStart
        ?.let { com.gbu.classisland.util.TimetableEngine.currentWeek(it, LocalDate.now()) }
        ?.coerceAtLeast(1)
        ?: 1

    /** DB 中存在的所有学期（去重，新学期在前），用于学期切换器。 */
    val availableSemesters: StateFlow<List<String>> = db.courseDao().observeAll()
        .map { list -> list.map { it.semesterId }.filter { it.isNotBlank() }.distinct().sortedDescending() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 用户选中的学期；null = 跟随当前学期（默认）。 */
    private val selectedSemester = MutableStateFlow<String?>(null)

    /** 当前实际展示的学期（默认当前学期，切换后固定为用户所选）。 */
    val effectiveSemester: StateFlow<String> =
        combine(selectedSemester, settings) { sel, s -> sel ?: s?.currentSemesterId ?: "" }
            .stateIn(viewModelScope, SharingStarted.Lazily, cachedSettings?.currentSemesterId.orEmpty())

    /** 是否正在查看当前学期（决定是否显示列头日期/今天高亮/"未开学"提示）。 */
    val isCurrentSemester: StateFlow<Boolean> =
        combine(effectiveSemester, settings) { eff, s -> eff == s?.currentSemesterId }
            .stateIn(viewModelScope, SharingStarted.Lazily, true)

    /** 所选学期的课程。 */
    val courses: StateFlow<List<Course>> = combine(db.courseDao().observeAll(), effectiveSemester) { all, sem ->
        if (sem.isBlank()) all else all.filter { it.semesterId == sem }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 课程库（课程代码 → 条目），用于课程详情页的学分/性质展示。 */
    val libraryByCode: StateFlow<Map<String, com.gbu.classisland.data.LibraryCourse>> =
        db.libraryCourseDao().observeAll()
            .map { list -> list.associateBy { it.kcdm } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** 节次时间表。 */
    val sections: StateFlow<List<SectionTime>> = settings
        .map { it?.sections ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, cachedSettings?.sections ?: emptyList())

    /** 开学日期（第 1 周周一），用于计算列头日期。 */
    val termStartDate: StateFlow<LocalDate?> = settings
        .map { runCatching { LocalDate.parse(it?.termStartDate ?: "") }.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.Lazily, initialTermStart)

    /** 今天日期（用于周视图高亮与当前周计算）。 */
    val today: StateFlow<LocalDate> =
        kotlinx.coroutines.flow.MutableStateFlow(LocalDate.now())

    /** 当前教学周（开学前=0）。 */
    val currentWeek: StateFlow<Int> = combine(termStartDate, today) { ts, t ->
        if (ts == null) 1 else com.gbu.classisland.util.TimetableEngine.currentWeek(ts, t)
    }.stateIn(viewModelScope, SharingStarted.Lazily, initialWeek)

    /**
     * 用户手动固定的周次；null = 自动跟随当前教学周（默认）。
     * 自动跟随后，打开 App / 跨周后都会直接定位到当前周（如第 2 周）。
     */
    private val pinnedWeek = MutableStateFlow<Int?>(null)

    /** 实际展示的周：未手动翻周时自动跟随当前教学周；未开学（currentWeek=0）回落到第 1 周。 */
    val selectedWeek: StateFlow<Int> =
        combine(pinnedWeek, currentWeek) { pinned, cur -> pinned ?: cur.coerceAtLeast(1) }
            .stateIn(viewModelScope, SharingStarted.Lazily, initialWeek)

    fun selectWeek(week: Int) {
        pinnedWeek.value = week
    }

    /** 切换查看的学期：历史学期固定第 1 周，切回当前学期则重新自动跟随当前周。 */
    fun selectSemester(semesterId: String) {
        selectedSemester.value = semesterId
        val isCurrent = semesterId == settings.value?.currentSemesterId
        pinnedWeek.value = if (isCurrent) null else 1
    }

    /** 手动添加/更新一门课，随后重排提醒并刷新小组件。 */
    fun upsertCourse(course: com.gbu.classisland.data.Course) {
        viewModelScope.launch {
            db.courseDao().upsert(course)
            com.gbu.classisland.reminder.ReminderScheduler.reschedule(getApplication())
            com.gbu.classisland.widget.TodayWidgetProvider.refreshAll(getApplication())
        }
    }

    /** 删除一门课（仅手动来源可删），随后重排提醒并刷新小组件。 */
    fun deleteCourse(course: com.gbu.classisland.data.Course) {
        if (course.source != "manual") return // 教务同步的课不直接删，重新同步即恢复
        viewModelScope.launch {
            db.courseDao().deleteById(course.id)
            com.gbu.classisland.reminder.ReminderScheduler.reschedule(getApplication())
            com.gbu.classisland.widget.TodayWidgetProvider.refreshAll(getApplication())
        }
    }
}
