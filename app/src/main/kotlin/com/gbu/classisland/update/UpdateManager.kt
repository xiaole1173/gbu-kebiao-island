package com.gbu.classisland.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.gbu.classisland.edu.EduApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

/**
 * 应用更新（Gitee 发行版驱动）。
 *
 * 机制：公开仓库免 token 读取。每次发版在 Gitee 仓库发一个"发行版"：
 *   tag = "v{versionCode}"（如 v2）、正文 = 更新日志、附件 = app-release.apk。
 * App 拉取 releases/latest → 解析 tag 里的 versionCode → 与本地比对 →
 * 下载 APK → FileProvider 拉起系统安装器。
 */
object UpdateManager {

    const val OWNER = "xiaole1173"
    const val REPO = "gbu-kebiao-island"
    /** 自动检查间隔：6 小时（手动"检查更新"不受限）。 */
    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val PREFS = "update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val APK_FILE_NAME = "app-release.apk"

    private val json = Json { ignoreUnknownKeys = true }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val changelog: String,
        val apkUrl: String
    )

    @Serializable
    private data class GiteeRelease(
        val id: Long = 0,
        @SerialName("tag_name") val tagName: String = "",
        val body: String? = null
    )

    @Serializable
    private data class GiteeAttachment(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = ""
    )

    /** 自动检查是否到时间（距上次成功检查 ≥ 6 小时）。 */
    fun shouldAutoCheck(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS
    }

    /** 本地已安装的 versionCode。 */
    fun localVersionCode(context: Context): Int =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)

    /**
     * 拉取最新发行版信息；网络/解析失败返回 null（失败不记检查时间，下次启动会重试）。
     * 返回的 [UpdateInfo] 即使版本不高也有值，是否更新由调用方比对 versionCode。
     */
    suspend fun checkLatest(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val client = EduApi.defaultClient()
            // 1) 最新发行版
            val releaseUrl = "https://gitee.com/api/v5/repos/$OWNER/$REPO/releases/latest"
            val releaseText = client.newCall(Request.Builder().url(releaseUrl).build()).execute()
                .use { it.body?.string() ?: return@use null } ?: return@runCatching null
            val release = json.decodeFromString<GiteeRelease>(releaseText)
            val versionCode = release.tagName.removePrefix("v").trim().toIntOrNull()
                ?: return@runCatching null

            // 2) APK 附件直链（拿不到则退回约定路径）
            val apkUrl = runCatching {
                val attachText = client.newCall(
                    Request.Builder().url(
                        "https://gitee.com/api/v5/repos/$OWNER/$REPO/releases/${release.id}/attach_files"
                    ).build()
                ).execute().use { it.body?.string() ?: "" }
                if (attachText.isBlank()) null
                else json.decodeFromString<List<GiteeAttachment>>(attachText)
                    .firstOrNull { it.name.endsWith(".apk") }?.downloadUrl
            }.getOrNull() ?: "https://gitee.com/$OWNER/$REPO/releases/download/${release.tagName}/$APK_FILE_NAME"

            // 检查成功才记时间（限制自动检查频率；失败留待下次重试）
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            UpdateInfo(versionCode, release.tagName, release.body.orEmpty(), apkUrl)
        }.getOrNull()
    }

    /** 下载 APK 到缓存目录，回调下载进度 [onProgress]（0..1）。 */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, APK_FILE_NAME)
        val client = EduApi.defaultClient()
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败（HTTP ${resp.code}）")
            val body = resp.body ?: error("下载失败：空响应")
            val total = body.contentLength()
            var read = 0L
            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        file
    }

    /** 拉起系统安装器安装 APK。返回 false 表示未授予"安装未知来源应用"。 */
    fun install(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 跳转"允许安装未知来源应用"设置页。 */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
