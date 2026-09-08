// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gbu.classisland.model.DefaultSections
import com.gbu.classisland.model.SectionTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 应用设置（不含敏感凭据，密码走 SecureStore）。 */
data class AppSettings(
    /** 教务账号（仅用于展示） */
    val eduUserName: String = "",
    /** 是否自动同步 */
    val autoSyncEnabled: Boolean = true,
    /** 上课前提前提醒分钟数 */
    val remindBeforeMinutes: Int = 10,
    /** 是否开启下课提醒 */
    val remindAfterEnabled: Boolean = false,
    /** 是否开启岛/胶囊显示 */
    val islandEnabled: Boolean = true,
    /** 主题：system / light / dark */
    val themeMode: String = "system",
    /** 当前学年学期（如 2026-2027-1） */
    val currentSemesterId: String = "",
    /** 开学日期（校历），用于当前周计算 */
    val termStartDate: String = "",
    /** 节次时间表（可编辑） */
    val sections: List<SectionTime> = DefaultSections.list,
    /** 教务系统地址（学生手动填写，如 https://jwxt.学校域名；空 = 未设置） */
    val eduBaseUrl: String = "",
    /** 统一身份认证地址（学生手动填写，如 https://iaaa.学校域名；空 = 未设置） */
    val eduAuthBaseUrl: String = ""
)

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            eduUserName = prefs[KEY_EDU_USER] ?: "",
            autoSyncEnabled = prefs[KEY_AUTO_SYNC] ?: true,
            remindBeforeMinutes = prefs[KEY_REMIND_BEFORE] ?: 10,
            remindAfterEnabled = prefs[KEY_REMIND_AFTER] ?: false,
            islandEnabled = prefs[KEY_ISLAND] ?: true,
            themeMode = prefs[KEY_THEME] ?: "system",
            currentSemesterId = prefs[KEY_SEMESTER] ?: "",
            termStartDate = prefs[KEY_TERM_START] ?: "",
            sections = DefaultSections.decode(prefs[KEY_SECTIONS] ?: ""),
            eduBaseUrl = prefs[KEY_EDU_BASE_URL] ?: "",
            eduAuthBaseUrl = prefs[KEY_EDU_AUTH_BASE_URL] ?: ""
        )
    }

    suspend fun setEduUserName(name: String) = dataStore.edit { it[KEY_EDU_USER] = name }

    suspend fun setAutoSync(enabled: Boolean) = dataStore.edit { it[KEY_AUTO_SYNC] = enabled }

    suspend fun setRemindBefore(minutes: Int) = dataStore.edit { it[KEY_REMIND_BEFORE] = minutes }

    suspend fun setRemindAfter(enabled: Boolean) = dataStore.edit { it[KEY_REMIND_AFTER] = enabled }

    suspend fun setIsland(enabled: Boolean) = dataStore.edit { it[KEY_ISLAND] = enabled }

    suspend fun setThemeMode(mode: String) = dataStore.edit { it[KEY_THEME] = mode }

    suspend fun setCurrentSemester(id: String) = dataStore.edit { it[KEY_SEMESTER] = id }

    suspend fun setTermStart(date: String) = dataStore.edit { it[KEY_TERM_START] = date }

    suspend fun setSections(sections: List<SectionTime>) =
        dataStore.edit { it[KEY_SECTIONS] = DefaultSections.encode(sections) }

    suspend fun setEduBaseUrl(url: String) = dataStore.edit { it[KEY_EDU_BASE_URL] = url.trim() }

    suspend fun setEduAuthBaseUrl(url: String) = dataStore.edit { it[KEY_EDU_AUTH_BASE_URL] = url.trim() }

    private companion object {
        val KEY_EDU_USER = stringPreferencesKey("edu_user_name")
        val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val KEY_REMIND_BEFORE = intPreferencesKey("remind_before_min")
        val KEY_REMIND_AFTER = booleanPreferencesKey("remind_after")
        val KEY_ISLAND = booleanPreferencesKey("island_enabled")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SEMESTER = stringPreferencesKey("current_semester")
        val KEY_TERM_START = stringPreferencesKey("term_start")
        val KEY_SECTIONS = stringPreferencesKey("sections_json")
        val KEY_EDU_BASE_URL = stringPreferencesKey("edu_base_url")
        val KEY_EDU_AUTH_BASE_URL = stringPreferencesKey("edu_auth_base_url")
    }
}
