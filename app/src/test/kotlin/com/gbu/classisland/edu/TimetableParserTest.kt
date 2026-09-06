// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.edu

import com.gbu.classisland.edu.EduApi.TimetableBlock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实教务数据（zongkb.json）验证课表解析逻辑。
 */
class TimetableParserTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── 基础单元 ────────────────────────────────────────────────────────────

    @Test
    fun parseDay_extractsWeekday() {
        assertEquals(5, TimetableParser.parseDay("xq5_jc9"))
        assertEquals(1, TimetableParser.parseDay("xq1_jc6"))
        assertEquals(7, TimetableParser.parseDay("xq7_jc2"))
        assertEquals(null, TimetableParser.parseDay("foo"))
    }

    @Test
    fun parseWeeks_bitmask() {
        // 真实格式：34 位，位置 p 的 '1' = 第 p 周（位置 0 恒为占位 0）
        // 第 1..16 周（实测线性代数 ZC）
        val weeks = TimetableParser.parseWeeks("0111111111111111100000000000000000")
        assertEquals((1..16).toSet(), weeks)
        // 第 2、4 周（实测物理实验[2,4周] ZC）
        val w2 = TimetableParser.parseWeeks("0010100000000000000000000000000000")
        assertEquals(setOf(2, 4), w2)
        // 第 6、8 周（实测物理实验[6,8周] ZC）
        val w3 = TimetableParser.parseWeeks("0000001010000000000000000000000000")
        assertEquals(setOf(6, 8), w3)
        // 空/短串
        assertEquals(emptySet<Int>(), TimetableParser.parseWeeks(""))
        assertEquals(emptySet<Int>(), TimetableParser.parseWeeks("0"))
    }

    @Test
    fun parseSksj_extractsMeta() {
        val meta = TimetableParser.parseSksj(
            "线性代数【实验】\n[柴子巍]\n[1-16周][B302]\n第11-12节"
        )
        assertEquals("线性代数【实验】", meta.name)
        assertEquals("柴子巍", meta.teacher)
        assertEquals("B302", meta.location)

        val noLoc = TimetableParser.parseSksj("物理原理1【实验】\n[何海燕]\n[2,4周][无地点]")
        assertEquals("物理原理1【实验】", noLoc.name)
        assertEquals("", noLoc.location)
    }

    @Test
    fun computeParity_cases() {
        assertEquals(0, TimetableParser.computeParity((1..16).toSet()))
        assertEquals(1, TimetableParser.computeParity(setOf(1, 3, 5, 7)))
        assertEquals(2, TimetableParser.computeParity(setOf(2, 4, 6, 8)))
        assertEquals(0, TimetableParser.computeParity(setOf(2, 4, 6, 7)))
    }

    // ── 真实数据 ─────────────────────────────────────────────────────────────

    @Test
    fun parseRealTimetable() {
        val raw = java.io.File("src/test/resources/zongkb.json").readText()
        val blocks = json.decodeFromString<List<TimetableBlock>>(raw)
        assertTrue("expect blocks", blocks.size > 0)

        val courses = TimetableParser.parse(blocks, "2026-2027", "1")
        assertTrue("parsed courses non-empty", courses.isNotEmpty())

        // 每条记录都必须解析出：名称、星期、周次、节次
        courses.forEach { c ->
            assertTrue("name non-blank: ${c.name}", c.name.isNotBlank())
            assertTrue("day 1..7: ${c.dayOfWeek}", c.dayOfWeek in 1..7)
            assertTrue("weeks non-empty", c.weeks.isNotBlank())
            assertTrue("section valid: ${c.startSection}", c.startSection >= 1 && c.endSection >= c.startSection)
            assertTrue("semester", c.semesterId == "2026-2027-1")
            assertTrue("source edu", c.source == "edu")
            assertTrue("externalId", c.externalId.startsWith("2026-2027-1-"))
        }

        // 与期望一致：存在线性代数（MATH103）
        assertTrue(courses.any { it.externalId.contains("MATH103") })
        // 每周课的 weekParity 为 0
        val weekly = courses.first { it.externalId.contains("MATH103") && it.name.contains("线性代数") }
        assertEquals(0, weekly.weekParity)
    }
}
