package app.trolley.lists.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import app.trolley.lists.BuildConfig
import app.trolley.lists.R
import app.trolley.lists.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateCheck {
    data object Skipped : UpdateCheck
    data object NotConfigured : UpdateCheck
    data object Unreachable : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val release: ReleaseInfo) : UpdateCheck
}

enum class DownloadState { RUNNING, SUCCESS, FAILED, MISSING }

data class DownloadStatus(val state: DownloadState, val percent: Int)

enum class InstallResult { STARTED, NEEDS_PERMISSION, FILE_MISSING, NO_INSTALLER }

/**
 * Self-update pipeline: ask GitHub for the newest release, download its APK with the system
 * DownloadManager (so it keeps going if the app is backgrounded) and hand the file to the
 * package installer. The installer only accepts an APK signed with the same key as the
 * installed build, so releases must be signed consistently in CI.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsStore,
) {

    private val api = UpdateApi(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)
    private val downloadManager: DownloadManager? by lazy {
        context.getSystemService(DownloadManager::class.java)
    }

    suspend fun check(force: Boolean): UpdateCheck {
        if (!api.configured) return UpdateCheck.NotConfigured
        if (!force) {
            val lastCheck = settings.lastCheckAt.first()
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return UpdateCheck.Skipped
        }
        val release = api.latestRelease() ?: return UpdateCheck.Unreachable
        settings.setLastCheck(System.currentTimeMillis())
        return if (release.isNewerThan(BuildConfig.VERSION_NAME)) {
            UpdateCheck.Available(release)
        } else {
            UpdateCheck.UpToDate
        }
    }

    suspend fun startDownload(release: ReleaseInfo): Long? = withContext(Dispatchers.IO) {
        val manager = downloadManager ?: return@withContext null
        updateDirectory().listFiles()?.forEach { it.delete() }

        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Trolley ${release.version}")
            .setDescription(context.getString(R.string.update_downloading_title, release.version))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${apkName(release.version)}",
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val id = runCatching { manager.enqueue(request) }.getOrNull() ?: return@withContext null
        settings.setPendingDownload(id, release.version)
        id
    }

    fun status(downloadId: Long): DownloadStatus {
        val manager = downloadManager ?: return DownloadStatus(DownloadState.MISSING, 0)
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return DownloadStatus(DownloadState.MISSING, 0)

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus(DownloadState.SUCCESS, 100)
                DownloadManager.STATUS_FAILED -> DownloadStatus(DownloadState.FAILED, 0)
                else -> DownloadStatus(DownloadState.RUNNING, percent)
            }
        }
    }

    fun install(version: String): InstallResult {
        val file = File(updateDirectory(), apkName(version))
        if (!file.exists()) return InstallResult.FILE_MISSING
        if (!context.packageManager.canRequestPackageInstalls()) return InstallResult.NEEDS_PERMISSION

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return InstallResult.NO_INSTALLER

        return runCatching {
            context.startActivity(intent)
            InstallResult.STARTED
        }.getOrDefault(InstallResult.NO_INSTALLER)
    }

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    suspend fun clearPendingDownload() = settings.clearPendingDownload()

    private fun updateDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "updates").apply { mkdirs() }
    }

    private fun apkName(version: String) = "trolley-$version.apk"

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
