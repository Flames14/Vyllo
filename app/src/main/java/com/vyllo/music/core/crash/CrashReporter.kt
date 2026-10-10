package com.vyllo.music.core.crash

import android.content.Context
import android.content.Intent
import com.vyllo.music.BuildConfig
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local crash persistence.
 *
 * Vyllo ships without a telemetry backend on purpose â€” no third party sees a
 * user's stack trace. That trade-off only pays off if crashes still reach the
 * developer, so this records every uncaught exception to app-private storage
 * where it survives the process death that follows the crash, and is read back
 * on the next launch.
 *
 * Guarantees:
 *  - The previous handler is always chained, so Android's own crash dialog and
 *    the system's crash reporting keep working exactly as before.
 *  - Recording can never throw: a failure inside the handler would otherwise
 *    mask the original crash and produce a useless "crash while crashing".
 *  - Reports are capped and pruned so a crash loop cannot fill the disk.
 *  - Payloads are scrubbed with [CrashScrubber] before hitting disk, so tokens,
 *    emails and URLs with credentials never leave the device inside a report.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR_NAME = "crash_reports"
    private const val MAX_REPORTS = 10
    private const val MAX_REPORT_BYTES = 64 * 1024

    private val installed = AtomicBoolean(false)

    /**
     * Captures the report directory and installs the handler. Safe to call more
     * than once; only the first call takes effect. Must run as early as possible
     * in [android.app.Application.onCreate] so startup crashes are captured too.
     */
    fun install(context: Context) {
        runCatching { VylloCrashPaths.set(File(context.filesDir, DIR_NAME).apply { mkdirs() }) }
            .onFailure { SecureLogger.e(TAG, "Could not create crash report dir", it) }

        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let recording take down the original crash path.
            runCatching { record(thread, throwable) }
                .onFailure { SecureLogger.e(TAG, "Failed to persist crash report", it) }
            previous?.uncaughtException(thread, throwable)
                ?: Runtime.getRuntime().halt(1)
        }
    }

    /** True once at least one report from a previous run is waiting. */
    fun hasPendingReports(context: Context): Boolean = pendingReports(context).isNotEmpty()

    /** Reports written by earlier runs, newest first. */
    fun pendingReports(context: Context): List<File> {
        val dir = reportsDir(context) ?: return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }

    /** Renders all pending reports into a single shareable/plain-text document. */
    fun buildReportDocument(context: Context): String {
        val reports = pendingReports(context)
        if (reports.isEmpty()) return "No crash reports."
        return buildString {
            appendLine("Vyllo crash report")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("debug=${BuildConfig.DEBUG}")
            appendLine("reports=${reports.size}")
            appendLine()
            reports.forEach { file ->
                appendLine("=".repeat(72))
                appendLine(runCatching { file.readText() }.getOrElse { "<unreadable: ${it.message}>" })
                appendLine()
            }
        }
    }

    /** Standard share sheet intent so a user can send reports without a backend. */
    fun shareIntent(context: Context): Intent {
        val body = buildReportDocument(context)
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_subject))
            .putExtra(Intent.EXTRA_TEXT, body)
    }

    /** Called once a report set has been read out; wipes the store. */
    fun clear(context: Context) {
        runCatching { reportsDir(context)?.listFiles()?.forEach { it.delete() } }
    }

    private fun record(thread: Thread, throwable: Throwable) {
        val dir = VylloCrashPaths.reportsDir ?: return
        prune(dir)

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(dir, "$stamp.txt")
        val payload = CrashScrubber.scrub(render(thread, throwable))

        file.outputStream().use { out ->
            val bytes = payload.take(MAX_REPORT_BYTES).toByteArray(Charsets.UTF_8)
            out.write(bytes)
        }
        // Last line: helps grep reports straight from `adb` / a bug report zip.
        file.appendText("\n--\nthread=${thread.name}\n")
    }

    private fun render(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("time=${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())}")
        appendLine("thread=${thread.name} (state=${thread.state})")
        appendLine("device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}")
        appendLine("android=${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine()
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < 8) {
            appendLine(if (depth == 0) "FATAL: ${cause.javaClass.name}: ${cause.message}" else "Caused by: ${cause.javaClass.name}: ${cause.message}")
            cause.stackTrace.forEach { appendLine("    at $it") }
            cause = cause.cause
            depth++
        }
    }

    private fun reportsDir(context: Context): File? = runCatching {
        File(context.filesDir, DIR_NAME).apply { mkdirs() }
    }.getOrNull()

    /** Keep at most [MAX_REPORTS] files so a crash loop cannot exhaust storage. */
    private fun prune(dir: File) {
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
        }
    }
}

/**
 * Process-lifetime holder for the crash report directory.
 *
 * The handler runs while the process is dying, where Hilt may already be
 * unusable, so the path is captured eagerly at startup. It deliberately holds
 * a [File] â€” not an `android.content.Context` â€” so there is no static
 * reference to an Android component for lint (or the GC) to worry about.
 */
internal object VylloCrashPaths {

    @Volatile
    var reportsDir: File? = null
        private set

    fun set(dir: File) {
        reportsDir = dir
    }
}
