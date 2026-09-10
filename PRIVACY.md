# Timebox Guard — Privacy Policy

_Last updated: 2026-09-09_

Timebox Guard is a personal digital-wellbeing app. It is designed so that
**your data never leaves your device.**

## What the app accesses

- **The package name of the app currently in the foreground**, via Android's
  Accessibility Service. This is used only to decide whether to show the
  time-and-reason prompt and to measure how long you spend in apps you have
  chosen to guard. The app does **not** read screen contents, the text you
  type, notifications, passwords, or any other on-screen information.
- **The list of apps installed on your device**, so you can choose which
  ones to guard. This list is shown only on the settings screen and is not
  stored or transmitted.
- **The reason text and planned duration you enter** in the prompt, plus a
  timestamped record of each time you start a session or tap "Close app
  instead." This is what the Usage Metrics screen displays.

## Where the data goes

Nowhere. All of the above is kept in the app's private local storage
(Android `SharedPreferences`) on your device. Specifically:

- There is **no account, no login, and no server.** The app has no code that
  makes network requests.
- **No analytics, advertising, crash-reporting or tracking SDKs** are
  included.
- Nothing is shared with the developer or any third party.
- Automatic cloud backup is disabled (`allowBackup="false"`), so the usage
  log is not copied to Google Drive.

## Retention and deletion

- The usage log is capped and old entries are discarded automatically.
- You can erase all logged data at any time with **Clear all usage data** on
  the Usage Metrics screen.
- Uninstalling the app deletes all of its data.

## Permissions

| Permission | Why |
|---|---|
| Accessibility Service | Detect which app is in the foreground (see above). |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Draw the blocking prompt on top of the guarded app. |
| Query installed apps | Populate the "choose apps to guard" list. |

You can revoke the Accessibility Service and the overlay permission at any
time in Android Settings; the app will simply stop guarding apps.

## Children

The app is not directed at children and collects no personal information
from anyone.

## Changes

If this policy changes, the updated version will be published at the same
URL with a new "last updated" date.

## Contact

Questions about this policy: **<YOUR-CONTACT-EMAIL>**
