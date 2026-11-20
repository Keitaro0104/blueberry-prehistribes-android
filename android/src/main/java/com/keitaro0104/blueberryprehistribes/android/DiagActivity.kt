package com.keitaro0104.blueberryprehistribes.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Plain diagnostic launcher screen (no game code at all).
 *
 * Purpose: find out HOW FAR the app gets before it dies.
 * - If this screen appears, the Application and a plain Activity start fine,
 *   so the crash is inside the game Activity (libGDX init) or later.
 * - If the app still vanishes before this screen, the crash happens before
 *   any Activity can even start.
 *
 * The screen shows the live session log and, after a crash, the log of the
 * previous run (if any) — readable on screen, so the user can screenshot
 * it instead of hunting for a file in Download.
 */
class DiagActivity : Activity() {

    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            updateLogView()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.log("DiagActivity.onCreate")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "Blueberry Tribes — Diagnosis"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        root.addView(title)

        val help = TextView(this).apply {
            text = "Press Start Game. If the app closes, open it again — " +
                "the log of the crashed run shows below. Screenshot it and send it."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(help)

        val startButton = Button(this).apply {
            text = "Start Game"
            textSize = 18f
            setOnClickListener {
                CrashLog.log("Diag: Start pressed")
                startActivity(Intent(this@DiagActivity, AndroidLauncher::class.java))
            }
        }
        root.addView(startButton)

        val saveButton = Button(this).apply {
            text = "Save log to Download"
            setOnClickListener {
                val ok = CrashLog.exportToDownloads(
                    this@DiagActivity,
                    CrashLog.sessionText() + "\n" + CrashLog.lastCrashText(this@DiagActivity)
                )
                Toast.makeText(
                    this@DiagActivity,
                    if (ok) "Log saved to Download" else "Could not save log",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        root.addView(saveButton)

        scrollView = ScrollView(this).apply {
            setPadding(0, dp(12), 0, 0)
        }
        logView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.BLACK)
        }
        scrollView.addView(logView)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
        CrashLog.log("DiagActivity: screen shown")
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun updateLogView() {
        val last = CrashLog.lastCrashText(this)
        val current = CrashLog.sessionText()
        val text = StringBuilder()
        text.append("=== LAST RUN (before a crash) ===\n")
        text.append(if (last.isBlank()) "(no log from a previous run)\n" else last)
        if (!last.endsWith("\n")) text.append('\n')
        text.append("\n=== THIS RUN ===\n")
        text.append(current)
        logView.text = text.toString()
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
