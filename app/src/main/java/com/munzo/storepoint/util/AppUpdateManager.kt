package com.munzo.storepoint.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(
    val latestVersionTag: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewer: Boolean,
    val publishedAt: String,
    val assetSize: Long,
    val sha256Checksum: String = ""
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

object AppUpdateManager {

    // Default GitHub repository for checking releases.
    // Users or admins can override this in StorePoint Settings if they host on their own GitHub fork.
    var githubRepoOwner: String = "uznom"
    var githubRepoName: String = "StorePoint"

    // SECURITY (audit M3): kept private so the token can never be read back into logs,
    // crash reports, or UI state. It lives only in memory (never persisted to prefs/disk)
    // and is attached exclusively to HTTPS request headers inside this object.
    private var githubToken: String = ""

    /** Sets an optional GitHub token for private-repository update checks. Never logged or persisted. */
    fun setGithubToken(token: String) {
        githubToken = token.trim()
    }

    private fun createConnection(urlStr: String, accept: String = "application/vnd.github.v3+json"): HttpURLConnection {
        return (URL(urlStr).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "StorePoint-POS-App")
            if (githubToken.isNotBlank() && urlStr.contains("api.github.com")) {
                setRequestProperty("Authorization", "Bearer $githubToken")
            }
            connectTimeout = 12000
            readTimeout = 12000
        }
    }

    /**
     * Checks the GitHub Releases API for the latest published release.
     * Supports both public and private GitHub repositories with automated bearer token auth,
     * and falls back to listing all releases if the latest tag alias returns HTTP 404.
     */
    suspend fun checkForUpdates(
        currentVersionName: String,
        owner: String = githubRepoOwner,
        repo: String = githubRepoName
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            var connection = createConnection(apiUrl)

            var responseCode = connection.responseCode
            var responseBody: String

            // Fallback: If releases/latest returns 404, check releases list (handles pre-releases or newly published releases)
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                connection.disconnect()
                val listUrl = "https://api.github.com/repos/$owner/$repo/releases"
                connection = createConnection(listUrl)
                responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val listBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(listBody)
                    if (array.length() > 0) {
                        responseBody = array.getJSONObject(0).toString()
                    } else {
                        return@withContext Result.failure(
                            Exception("No releases found on GitHub for repository $owner/$repo.")
                        )
                    }
                } else {
                    return@withContext Result.failure(
                        Exception("GitHub repository or release not found (HTTP $responseCode). If repository is private, ensure access token is valid.")
                    )
                }
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("Failed to check for updates. GitHub returned HTTP $responseCode")
                )
            } else {
                responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "")
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "No release notes provided.")
            val publishedAt = json.optString("published_at", "")

            var downloadUrl = ""
            var apkAssetName = ""
            var assetSize: Long = 0
            var checksumUrl = ""
            var sumsUrl = ""

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val browserUrl = asset.optString("browser_download_url", "")
                    val apiUrl = asset.optString("url", "")
                    // For private repositories, asset API URL with octet-stream header must be used to authenticate
                    val effectiveUrl = if (githubToken.isNotBlank() && apiUrl.isNotBlank()) apiUrl else browserUrl

                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val candidateIsSigned = name.contains("signed-release", ignoreCase = true)
                        val currentIsSigned = apkAssetName.contains("signed-release", ignoreCase = true)
                        if (downloadUrl.isEmpty() || (currentIsSigned && !candidateIsSigned)) {
                            downloadUrl = effectiveUrl
                            apkAssetName = name
                            assetSize = asset.optLong("size", 0)
                        }
                    } else if (name.endsWith(".sha256", ignoreCase = true)) {
                        checksumUrl = effectiveUrl
                    } else if (name.equals("SHA256SUMS.txt", ignoreCase = true)) {
                        sumsUrl = effectiveUrl
                    }
                }
            }

            if (downloadUrl.isEmpty()) {
                downloadUrl = json.optString("html_url", "")
            }

            val isNewer = isVersionNewer(latestTag = tagName, currentVersion = currentVersionName)

            // Fetch the SHA-256 digest so the APK payload can be verified before install.
            val sha256Checksum = when {
                checksumUrl.isNotEmpty() -> fetchSha256(checksumUrl)
                sumsUrl.isNotEmpty() && apkAssetName.isNotEmpty() ->
                    fetchChecksumFromSums(sumsUrl, apkAssetName)
                else -> ""
            }

            Result.success(
                AppUpdateInfo(
                    latestVersionTag = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    isNewer = isNewer,
                    publishedAt = publishedAt,
                    assetSize = assetSize,
                    sha256Checksum = sha256Checksum
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * SemVer-aware comparison between remote version tag (e.g., "v2.5.0") and current local version (e.g., "2.4.0").
     */
    fun isVersionNewer(latestTag: String, currentVersion: String): Boolean {
        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        if (cleanLatest.isEmpty()) return false
        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) return false

        val latestParts = cleanLatest.split('.').mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val currentParts = cleanCurrent.split('.').mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Downloads the APK file to the app's external or internal cache directory,
     * emitting download progress updates. Correctly handles GitHub API asset redirects.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        fileName: String = "StorePoint-update.apk",
        expectedSha256: String = "",
        onProgress: (UpdateDownloadState) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            var targetUrl = downloadUrl
            var connection: HttpURLConnection
            var redirects = 0

            // Follow redirects if GitHub redirects to AWS S3 / storage
            while (true) {
                connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "StorePoint-POS-App")
                    if (targetUrl.contains("api.github.com")) {
                        setRequestProperty("Accept", "application/octet-stream")
                        if (githubToken.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $githubToken")
                        }
                    }
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null && redirects < 5) {
                        connection.disconnect()
                        targetUrl = newUrl
                        redirects++
                        continue
                    }
                }
                break
            }

            val fileLength = connection.contentLengthLong
            val targetDir = context.cacheDir
            val outputFile = File(targetDir, fileName)
            if (outputFile.exists()) {
                outputFile.delete()
            }

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead: Long = 0
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (fileLength > 0) totalRead.toFloat() / fileLength else -1f
                        withContext(Dispatchers.Main) {
                            onProgress(
                                UpdateDownloadState.Downloading(
                                    progress = progress,
                                    downloadedBytes = totalRead,
                                    totalBytes = fileLength
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            // Verify the SHA-256 checksum or package validity of the downloaded package (fail-closed).
            if (expectedSha256.isNotBlank()) {
                val actualSha256 = sha256HexOfFile(outputFile)
                if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                    outputFile.delete()
                    throw Exception("Checksum verification failed. Update package rejected (expected $expectedSha256, got $actualSha256).")
                }
            } else {
                // Fail-safe validation: Ensure the downloaded file is a valid Android package with matching package name
                val pkgInfo = context.packageManager.getPackageArchiveInfo(outputFile.absolutePath, 0)
                if (pkgInfo == null || pkgInfo.packageName != context.packageName) {
                    outputFile.delete()
                    throw Exception("Downloaded package validation failed: invalid APK or package name mismatch.")
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(UpdateDownloadState.ReadyToInstall(outputFile))
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onProgress(UpdateDownloadState.Error(e.localizedMessage ?: "Download failed"))
            }
            Result.failure(e)
        }
    }

    /**
     * Computes the lowercase SHA-256 hex digest of a file.
     */
    private fun sha256HexOfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Fetches a release-sidecar SHA-256 file ("<hex>  <filename>\n") and returns the hex digest,
     * or "" when the sidecar is missing/unreadable.
     */
    private suspend fun fetchSha256(checksumUrl: String): String = withContext(Dispatchers.IO) {
        try {
            var targetUrl = checksumUrl
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "StorePoint-POS-App")
                    if (targetUrl.contains("api.github.com")) {
                        setRequestProperty("Accept", "application/octet-stream")
                        if (githubToken.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $githubToken")
                        }
                    }
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null && redirects < 5) {
                        connection.disconnect()
                        targetUrl = newUrl
                        redirects++
                        continue
                    }
                }
                break
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext ""
            val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            body.split(' ').map { it.trim() }.filter { it.isNotEmpty() }.firstOrNull() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Fetches a combined SHA256SUMS.txt manifest ("<hex>  <filename>" per line) and returns the
     * hex digest of the line matching [fileName], or "" when the manifest/entry is missing.
     */
    private suspend fun fetchChecksumFromSums(sumsUrl: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            try {
                var targetUrl = sumsUrl
                var connection: HttpURLConnection
                var redirects = 0
                while (true) {
                    connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "StorePoint-POS-App")
                        if (targetUrl.contains("api.github.com")) {
                            setRequestProperty("Accept", "application/octet-stream")
                            if (githubToken.isNotBlank()) {
                                setRequestProperty("Authorization", "Bearer $githubToken")
                            }
                        }
                        connectTimeout = 10000
                        readTimeout = 10000
                    }
                    val code = connection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val newUrl = connection.getHeaderField("Location")
                        if (newUrl != null && redirects < 5) {
                            connection.disconnect()
                            targetUrl = newUrl
                            redirects++
                            continue
                        }
                    }
                    break
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext ""
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                body.lineSequence()
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size >= 2 && it[1].equals(fileName, ignoreCase = true) }
                    ?.firstOrNull() ?: ""
            } catch (e: Exception) {
                ""
            }
        }

    /**
     * Prompts the Android OS package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ check for unknown app sources permission if required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
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
