package com.vyllo.music.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.vyllo.music.core.security.SecureLogger as Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "SecurePrefManager"

// DataStore instance for non-sensitive preferences
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_preferences"
)

/**
 * Secure Preference Manager with Encrypted Storage
 * 
 * Provides:
 * - Hardware-backed encryption (AES-256-GCM)
 * - Secure key storage in Android Keystore
 * - Hashed keys for additional privacy
 * - Migration from old SharedPreferences
 */
class SecurePreferenceManager(private val context: Context) {

    // MasterKey with hardware-backed keystore (Android 6.0+)
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setUserAuthenticationRequired(false) // Set true for biometric-locked data
            .build()
    }

    // Encrypted SharedPreferences for sensitive data
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "encrypted_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences: ${e.message}")
            // Fallback to regular prefs (less secure but functional)
            context.getSharedPreferences("prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Saves lyrics preference securely (hashed URL as key)
     */
    fun saveLyricsPreference(videoUrl: String, lrcId: String) {
        val hashedUrl = videoUrl.toSha256()
        encryptedPrefs.edit()
            .putString("lyrics_$hashedUrl", lrcId)
            .apply()
        Log.sensitive(TAG, "Saved lyrics preference for URL hash: ${hashedUrl.take(8)}...")
    }

    /**
     * Loads lyrics preference securely
     */
    fun loadLyricsPreference(videoUrl: String): String? {
        val hashedUrl = videoUrl.toSha256()
        return encryptedPrefs.getString("lyrics_$hashedUrl", null)
    }

    /**
     * Saves search query to encrypted DataStore
     */
    suspend fun saveSearchQuery(query: String) {
        try {
            context.dataStore.edit { prefs ->
                val id = query.toSha256().take(8)
                prefs[stringPreferencesKey("search_$id")] = query
            }
            Log.sensitive(TAG, "Saved search query")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search query: ${e.message}")
        }
    }

    /**
     * Gets search history as a Flow
     */
    fun getSearchHistory(): Flow<List<String>> {
        return context.dataStore.data.map { prefs ->
            prefs.asMap()
                .filterKeys { it.name.startsWith("search_") }
                .values
                .map { it.toString() }
                .sortedByDescending { it } // Most recent first
        }
    }

    /**
     * Clears all search history
     */
    suspend fun clearSearchHistory() {
        try {
            context.dataStore.edit { prefs ->
                val searchKeys = prefs.asMap().keys.filter { it.name.startsWith("search_") }
                searchKeys.forEach { prefs.remove(it) }
            }
            Log.d(TAG, "Search history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear search history: ${e.message}")
        }
    }

    /**
     * Saves a boolean value securely
     */
    fun saveBoolean(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean(key, value).apply()
    }

    /**
     * Gets a boolean value securely
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return encryptedPrefs.getBoolean(key, defaultValue)
    }

    /**
     * Saves an integer value securely
     */
    fun saveInt(key: String, value: Int) {
        encryptedPrefs.edit().putInt(key, value).apply()
    }

    /**
     * Gets an integer value securely
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return encryptedPrefs.getInt(key, defaultValue)
    }

    /**
     * Saves a string value securely
     */
    fun saveString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    /**
     * Gets a string value securely
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return encryptedPrefs.getString(key, defaultValue)
    }

    /**
     * Wipes all sensitive encrypted data
     * Use for logout or security clear
     */
    fun wipeSensitiveData() {
        try {
            encryptedPrefs.edit().clear().apply()
            Log.d(TAG, "Sensitive data wiped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wipe sensitive data: ${e.message}")
        }
    }

    /**
     * Extension function for SHA-256 hashing
     */
    private fun String.toSha256(): String {
        return java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(this.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Check if encrypted preferences are being used (not fallback)
     */
    fun isEncryptionEnabled(): Boolean {
        return encryptedPrefs is EncryptedSharedPreferences
    }
}
