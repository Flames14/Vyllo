package com.vyllo.music.update

import android.util.Log
import com.vyllo.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateResult {
    data class UpdateAvailable(val release: GithubRelease, val apkUrl: String) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

@Singleton
class AppUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val jsonFormatter = Json { ignoreUnknownKeys = true }
    private val REPO_OWNER = "Flames14"
    private val REPO_NAME = "Vyllo"
    private val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        Log.d("AppUpdate", "Checking for updates at: $LATEST_RELEASE_URL")
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext UpdateResult.Error("No releases found. Make sure your GitHub repository is public and has a release published.")
                }
                return@withContext UpdateResult.Error("GitHub API error: ${response.code}")
            }

            val bodyString = response.body?.string() ?: return@withContext UpdateResult.Error("Empty response body")
            val release = jsonFormatter.decodeFromString<GithubRelease>(bodyString)
            
            // Expected tag like "v1.1.0" or "1.1.0"
            val gitHubVersion = release.tagName.replace("v", "").replace("V", "")
            val currentVersion = BuildConfig.VERSION_NAME.replace("v", "").replace("V", "")
            
            if (isNewerVersion(currentVersion, gitHubVersion)) {
                val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.downloadUrl
                if (apkUrl != null) {
                    return@withContext UpdateResult.UpdateAvailable(release, apkUrl)
                } else {
                    return@withContext UpdateResult.Error("No APK found in the latest release.")
                }
            } else {
                return@withContext UpdateResult.NoUpdate
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateResult.Error(e.localizedMessage ?: "Unknown error occurred")
        }
    }
    
    private fun isNewerVersion(current: String, incoming: String): Boolean {
        // Simple version comparison e.g., "1.0.1" vs "1.1.0"
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val incomingParts = incoming.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, incomingParts.size)
        
        for (i in 0 until maxLength) {
            val c = currentParts.getOrElse(i) { 0 }
            val inc = incomingParts.getOrElse(i) { 0 }
            if (inc > c) return true
            if (inc < c) return false
        }
        return false // Exactly the same
    }
}
