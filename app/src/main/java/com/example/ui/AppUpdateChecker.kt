package com.example.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AppUpdateInfo(
    val available: Boolean,
    val latestVersion: String?,
    val downloadUrl: String?,
    val message: String
)

object AppUpdateChecker {
    private const val SUPABASE_URL = "https://ralinnuegsbuvlhwpzln.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFseSIsInJlZiI6ImphbGlubnVlZ3NidXZsaHdwemxuIiwiaWF0IjoxNzgwMjk1NjQyLCJleHAiOjIwOTU4NzE2NDJ9.hIec6UxRx5gzSMTi5oJ3_xXw3d1QKCmKsPF-stBwIFE"
    private const val APP_NAME = "Apna Translator"

    suspend fun check(context: Context): AppUpdateInfo = withContext(Dispatchers.IO) {
        try {
            val name = URLEncoder.encode(APP_NAME, "UTF-8")
            val endpoint = "$SUPABASE_URL/rest/v1/apps?select=name,version,apk_url&name=eq.$name&limit=1"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@withContext AppUpdateInfo(
                    false, null, null,
                    "Update server is unavailable. Please try again."
                )
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val latest = Regex(""version"\s*:\s*"([^"]+)"")
                .find(body)?.groupValues?.get(1)

            val downloadUrl = Regex(""apk_url"\s*:\s*"([^"]+)"")
                .find(body)?.groupValues?.get(1)
                ?.replace("\\/", "/")

            if (latest.isNullOrBlank()) {
                AppUpdateInfo(false, null, null, "No Apna Translator update is published yet.")
            } else {
                val current = context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName ?: "1.0.0"

                val newer = compareVersions(latest, current) > 0

                if (newer && !downloadUrl.isNullOrBlank()) {
                    AppUpdateInfo(
                        available = true,
                        latestVersion = latest,
                        downloadUrl = downloadUrl,
                        message = "New version $latest is available. Tap Download Update to install it."
                    )
                } else if (newer) {
                    AppUpdateInfo(
                        available = true,
                        latestVersion = latest,
                        downloadUrl = null,
                        message = "New version $latest is available, but the APK download link is not published yet."
                    )
                } else {
                    AppUpdateInfo(
                        available = false,
                        latestVersion = latest,
                        downloadUrl = null,
                        message = "You are up to date (v$current)."
                    )
                }
            }
        } catch (_: Exception) {
            AppUpdateInfo(
                false, null, null,
                "Could not check for updates. Please try again."
            )
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val av = a.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val bv = b.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(av.size, bv.size)) {
            val x = av.getOrElse(i) { 0 }
            val y = bv.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
