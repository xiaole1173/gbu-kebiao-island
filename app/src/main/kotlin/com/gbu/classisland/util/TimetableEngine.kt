// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.util

import com.gbu.classisland.data.Course
import com.gbu.classisland.model.SectionTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 一节即将/正在进行课程的时间信息。 */
data class UpcomingClass(
    val course: Course,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime
) {
    val isOngoing: Boolean get() = LocalDateTime.now() in start..end

    val isToday: Boolean get() = date == LocalDate.now()
}

/** 课表领域引擎：周次计算、当日课程、当前/下一节课。 */
object TimetableEngine {

    fun parseWeeks(raw: String): Set<Int> =
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** 计算当前教学周（开学首日 = 第 1 周周一）。开学前返回 0。 */
    fun currentWeek(termStart: LocalDate, today: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(termStart, today)
        if (days < 0) return 0
        return (days / 7).toInt() + 1
    }

    /** 某日（某教学周）有哪些课。weeks 为空表示每周都有。 */
    fun coursesOn(courses: List<Course>, date: LocalDate, week: Int): List<Course> {
        val day = date.dayOfWeek.value // 1=周一 .. 7=周日
        return courses.filter { c ->
            c.dayOfWeek == day && (c.weeks.isBlank() || week in parseWeeks(c.weeks))
        }
    }

    /** 课程在某天的起止时间（按当天作息表：周一三五 75 分钟 / 周二四 115 分钟）。找不到返回 null。 */
    fun sessionTimes(course: Course, date: LocalDate): Pair<LocalDateTime, LocalDateTime>? {
        val sections = com.gbu.classisland.model.DefaultSections.forDay(date.dayOfWeek.value)
        val start = sections.find { it.section == course.startSection } ?: return null
        val end = sections.find { it.section == course.endSection } ?: return null
        return LocalDateTime.of(date, LocalTime.parse(start.start)) to
            LocalDateTime.of(date, LocalTime.parse(end.end))
    }

    /**
     * 计算"当前正在进行或即将开始的课"。
     * 优先：今天正在上的课 → 今天下一节 → 明天第一节（跨日准备）。
     */
    fun upcoming(
        courses: List<Course>,
        now: LocalDateTime,
        termStart: LocalDate
    ): UpcomingClass? {
        val week = currentWeek(termStart, now.toLocalDate())
        if (week <= 0) return null

        fun forDate(date: LocalDate): List<UpcomingClass> =
            coursesOn(courses, date, week).mapNotNull { c ->
                sessionTimes(c, date)?.let { (s, e) -> UpcomingClass(c, date, s, e) }
            }.sortedBy { it.start }

        val todayList = forDate(now.toLocalDate())
        // 正在进行的课
        todayList.firstOrNull { it.start <= now && now < it.end }?.let { return it }
        // 今天下一节
        todayList.firstOrNull { it.start > now }?.let { return it }
        // 明天第一节
        forDate(now.toLocalDate().plusDays(1)).firstOrNull()?.let { return it }
        return null
    }
}
