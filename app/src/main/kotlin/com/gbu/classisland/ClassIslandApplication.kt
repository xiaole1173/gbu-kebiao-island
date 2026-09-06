// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.gbu.classisland.data.AppDatabase
import com.gbu.classisland.notification.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClassIslandApplication : Application() {

    /** 全局数据库实例（Room）。后续业务仓库由此派生。 */
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // 创建通知频道（Android 8+ 无频道则不显示通知）
        NotificationChannels.ensure(this)
        // 跟踪前台状态（提醒触发时决定：前台直接全屏 / 后台 fullScreenIntent）
        registerActivityLifecycleCallbacks(ForegroundTracker)
        // 启动时清理重复课程（教务可能返回重复块，避免周课表/今日重叠）
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dao = database.courseDao()
                val all = dao.getAll()
                val seen = mutableSetOf<String>()
                val toDelete = mutableListOf<Long>()
                for (c in all) {
                    val key = listOf(c.externalId, c.dayOfWeek, c.startSection, c.endSection, c.weeks)
                        .joinToString("|")
                    if (!seen.add(key)) toDelete += c.id
                }
                if (toDelete.isNotEmpty()) dao.deleteByIds(toDelete)
            }
        }
    }
}

/** 前台状态跟踪：供提醒接收器判断当前 App 是否在前台。 */
object ForegroundTracker : Application.ActivityLifecycleCallbacks {

    @Volatile
    var isForeground: Boolean = false
        private set

    private var started = 0
    private var resumed = 0

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        update()
    }

    override fun onActivityResumed(activity: Activity) {
        resumed++
        update()
    }

    override fun onActivityPaused(activity: Activity) {
        resumed--
    }

    private fun update() {
        isForeground = started > 0 && resumed > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
