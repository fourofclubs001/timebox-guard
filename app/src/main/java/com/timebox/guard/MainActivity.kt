package com.timebox.guard

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Setup screen: enable the accessibility service, choose which apps
 * are guarded (searchable list with a switch per app), and toggle the
 * unlock-time prompt.
 */
class MainActivity : Activity() {

    private lateinit var adapter: AppListAdapter
    private lateinit var selected: MutableSet<String>
    private lateinit var overlayStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessibilityButton = findViewById<Button>(R.id.buttonAccessibility)
        val overlayButton = findViewById<Button>(R.id.buttonOverlay)
        overlayStatus = findViewById(R.id.textOverlayStatus)
        val unlockSwitch = findViewById<Switch>(R.id.switchUnlockPrompt)
        val searchInput = findViewById<EditText>(R.id.editSearch)
        val list = findViewById<RecyclerView>(R.id.listApps)

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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

        adapter = AppListAdapter(loadApps(), selected) { updated ->
            Prefs.setSelectedApps(this, updated)
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            overlayStatus.text = "Granted ✓"
        } else {
            overlayStatus.text =
                "Required so the blocking prompt can appear on top of the guarded app."
        }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .map { AppEntry(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }
    }
}
