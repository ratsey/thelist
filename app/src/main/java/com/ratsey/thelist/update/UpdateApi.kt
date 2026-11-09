package com.ratsey.thelist.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
) {
    fun isNewerThan(current: String): Boolean = version.isNewerThan(current)
}

/** Compares dotted versions ("1.2.3" vs "1.2.10"); a leading "v" is ignored. */
fun String.isNewerThan(current: String): Boolean {
    val remote = numericParts(this)
    val local = numericParts(current)
    for (index in 0 until maxOf(remote.size, local.size)) {
        val r = remote.getOrElse(index) { 0 }
        val l = local.getOrElse(index) { 0 }
        if (r != l) return r > l
    }
    return false
}

private fun numericParts(version: String): List<Int> =
    version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '+', ' ')
        .mapNotNull { it.toIntOrNull() }

/**
 * Reads the latest GitHub release. Nothing here needs a token: the repository and its
 * releases must be public, which is the point of shipping the APK through GitHub.
 */
class UpdateApi(private val owner: String, private val repo: String) {

    val configured: Boolean
        get() = owner.isNotBlank() &&
            repo.isNotBlank() &&
            !owner.startsWith("your-") &&
            !owner.contains("<")

    suspend fun latestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null

        val connection = runCatching {
            (URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Trolley-Android")
            }
        }.getOrNull() ?: return@withContext null

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): ReleaseInfo? = runCatching {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").ifBlank { json.optString("name") }
        if (tag.isBlank()) return null

        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val url = assets.optJSONObject(index)?.optString("browser_download_url").orEmpty()
                if (url.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = url
                    break
                }
            }
        }
        val downloadUrl = apkUrl ?: return null

        ReleaseInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = json.optString("body"),
            apkUrl = downloadUrl,
            pageUrl = json.optString("html_url"),
        )
    }.getOrNull()
}
