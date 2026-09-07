// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.edu

import com.gbu.classisland.data.Course
import com.gbu.classisland.data.CourseDao
import com.gbu.classisland.data.LibraryCourse
import com.gbu.classisland.data.LibraryCourseDao

/**
 * 教务课表同步：登录 → 拉总课表 → 解析 → 变更对比 → 落库。
 * 只同步本人课表；由外部调度（WorkManager 每日一次 / 手动刷新）低频调用。
 */
class SyncRepository(
    private val api: EduApi,
    private val courseDao: CourseDao,
    private val libraryCourseDao: LibraryCourseDao
) {
    /** 同步结果；changes 仅在真正发生变化时非空。 */
    data class SyncResult(
        /** 当前学期（写入 settings.currentSemester，保持兼容）。 */
        val semesterId: String? = null,
        /** 所有学期同步到的课程条数总和。 */
        val syncedCount: Int = 0,
        val changed: Boolean = false,
        val added: List<Course> = emptyList(),
        val removed: List<Course> = emptyList(),
        val modified: List<Course> = emptyList(),
        /** 实际同步到数据的学期列表（如 ["2025-2026-1","2026-2027-1"]）。 */
        val syncedSemesters: List<String> = emptyList()
    )

    sealed class SyncError : Exception() {
        object NeedLogin : SyncError()
        object LoginFailed : SyncError()
        object FetchFailed : SyncError()
        data class Network(val msg: String) : SyncError()
        data class Message(val msg: String) : SyncError()
    }

    /**
     * 执行同步。需先通过 [ensureLoggedIn] 或传入有效会话。
     *
     * 多学期同步：遍历教务全部教学学期（秋季 XQ=1 / 春季 XQ=2，跳过暑假、夏季等），
     * 逐个拉总课表；无数据的学期（未选课 / 未来学期）返回空、直接丢弃。这样高年级
     * 用户的历史学期课表也会入库，App 内可切换查看。
     *
     * @throws SyncError 同步失败时（当前学期拉不到数据视为失败）
     */
    suspend fun sync(userName: String? = null, password: String? = null): SyncResult {
        if (userName != null && password != null) {
            when (val result = api.login(userName, password)) {
                EduApi.LoginResult.Success -> {}
                is EduApi.LoginResult.WrongCredentials -> throw SyncError.LoginFailed
                is EduApi.LoginResult.NetworkError -> throw SyncError.Network(
                    "无法连接教务系统（可能不在校园网或需要校园 VPN），请检查网络后重试"
                )
            }
        }

        val current = api.fetchCurrentXnxq() ?: throw SyncError.FetchFailed
        val currentSemesterId = "${current.XN}-${current.XQ}"
        if (currentSemesterId == "-") throw SyncError.FetchFailed

        // 全部学期（含历史）；只取教学学期（1 秋 / 2 春），过滤暑假(9)/夏季(3)等
        val semesters = api.fetchSemesters().filter { it.XQ == "1" || it.XQ == "2" }
        // 拿不到学期列表时退回只同步当前学期
        val targets = if (semesters.isEmpty()) listOf(current) else semesters

        var totalSynced = 0
        val addedAll = mutableListOf<Course>()
        val removedAll = mutableListOf<Course>()
        val modifiedAll = mutableListOf<Course>()
        val syncedSemesters = mutableListOf<String>()
        var anyChanged = false

        for (sem in targets) {
            val semesterId = "${sem.XN}-${sem.XQ}"
            val blocks = api.fetchTotalTimetable(sem.XN, sem.XQ)
            if (blocks.isEmpty()) continue // 该学期无数据 → 丢弃（未选课/未来学期）

            // 去重：教务可能返回重复块（同课/同天/同节次/同周次），避免周课表重叠
            val incoming = TimetableParser.parse(blocks, sem.XN, sem.XQ)
                .distinctBy { listOf(it.externalId, it.dayOfWeek, it.startSection, it.endSection, it.weeks) }
            if (incoming.isEmpty()) continue

            val existing = courseDao.getBySemester(semesterId)

            // 以 externalId（RWH）为键做变更对比（id 归一化后比较，避免 DB 主键干扰）
            val newById = incoming.groupBy { it.externalId }
            val oldById = existing.filter { it.source == "edu" }.groupBy { it.externalId }

            val added = incoming.filter { it.externalId !in oldById }
            val removed = oldById.keys.filter { it !in newById }
                .mapNotNull { oldById[it]?.first() }
            val modified = incoming.filter { c ->
                oldById[c.externalId]?.firstOrNull()?.let { old -> old.copy(id = 0L) != c } == true
            }

            val changed = added.isNotEmpty() || removed.isNotEmpty() || modified.isNotEmpty()

            // 落库：先清掉本学期的 edu 旧数据，再写入新数据（保持幂等）
            if (changed) {
                courseDao.deleteBySemester(semesterId)
                courseDao.upsertAll(incoming)
            }

            addedAll += added
            removedAll += removed
            modifiedAll += modified
            totalSynced += incoming.size
            syncedSemesters += semesterId
            if (changed) anyChanged = true
        }

        // 当前学期必须有数据，否则视为同步失败（避免"半同步"被当成成功）
        if (currentSemesterId !in syncedSemesters) throw SyncError.FetchFailed

        // 课程库（全校一张表）：best-effort 拉取并整表替换。
        // 拉不到时学分统计回退到内置目录，不影响课表同步成功。
        val library = api.fetchCourseLibrary()
        if (library.isNotEmpty()) {
            libraryCourseDao.deleteAll()
            libraryCourseDao.insertAll(
                library.map { LibraryCourse(it.kcdm, it.kcmc, it.xf, it.kcxzmc, it.kclbmc, it.pylx) }
            )
        }

        return SyncResult(
            semesterId = currentSemesterId,
            syncedCount = totalSynced,
            changed = anyChanged,
            added = addedAll,
            removed = removedAll,
            modified = modifiedAll,
            syncedSemesters = syncedSemesters
        )
    }
}
