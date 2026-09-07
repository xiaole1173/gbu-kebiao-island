// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 节次时间配置。教务系统未直接提供结构化的节次时间接口（帆软报表渲染），
 * 因此内置默认作息表 + 用户在设置中可编辑（JSON 持久化到 DataStore）。
 */
@Serializable
data class SectionTime(
    val section: Int,   // 第几节（1 起）
    val start: String,  // "HH:mm"
    val end: String     // "HH:mm"
) {
    /** 开始时间的分钟数（0 点起） */
    fun startMinutes(): Int = parseMinutes(start)

    /** 结束时间的分钟数 */
    fun endMinutes(): Int = parseMinutes(end)

    companion object {
        fun parseMinutes(hhmm: String): Int {
            val parts = hhmm.split(":")
            if (parts.size != 2) return 0
            return parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
        }
    }
}

/**
 * 默认作息表（大湾区大学 2026 级本科生）。
 * 每节课 35 分钟；大节内课间 5 分钟，大节间休息 15 分钟。
 * 周一三五：9 大节 × 2 节 = 75 分钟/大节（08:00-09:15 起）。
 * 周二四：6 大节 × 3 节 = 115 分钟/大节（08:00-09:55 起）。
 */
object DefaultSections {
    /** 周一三五：每节 35 分钟，大节 = 2 节（第1-2节、第3-4节…）。 */
    val list: List<SectionTime> = listOf(
        SectionTime(1, "08:00", "08:35"), SectionTime(2, "08:40", "09:15"),
        SectionTime(3, "09:30", "10:05"), SectionTime(4, "10:10", "10:45"),
        SectionTime(5, "11:00", "11:35"), SectionTime(6, "11:40", "12:15"),
        SectionTime(7, "12:30", "13:05"), SectionTime(8, "13:10", "13:45"),
        SectionTime(9, "14:00", "14:35"), SectionTime(10, "14:40", "15:15"),
        SectionTime(11, "15:30", "16:05"), SectionTime(12, "16:10", "16:45"),
        SectionTime(13, "17:00", "17:35"), SectionTime(14, "17:40", "18:15"),
        SectionTime(15, "18:30", "19:05"), SectionTime(16, "19:10", "19:45"),
        SectionTime(17, "20:00", "20:35"), SectionTime(18, "20:40", "21:15")
    )

    /** 周二四：每节 35 分钟，大节 = 3 节（第1-3节、第4-6节…）。 */
    val tueThu: List<SectionTime> = listOf(
        SectionTime(1, "08:00", "08:35"), SectionTime(2, "08:40", "09:15"), SectionTime(3, "09:20", "09:55"),
        SectionTime(4, "10:10", "10:45"), SectionTime(5, "10:50", "11:25"), SectionTime(6, "11:30", "12:05"),
        SectionTime(7, "12:20", "12:55"), SectionTime(8, "13:00", "13:35"), SectionTime(9, "13:40", "14:15"),
        SectionTime(10, "14:30", "15:05"), SectionTime(11, "15:10", "15:45"), SectionTime(12, "15:50", "16:25"),
        SectionTime(13, "16:40", "17:15"), SectionTime(14, "17:20", "17:55"), SectionTime(15, "18:00", "18:35"),
        SectionTime(16, "18:50", "19:25"), SectionTime(17, "19:30", "20:05"), SectionTime(18, "20:10", "20:45")
    )

    /** 按星期取作息表：1/3/5=周一三五，2/4=周二四，其余回退周一三五。 */
    fun forDay(dayOfWeek: Int): List<SectionTime> =
        if (dayOfWeek == 2 || dayOfWeek == 4) tueThu else list

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(sections: List<SectionTime>): String = json.encodeToString(sections)

    fun decode(raw: String): List<SectionTime> =
        runCatching { json.decodeFromString<List<SectionTime>>(raw) }.getOrElse { list }
}
