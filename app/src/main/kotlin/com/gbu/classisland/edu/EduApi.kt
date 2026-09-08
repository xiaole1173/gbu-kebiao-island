// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.edu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 教务系统（统一身份认证 + 课表接口）客户端。
 * 统一认证地址与教务系统地址均由用户在「设置 / 新手引导」中手动填写
 * （不在 App 内置、不推导任何学校域名）。
 *
 * 登录链路（已验证，无验证码时一次通过）：
 *   GET  <认证>/iaaa/oauth.jsp?appID=...&redirectUrl=...   → 种会话 cookie
 *   POST <认证>/iaaa/oauthlogin.do (form)                  → {"success":true,"token":"..."}
 *   GET  <教务>/oauth/login/code?_rand=1&token=TOKEN        → 落地 /authentication/main 建立会话
 *
 * 仅获取本人课表数据，绝不批量爬取。
 */
class EduApi(
    /** 统一身份认证地址（如 https://iaaa.学校域名） */
    private val authBaseUrl: String = "",
    /** 教务系统地址（如 https://jwxt.学校域名） */
    private val baseUrl: String = "",
    private val client: OkHttpClient = defaultClient()
) {
    // coerceInputValues: 服务端个别记录（如备注行）JSJC/KSJC 可能为 null，强制为默认值
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val normalizedAuth: String get() = normalizeBaseUrl(authBaseUrl)
    private val normalizedBase: String get() = normalizeBaseUrl(baseUrl)
    private val callback: String get() = "$normalizedBase/oauth/login/code"
    private val loginPage: String get() = "$normalizedAuth/iaaa/oauth.jsp?appID=$APP_ID&redirectUrl=$callback"

    /** 两个地址是否都已配置（未配置时登录/同步应引导用户先填写）。 */
    fun isConfigured(): Boolean = normalizedAuth.isNotBlank() && normalizedBase.isNotBlank()

    companion object {
        const val APP_ID = "gbu_jwxt"

        /** 规范化地址：补协议、去尾斜杠，返回 "https://host"。空输入返回空串。 */
        fun normalizeBaseUrl(raw: String): String {
            var s = raw.trim()
            if (s.isBlank()) return ""
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            return s.trimEnd('/')
        }

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(MemoryCookieJar())
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
    }

    // ── 登录 ────────────────────────────────────────────────────────────────

    @Serializable
    private data class LoginResponse(val success: Boolean = false, val token: String = "", val errors: Errors? = null) {
        @Serializable data class Errors(val code: String? = null, val msg: String? = null)
    }

    /** 登录结果：区分「成功 / 密码错误 / 网络不可达」，避免把外网无法访问误报成账号密码错误。 */
    sealed class LoginResult {
        data object Success : LoginResult()
        data class WrongCredentials(val msg: String? = null) : LoginResult()
        data class NetworkError(val cause: Exception? = null) : LoginResult()
    }

    /**
     * 统一身份认证登录。成功后会话 cookie 已写入 CookieJar。
     * 教务地址未配置时返回 NetworkError（调用方应提示先填写地址）。
     */
    suspend fun login(userName: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext LoginResult.NetworkError()
        try {
            // 1) 访问登录页种 cookie
            client.newCall(Request.Builder().url(loginPage).build()).execute().use { }

            // 2) POST oauthlogin.do
            val body = FormBody.Builder()
                .add("appid", APP_ID)
                .add("userName", userName)
                .add("password", password)
                .add("randCode", "")
                .add("smsCode", "")
                .add("otpCode", "")
                .add("redirUrl", callback)
                .build()
            val req = Request.Builder()
                .url("$normalizedAuth/iaaa/oauthlogin.do")
                .post(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", loginPage)
                .build()
            val resp = client.newCall(req).execute()
            val text = resp.body?.string() ?: return@withContext LoginResult.NetworkError()
            val login = json.decodeFromString<LoginResponse>(text)
            if (!login.success) {
                return@withContext LoginResult.WrongCredentials(login.errors?.msg)
            }

            // 3) 带 token 回跳，建立教务会话
            client.newCall(
                Request.Builder().url("$callback?_rand=1&token=${login.token}").build()
            ).execute().use { }
            LoginResult.Success
        } catch (e: Exception) {
            LoginResult.NetworkError(e)
        }
    }

    // ── 课表接口 ────────────────────────────────────────────────────────────

    @Serializable
    data class TimetableBlock(
        val RWH: String = "",
        val KSJC: Int = 0,
        val JSJC: Int = 0,
        val ZC: String = "",
        val KEY: String = "",
        val SKSJ: String = "",
        val SKSJ_EN: String? = null,
        val XB: Int = 0,
        val PYLX: String = "",
        val SKFS: String? = null,
        val KCWZSM: String? = null,
        val SFFXEXW: String? = null,
        val FILEURL: String? = null
    )

    @Serializable
    data class Xnxq(val XN: String = "", val XQ: String = "")

    /** 课程库条目（kck/kcxxwh/queryKcxxwhList 单条）。 */
    @Serializable
    data class CourseLibraryItem(
        val kcdm: String = "",
        val kcmc: String = "",
        val xf: Double = 0.0,
        val kcxzmc: String? = null,
        val kclbmc: String? = null,
        val pylx: String? = null
    )

    @Serializable
    private data class CourseLibraryResponse(
        val total: Int = 0,
        val list: List<CourseLibraryItem> = emptyList()
    )

    /** 全校课表（任务查询）条目：RWH → 任务名称（rwmc 含班级/分组，如 "…-01班-2组"）。 */
    @Serializable
    data class RwxxItem(
        val rwh: String = "",
        val rwmc: String = "",
        val pylx: String? = null
    )

    @Serializable
    private data class RwxxListResp(val total: Int = 0, val list: List<RwxxItem> = emptyList())

    @Serializable
    private data class RwxxResponse(val rwList: RwxxListResp = RwxxListResp())

    /** 总课表（整学期全部课程块），同步主数据源。 */
    suspend fun fetchTotalTimetable(xn: String, xq: String): List<TimetableBlock> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder().add("xn", xn).add("xq", xq).build()
                val req = Request.Builder()
                    .url("$normalizedBase/xszykb/queryxszykbzong")
                    .post(body)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$normalizedBase/authentication/main")
                    .build()
                val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
                if (text.isBlank()) emptyList()
                else json.decodeFromString<List<TimetableBlock>>(text)
            }.getOrDefault(emptyList())
        }

    /** 全部学年学期列表（含历史学期），用于多学期同步：遍历各教学学期逐个拉课表。 */
    suspend fun fetchSemesters(): List<Xnxq> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$normalizedBase/component/queryxnxqdata")
                .post(FormBody.Builder().build())
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<Xnxq>>(text)
        }.getOrDefault(emptyList())
    }

    /** 当前学年学期。 */
    suspend fun fetchCurrentXnxq(): Xnxq? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$normalizedBase/component/querydangqianxnxq")
                .post(FormBody.Builder().build())
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
            if (text.isBlank()) null else json.decodeFromString<Xnxq>(text)
        }.getOrNull()
    }

    /**
     * 当前教学周（未开学=0）。返回裸数字如 "0"、"5"。
     * 用于开学后反推校历开学日期（第 1 周周一）。
     */
    suspend fun fetchCurrentWeek(): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$normalizedBase/component/querydangqianzc")
                .post(FormBody.Builder().build())
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
            text.trim().toIntOrNull()
        }.getOrNull()
    }

    /**
     * 教务课程库（全校一张表，跨学期/跨年级通用）。返回每门课的课程代码、名称、学分、
     * 课程性质（必修/选修）、课程类别（含 "通识选修课-通识选修课A..F"）。
     *
     * 只取本科（pylx=1）课程：同一个库同时含本科与研究生课程，按培养类型过滤。
     */
    suspend fun fetchCourseLibrary(): List<CourseLibraryItem> = withContext(Dispatchers.IO) {
        runCatching {
            val all = mutableListOf<CourseLibraryItem>()
            var pageNum = 1
            while (true) {
                val body = FormBody.Builder()
                    .add("pageNum", pageNum.toString())
                    .add("pageSize", "200")
                    .add("kcmc", "").add("pylb", "").add("kkxys", "").add("pylbmenu", "")
                    .add("kcztdm", "").add("ordertext", "").add("skyydm", "")
                    .add("kcxzdms", "").add("kclbdms", "").add("sfsyk", "")
                    .add("sfsjk", "").add("rwlxdm", "").add("kctdcy", "")
                    .add("sfckkc", "1")
                    .build()
                val req = Request.Builder()
                    .url("$normalizedBase/kck/kcxxwh/queryKcxxwhList")
                    .post(body)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$normalizedBase/authentication/main")
                    .build()
                val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
                if (text.isBlank()) break
                val resp = json.decodeFromString<CourseLibraryResponse>(text)
                all += resp.list
                if (resp.list.isEmpty() || all.size >= resp.total) break
                pageNum++
            }
            all.filter { it.pylx == "1" }
        }.getOrDefault(emptyList())
    }

    /**
     * 任务名称（rwmc，含班级/分组信息）按 RWH 的映射。
     * 来源：全校课表 Xsxktz/queryRwxxcxList（本科 p_chaxunpylx=1），best-effort。
     */
    suspend fun fetchTaskInfoMap(xn: String, xq: String): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            val all = mutableMapOf<String, String>()
            var pageNum = 1
            while (true) {
                val body = FormBody.Builder()
                    .add("p_chapylx", "").add("ordertext_0", "")
                    .add("p_xn", xn).add("p_xq", xq).add("p_xnxq", "$xn-$xq")
                    .add("p_gjz", "").add("p_xiaoqu", "").add("p_kkyx", "").add("p_rwlx", "")
                    .add("p_kclb", "").add("p_kcxz", "").add("p_chaxungjz", "").add("p_chaxunxiaoqu", "")
                    .add("p_chaxunkkyx", "").add("p_chaxunnj", "").add("p_chaxunglyx", "").add("p_chaxunzy", "")
                    .add("p_chaxunxdm", "").add("p_chaxunpylx", "1").add("mxpylx", "1")
                    .add("p_zc", "").add("p_xqj", "").add("p_ksjc", "").add("p_jsjc", "").add("p_skjs", "")
                    .add("p_sfhltsxx", "0")
                    .add("pageNum", pageNum.toString()).add("pageSize", "200")
                    .build()
                val req = Request.Builder()
                    .url("$normalizedBase/Xsxktz/queryRwxxcxList")
                    .post(body)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$normalizedBase/authentication/main")
                    .build()
                val text = client.newCall(req).execute().use { it.body?.string() ?: "" }
                if (text.isBlank()) break
                val resp = json.decodeFromString<RwxxResponse>(text)
                resp.rwList.list.forEach { item ->
                    if (item.rwmc.isNotBlank() && item.pylx == "1") all[item.rwh] = item.rwmc
                }
                if (resp.rwList.list.isEmpty() || resp.rwList.list.size < 200) break
                pageNum++
            }
            all
        }.getOrDefault(emptyMap())
    }
}

/** 进程内 cookie jar（后续可持久化以复用会话、减少登录频率）。 */
class MemoryCookieJar : CookieJar {
    private val cache = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = cache.getOrPut(url.host) { mutableListOf() }
        val names = cookies.map { it.name }
        list.removeAll { c -> c.name in names }
        list.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cache[url.host]?.filter { it.expiresAt > System.currentTimeMillis() } ?: emptyList()
}
