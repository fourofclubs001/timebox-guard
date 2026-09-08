package com.timebox.guard

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast

/**
 * Full-screen blocking prompt. Launched either:
 *  - with EXTRA_TARGET_PACKAGE set, when a guarded app is opened or its
 *    time runs out, or
 *  - with no extra, as the generic "why are you unlocking your phone" prompt.
 *
 * The only ways out are "Start session" (sets a new deadline and returns
 * control to the app underneath) or "Close app instead" (goes home).
 * The back button is disabled so the prompt can't just be dismissed.
 */
class PromptActivity : Activity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val MINUTE_STEP = 5
    }

    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt)

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

        val title = findViewById<TextView>(R.id.textTitle)
        val minutesPicker = findViewById<NumberPicker>(R.id.pickerMinutes)
        val reasonInput = findViewById<EditText>(R.id.editReason)
        val startButton = findViewById<Button>(R.id.buttonStart)
        val closeButton = findViewById<Button>(R.id.buttonClose)

        // Scroll wheel of 5, 10, 15 ... 180 minutes; default 15.
        val minuteOptions = (1..36).map { (it * MINUTE_STEP).toString() }.toTypedArray()
        minutesPicker.minValue = 0
        minutesPicker.maxValue = minuteOptions.size - 1
        minutesPicker.displayedValues = minuteOptions
        minutesPicker.wrapSelectorWheel = false
        minutesPicker.value = 2

        val appLabel = targetPackage?.let { getAppLabel(it) }
        title.text = if (appLabel != null)
            "You're opening $appLabel.\nHow long, and why?"
        else
            "Before you continue,\nhow long and why?"

        startButton.setOnClickListener {
            val minutes = (minutesPicker.value + 1) * MINUTE_STEP
            val reason = reasonInput.text.toString().trim()

            if (reason.isEmpty()) {
                Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            targetPackage?.let {
                val endTime = System.currentTimeMillis() + minutes * 60_000L
                Prefs.setEndTime(applicationContext, it, endTime)
                // Bring the guarded app back to the foreground - otherwise
                // finishing this prompt just drops back into our own task.
                packageManager.getLaunchIntentForPackage(it)?.let { launch ->
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                }
            }
            finish()
        }

        closeButton.setOnClickListener {
            targetPackage?.let { Prefs.clearEndTime(applicationContext, it) }
            goHome()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally does nothing: user must pick Start or Close app.
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    private fun getAppLabel(pkg: String): String? {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
