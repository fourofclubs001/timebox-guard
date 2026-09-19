package com.timebox.guard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

/**
 * Small wrapper around SharedPreferences holding:
 *  - the set of package names the user wants guarded
 *  - the "session end time" (epoch millis) per guarded package
 *  - whether the time/reason prompt should also show on unlock
 *  - per-app: whether the reason is restricted to a fixed list of
 *    "excuses" the user defined, and what that list is
 */
object Prefs {
    private const val PREF_NAME = "timebox_prefs"
    private const val KEY_SELECTED_APPS = "selected_apps"
    private const val KEY_UNLOCK_PROMPT = "unlock_prompt_enabled"
    private const val KEY_END_TIME_PREFIX = "end_time_"
    private const val KEY_EXCUSES_ENABLED_PREFIX = "excuses_enabled_"
    private const val KEY_EXCUSES_PREFIX = "excuses_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getSelectedApps(context: Context): MutableSet<String> =
        HashSet(prefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet())

    fun setSelectedApps(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SELECTED_APPS, apps).apply()
    }

    fun isMonitored(context: Context, pkg: String): Boolean =
        getSelectedApps(context).contains(pkg)

    fun getEndTime(context: Context, pkg: String): Long =
        prefs(context).getLong(KEY_END_TIME_PREFIX + pkg, 0L)

    fun setEndTime(context: Context, pkg: String, endTime: Long) {
        prefs(context).edit().putLong(KEY_END_TIME_PREFIX + pkg, endTime).apply()
    }

    fun clearEndTime(context: Context, pkg: String) {
        prefs(context).edit().remove(KEY_END_TIME_PREFIX + pkg).apply()
    }

    fun isUnlockPromptEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UNLOCK_PROMPT, false)

    fun setUnlockPromptEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UNLOCK_PROMPT, enabled).apply()
    }

    /** Whether [pkg]'s prompt should offer a fixed list of reasons instead of free text. */
    fun isExcuseListEnabled(context: Context, pkg: String): Boolean =
        prefs(context).getBoolean(KEY_EXCUSES_ENABLED_PREFIX + pkg, false)

    fun setExcuseListEnabled(context: Context, pkg: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXCUSES_ENABLED_PREFIX + pkg, enabled).apply()
    }

    /** The user-defined reasons for [pkg], in the order they were added. */
    fun getExcuses(context: Context, pkg: String): List<String> {
        val raw = prefs(context).getString(KEY_EXCUSES_PREFIX + pkg, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun setExcuses(context: Context, pkg: String, excuses: List<String>) {
        val array = JSONArray()
        excuses.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_EXCUSES_PREFIX + pkg, array.toString()).apply()
    }
}
