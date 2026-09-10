# Accessibility API — Play Console declaration

Apps that use `AccessibilityService` for anything other than assisting users
with disabilities must declare it and pass a manual policy review. This file
has everything you need to fill in and to answer a reviewer.

References:
- Policy: https://support.google.com/googleplay/android-developer/answer/10964491
- `IsAccessibilityTool`: **do NOT declare true** — Timebox Guard is not a
  screen-reader-class tool. Leave it unset (it already is).

---

## Where to fill this in

**Play Console → Policy and programmes → App content → "Permissions and
APIs that access sensitive information" → Accessibility API.**

You will be asked to describe the use and provide a video.

---

## 1. Which functionality uses the Accessibility API?

```
Detecting which application is currently in the foreground.
```

## 2. Describe how your app uses the Accessibility API (verbatim answer)

```
Timebox Guard is a digital-wellbeing / self-control app. Its core feature is
a friction prompt: before the user opens an app they have chosen to "guard",
Timebox Guard asks them to enter how long they intend to use it and why, and
re-shows that prompt when the time expires.

To do this the app must know, in real time, which app the user has just
brought to the foreground. It registers an AccessibilityService that listens
only for TYPE_WINDOW_STATE_CHANGED events and reads a single field from
them: event.getPackageName(). It compares that package name against the
user's guarded list and, on a match with no active session, shows the
prompt as an overlay.

The same signal is used to measure how long a guarded app stays in the
foreground, which the app's Usage Metrics screen reports back to the user.

The service sets canRetrieveWindowContent="false". It does not read window
content, view text, typed input, notifications or any other on-screen
information, and it takes no action on behalf of the user. No data collected
via the service leaves the device; the app contains no networking code and
no analytics.

We considered UsageStatsManager as an alternative. It cannot deliver a
foreground-change event promptly enough for the prompt to appear before the
guarded app becomes interactive, which is essential to the feature, so the
AccessibilityService approach is required.
```

## 3. Is there an alternative to using the Accessibility API? Why is it not sufficient?

```
UsageStatsManager (PACKAGE_USAGE_STATS) can report the most-recently-used
app but only on a poll, with multi-second latency and no push event. The
guarded app would be fully visible and interactive before the prompt could
appear, defeating the purpose. The Accessibility API is the only mechanism
that provides an immediate foreground-change callback.
```

## 4. Prominent disclosure

Google requires an in-app disclosure shown *before* the user enables the
service, with an affirmative action to proceed. This is implemented in
`SettingsActivity.showAccessibilityDisclosure()` — a dialog titled
"Before you enable the service" with **Continue** / **Cancel**. Its text:

> Timebox Guard uses Android's Accessibility Service for one thing: to detect
> which app you've just opened, so it can show the time-and-reason prompt for
> apps you've chosen to guard and measure how long you spend in them.
>
> • It reads only the package name of the app in the foreground — not screen
>   contents, text you type, or passwords.
> • All of it stays on this device. Nothing is sent anywhere, and there are
>   no analytics or ads.
> • You can turn the service off at any time in Android Settings ›
>   Accessibility.
>
> Tap Continue to open Accessibility settings and turn Timebox Guard on.

The `android:description` on the service (shown in the system Accessibility
screen) carries the same explanation — see
`res/values/strings.xml → accessibility_service_description`.

## 5. Demonstration video

Google wants a short (≈30–60 s), unlisted **YouTube** video showing the
feature end to end. Suggested shot list:

1. Open Timebox Guard → the Usage Metrics dashboard.
2. Tap **Settings**, tap **Enable Accessibility Service** → the prominent
   disclosure dialog appears. Read it on screen, tap **Continue**.
3. In Android Accessibility settings, turn Timebox Guard on.
4. Add e.g. Instagram to the guarded list.
5. Open Instagram → the time + reason prompt appears on top. Enter "5" and a
   reason, tap **Start session**.
6. Show Instagram opening; switch away and back to show it isn't re-prompted
   within the window.
7. Open Timebox Guard again → show the new session on the dashboard (chart
   bar, reason listed).
8. Open Instagram once more, this time tap **Close app instead** → you land
   on the home screen; dashboard shows the "backed out" count go up.

Put the URL in the declaration form.

## 6. Category / labelling in Console

- Accessibility functionality: **not** an accessibility tool for people with
  disabilities — it's a productivity/self-control feature.
- App category: **Productivity**.
- If asked "does your app disclose accessibility use in the store listing?":
  yes — the full description's "HOW IT WORKS" paragraph covers it.

---

## If it still gets rejected

1. Reply to the rejection with the section 2–3 text above and the video.
2. If upheld, the fallback is the `UsageStatsManager` detection path (a
   branch can be prepared) — slightly slower prompt, but no accessibility
   review. The prompt overlay and everything else stay the same.
