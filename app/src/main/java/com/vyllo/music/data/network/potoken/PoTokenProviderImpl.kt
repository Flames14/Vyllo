package com.vyllo.music.data.network.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import kotlin.coroutines.resume

object PoTokenProviderImpl : PoTokenProvider {
    private val TAG = PoTokenProviderImpl::class.simpleName ?: "PoToken"
    private lateinit var appContext: Context
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    /** Identifier used only to mint (and immediately discard) a warm-up token. */
    private const val WARM_UP_IDENTIFIER = "vyllo:warmup"

    /** Clears the launch animation before the warm-up is even considered. */
    private const val WARM_UP_DELAY_MS = 900L

    /**
     * Upper bound on waiting for an idle main queue. If the user is scrolling
     * continuously for this long we give up and run anyway — the warm-up must
     * always happen eventually, even if it costs one frame.
     */
    private const val MAIN_IDLE_TIMEOUT_MS = 3_000L

    /** Background worker for [warmUpAsync]; keeps the launch frames clean. */
    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var warmUpRequested = false

    private object WebPoTokenGenLock
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenGenerator? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Pre-warms the WebView/BotGuard generator on a background worker.
     *
     * Building the generator is the expensive part of the first play of a
     * session (spawn WebView, load the BotGuard bundle, Create + GenerateIT,
     * then mint the streaming token) and it used to happen inline, inside the
     * synchronous [getWebClientPoToken] contract, while the user waited. Doing
     * it once at launch removes that cold cost from the first tap.
     *
     * Idempotent and safe from any thread. Failures are only logged: the
     * on-demand path below stays the authoritative one.
     */
    fun warmUpAsync() {
        if (!isWebViewSupported() || webViewBadImpl) return
        synchronized(WebPoTokenGenLock) {
            if (warmUpRequested) return
            warmUpRequested = true
        }
        warmUpScope.launch {
            try {
                // Never compete with the launch frames…
                delay(WARM_UP_DELAY_MS)
                // …and never land inside a scroll either: the generator builds
                // its WebView on the main thread, so let the queue go quiet
                // first. This is what keeps "warm up in the background" from
                // simply moving a frame hitch from the first play to one second
                // after launch.
                awaitMainThreadIdle()
                if (!::appContext.isInitialized || webViewBadImpl) return@launch
                val startedAt = android.os.SystemClock.elapsedRealtime()
                getWebClientPoToken(videoId = WARM_UP_IDENTIFIER, forceRecreate = false)
                SecureLogger.d(
                    TAG,
                    "poToken warm-up ready in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                SecureLogger.w(TAG, "poToken warm-up failed; will retry on demand", t)
            }
        }
    }

    /**
     * Suspends until the main message queue has nothing left to process.
     *
     * The generator's WebView can only be constructed on a Looper thread, and
     * the app builds it on the main one — so a warm-up that fires while a fling
     * is in flight would drop the very frames this optimisation exists to
     * protect. An IdleHandler is exactly that signal.
     *
     * Bounded by [timeoutMs]: a queue that never goes idle still gets the
     * warm-up (a cold first play beats an eternal wait for idle).
     */
    private suspend fun awaitMainThreadIdle(timeoutMs: Long = MAIN_IDLE_TIMEOUT_MS) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { continuation ->
                Handler(Looper.getMainLooper()).post {
                    // Both Looper.myQueue() and addIdleHandler() must run on
                    // the main thread, so registration happens inside the post.
                    val queue: MessageQueue = Looper.myQueue()
                    val idleHandler = object : MessageQueue.IdleHandler {
                        override fun queueIdle(): Boolean {
                            queue.removeIdleHandler(this)
                            if (continuation.isActive) continuation.resume(Unit)
                            return false // one-shot: MessageQueue removes us anyway
                        }
                    }
                    if (continuation.isActive) {
                        queue.addIdleHandler(idleHandler)
                        continuation.invokeOnCancellation { queue.removeIdleHandler(idleHandler) }
                    }
                }
            }
        }
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

        // NewPipe's PoTokenProvider API is synchronous, so runBlocking below is
        // forced by the extractor contract (not a coroutine design choice).
        // Never block the main thread: fail gracefully instead of ANR-ing.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            SecureLogger.w(TAG, "PoToken requested on main thread; returning null to avoid ANR")
            return null
        }

        try {
            return getWebClientPoToken(videoId = videoId, forceRecreate = false)
        } catch (e: Exception) {
            // BotGuard plumbing throws plain Exceptions — BadWebViewException
            // (directly or as cause) and PoTokenException. A RuntimeException
            // -only catch let both escape and crash the extractor instead of
            // degrading to tokenless playback.
            when {
                e is BadWebViewException || e.cause is BadWebViewException -> {
                    SecureLogger.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    return null
                }
                e is PoTokenException || e.cause is PoTokenException -> {
                    SecureLogger.e(TAG, "Could not obtain poToken", e)
                    return null
                }
                else -> throw e
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
                val existing = webPoTokenGenerator
                val shouldRecreate = existing == null || forceRecreate || existing.isExpired()

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
                    existing?.let { generator ->
                        Handler(Looper.getMainLooper()).post { generator.close() }
                    }

                    // NewPipe's PoTokenProvider API is synchronous; the underlying
                    // suspend funs hop to Dispatchers.Main themselves, so this only
                    // blocks the calling worker thread (never the main thread).
                    webPoTokenGenerator = runBlocking {
                        PoTokenWebView.newPoTokenGenerator(appContext)
                    }

                    val visitor = webPoTokenVisitorData
                        ?: throw PoTokenException("visitorData missing after recreate")
                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    val newGen = webPoTokenGenerator
                        ?: throw PoTokenException("PoToken generator missing after recreate")
                    val streamingToken = runBlocking {
                        newGen.generatePoToken(visitor)
                    }
                    webPoTokenStreamingPot = streamingToken
                }

                val gen = webPoTokenGenerator
                    ?: throw PoTokenException("PoToken generator unavailable")
                val vd = webPoTokenVisitorData
                    ?: throw PoTokenException("visitorData unavailable")
                val sp = webPoTokenStreamingPot
                    ?: throw PoTokenException("streaming PoToken unavailable")

                return@synchronized Quadruple(gen, vd, sp, shouldRecreate)
            }

        val playerPot = try {
            runBlocking {
                poTokenGenerator.generatePoToken(videoId)
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch
                SecureLogger.e(TAG, "Failed to obtain poToken, retrying", throwable)
                return getWebClientPoToken(videoId = videoId, forceRecreate = true)
            }
        }

        if (com.vyllo.music.BuildConfig.DEBUG) {
            SecureLogger.d(
                TAG,
                "poToken obtained for video ${videoId.take(11)}"
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
