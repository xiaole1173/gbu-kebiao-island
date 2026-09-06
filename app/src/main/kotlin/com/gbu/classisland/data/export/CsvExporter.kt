// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data.export

import com.gbu.classisland.data.Course

/**
 * CSV 导出：Excel 可直接打开，作为课表数据备份/分享格式。
 * 列：课程名,教师,教室,星期(1-7),开始节次,结束节次,周次,学期,来源
 */
object CsvExporter {

    fun buildCsv(courses: List<Course>): String {
        val sb = StringBuilder()
        sb.appendLine("\uFEFF课程名,教师,教室,星期,开始节次,结束节次,周次,学期,来源")
        for (c in courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))) {
            sb.append(quote(c.name)).append(',')
                .append(quote(c.teacher)).append(',')
                .append(quote(c.location)).append(',')
                .append(c.dayOfWeek).append(',')
                .append(c.startSection).append(',')
                .append(c.endSection).append(',')
                .append(quote(c.weeks)).append(',')
                .append(quote(c.semesterId)).append(',')
                .append(c.source)
                .appendLine()
        }
        return sb.toString()
    }

    private fun quote(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
}
