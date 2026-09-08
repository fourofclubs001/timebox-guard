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
 *     it launches the blocking prompt.
 *  3. If it does have an active session, it schedules a check for when
 *     that session's time runs out, and re-blocks then.
 *  4. Also listens for the phone being unlocked, to optionally show the
 *     same prompt at unlock time.
 */
class AppMonitorService : AccessibilityService() {

    /** Flip to false to silence the on-screen debug toasts. */
    private val debug = true

    private val handler = Handler(Looper.getMainLooper())
    private var currentForegroundPackage: String? = null
    private var pendingCheckRunnable: Runnable? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                if (Prefs.isUnlockPromptEnabled(applicationContext)) {
                    launchPrompt(null)
                }
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
        if (pkg == packageName) return // ignore our own prompt/UI showing up
        if (pkg == currentForegroundPackage) return // no actual app switch

        currentForegroundPackage = pkg
        pendingCheckRunnable?.let { handler.removeCallbacks(it) }
        pendingCheckRunnable = null

        if (!Prefs.isMonitored(applicationContext, pkg)) return

        toast("Guarded app in front: $pkg")

        val endTime = Prefs.getEndTime(applicationContext, pkg)
        val now = System.currentTimeMillis()

        if (endTime <= 0L || now >= endTime) {
            // No planned session yet, or the previous one already expired.
            Prefs.clearEndTime(applicationContext, pkg)
            launchPrompt(pkg)
        } else {
            // There's time left on the clock - schedule a re-check for
            // exactly when it runs out, in case the user is still in
            // that app at that moment.
            val delay = endTime - now
            val runnable = Runnable {
                if (currentForegroundPackage == pkg) {
                    val stillEnd = Prefs.getEndTime(applicationContext, pkg)
                    if (System.currentTimeMillis() >= stillEnd) {
                        Prefs.clearEndTime(applicationContext, pkg)
                        launchPrompt(pkg)
                    }
                }
            }
            pendingCheckRunnable = runnable
            handler.postDelayed(runnable, delay)
        }
    }

    private fun launchPrompt(targetPackage: String?) {
        if (!Settings.canDrawOverlays(this)) {
            toast("Can't show prompt: grant \"Display over other apps\"")
            return
        }
        val intent = Intent(this, PromptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (targetPackage != null) putExtra(PromptActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }
        startActivity(intent)
    }

    private fun toast(message: String) {
        if (!debug) return
        handler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // receiver was already unregistered / never registered - ignore
        }
    }
}
