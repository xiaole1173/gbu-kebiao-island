// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data.calendar

import java.time.LocalDate

/**
 * 大湾区大学 2026-2027 学年校历：节假日 / 停课日期（校历中灰色块）。
 *
 * 这些日期没有课程：周课表 / 今日视图隐藏对应课程、关闭提醒，并在 UI 标注。
 * 数据来源：《附件6：大湾区大学 2026-2027学年校历.pdf》。
 */
object AcademicCalendar {

    /** 节假日/停课日期区间（含两端）。 */
    private val holidayRanges: List<Pair<LocalDate, LocalDate>> = listOf(
        LocalDate.of(2026, 9, 25) to LocalDate.of(2026, 9, 27),  // 中秋节 9/25，9/25-27 法定节假日
        LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 7),  // 国庆节 10/1-7 法定节假日
        LocalDate.of(2026, 12, 28) to LocalDate.of(2027, 1, 8),  // 本科期末停课复习考试
        LocalDate.of(2027, 1, 11) to LocalDate.of(2027, 2, 21),  // 寒假
        LocalDate.of(2027, 4, 5) to LocalDate.of(2027, 4, 5),    // 清明节
        LocalDate.of(2027, 5, 1) to LocalDate.of(2027, 5, 7),    // 劳动节 + 春假停课
        LocalDate.of(2027, 6, 9) to LocalDate.of(2027, 6, 9),    // 端午节
        LocalDate.of(2027, 6, 14) to LocalDate.of(2027, 6, 25),  // 本科期末停课复习考试
        LocalDate.of(2027, 6, 28) to LocalDate.of(2027, 7, 30)   // 暑假
    )

    /** 本校历所属学年边界（2026-2027 学年）：只对该学年内的日期生效，避免误伤其他学年课表。 */
    private val YEAR_START = LocalDate.of(2026, 9, 1)
    private val YEAR_END = LocalDate.of(2027, 8, 31)

    /** 是否节假日/停课（当天无课）。仅对 2026-2027 学年内日期生效。 */
    fun isHoliday(date: LocalDate): Boolean {
        if (date.isBefore(YEAR_START) || date.isAfter(YEAR_END)) return false
        return holidayRanges.any { (start, end) -> !date.isBefore(start) && !date.isAfter(end) }
    }

    /** 具体节日名（仅标注主要节日；区间内其他日子返回 null）。 */
    fun holidayName(date: LocalDate): String? = when (date) {
        LocalDate.of(2026, 9, 25) -> "中秋节"
        LocalDate.of(2026, 10, 1) -> "国庆节"
        LocalDate.of(2027, 1, 1) -> "元旦"
        LocalDate.of(2027, 4, 5) -> "清明节"
        LocalDate.of(2027, 5, 1) -> "劳动节"
        LocalDate.of(2027, 6, 9) -> "端午节"
        else -> null
    }
}
