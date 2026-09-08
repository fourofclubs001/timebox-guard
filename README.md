# Timebox Guard

A small Android app that makes you state a planned time and a reason before
opening apps you choose to limit, and re-blocks the screen when that time is
up. Optionally shows the same prompt whenever you unlock your phone.

## How it works

- **MainActivity** — setup screen. Turn on the accessibility service, pick
  which installed apps are "guarded", and optionally enable the unlock
  prompt.
- **AppMonitorService** (an `AccessibilityService`) — runs in the background
  watching which app is in the foreground.
  - When a guarded app comes to the foreground with no active session (or an
    expired one), it launches the blocking prompt.
  - When a guarded app has an active session, it schedules a check for the
    exact moment the time runs out and re-blocks then if you're still in
    that app.
  - It also listens for `ACTION_USER_PRESENT` (phone unlocked) and, if you've
    enabled that option, shows the same prompt right after unlock.
- **PromptActivity** — the full-screen blocking UI: "planned minutes" +
  "reason" fields, a **Start session** button, and a **Close app instead**
  button (present from the very first time it's shown, not just after time
  runs out). The back button is disabled so it can't be swiped away.

Sessions are tracked per package name using an end-timestamp in
SharedPreferences — no foreground service or notification needed.

## Building it

1. Open this folder (`TimeboxApp/`) in Android Studio (Koala or newer).
2. Let Gradle sync — it needs internet access to Google's Maven repo the
   first time.
3. Run on a device or emulator (minSdk 26 / Android 8.0+).

I generated the source but couldn't compile an APK in this sandbox (no
access to Android's SDK/Maven servers here), so please build it in Android
Studio.

## First-run setup on the phone

1. Open the app, tap **Enable Accessibility Service**, find "Timebox Guard"
   in the list, and turn it on. This is what lets the app detect when a
   guarded app opens — Android requires this to be granted manually in
   Settings, it can't be requested as a normal permission dialog.
2. Check the apps you want guarded in the list.
3. Optionally flip on "ask for time + reason when I unlock my phone."

## Known limitations worth knowing about

- **"Close app instead" sends you to the home screen** rather than force-
  killing the target app. Since Android 5, apps aren't allowed to kill other
  apps' processes for security reasons — this is a platform restriction, not
  something fixable in app code. In practice this still stops you from using
  it in that moment, which is the main goal.
- **Accessibility service reliability**: some phone manufacturers (Xiaomi,
  Huawei, OnePlus, etc.) aggressively kill background services to save
  battery. If the prompt stops appearing after a while, add Timebox Guard to
  your phone's battery-optimization allowlist.
- **Google Play policy**: apps that use the accessibility API for purposes
  other than accessibility (like this one) need to declare that clearly in
  the Play Console and are reviewed more strictly. Fine for personal use /
  sideloading; worth knowing if you ever plan to publish it.
- The "reason" text you type isn't stored anywhere long-term right now — it's
  just required as a friction step before the timer starts. Easy to add a
  log (e.g. a simple list screen backed by a Room database) if you want a
  history of why/when you opened each app.
