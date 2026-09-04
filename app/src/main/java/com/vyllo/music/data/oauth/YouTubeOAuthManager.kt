package com.vyllo.music.data.oauth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vyllo.music.core.security.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeOAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "YouTubeOAuthManager"
        private const val PREFS_NAME = "vyllo_youtube_oauth_prefs"
        
        // Default client ID for Vyllo Music Android App
        // Users can also override this in settings if they have their own Google Cloud Client ID
        const val DEFAULT_CLIENT_ID = "104192837492-vylloytmandroidclient.apps.googleusercontent.com"
        const val REDIRECT_URI = "com.vyllo.music://oauth2redirect"
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val SCOPE_YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_CODE_VERIFIER = "code_verifier"
        private const val KEY_CUSTOM_CLIENT_ID = "custom_client_id"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to initialize EncryptedSharedPreferences, using fallback", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val _isAuthorized = MutableStateFlow(hasValidTokenSync())
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _userEmail = MutableStateFlow(prefs.getString(KEY_USER_EMAIL, null))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _hasConfiguredClientId = MutableStateFlow(!getClientId().isNullOrBlank())
    val hasConfiguredClientId: StateFlow<Boolean> = _hasConfiguredClientId.asStateFlow()

    fun getClientId(): String? {
        return prefs.getString(KEY_CUSTOM_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun setCustomClientId(clientId: String?) {
        val cleaned = clientId?.trim()?.takeIf { it.isNotBlank() }
        if (cleaned != null) {
            prefs.edit().putString(KEY_CUSTOM_CLIENT_ID, cleaned).apply()
            _hasConfiguredClientId.value = true
        } else {
            prefs.edit().remove(KEY_CUSTOM_CLIENT_ID).apply()
            _hasConfiguredClientId.value = false
        }
    }

    private fun hasValidTokenSync(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        return !token.isNullOrBlank() || !refreshToken.isNullOrBlank()
    }

    /**
     * Generates a secure PKCE Code Verifier & Challenge and builds the Authorization URI.
     */
    fun createAuthorizationUri(): Uri? {
        val clientId = getClientId() ?: return null
        val codeVerifier = generateCodeVerifier()
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()

        val codeChallenge = generateCodeChallenge(codeVerifier)

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPE_YOUTUBE_READONLY)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
    }

    /**
     * Handles redirect from Google OAuth consent screen, exchanges code for access & refresh tokens.
     */
    suspend fun handleAuthorizationResponse(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            if (error != null) {
                return@withContext Result.failure(Exception("Google Sign-In failed: $error"))
            }

            if (code.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No authorization code received from Google"))
            }

            val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
                ?: return@withContext Result.failure(Exception("PKCE Code verifier not found"))

            val clientId = getClientId() ?: return@withContext Result.failure(Exception("Google Client ID not configured"))
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("redirect_uri", REDIRECT_URI)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                SecureLogger.e(TAG, "Token exchange failed: code=${response.code}, body=$responseBody")
                return@withContext Result.failure(Exception("Failed to exchange token with Google (HTTP ${response.code})"))
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", prefs.getString(KEY_REFRESH_TOKEN, ""))
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .remove(KEY_CODE_VERIFIER)
                .apply()

            _isAuthorized.value = true
            SecureLogger.d(TAG, "Google OAuth successfully authorized")

            return@withContext Result.success(accessToken)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error handling authorization response", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Retrieves a valid access token, automatically refreshing it if expired.
     */
    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

        // If access token is valid for at least another 60 seconds, use it
        if (!accessToken.isNullOrBlank() && System.currentTimeMillis() < (expiresAt - 60_000L)) {
            return@withContext accessToken
        }

        // If expired but refresh token exists, refresh it
        if (!refreshToken.isNullOrBlank()) {
            return@withContext refreshAccessToken(refreshToken)
        }

        return@withContext accessToken
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val clientId = getClientId() ?: return@withContext null
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val json = JSONObject(responseBody)
                val newAccessToken = json.getString("access_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, newAccessToken)
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .apply()

                _isAuthorized.value = true
                return@withContext newAccessToken
            } else {
                SecureLogger.w(TAG, "Token refresh failed: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error refreshing token", e)
        }
        return@withContext null
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_EMAIL)
            .apply()
        _isAuthorized.value = false
        _userEmail.value = null
    }

    // PKCE Helper Functions
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val code = ByteArray(32)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
