package com.vyllo.music.data.network.potoken

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.*
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PoTokenWebView private constructor(
    context: Context,
    private val initCallback: (Result<PoTokenGenerator>) -> Unit
) : PoTokenGenerator {
    private val webView = WebView(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val poTokenContinuations = mutableListOf<Pair<String, CancellableContinuation<String>>>()
    private lateinit var expirationInstant: Instant

    init {
        val webViewSettings = webView.settings
        @Suppress("SetJavaScriptEnabled")
        webViewSettings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webViewSettings.safeBrowsingEnabled = true
        }
        webViewSettings.allowFileAccess = false
        webViewSettings.allowContentAccess = false
        webViewSettings.allowFileAccessFromFileURLs = false
        webViewSettings.allowUniversalAccessFromFileURLs = false
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // The WebView does not need internet access

        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    val fmt = "\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})"
                    val exception = BadWebViewException(fmt)
                    SecureLogger.e(TAG, "BotGuard WebView initialization failed")

                    onInitializationErrorCloseAndCancel(exception)
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    private fun loadHtmlAndObtainBotguard(context: Context) {
        SecureLogger.d(TAG) { "Loading BotGuard WebView" }

        scope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                }
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html.replaceFirst(
                        "</script>",
                        // Calls downloadAndRunBotguard() when the page has finished loading
                        "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
                    ),
                    "text/html",
                    "utf-8",
                    null
                )
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(e)
            }
        }
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        SecureLogger.d(TAG) { "Starting BotGuard challenge" }

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]"
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            // Never interpolate raw server text into JS: a MITM'd challenge
            // could inject script into the privileged bridge context.
            if (!isValidJsonValue(parsedChallengeData)) {
                onInitializationErrorCloseAndCancel(BadWebViewException("Invalid challenge data"))
                return@makeBotguardServiceRequest
            }
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        SecureLogger.d(TAG) { "BotGuard initialization returned a JavaScript error" }
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        // Challenge responses contain opaque BotGuard data and must never reach logs.
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]"
        ) { responseBody ->
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)

            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds - 600)

            scope.launch(Dispatchers.Main) {
                if (!isValidUint8ArrayLiteral(integrityToken)) {
                    onInitializationErrorCloseAndCancel(BadWebViewException("Invalid integrity token"))
                    return@launch
                }
                webView.evaluateJavascript(
                    "this.integrityToken = $integrityToken"
                ) {
                    SecureLogger.d(TAG) { "BotGuard initialization completed" }
                    initCallback(Result.success(this@PoTokenWebView))
                }
            }
        }
    }

    override suspend fun generatePoToken(identifier: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            addPoTokenContinuation(identifier, continuation)
            val u8Identifier = stringToU8(identifier)
            webView.evaluateJavascript(
                """try {
                        identifier = ${JSONObject.quote(identifier)}
                        u8Identifier = $u8Identifier
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        poTokenU8String = ""
                        for (i = 0; i < poTokenU8.length; i++) {
                            if (i != 0) poTokenU8String += ","
                            poTokenU8String += poTokenU8[i]
                        }
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }"""
            ) {}
        }
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        SecureLogger.d(TAG) { "PoToken generation returned a JavaScript error" }
        scope.launch(Dispatchers.Main) {
            popPoTokenContinuation(identifier)?.resumeWithException(buildExceptionForJsError(error))
        }
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            scope.launch(Dispatchers.Main) {
                popPoTokenContinuation(identifier)?.resumeWithException(t)
            }
            return
        }

        scope.launch(Dispatchers.Main) {
            popPoTokenContinuation(identifier)?.resume(poToken)
        }
    }

    override fun isExpired(): Boolean {
        return Instant.now().isAfter(expirationInstant)
    }

    private fun addPoTokenContinuation(identifier: String, continuation: CancellableContinuation<String>) {
        synchronized(poTokenContinuations) {
            poTokenContinuations.add(Pair(identifier, continuation))
        }
        continuation.invokeOnCancellation {
            popPoTokenContinuation(identifier)
        }
    }

    private fun popPoTokenContinuation(identifier: String): CancellableContinuation<String>? {
        return synchronized(poTokenContinuations) {
            val index = poTokenContinuations.indexOfFirst { it.first == identifier }
            if (index >= 0) {
                poTokenContinuations.removeAt(index).second
            } else {
                null
            }
        }
    }

    private fun popAllPoTokenContinuations(): List<Pair<String, CancellableContinuation<String>>> {
        return synchronized(poTokenContinuations) {
            val result = poTokenContinuations.toList()
            poTokenContinuations.clear()
            result
        }
    }

    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit
    ) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    NewPipe.getDownloader().post(
                        url,
                        mapOf(
                            "User-Agent" to listOf(USER_AGENT),
                            "Accept" to listOf("application/json"),
                            "Content-Type" to listOf("application/json+protobuf"),
                            "x-goog-api-key" to listOf(com.vyllo.music.BuildConfig.GOOGLE_API_KEY),
                            "x-user-agent" to listOf("grpc-web-javascript/0.1")
                        ),
                        data.toByteArray()
                    )
                }
                val httpCode = response.responseCode()
                if (httpCode != 200) {
                    onInitializationErrorCloseAndCancel(
                        PoTokenException("Invalid response code: $httpCode")
                    )
                    return@launch
                }
                val responseBody = response.responseBody()
                handleResponseBody(responseBody)
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(e)
            }
        }
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {       scope.launch(Dispatchers.Main) {
            close()
            initCallback(Result.failure(error))
            popAllPoTokenContinuations().forEach { (_, continuation) ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    @MainThread
    override fun close() {
        scope.cancel()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    companion object : PoTokenGenerator.Factory {
        private val TAG = PoTokenWebView::class.simpleName ?: "PoTokenWebView"

        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        override suspend fun newPoTokenGenerator(context: Context): PoTokenGenerator = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var potWv: PoTokenWebView? = null
                potWv = PoTokenWebView(context) { result ->
                    if (continuation.isActive) {
                        result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = {
                                potWv?.close()
                                continuation.resumeWithException(it)
                            }
                        )
                    }
                }
                potWv.loadHtmlAndObtainBotguard(context)
                continuation.invokeOnCancellation {
                    potWv.close()
                }
            }
        }
    }
}

/** Bounds the `new Uint8Array([...])` literal so a tampered token cannot bloat memory. */
internal const val MAX_UINT8_ARRAY_ELEMENTS = 2_500

/**
 * Validates a string that is interpolated raw into the privileged
 * `data = $parsedChallengeData` JavaScript. org.json parses leniently and
 * ignores trailing text, so a payload like `{"a":1};evil()` must be rejected
 * by requiring the single object/array to consume the whole input and to be
 * non-empty.
 */
internal fun isValidJsonValue(raw: String): Boolean {
    return try {
        val trimmed = raw.trim()
        val tokener = org.json.JSONTokener(trimmed)
        val value = tokener.nextValue()
        val consumedAll = tokener.nextClean().code == 0
        when {
            value is JSONObject -> consumedAll && value.length() > 0
            value is org.json.JSONArray -> consumedAll && value.length() > 0
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * The integrity token is interpolated as raw JavaScript
 * ("this.integrityToken = $integrityToken"), so it must be exactly the
 * `new Uint8Array([...])` literal produced by base64ToU8(). The previous
 * JSON-based check accepted everything (JSONObject.quote never fails),
 * leaving a code-injection hole if the GenerateIT response were tampered.
 */
internal fun isValidUint8ArrayLiteral(raw: String): Boolean {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("new Uint8Array([") || !trimmed.endsWith("])")) return false
    val body = trimmed.removePrefix("new Uint8Array([").removeSuffix("])")
    val elements = body.split(",")
    if (elements.size > MAX_UINT8_ARRAY_ELEMENTS) return false
    return elements.all { element ->
        val value = elementOrNull(element) ?: return@all false
        value in 0..255
    }
}

private fun elementOrNull(element: String): Int? {
    val value = element.trim()
    if (value.isEmpty() || value.length > 3 || !value.all { it.isDigit() }) return null
    return value.toIntOrNull()
}
