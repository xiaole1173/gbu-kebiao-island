// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.edu

import com.gbu.classisland.data.Course
import com.gbu.classisland.edu.EduApi.TimetableBlock

/**
 * 把教务系统总课表（xszykb/queryxszykbzong）解析成本地 Course 列表。
 *
 * 字段语义（已实测）：
 *  - KEY:  "xq5_jc9" → xq 后的数字=星期（1..7），jc 后=展示块索引（非节次）；特殊值 "bz"=备注行（跳过）
 *  - KSJC/JSJC: 起止节次（备注行可能为 null）
 *  - ZC:   34 位周次位图，位置 p 的 '1' = 第 p 周有课（位置 0 恒为占位 0）
 *  - SKSJ: 展示文本，形如
 *          课程名【实验】\n[教师]\n[1-16周][B302]\n第11-12节
 */
object TimetableParser {

    private val KEY_RE = Regex("""xq(\d)_jc\d+""")
    private val WEEKS_RE = Regex("""\[[^\]]*?周\]""")

    fun parse(blocks: List<TimetableBlock>, xn: String, xq: String): List<Course> {
        val semesterId = "$xn-$xq"
        return blocks.mapNotNull { block ->
            val day = parseDay(block.KEY) ?: return@mapNotNull null
            val weeks = parseWeeks(block.ZC)
            if (weeks.isEmpty()) return@mapNotNull null
            val meta = parseSksj(block.SKSJ)

            Course(
                name = meta.name,
                teacher = meta.teacher,
                location = meta.location,
                dayOfWeek = day,
                startSection = block.KSJC,
                endSection = block.JSJC,
                startWeek = weeks.min(),
                endWeek = weeks.max(),
                weekParity = computeParity(weeks),
                weeks = weeks.sorted().joinToString(","),
                semesterId = semesterId,
                source = "edu",
                externalId = block.RWH
            )
        }
    }

    /** KEY 里的星期：xq1..xq7 → 1..7。 */
    internal fun parseDay(key: String): Int? =
        KEY_RE.find(key)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..7 }

    /** ZC 位图 → 周号集合。位置 p 的 '1' = 第 p 周有课（位置 0 恒为占位 0）。 */
    internal fun parseWeeks(zc: String): Set<Int> {
        if (zc.length < 2) return emptySet()
        val weeks = mutableSetOf<Int>()
        for (i in 1 until zc.length) {
            if (zc[i] == '1') weeks += i
        }
        return weeks
    }

    /** SKSJ 展示文本 → 名称/教师/教室。 */
    internal fun parseSksj(sksj: String): Meta {
        val lines = sksj.trim().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Meta("", "", "")
        val name = lines[0]
        var teacher = ""
        var location = ""
        for (line in lines.drop(1)) {
            if (line.startsWith("[")) {
                // [教师] 或 [周次][教室]
                if (line.contains("周]")) {
                    location = line.replace(WEEKS_RE, "").trim('[', ']').trim()
                } else {
                    teacher = line.trim('[', ']').trim()
                }
            }
        }
        if (location == "无地点" || location == "No venue arranged") location = ""
        return Meta(name, teacher, location)
    }

    /** 由周集合派生单双周（仅便捷标记；精确周次以 weeks 字段为准）。 */
    internal fun computeParity(weeks: Set<Int>): Int {
        if (weeks.size <= 1) return 0
        val allOdd = weeks.all { it % 2 == 1 }
        val allEven = weeks.all { it % 2 == 0 }
        return when {
            allOdd -> 1
            allEven -> 2
            else -> 0
        }
    }

    data class Meta(val name: String, val teacher: String, val location: String)
}
