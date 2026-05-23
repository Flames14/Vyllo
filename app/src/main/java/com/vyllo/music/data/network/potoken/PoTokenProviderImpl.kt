package com.vyllo.music.data.network.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

object PoTokenProviderImpl : PoTokenProvider {
    private val TAG = PoTokenProviderImpl::class.simpleName
    private lateinit var appContext: Context
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private object WebPoTokenGenLock
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenGenerator? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun isWebViewSupported(): Boolean {
        if (!::appContext.isInitialized) return false
        return try {
            appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WEBVIEW)
        } catch (e: Exception) {
            false
        }
    }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        if (!isWebViewSupported() || webViewBadImpl) {
            return null
        }

        try {
            return getWebClientPoToken(videoId = videoId, forceRecreate = false)
        } catch (e: RuntimeException) {
            // Unpack wrapped exceptions
            when (val cause = e.cause) {
                is BadWebViewException -> {
                    Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    return null
                }
                null -> throw e
                else -> throw cause // includes PoTokenException
            }
        }
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator]
     */
    private fun getWebClientPoToken(videoId: String, forceRecreate: Boolean): PoTokenResult {
        data class Quadruple<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

        val (poTokenGenerator, visitorData, streamingPot, hasBeenRecreated) =
            synchronized(WebPoTokenGenLock) {
                val shouldRecreate = webPoTokenGenerator == null || forceRecreate ||
                    webPoTokenGenerator!!.isExpired()

                if (shouldRecreate) {
                    val innertubeClientRequestInfo = InnertubeClientRequestInfo.ofWebClient()
                    innertubeClientRequestInfo.clientInfo.clientVersion =
                        YoutubeParsingHelper.getClientVersion()

                    webPoTokenVisitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
                        innertubeClientRequestInfo,
                        NewPipe.getPreferredLocalization(),
                        NewPipe.getPreferredContentCountry(),
                        YoutubeParsingHelper.getYouTubeHeaders(),
                        YoutubeParsingHelper.YOUTUBEI_V1_URL,
                        null,
                        false
                    )
                    // close the current webPoTokenGenerator on the main thread
                    webPoTokenGenerator?.let { generator ->
                        Handler(Looper.getMainLooper()).post { generator.close() }
                    }

                    // create a new webPoTokenGenerator using runBlocking
                    webPoTokenGenerator = runBlocking {
                        PoTokenWebView.newPoTokenGenerator(appContext)
                    }

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    webPoTokenStreamingPot = runBlocking {
                        webPoTokenGenerator!!.generatePoToken(webPoTokenVisitorData!!)
                    }
                }

                return@synchronized Quadruple(
                    webPoTokenGenerator!!,
                    webPoTokenVisitorData!!,
                    webPoTokenStreamingPot!!,
                    shouldRecreate
                )
            }

        val playerPot = try {
            runBlocking {
                poTokenGenerator.generatePoToken(videoId)
            }
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch
                Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
                return getWebClientPoToken(videoId = videoId, forceRecreate = true)
            }
        }

        if (com.vyllo.music.BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "poToken for $videoId: playerPot=$playerPot, " +
                    "streamingPot=$streamingPot, visitor_data=$visitorData"
            )
        }

        return PoTokenResult(visitorData, playerPot, streamingPot)
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? {
        return getWebClientPoToken(videoId)
    }

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? {
        // Return null so the extractor uses getAndroidReelPlayerResponse()
        // which does not require a PoToken. Using a WEB-derived PoToken here
        // causes 403 errors because YouTube validates client-PoToken identity match.
        return null
    }

    override fun getIosClientPoToken(videoId: String): PoTokenResult? {
        // Return null so the extractor uses the plain iOS player endpoint.
        // Same rationale as getAndroidClientPoToken.
        return null
    }
}
