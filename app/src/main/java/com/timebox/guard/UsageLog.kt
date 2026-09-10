package com.timebox.guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Append-only log of what the user did with the prompt, kept in its own
 * SharedPreferences file as a single JSON array under [KEY_EVENTS].
 *
 * Three event types (`t`):
 *  - `"start"` — user chose "Start session": has `reason` and `plannedMin`.
 *  - `"close"` — user chose "Close app instead" (an avoided use).
 *  - `"usage"` — a guarded app left the foreground after a started session;
 *    `durMs` is how long it was actually in front.
 *
 * Every event carries `pkg` (empty for the generic unlock prompt) and `ts`
 * (epoch millis). The list is capped at [MAX_EVENTS], oldest dropped first,
 * so it can never grow without bound.
 */
object UsageLog {
    private const val PREF_NAME = "timebox_usage"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 4000

    const val TYPE_START = "start"
    const val TYPE_CLOSE = "close"
    const val TYPE_USAGE = "usage"

    data class Event(
        val type: String,
        val pkg: String,
        val timestamp: Long,
        val reason: String = "",
        val plannedMin: Int = 0,
        val durationMs: Long = 0L,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun logStart(context: Context, pkg: String?, reason: String, plannedMin: Int) {
        append(context, JSONObject().apply {
            put("t", TYPE_START)
            put("pkg", pkg ?: "")
            put("ts", System.currentTimeMillis())
            put("reason", reason)
            put("plannedMin", plannedMin)
        })
    }

    fun logClose(context: Context, pkg: String?) {
        append(context, JSONObject().apply {
            put("t", TYPE_CLOSE)
            put("pkg", pkg ?: "")
            put("ts", System.currentTimeMillis())
        })
    }

    fun logUsage(context: Context, pkg: String, durationMs: Long) {
        if (durationMs <= 0L) return
        append(context, JSONObject().apply {
            put("t", TYPE_USAGE)
            put("pkg", pkg)
            put("ts", System.currentTimeMillis())
            put("durMs", durationMs)
        })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_EVENTS).apply()
    }

    fun allEvents(context: Context): List<Event> {
        val raw = prefs(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(
                        Event(
                            type = o.optString("t"),
                            pkg = o.optString("pkg"),
                            timestamp = o.optLong("ts"),
                            reason = o.optString("reason"),
                            plannedMin = o.optInt("plannedMin"),
                            durationMs = o.optLong("durMs"),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun append(context: Context, event: JSONObject) {
        val p = prefs(context)
        val array = try {
            JSONArray(p.getString(KEY_EVENTS, null) ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        array.put(event)
        // Trim from the front if we're over the cap.
        val trimmed = if (array.length() > MAX_EVENTS) {
            JSONArray().also { out ->
                for (i in (array.length() - MAX_EVENTS) until array.length()) {
                    out.put(array.get(i))
                }
            }
        } else {
            array
        }
        p.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
    }
}
