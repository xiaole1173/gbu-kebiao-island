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

/** 默认作息表（大学常见，45 分钟/节，可编辑）。 */
object DefaultSections {
    val list: List<SectionTime> = listOf(
        SectionTime(1, "08:00", "08:45"),
        SectionTime(2, "08:45", "09:30"),
        SectionTime(3, "09:30", "10:15"),
        SectionTime(4, "10:15", "11:00"),
        SectionTime(5, "11:00", "11:45"),
        SectionTime(6, "11:45", "12:30"),
        SectionTime(7, "12:30", "13:15"),
        SectionTime(8, "13:15", "14:00"),
        SectionTime(9, "14:00", "14:45"),
        SectionTime(10, "14:45", "15:30"),
        SectionTime(11, "15:30", "16:15"),
        SectionTime(12, "16:15", "17:00"),
        SectionTime(13, "17:00", "17:45"),
        SectionTime(14, "17:45", "18:30"),
        SectionTime(15, "18:30", "19:15"),
        SectionTime(16, "19:15", "20:00"),
        SectionTime(17, "20:00", "20:45"),
        SectionTime(18, "20:45", "21:30")
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(sections: List<SectionTime>): String = json.encodeToString(sections)

    fun decode(raw: String): List<SectionTime> =
        runCatching { json.decodeFromString<List<SectionTime>>(raw) }.getOrElse { list }
}
