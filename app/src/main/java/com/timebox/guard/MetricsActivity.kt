package com.timebox.guard

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Read-only dashboard over [UsageLog]. Pick a time window (today / 7 / 30
 * days / all) and it aggregates the logged events into:
 *  - a summary (focused time, sessions started, times backed out, restraint
 *    rate, planned-vs-actual),
 *  - a per-app breakdown, and
 *  - the most recent reasons the user typed.
 *
 * Everything is drawn programmatically into the `metricsContainer` from the
 * layout, in the same plain-views style as the setup screen.
 */
class MetricsActivity : Activity() {

    private enum class Range(val label: String, val windowMs: Long?) {
        TODAY("today", null),
        WEEK("last 7 days", TimeUnit.DAYS.toMillis(7)),
        MONTH("last 30 days", TimeUnit.DAYS.toMillis(30)),
        ALL("all time", Long.MAX_VALUE),
    }

    private lateinit var container: LinearLayout
    private var range = Range.WEEK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metrics)
        title = "Usage metrics"

        container = findViewById(R.id.metricsContainer)

        val buttons = mapOf(
            Range.TODAY to findViewById<Button>(R.id.rangeToday),
            Range.WEEK to findViewById(R.id.range7),
            Range.MONTH to findViewById(R.id.range30),
            Range.ALL to findViewById(R.id.rangeAll),
        )
        buttons.forEach { (r, button) ->
            button.setOnClickListener {
                range = r
                render()
            }
        }

        findViewById<Button>(R.id.buttonClearMetrics).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all usage data?")
                .setMessage("This permanently deletes every logged session, reason and close. It can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    UsageLog.clear(this)
                    render()
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun windowStart(): Long = when (range) {
        Range.TODAY -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        Range.ALL -> 0L
        else -> System.currentTimeMillis() - (range.windowMs ?: 0L)
    }

    private fun render() {
        container.removeAllViews()

        val from = windowStart()
        val events = UsageLog.allEvents(this).filter { it.timestamp >= from }

        if (events.isEmpty()) {
            container.addView(bodyText("No activity recorded ${range.label}. Once you start using guarded apps, your metrics show up here."))
            return
        }

        val starts = events.filter { it.type == UsageLog.TYPE_START }
        val closes = events.filter { it.type == UsageLog.TYPE_CLOSE }
        val usages = events.filter { it.type == UsageLog.TYPE_USAGE }

        val focusedMs = usages.sumOf { it.durationMs }
        val plannedMs = starts.sumOf { it.plannedMin * 60_000L }
        val decisions = starts.size + closes.size
        val restraint = if (decisions > 0) closes.size * 100 / decisions else 0

        container.addView(sectionHeader("Summary — ${range.label}"))
        container.addView(statRow("Focused time in guarded apps", formatDuration(focusedMs)))
        container.addView(statRow("Sessions started", starts.size.toString()))
        container.addView(statRow("Closed with “Close app instead”", closes.size.toString()))
        container.addView(statRow("Restraint rate", "$restraint%  ($decisions prompts)"))
        if (starts.isNotEmpty()) {
            container.addView(statRow("Avg session (actual)", formatDuration(focusedMs / starts.size)))
            container.addView(statRow("Planned vs. actual", "${formatDuration(plannedMs)} planned → ${formatDuration(focusedMs)} used"))
        }

        // ---- Per-app breakdown -------------------------------------------
        val packages = events.map { it.pkg }.toSet()
        val perApp = packages.map { pkg ->
            AppStats(
                pkg = pkg,
                focusedMs = usages.filter { it.pkg == pkg }.sumOf { it.durationMs },
                started = starts.count { it.pkg == pkg },
                closed = closes.count { it.pkg == pkg },
            )
        }.sortedWith(compareByDescending<AppStats> { it.focusedMs }.thenByDescending { it.started + it.closed })

        container.addView(sectionHeader("By app"))
        for (stat in perApp) {
            container.addView(
                statRow(
                    appLabel(stat.pkg),
                    buildString {
                        append(formatDuration(stat.focusedMs))
                        append("  ·  ")
                        append("${stat.started} started")
                        if (stat.closed > 0) append(", ${stat.closed} closed")
                    }
                )
            )
        }

        // ---- Reasons ---------------------------------------------------
        val reasoned = starts.filter { it.reason.isNotBlank() }
        if (reasoned.isNotEmpty()) {
            container.addView(sectionHeader("Recent reasons"))
            for (event in reasoned.sortedByDescending { it.timestamp }.take(20)) {
                container.addView(
                    bodyText(
                        "“${event.reason}”\n${appLabel(event.pkg)} · ${event.plannedMin} min · ${relativeTime(event.timestamp)}"
                    )
                )
            }
        }
    }

    private data class AppStats(
        val pkg: String,
        val focusedMs: Long,
        val started: Int,
        val closed: Int,
    )

    // ---- view builders -------------------------------------------------

    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun statRow(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 14f
            gravity = Gravity.END
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        })
        return row
    }

    private fun bodyText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#555555"))
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---- formatting --------------------------------------------------

    private fun formatDuration(ms: Long): String {
        if (ms < 60_000L) return "${ms / 1000}s"
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun relativeTime(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}d ago"
            else -> android.text.format.DateFormat.getDateFormat(this).format(java.util.Date(ts))
        }
    }

    private val labelCache = HashMap<String, String>()

    private fun appLabel(pkg: String): String {
        if (pkg.isBlank()) return "Unlock prompt"
        return labelCache.getOrPut(pkg) {
            try {
                val pm = packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }
}
