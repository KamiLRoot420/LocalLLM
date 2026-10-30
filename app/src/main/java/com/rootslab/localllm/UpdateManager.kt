package com.rootslab.localllm

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Over-the-air updater backed by the GitHub Releases API.
 *
 * The APK is downloaded with the system DownloadManager (resumable, survives
 * process death) and the installation is triggered through a FileProvider URI.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val RELEASES_URL =
        "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Serializable
    private data class GitHubRelease(
        val tag_name: String = "",
        val name: String = "",
        val body: String = "",
        val html_url: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList()
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        val browser_download_url: String = "",
        val size: Long = 0L
    )

    data class ReleaseInfo(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkSizeBytes: Long,
        val releaseUrl: String
    )

    /**
     * Checks the latest GitHub release. Returns null when the installed version
     * is already up to date or when the check failed.
     */
    suspend fun checkForUpdate(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val release: GitHubRelease = client.get(RELEASES_URL) {
                header(HttpHeaders.UserAgent, "LocalLLM-AI-Server/$currentVersion")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }.body()

            if (release.draft || release.prerelease) return@runCatching null

            val latestVersion = release.tag_name.removePrefix("v")
            if (isSameOrNewer(latestVersion, currentVersion)) return@runCatching null

            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@runCatching null

            ReleaseInfo(
                version = latestVersion,
                notes = release.body,
                apkUrl = apk.browser_download_url,
                apkSizeBytes = apk.size,
                releaseUrl = release.html_url
            )
        }.onFailure {
            Log.w(TAG, "Update check failed: ${it.message}")
        }.getOrNull()
    }

    /**
     * Enqueues the APK download in the system DownloadManager.
     */
    fun downloadApk(context: Context, release: ReleaseInfo): Long {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("LocalLLM AI Server ${release.version}")
            .setDescription("Downloading update...")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "LocalLLM-${release.version}.apk"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        return manager.enqueue(request)
    }

    /**
     * Builds the installation intent for a downloaded APK.
     */
    fun installIntent(context: Context, apkFile: File): Intent? {
        if (!apkFile.exists()) return null
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Opens the system download notification list so the user can tap the APK. */
    fun openDownloads(context: Context) {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun isSameOrNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }
        if (candidateParts.isEmpty() || currentParts.isEmpty()) return candidate == current

        for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val a = candidateParts.getOrElse(index) { 0 }
            val b = currentParts.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return true
    }
}
