package com.vyllo.music.recognition.data.remote

import com.vyllo.music.recognition.data.model.ShazamRequestJson
import com.vyllo.music.recognition.data.model.ShazamResponseJson
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ShazamApi @Inject constructor(
    private val client: OkHttpClient
) {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val userAgents = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/UD1A.230805.019)",
        "Dalvik/2.1.0 (Linux; U; Android 13; SM-G998B Build/TP1A.220624.014)",
        "Dalvik/2.1.0 (Linux; U; Android 12; M2101K6G Build/SKQ1.210908.001)"
    )

    private val timezones = listOf(
        "Europe/Paris", "Europe/London", "America/New_York",
        "America/Los_Angeles", "Asia/Tokyo", "Asia/Dubai"
    )

    suspend fun recognize(signature: String, sampleDurationMs: Long): Result<ShazamResponseJson> {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid1 = UUID.randomUUID().toString().uppercase()
        val uuid2 = UUID.randomUUID().toString()

        val requestBody = ShazamRequestJson(
            geolocation = ShazamRequestJson.Geolocation(
                altitude = Random.nextDouble() * 400 + 100,
                latitude = Random.nextDouble() * 180 - 90,
                longitude = Random.nextDouble() * 360 - 180
            ),
            signature = ShazamRequestJson.Signature(
                samplems = sampleDurationMs,
                timestamp = timestamp,
                uri = signature
            ),
            timestamp = timestamp,
            timezone = timezones.random()
        )

        val jsonString = json.encodeToString(requestBody)
        Log.d("ShazamApi", "Request JSON: $jsonString")
        val body = jsonString.toRequestBody("application/json".toMediaType())

        val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("User-Agent", "Android")
            .addHeader("X-Shazam-Platform", "android")
            .addHeader("X-Shazam-AppVersion", "14.1.0")
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e("ShazamApi", "Shazam API error: ${response.code}, body: $errorBody")
                    return Result.failure(Exception("Shazam API error: ${response.code}"))
                }
                val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
                Log.d("ShazamApi", "Response JSON: $responseBody")
                val shazamResponse = json.decodeFromString<ShazamResponseJson>(responseBody)
                Result.success(shazamResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
