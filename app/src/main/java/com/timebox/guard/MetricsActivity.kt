package com.timebox.guard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The app's home screen: a read-only dashboard over [UsageLog]. Pick a time
 * window (today / 7 / 30 days / all) and it aggregates the logged events
 * into a summary, a per-day bar chart, a per-app breakdown with bars, a
 * restraint bar, and the most recent reasons typed.
 *
 * Everything is drawn programmatically into the `metricsContainer` from the
 * layout, in the same plain-views style as the settings screen. The only
 * custom view is [BarChartView].
 */
class MetricsActivity : Activity() {

    private enum class Range(val label: String, val windowMs: Long?) {
        TODAY("today", null),
        WEEK("last 7 days", TimeUnit.DAYS.toMillis(7)),
        MONTH("last 30 days", TimeUnit.DAYS.toMillis(30)),
        ALL("all time", Long.MAX_VALUE),
    }

    private val accent = Color.parseColor("#3F7DE0")

    private lateinit var container: LinearLayout
    private var range = Range.WEEK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metrics)

        container = findViewById(R.id.metricsContainer)

        findViewById<Button>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val buttons = mapOf(
            Range.TODAY to findViewById<Button>(R.id.rangeToday),
            Range.WEEK to findViewById(R.id.range7),
            Range.MONTH to findViewById(R.id.range30),
            Range.ALL to findViewById(R.id.rangeAll),
        )
        buttons.forEach { (r, button) ->
            button.setOnClickListener {
                range = r
                highlightRange(buttons)
                render()
            }
        }
        highlightRange(buttons)

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

    private fun highlightRange(buttons: Map<Range, Button>) {
        buttons.forEach { (r, button) ->
            button.setTypeface(null, if (r == range) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun windowStart(): Long = when (range) {
        Range.TODAY -> startOfToday()
        Range.ALL -> 0L
        else -> System.currentTimeMillis() - (range.windowMs ?: 0L)
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun render() {
        container.removeAllViews()

        val from = windowStart()
        val allEvents = UsageLog.allEvents(this)
        val events = allEvents.filter { it.timestamp >= from }

        if (allEvents.isEmpty()) {
            container.addView(bodyText("No activity recorded yet. Open a guarded app and your metrics will show up here.\n\nTap Settings above to choose which apps to guard."))
            return
        }
        if (events.isEmpty()) {
            container.addView(bodyText("Nothing recorded ${range.label}."))
        }

        val starts = events.filter { it.type == UsageLog.TYPE_START }
        val closes = events.filter { it.type == UsageLog.TYPE_CLOSE }
        val usages = events.filter { it.type == UsageLog.TYPE_USAGE }

        val focusedMs = usages.sumOf { it.durationMs }
        val plannedMs = starts.sumOf { it.plannedMin * 60_000L }
        val decisions = starts.size + closes.size
        val restraint = if (decisions > 0) closes.size * 100 / decisions else 0

        // ---- Summary ---------------------------------------------------
        container.addView(sectionHeader("Summary — ${range.label}"))
        container.addView(statRow("Focused time in guarded apps", formatDuration(focusedMs)))
        container.addView(statRow("Sessions started", starts.size.toString()))
        container.addView(statRow("Closed with “Close app instead”", closes.size.toString()))
        if (starts.isNotEmpty()) {
            container.addView(statRow("Avg session (actual)", formatDuration(focusedMs / starts.size)))
            container.addView(statRow("Planned vs. actual", "${formatDuration(plannedMs)} → ${formatDuration(focusedMs)}"))
        }

        // ---- Restraint bar -------------------------------------------
        if (decisions > 0) {
            container.addView(sectionHeader("Restraint rate — $restraint%"))
            container.addView(bodyText("You backed out of ${closes.size} of $decisions prompts."))
            container.addView(
                splitBar(
                    leftValue = closes.size.toFloat(),
                    rightValue = starts.size.toFloat(),
                    leftColor = accent,
                    rightColor = Color.parseColor("#D8DCE0"),
                )
            )
            container.addView(legendRow("Backed out (${closes.size})", accent, "Went ahead (${starts.size})", Color.parseColor("#D8DCE0")))
        }

        // ---- Per-day chart ------------------------------------------
        val days = if (range == Range.MONTH || range == Range.ALL) 14 else 7
        container.addView(sectionHeader("Focused time — last $days days"))
        container.addView(dayChart(allEvents, days))

        // ---- Per-app breakdown -------------------------------------
        val packages = events.map { it.pkg }.toSet()
        val perApp = packages.map { pkg ->
            AppStats(
                pkg = pkg,
                focusedMs = usages.filter { it.pkg == pkg }.sumOf { it.durationMs },
                started = starts.count { it.pkg == pkg },
                closed = closes.count { it.pkg == pkg },
            )
        }.sortedWith(compareByDescending<AppStats> { it.focusedMs }.thenByDescending { it.started + it.closed })

        if (perApp.isNotEmpty()) {
            container.addView(sectionHeader("By app"))
            val maxAppMs = perApp.maxOf { it.focusedMs }.coerceAtLeast(1L)
            for (stat in perApp) {
                container.addView(
                    appRow(
                        label = appLabel(stat.pkg),
                        value = buildString {
                            append(formatDuration(stat.focusedMs))
                            append("  ·  ${stat.started} started")
                            if (stat.closed > 0) append(", ${stat.closed} closed")
                        },
                        fraction = stat.focusedMs.toFloat() / maxAppMs,
                    )
                )
            }
        }

        // ---- Reasons ------------------------------------------------
        val reasoned = starts.filter { it.reason.isNotBlank() }
        if (reasoned.isNotEmpty()) {
            container.addView(sectionHeader("Recent reasons"))
            for (event in reasoned.sortedByDescending { it.timestamp }.take(20)) {
                container.addView(
                    bodyText("“${event.reason}”\n${appLabel(event.pkg)} · ${event.plannedMin} min · ${relativeTime(event.timestamp)}")
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

    // ---- charts -------------------------------------------------------

    /** Bar chart of focused minutes for each of the last [days] calendar days. */
    private fun dayChart(allEvents: List<UsageLog.Event>, days: Int): View {
        val dayFmt = SimpleDateFormat(if (days > 7) "d" else "EEE", Locale.getDefault())
        val startToday = startOfToday()
        val dayMs = TimeUnit.DAYS.toMillis(1)

        val bars = (days - 1 downTo 0).map { back ->
            val dayStart = startToday - back * dayMs
            val dayEnd = dayStart + dayMs
            val ms = allEvents
                .filter { it.type == UsageLog.TYPE_USAGE && it.timestamp >= dayStart && it.timestamp < dayEnd }
                .sumOf { it.durationMs }
            BarChartView.Bar(
                label = if (back == 0) "Today" else dayFmt.format(Date(dayStart)),
                value = ms / 60_000f,
                valueLabel = if (ms >= 60_000L) formatDuration(ms) else "",
            )
        }

        return BarChartView(this).apply {
            barColor = accent
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170)
            ).apply { topMargin = dp(4) }
            setBars(bars)
        }
    }

    /** A single horizontal bar split between two values. */
    private fun splitBar(leftValue: Float, rightValue: Float, leftColor: Int, rightColor: Int): View {
        val total = (leftValue + rightValue).coerceAtLeast(1f)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14))
                .apply { topMargin = dp(6) }
            addView(barPiece(leftValue / total, leftColor))
            addView(barPiece(rightValue / total, rightColor))
        }
    }

    private fun barPiece(weight: Float, color: Int): View = View(this).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight.coerceAtLeast(0.0001f))
    }

    /** App name + stats on one line, with a proportional bar underneath. */
    private fun appRow(label: String, value: String, fraction: Float): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(this).apply {
            text = label
            textSize = 14f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(8), 0, 0, 0)
        })
        col.addView(top)

        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
                .apply { topMargin = dp(4) }
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(Color.parseColor("#ECECEC"))
            }
        }
        track.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(accent)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fraction.coerceIn(0.02f, 1f))
        })
        track.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (1f - fraction).coerceIn(0f, 0.98f))
        })
        col.addView(track)
        return col
    }

    private fun legendRow(leftText: String, leftColor: Int, rightText: String, rightColor: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        row.addView(dot(leftColor))
        row.addView(TextView(this).apply {
            text = leftText
            textSize = 12f
            setPadding(dp(4), 0, dp(16), 0)
        })
        row.addView(dot(rightColor))
        row.addView(TextView(this).apply {
            text = rightText
            textSize = 12f
            setPadding(dp(4), 0, 0, 0)
        })
        return row
    }

    private fun dot(color: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { gravity = Gravity.CENTER_VERTICAL }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    // ---- view builders ----------------------------------------------

    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(22), 0, dp(6))
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

    // ---- formatting ------------------------------------------------

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
            else -> android.text.format.DateFormat.getDateFormat(this).format(Date(ts))
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
