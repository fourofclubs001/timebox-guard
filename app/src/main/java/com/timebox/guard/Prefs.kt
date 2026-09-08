package com.timebox.guard

import android.content.Context
import android.content.SharedPreferences

/**
 * Small wrapper around SharedPreferences holding:
 *  - the set of package names the user wants guarded
 *  - the "session end time" (epoch millis) per guarded package
 *  - whether the time/reason prompt should also show on unlock
 */
object Prefs {
    private const val PREF_NAME = "timebox_prefs"
    private const val KEY_SELECTED_APPS = "selected_apps"
    private const val KEY_UNLOCK_PROMPT = "unlock_prompt_enabled"
    private const val KEY_END_TIME_PREFIX = "end_time_"

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
}
