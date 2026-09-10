package com.timebox.guard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/** One installed, launchable app the user can choose to guard. */
data class AppEntry(val label: String, val packageName: String)

/**
 * Configuration screen: enable the accessibility service, allow the overlay
 * permission, choose which apps are guarded (searchable list with a switch
 * per app), and toggle the unlock-time prompt.
 *
 * This is no longer the launcher entry point — [MetricsActivity] is. It's
 * reached from the "Settings" button there.
 *
 * The whole screen is one ScrollView; the app list is plain inflated rows
 * inside it (no RecyclerView) so dragging anywhere scrolls the page.
 */
class SettingsActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var selected: MutableSet<String>

    /** Each app paired with its row view, so search can just hide/show rows. */
    private val rows = mutableListOf<Pair<AppEntry, View>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessibilityButton = findViewById<Button>(R.id.buttonAccessibility)
        val overlayButton = findViewById<Button>(R.id.buttonOverlay)
        overlayStatus = findViewById(R.id.textOverlayStatus)
        accessibilityStatus = findViewById(R.id.textAccessibilityStatus)
        val unlockSwitch = findViewById<Switch>(R.id.switchUnlockPrompt)
        val searchInput = findViewById<EditText>(R.id.editSearch)
        listContainer = findViewById(R.id.listContainer)

        accessibilityButton.setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                showAccessibilityDisclosure()
            }
        }

        overlayButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        unlockSwitch.isChecked = Prefs.isUnlockPromptEnabled(this)
        unlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setUnlockPromptEnabled(this, isChecked)
        }

        selected = Prefs.getSelectedApps(this)
        buildAppRows()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        overlayStatus.text = if (Settings.canDrawOverlays(this)) {
            "Granted ✓"
        } else {
            "Required so the blocking prompt can appear on top of the guarded app."
        }
        accessibilityStatus.text = if (isAccessibilityServiceEnabled()) {
            "Running ✓"
        } else {
            "Not enabled — nothing is being guarded. Tap above and turn Timebox Guard on."
        }
    }

    /**
     * Prominent disclosure shown before we send the user to enable the
     * accessibility service, as required by Google Play's policy on
     * accessibility-API use. States exactly what is accessed, why, and that
     * nothing leaves the device, and requires an explicit tap to continue.
     */
    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Before you enable the service")
            .setMessage(
                "Timebox Guard uses Android's Accessibility Service for one thing: " +
                    "to detect which app you've just opened, so it can show the " +
                    "time-and-reason prompt for apps you've chosen to guard and " +
                    "measure how long you spend in them.\n\n" +
                    "• It reads only the package name of the app in the foreground — " +
                    "not screen contents, text you type, or passwords.\n" +
                    "• All of it stays on this device. Nothing is sent anywhere, and " +
                    "there are no analytics or ads.\n" +
                    "• You can turn the service off at any time in Android Settings › " +
                    "Accessibility.\n\n" +
                    "Tap Continue to open Accessibility settings and turn Timebox Guard on."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    /**
     * Whether our [AppMonitorService] is currently switched on in system
     * settings. `enabled_accessibility_services` is a plain readable secure
     * setting; no special permission needed.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${AppMonitorService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            if (entry.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun buildAppRows() {
        val inflater = LayoutInflater.from(this)
        for (app in loadApps()) {
            val row = inflater.inflate(R.layout.item_app, listContainer, false)
            row.findViewById<TextView>(R.id.textLabel).text = app.label
            row.findViewById<TextView>(R.id.textPackage).text = app.packageName
            val guardSwitch = row.findViewById<Switch>(R.id.switchGuard)
            guardSwitch.isChecked = selected.contains(app.packageName)
            row.setOnClickListener {
                val nowOn = !selected.contains(app.packageName)
                if (nowOn) selected.add(app.packageName) else selected.remove(app.packageName)
                guardSwitch.isChecked = nowOn
                Prefs.setSelectedApps(this, selected)
            }
            listContainer.addView(row)
            rows.add(app to row)
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        for ((app, row) in rows) {
            val match = q.isEmpty() ||
                app.label.lowercase(Locale.getDefault()).contains(q) ||
                app.packageName.lowercase(Locale.getDefault()).contains(q)
            row.visibility = if (match) View.VISIBLE else View.GONE
        }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .map { AppEntry(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
}
