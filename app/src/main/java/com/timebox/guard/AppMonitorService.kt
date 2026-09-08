package com.timebox.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Runs for as long as the user has the accessibility service turned on
 * (Settings > Accessibility > Timebox Guard). It:
 *  1. Watches which app comes to the foreground.
 *  2. If that app is on the guarded list and has no active/valid session,
 *     it shows the blocking prompt overlay.
 *  3. If it does have an active session, it schedules a check for when
 *     that session's time runs out, and re-blocks then.
 *  4. Also listens for the phone being unlocked, to optionally show the
 *     same prompt at unlock time.
 *
 * The prompt is a system overlay window (see [PromptOverlay]) rather than
 * an Activity: launching an Activity from the background is unreliable on
 * Android 10+ and produced a flicker loop with the guarded app.
 */
class AppMonitorService : AccessibilityService() {

    /** Flip to true to get on-screen debug toasts. */
    private val debug = false

    private val handler = Handler(Looper.getMainLooper())
    private val overlay by lazy { PromptOverlay(this) }
    private var currentForegroundPackage: String? = null
    private var pendingCheckRunnable: Runnable? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT &&
                Prefs.isUnlockPromptEnabled(applicationContext)
            ) {
                showPrompt(null)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.DEFAULT
        serviceInfo = info

        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        toast("Timebox Guard service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return // our own overlay / setup UI
        if (pkg == currentForegroundPackage) return // no actual app switch

        currentForegroundPackage = pkg
        pendingCheckRunnable?.let { handler.removeCallbacks(it) }
        pendingCheckRunnable = null

        // Leaving a guarded app dismisses a prompt that is still up.
        if (overlay.isShowing) overlay.hide()

        if (!Prefs.isMonitored(applicationContext, pkg)) return

        toast("Guarded app in front: $pkg")

        val endTime = Prefs.getEndTime(applicationContext, pkg)
        val now = System.currentTimeMillis()

        if (endTime <= 0L || now >= endTime) {
            Prefs.clearEndTime(applicationContext, pkg)
            showPrompt(pkg)
        } else {
            // Time left on the clock - re-check exactly when it runs out.
            val runnable = Runnable {
                if (currentForegroundPackage == pkg &&
                    System.currentTimeMillis() >= Prefs.getEndTime(applicationContext, pkg)
                ) {
                    Prefs.clearEndTime(applicationContext, pkg)
                    showPrompt(pkg)
                }
            }
            pendingCheckRunnable = runnable
            handler.postDelayed(runnable, endTime - now)
        }
    }

    private fun showPrompt(targetPackage: String?) {
        if (!Settings.canDrawOverlays(this)) {
            toastAlways("Timebox Guard: grant \"Display over other apps\" to block apps")
            return
        }
        if (overlay.isShowing) return
        overlay.show(targetPackage) {
            // "Close app instead" was tapped - drop any pending timer.
            pendingCheckRunnable?.let { handler.removeCallbacks(it) }
            pendingCheckRunnable = null
        }
    }

    private fun toast(message: String) {
        if (debug) toastAlways(message)
    }

    private fun toastAlways(message: String) {
        handler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        overlay.hide()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // never registered / already unregistered - ignore
        }
    }
}
