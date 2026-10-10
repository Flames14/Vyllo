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
            // FAIL CLOSED: plaintext storage of "secure" data is worse than none.
            Log.e(TAG, "EncryptedSharedPreferences unavailable — using process-local memory only")
            InMemorySharedPreferences()
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
     * Saves search query to encrypted DataStore.
     * Values are AES-256-GCM encrypted before hitting disk; only the
     * timestamped key names remain visible in the protobuf file.
     */
    suspend fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        try {
            val encrypted = encryptSearchQuery(query) ?: return
            context.dataStore.edit { prefs ->
                val id = query.toSha256().take(8)
                val timestamp = System.currentTimeMillis()
                // Remove existing entry for the same query hash to avoid duplicates
                val existingKeys = prefs.asMap().keys.filter { it.name.endsWith("_$id") }
                existingKeys.forEach { prefs.remove(it) }
                prefs[stringPreferencesKey("search_${timestamp}_$id")] = encrypted
            }
            Log.sensitive(TAG, "Saved search query")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search query: ${e.message}")
        }
    }

    /**
     * Gets search history as a Flow (most recent first). Entries written by
     * older plaintext versions are dropped silently.
     */
    fun getSearchHistory(): Flow<List<String>> {
        return context.dataStore.data.map { prefs ->
            prefs.asMap()
                .filterKeys { it.name.startsWith("search_") }
                .entries
                .sortedByDescending { it.key.name } // Most recent timestamp first
                .mapNotNull { decryptSearchQuery(it.value.toString()) }
                .distinct()
        }
    }

    private fun getOrCreateSearchKey(): javax.crypto.SecretKey? {
        return try {
            val stored = encryptedPrefs.getString("search_history_key", null)
            if (stored != null) {
                javax.crypto.spec.SecretKeySpec(
                    android.util.Base64.decode(stored, android.util.Base64.NO_WRAP),
                    "AES"
                )
            } else {
                val random = java.security.SecureRandom()
                val keyBytes = ByteArray(32)
                random.nextBytes(keyBytes)
                encryptedPrefs.edit()
                    .putString(
                        "search_history_key",
                        android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
                    )
                    .apply()
                javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search-history key unavailable", e)
            null
        }
    }

    private fun encryptSearchQuery(plain: String): String? {
        return try {
            val key = getOrCreateSearchKey() ?: return null
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE, key,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val b64 = android.util.Base64.NO_WRAP
            android.util.Base64.encodeToString(iv, b64) + ":" +
                android.util.Base64.encodeToString(cipherText, b64)
        } catch (e: Exception) {
            Log.e(TAG, "Search-history encrypt failed", e)
            null
        }
    }

    private fun decryptSearchQuery(stored: String): String? {
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return null // legacy plaintext entry: drop
            val key = getOrCreateSearchKey() ?: return null
            val b64 = android.util.Base64.NO_WRAP
            val iv = android.util.Base64.decode(parts[0], b64)
            val cipherText = android.util.Base64.decode(parts[1], b64)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE, key,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
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
