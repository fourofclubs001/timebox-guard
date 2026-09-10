# Publishing Timebox Guard to Google Play

Everything in this folder is prep material. What's **done in the repo** and
what **needs you** is spelled out below.

---

## Done in the repo (v1.0, versionCode 13)

- **Release build config** — `app/build.gradle.kts` now has a `release`
  build type with R8 (`isMinifyEnabled`) + resource shrinking, ProGuard
  keep rules (`app/proguard-rules.pro`), a real `versionName` (`1.0`), and a
  signing config that reads `keystore.properties`.
- **Upload keystore generated** — `timebox-upload.jks` in the project root
  (gitignored), with `keystore.properties` beside it (also gitignored).
  **⚠️ Back both up somewhere safe — losing them means you can't update the
  app** (though Play App Signing lets Google reset a lost *upload* key).
- **Debug cruft removed** — `AppMonitorService.debug = false`, no more
  on-screen toasts.
- **Permissions minimised** — dropped the unused `RECEIVE_BOOT_COMPLETED`;
  the only real permission is `SYSTEM_ALERT_WINDOW`. `allowBackup="false"`
  so the usage log isn't cloud-backed.
- **Prominent disclosure** for the accessibility service —
  `SettingsActivity.showAccessibilityDisclosure()`, shown before the user is
  sent to enable it. Service `android:description` rewritten to match.
- **Store assets** — `playstore/assets/` has a 512 icon, a 1024×500 feature
  graphic, and an adaptive-icon foreground, plus `gen_assets.py` that
  produced them (tweak + rerun with `python3` if you want). The in-app
  launcher icon was updated to the same hourglass mark.
- **Privacy policy** — `PRIVACY.md` and `docs/privacy.html`.
- **Listing copy / form answers** — the other files in this folder.
- **Verified locally:** `./gradlew :app:bundleRelease` produces a signed
  `app-release.aab`; the APK has no `INTERNET` permission.

---

## Your actions

### 1. Fill in the placeholders

- `PRIVACY.md` and `docs/privacy.html` — replace `<YOUR-CONTACT-EMAIL>`.
- `playstore/store-listing.md` — same, and review the description wording.

### 2. Host the privacy policy (needs a public URL)

Easiest: **GitHub Pages** from this repo.
1. Push the repo to GitHub (public or private both work for Pages on a
   free account? — private needs Pro; use public).
2. Repo → Settings → Pages → Source: `main` / `/docs`.
3. Your URL becomes `https://<user>.github.io/timebox-guard/privacy.html`.

### 3. Create a Google Play developer account

- https://play.google.com/console — **US$25 one-time**, plus **identity
  verification** (a few days).
- **New personal accounts** must then run a **closed test with ≥ 12 testers
  for ≥ 14 continuous days** before they can request production access.
  Plan for this — set up an "Alpha" track and recruit 12 people early.

### 4. Build the thing you upload

From the project root, with `keystore.properties` present:

```
./gradlew clean :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab` — this is what
you upload. (The first run on a networked machine downloads the lint
artifacts this sandbox couldn't; that's expected.)

### 5. Create the app in Play Console

- App name **Timebox Guard**, type **App**, **Free**.
- **Enrol in Play App Signing** when prompted (recommended — accept the
  default where Google holds the app signing key and you keep the upload
  key).

### 6. Fill in "App content" (left nav → Policy → App content)

Work through each section using the files here:

| Section | File |
|---|---|
| Privacy policy | URL from step 2 |
| Accessibility API declaration | `accessibility-declaration.md` |
| Data safety | `data-safety.md` |
| Content ratings | `content-rating.md` |
| Target audience | `content-rating.md` (bottom) |
| Ads | No |
| Government apps, financial features, health | No / N/A |

### 7. Record the demo video

Google's accessibility review wants it. Shot list is in
`accessibility-declaration.md §5`. Upload to YouTube **unlisted**, paste the
link into the declaration.

### 8. Store listing

Paste from `store-listing.md`. Upload:
- **App icon** — `playstore/assets/ic_play_store_512.png`
- **Feature graphic** — `playstore/assets/feature_graphic_1024x500.png`
- **Phone screenshots** — take 4+ on a device/emulator: the dashboard, a
  day with a chart bar, the time+reason prompt, the settings screen. Min
  1080 px on the short edge.

### 9. Release

- Start on the **Closed testing (Alpha)** track (also satisfies the 12/14
  requirement for new accounts).
- After the testing period, promote to **Production**. First review with an
  accessibility declaration typically takes several days to a couple of
  weeks.

---

## If the accessibility declaration is rejected

See `accessibility-declaration.md` — reply with the written justification
and video first. The fallback is a `UsageStatsManager` detection path
(no accessibility service, slightly slower prompt); ask and it can be built
on a branch.

## Regenerating / rotating the keystore

If you'd rather own a keystore you created yourself:

```
keytool -genkeypair -v -keystore timebox-upload.jks -alias timebox \
  -keyalg RSA -keysize 4096 -validity 10000
```

then update `keystore.properties` with your passwords. Do this **before**
the first upload — after that the upload key is registered with Google and
changing it means a key reset request.
