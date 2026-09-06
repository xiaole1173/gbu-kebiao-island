// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 教务课程库条目（来源 kck/kcxxwh/queryKcxxwhList）。
 * 全校一张表（跨学期、跨年级通用）：课程代码 → 名称/学分/课程性质/课程类别。
 *
 * [kclbmc] 形如 "公共必修课"、"专业必修课"、"专业选修课"、"通识选修课-通识选修课A"（A–F 类
 * 别直接写在此字段里），是学分统计（本科累计选修 ≥12 分且覆盖 ≥3 类）的权威数据源。
 */
@Entity(tableName = "library_course")
data class LibraryCourse(
    @PrimaryKey val kcdm: String,
    val kcmc: String,
    val xf: Double,
    val kcxzmc: String? = null,
    val kclbmc: String? = null,
    val pylx: String? = null
)