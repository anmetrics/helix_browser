# Helix Browser — Android

WebView-based Android browser. Min SDK 24 (Android 7), target SDK 35 (Android 15).

## Build

```sh
# Debug APK (no signing required)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (requires signing config — see below)
./gradlew assembleRelease
```

## Tests & lint

```sh
./gradlew testDebugUnitTest      # 29 JVM unit tests via Robolectric
./gradlew lintDebug              # 0 errors, ~240 cosmetic warnings
```

## Release signing

Two paths — pick one:

**Local development:** copy `keystore.properties.sample` to `keystore.properties`,
fill in keystore path + passwords. The file is `.gitignore`d.

**CI / production:** set environment variables —

```sh
export HELIX_KEYSTORE_FILE=/path/to/helix-release.jks
export HELIX_KEYSTORE_PASSWORD=...
export HELIX_KEY_ALIAS=helix
export HELIX_KEY_PASSWORD=...
./gradlew bundleRelease
```

Generate the keystore once with:

```sh
keytool -genkey -v -keystore helix-release.jks \
        -keyalg RSA -keysize 2048 -validity 10000 -alias helix
```

## Architecture

| Layer        | Location                                             |
|--------------|------------------------------------------------------|
| UI           | `ui/` Activities + `ui/adapter/` RecyclerView        |
| Web engine   | `engine/` HelixWebView / HelixWebViewClient / Privacy|
| Data         | `data/` Room (bookmarks, history) + repositories     |
| Tabs         | `tabs/` TabManager (in-memory) + persistence         |
| Billing      | `billing/` Google Play subscription                  |
| Crash log    | `HelixCrashHandler` → `filesDir/crashlogs/`          |

State management is LiveData; coroutines for all DB I/O.

## Production-readiness checklist

- [x] Security hardening (M1) — file access disabled, geolocation/cam/mic prompts, ProGuard, signing
- [x] Stability (M2) — leak-safe WebView destroy, tab callback race fix, Room migrations
- [x] Privacy UX (M3) — HTTPS-Only, Site Permissions UI, anti-fingerprinting
- [x] Core features (M4) — Reader mode, PiP, PWA install, voice search, save MHTML, find counter, bookmark HTML export/import
- [x] Crash handler (local disk log)
- [x] Edge-to-edge + safe-area insets
- [x] ProGuard/R8 verified — release build succeeds with shrinking
- [x] Lint clean (no errors)
- [x] 29 unit tests passing
- [x] CI workflow (.github/workflows/android.yml)
- [ ] Crashlytics / Sentry remote reporting — deferred
- [ ] Hilt DI migration — deferred
- [ ] UI / Espresso tests — deferred
- [ ] Baseline Profile — deferred

## Crash reports

Local crash logs land in `filesDir/crashlogs/` (max 5, rotated). Pull via:

```sh
adb shell run-as com.helix.browser.debug ls files/crashlogs/
adb shell run-as com.helix.browser.debug cat files/crashlogs/crash-*.txt
```

## Notes

- We intentionally keep `usesCleartextTraffic="true"` at the manifest level
  because this is a browser; HTTP/HTTPS policy is enforced per-WebView via
  `MIXED_CONTENT_NEVER_ALLOW` + the user-facing HTTPS-Only mode toggle.
- `network_security_config.xml` trusts **system CAs only**, not user-installed
  certificates, to prevent silent MITM via rogue corporate proxies.
- Anti-fingerprinting JS is injected on every page when the user toggles it
  (canvas/audio/WebGL noise, WebRTC IP scrub, battery/hardware spoof).
