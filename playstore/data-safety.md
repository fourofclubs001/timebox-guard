# Data safety form answers

**Play Console → Policy and programmes → App content → Data safety.**

Timebox Guard collects nothing off-device. Answers:

## Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A (no data leaves the device) — but answer **Yes** if the form forces a choice; there is no transit. |
| Do you provide a way for users to request that their data be deleted? | **Yes** — "Clear all usage data" in the app, and uninstalling removes everything. |

Because you select "No" to collection/sharing, most of the form collapses.
If Google's wording pushes you to declare the on-device usage log, describe
it as:

- **Data type:** App activity → "App interactions" / other user-generated
  content (the reasons you type, session times).
- **Collected:** Yes. **Shared:** No.
- **Processed ephemerally:** No (it persists locally until you clear it).
- **Required or optional:** Required for the app to function.
- **Purpose:** App functionality only.
- **Is this data transferred off the device?** No.

## Notes to keep the declaration true

- No SDKs. `./gradlew :app:dependencies` shows only `androidx.core` and
  `androidx.appcompat`.
- No `INTERNET` permission is requested — verify with
  `aapt dump permissions app-release.apk`. If `INTERNET` ever appears
  (a library pulled it in), the "no data leaves the device" claims must be
  revisited.
