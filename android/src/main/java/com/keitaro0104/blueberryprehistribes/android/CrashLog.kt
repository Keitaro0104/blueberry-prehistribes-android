package com.keitaro0104.blueberryprehistribes.android

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.keitaro0104.blueberryprehistribes.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash + session logging for the Android port.
 *
 * Every launch writes timestamped stage lines to a session log file in the
 * app's own external files dir (no permission needed, survives a crash).
 * On every force close — managed exception OR native crash — a copy of the
 * log ends up in the phone's Download folder as
 * `BlueberryTribes-log_<timestamp>.txt`:
 *
 * - Managed exception (Java/Kotlin): the global uncaught-exception handler
 *   appends the stack trace and exports the log to Download immediately.
 * - Native crash (the process dies without an exception): the log file is
 *   still on disk. At the next launch, if the "running" marker from the
 *   previous run is still there, the previous run never exited cleanly,
 *   so the old session log is exported to Download before a new one starts.
 */
object CrashLog {
    private const val SESSION_FILE = "tribes-session.log"
    private const val MARKER_FILE = "tribes-running.marker"

    private var sessionFile: File? = null

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    /** Call from Application.onCreate, before any activity starts. */
    fun install(context: Context) {
        try {
            installInner(context)
        } catch (_: Exception) {
            // Logging must never break the app.
        }
    }

    private fun installInner(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val marker = File(dir, MARKER_FILE)
        val oldSession = File(dir, SESSION_FILE)
        // A leftover marker means the previous run died without a clean exit.
        if (marker.exists() && oldSession.exists()) {
            try {
                exportToDownloads(context, oldSession.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // If the old log cannot be read, start fresh anyway.
            }
        }
        marker.createNewFile()
        sessionFile = oldSession
        sessionFile?.writeText("=== Blueberry Tribes session ${stamp()} ===\n")
        log(
            "session start | ${Build.MANUFACTURER} ${Build.MODEL} | " +
                "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) | " +
                "ABIs ${Build.SUPPORTED_ABIS.joinToString(",")} | " +
                "app ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringBuilder()
                trace.append("CRASH on thread ${thread.name} at ${stamp()}\n")
                var cause: Throwable? = error
                while (cause != null) {
                    trace.append(cause.toString()).append('\n')
                    for (el in cause.stackTrace) trace.append("    at ").append(el).append('\n')
                    cause = cause.cause
                    if (cause != null) trace.append("Caused by: ")
                }
                log(trace.toString())
                exportToDownloads(context, sessionFile?.readText(Charsets.UTF_8).orEmpty())
            } catch (_: Exception) {
                // Never let the crash handler itself crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Append one timestamped line to the session log. Safe to call from anywhere. */
    fun log(line: String) {
        try {
            sessionFile?.appendText("[${stamp()}] $line\n")
        } catch (_: Exception) {
            // Logging must never break the game.
        }
    }

    /** Call when the app exits cleanly (Activity.onDestroy). */
    fun cleanExit(context: Context) {
        try {
            log("clean exit")
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, MARKER_FILE).delete()
        } catch (_: Exception) {
            // Ignore.
        }
    }

    /**
     * Copy the given log text to the phone's Download folder via MediaStore
     * (Android 10+). File name: BlueberryTribes-log_<timestamp>.txt.
     * Returns true when the file was written.
     */
    fun exportToDownloads(context: Context, content: String): Boolean {
        if (content.isEmpty()) return false
        val name = "BlueberryTribes-log_${timestamp()}.txt"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                // Pre-Android 10 fallback: write straight to the public Download dir.
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeText(content, Charsets.UTF_8)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
