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
 * The log is written to TWO places from the very first line, so a force
 * close can never leave us with nothing:
 *
 * 1. A session log file in the app's own external files dir (no permission
 *    needed, survives a crash).
 * 2. A live file in the phone's Download folder, created the moment the app
 *    starts (`BlueberryTribes-log_<timestamp>.txt`). Every stage line is
 *    written to it and flushed immediately, so even a native crash one
 *    millisecond after launch leaves a readable log in Download — no second
 *    launch needed.
 *
 * On top of that, for a managed exception (Java/Kotlin) the global
 * uncaught-exception handler appends the stack trace to the live file.
 * And if a previous run died without a clean exit (leftover "running"
 * marker), its session log is kept as `tribes-last-crash.log` (shown on the
 * diagnostic screen) and exported to Download at the next launch.
 */
object CrashLog {
    private const val SESSION_FILE = "tribes-session.log"
    private const val MARKER_FILE = "tribes-running.marker"
    private const val LAST_CRASH_FILE = "tribes-last-crash.log"

    private var sessionFile: File? = null

    /** Open stream to the live log file in Download, if it could be created. */
    private var downloadStream: java.io.OutputStream? = null

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
        // Keep its log for the diagnostic screen before starting fresh.
        if (marker.exists() && oldSession.exists()) {
            try {
                File(dir, LAST_CRASH_FILE).writeText(oldSession.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // If the old log cannot be read, start fresh anyway.
            }
            try {
                exportToDownloads(context, oldSession.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                // If the old log cannot be read, start fresh anyway.
            }
        }
        marker.createNewFile()
        sessionFile = oldSession
        sessionFile?.writeText("=== Blueberry Tribes session ${stamp()} ===\n")
        // Open the live Download log NOW: if the app dies a millisecond
        // later, the lines already flushed are still there to read.
        downloadStream = openDownloadStream(context)
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
                try {
                    // The live Download file already holds the whole trace.
                    downloadStream?.flush()
                    downloadStream?.close()
                } catch (_: Exception) {
                }
                // If the live file never opened, fall back to a full export.
                if (downloadStream == null) {
                    exportToDownloads(context, sessionFile?.readText(Charsets.UTF_8).orEmpty())
                }
            } catch (_: Exception) {
                // Never let the crash handler itself crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Append one timestamped line to the session log and the live Download file. */
    fun log(line: String) {
        val text = "[${stamp()}] $line\n"
        try {
            sessionFile?.appendText(text)
        } catch (_: Exception) {
            // Logging must never break the game.
        }
        try {
            downloadStream?.write(text.toByteArray(Charsets.UTF_8))
            downloadStream?.flush()
        } catch (_: Exception) {
            // The live file is best-effort; the session file is the backup.
        }
    }

    /** Call when the app exits cleanly (Activity.onDestroy). */
    fun cleanExit(context: Context) {
        try {
            log("clean exit")
            downloadStream?.close()
        } catch (_: Exception) {
            // Ignore.
        }
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, MARKER_FILE).delete()
        } catch (_: Exception) {
            // Ignore.
        }
    }

    /** Current session log text (empty when logging never started). */
    fun sessionText(): String {
        return try {
            sessionFile?.readText(Charsets.UTF_8).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /** Log of the previous run, when it died without a clean exit. */
    fun lastCrashText(context: Context): String {
        return try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val last = File(dir, LAST_CRASH_FILE)
            if (last.exists()) last.readText(Charsets.UTF_8) else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Create the live log file in the phone's Download folder and return an
     * open stream to it, or null when it cannot be created. The file appears
     * in Download the moment the app starts, even if the app dies right after.
     */
    private fun openDownloadStream(context: Context): java.io.OutputStream? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "BlueberryTribes-log_${timestamp()}.txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, "BlueberryTribes-log_${timestamp()}.txt").outputStream()
            }
        } catch (_: Exception) {
            null
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
