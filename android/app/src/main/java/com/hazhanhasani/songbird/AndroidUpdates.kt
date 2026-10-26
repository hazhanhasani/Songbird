package com.hazhanhasani.songbird

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import android.app.Activity
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AndroidRelease(
    val version: String,
    val tag: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String
)

/**
 * Dedicated Android release channel on this fork.
 * A release must be tagged android-vX.Y.Z and contain a GitHub-computed SHA256 digest.
 */
object AndroidUpdates {
    private const val RELEASES_API =
        "https://api.github.com/repos/hazhanhasani/Songbird/releases?per_page=30"
    private val versionPattern = Regex("^android-v(\\d+)\\.(\\d+)\\.(\\d+)$")
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L

    fun compareVersions(a: String, b: String): Int {
        val av = a.split('.').map { it.toIntOrNull() ?: 0 }
        val bv = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val diff = (av.getOrElse(i) { 0 }).compareTo(bv.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    suspend fun findUpdate(currentVersion: String): AndroidRelease? = withContext(Dispatchers.IO) {
        val connection = openHttps(RELEASES_API, "application/vnd.github+json")
        try {
            if (connection.responseCode != 200) {
                throw IllegalStateException("GitHub release check failed (HTTP ${connection.responseCode})")
            }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            var newest: AndroidRelease? = null
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                val tag = release.optString("tag_name")
                val match = versionPattern.matchEntire(tag) ?: continue
                val version = match.groupValues.drop(1).joinToString(".")
                if (compareVersions(version, currentVersion) <= 0) continue
                val assets = release.optJSONArray("assets") ?: continue
                val apkName = "songbird-android-v$version.apk"
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name") != apkName) continue
                    val digest = asset.optString("digest").removePrefix("sha256:").lowercase(Locale.ROOT)
                    if (!digest.matches(Regex("[0-9a-f]{64}"))) continue
                    val url = asset.optString("browser_download_url")
                    val expected = "https://github.com/hazhanhasani/Songbird/releases/download/$tag/$apkName"
                    if (url != expected) continue
                    val candidate = AndroidRelease(version, tag, url, digest, release.optString("body"))
                    if (newest == null || compareVersions(candidate.version, newest.version) > 0) {
                        newest = candidate
                    }
                }
            }
            newest
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads to private cache and validates the immutable GitHub SHA256 before install. */
    suspend fun download(activity: Activity, release: AndroidRelease, progress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "songbird-${release.version}.apk")
            val temp = File(dir, "songbird-${release.version}.download")
            temp.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = openHttps(release.apkUrl, "application/octet-stream")
            try {
                if (connection.responseCode != 200) {
                    error("APK download failed (HTTP ${connection.responseCode})")
                }
                val expectedBytes = connection.contentLengthLong
                if (expectedBytes > MAX_APK_BYTES) error("APK exceeds size limit")
                var received = 0L
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            received += count
                            if (received > MAX_APK_BYTES) error("APK exceeds size limit")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            if (expectedBytes > 0) {
                                val pct = (received * 100 / expectedBytes).toInt().coerceIn(0, 100)
                                withContext(Dispatchers.Main) { progress(pct) }
                            }
                        }
                    }
                }
                if (received == 0L || (expectedBytes > 0 && received != expectedBytes)) {
                    error("Incomplete APK download")
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(release.sha256, ignoreCase = true)) error("APK SHA256 mismatch")
                apk.delete()
                check(temp.renameTo(apk)) { "Cannot save verified APK" }
                withContext(Dispatchers.Main) { progress(100) }
                apk
            } catch (error: Exception) {
                temp.delete()
                throw error
            } finally {
                connection.disconnect()
            }
        }

    fun mayInstall(activity: Activity): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"))
        )
    }

    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun openHttps(url: String, accept: String): HttpURLConnection {
        val uri = URL(url)
        require(uri.protocol == "https") { "HTTPS required" }
        require(
            uri.host == "api.github.com" ||
            (uri.host == "github.com" && uri.path.startsWith(
                "/hazhanhasani/Songbird/releases/download/android-v"))
        ) { "Untrusted download source" }
        return (uri.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 35_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Songbird-Android/${BuildConfig.VERSION_NAME}")
        }
    }
}
