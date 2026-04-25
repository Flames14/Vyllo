package com.vyllo.music.recognition.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.vyllo.music.recognition.data.AudioResampler
import com.vyllo.music.recognition.data.ShazamSignatureGenerator
import com.vyllo.music.recognition.data.remote.ShazamApi
import com.vyllo.music.recognition.domain.model.RecognitionResult
import com.vyllo.music.recognition.domain.repository.ShazamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShazamRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ShazamApi
) : ShazamRepository {

    private val TAG = "ShazamRepo"

    companion object {
        private const val RECORDING_SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RECORDING_DURATION_MS = 12000L
        private const val TARGET_SAMPLE_RATE = 16000
    }

    @SuppressLint("MissingPermission")
    override suspend fun recognize(): Result<RecognitionResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Record Audio
            Log.d(TAG, "Starting audio recording...")
            val rawAudio = recordAudio() ?: return@withContext Result.failure(Exception("Failed to record audio"))
            Log.d(TAG, "Recorded ${rawAudio.size} bytes")
            
            val isSilence = rawAudio.all { it == 0.toByte() }
            if (isSilence) {
                Log.w(TAG, "WARNING: Recorded audio is pure silence!")
            }

            // 2. Resample to 16kHz
            val decodedAudio = AudioResampler.DecodedAudio(
                data = rawAudio,
                channelCount = 1,
                sampleRate = RECORDING_SAMPLE_RATE,
                pcmEncoding = AUDIO_FORMAT
            )
            val resampled = AudioResampler.resample(decodedAudio, TARGET_SAMPLE_RATE).getOrThrow()
            Log.d(TAG, "Resampled to ${resampled.data.size} bytes at 16kHz")

            // 3. Generate Signature
            val signature = ShazamSignatureGenerator.fromI16(resampled.data)
            val sampleDurationMs = (resampled.data.size / 2) * 1000L / TARGET_SAMPLE_RATE

            // 4. Hit Shazam API
            Log.d(TAG, "Generated signature (length: ${signature.length}). Sending to API...")
            val apiResponse = api.recognize(signature, sampleDurationMs).getOrThrow()
            Log.d(TAG, "API Response: $apiResponse")
            val domainResult = apiResponse.toDomain() ?: return@withContext Result.failure(Exception("No match found"))

            Log.d(TAG, "Match found: ${domainResult.title} by ${domainResult.artist}")
            Result.success(domainResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(): ByteArray? {
        val bufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            return null
        }

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()
        var maxAmplitude = 0

        try {
            audioRecord.startRecording()
            while (System.currentTimeMillis() - startTime < RECORDING_DURATION_MS) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                    
                    // Check max amplitude in this buffer
                    for (i in 0 until bytesRead step 2) {
                        if (i + 1 < bytesRead) {
                            val sample = ((buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xFF).toInt()).toShort()
                            val absSample = if (sample < 0) -sample.toInt() else sample.toInt()
                            if (absSample > maxAmplitude) maxAmplitude = absSample
                        }
                    }
                }
                delay(10) // Small delay to avoid tight loop
            }
            audioRecord.stop()
            Log.d(TAG, "Recording finished. Max amplitude: $maxAmplitude")
        } catch (e: Exception) {
            return null
        } finally {
            audioRecord.release()
        }

        return outputStream.toByteArray()
    }
}
