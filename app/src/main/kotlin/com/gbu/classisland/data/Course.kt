// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一节课（一条课程记录）。
 * 字段覆盖教务课表的基本维度；semesterId 用于多学期隔离。
 */
@Serializable
@Entity(
    tableName = "courses",
    indices = [Index("semesterId"), Index("dayOfWeek")]
)
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 课程名 */
    val name: String,
    /** 教师 */
    val teacher: String = "",
    /** 教室/地点 */
    val location: String = "",
    /** 星期几：1=周一 .. 7=周日 */
    val dayOfWeek: Int,
    /** 起始节次（1 起） */
    val startSection: Int,
    /** 结束节次 */
    val endSection: Int,
    /** 起始周（1 起） */
    val startWeek: Int = 1,
    /** 结束周 */
    val endWeek: Int = 18,
    /** 周类型：0=每周 1=单周 2=双周（由 weeks 派生的便捷字段） */
    val weekParity: Int = 0,
    /** 精确周次集合，逗号分隔（如 "1,2,3,4"、"2,4"、"1,2,...,16"），来自教务 ZC 位图 */
    val weeks: String = "",
    /** 备注 */
    val note: String = "",
    /** 学期标识（如 2026-2027-1） */
    val semesterId: String = "",
    /** 来源：manual 手动 / edu 教务同步 / excel 导入 */
    val source: String = "manual",
    /** 教务任物号 RWH（如 2026-2027-1-MATH103-001B），用于同步变更对比；手动课程可为空 */
    val externalId: String = "",
    /** 任务名称（全校课表接口 rwmc，含班级/分组信息，如 "计算机科学导论（上）-01班-2组"） */
    val taskName: String = ""
)
