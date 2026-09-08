// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.edu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * 学校默认配置：从应用自有仓库（Gitee）拉取默认教务地址。
 * 目的：APK 不内置任何学校域名（合规）；已有用户升级后一次性自动继承，
 * 新安装用户不拉取、在设置/引导中手动填写。
 */
object SchoolConfig {
    private const val CONFIG_URL =
        "https://gitee.com/xiaole1173/gbu-kebiao-island/raw/main/school-default.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Config(val eduBaseUrl: String? = null)

    /** 拉取默认教务地址（best-effort，网络失败或字段缺失返回空串）。 */
    suspend fun fetchEduBaseUrl(): String = withContext(Dispatchers.IO) {
        runCatching {
            val client = EduApi.defaultClient()
            val text = client.newCall(Request.Builder().url(CONFIG_URL).build()).execute()
                .use { it.body?.string() ?: return@use null } ?: return@runCatching ""
            json.decodeFromString<Config>(text).eduBaseUrl.orEmpty()
        }.getOrDefault("")
    }
}
