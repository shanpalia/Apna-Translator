package com.example.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import org.json.JSONObject

data class AppUpdateInfo(
    val available: Boolean,
    val latestVersion: String?,
    val downloadUrl: String?,
    val releaseNotes: String?,
    val message: String
)

object AppUpdateChecker {
    private const val UPDATE_URL =
        "https://shanpalia.github.io/WebsitePaliaAPK_V.2/updates/updates.json"

    suspend fun check(context: Context): AppUpdateInfo = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(UPDATE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext AppUpdateInfo(
                    false, null, null, null,
                    "Update server is unavailable. Please try again."
                )
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val root = JSONObject(body)
            val apps = root.optJSONArray("apps")

            if (apps == null) {
                return@withContext AppUpdateInfo(
                    false, null, null, null,
                    "Update manifest is unavailable. Please try again."
                )
            }

            val packageName = context.packageName
            var appJson: JSONObject? = null

            for (i in 0 until apps.length()) {
                val item = apps.optJSONObject(i) ?: continue
                if (item.optString("package_name").trim() == packageName) {
                    appJson = item
                    break
                }
            }

            if (appJson == null) {
                return@withContext AppUpdateInfo(
                    false, null, null, null,
                    "No update information is published for this app yet."
                )
            }

            val latest = appJson.optString("latest_version").trim()
            val downloadUrl = appJson.optString("download_url").trim().ifBlank { null }
            val releaseNotes = appJson.optString("release_notes").trim().ifBlank { null }

            if (latest.isBlank()) {
                return@withContext AppUpdateInfo(
                    false, null, null, null,
                    "No update information is published for this app yet."
                )
            }

            val current = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "0.0.0"

            val newer = compareVersions(latest, current) > 0

            when {
                newer && downloadUrl != null -> AppUpdateInfo(
                    available = true,
                    latestVersion = latest,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    message = "New version $latest is available."
                )

                newer -> AppUpdateInfo(
                    available = true,
                    latestVersion = latest,
                    downloadUrl = null,
                    releaseNotes = releaseNotes,
                    message = "New version $latest is available, but the download link is not published yet."
                )

                else -> AppUpdateInfo(
                    available = false,
                    latestVersion = latest,
                    downloadUrl = null,
                    releaseNotes = null,
                    message = "You are up to date (v$current)."
                )
            }
        } catch (_: Exception) {
            AppUpdateInfo(
                false, null, null, null,
                "Could not check for updates. Please try again."
            )
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val av = a.removePrefix("v").split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val bv = b.removePrefix("v").split(".", "-", "_").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(av.size, bv.size)) {
            val x = av.getOrElse(i) { 0 }
            val y = bv.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
