package com.englishfluency.ai.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val bodyNotes: String,
    val apkDownloadUrl: String,
    val htmlUrl: String
)

object GitHubUpdater {

    private const val REPO_LATEST_RELEASE_API =
        "https://api.github.com/repos/ladontsev/EnglishFluency/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    fun getCurrentAppVersion(context: Context): String {
        return runCatching {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
    }

    suspend fun checkLatestRelease(): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(REPO_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EnglishFluency-Android-Updater")
            }

            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            if (status !in 200..299) {
                throw IllegalStateException("GitHub API вернул код $status")
            }

            val root = json.parseToJsonElement(body).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
            val releaseName = root["name"]?.jsonPrimitive?.content ?: tagName
            val notes = root["body"]?.jsonPrimitive?.content.orEmpty()
            val htmlUrl = root["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/ladontsev/EnglishFluency/releases"

            val assets = root["assets"]?.jsonArray.orEmpty()
            val apkAsset = assets.firstOrNull { el ->
                val name = el.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                name.endsWith(".apk", ignoreCase = true)
            }?.jsonObject

            val downloadUrl = apkAsset?.get("browser_download_url")?.jsonPrimitive?.content
                ?: throw IllegalStateException("В релизе $tagName не найден прикрепленный .apk файл")

            GitHubReleaseInfo(
                tagName = tagName,
                releaseName = releaseName,
                bodyNotes = notes,
                apkDownloadUrl = downloadUrl,
                htmlUrl = htmlUrl
            )
        }
    }

    fun isNewerVersion(currentVersion: String, latestTag: String): Boolean {
        val cleanCurrent = currentVersion.removePrefix("v").trim()
        val cleanLatest = latestTag.removePrefix("v").trim()
        if (cleanCurrent == cleanLatest) return false

        val currParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return cleanLatest != cleanCurrent
    }

    suspend fun downloadApkToCache(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = File(context.cacheDir, "EnglishFluency-update.apk")
            if (targetFile.exists()) targetFile.delete()

            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "EnglishFluency-Android-Updater")
            }

            val totalBytes = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(pct)
                        }
                    }
                    output.flush()
                }
            }
            targetFile
        }
    }

    fun promptInstallApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
