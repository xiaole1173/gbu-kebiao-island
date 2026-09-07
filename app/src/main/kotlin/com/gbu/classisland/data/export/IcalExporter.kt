// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data.export

import com.gbu.classisland.data.Course
import com.gbu.classisland.model.SectionTime
import com.gbu.classisland.util.TimetableEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * iCal（.ics）导出：每门课在有效周内生成逐个 VEVENT，
 * 可导入系统日历 / Google 日历，作为提醒之外的双重保障。
 */
object IcalExporter {

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun buildIcs(
        courses: List<Course>,
        termStart: LocalDate,
        appName: String = "课表小岛"
    ): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//ClassIsland//$appName//CN")
        sb.appendLine("CALSCALE:GREGORIAN")

        val now = LocalDateTime.now().format(fmt)

        for (c in courses) {
            val sections = com.gbu.classisland.model.DefaultSections.forDay(c.dayOfWeek)
            val startTime = sections.find { it.section == c.startSection } ?: continue
            val endTime = sections.find { it.section == c.endSection } ?: continue
            val weeks = TimetableEngine.parseWeeks(c.weeks)

            for (w in weeks) {
                val date = termStart.plusWeeks(w - 1L).plusDays((c.dayOfWeek - 1).toLong())
                val dtStart = LocalDateTime.of(date, java.time.LocalTime.parse(startTime.start))
                val dtEnd = LocalDateTime.of(date, java.time.LocalTime.parse(endTime.end))

                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:${UUID.randomUUID()}@classisland")
                sb.appendLine("DTSTAMP:$now")
                sb.appendLine("DTSTART:${dtStart.format(fmt)}")
                sb.appendLine("DTEND:${dtEnd.format(fmt)}")
                sb.appendLine("SUMMARY:${c.name}")
                if (c.location.isNotBlank()) sb.appendLine("LOCATION:${c.location}")
                val desc = listOf(c.teacher, "第$w 周", "第${c.startSection}-${c.endSection}节")
                    .filter { it.isNotBlank() }.joinToString("\n")
                sb.appendLine("DESCRIPTION:$desc")
                sb.appendLine("END:VEVENT")
            }
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }
}
