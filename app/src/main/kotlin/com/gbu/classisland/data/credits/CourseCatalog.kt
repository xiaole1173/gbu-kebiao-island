// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data.credits

import com.gbu.classisland.data.Course
import com.gbu.classisland.data.LibraryCourse

/**
 * 通识选修六大类别（官方：大湾区大学 2026 级本科生培养方案）。
 * 本科要求：选修至少 12 学分，且涵盖至少 3 类不同类别。
 */
enum class Category(val label: String, val desc: String) {
    A("A", "数学与自然科学类"),
    B("B", "社会科学类"),
    C("C", "人文科学类"),
    D("D", "语言与艺术类"),
    E("E", "个人能力与职业发展类"),
    F("F", "交叉学科与前沿技术")
}

/**
 * 课程目录：课程编码 → 学分 / 必修选修 / 通识类别。
 * 数据来自《附件3：2026级本科生秋季开课计划.xlsx》。
 * 必修课无通识类别；选修课按编码后缀归入 A/C/D/F 类。
 */
object CourseCatalog {

    const val REQUIRED_ELECTIVE_CREDITS = 12.0
    const val REQUIRED_CATEGORY_COUNT = 3

    data class Entry(
        val code: String,
        val name: String,
        val credits: Double,
        val required: Boolean,
        val category: Category? = null
    )

    val entries: List<Entry> = listOf(
        // ── 公共必修课 ──
        Entry("IPC101", "思想道德与法治", 3.0, true),
        Entry("ENGL101", "大学英语1", 2.0, true),
        Entry("PEC101", "体育1", 1.0, true),
        Entry("MT101", "军事技能", 2.0, true),
        // ── 专业必修课 ──
        Entry("MATH101", "高等数学1", 5.0, true),
        Entry("MATH102", "数学分析1", 5.0, true),
        Entry("MATH103", "线性代数", 4.0, true),
        Entry("CS101", "计算机科学导论（上）", 4.0, true),
        Entry("PHY101", "物理原理1", 4.0, true),
        // ── 答疑课（0 学分必修）──
        Entry("TUT01", "《计算机科学导论（上）》答疑课", 0.0, true),
        Entry("TUT02", "《物理原理1》习题答疑课", 0.0, true),
        // ── 通识选修课 ──
        Entry("GEC01A", "化学概论", 2.0, false, Category.A),
        Entry("GEC02A", "前沿物理进展", 1.0, false, Category.A),
        Entry("GEC04A", "新能源可持续发展技术简介", 1.0, false, Category.A),
        Entry("GEC05A", "数学实验班Seminar1", 2.0, false, Category.A),
        Entry("GEC06A", "人工智能研讨课1", 2.0, false, Category.A),
        Entry("GEC019F", "智能体架构与Vibe Coding 实战", 2.0, false, Category.F),
        Entry("GEC15D", "歌唱的魅力", 2.0, false, Category.D),
        Entry("GEC018C", "大湾区大学人文素养名家讲堂", 2.0, false, Category.C)
    )

    /** 从教务任物号 RWH（2026-2027-1-MATH101-002A）提取课程编码。 */
    fun extractCode(rwh: String): String? {
        val parts = rwh.split("-")
        return if (parts.size >= 4) parts[3] else null
    }

    fun find(code: String): Entry? = entries.find { it.code == code }

    fun findByRwh(rwh: String): Entry? = extractCode(rwh)?.let { find(it) }

    /** 学分汇总结果。 */
    data class CreditSummary(
        val requiredCredits: Double = 0.0,
        val electiveCredits: Double = 0.0,
        val electiveByCategory: Map<Category, Double> = emptyMap(),
        val courseCount: Int = 0
    ) {
        val totalCredits: Double get() = requiredCredits + electiveCredits
        val categoriesCovered: List<Category>
            get() = electiveByCategory.filterValues { it > 0 }.keys.sortedBy { it.label }
        val creditProgress: Float
            get() = (electiveCredits / REQUIRED_ELECTIVE_CREDITS).toFloat().coerceIn(0f, 1f)
        val categoryRequirementMet: Boolean
            get() = categoriesCovered.size >= REQUIRED_CATEGORY_COUNT
        val creditRequirementMet: Boolean
            get() = electiveCredits >= REQUIRED_ELECTIVE_CREDITS
    }

    /** 汇总本学期已选课程的学分（同一课程编码只计一次，实验/答疑不重复计）。 */
    fun summarize(courses: List<Course>): CreditSummary {
        var required = 0.0
        var elective = 0.0
        val byCat = mutableMapOf<Category, Double>()
        val seen = mutableSetOf<String>()
        var count = 0
        for (c in courses) {
            val e = findByRwh(c.externalId) ?: continue
            if (!seen.add(e.code)) continue
            count++
            if (e.required) {
                required += e.credits
            } else {
                elective += e.credits
                e.category?.let { byCat[it] = (byCat[it] ?: 0.0) + e.credits }
            }
        }
        return CreditSummary(required, elective, byCat, count)
    }

    /**
     * 从课程库条目解析通识选修 A–F 类别。
     * 优先用库的 kclbmc（形如 "通识选修课-通识选修课A"，末尾字母即类别）；
     * 库里个别 GEC 课类别字段标错（如 GEC06A 人工智能研讨课1 被标成"专业选修课"），
     * 此时回退到课程代码末位字母（GEC 前缀即通识选修，GEC06A→A）。
     */
    fun categoryOf(lib: LibraryCourse): Category? {
        lib.kclbmc?.let { s ->
            if (s.startsWith("通识选修课")) {
                Category.entries.firstOrNull { it.label == s.takeLast(1) }?.let { return it }
            }
        }
        if (lib.kcdm.startsWith("GEC")) {
            return Category.entries.firstOrNull { it.label == lib.kcdm.takeLast(1) }
        }
        return null
    }

    /** 课程库条目 → 内置 Entry 形态（用于课程详情页的学分/必修选修展示）。 */
    fun fromLibrary(lib: LibraryCourse): Entry {
        val cat = categoryOf(lib)
        val isElective = lib.kcdm.startsWith("GEC") ||
            (lib.kcxzmc?.contains("选修") == true) ||
            (lib.kclbmc?.contains("选修") == true)
        return Entry(lib.kcdm, lib.kcmc, lib.xf, required = !isElective, category = cat)
    }

    /**
     * 基于课程库的学分汇总（多学期通用）：按课程代码在库中查学分/性质/类别，
     * 累加必修与通识选修（A–F）学分、统计覆盖类别。库中未命中的课程回退内置目录。
     */
    fun summarize(courses: List<Course>, library: List<LibraryCourse>): CreditSummary {
        val libByCode = if (library.isNotEmpty()) library.associateBy { it.kcdm } else emptyMap()
        var required = 0.0
        var elective = 0.0
        val byCat = mutableMapOf<Category, Double>()
        val seen = mutableSetOf<String>()
        var count = 0
        for (c in courses) {
            val code = extractCode(c.externalId) ?: continue
            if (!seen.add(code)) continue
            val lib = libByCode[code]
            if (lib != null) {
                count++
                val cat = categoryOf(lib)
                if (cat != null) {
                    elective += lib.xf
                    byCat[cat] = (byCat[cat] ?: 0.0) + lib.xf
                } else {
                    required += lib.xf
                }
            } else {
                // 课程库未命中（未同步 / 新开课）→ 回退内置目录
                val e = find(code) ?: continue
                count++
                if (e.required) {
                    required += e.credits
                } else {
                    elective += e.credits
                    e.category?.let { byCat[it] = (byCat[it] ?: 0.0) + e.credits }
                }
            }
        }
        return CreditSummary(required, elective, byCat, count)
    }

    /**
     * 体育1（PEC101）任课教师 → 可选项目（数据来自附件3 开课计划）。
     * 教务课表不返回具体项目，此映射用于详情页提示用户实际项目。
     */
    val peProjectsByTeacher: Map<String, String> = mapOf(
        "胡海斌" to "武术 / 篮球 / 太极拳",
        "崔耀民" to "羽毛球 / 乒乓球"
    )

    fun peProjectHint(teacher: String): String? = peProjectsByTeacher[teacher]
}
