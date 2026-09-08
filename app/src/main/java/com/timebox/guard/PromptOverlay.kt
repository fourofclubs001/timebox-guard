package com.timebox.guard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast

/**
 * The full-screen blocking prompt, shown as a system overlay window drawn
 * straight from [AppMonitorService] rather than as an Activity.
 *
 * Launching an Activity from the background is unreliable on Android 10+
 * (it flickers away and the guarded app comes back), so instead we add an
 * opaque, focusable view on top of everything via WindowManager. Removing
 * it simply reveals the guarded app that is still sitting underneath.
 */
class PromptOverlay(private val context: Context) {

    companion object {
        private const val MINUTE_STEP = 5
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    /**
     * @param targetPackage the guarded app, or null for the generic
     *   post-unlock prompt.
     * @param onClosed called after "Close app instead" (the guarded app was
     *   sent away), so the service can forget any pending timer.
     */
    fun show(targetPackage: String?, onClosed: () -> Unit) {
        if (view != null) return

        val v = LayoutInflater.from(context).inflate(R.layout.overlay_prompt, null)

        val title = v.findViewById<TextView>(R.id.textTitle)
        val picker = v.findViewById<NumberPicker>(R.id.pickerMinutes)
        val reasonInput = v.findViewById<EditText>(R.id.editReason)
        val startButton = v.findViewById<Button>(R.id.buttonStart)
        val closeButton = v.findViewById<Button>(R.id.buttonClose)

        val minuteOptions = (1..36).map { (it * MINUTE_STEP).toString() }.toTypedArray()
        picker.minValue = 0
        picker.maxValue = minuteOptions.size - 1
        picker.displayedValues = minuteOptions
        picker.wrapSelectorWheel = false
        picker.value = 2 // 15 minutes

        val label = targetPackage?.let { appLabel(it) }
        title.text = if (label != null)
            "You're opening $label.\nHow long, and why?"
        else
            "Before you continue,\nhow long and why?"

        startButton.setOnClickListener {
            val reason = reasonInput.text.toString().trim()
            if (reason.isEmpty()) {
                Toast.makeText(context, "Please enter a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val minutes = (picker.value + 1) * MINUTE_STEP
            targetPackage?.let {
                Prefs.setEndTime(context, it, System.currentTimeMillis() + minutes * 60_000L)
            }
            hide()
        }

        closeButton.setOnClickListener {
            targetPackage?.let { Prefs.clearEndTime(context, it) }
            hide()
            goHome()
            onClosed()
        }

        // Swallow the back key so the prompt can't just be dismissed.
        v.isFocusableInTouchMode = true
        v.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        windowManager.addView(v, params)
        view = v
    }

    fun hide() {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // not attached anymore - ignore
            }
        }
        view = null
    }

    private fun goHome() {
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun appLabel(pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
