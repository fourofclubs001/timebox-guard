package com.timebox.guard

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Switch

/**
 * Setup screen: enable the accessibility service, choose which apps
 * are guarded, and toggle the unlock-time prompt.
 */
class MainActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var apps: List<ApplicationInfo>
    private lateinit var selected: MutableSet<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessibilityButton = findViewById<Button>(R.id.buttonAccessibility)
        val unlockSwitch = findViewById<Switch>(R.id.switchUnlockPrompt)
        listView = findViewById(R.id.listApps)

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        unlockSwitch.isChecked = Prefs.isUnlockPromptEnabled(this)
        unlockSwitch.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setUnlockPromptEnabled(this, isChecked)
        }

        selected = Prefs.getSelectedApps(this)
        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val labels = apps.map { pm.getApplicationLabel(it).toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        apps.forEachIndexed { index, appInfo ->
            if (selected.contains(appInfo.packageName)) {
                listView.setItemChecked(index, true)
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = apps[position].packageName
            if (listView.isItemChecked(position)) {
                selected.add(pkg)
            } else {
                selected.remove(pkg)
            }
            Prefs.setSelectedApps(this, selected)
        }
    }
}
