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
 *  3. If it has an active session, it schedules a check for when that
 *     session's time runs out, and re-blocks then.
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
    /** Package the overlay is currently blocking, if any. */
    private var overlayTargetPackage: String? = null

    /** Home / launcher package names, treated as a real foreground app. */
    private val launcherPackages: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

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

        // Inflate the prompt now, while nothing is waiting on it. Doing it
        // lazily on the first guarded-app switch is slow enough (the
        // NumberPicker) that the app shows through before the prompt lands.
        overlay.prewarm()

        toast("Timebox Guard service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return // our own overlay / setup UI
        // Ignore transient system windows (system UI, the IME, edge panels,
        // pop-up toasts...): only real launchable apps count as a foreground
        // switch. Without this, those windows make the overlay hide itself
        // and the service re-trigger, causing a flicker loop.
        if (!isForegroundApp(pkg)) return

        // While the overlay is up, the guarded app is still the real
        // foreground app underneath. Ignore its own redraws and any stray
        // launcher blips so the overlay stays put; only a switch to a
        // genuinely different app takes it down.
        if (overlay.isShowing) {
            if (pkg == overlayTargetPackage || pkg in launcherPackages) return
            overlay.hide()
            overlayTargetPackage = null
        }

        if (pkg == currentForegroundPackage) return // no actual app switch
        currentForegroundPackage = pkg
        cancelPendingCheck()

        if (!Prefs.isMonitored(applicationContext, pkg)) return

        toast("Guarded app in front: $pkg")

        val endTime = Prefs.getEndTime(applicationContext, pkg)
        if (endTime <= 0L || System.currentTimeMillis() >= endTime) {
            Prefs.clearEndTime(applicationContext, pkg)
            showPrompt(pkg)
        } else {
            scheduleExpiryCheck(pkg, endTime)
        }
    }

    private fun isForegroundApp(pkg: String): Boolean =
        pkg in launcherPackages || packageManager.getLaunchIntentForPackage(pkg) != null

    private fun showPrompt(targetPackage: String?) {
        if (!Settings.canDrawOverlays(this)) {
            toastAlways("Timebox Guard: grant \"Display over other apps\" to block apps")
            return
        }
        if (overlay.isShowing) return
        overlayTargetPackage = targetPackage
        overlay.show(targetPackage) { result ->
            overlayTargetPackage = null
            if (result.started && targetPackage != null) {
                // The guarded app is now the foreground app but no window
                // event will fire for it, so arm the expiry check here.
                scheduleExpiryCheck(targetPackage, result.endTime)
            } else {
                cancelPendingCheck()
            }
        }
    }

    /** Re-block [pkg] the moment its session runs out, if still in use then. */
    private fun scheduleExpiryCheck(pkg: String, endTime: Long) {
        cancelPendingCheck()
        val delay = endTime - System.currentTimeMillis()
        if (delay <= 0L) return
        val runnable = Runnable {
            if (currentForegroundPackage == pkg &&
                System.currentTimeMillis() >= Prefs.getEndTime(applicationContext, pkg)
            ) {
                Prefs.clearEndTime(applicationContext, pkg)
                showPrompt(pkg)
            }
        }
        pendingCheckRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun cancelPendingCheck() {
        pendingCheckRunnable?.let { handler.removeCallbacks(it) }
        pendingCheckRunnable = null
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
