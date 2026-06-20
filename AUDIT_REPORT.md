# Helix Browser — Cross-Platform Production Audit

_Multi-agent audit: 92 agents, 210 confirmed findings (5 refuted via adversarial verification). Generated 2026-06-20._

## Severity summary

| Platform | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Android (Kotlin) | 0 | 5 | 29 | 11 | 45 |
| iOS (Swift/UIKit) | 0 | 3 | 36 | 21 | 60 |
| macOS (SwiftUI) | 1 | 2 | 24 | 9 | 36 |
| Linux (Python) | 0 | 7 | 20 | 5 | 32 |
| Windows (C#/WinUI) | 0 | 5 | 23 | 9 | 37 |
| **All** | **1** | **22** | **132** | **55** | **210** |

## Cross-platform feature parity

Across the five Helix Browser implementations, macOS and Android are the most complete; Android is the clear reference build (it is the only platform with a genuine site-permissions manager, reader mode, downloads, zoom, voice search, bookmark HTML import/export, print/PWA/PiP, and a real per-block tracker counter). macOS leads on advanced tab ops, tab groups, and keyboard/menu integration. Linux and Windows are thin shells with strong privacy/ad-blocking but large backend-only or missing surfaces (Linux History/Bookmarks/Downloads have DB layers but empty UI stubs; Windows has no Settings UI at all, no history recording, no downloads, and several dead privacy prefs). The two priority platforms diverge sharply: Android is broad but has several no-op settings switches (javascript_enabled, desktop_mode), an unimplemented dark-mode toggle, weak accessibility, and a premium feature with no real entry point. iOS is the weakest of the two priority targets — it is entirely missing find-in-page, reader mode, site permissions, zoom (pref-only), localization, and billing, and has multiple wired-but-dead features (Downloads unreachable, trackers counter never increments, advanced tab ops with no UI, third-party-cookie toggle never enforced). To make Android and iOS best-in-class, the critical work is: ship find-in-page, site permissions, and functioning downloads on iOS; complete reader mode, zoom, suggestions, and localization on iOS; and on both platforms wire up real dark-mode theming, fix no-op settings switches, add real search suggestions (bookmarks + live autocomplete), and invest in accessibility. Universally weak areas (no platform is full) include search suggestions/autocomplete, true theming with a light/dark toggle, accessibility, and billing/premium.

| Feature | Android | iOS | macOS | Linux | Windows |
|---|---|---|---|---|---|
| Tab management (CRUD: create/close/switch) | full | full | full | partial | partial |
| Advanced tab ops (pin/mute/duplicate/close-others/close-to-right) | partial | stub | full | stub | stub |
| Tab groups | stub | stub | full | partial | none |
| Tab switcher / strip UI | full | full | full | partial | partial |
| Tab thumbnails | full | stub | none | none | none |
| Tab session restore | full | full | full | full | full |
| Tab suspension (memory mgmt) | partial | stub | partial | stub | none |
| Navigation (back/forward/reload/stop/home) | full | full | full | full | partial |
| Navigation gestures (edge-swipe / back-forward) | full | full | full | none | none |
| Address bar URL/search routing | full | full | full | full | full |
| Search engine selection | full | full | full | full | partial |
| Voice search | full | none | none | none | none |
| Search suggestions / autocomplete | partial | none | none | none | none |
| History (record + viewer) | full | partial | full | partial | stub |
| Bookmarks (record + viewer) | full | partial | full | partial | partial |
| Bookmarks import/export (HTML) | full | none | none | none | none |
| Downloads | full | stub | partial | stub | none |
| Find in page | full | none | partial | full | partial |
| Reader mode | partial | none | none | none | none |
| Zoom (text/page) | full | stub | partial | none | none |
| Desktop mode (UA toggle) | partial | partial | full | partial | none |
| Incognito / private mode | full | full | full | full | partial |
| Ad / tracker blocking (network) | full | full | full | full | partial |
| Ad / tracker blocking (JS cosmetic + YouTube) | full | full | full | full | partial |
| Trackers-blocked counter | full | stub | stub | none | none |
| Anti-fingerprinting | full | full | full | full | full |
| Do Not Track | full | full | full | full | stub |
| HTTPS upgrade / HTTPS-only | full | full | full | full | none |
| Third-party cookie blocking | full | stub | stub | stub | none |
| Popup blocking | full | full | full | full | stub |
| Privacy / data clearing (cookies/cache/all) | full | partial | full | partial | none |
| Site permissions (camera/mic/geo) + manager | full | none | none | none | none |
| Settings UI | partial | full | full | partial | none |
| Start / home page | full | full | full | full | full |
| Sharing | full | full | none | none | none |
| Print / Save-as / Add-to-home (PWA) / PiP | full | none | none | none | none |
| Long-press / context menu (link/image) | full | none | full | none | none |
| JS dialogs (alert/confirm/prompt) | full | full | full | full | partial |
| Fullscreen video / file chooser | full | partial | partial | partial | partial |
| SSL errors / error pages / safe browsing | full | full | full | partial | partial |
| Billing / premium | partial | none | none | none | none |
| i18n / localization | partial | none | none | none | none |
| Accessibility | partial | stub | none | none | none |
| Theming / dark mode | partial | partial | partial | partial | partial |
| Keyboard shortcuts / menu bar | none | none | full | partial | partial |

### Priority parity gaps

- **[CRITICAL] Find in page** — missing on ios. iOS has NO find-in-page at all (no find bar, no WKWebView.find(_:) usage). This is table-stakes for a browser. Add a find bar wired to WKWebView.find(_:configuration:) (iOS 16+) with next/prev and a match counter, mirroring Linux's complete FindController implementation. Android is already the reference (live findAllAsync + active/total counter).
- **[CRITICAL] Site permissions (camera/mic/geolocation) + per-site manager** — missing on ios. iOS has no permission prompt handling, no per-site store, and no UI (Info.plist usage strings only). Port Android's model: WKUIDelegate media-capture + CLLocationManager bridging, per-origin allow/deny persisted in UserDefaults, and a permissions management screen. Android is best-in-class here (per-origin prompts + SitePermissionsActivity); iOS relies on raw WKWebView defaults.
- **[CRITICAL] Downloads** — missing on ios. iOS Downloads is entirely dead: full UI + DataManager CRUD exist but no WKDownloadDelegate, nothing calls addDownload, and DownloadsViewController is never presented. Wire WKDownloadDelegate to capture downloads, call the existing addDownload on completion, and add a menu entry to present the downloads list. Android's system-DownloadManager flow is the reference.
- **[HIGH] Reader mode** — missing on ios. iOS has no reader mode at all. Android already ships a heuristic extractor; port that (ideally upgraded to Readability.js for quality) to iOS as an injected script + styled overlay with an enter/exit toggle. This is a marquee privacy/readability feature users expect.
- **[HIGH] Zoom (text/page)** — missing on ios. iOS zoom is a stub: only a Prefs.defaultZoom value that is never read or applied. Add pinch + explicit zoom UI applying WKWebView page zoom (or viewport meta injection), persisting per-session like Android's 50-200% textZoom. Android is the reference; macOS is partial (uses fragile document.body.style.zoom).
- **[HIGH] Search suggestions / autocomplete** — missing on android, ios. iOS has nothing; Android is only partial (HISTORY-ONLY, no live search-engine autocomplete and no bookmark suggestions). No platform has full suggestions, so this is a green-field parity opportunity. For iOS add a suggestions dropdown (history + bookmarks + remote suggest API); for Android extend the existing dropdown to include bookmark matches and live search-engine completions.
- **[HIGH] i18n / localization** — missing on ios. iOS is single-language hardcoded Vietnamese (no .strings/.lproj, CFBundleDevelopmentRegion=vi, no NSLocalizedString). Introduce Localizable.strings and route all UI strings through NSLocalizedString. Android already has the in-app locale framework (14-language picker via LocaleHelper) and is the model; iOS should reach at least functional multi-language parity. Note Android's own gap: many non-vi locales ship only ~38 of ~207 strings, so Android needs translation completion too.
- **[MEDIUM] Theming / dark mode (toggle + web-content darkening)** — missing on android, ios. No platform has a true theming feature. Android follows system DayNight but has NO in-app toggle, NO values-night override, and HelixWebView.setNightMode is never called (dark mode effectively unimplemented as a user feature). iOS is dark-only (forced overrideUserInterfaceStyle=.dark, no light theme/switcher). Add an explicit Light/Dark/System toggle plus web-content dark mode; on Android actually call setNightMode and add values-night, on iOS add a light palette and theme selector.
- **[MEDIUM] Billing / premium** — missing on ios. iOS has no StoreKit/paywall/premium gating at all. Android has the full Play Billing flow but it is only partial: the sole entry point is the tab-switcher banner and premium gates nothing else. For iOS add a StoreKit 2 subscription mirroring Android's product; for Android add a premium entry point in Settings/Main and make premium actually gate features. Android is the closest to a reference but still incomplete.
- **[MEDIUM] Accessibility** — missing on android, ios. iOS accessibility is a stub (no accessibilityLabels/Dynamic Type/VoiceOver; the only accessibility* usage is a hack stashing URLs). Android is partial (contentDescription on ~half of layouts, supportsRtl, but no font-scale handling and many unlabeled controls). Add VoiceOver labels + Dynamic Type on iOS, complete contentDescription coverage and font-scale handling on Android, and add an accessibility settings section. No platform does this well.
- **[MEDIUM] Functional/no-op settings switches** — missing on android, ios. Android has dead prefs: 'javascript_enabled' (HelixWebView hardcodes JS on) and 'desktop_mode' (nothing reads Prefs.isDesktopMode) are no-ops; the notifications site-permission label never requests anything. iOS Settings has several non-functional toggles (third-party cookies never consumed, mute does not mute, zoom never applied). Audit and either wire each toggle to real behavior or remove it so settings do not silently lie to users.
- **[MEDIUM] Third-party cookie blocking (real enforcement)** — missing on ios. iOS has the pref + toggle but the value is never consumed (no WKWebsiteDataStore cookie policy). Enforce it via WKWebsiteDataStore/cookie policy. Android actually applies a third-party cookie policy per-WebView and is the reference; macOS and Linux are also only stubs here, but iOS is a priority platform.
- **[LOW] Trackers-blocked counter (real increments)** — missing on ios. iOS displays the trackers-blocked counter on the start page and Settings but nothing ever increments it (content-rule-list blocks have no JS callback), so it always shows 0 — misleading. Increment it from the JS tracker-interception scripts (postMessage back to native) the way Android's persisted per-block counter works. Android is the only platform that does this correctly.
- **[LOW] Advanced tab ops UI (pin/mute/duplicate/groups)** — missing on ios. On iOS the TabManager methods (pin/mute/duplicate/closeOthers/closeToRight/createTabGroup) exist but NO UI ever invokes them and mute does not actually mute the WKWebView. Add long-press/context menus in the tab switcher to surface them (Android surfaces pin/close-others/close-right via long-press; macOS exposes the full set via context menu and is the reference).
- **[LOW] Link/image long-press context menu** — missing on ios. iOS lacks a custom long-press context menu for links/images (open in new/incognito tab, copy, save image, share). Android implements this via HitTestResult and is the reference; add a WKUIDelegate contextMenuConfiguration on iOS.


## Android (Kotlin) — 45 findings

#### 1. [HIGH] External-app schemes (intent:, tel:, mailto:, market:, sms:) are never launched
_Navigation / External intents · Browsing Feature Completeness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebViewClient.kt  (lines 184-198)`  

shouldOverrideUrlLoading handles only http/https/about/data/blob. For `intent` and `market` it returns true (consuming the navigation) but never builds or starts an Intent — so tapping a deep link (intent://…#Intent;…end), a Play Store link (market://), or a custom-scheme app link does nothing. For `tel`, `mailto`, `sms`, etc. it returns false, which tells WebView to load the URL itself; WebView cannot load these schemes and shows an error / net::ERR_UNKNOWN_URL_SCHEME page instead of opening the dialer/mail app. A production browser must dispatch these to the OS via Intent.parseUri / ACTION_VIEW. There is no Intent.parseUri or ACTION_VIEW dispatch anywhere in the engine.

```
```
return when (url.toUri().scheme) {
    "http", "https", "about", "data", "blob" -> { shouldOverrideUrl?.invoke(url) ?: false }
    "intent", "market" -> { /* comment: handled externally */ true }   // nothing started
    else -> { /* Tel, mailto, etc. */ false }   // WebView errors out
}
```
Grep confirms no Intent.parseUri / startActivity for tel/mailto/intent anywhere under engine/.
```

**Fix:** For non-web schemes, build an Intent (Intent.parseUri(url, Intent.URI_INTENT_SCHEME) for intent:, ACTION_VIEW for tel/mailto/sms/market), set FLAG_ACTIVITY_NEW_TASK, verify resolveActivity != null, startActivity, and return true. Handle the intent: scheme's browser_fallback_url. Catch ActivityNotFoundException to show a toast.
**Verifier note:** In shouldOverrideUrlLoading, replace the no-op intent/market/else branches with real OS dispatch. For the intent: scheme, use Intent.parseUri(url, Intent.URI_INTENT_SCHEME); for tel/mailto/sms/market and other non-web schemes, build Intent(ACTION_VIEW, request.url). Add FLAG_ACTIVITY_NEW_TASK (WebView's context may not be an Activity), and before launching either check packageManager.resolveActivity(intent, 0) != null or wrap startActivity in try/catch for ActivityNotFoundException. On the intent: scheme specifically, honor the browser_fallback_url extra (intent.getStringExtra("browser_fallback_url")) by loading it in the WebView when no app resolves; otherwise show a toast/snackbar ("No app found to open this link"). Return true once handled. Also strip the misleading "handled externally" comment and either wire up the shouldOverrideUrl callback at the MainActivity call site (currently passed as null) or remove it. Consider a security guard: only auto-launch external apps on a user gesture (request.hasGesture()) to avoid drive-by intent launches from page scripts.

#### 2. [HIGH] JavaScript dialogs (alert/confirm/prompt) are suppressed, breaking sites that depend on them
_Navigation / Page interaction · Browsing Feature Completeness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebChromeClient.kt  (lines 73-81)`  

onJsAlert immediately calls result.confirm() and returns true (no dialog ever shown to the user), onJsConfirm immediately calls result.cancel() and returns true (every confirm() returns false to the page), and there is no onJsPrompt override (default cancels). This means JS alert() text is never displayed, confirm() always behaves as if the user clicked Cancel, and prompt() always returns null. Real sites break: 'Are you sure you want to leave this page?' (beforeunload/confirm), delete-confirmation flows, and many web apps that gate actions on confirm()/prompt(). This silently changes page behavior with no user awareness.

```
```
override fun onJsAlert(view, url, message, result): Boolean { result.confirm(); return true }
override fun onJsConfirm(view, url, message, result): Boolean { result.cancel(); return true }
// no onJsPrompt override
```
```

**Fix:** Show real Material AlertDialogs for onJsAlert (OK -> result.confirm()), onJsConfirm (OK -> confirm(), Cancel -> cancel()), and override onJsPrompt with an input field. Guard against showing dialogs when the activity is finishing/destroyed.
**Verifier note:** Replace the suppressing overrides with real dialogs. For onJsAlert: show a Material AlertDialog (or AppCompat AlertDialog using the WebView's context) with the message and a single OK button that calls result.confirm(); set a cancel listener that also calls result.confirm() so the JsResult is always resolved. For onJsConfirm: show a dialog with OK -> result.confirm() and Cancel/dismiss -> result.cancel(). Add an onJsPrompt override (JsPromptResult) with an EditText prefilled with defaultValue: OK -> result.confirm(input), Cancel/dismiss -> result.cancel(). Wire these through new optional constructor callbacks to MainActivity (mirroring the existing onShowFileChooser/onGeolocationPermission pattern) rather than building dialogs in the engine class, and obtain a valid Activity context. Critically, guard every dialog: if the activity isFinishing/isDestroyed (or the context is not an alive Activity), resolve the JsResult immediately (cancel) and return true instead of attempting to show a window, to avoid WindowManager BadTokenException and to ensure JS is never left hanging. Optionally consider Chrome-like hardening: suppress/throttle dialogs from background tabs and rate-limit repeated dialogs, but do not blanket-suppress top-level dialogs.

#### 3. [HIGH] Address-bar and voice searches ignore the user's selected search engine (hardcoded Google)
_correctness · Production Quality & Robustness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/utils/UrlUtils.kt  (lines 18-26)`  

The primary search path - typing a query in the address bar and pressing Go, and the voice-search result - routes through formatUrl(), which hard-codes "google" as the engine. A user who selects DuckDuckGo/Brave/Ecosia in Settings (a flagship feature for a 'private' browser) still has every typed search sent to Google. This is both a broken core feature and a privacy regression for the product's positioning. The `null ?:` is also dead code.

```
formatUrl(input): "else -> null ?: buildSearchQuery(trimmed, \"google\")". Callers: MainActivity.kt:248 (address-bar IME_ACTION_GO) and MainActivity.kt:90 (voice search) both call UrlUtils.formatUrl(input). Only handleIntent (line 193) passes Prefs.getSearchEngine(this).
```

**Fix:** Pass the selected engine into formatUrl, e.g. formatUrl(input, Prefs.getSearchEngine(this)) and forward it to buildSearchQuery; remove the dead `null ?:`. Update the address-bar and voice-search call sites.
**Verifier note:** Thread the user's selected engine into formatUrl and forward it to buildSearchQuery, then update all call sites. Concretely:

1. UrlUtils.kt: change the signature to `fun formatUrl(input: String, engine: String): String` and the search branch to `else -> buildSearchQuery(trimmed, engine)` (drop the dead `null ?:`).

2. MainActivity.kt:248 (address bar Go/Enter): `val url = UrlUtils.formatUrl(input, Prefs.getSearchEngine(this))`.

3. MainActivity.kt:90 (voice search): `val url = UrlUtils.formatUrl(query, Prefs.getSearchEngine(this))`.

4. Also fix the path the finding didn't list: MainActivity.kt:212 in sanitizeIncomingUrl — `null -> UrlUtils.formatUrl(trimmed, Prefs.getSearchEngine(this))` — so bare deep-link input honors the engine too.

Optionally keep a one-arg overload that defaults to Prefs.getSearchEngine, but since formatUrl is in a Context-free util object, passing the engine explicitly from each Context-holding call site (as handleIntent:193 already does) is cleaner. After the change, add a quick unit test asserting formatUrl("cats", "duckduckgo") yields the DuckDuckGo URL, to lock the behavior.

#### 4. [HIGH] Incognito 'clear' wipes ALL cookies globally and is dead code; incognito shares the global cookie/storage jar
_Incognito / private mode correctness · Security & Privacy · effort L_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt  (lines 133-143)`  

clearIncognitoData() calls CookieManager.getInstance().removeAllCookies(null) and WebStorage.getInstance().deleteAllData(), both of which are process-global. There is only one WebView cookie/storage jar; incognito tabs are not isolated into a separate profile. Two problems: (1) if this method were ever invoked it would destroy the regular (non-incognito) user's cookies and all sites' localStorage/IndexedDB, i.e. log the user out everywhere; (2) more importantly, a grep of the whole source tree shows clearIncognitoData() is never called anywhere, so cookies set during an incognito session are NOT purged when the incognito tab/app closes — they persist in the shared global cookie store. onDestroy() only calls tabManager.closeAllIncognito() (which just removes tab metadata) and never clears the incognito web data. Incognito therefore leaks cookies (and any DOM storage written before domStorageEnabled=false took effect) into normal browsing. setIncognitoMode() also only sets databaseEnabled/domStorageEnabled=false and cacheMode=LOAD_NO_CACHE on the WebView, but cookies still go to the global jar with no per-session isolation.

```
fun clearIncognitoData() {
    if (!incognito) return
    ...
    WebStorage.getInstance().deleteAllData()              // GLOBAL: affects all tabs
    CookieManager.getInstance().removeAllCookies(null)   // GLOBAL: wipes regular cookies too
}
// grep -rn clearIncognitoData -> only the definition; never invoked.
```

**Fix:** Isolate incognito into a separate profile/cookie jar (androidx.webkit Profile API where available, or a dedicated WebView data directory). When the last incognito tab/the app closes, delete only the incognito profile's cookies, cache, and DOM storage. Never call the global removeAllCookies()/WebStorage.deleteAllData() from an incognito-teardown path. Wire the teardown into onDestroy()/tab-close so incognito data is actually purged.
**Verifier note:** Isolate incognito into its own data store rather than sharing the global jar. Preferred: use the androidx.webkit Profile API (WebViewFeature.MULTI_PROFILE / ProfileStore) to create a dedicated 'incognito' profile and attach incognito WebViews to it; on last-incognito-tab-close and in onDestroy(), delete only that profile (ProfileStore.deleteProfile) so no regular data is touched. If Profile API is unavailable on target API levels, fall back to a separate process with WebView.setDataDirectorySuffix('incognito') so its cookies/cache/DOM storage live in a distinct directory that can be wiped wholesale.\n\nFix clearIncognitoData() so it is session-scoped, not global: stop calling the process-global CookieManager.removeAllCookies(null) and WebStorage.deleteAllData() from the incognito path (those nuke regular users' data); instead clear only the incognito profile/data-directory. Then actually wire the teardown in: invoke it from closeTab() when tab.isIncognito, from closeAllIncognito(), and from onDestroy() before destroying the WebViews — currently it is never called. Until proper isolation lands, at minimum delete the per-session incognito cookies by name/domain captured during the session, and add a regression test asserting that closing the last incognito tab removes incognito-set cookies while leaving non-incognito cookies intact.

#### 5. [HIGH] ~82% of user-facing strings are untranslated in 12 of 14 locales (only ~38 of 207 strings localized)
_i18n · Store Readiness, i18n & Accessibility · effort L_  
**Location:** `android/app/src/main/res/values-de/strings.xml  (lines 1-49)`  

The default locale defines 207 strings. Every translated locale except Vietnamese (123) contains only 38 strings — the basic nav labels. All Settings screen text, privacy/SSL warnings, the per-site permission dialogs ('X wants to access your location'), page-info security explanations, the custom-search-engine flow, zoom controls, and the premium upsell are English-only in German, Spanish, French, Portuguese, Russian, Japanese, Korean, Hindi, Indonesian, Arabic, Thai and Chinese. For an app marketed as supporting 14 languages this is a major localization gap: non-English users see a half-translated UI, including security-critical warnings shown in English they may not read. Lint MissingTranslation is explicitly disabled (build.gradle:81) so this is invisible at build time.

```
values/strings.xml: 207 <string> entries
values-de, values-es, values-fr, values-pt, values-ru, values-ja, values-ko, values-hi, values-id, values-ar, values-th, values-zh-rCN: 38 entries each
values-vi: 123 entries

Untranslated (fall back to English) include all Settings labels, SSL/security warnings, site-permission dialogs, page-info, custom search engine, zoom, premium banner, e.g.:
ssl_untrusted, ssl_message, permission_location_message, security_not_secure_message, clear_all_data_summary, premium_banner_title, page_info_not_secure_subtitle ...
```

**Fix:** Complete the translations for all 207 strings across the shipped locales (prioritize security/permission/SSL strings and Settings). Re-enable the MissingTranslation lint check (or a CI translation-coverage gate) so regressions are caught.
**Verifier note:** Prioritize by user-safety impact, not alphabetically. First, translate the security-decision strings across all 12 affected locales: the SSL cert-error dialog (ssl_title, ssl_message, ssl_untrusted, ssl_expired, ssl_id_mismatch, ssl_date_invalid, ssl_not_yet_valid, ssl_invalid, ssl_unknown, continue_button/go_back_button, ssl_error_page_*) and the runtime permission prompts (permission_location_title/message, permission_camera_*, permission_mic_*, permission_notifications_*, security_not_secure_*). These are wired into HelixWebViewClient.kt:134-139 and MainActivity.kt:954 and gate destructive user choices, so an English-only warning is a real security-comprehension risk. Second, complete the remaining Settings, page-info, site-permission, custom-search, zoom, and premium strings. Third, fix the false claim in build.gradle:79 — either actually add a translation-coverage gate to .github/workflows/android.yml (e.g. a script that diffs each values-*/strings.xml key set against values/ and fails on missing security/permission/SSL keys), or re-enable Android Lint MissingTranslation (drop it from the disable list at build.gradle:81; abortOnError is already true). Until a gate exists, the comment claiming CI tracks coverage is misleading and should be removed.

#### 6. [MEDIUM] Typed address-bar searches always use Google, ignoring the user's selected search engine
_Address bar / Search engines · Browsing Feature Completeness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/utils/UrlUtils.kt  (lines 18-26)`  

UrlUtils.formatUrl() hardcodes "google" when a typed query is not a URL: `else -> null ?: buildSearchQuery(trimmed, "google")`. formatUrl takes no Context and never reads Prefs.getSearchEngine(). The address bar's editor action (MainActivity setupAddressBar, line 248) and voice search (line 90) call formatUrl(input) directly. SettingsActivity lets the user pick Bing/DuckDuckGo/Yahoo/Yandex/Ecosia/custom and persists it under "search_engine", and getSearchEngine() reads it — but that preference is only consulted from the ACTION_WEB_SEARCH intent path (handleIntent). Every search the user types into the address bar still goes to Google, making the search-engine setting effectively dead for the primary entry point.

```
UrlUtils.kt:24 `else -> null ?: buildSearchQuery(trimmed, "google")`
MainActivity.kt:248 `val url = UrlUtils.formatUrl(input)` (no engine passed)
buildSearchQuery clearly supports the configured engines, but formatUrl never receives the user's choice.
```

**Fix:** Add a Context-aware formatUrl overload (or pass the engine) that resolves Prefs.getSearchEngine(context) for the non-URL branch, and call it from the address bar / voice search / suggestion paths. Keep the default-arg version only for tests.
**Verifier note:** Make formatUrl engine-aware at the typed/voice/deep-link entry points. Concretely: add an overload `formatUrl(input: String, engine: String): String` (or a Context-aware `formatUrl(context: Context, input: String)` that resolves `Prefs.getSearchEngine(context)`), and have its non-URL branch call `buildSearchQuery(trimmed, engine)` instead of the hardcoded "google". Update the three call sites that handle user-originated input: MainActivity.kt:248 (address bar GO/Enter), MainActivity.kt:90 (voice search), and MainActivity.kt:212 (sanitizeIncomingUrl bare input). Keep the no-arg `formatUrl(input)` only as a default/test convenience, or remove it to prevent the engine-ignoring path from being reused. Also clean up the dead `null ?:` in the `else` branch. Suggestion paths can be ignored — they replay stored history URLs and do not build search queries.

#### 7. [MEDIUM] File upload chooser ignores accept type, capture, and single/multiple mode
_File upload (<input type=file>) · Browsing Feature Completeness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 561-565, 77-82)`  

onShowFileChooser discards FileChooserParams (the param is bound to `_`) and always launches ActivityResultContracts.GetMultipleContents("*/*"). Consequences: an `<input accept="image/*">` still offers every file type; an `<input capture>` (camera) never opens the camera; a single-file input (no `multiple`) still lets the user pick many files. Production browsers honor params.acceptTypes, params.isCaptureEnabled, and params.mode (MODE_OPEN_MULTIPLE). The current code also can't surface the camera at all.

```
```
onShowFileChooser = { callback, _ ->   // FileChooserParams discarded
    fileChooserCallback?.onReceiveValue(null)
    fileChooserCallback = callback
    fileChooserLauncher.launch("*/*")
    true
}
```
Launcher: `registerForActivityResult(ActivityResultContracts.GetMultipleContents())`.
```

**Fix:** Read fileChooserParams.acceptTypes to derive a MIME filter, honor fileChooserParams.mode for single vs multiple, and offer a camera capture option when isCaptureEnabled is set (ACTION_IMAGE_CAPTURE/ACTION_VIDEO_CAPTURE via a FileProvider Uri). Pass null to the callback on cancel (already handled by the launcher returning empty list, but verify cancel vs empty selection).

#### 8. [MEDIUM] History records duplicates on every visit; dedup comment is not implemented
_History (record) · Browsing Feature Completeness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/data/HistoryRepository.kt  (lines 11-14)`  

addHistory's comment claims 'Avoid duplicates in quick succession (same URL within 5 minutes)' but the body just calls dao.insert(HistoryItem(...)). HistoryItem has an autoGenerate Long PK and no unique index on url, so OnConflictStrategy.REPLACE never fires — every page load (including reloads and SPA finishes) inserts a brand-new row. History fills with consecutive duplicate entries of the same page, degrading the history list and the address-bar suggestions (which are derived from history). getSuggestions uses GROUP BY url so suggestions are partly protected, but getAllHistory()/search() are not.

```
HistoryRepository.kt:
```
suspend fun addHistory(title, url, faviconUrl) {
    // Avoid duplicates in quick succession (same URL within 5 minutes)
    dao.insert(HistoryItem(title = title, url = url, faviconUrl = faviconUrl))
}
```
HistoryItem.kt: `@PrimaryKey(autoGenerate = true) val id: Long = 0` with no @Index(unique) on url.
```

**Fix:** Before insert, query the most-recent row for the same url and update its timestamp if within the dedup window; otherwise insert. Alternatively add a unique index on url with REPLACE plus a visit count/last-visited column. Either way make the behavior match the comment.

#### 9. [MEDIUM] New-tab page (about:blank) and error pages are recorded into history
_History (record) · Browsing Feature Completeness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/viewmodel/BrowserViewModel.kt  (lines 46-60)`  

onPageFinished saves history for any url with no filtering. The home/start page is loaded via loadDataWithBaseURL("about:blank", buildNewTabHtml(), ...), so opening a new tab fires onPageFinished with url == "about:blank", which is then written to history as a visited page. Likewise, when a page fails and HelixWebViewClient.onReceivedError loads an error page via loadDataWithBaseURL, onPageFinished still fires for the original URL and records the failed visit. Production browsers exclude about:blank, data:, and the internal new-tab page from history.

```
BrowserViewModel.onPageFinished:
```
if (isIncognito.value != true && Prefs.isSaveHistoryEnabled(app)) {
    viewModelScope.launch { historyRepo.addHistory(title = title.ifEmpty { url }, url = url) }
}
```
New tab load: MainActivity.kt:599 `webView.loadDataWithBaseURL("about:blank", buildNewTabHtml(), ...)`.
```

**Fix:** Skip history recording when url is empty, "about:blank", starts with "data:", or is the internal new-tab/error page. Add a guard at the top of onPageFinished's history block.

#### 10. [MEDIUM] New-tab start page search box does not open the address bar
_Home / Start page · Browsing Feature Completeness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 647)`  

The prominent 'Search or type URL' box on the home page has onclick="window.location.href='about:blank'". Because the page itself is loaded with baseURL about:blank, this navigates the WebView to a truly empty about:blank document — destroying the new-tab UI and doing nothing useful. There is no JavascriptInterface bridge, so the page cannot ask the native address bar to focus. The intended affordance (tap the box -> focus the address bar + keyboard) is broken; only the eight site shortcuts work.

```
MainActivity.kt:647 `<div class="search-box" onclick="window.location.href='about:blank'">`
Grep confirms no addJavascriptInterface anywhere in the project.
```

**Fix:** Either add a small @JavascriptInterface (e.g. window.HelixHome.focusAddressBar()) that calls binding.addressBar.requestFocus(), or navigate to a sentinel URL the WebViewClient intercepts and translates into focusing the address bar. Make the central search box focus the real omnibox like every shortcut grid browser does.

#### 11. [MEDIUM] Bookmarks have no folders, editing, or nesting; import flattens all structure
_Bookmarks (folders/edit) · Browsing Feature Completeness · effort L_  
**Location:** `android/app/src/main/java/com/helix/browser/data/Bookmark.kt  (lines 6-14)`  

The Bookmark entity is a flat (title, url, favicon, timestamp) row with no parent/folder column. BookmarksActivity supports only add (toggle), delete, and search — there is no rename/edit, no folder creation, and no move. BookmarksHtml.import explicitly flattens folder nesting (per its own doc comment: 'folder nesting is intentionally flattened on import, since Helix does not currently expose folders'). For a production browser, the absence of bookmark folders and the inability to edit a saved bookmark's title/URL is a notable feature gap, and round-tripping bookmarks with another browser loses all folder organization.

```
Bookmark.kt has no folderId/parent field. BookmarksHtml.kt:11-12 comment: 'folder nesting is intentionally flattened on import, since Helix does not currently expose folders.' BookmarksActivity only wires onItemClick + onDeleteClick + export/import.
```

**Fix:** Add a folder model (parentId column + a folders table or a self-referential bookmark row) and UI to create/move/rename. Preserve folder structure in BookmarksHtml import/export (nested <DL>/<DT><H3> blocks). At minimum add an 'edit bookmark' dialog for title/url.

#### 12. [MEDIUM] Downloads screen has no pause/resume/cancel; poll-based UI never live-updates progress
_Downloads (pause/resume/progress) · Browsing Feature Completeness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/DownloadsActivity.kt  (lines 47-79)`  

DownloadsActivity queries DownloadManager once in onCreate (loadDownloads) and never re-queries — despite importing Handler/Looper, no polling loop is started, so an in-progress download's percentage never advances in the list while the screen is open. DownloadsAdapter renders STATUS_RUNNING/PAUSED text but exposes no pause/resume/cancel/retry actions; click only does anything for STATUS_SUCCESSFUL (open). There is also no way to remove a download entry or retry a failed one. The audit dimension explicitly calls out pause/resume; those operations are entirely absent from the UI.

```
DownloadsActivity.kt: `private val handler = Handler(Looper.getMainLooper())` is declared but never used; loadDownloads() is only called once from onCreate. DownloadsAdapter.bind only sets onClickListener for STATUS_SUCCESSFUL; no pause/resume/cancel handlers exist.
```

**Fix:** Add a periodic refresh (handler.postDelayed re-query while any download is RUNNING/PENDING/PAUSED, stopped in onPause). Add per-item actions: cancel (dm.remove), and on Android N+ pause/resume is limited via DownloadManager — at minimum provide cancel, retry (re-enqueue), and remove-from-list. Update progress text/bar from COLUMN_BYTES_DOWNLOADED_SO_FAR on each tick.

#### 13. [MEDIUM] onReceivedError replaces valid page content on aborted/transient main-frame errors
_Logic error / broken navigation · Correctness & Crashes · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebViewClient.kt  (lines 86-98)`  

onReceivedError loads a full error page for ANY main-frame WebResourceError, with no filtering of the error code. WebView fires onReceivedError with ERROR_UNKNOWN / net::ERR_ABORTED for completely normal events: the user tapping a new link before the current load finishes, a page issuing a JavaScript/meta redirect, or hitting back during a load. In all of those cases the already-correct page is wiped and replaced with the 'something went wrong' interstitial, and because the interstitial is loaded via loadDataWithBaseURL it also corrupts the back/forward history entry. This is a frequent, visible breakage during normal browsing.

```
override fun onReceivedError(view, request, error) {
    if (request.isForMainFrame) {
        val errorCode = error.errorCode
        val description = error.description?.toString() ?: "Unknown error"
        onPageError(request.url.toString(), errorCode, description)
        val errorHtml = buildErrorPage(view.context, request.url.toString(), description)
        view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }
}
```

**Fix:** Only render the error page for genuine load failures and when the failing URL matches the URL the WebView is currently committing. At minimum ignore ERROR_UNKNOWN/-1 and aborted loads (description containing ERR_ABORTED), and guard with `request.url.toString() == view.url`.
**Verifier note:** Filter onReceivedError before rendering the interstitial. Specifically: (1) ignore aborted/transient errors — skip when error.errorCode == WebViewClient.ERROR_UNKNOWN (-1) or when error.description contains "ERR_ABORTED" (and consider skipping ERROR_TIMEOUT for transient retries); (2) only render when the failing request is the document the WebView is actually committing, e.g. guard with request.url.toString() == view.url (treat null/blank view.url carefully). Additionally, prefer not to pollute history: if you keep rendering, note that loadDataWithBaseURL adds a back-stack entry, so the interstitial's history.back() may bounce to the wrong place — consider rendering the error inline without a new navigation or document the history trade-off. Apply the same code-filtering rigor already present in onReceivedHttpError (which gates on statusCode >= 400).

#### 14. [MEDIUM] WebView pool grows unbounded; 'suspend inactive tabs' never frees memory (OOM with many tabs)
_Memory leak / lifecycle · Correctness & Crashes · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 68, 463; tabs/TabManager.kt:343-357)`  

webViewPool is a LinkedHashMap keyed by tab id and attachWebViewForTab does webViewPool.getOrPut(tab.id) { createWebViewForTab(tab) }. A full HelixWebView (with its own renderer process, JS heap, and bitmaps) is created and retained for every tab the user ever switches to, and is only removed when that specific tab is closed. The UI advertises up to '99+' tabs (updateTabCountBadge). The only memory-pressure mechanism, suspendInactiveTabs(), merely sets tab.isSuspended = true and nothing ever consumes that flag to destroy, pause-timers, or recreate a WebView. There is also no onTrimMemory / onLowMemory handling. With a few dozen open tabs the app will exhaust the per-app renderer/memory budget and be killed.

```
private val webViewPool = LinkedHashMap<String, HelixWebView>()
...
val webView = webViewPool.getOrPut(tab.id) { createWebViewForTab(tab) }
...
fun suspendInactiveTabs() {
    ...
    if (!tab.isSuspended && !tab.isPinned && (now - tab.lastAccessTime) > SUSPEND_TIMEOUT_MS) {
        tab.isSuspended = true   // flag set but never acted upon
        changed = true
    }
}
(no usage of isSuspended that destroys/recreates a WebView anywhere in the codebase)
```

**Fix:** Cap the pool (LRU-evict and safelyDestroyWebView the least-recently-used WebView beyond a small limit, e.g. 4-6) and actually implement suspension: destroy/evict suspended tabs' WebViews and recreate-from-url on re-selection. Add ComponentCallbacks2.onTrimMemory to evict background WebViews under pressure.
**Verifier note:** Implement an actual eviction policy on the webViewPool, which is the LinkedHashMap at MainActivity.kt:68. Concretely: (1) Cap the pool to a small number of live WebViews (e.g. 4-6 plus the foreground tab). In attachWebViewForTab (around line 463), after getOrPut, if the pool exceeds the cap, evict the least-recently-used non-pinned, non-current entry via the existing safelyDestroyWebView (defined at 1476) and remove it from the pool. LinkedHashMap with accessOrder=true makes LRU tracking trivial. (2) Make suspension functional: when tabManager.suspendInactiveTabs() flips isSuspended (TabManager.kt:343-357), have MainActivity evict those tabs' WebViews from the pool and destroy them; on re-selection, createWebViewForTab already reloads from tab.url (line 598), so re-creation from URL works for free. (3) Add ComponentCallbacks2.onTrimMemory (and onLowMemory) - either in MainActivity or, better, registered app-wide in HelixApp - to evict all background/non-current pooled WebViews when the level is TRIM_MEMORY_RUNNING_LOW or higher. Optionally call WebView state-saving before destroying so scroll/form state survives. These changes reuse the already-present safelyDestroyWebView teardown and the load-from-url path, so they are low-risk.

#### 15. [MEDIUM] JS dialogs broken: onJsConfirm always returns cancel, onJsAlert auto-confirms with no UI
_Broken feature / logic error · Correctness & Crashes · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebChromeClient.kt  (lines 73-81)`  

onJsAlert immediately calls result.confirm() and shows nothing, and onJsConfirm immediately calls result.cancel(). This means window.confirm() ALWAYS evaluates to false for every site, and window.alert() is silently swallowed. Many sites gate actions on confirm() (delete confirmations, 'are you sure', cookie/age gates, beforeunload-style prompts), so flows that depend on a user confirming will universally fail, and alerts the user is meant to read are never shown. This is a correctness regression versus default WebView behavior.

```
override fun onJsAlert(view, url, message, result): Boolean {
    result.confirm()
    return true
}
override fun onJsConfirm(view, url, message, result): Boolean {
    result.cancel()
    return true
}
```

**Fix:** Show a real AlertDialog (anchored to the host activity, guarded against destroyed activity) and call result.confirm()/cancel() based on the user's choice; for onJsAlert show an OK dialog. If suppressing for security, at least make confirm default match platform expectations and rate-limit rather than hard-failing every dialog.

#### 16. [MEDIUM] Web content dark mode never applied: setNightMode is dead code
_Broken feature / dead code · Correctness & Crashes · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt  (lines 103-110)`  

HelixWebView.setNightMode() (which toggles isAlgorithmicDarkeningAllowed / forceDark) is never called anywhere in the app. There is a KEY_DARK_MODE preference (Prefs.isDarkMode) and reader mode even reads it, but no code path ever pushes dark mode into a WebView. As a result the 'dark mode' setting has no effect on rendered web pages.

```
grep across the module shows setNightMode is defined at HelixWebView.kt:103 and has zero call sites. Prefs.isDarkMode is only consulted in MainActivity.toggleReaderMode (line ~799), never to call setNightMode.
```

**Fix:** Call webView.setNightMode(...) from createWebViewForTab/attachWebViewForTab based on Prefs.isDarkMode plus the system uiMode, and re-apply when the setting changes, or remove the dead API and the dark-mode setting if unsupported.

#### 17. [MEDIUM] Tab thumbnail captured via software canvas on a hardware-accelerated WebView can render blank/black
_Robustness / rendering · Correctness & Crashes · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 305-318)`  

When opening the tab switcher, the code captures the current page by allocating a Bitmap and calling webView.draw(canvas) on a software Canvas. HelixWebView forces LAYER_TYPE_HARDWARE (setLayerType(View.LAYER_TYPE_HARDWARE, null) in setupSettings). Drawing a hardware-layer WebView into a software Bitmap canvas frequently yields a blank or black thumbnail (and on some devices/GPU-composited content like video, nothing at all). Capturing a full-size ARGB_8888 bitmap of the viewport then scaling it is also a large transient allocation done on the main thread for every tab-switcher open.

```
val bitmap = android.graphics.Bitmap.createBitmap(webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888)
val canvas = android.graphics.Canvas(bitmap)
webView.draw(canvas)
...
// while setupSettings() does: setLayerType(View.LAYER_TYPE_HARDWARE, null)
```

**Fix:** Capture into a smaller pre-scaled bitmap directly and/or temporarily set LAYER_TYPE_SOFTWARE for the capture, or use PixelCopy (API 26+) against the WebView's surface for a correct hardware-rendered snapshot. Move the capture off the main thread where possible.

#### 18. [MEDIUM] Pin action in TabSwitcher mutates tab.isPinned directly, bypassing TabManager reorder logic
_State corruption · Correctness & Crashes · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/TabSwitcherActivity.kt  (lines 312-316)`  

The tab context menu 'Pin tab' sets tab.isPinned = !tab.isPinned directly on the BrowserTab object instead of calling tabManager.pinTab(id). TabManager.pinTab() is responsible for moving pinned tabs to the front and recomputing _currentIndex. By flipping the flag directly, the pinned tab is never reordered, _currentIndex is not adjusted, and the LiveData (tabsLiveData) is not notified — so the in-memory invariant 'pinned tabs are contiguous at the front' that other methods (pinTab insertIndex, closeOtherTabs, closeTabsToRight) rely on is violated. Subsequent pin/close operations can then compute wrong indices.

```
0 -> {
    tab.isPinned = !tab.isPinned
    refreshList(tabManager.tabs)
}
// vs TabManager.pinTab(tabId) which removes/reinserts and fixes _currentIndex
```

**Fix:** Call tabManager.pinTab(tab.id) (and rely on notifyChanged) instead of mutating the flag, so ordering and current-index bookkeeping stay consistent.

#### 19. [MEDIUM] Error/SSL/HTTPS-only pages reflect attacker-controlled URL into HTML without escaping
_security · Production Quality & Robustness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebViewClient.kt  (lines 200-227, 229-251, 253-280)`  

The custom error, SSL-error and HTTPS-only interstitial pages embed the failing request URL (and onReceivedError's description) directly into HTML markup. JavaScript is enabled on every WebView (HelixWebView.setupSettings javaScriptEnabled=true). A page that navigates to a crafted URL such as https://x/<img src=x onerror=...> (or a redirect to one that then fails/has a bad cert) gets its markup reflected and executed in the browser's own error page (opaque origin). This is HTML/JS injection into trusted browser chrome and can be used for convincing phishing overlays (the page fully controls the error screen) or to drive top-level navigation. A production browser must HTML-escape any URL/description before interpolation.

```
buildErrorPage(...): "<p class=\"url\">$url</p>" and "<p>$description</p>"; buildSslErrorPage/buildHttpsOnlyInterstitial similarly interpolate $url. These are loaded via view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null) at lines 96, 118-119, 151, 170. The url comes straight from request.url.toString()/error.url with no HTML escaping.
```

**Fix:** HTML-escape url and description before string interpolation (escape &, <, >, ", '), e.g. a small escapeHtml() like the one already present in BookmarksHtml.kt, applied in all three build*Page functions.
**Verifier note:** HTML-escape every interpolated dynamic value (url and description) in all three builders before string interpolation. Reuse/extract the existing escapeHtml() from data/BookmarksHtml.kt (escape &, <, >, ", and ideally ') into a shared util and apply it to $url in buildErrorPage (line 222), buildHttpsOnlyInterstitial (line 247), and buildSslErrorPage (line 275), and to $description in buildErrorPage (line 221). Defense-in-depth: add a restrictive CSP meta tag (e.g. default-src 'none'; style-src 'unsafe-inline') to these self-generated chrome pages so even an escaping miss cannot execute script, and prefer rendering the URL into a textContent-assigned element rather than raw HTML. Add a regression unit test that feeds a URL/description containing <img src=x onerror=...> and asserts the output contains &lt;img and not the raw tag.

#### 20. [MEDIUM] WebView pool grows unbounded; "suspend inactive tabs" never frees a WebView
_performance · Production Quality & Robustness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 68, 463, 1399-1411)`  

Every visited tab keeps a fully live HelixWebView in webViewPool for the entire activity lifetime. With many tabs (a browser routinely has dozens) this holds dozens of WebView instances each with their own renderer/native heap, leading to memory pressure and OOM/renderer kills. The advertised 'suspend inactive tabs' feature (Settings switch, onPause call at line 1408-1410) is purely cosmetic: it flips a boolean but the WebView is never paused/destroyed and is never reloaded on switch-back, so it frees nothing. There is no LRU eviction despite using a LinkedHashMap.

```
private val webViewPool = LinkedHashMap<String, HelixWebView>() ; attachWebViewForTab: val webView = webViewPool.getOrPut(tab.id) { createWebViewForTab(tab) }. WebViews are only ever removed on explicit tab close (lines 350/357/1388) or onDestroy (1418). TabManager.suspendInactiveTabs() (TabManager.kt:343) only sets tab.isSuspended=true; attachWebViewForTab never inspects isSuspended and never destroys/recreates a suspended tab's WebView.
```

**Fix:** Implement real suspension: when a tab is suspended (or when the pool exceeds an LRU cap, e.g. 5-8), call safelyDestroyWebView() and remove it from webViewPool; recreate and reload from tab.url on next attach. Cap the pool and evict the least-recently-used non-current WebView in attachWebViewForTab.
**Verifier note:** Make suspension actually reclaim memory and bound the pool:

1) Honor isSuspended in attachWebViewForTab. When a non-current tab is suspended, evict its WebView: `webViewPool.remove(tab.id)?.let(::safelyDestroyWebView)`. On switch-back, the existing getOrPut already recreates the WebView; ensure createWebViewForTab loads tab.url (createWebViewForTab at ~598 does `if (tab.url.isNotEmpty()) webView.loadUrl(tab.url)`, so recreation will restore the page). Persist scroll position if you want fidelity.

2) Drive eviction from suspendInactiveTabs. Since TabManager has no WebView access, after `tabManager.suspendInactiveTabs()` in onPause, iterate the pool and destroy WebViews for any non-current tab whose tab.isSuspended is now true.

3) Add an LRU cap in attachWebViewForTab. After getOrPut, if `webViewPool.size` exceeds a cap (e.g. 5-8), evict the least-recently-used non-current entry. The LinkedHashMap is already access-ordered-friendly; either construct it with accessOrder=true and override removeEldestEntry (skipping the current tab), or maintain order by re-inserting on attach and removing from the front.

4) Add onTrimMemory(level)/onLowMemory overrides that, on TRIM_MEMORY_RUNNING_LOW or higher, destroy all non-current pooled WebViews (safelyDestroyWebView already documents itself as safe for low-memory eviction but currently has no caller).

#### 21. [MEDIUM] window.alert and window.confirm are silently suppressed, breaking site functionality
_robustness · Production Quality & Robustness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebChromeClient.kt  (lines 73-81)`  

onJsAlert immediately confirms and consumes the event, so alert() messages never appear to the user. onJsConfirm always returns Cancel to the page, so window.confirm() always evaluates to false. This breaks legitimate flows: 'Are you sure you want to leave/delete?' confirmations always cancel, form-validation alerts vanish, and some sites that gate actions on confirm() will silently no-op. A production browser should present a real (dismissable) dialog or at least surface alert text.

```
onJsAlert(...) { result.confirm(); return true } and onJsConfirm(...) { result.cancel(); return true }
```

**Fix:** Show a Material dialog for onJsAlert (OK) and onJsConfirm (OK/Cancel mapped to result.confirm()/result.cancel()), guarding against destroyed activity. Optionally rate-limit to prevent dialog spam.

#### 22. [MEDIUM] New-tab page fetches Google Fonts over the network on every blank tab
_privacy · Production Quality & Robustness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 626)`  

Each new/empty tab loads its internal start page, which @imports a font stylesheet from fonts.googleapis.com. This makes a network request to Google (leaking the device IP/timing on every new tab) - undermining the privacy positioning and the ad/tracker blocking - and degrades the offline experience (the start page renders with no styled font when offline). The font is also not blocked by the local font-display fallback consistently across renders.

```
buildNewTabHtml(): "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');"
```

**Fix:** Bundle the Inter font as a local asset (or rely on the existing -apple-system/sans-serif fallback) and remove the remote @import so the start page is fully offline and makes no third-party request.

#### 23. [MEDIUM] Tab thumbnail captured by drawing the full-size WebView into an ARGB_8888 bitmap on the main thread
_performance · Production Quality & Robustness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 306-318)`  

Opening the tab switcher synchronously allocates a full-resolution ARGB_8888 bitmap (~8MB on a 1080x2000 screen), renders the entire WebView into it via draw(), then scales it - all on the main thread on every tap of the tabs button. This causes visible jank and, on large/old devices or under memory pressure, an OutOfMemoryError or ANR. The try/catch swallows the OOM (e.printStackTrace) but the frame is still dropped.

```
val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); webView.draw(canvas); val scaledBitmap = Bitmap.createScaledBitmap(bitmap, webView.width / 4, webView.height / 4, true) - all inside btnTabs onClick on the UI thread.
```

**Fix:** Capture into an already-downscaled bitmap (allocate at width/4 x height/4 and scale the Canvas), reuse a pooled bitmap, or offload scaling to a background thread. Guard with a max bitmap size and skip capture under low memory.

#### 24. [MEDIUM] Hardcoded English UI strings in Tab Switcher and Page Info despite 15 shipped locales
_robustness · Production Quality & Robustness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/TabSwitcherActivity.kt  (lines 169, 245-246, 262-266, 302-308)`  

The app ships values-* resources for ~15 languages, but several user-facing strings in the tab switcher (close-all confirmation, undo snackbar, overflow menu, per-tab context menu) and in the page-info/cookie dialogs are hardcoded English literals. Non-English users see mixed-language UI on core screens. This is also a likely store-review/localization-quality flag.

```
setMessage("Close all ${tabManager.tabCount} tabs?"); Snackbar.make(..., "Tab closed").setAction("Undo"); items.add("New tab"/"New incognito tab"/"Close all tabs"/"Reopen closed tab"); context-menu arrayOf("Pin tab","Close other tabs","Close tabs to the right","Share link","Copy link"). Also MainActivity.kt:1199 ("$cookieCount cookies in use"/"No cookies"), 1218 ("Clear cookies for $domain?"), 286 ("Voice search not available").
```

**Fix:** Move all user-facing literals to strings.xml (with plurals for the 'Close all N tabs' / 'N cookies' counts) and reference via getString/getQuantityString; translate in the existing values-* files.

#### 25. [MEDIUM] Premium entitlement persisted client-side with no Play purchase verification
_security · Production Quality & Robustness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/billing/BillingManager.kt  (lines 36, 90-111, 132-163)`  

Premium (ad-free) status is derived solely from local SharedPreferences and from the unverified purchase state, with no server-side or on-device signature verification (BillingClient delivers signature + originalJson for exactly this). An attacker who flips the helix_billing/is_premium pref (rooted device, backup edit) or replays a fake purchase gets premium for free. allowBackup=true (AndroidManifest line 25) makes the pref trivially editable via adb backup on debuggable/older devices. For a paid feature this is a real monetization-integrity gap.

```
private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false)); setPremium(premium) { _isPremium.value = premium; prefs.edit().putBoolean(KEY_IS_PREMIUM, premium).apply() }. Purchases are accepted on PurchaseState.PURCHASED with no Purchase.getSignature()/originalJson signature verification.
```

**Fix:** Verify Purchase signatures with the app's RSA public key before granting entitlement (ideally validate server-side), and do not trust the cached pref as the source of truth - re-query queryPurchasesAsync on launch and gate features on the verified result. Exclude the billing pref from backup.

#### 26. [MEDIUM] Reader Mode injects page-controlled HTML into innerHTML, enabling DOM XSS
_JavaScript injection / XSS · Security & Privacy · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/ReaderMode.kt  (lines 47-68)`  

ReaderMode.enter() builds a new document by concatenating page-derived values (title, byline, and the extracted node's innerHTML) directly into document.documentElement.innerHTML. The 'script strip' that was meant to make this safe runs AFTER the innerHTML assignment, so it cannot prevent execution. Assigning innerHTML does not run <script> tags, but it DOES fire HTML event-handler attributes such as <img src=x onerror=...>, <svg onload=...>, <body onload=...>, and <iframe onload=...>. A malicious or compromised page that the user opens in Reader Mode can therefore execute arbitrary JavaScript in the page's own origin context (read cookies/localStorage, exfiltrate page content, drive logged-in sessions). 'title' and 'byline' come from document.title / .byline elements and are likewise interpolated unescaped, giving a second injection point. Because reader mode is a user-invoked, attacker-reachable code path on every site, this is a genuine same-origin XSS sink.

```
document.documentElement.innerHTML =
  '<head>...'+
  '<body><article class="helix-reader">' +
  '<h1>' + title + '</h1>' +
  (byline ? '<div class="byline">' + byline + '</div>' : '') +
  content +
  '</article></body>';
// Strip scripts in extracted content for safety.
document.querySelectorAll('script').forEach(function(s){ s.remove(); });   // runs AFTER innerHTML; does not stop onerror/onload
```

**Fix:** Do not assemble the reader view via innerHTML of untrusted DOM. Build the new document with DOMParser + a sanitizer that strips all event-handler attributes and javascript:/data: URLs, or construct nodes with createElement/textContent and clone only whitelisted elements. At minimum, sanitize before insertion (not after) and HTML-escape title/byline. Strip on* attributes and inline event handlers explicitly.
**Verifier note:** Sanitize before insertion, not after. Concrete fixes: (1) Do not assign attacker DOM via innerHTML on the live document. Parse the extracted markup with DOMParser into a detached document, run a sanitizer that removes ALL on* event-handler attributes and strips javascript:/data: URLs and <iframe>/<object>/<embed>/<svg> (or whitelist only safe elements), then import the cleaned nodes. (2) HTML-escape title and byline before interpolation (they are text from .innerText / document.title but are placed in an HTML context). (3) Remove or fix the misleading post-insertion script-strip -- it gives a false sense of safety and does not stop on* handlers; if kept, it must run against the parsed-but-not-yet-inserted fragment. (4) Prefer building the reader DOM with createElement/textContent and cloning only whitelisted elements (p, h1-h3, a with vetted href, img with vetted src, blockquote, pre, code) rather than wholesale innerHTML. Optionally render the reader view in an about:blank/srcdoc sandboxed frame with a restrictive CSP to contain any residual injection.

#### 27. [MEDIUM] intent:// and market:// links are swallowed; tel:/mailto:/sms: are routed to WebView and fail
_Intent / custom-URL-scheme handling · Security & Privacy · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebViewClient.kt  (lines 184-197)`  

shouldOverrideUrlLoading returns true for the 'intent' and 'market' schemes with the comment 'handled externally', but no code anywhere in the app launches an Intent for these (confirmed by grepping startActivity/parseUri in the engine and MainActivity). The link is therefore neither loaded nor dispatched to another app: it is silently dropped. Conversely, tel/mailto/sms/whatsapp/geo etc. fall into the else branch and return false, which hands them to WebView.loadUrl — WebView cannot load these schemes and the page shows an error. The net effect is a core browser feature (deep links / app links / dialer / mailto) being broken. Returning true for intent:// without parsing/validating is also the only thing preventing an arbitrary-intent-launch vuln if a launch is later added carelessly, so the eventual implementation must use Intent.parseUri with URI_ANDROID_APP_SCHEME guards and drop selector/component/flags to avoid open-intent redirection.

```
"intent", "market" -> {
    // Android intent URLs - handled externally
    true   // claims external handling, but nothing launches them -> link dropped
}
else -> {
    // Tel, mailto, etc.
    false  // handed to WebView.loadUrl, which cannot load tel:/mailto: -> error
}
```

**Fix:** Implement explicit, safe external dispatch: for tel/mailto/sms/geo and other non-web schemes, build an ACTION_VIEW intent and startActivity inside a try/catch (handle ActivityNotFoundException). For intent:// use Intent.parseUri(url, Intent.URI_INTENT_SCHEME), then clear component/selector and strip GRANT_URI flags, verify resolveActivity, and only launch with user awareness. Never blindly forward attacker-supplied intents.

#### 28. [MEDIUM] Download manager accepts arbitrary URL scheme; no validation of scheme or MIME before enqueue
_Download safety / MIME sniffing · Security & Privacy · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 1251-1267)`  

downloadFile() passes the raw url straight into DownloadManager.Request(Uri.parse(url)) and computes the filename purely from URLUtil.guessFileName(url, contentDisposition, mimeType) before writing to the public Downloads directory via setDestinationInExternalPublicDir. The url is not restricted to http/https — it is also reachable from the image/link context menu (showImageContextMenu -> downloadFile(imageUrl, ...)) and the page's own DownloadListener, so a data:, blob:, file:, or content: URL or a hostile content-disposition can be fed in. There is no confirmation prompt showing the user the host and filename/type (standard anti-drive-by-download protection), no check that the mime/extension is sane, and no guard that DownloadManager can even handle the scheme (data:/blob: throw or silently fail). guessFileName generally neutralizes path separators, but combined with attacker-controlled content-disposition and no user confirmation this is a drive-by-download / deceptive-extension risk.

```
private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
    ...
    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        ...
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    }
    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
```

**Fix:** Validate the scheme (allow only http/https for DownloadManager; handle data:/blob: via a separate code path that writes app-owned files) before enqueue. Show a confirmation sheet with the resolved host, filename and detected type, and sanitize the filename. Reject or specially handle executable/installer MIME types and mismatched extensions.

#### 29. [MEDIUM] SSL certificate errors can be bypassed with a single tap and no per-host scoping or persisted warning state
_TLS/SSL certificate error handling · Security & Privacy · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebViewClient.kt  (lines 111-153)`  

onReceivedSslError (correctly) prompts before proceeding rather than silently calling proceed(), and HTTPS-Only mode hard-fails — that part is good. However in the default mode the dialog offers a one-tap 'Continue' that calls handler.proceed() for ANY cert error including SSL_UNTRUSTED and SSL_IDMISMATCH, the highest-risk classes that modern browsers treat as hard-to-bypass. The dialog uses view.context (an Activity context) to build an AlertDialog from inside the WebViewClient callback, which is fragile across config changes. The proceed decision is also not scoped or labeled per host in a way that distinguishes a fresh MITM from a previously-accepted self-signed cert, and clearSslPreferences() is only invoked by the global clear-data flow, so a user who taps Continue once has effectively trained themselves to click through. For a browser this is the weakest area after the items above.

```
.setPositiveButton(context.getString(R.string.continue_button)) { _, _ ->
    handler.proceed()   // proceeds for SSL_UNTRUSTED / SSL_IDMISMATCH too, one tap
}
```

**Fix:** Distinguish error classes: for SSL_UNTRUSTED / SSL_IDMISMATCH show a full-page interstitial requiring an explicit 'proceed unsafely' affirmation (or disallow entirely), matching Chrome. Show the exact host and cert subject. Build the warning UI as a page/interstitial instead of an AlertDialog tied to a possibly-stale Activity context, and avoid normalizing one-tap continue.

#### 30. [MEDIUM] Thai and Chinese translations are stripped from release builds (resourceConfigurations omits them) yet offered in the language picker
_i18n · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `android/app/build.gradle  (lines 36-40)`  

The gradle resourceConfigurations whitelist lists only 12 locales (en, vi, de, es, fr, pt, ru, ja, ko, hi, id, ar). It deliberately strips every other locale's resources from the APK/bundle to cut size. However res/values-th/ and res/values-zh-rCN/ exist with translated strings, and LanguageActivity explicitly presents Thai and Chinese (Simplified) as selectable languages. Because those two qualifiers are not in resourceConfigurations, their string resources are removed at build time. When a user picks Thai or Chinese, LocaleHelper correctly sets the locale (Locale("zh","CN") / Locale("th")) but resource resolution finds no matching values-* folder in the shipped artifact, so the entire UI falls back to English. The app advertises 14 locales but ships strings for at most 12, and two of the offered languages are completely non-functional in release. This may be masked in debug builds where resource filtering differs.

```
resourceConfigurations += [
    'en','vi','de','es','fr','pt','ru','ja','ko','hi','id','ar'
]

// But on disk: res/values-th/ and res/values-zh-rCN/ both exist, and
// LanguageActivity.kt:63,69 offer them to users:
LanguageItem("zh-rCN", "中文", "Chinese (Simplified)"),
LanguageItem("th", "ไทย", "Thai"),
```

**Fix:** Either add 'th' and 'zh' (for zh-rCN use 'zh') to resourceConfigurations so the resources survive the build, or remove Thai and Chinese from the LanguageActivity picker. Keep the whitelist, the on-disk locale folders, and the picker list in sync (ideally derive the picker from a single source of truth).
**Verifier note:** Keep the three sources in sync from a single source of truth. Concretely, either:

(A) Make the offered locales ship: add the missing qualifiers to resourceConfigurations in android/app/build.gradle. Use the exact AGP locale-qualifier syntax — 'th' and 'zh-rCN' (for region-qualified Chinese, list 'zh-rCN' to match the values-zh-rCN folder; plain 'zh' would NOT package values-zh-rCN). Then any future picker locale must also be added here, or

(B) Trim the picker: remove the LanguageItem("zh-rCN", ...) and LanguageItem("th", ...) entries from LanguageActivity.kt (lines 63 and 69) so the app does not advertise locales it strips.

Prefer (A) since real translations already exist on disk (38 strings each, same as Japanese). Best long-term fix: derive the LanguageActivity picker list from the actual values-* folders / resourceConfigurations whitelist (or a shared constant) so the gradle whitelist, on-disk locale folders, and picker can never drift apart. Add a CI check (or a unit test using the existing Robolectric setup) asserting every picker code resolves to a packaged locale. Note the broader translation-coverage gap (th/zh/ja each only translate 38 of 207 strings) is a separate, lower-priority issue.

#### 31. [MEDIUM] DayNight theme with no values-night and hardcoded dark-only colors breaks the app in system Light mode
_theming · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `android/app/src/main/res/values/themes.xml  (lines 4)`  

The base theme inherits from Theme.Material3.DayNight, which switches Material framework component styling (dialog backgrounds, switch tracks, popup menus, default text colors) between light and dark based on the system setting. But every app color is a hardcoded dark value, there is no values-night override, and forceDarkAllowed is false, so the app's own surfaces stay dark in every mode. The result: when the device is in Light mode, Material-provided components render with light backgrounds while the app's custom surfaces stay near-black, producing inconsistent contrast (e.g. light dialog scrims over dark content, mismatched menu/popup colors). Because no setDefaultNightMode is set, this happens on any user whose phone is in light mode. The app is effectively dark-only but does not declare itself as such.

```
<style name="Theme.HelixBrowser" parent="Theme.Material3.DayNight.NoActionBar">
...
<item name="android:forceDarkAllowed" tools:targetApi="q">false</item>

// colors.xml has only dark values, e.g. background=#0F0F0F, text_primary=#F0F0F0
// No values-night/, color-night/, or drawable-night/ directories exist.
// No AppCompatDelegate.setDefaultNightMode() call anywhere in java/.
```

**Fix:** Either (a) force dark via AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES) and change the theme parent to a non-DayNight dark theme so framework components match, or (b) provide a proper values-night palette and a matching light palette in values/ so the DayNight switch produces a consistent UI in both modes.

#### 32. [MEDIUM] Three declared permissions are never used (ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, POST_NOTIFICATIONS)
_permissions · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `android/app/src/main/AndroidManifest.xml  (lines 8-9,15)`  

ACCESS_NETWORK_STATE and ACCESS_WIFI_STATE are declared but no ConnectivityManager/WifiManager code exists anywhere. POST_NOTIFICATIONS is declared but the app never builds or posts a Notification, never creates a NotificationChannel, and never requests the runtime permission — download progress is shown by the system DownloadManager under its own identity, which does not require the app's POST_NOTIFICATIONS. ACCESS_WIFI_STATE in particular is sometimes treated as a device-identifier signal by Play's data-safety review. Over-declared permissions inflate the Play Store permissions list, complicate the required data-safety form, and offer no benefit.

```
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

// grep across java/: no ConnectivityManager / getActiveNetwork / NETWORK_STATE usage,
// no WifiManager / getConnectionInfo usage,
// no NotificationCompat / NotificationChannel / .notify() / runtime POST_NOTIFICATIONS request.
// Downloads use DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED (system identity).
```

**Fix:** Remove ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, and POST_NOTIFICATIONS from the manifest unless a concrete feature uses them. If app-posted notifications are planned, add POST_NOTIFICATIONS back together with the runtime request and channel.

#### 33. [MEDIUM] Browsing history and bookmarks are included in Google cloud backup (allowBackup=true, DB not excluded)
_privacy · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `android/app/src/main/res/xml/backup_rules.xml  (lines 1-5)`  

allowBackup is true and the backup/data-extraction rules exclude only the sharedpref domain. The Room database helix_browser.db — which contains the user's full browsing history and bookmarks — is left in the default 'database' backup domain, so it is uploaded to Google's cloud Auto Backup and restored onto any device the user signs into. For a browser marketed as 'Private', silently syncing browsing history to the cloud is a meaningful privacy exposure and a data-safety disclosure obligation. Note the asymmetry: the privacy-relevant SharedPreferences are excluded but the far more sensitive history DB is not.

```
<full-backup-content>
    <exclude domain="sharedpref" path="." />
</full-backup-content>

// data_extraction_rules.xml similarly excludes only sharedpref.
// AndroidManifest: android:allowBackup="true".
// AppDatabase.kt:44 -> Room db file "helix_browser.db" with entities
// bookmarks (Bookmark.kt:6) and history (HistoryItem.kt:6) is NOT excluded.
```

**Fix:** Add <exclude domain="database" path="helix_browser.db"/> (and the -wal/-shm files, or exclude the whole database domain) to both backup_rules.xml and data_extraction_rules.xml, or set allowBackup=false. Whatever is backed up must be reflected in the Play data-safety form.

#### 34. [MEDIUM] Premium entitlement granted with no purchase signature verification and persisted in plaintext SharedPreferences
_billing · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/billing/BillingManager.kt  (lines 97-101)`  

Premium is unlocked purely from the local Purchase object's PurchaseState, with no verification of purchase.signature against the app's Play public key and no server-side receipt validation. The entitlement is then cached as a plain boolean in SharedPreferences (helix_billing/is_premium). On a rooted device a user can flip that flag or feed a forged purchase, unlocking premium offline. queryPurchasesAsync re-checks against Play on each launch, which mitigates the simplest replay, but the lack of signature verification plus a trivially editable local flag makes entitlement easy to spoof. This is a known billing-fraud pattern Google's billing security guidance warns against.

```
val hasActive = purchases.any { purchase ->
    purchase.products.contains(PRODUCT_ID) &&
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
}
setPremium(hasActive)
...
private fun setPremium(premium: Boolean) {
    _isPremium.value = premium
    prefs.edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
}
// No purchase.signature / getOriginalJson / public-key verification anywhere.
```

**Fix:** Verify each Purchase's signature with Security.verifyPurchase() against the base64-encoded RSA public key (ideally via a backend, not embedded in the APK), and treat the SharedPreferences flag only as a non-authoritative cache, re-deriving entitlement from verified Play queries.

#### 35. [LOW] Desktop-site toggle and text zoom are per-session only and reset on tab switch/restart
_Desktop-site / Page zoom · Browsing Feature Completeness · effort M_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 690-712)`  

The desktop-site toggle writes only to viewModel.isDesktopMode (in-memory) and applies setDesktopMode to live WebViews; it never persists via Prefs.setDesktopMode/isDesktopMode (those exist in Prefs but are unused for this toggle). After process restart the choice is lost. Text zoom is read/written directly on currentWebView.settings.textZoom with no persistence, so it resets to 100% every new tab and every restart, and is not applied to other tabs. Also, toggling desktop mode calls webView.reload() inside setDesktopMode, which on a brand-new tab reloads the about:blank new-tab page. These are robustness/UX gaps versus a production browser where zoom and desktop-site are remembered.

```
Toggle: `viewModel.isDesktopMode.value = isDesktop; webViewPool.values.forEach { it.setDesktopMode(isDesktop) }` — no Prefs write. Zoom: `currentWebView?.settings?.textZoom = newZoom` — no persistence. HelixWebView.setDesktopMode ends with `reload()`.
```

**Fix:** Persist the desktop-site choice (global or per-host) and re-apply it when creating WebViews; persist a default text-zoom in Prefs and apply it in setupSettings/createWebViewForTab. Guard setDesktopMode's reload() so it doesn't reload the new-tab page when url is empty/about:blank.

#### 36. [LOW] Per-page night/dark mode is defined but never invoked
_Reader mode / Page rendering · Browsing Feature Completeness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt  (lines 103-110)`  

HelixWebView.setNightMode (which toggles isAlgorithmicDarkeningAllowed / forceDark to render web content in dark) is dead code — grep shows no caller anywhere. There is no menu/setting that forces page dark rendering, so the app's dark-mode preference does not affect how web pages themselves render. Reader mode does respect dark, but ordinary pages do not. Minor since it may be intentional, but the capability is wired only halfway.

```
Grep: setNightMode appears only at its definition (HelixWebView.kt:103) with no invocation. forceDark/isAlgorithmicDarkeningAllowed appear only inside this unused function.
```

**Fix:** Either remove the dead setNightMode, or call it from a 'Dark websites' toggle wired to the dark-mode preference and apply it when creating/attaching WebViews.

#### 37. [LOW] Picture-in-Picture does not adjust UI on PiP mode change
_Fullscreen / PiP video · Browsing Feature Completeness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 808-825)`  

enterPictureInPicture() builds PictureInPictureParams and calls enterPictureInPictureMode, but the activity never overrides onPictureInPictureModeChanged. In PiP, the address bar, bottom nav, and toolbars remain visible inside the tiny window (they should be hidden so only the <video> shows), and there is no handling to restore them on exit or to auto-enter PiP on home-press during video playback. Functional but visually rough compared to a production browser's PiP.

```
Grep for onPictureInPictureModeChanged returns no override. enterPictureInPicture only sets a 16:9 aspect ratio and calls enterPictureInPictureMode(params).
```

**Fix:** Override onPictureInPictureModeChanged(isInPictureInPictureMode, ...) to hide the toolbars/nav/address bar (and the swipe-refresh chrome) while in PiP and restore them on exit. Optionally auto-enter PiP from onUserLeaveHint when a video is playing fullscreen.

#### 38. [LOW] Third-party cookie policy race: HelixWebView.init unconditionally enables third-party cookies
_Race / ordering · Correctness & Crashes · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt  (lines 37-39)`  

Every HelixWebView's init block calls CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) unconditionally. createWebViewForTab later calls PrivacyManager.applyThirdPartyCookiePolicy(this, webView) which sets it according to the user setting, but only for the active tab's WebView at creation. The ordering means a WebView briefly has third-party cookies enabled regardless of the user's 'block third-party cookies' setting, and for incognito tabs setIncognitoMode is what re-disables it. This is fragile: if applyThirdPartyCookiePolicy is ever skipped/reordered the privacy setting silently does not take effect.

```
init {
    ...
    // Default: accept third-party cookies on non-incognito tabs only.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    ...
}
```

**Fix:** Do not hardcode true in init. Apply the actual per-setting policy as part of WebView construction (pass context/policy in), so a WebView never starts with the wrong third-party-cookie state.

#### 39. [LOW] Dead Accept-Language code in WebView setup (computed languageTag never applied)
_Dead code · Correctness & Crashes · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt  (lines 80-89)`  

setupSettings() reads the current locale and builds languageTag with an explanatory comment about the Accept-Language header, but the value is never used — no header is set on the WebView. So localized content negotiation via Accept-Language is not actually implemented despite the apparent intent and the app's heavy localization effort.

```
val languageTag = locale.toLanguageTag()
// Some websites prefer comma separated list (e.g. en-US,en;q=0.9)
// But just the current one is usually enough for redirection
// (languageTag is never referenced again)
```

**Fix:** Either remove the dead computation or actually apply it (e.g. inject an Accept-Language via shouldInterceptRequest header rewriting, since WebSettings has no direct API) so localized sites are served correctly.

#### 40. [LOW] DownloadManager query and cursor iteration run on the main thread; cursor not closed in finally
_performance · Production Quality & Robustness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/DownloadsActivity.kt  (lines 47-67)`  

loadDownloads() performs a binder IPC DownloadManager.query() and iterates the resulting cursor synchronously on the UI thread during onCreate. With many historical downloads this can drop frames or, in pathological cases, contribute to an ANR. Additionally cursor.close() is not in a finally block, so any exception during column reads (e.g. getColumnIndexOrThrow) leaks the cursor.

```
private fun loadDownloads() { ... val cursor: Cursor = dm.query(query); if (cursor.moveToFirst()) { do { ... } while (cursor.moveToNext()) }; cursor.close() } called from onCreate (line 39).
```

**Fix:** Run the query/iteration on Dispatchers.IO and post the resulting list to the adapter; wrap cursor use in cursor.use { } so it is always closed.

#### 41. [LOW] Inconsistent ad-block default between Prefs and PrivacyManager script gate
_correctness · Production Quality & Robustness · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/utils/UrlUtils.kt  (lines 97)`  

Network-level ad blocking (shouldInterceptRequest/shouldOverrideUrlLoading, gated by Prefs.isAdBlockEnabled) defaults to false, while the JS cosmetic/YouTube ad-block injection (getPrivacyScripts) and the Settings switch default to true. Before the user ever touches the switch, the on-screen state (on) disagrees with the actual network ad-blocking behavior (off), so requests to ad domains are not intercepted even though the UI says ad-block is enabled. Confusing and a real behavioral mismatch.

```
Prefs.isAdBlockEnabled: prefs.getBoolean(KEY_BLOCK_ADS, false) (default false). PrivacyManager.getPrivacyScripts (PrivacyManager.kt:576-577) reads getBoolean("block_ads", true) (default true). SettingsActivity binds switchBlockAds with default true (SettingsActivity.kt:48).
```

**Fix:** Make Prefs.isAdBlockEnabled default true to match the Settings switch and the script gate (or centralize all three on one constant/key).

#### 42. [LOW] FileProvider exposes broad root paths (cache/files/external-files '.')
_file:// / content:// scheme exposure · Security & Privacy · effort S_  
**Location:** `android/app/src/main/res/xml/file_paths.xml  (lines 2-6)`  

The FileProvider is declared with path='.' for external-files, files, and cache, i.e. it exposes the entire app files/cache/external-files roots over content:// URIs. The provider itself is exported=false and grantUriPermissions=true, and I did not find code that mints and shares these content URIs to other apps, so direct external exploitability is limited. Still, granting the whole root is over-broad: any future feature (or a bug in share/save logic) that creates a grant could leak unintended app-internal files (cookies db lives elsewhere, but archives, partial downloads, and cached web content sit under these roots). Principle of least privilege says scope provider paths to the specific sub-directories actually shared (e.g. an 'archives' or 'shared' folder).

```
<external-path name="external_files" path="." />
<files-path name="files" path="." />
<cache-path name="cache" path="." />
```

**Fix:** Replace the '.' roots with narrowly-scoped sub-paths that match exactly what the app shares (e.g. <external-files-path name="archives" path="archives/"/>). Avoid exposing files-path/cache-path roots wholesale.

#### 43. [LOW] Hardcoded user-facing strings bypass localization (Toast / Snackbar / dialog / contentDescription literals)
_i18n · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/ui/MainActivity.kt  (lines 286)`  

Several user-visible strings are hardcoded English literals instead of string resources: a voice-search Toast, a 'Tab closed' Snackbar, two AlertDialog messages ('Close all N tabs?', 'Clear cookies for X?'), and a 'Pinned' contentDescription in item_tab.xml. These will always render in English regardless of the selected locale and are not extractable for translation.

```
MainActivity.kt:286  Toast.makeText(this, "Voice search not available", Toast.LENGTH_SHORT).show()
TabSwitcherActivity.kt:245  Snackbar.make(binding.tabsRecyclerView, "Tab closed", Snackbar.LENGTH_LONG)
TabSwitcherActivity.kt:169  .setMessage("Close all ${tabManager.tabCount} tabs?")
MainActivity.kt:1218  .setMessage("Clear cookies for $domain?")
item_tab.xml:65  android:contentDescription="Pinned"
```

**Fix:** Move each literal into strings.xml (using a placeholder for the dynamic tab count / domain) and reference via getString()/@string. Add them to the translation set.

#### 44. [LOW] Address-bar action icons use 36dp touch targets, below the 48dp accessibility minimum
_accessibility · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `android/app/src/main/res/layout/activity_main.xml  (lines 159-185)`  

The bookmark, refresh and voice-search buttons in the address bar are 36dp x 36dp, and the per-tab close button is 32dp — below Android's recommended 48dp x 48dp minimum touch target. The Play Console pre-launch accessibility report flags sub-48dp interactive elements, and they are harder to hit for users with motor impairments. The bottom-nav buttons (44dp) and find-in-page buttons (40dp) are closer but still under 48dp.

```
<ImageButton android:id="@+id/btnBookmark" android:layout_width="36dp" android:layout_height="36dp" .../>
<ImageButton android:id="@+id/btnRefresh" android:layout_width="36dp" android:layout_height="36dp" .../>
<ImageButton android:id="@+id/btnVoiceSearch" android:layout_width="36dp" android:layout_height="36dp" .../>
// Also item_tab.xml:83 btnCloseTab is 32dp.
```

**Fix:** Increase the interactive targets to at least 48dp (keep the visual icon size via padding), or set a minimum touch area via TouchDelegate / android:minWidth/minHeight 48dp.

#### 45. [LOW] WebView does not apply the system font-scale to web content text zoom
_accessibility · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt  (lines 52-53)`  

WebView text zoom defaults to 100 and is only changed by the in-app zoom buttons (MainActivity.kt:701-711). The user's system 'Font size' accessibility setting (configuration.fontScale) is never propagated to settings.textZoom, so users who enlarge their system font for readability do not get larger web text by default. Web content remains pinch-zoomable, which partially mitigates this, but the default does not respect the OS accessibility preference the way Chrome/Firefox do.

```
setSupportZoom(true)
builtInZoomControls = true
// No settings.textZoom = (100 * resources.configuration.fontScale) and no
// minimumFontSize tied to the accessibility font scale.
```

**Fix:** On WebView creation set settings.textZoom = (resources.configuration.fontScale * 100).toInt() (and update on configuration changes), so web text honors the system font-size accessibility setting.


## iOS (Swift/UIKit) — 60 findings

#### 1. [HIGH] Undefined symbols BrandColors.accentPink / BrandColors.accentPurple — project will not compile
_Build / Compile error · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/DownloadsViewController.swift  (lines 28, 146, 199, 201, 204, 206)`  

DownloadsViewController references BrandColors.accentPink and BrandColors.accentPurple, but BrandColors (Utils/BrandColors.swift) only defines accentPurpleUI, accentPinkUI, accentPurpleCG, accentPinkCG (plus textPrimary/textSecondary/secureGreen/background/toolbar/addressBar). There is no accentPink or accentPurple member defined anywhere in the project (verified by grep across all .swift/.h/.m). These are compile-time 'type has no member' errors. Because Swift compiles the whole module, this single file blocks the entire app from building — it cannot ship.

```
DownloadsViewController.swift:28  navigationItem.rightBarButtonItem?.tintColor = BrandColors.accentPink
DownloadsViewController.swift:146  iconView.tintColor = BrandColors.accentPurple
DownloadsViewController.swift:199  statusLabel.textColor = BrandColors.accentPurple
— but BrandColors.swift only declares: static let accentPurpleUI / accentPinkUI / accentPurpleCG / accentPinkCG (no accentPink, no accentPurple).
```

**Fix:** Add `static let accentPurple` and `static let accentPink` aliases to BrandColors (e.g. = accentPurpleUI / accentPinkUI), or change DownloadsViewController to use the existing *UI members. Then build the target to confirm there are no other unresolved symbols.
**Verifier note:** Fix the symbol mismatch in DownloadsViewController.swift. Simplest, lowest-risk option: change the 6 sites to the existing members — `BrandColors.accentPink` -> `BrandColors.accentPinkUI` (lines 28, 204, 206) and `BrandColors.accentPurple` -> `BrandColors.accentPurpleUI` (lines 146, 199, 201) — matching how every other view controller already uses them. Alternatively, add convenience aliases to BrandColors (`static let accentPurple = accentPurpleUI` / `static let accentPink = accentPinkUI`) if you prefer the shorter names project-wide; if you do, consider migrating the other files for consistency. Then build the HelixBrowser target in Xcode to confirm no further unresolved symbols (note: no Xcode project file is checked into the repo, so a build could not be performed here).

#### 2. [HIGH] No localization infrastructure — entire UI hardcoded in Vietnamese, all 14 promised locales missing
_i18n · Store Readiness, i18n & Accessibility · effort L_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/SettingsViewController.swift  (lines 13, 40-46, 59-125, 162-178)`  

Every user-facing string across the app is a hardcoded Vietnamese string literal. There are no .lproj directories, no Localizable.strings, no .xcstrings catalog, and grep for NSLocalizedString/String(localized:) returns 0 hits. CFBundleDevelopmentRegion is also set to 'vi'. The app is effectively single-language despite the project's cross-platform 14-locale claim; it cannot be translated without a full retrofit and has no RTL/Arabic support.

```
title = "Cài đặt" ... case .privacy: return "Quyền riêng tư & Bảo mật" ... cell.textLabel?.text = "Phiên bản máy tính" ... alert = UIAlertController(title: "Xóa tất cả dữ liệu?", message: "Lịch sử, cookie, cache sẽ bị xóa. Không thể hoàn tác."). grep -rn 'NSLocalizedString|String(localized' over the whole iOS tree = 0 hits; no .lproj / Localizable.strings / .xcstrings exist.
```

**Fix:** Externalize every user-facing string via NSLocalizedString / String(localized:) and a String Catalog (.xcstrings), then provide translations for all 14 target locales including RTL Arabic.
**Verifier note:** Adopt iOS localization infrastructure to reach parity with Android's 14 locales: (1) create a String Catalog (Localizable.xcstrings) and add knownRegions in the xcodeproj for the same 14 locales Android ships (en/default, ar, de, es, fr, hi, id, ja, ko, pt, ru, th, vi, zh-Hans); (2) replace every hardcoded literal in the 7 affected view files (start with SettingsViewController.swift lines 13, 40-46, 59-125, 162-178) with String(localized:) keys — reuse Android's existing keys (settings, privacy_settings, block_trackers, do_not_track, https_upgrade, anti_fingerprinting, block_popups, clear_all_data, etc.) so translations can be lifted directly from android/app/src/main/res/values-*/strings.xml; (3) move the Info.plist usage-description strings (NSCamera/Microphone/PhotoLibraryUsageDescription) into InfoPlist.strings per locale; (4) set CFBundleDevelopmentRegion to a base locale (e.g. en) or keep vi but ensure it is one of several base localizations rather than the only one; (5) add and test RTL layout for Arabic (verify auto-mirroring of the table/toolbars and semantic content attributes). Prioritize this before any non-Vietnamese release.

#### 3. [HIGH] Zero VoiceOver accessibility — icon-only controls have no accessibility labels
_accessibility · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 71-73, 130-134)`  

Every toolbar button (back/forward/reload/home/bookmark/share/tabs/menu), the SSL lock icon, and the tab-cell close button is image-only with no accessibilityLabel. grep for accessibilityLabel/accessibilityHint/isAccessibilityElement over the whole app returns 0 hits. The address bar also has no label. VoiceOver announces these controls as unlabeled buttons, making the app's core navigation unusable for blind users.

```
backButton.setImage(UIImage(systemName: "chevron.left"), for: .normal); tabsButton.setImage(UIImage(systemName: "square.on.square"), ...); menuButton.setImage(UIImage(systemName: "ellipsis.circle"), ...) — none have accessibilityLabel set anywhere.
```

**Fix:** Set accessibilityLabel (and helpful accessibilityHint) on every icon-only button/image, label the address bar, and expose tab-cell controls.
**Verifier note:** Add accessibilityLabel (and where useful accessibilityHint) to every icon-only control. Concretely in BrowserViewController.swift: backButton="Quay lại"/Back, forwardButton="Tiến tới"/Forward, reloadButton="Tải lại"/Reload, homeButton="Trang chủ"/Home, bookmarkButton="Dấu trang"/Bookmark (update label to reflect added/removed state in toggleBookmark/updateBookmarkButton), shareButton="Chia sẻ"/Share, tabsButton="Thẻ" with accessibilityValue=tab count (set in updateTabCount, since tabCountLabel is a visual subview), menuButton="Menu". For sslIcon (a UIImageView), set isAccessibilityElement=true and update accessibilityLabel in updateSSLIcon() to "Kết nối an toàn"/Secure vs "Không an toàn"/Not secure. Give addressBar an accessibilityLabel="Thanh địa chỉ"/Address bar. In TabSwitcherViewController.swift add labels to newTabButton ("Thẻ mới"/New tab) and the cell closeButton ("Đóng thẻ"/Close tab), and expose each tab cell as an accessibility element with its title. Separately, fix the accessibilityValue misuse in StartPageView.swift (lines 176/191): store the URL in a real backing model (e.g. a tag-to-URL dictionary or a small custom view subclass) instead of accessibilityValue, and instead set an appropriate accessibilityLabel/traits on the favorite container so VoiceOver does not read the raw URL.

#### 4. [MEDIUM] Downloads are completely non-functional — no WKDownloadDelegate/navigationResponse handler; DownloadsViewController is never presented
_Missing core feature · Browsing Feature Completeness · effort L_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 413-424, 235-262)`  

There is no download pipeline at all. WKNavigationDelegate has no navigationResponse policy handler and no WKDownloadDelegate, so the webview never converts a non-displayable response into a download — tapping a download link (.zip, .dmg, etc.) just triggers a navigation failure and shows the generic error page. The DownloadsViewController and DataManager download APIs exist but are dead: the page menu never offers a Downloads entry and addDownload is only ever called from inside DownloadsViewController's own swipe-to-delete re-save loop.

```
decidePolicyFor navigationAction does only the HTTPS upgrade then `decisionHandler(.allow)`; there is NO `webView(_:decidePolicyFor navigationResponse:...)`, no WKDownloadDelegate, no `didBecome download`. showMenu (235-262) offers only New tab / Incognito / History / Bookmarks / Settings — no Downloads. Grep: `DownloadsViewController` is referenced nowhere outside its own file; `DataManager.shared.addDownload` is called only within DownloadsViewController.
```

**Fix:** Implement navigationResponse policy detection of non-displayable MIME types (decisionHandler(.download) on iOS 14.5+), a WKDownload delegate that writes the file and records it via DataManager with status updates, and add a Downloads entry to the menu that presents DownloadsViewController.
**Verifier note:** Implement a real download pipeline in BrowserViewController:
1. Add `webView(_:decidePolicyFor navigationResponse:decisionHandler:)` that inspects the response: if `!navigationResponse.canShowMIMEType` (or a non-inline Content-Disposition: attachment), call `decisionHandler(.download)` on iOS 14.5+ (guard older OS with a fallback that hands the URL to UIDocumentInteractionController / share sheet).
2. Implement WKDownloadDelegate: handle `webView(_:navigationResponse:didBecome:)` and `webView(_:navigationAction:didBecome:)`, set the download's delegate to self, provide a destination URL in the app's Documents/temp directory via `decideDestinationUsing`, and on `downloadDidFinish`/`didFailWithError` call the existing CoreDataManager APIs (`addDownload`, `updateDownloadStatus`) so the records reflect real state instead of being write-only stubs.
3. Wire the UI: add a "Tải xuống" entry to showMenu (235-262) that presents `DownloadsViewController` inside a UINavigationController, matching the existing History/Bookmarks pattern, so the already-built VC and storage layer become reachable.
4. Fix the swipe-to-delete re-save loop in DownloadsViewController (clear-then-re-add drops the original status/timestamp); replace with a targeted delete API on CoreDataManager.

#### 5. [MEDIUM] Restore-after-restart never restores the previously active tab — always activates the first tab
_Tab restore correctness · Browsing Feature Completeness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Tabs/TabManager.swift  (lines 156-171)`  

The active tab id is never persisted, so after a cold start the user is always dropped onto the first tab regardless of which tab they were viewing when the app was backgrounded.

```
saveTabs() encodes only the tabs array (and groups). restoreTabs(): `tabs = restored; activeTabId = restored.first?.id ?? ""`.
```

**Fix:** Persist activeTabId in saveTabs() and restore it in restoreTabs(), falling back to the first tab only if the saved id no longer exists.

#### 6. [MEDIUM] Address bar has no search suggestions or URL autocomplete
_Missing feature / UX gap · Browsing Feature Completeness · effort L_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 400-408)`  

The address bar only loads on Enter. There is no editing-changed handler, no suggestions dropdown, no history/bookmark autocomplete, and no search-engine query completions — a baseline expectation for a production browser.

```
The UITextFieldDelegate extension implements only textFieldShouldReturn { ... loadUrl(text) }. No addControlEvent(.editingChanged), no suggestions UI anywhere (grep for suggestion/autocomplete returns nothing).
```

**Fix:** Add a suggestions overlay driven by editing-changed events surfacing matching history/bookmarks (DataManager.searchHistory/searchBookmarks exist) and search-engine suggestions.

#### 7. [MEDIUM] Desktop-site toggle does not affect open tabs and there is no per-page toggle
_Half-implemented feature · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 352-358)`  

applyUserAgent runs once at webView creation. Flipping the Settings desktop-mode switch updates the pref but never re-applies the user agent or reloads existing webViews, so open tabs keep their old UA until their webView is destroyed and lazily recreated. There is also no quick per-page Request-Desktop-Site toggle, which users expect.

```
applyUserAgent(to:) is called only inside createWebView (line 341). SettingsViewController.toggleChanged case 100 just sets `prefs.isDesktopMode = sender.isOn` with no reload/UA-refresh of live webViews.
```

**Fix:** On toggling desktop mode, update customUserAgent on all live webViews and reloadFromOrigin(); add a per-page desktop-site toggle in the page menu.

#### 8. [MEDIUM] Page/text zoom preference (default_zoom) is dead — never read or applied, and there are no zoom controls
_Stubbed feature · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Utils/Prefs.swift  (lines 69-72)`  

Prefs.defaultZoom has a getter/setter but is never read anywhere, no code applies pageZoom / a viewport override / text-size-adjust, and there is no UI to change zoom. Page and text zoom are effectively missing.

```
Grep across the target shows the only `defaultZoom` occurrence is its declaration in Prefs; no reader, no `pageZoom`, no viewport injection.
```

**Fix:** Apply pageZoom (iOS 14+) from Prefs.defaultZoom on each webView, add explicit zoom and text-zoom controls, and a Settings entry for the default.

#### 9. [MEDIUM] No find-in-page, reader mode, or print / save-as-PDF
_Missing features · Browsing Feature Completeness · effort L_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 235-262)`  

Several baseline browser features are entirely absent. WKWebView's built-in find interaction (isFindInteractionEnabled, iOS 16+) is not enabled; there is no reader/distiller mode; and there is no Print or Save-as-PDF path (createPDF / UIPrintInteractionController) — the only print access is incidental via the system share sheet.

```
Grep for find/reader/readability/print/UIPrintInteractionController/createPDF/pdf returns nothing functional. showMenu offers only New tab / Incognito / History / Bookmarks / Settings.
```

**Fix:** Enable WKWebView.isFindInteractionEnabled, add a reader-mode distiller, and add Print/Save-as-PDF (WKWebView.createPDF / UIPrintInteractionController) to the page menu.

#### 10. [MEDIUM] WebRTC camera/microphone capture is not granted — media-capture permission handler missing despite advertised support
_Missing handler · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 495-515)`  

Info.plist advertises camera/microphone/photo-library usage for WebRTC and file upload, but WKUIDelegate implements only createWebView/alert/confirm. On iOS 15+ getUserMedia requires implementing requestMediaCapturePermissionFor or the request is denied, so the stated WebRTC support silently fails. No requestMediaCapturePermission handler exists.

```
The WKUIDelegate extension contains only createWebViewWith, runJavaScriptAlertPanel, and runJavaScriptConfirmPanel. No `webView(_:requestMediaCapturePermissionFor:initiatedByFrame:type:decisionHandler:)`. Info.plist declares NSCameraUsageDescription/NSMicrophoneUsageDescription 'cho WebRTC'.
```

**Fix:** Implement requestMediaCapturePermissionFor to grant camera/mic for WebRTC, matching the capability already advertised in Info.plist; verify the native file-input flow for uploads.

#### 11. [MEDIUM] JS dialogs can hang the page; window.prompt() is unhandled
_Robustness / missing handler · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 503-514)`  

JS alert/confirm panels are presented directly via present(); if a JS dialog fires while another modal is already presented (action-sheet menu, share sheet, a prior alert), present is a no-op and the completion handler is never called, permanently stalling the page's JS. There is also no runJavaScriptTextInputPanel handler, so window.prompt() always returns null.

```
runJavaScriptAlertPanel/runJavaScriptConfirmPanel call `present(alert, animated: true)` on self with the completion handler inside the action; if present fails the handler never runs. No runJavaScriptTextInputPanelWithPrompt implementation.
```

**Fix:** Present JS dialogs from the top-most presented controller (or queue them) and always invoke the completion handler even on present failure; add a prompt (text-input) handler.

#### 12. [MEDIUM] No file-download support at all — DownloadsViewController is vestigial, core browser feature missing
_Missing core feature · Correctness & Crashes · effort L_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 412-491 (WKNavigationDelegate))`  

The WKNavigationDelegate implements only decidePolicyFor navigationAction; there is no decidePolicyFor navigationResponse and no WKDownloadDelegate/WKDownload handling. The browser therefore cannot download files — tapping a download link either does nothing or WebKit tries to render the bytes inline. DataManager.addDownload is never called from any download flow (grep shows the only caller is DownloadsViewController's delete/re-save loop), so the Downloads list is always empty. DownloadsViewController itself is never instantiated/presented anywhere (no caller). A production browser must support downloads.

```
grep across the codebase: the only references to 'download' are DataManager.addDownload/getDownloads and DownloadsViewController; no `func webView(_:decidePolicyFor navigationResponse:` and no `WKDownloadDelegate` exist. DownloadsViewController is not referenced by any other file.
```

**Fix:** Implement WKNavigationDelegate.decidePolicyFor navigationResponse to detect attachments / non-renderable MIME types and start a WKDownload (iOS 14.5+) with a WKDownloadDelegate that writes to the app's Documents/Caches and records progress via DataManager. Wire DownloadsViewController into the menu.
**Verifier note:** Implement WKNavigationDelegate.webView(_:decidePolicyFor navigationResponse:decisionHandler:) in BrowserViewController.swift to inspect the response: if it is an attachment (Content-Disposition) or a non-renderable MIME type (response.canShowMIMEType == false), return .download. Adopt WKNavigationResponse-based download via navigationResponse.download(...) / WKDownloadDelegate (iOS 14.5+), implementing download(_:decideDestinationUsing:suggestedFilename:completionHandler:) to write into the app's Documents (or Caches) directory, and downloadDidFinish/didFailWithError to update state. Call DataManager.addDownload at download start and updateDownloadStatus on completion/failure so the list reflects reality. Finally, add a "Tải xuống" (Downloads) entry to showMenu() that presents DownloadsViewController in a UINavigationController, mirroring the existing History/Bookmarks/Settings menu actions, so the already-built UI becomes reachable.

#### 13. [MEDIUM] HTTPS-upgrade rebuilds request with URLRequest(url:), dropping POST method/body and offering no http fallback
_Broken navigation / logic error · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 413-424)`  

decidePolicyFor cancels every http navigationAction and reloads as https via `webView.load(URLRequest(url: httpsUrl))`. Two real defects: (1) URLRequest(url:) creates a fresh GET request, discarding the original HTTP method, body and headers — so submitting any form (POST) on an http page silently turns into a GET and loses the payload. (2) There is no fallback: for genuinely http-only hosts the original request is permanently cancelled and the https reload fails, making such sites unreachable with no way to proceed. It also indiscriminately upgrades subresource/iframe requests, not just top-level navigations.

```
decidePolicyFor:
  if let url = navigationAction.request.url, ... url.scheme == "http" {
      var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
      comps?.scheme = "https"
      if let httpsUrl = comps?.url {
          decisionHandler(.cancel)
          webView.load(URLRequest(url: httpsUrl))   // loses method/body, no fallback
          return
      }
  }
```

**Fix:** Only upgrade main-frame navigations (navigationAction.targetFrame?.isMainFrame == true) and only GET requests; preserve the original request when upgrading. Track upgraded hosts and, on https failure (didFailProvisionalNavigation), fall back to http for that navigation instead of cancelling forever.
**Verifier note:** Gate the upgrade so it only affects safe, top-level GET navigations and add a real fallback path:
1. Add guards before upgrading: only proceed when navigationAction.targetFrame?.isMainFrame == true AND navigationAction.request.httpMethod == "GET" (or nil). This eliminates the POST payload-loss bug and the subresource/iframe hijacking by leaving those requests to decisionHandler(.allow).
2. Track hosts you have already attempted to upgrade (e.g. a Set<String> of upgraded hosts). In didFailProvisionalNavigation, if the failed URL's host was upgraded-to-https and the failure is a connection/SSL error, reload the original http URL instead of showing the error page, and remember not to re-upgrade that host for the session. This makes genuinely http-only sites reachable rather than a permanent dead end.
3. Optionally, when reconstructing the request, preserve the original (copy navigationAction.request into a mutable URLRequest and only swap the scheme) so any headers survive — though limiting to GET already avoids the body-loss issue.

#### 14. [MEDIUM] Ad-block content rules not applied to the first tab (async compile vs synchronous webView creation)
_Race condition / logic error · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 43-47, 328-330)`  

viewDidLoad kicks off AdBlockEngine.shared.compileRules { _ in } (asynchronous; WKContentRuleListStore compiles on a background queue) and then immediately calls loadActiveTab(), which synchronously creates the first WKWebView and calls AdBlockEngine.shared.apply(to: config). apply() only adds the rule list if ruleList != nil, but on a cold start ruleList is still nil because compilation hasn't finished. So the initial tab (and any webView created before compilation completes) loads with NO WKContentRuleList ad-blocking — ads are not blocked on the very first page the user sees. apply() is also never retried once compilation finishes.

```
viewDidLoad:
  if Prefs.shared.isAdBlockEnabled { AdBlockEngine.shared.compileRules { _ in } }
  loadActiveTab()   // creates webView synchronously
AdBlockEngine.apply:
  func apply(to config: WKWebViewConfiguration) { if let ruleList = ruleList { config.userContentController.add(ruleList) } }   // ruleList still nil here on cold start
```

**Fix:** Compile rules before creating the first webView, or in the compileRules completion add the compiled list to all existing tabs' webViews (webView.configuration.userContentController.add). Alternatively gate loadActiveTab() on the compile completion when ad-block is enabled.

#### 15. [MEDIUM] Progress observer lost when switching back to an already-created tab
_Broken observer / UX · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 283-300, 343-347)`  

There is a single shared progressObserver. loadActiveTab() always invalidates it (line 288). When the active tab already has a webView (the `if let existingWebView = tab.webView` branch), no new observer is attached — the observer is only created inside createWebView(), which runs only for brand-new webViews. Consequently, after switching to a tab whose webView already exists, the progress bar no longer reflects that tab's loading progress. Each tab also needs its own observer; a single property cannot track multiple live webViews.

```
loadActiveTab:
  progressObserver?.invalidate()
  if let existingWebView = tab.webView { currentWebView = existingWebView }   // no observer re-created
  else { let webView = createWebView(...) ... }
createWebView:
  progressObserver = webView.observe(\.estimatedProgress, ...) { ... }
```

**Fix:** Re-attach the estimatedProgress observation whenever currentWebView changes (move the observe() call out of createWebView into loadActiveTab after currentWebView is set), or store the observation per BrowserTab.

#### 16. [MEDIUM] Non-functional privacy/tab settings shown to the user (third-party cookies, tracker counter, zoom, mute)
_Dead feature / privacy gap · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Utils/Prefs.swift  (lines 39-42, 69-72; TabManager.swift:10-13; SettingsViewController.swift:82,114)`  

Several user-visible toggles/stats do nothing. (a) isBlockThirdPartyCookies is toggled in Settings (tag 202) but is never read by any webView configuration — no cookie policy is applied, so the privacy promise is false. (b) trackersBlocked is only ever read (Settings and StartPage 'X trackers blocked'); it is never incremented anywhere, so the count is permanently 0 and the StartPage stats view stays hidden — a fake/dead privacy metric. (c) Prefs.defaultZoom is never applied to any webView. (d) BrowserTab.isMuted toggles in TabManager.muteTab but is never applied to webView audio. These are honesty/robustness gaps for a privacy-branded browser.

```
grep isBlockThirdPartyCookies → only Prefs getter/setter + Settings toggle; never applied to WKWebViewConfiguration.
grep trackersBlocked → only read in SettingsViewController:114 and StartPageView:58; no `trackersBlocked += 1` / `= ` write anywhere.
grep defaultZoom → only Prefs declaration, never used.
```

**Fix:** Wire isBlockThirdPartyCookies to an actual cookie policy, increment trackersBlocked from the ad-block/tracker scripts via a WKScriptMessageHandler (or remove the stat), apply defaultZoom via pageZoom/viewport, and apply isMuted to webViews. At minimum, remove settings that have no effect so the UI doesn't mislead users.

#### 17. [MEDIUM] File downloads are completely non-functional — no WKDownloadDelegate or navigationResponse handling
_Missing core feature · Production Quality & Robustness · effort L_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 410-515 (WKNavigationDelegate / WKUIDelegate extensions))`  

The app ships a full Downloads UI (DownloadsViewController + DataManager.addDownload/getDownloads) but there is no mechanism that ever starts a download. There is no `webView(_:decidePolicyFor navigationResponse:)`, no `WKDownloadDelegate`, and no `webView(_:navigationAction:didBecome download:)` implementation anywhere in the codebase. `DataManager.addDownload` is only ever invoked inside the swipe-to-delete re-save loop in DownloadsViewController (line 107), never from a real navigation. A user who taps a downloadable link (PDF, ZIP, image, etc.) will either have it rendered inline or get a blank page; nothing is saved to disk and nothing appears in the Downloads list. Downloading files is table-stakes for a production browser.

```
No occurrence of `WKDownload`, `decidePolicyFor navigationResponse`, or `didBecome download` exists (grep returns only the Downloads UI). The only addDownload call: DownloadsViewController.swift:107 `DataManager.shared.addDownload(url: d["url"] ?? "", ...)` inside the delete-and-resave loop.
```

**Fix:** Implement `webView(_:decidePolicyFor navigationResponse:decisionHandler:)` to return `.download` for non-renderable MIME types, conform to `WKDownloadDelegate` (`download(_:decideDestinationUsing:suggestedFilename:completionHandler:)`, progress, completion/failure), persist real entries via DataManager, and write files to the Documents/Downloads directory. Without this, the Downloads feature is dead code.
**Verifier note:** Implement download support so the existing Downloads UI has a real producer: (1) Add webView(_:decidePolicyFor navigationResponse:decisionHandler:) and return .download when !navigationResponse.canShowMIMEType (or for known attachment dispositions); allow otherwise. (2) Implement webView(_:navigationAction:didBecome download:) and webView(_:navigationResponse:didBecome download:) to set self as the WKDownloadDelegate. (3) Conform to WKDownloadDelegate: download(_:decideDestinationUsing:suggestedFilename:completionHandler:) writing into a Documents/Downloads directory created via FileManager, plus downloadDidFinish(_:) and download(_:didFailWithError:resumeData:) to call DataManager.updateDownloadStatus (currently never invoked) so the hardcoded status:"downloading" actually transitions to completed/failed. (4) Call DataManager.addDownload at download start from the real navigation path. (5) Separately, fix the O(n) clear-and-re-add delete loop in DownloadsViewController.swift:105-118 with a targeted delete. Until at least steps 1-4 land, the Downloads feature remains non-functional dead code.

#### 18. [MEDIUM] No TLS/certificate error handling — lock icon is shown for any https URL regardless of cert validity
_Security UX / misleading indicator · Production Quality & Robustness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 391-395, 412-424)`  

There is no `webView(_:didReceive challenge:completionHandler:)` implementation, so the app cannot present a certificate-error interstitial or let the user make an informed decision — invalid-cert loads simply fail with a generic error page. Worse, the SSL indicator is purely scheme-based: `updateSSLIcon` shows the green `lock.fill` whenever `url?.scheme == "https"`, even if the connection used an expired/self-signed/invalid certificate. This is an actively misleading security indicator in a browser whose marketing is 'An toàn. Riêng tư.' (Safe. Private.). A user on a MITM'd network sees a green lock.

```
updateSSLIcon(url:): `let isSecure = url?.scheme == "https"` then `sslIcon.image = UIImage(systemName: isSecure ? "lock.fill" : "info.circle")`. No `didReceive challenge` method exists anywhere (grep confirms).
```

**Fix:** Implement `webView(_:didReceive:completionHandler:)` to handle `NSURLAuthenticationMethodServerTrust`, evaluate the trust, and show a proper cert-warning interstitial on failure. Drive the lock icon from actual `webView.hasOnlySecureContent` / trust evaluation rather than the URL scheme string.
**Verifier note:** Two fixes: (1) Drive the lock icon from real connection state, not the scheme string. After didFinish, set the secure indicator from `webView.hasOnlySecureContent` (lock only when true; show a degraded/warning indicator for https pages with insecure subresources) and avoid showing a full green lock during the provisional phase. (2) Implement `webView(_:didReceive challenge:completionHandler:)` for `NSURLAuthenticationMethodServerTrust`: evaluate the server trust with SecTrustEvaluateWithError; on success call completionHandler(.useCredential, URLCredential(trust:)) (or .performDefaultHandling); on failure call completionHandler(.cancelAuthenticationChallenge) and present an explicit cert-error interstitial that explains the specific problem and lets the user make an informed choice — do NOT auto-accept invalid certs. Do not add a handler that returns a credential unconditionally, as that would convert this medium indicator/UX issue into a real high-severity MITM bypass.

#### 19. [MEDIUM] armv7 device capability requirement blocks modern 64-bit-only iOS devices
_Store rejection / install failure · Production Quality & Robustness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 27-30)`  

Info.plist requires the `armv7` device capability. armv7 is the 32-bit ARM architecture; iOS dropped 32-bit support after iOS 10 and all devices running iOS 11+ are 64-bit (arm64) only. Requiring `armv7` means the App Store will refuse to install on any current iPhone/iPad, and modern toolchains do not even build an armv7 slice. This is a guaranteed install/submission failure.

```
UIRequiredDeviceCapabilities array: `<string>armv7</string>`
```

**Fix:** Change the required capability to `arm64` (or remove it entirely if no specific capability is needed). Verify the deployment target is iOS 13+ given WKWebView/scene usage.
**Verifier note:** Replace the armv7 entry with arm64 in UIRequiredDeviceCapabilities (Info.plist lines 28-30: <array><string>arm64</string></array>), or simpler, remove the UIRequiredDeviceCapabilities key entirely since the app needs no special hardware capability and the deployment target already gates supported devices. Because the repo currently has no Xcode project/xcconfig, also ensure that when these sources are wired into a project the IPHONEOS_DEPLOYMENT_TARGET is set to iOS 13.0+ to match the UIApplicationSceneManifest/SceneDelegate and WKWebView usage; that deployment target alone makes the device-architecture requirement redundant.

#### 20. [MEDIUM] ATS fully disabled with NSAllowsArbitraryLoads while HTTPS upgrade is merely an optional toggle
_Security weakening / store review · Production Quality & Robustness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 44-48)`  

`NSAppTransportSecurity.NSAllowsArbitraryLoads = true` globally disables App Transport Security, permitting all cleartext HTTP. While a browser legitimately needs to load arbitrary user-entered HTTP URLs (and Apple grants an exception for that), the global blanket flag without the more appropriate `NSAllowsArbitraryLoadsInWebContent` (or a justification) commonly triggers App Store review questions and weakens security for any non-WebView networking (e.g. the favicon and thumbnail `URLSession` calls now run over plaintext silently). Combined with HTTPS-upgrade being an opt-out preference, default behavior allows downgrade.

```
`<key>NSAllowsArbitraryLoads</key><true/>` with no `NSAllowsArbitraryLoadsInWebContent` scoping.
```

**Fix:** Prefer `NSAllowsArbitraryLoadsInWebContent = true` so only WebView traffic is exempt while app-level requests (favicons, etc.) stay TLS-protected, and be ready to justify it in App Review.

#### 21. [MEDIUM] Inactive-tab suspension exists but is never invoked; no memory-warning handling — OOM/jetsam risk with many tabs
_Memory / performance · Production Quality & Robustness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Tabs/TabManager.swift  (lines 180-187)`  

`TabManager.suspendInactiveTabs()` is implemented to tear down WKWebViews for tabs idle >10 min, but it is never called from anywhere (grep shows the only reference is its own definition). There is also no `didReceiveMemoryWarning` override in BrowserViewController and no `applicationDidReceiveMemoryWarning` handling. Each tab keeps a live WKWebView in `BrowserTab.webView` indefinitely (a WKWebView spawns its own content process and consumes tens of MB). With many tabs open, memory grows unbounded and iOS will jetsam-kill the app. The `isSuspended` flag is persisted but the suspension logic that sets it is dead.

```
TabManager.swift:180 `func suspendInactiveTabs()` — no caller anywhere in the codebase; no memory-warning override exists (grep for `didReceiveMemoryWarning` returns nothing).
```

**Fix:** Call `suspendInactiveTabs()` from a memory-warning observer and/or a timer, and override `didReceiveMemoryWarning` to drop non-active WebViews. Reload suspended tabs lazily on activation.

#### 22. [MEDIUM] Progress observer is invalidated on tab switch but only recreated for new WebViews — progress bar breaks for reused tabs
_Resource cleanup / broken feature · Production Quality & Robustness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 283-300, 343-347)`  

`loadActiveTab()` calls `progressObserver?.invalidate()` on every tab switch (line 288), but the observer is only (re)created inside `createWebView()` (line 343). When switching to a tab whose WKWebView already exists (`tab.webView != nil`, line 290), `createWebView` is NOT called, so no new observer is registered. From that point on the loading progress bar is dead for that tab — navigations show no progress. There is also a single shared `progressObserver` for a multi-tab model, so it can only ever track one webview at a time and is never invalidated in a deinit.

```
loadActiveTab: line 288 `progressObserver?.invalidate()`; the `if let existingWebView = tab.webView { currentWebView = existingWebView }` branch (lines 290-291) re-uses the webview without re-installing the KVO observer that line 288 just tore down.
```

**Fix:** Re-attach the `estimatedProgress` observer to the current webview every time `loadActiveTab` selects/reuses a webview, not only in `createWebView`. Or attach the observer per-tab and store it on BrowserTab.

#### 23. [MEDIUM] No WebView navigation-state restoration after process death — back/forward history and scroll lost
_State restoration · Production Quality & Robustness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Tabs/TabManager.swift  (lines 156-176)`  

Tab restoration persists only the last `url` string per tab. WKWebView's `interactionState` (the documented mechanism, iOS 15+, that encodes the full back/forward list and scroll position) is never captured or restored. After the OS terminates the app in the background and the user relaunches, every restored tab loses its entire navigation history and scroll position and reloads from a single URL. For a browser that advertises tab restoration, this is a noticeable UX regression vs Safari/Chrome.

```
saveTabs()/restoreTabs() only JSON-encode `BrowserTab` whose Codable keys (BrowserTab.swift:22-24) are url/title/etc. — no `interactionState`. `webView` is explicitly non-codable (BrowserTab.swift:18-19).
```

**Fix:** Capture `webView.interactionState` per tab (Data) on background/save and restore it via `webView.interactionState = ...` when recreating the WebView, instead of reloading from a bare URL.

#### 24. [MEDIUM] No TLS/certificate challenge handling; SSL lock icon is purely cosmetic (scheme-only)
_TLS / Certificate Validation · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 391-395)`  

The navigation delegate implements no webView(_:didReceive:completionHandler:) (URLAuthenticationChallenge) method. WebKit's default rejects invalid certificates (good), but the app also provides NO certificate-error UI: when a cert is invalid the navigation just fails into the generic error page with no indication it was a security failure and no advanced/learn-more path — a production browser must clearly distinguish a TLS failure from a generic network error. Worse, the security indicator (updateSSLIcon) decides 'secure' solely from url?.scheme == \"https\", showing a green lock for ANY https URL regardless of actual certificate validity, mixed content, or EV/extended state. This gives users a false sense of security: a page can be marked 'secure' (green lock) when WebKit later downgrades trust, and there is no mixed-content detection at all.

```
private func updateSSLIcon(url: URL?) {
    let isSecure = url?.scheme == "https"
    sslIcon.image = UIImage(systemName: isSecure ? "lock.fill" : "info.circle")
    sslIcon.tintColor = isSecure ? BrandColors.secureGreen : BrandColors.textSecondary
}
```

**Fix:** Implement didReceive challenge to surface certificate failures distinctly (and never silently call completionHandler(.useCredential, URLCredential(trust:)) to bypass). Drive the lock indicator off WebKit's hasOnlySecureContent / serverTrust state, not the URL scheme, and detect/flag mixed content.
**Verifier note:** Two separate fixes:

1) Drive the lock indicator off real trust state, not the URL scheme. After didFinish, query WebKit's mixed-content state via webView.hasOnlySecureContent and only show the green lock when scheme is https AND hasOnlySecureContent is true; show a 'not fully secure' state (e.g., neutral/warning icon) for HTTPS pages with mixed content, and the info/insecure icon for http. Do not set 'secure' in didStartProvisionalNavigation before the page has loaded/validated — set a neutral state during provisional navigation and only commit the secure indicator on didFinish (or didCommit) based on actual state.

2) Distinguish TLS failures in the error UI. In didFailProvisionalNavigation, inspect the NSError domain/code (e.g. NSURLErrorDomain codes like NSURLErrorServerCertificateUntrusted, NSURLErrorServerCertificateHasBadDate, NSURLErrorServerCertificateHasUnknownRoot, NSURLErrorServerCertificateNotYetValid, NSURLErrorSecureConnectionFailed) and render a distinct certificate-error page that clearly states this is a security/certificate problem (not a generic connectivity error). Keep it informational; do not offer a one-tap 'proceed anyway'.

3) If a didReceive(challenge) handler is added later, it MUST default to completionHandler(.performDefaultHandling, nil) and must NEVER unconditionally call completionHandler(.useCredential, URLCredential(trust: challenge.protectionSpace.serverTrust!)), which would silently accept invalid certificates and turn this medium-severity UI issue into a critical MITM vulnerability.

4) Reconsider Info.plist NSAllowsArbitraryLoads=true — it broadly weakens transport security; scope ATS exceptions narrowly or remove the blanket allow if not strictly required.

#### 25. [MEDIUM] "Clear all browsing data" is incomplete — leaves history-equivalent and persisted data behind
_Privacy / Data Clearing · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/SettingsViewController.swift  (lines 177-186)`  

The 'Xóa tất cả dữ liệu duyệt web' (Clear all browsing data) action clears only WKWebsiteDataStore.default() and the helix_history key. It does NOT clear: bookmarks (helix_bookmarks), the downloads list (helix_downloads), saved/restored tabs (helix_saved_tabs and helix_saved_groups — which contain visited URLs and titles, effectively a second history copy), or the trackers_blocked counter. The dialog text explicitly promises 'Lịch sử, cookie, cache sẽ bị xóa' but saved tabs (a full record of open pages and URLs) survive, so a user expecting privacy after 'clear all' still has their browsing recorded in UserDefaults. PrivacyManager.clearAllData also only touches the default data store, not any non-persistent incognito stores (acceptable) nor the in-memory tab webviews.

```
PrivacyManager.shared.clearAllData {
    UserDefaults.standard.removeObject(forKey: "helix_history")
}
// clearAllData:
let dataStore = WKWebsiteDataStore.default()
dataStore.removeData(ofTypes: types, modifiedSince: Date(timeIntervalSince1970: 0)) { completion() }
```

**Fix:** On clear-all, also remove helix_saved_tabs, helix_saved_groups, helix_downloads, and (optionally, with a checkbox) helix_bookmarks; reset open tabs/webviews; and reload the active webview. Use DataManager.clearAllData() plus the saved-tabs keys so the promise in the dialog is actually fulfilled.
**Verifier note:** In showClearDataAlert's destructive action, after PrivacyManager.shared.clearAllData completes, also remove the persisted browsing records that the dialog implicitly promises to erase: UserDefaults.standard.removeObject(forKey: "helix_saved_tabs") and "helix_saved_groups" (these contain visited URLs/titles), plus "helix_downloads". Prefer routing through DataManager.shared.clearAllData() (history + bookmarks + downloads) combined with explicit removal of the two saved-tabs keys, since DataManager.clearAllData() does not touch TabManager's keys. Reset TabManager's in-memory tabs/webviews and open a fresh start tab so the live session reflects the clear. Leave helix_bookmarks deletion behind an explicit opt-in checkbox (bookmarks are intentional saved data, not history). Optionally reset the trackers_blocked counter. Alternatively, if retaining open tabs across clear-all is intended, soften the dialog wording so it no longer promises that all history is removed.

#### 26. [MEDIUM] "Block third-party cookies" setting is a no-op (never applied to any WKWebView)
_Privacy / Cookie Isolation · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Engine/PrivacyManager.swift  (lines 190-208)`  

Prefs.isBlockThirdPartyCookies is read and written only in SettingsViewController (tag 202) and stored in Prefs; it is never consumed anywhere that configures a WKWebView. applyPrivacySettings does not reference it, and createWebView does not act on it. The toggle defaults to ON and shows the subtitle 'Ngăn cookie theo dõi' (prevents tracking cookies), but no cookie policy is actually enforced — third-party cookies are sent/stored exactly as if the toggle were off. This is a privacy feature that silently does nothing, misleading users.

```
grep result: isBlockThirdPartyCookies appears only in Prefs.swift (get/set) and SettingsViewController.swift (display + 'case 202'); zero references in PrivacyManager/BrowserViewController/createWebView.
```

**Fix:** Actually enforce the setting: e.g. set config.defaultWebpagePreferences / use WKHTTPCookieStore policy, or for iOS 17+ apply per-site cookie partitioning, or at minimum remove the toggle so it doesn't misrepresent protection.

#### 27. [MEDIUM] Incognito tabs share the same WKProcessPool as normal tabs
_Private Mode / Isolation · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 317-326)`  

createWebView assigns the single shared processPool (the BrowserViewController.processPool instance) to every webview configuration, including incognito tabs, before optionally swapping in a non-persistent data store. Sharing a WKProcessPool across the incognito boundary means incognito and normal web content can run in the same web content process, weakening process-level isolation between a private session and the persistent session. A correct private-mode implementation should isolate incognito tabs into their own process pool (and they should never share a pool with persistent tabs). The non-persistent dataStore is correct, but the shared process pool undermines the isolation guarantee.

```
private let processPool = WKProcessPool()
...
config.processPool = processPool
...
if isIncognito {
    config.websiteDataStore = WKWebsiteDataStore.nonPersistent()
}
```

**Fix:** Use a separate WKProcessPool for incognito tabs (one shared pool for persistent tabs, a distinct pool for the private session) so private and normal content never share a web content process.

#### 28. [MEDIUM] Popup/new-window handler opens arbitrary URLs in a new tab with no user-gesture gating
_Popup / Open-Redirect Abuse · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 496-501)`  

createWebViewWith (window.open / target=_blank) unconditionally calls tabManager.createTab(url:) for any navigationAction.request.url, with no check on navigationAction.navigationType or user activation. Native-side popup blocking relies entirely on the injected popupBlockerScript (which is only added when isBlockPopupsEnabled and is bypassable from page JS since it overrides window.open in the page's own context). A malicious page can spawn unlimited background tabs to arbitrary URLs (tab-flooding / forced navigation / ad redirects) without a user gesture, even with the popup blocker enabled, because this native handler does not consult the gesture or the block-popups preference at all.

```
func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
    if let url = navigationAction.request.url {
        tabManager.createTab(url: url.absoluteString)
    }
    return nil
}
```

**Fix:** Gate new-tab creation on Prefs.isBlockPopupsEnabled and/or navigationAction.navigationType == .linkActivated (user-initiated). Optionally rate-limit programmatic window.open. Do not rely solely on page-context JS for popup suppression.

#### 29. [MEDIUM] Address bar accepts file:// and about: schemes, enabling local-file access from the URL bar
_file:// Scheme Exposure · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Utils/UrlUtils.swift  (lines 17)`  

formatUrl passes through file:// and about: URLs verbatim, and loadUrl then constructs URLRequest(url:) and calls webView.load on them. While WKWebView.load(URLRequest) for file URLs is restricted by sandbox/read-access rules (loadFileURL is the sanctioned API and is not used), permitting file:// at the address-bar layer is unnecessary attack surface for a browser and can expose app-bundle/sandbox paths if read access is ever broadened. about: schemes other than about:blank can also produce confusing/abusable states. There is no allowlist constraining which schemes the user/page may load.

```
if trimmed.hasPrefix("file://") || trimmed.hasPrefix("about:") { return trimmed }
```

**Fix:** Drop file:// (and restrict about: to about:blank) from formatUrl's pass-through; reject or search-redirect non http(s)/helix schemes entered in the address bar. If local file viewing is a real feature, route it through loadFileURL(_:allowingReadAccessTo:) with a tightly scoped directory.

#### 30. [MEDIUM] No download handling — WKDownloadDelegate absent; downloads never work and bypass MIME/path safety
_Downloads · Security & Privacy · effort L_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 412-424)`  

There is a full Downloads UI (DownloadsViewController, DownloadCell, DataManager download CRUD) but nothing ever creates a download: there is no WKDownloadDelegate, no decidePolicyFor navigationResponse handler, and decidePolicyFor navigationAction always allows. Non-displayable responses (Content-Disposition: attachment, binaries) are handed to WebKit's default behavior with no destination path control, no filename sanitization, and no MIME-type checking — so the documented download feature is effectively dead code, and any download path that does occur is uncontrolled (no path-traversal/filename sanitization since the app never sets the destination). For a production browser this is a missing core feature plus a latent safety gap.

```
decidePolicyFor navigationAction only does HTTPS-upgrade then decisionHandler(.allow); no decidePolicyFor navigationResponse, no WKDownloadDelegate, no download(_:decideDestinationUsing:...) anywhere in the codebase.
```

**Fix:** Implement decidePolicyFor navigationResponse to detect attachments and call .download, adopt WKDownloadDelegate, and in decideDestinationUsing sanitize the suggested filename (strip path separators / '..'), write to a sandboxed Downloads dir, and validate MIME type before presenting.

#### 31. [MEDIUM] Missing PrivacyInfo.xcprivacy privacy manifest (App Store requirement since May 2024)
_store-readiness · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/  (lines n/a (file absent))`  

No privacy manifest exists anywhere in the iOS tree. The app uses required-reason APIs (pervasive UserDefaults access in Prefs.swift and CoreDataManager.swift) and persists browsing data, but ships no manifest declaring NSPrivacyAccessedAPITypes or its data-collection categories. App Store Connect now warns/rejects binaries using required-reason APIs without a manifest.

```
find ... -name PrivacyInfo.xcprivacy returns nothing. Prefs.swift and CoreDataManager.swift use UserDefaults.standard throughout (a required-reason API).
```

**Fix:** Add PrivacyInfo.xcprivacy declaring the UserDefaults required-reason API (reason CA92.1 or equivalent), any file-timestamp/disk-space reasons used, and the data-collection / tracking categories.
**Verifier note:** When the iOS app is assembled into an actual Xcode project for App Store submission, add a PrivacyInfo.xcprivacy to the app target declaring the one required-reason API actually used: NSPrivacyAccessedAPICategoryUserDefaults with reason CA92.1 (access limited to the app's own data). Do NOT add file-timestamp/disk-space/system-boot-time reasons — none of those APIs are used in the current code. For NSPrivacyCollectedDataTypes, the app currently stores history/bookmarks/downloads only on-device via UserDefaults with no evidence of off-device transmission, so set NSPrivacyTracking to false and declare collected-data types only if/when telemetry or network sync is added. This is a pre-submission compliance task, not an active runtime defect, so it can be tracked alongside creating the missing .xcodeproj/build configuration rather than treated as a release blocker today.

#### 32. [MEDIUM] NSAllowsArbitraryLoads = true disables App Transport Security globally
_store-readiness · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 44-48)`  

A blanket ATS exemption is applied at the app level, disabling ATS for all networking including the app's own URLSession calls (e.g. favicon fetches). This triggers App Review scrutiny and requires written justification, and for a browser it is the wrong scope.

```
<key>NSAppTransportSecurity</key>\n<dict>\n  <key>NSAllowsArbitraryLoads</key>\n  <true/>\n</dict>
```

**Fix:** Replace the global NSAllowsArbitraryLoads with NSAllowsArbitraryLoadsInWebContent=true (scoped to WKWebView), keeping ATS enforced for the app's own networking.
**Verifier note:** Replace the blanket NSAllowsArbitraryLoads=true with the WebView-scoped exemption so ATS stays enforced for the app's own URLSession traffic while WKWebView can still load arbitrary HTTP/HTTPS sites:

<key>NSAppTransportSecurity</key>
<dict>
  <key>NSAllowsArbitraryLoadsInWebContent</key>
  <true/>
</dict>

This is Apple's documented browser pattern and removes the App Review justification burden. If any specific app-owned (non-WebView) endpoint genuinely needs HTTP, add a narrow NSExceptionDomains entry for just that host rather than re-enabling the global flag. Note the favicon fetch in TabSwitcherViewController.swift already uses an HTTPS Google endpoint, so it will continue to work under enforced ATS.

#### 33. [MEDIUM] Privacy leak: favicons of incognito tabs are fetched from Google over the shared persistent URLSession
_privacy · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 445)`  

didFinish runs for every tab and sets tab.faviconUrl unconditionally (the incognito guard only protects history). The favicon URL points at google.com/s2/favicons?domain=<host>, and the tab switcher fetches it with URLSession.shared (the default persistent session with cookies/cache). For incognito tabs the WKWebView uses a non-persistent data store, but this favicon path leaks the visited domain of private-browsing tabs to Google over a cookied session, undermining the advertised privacy feature.

```
BrowserViewController.swift:445 tab.faviconUrl = UrlUtils.getFaviconUrl(url) (runs for all tabs; incognito guard is only at line 454 for history). UrlUtils.getFaviconUrl => "https://www.google.com/s2/favicons?domain=<host>&sz=64". TabSwitcherViewController.swift:227-228 URLSession.shared.dataTask(with: faviconUrl).
```

**Fix:** Do not derive/fetch favicons for incognito tabs, and route favicon requests through the tab's WKWebsiteDataStore or an ephemeral URLSession with no persistent cookies/cache.
**Verifier note:** Two complementary fixes:

1) Do not derive a favicon URL for incognito tabs. In BrowserViewController.swift:445, guard the assignment, e.g. `if !(tab.isIncognito) { tab.faviconUrl = UrlUtils.getFaviconUrl(url) }`. This prevents the Google domain string from being created/stored for private tabs at all.

2) Stop using the persistent shared session for ALL favicon fetches. In TabSwitcherViewController.swift:228, replace `URLSession.shared` with an ephemeral session (`URLSession(configuration: .ephemeral)`) so no favicon request carries persistent cookies or is written to the on-disk cache — this also protects non-incognito tabs from sending Google-correlatable cookies on every favicon load. Additionally, skip the network fetch entirely for incognito cells (mirror the line 216 incognito check before the dataTask) and fall back to the local `globe` glyph. Longer term, prefer routing favicon retrieval through the WKWebView's own (per-tab, non-persistent for incognito) data store, or fetch favicons directly from the site's /favicon.ico over the appropriate store rather than proxying through Google.

#### 34. [MEDIUM] Over-permissioning: NSPhotoLibraryUsageDescription declared but photo library is never accessed
_store-readiness · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 53-54)`  

The Info.plist declares a photo-library purpose string claiming the app needs photo access 'to upload files', but there is no photo-picker or file-upload code anywhere (grep for PHPhotoLibrary/UIImagePickerController/PHPicker/UIDocumentPicker = 0 hits). Declaring an unused sensitive-data purpose string for a feature that does not exist is an App Review rejection risk.

```
<key>NSPhotoLibraryUsageDescription</key>\n<string>Helix Browser cần truy cập thư viện ảnh để tải file lên</string>
```

**Fix:** Remove NSPhotoLibraryUsageDescription unless/until you actually present a photo picker for file uploads.

#### 35. [MEDIUM] Camera & microphone usage strings declared but no WebRTC permission delegate is implemented
_store-readiness · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 49-52)`  

Camera and microphone purpose strings cite WebRTC, but the WKUIDelegate implements only createWebViewWith, JS alert and JS confirm. It does not implement webView(_:requestMediaCapturePermissionFor:initiatedByFrame:type:decisionHandler:), so getUserMedia is effectively denied and the declared camera/mic purpose is unreachable dead permission.

```
<key>NSCameraUsageDescription</key>..."cần truy cập camera cho WebRTC"; BrowserViewController.swift WKUIDelegate (lines 495-515) has no requestMediaCapturePermissionFor method.
```

**Fix:** Implement WKUIDelegate.webView(_:requestMediaCapturePermissionFor:...) so the declared camera/mic descriptions are actually used, or remove the strings.

#### 36. [MEDIUM] UIRequiredDeviceCapabilities = armv7 (32-bit) is incorrect for a modern arm64-only iOS app
_store-readiness · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 27-30)`  

armv7 is the 32-bit instruction set dropped after iOS 10; current devices and the App Store are arm64-only. Requiring armv7 capability is meaningless/incorrect and can mis-gate device eligibility.

```
<key>UIRequiredDeviceCapabilities</key>\n<array>\n  <string>armv7</string>\n</array>
```

**Fix:** Change to <string>arm64</string> or remove the key entirely and let the deployment target govern eligibility.

#### 37. [MEDIUM] No Dynamic Type / font scaling support anywhere
_accessibility · Store Readiness, i18n & Accessibility · effort L_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/StartPageView.swift  (lines 43, 51, 75, 83, 168)`  

Every label, button and cell uses a hardcoded point size via systemFont(ofSize:). grep for preferredFont/adjustsFontForContentSizeCategory/UIFontMetrics/forTextStyle returns 0 hits. Text does not respond to the user's Larger Text / accessibility text-size settings, and several sizes (10-11pt) are below comfortable minimums.

```
titleLabel.font = .systemFont(ofSize: 28, weight: .bold); subtitleLabel.font = .systemFont(ofSize: 14...); label.font = .systemFont(ofSize: 11, weight: .medium)
```

**Fix:** Adopt UIFont.preferredFont(forTextStyle:) (or UIFontMetrics-scaled fonts) with adjustsFontForContentSizeCategory = true so the UI honors Dynamic Type.

#### 38. [MEDIUM] LaunchScreen storyboard referenced in Info.plist is absent; no app icon / asset catalog committed
_store-readiness · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Info.plist  (lines 25-26)`  

Info.plist declares UILaunchStoryboardName=LaunchScreen but no LaunchScreen.storyboard/.xib exists anywhere in the iOS tree, and no Assets.xcassets / AppIcon set is committed. A missing launch storyboard yields a blank launch and a validation issue; a missing AppIcon causes outright App Store submission rejection. Note: no .xcodeproj is committed, so confirm these assets are not living outside the repo before treating as fully missing — but as committed they are absent.

```
<key>UILaunchStoryboardName</key>\n<string>LaunchScreen</string>; find for *.storyboard/*.xib/*.xcassets/AppIcon* over ios/ returns nothing.
```

**Fix:** Add the LaunchScreen.storyboard matching the Info.plist key (or switch to a UILaunchScreen dict) and provide a complete AppIcon set in an asset catalog.

#### 39. [MEDIUM] Non-responsive tablet/iPad layout — fixed column counts and UIScreen.main width
_responsive-layout · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/TabSwitcherViewController.swift  (lines 41)`  

The tab switcher computes item width from UIScreen.main.bounds (always 2 columns, ignores the actual container size and iPad Split View/Slide Over), and the start page hardcodes a 4-per-row favorites grid regardless of width. The Info.plist declares full iPad orientation support, so on iPad these fixed layouts produce stretched tiles and a 4-wide start page that does not adapt to size class.

```
layout.itemSize = CGSize(width: (UIScreen.main.bounds.width - 48) / 2, height: 200); StartPageView.swift:95 for row in stride(from: 0, to: favorites.count, by: 4) (fixed 4 columns).
```

**Fix:** Use the view's bounds/safeAreaLayoutGuide or a UICollectionViewCompositionalLayout with adaptive columns based on the trait collection size class; avoid UIScreen.main.bounds, which misbehaves under iPad multitasking.

#### 40. [LOW] History/bookmark storage format is incompatible between DataManager (JSON Data) and the live UI/browser code (plist arrays)
_Data correctness · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Data/CoreDataManager.swift  (lines 33-39, 90-96, 142-148)`  

Two storage layers write the same UserDefaults keys (helix_history, helix_bookmarks, helix_downloads) in mutually-unreadable encodings. DataManager uses JSONEncoder/Decoder into a Data blob; the actually-used screens and the browser use plist arrays via UserDefaults.array/.set. UserDefaults.array(forKey:) returns nil when the stored object is a Data blob (and vice versa), so anything written through DataManager is invisible to the UI and vice versa. The live path (browser + History/Bookmarks VCs) is internally consistent, which masks the bug today, but DataManager is dead, mismatched code that will silently corrupt/hide data the moment it is wired in.

```
DataManager.getHistory: `guard let data = defaults.data(forKey: historyKey), let items = try? JSONDecoder().decode([[String:String]].self, from: data)`. BrowserViewController.saveHistory: `var history = UserDefaults.standard.array(forKey: "helix_history") as? [[String:String]] ?? []` ... `UserDefaults.standard.set(history, forKey: "helix_history")`. toggleBookmark: `UserDefaults.standard.set(bookmarks, forKey: "helix_bookmarks")`. Same divergence in History/Bookmarks VCs.
```

**Fix:** Route every history/bookmark/download read/write through one layer (DataManager already exposes addHistory/addBookmark/getBookmarks/etc.) and replace the ad-hoc UserDefaults.array/set calls in BrowserViewController, HistoryViewController and BookmarksViewController so the encodings can never diverge.
**Verifier note:** Treat this as a code-hygiene / latent-hazard cleanup rather than a live data bug. Pick ONE storage encoding per key and delete the other path to remove the trap: either (a) delete the unused history/bookmark methods from DataManager (addHistory/getHistory/saveHistory/addBookmark/getBookmarks/saveBookmarks and their clear/search helpers) since nothing calls them and the plist path is what actually ships, keeping only the downloads methods that are wired into DownloadsViewController; or (b) if you intend DataManager to be the single source of truth, route BrowserViewController (saveHistory, getBookmarks, toggleBookmark), HistoryViewController and BookmarksViewController through DataManager and add a one-time migration that reads any existing plist arrays for helix_history/helix_bookmarks and re-encodes them as JSON (otherwise pre-existing user data becomes invisible after the switch). Note the downloads key is already consistent, so it needs no change. Separately, DownloadsViewController is never presented from the menu — either wire it in or remove it.

#### 41. [LOW] Suspended-tab flag is persisted and restored stale across launches
_Tab restore robustness · Browsing Feature Completeness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Tabs/TabManager.swift  (lines 166-176)`  

isSuspended is encoded/decoded with the tab. After a cold start all webViews are nil and are recreated lazily, making the persisted suspended state meaningless; a non-active restored tab can report stale suspended state in the switcher even though there is no live webView. Only switchToTab ever clears isSuspended.

```
restoreTabs assigns `tabs = restored` with isSuspended decoded from disk (BrowserTab decode line 49), and nothing resets it on restore.
```

**Fix:** Reset transient flags (isSuspended) to false on restore since webViews are always recreated lazily after relaunch.

#### 42. [LOW] No pull-to-refresh on web content
_Missing UX feature · Browsing Feature Completeness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 317-350)`  

No UIRefreshControl is attached to the webView's scrollView; reload is only available via the toolbar button. Pull-to-refresh is a standard mobile-browser expectation.

```
createWebView configures the webView but never adds a UIRefreshControl; grep for UIRefreshControl/pull returns nothing.
```

**Fix:** Attach a UIRefreshControl to each webView.scrollView that triggers reload().

#### 43. [LOW] Picture-in-Picture / background video not enabled; no audio background mode
_Missing feature · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 317-323)`  

createWebView enables inline playback and AirPlay but not Picture-in-Picture, and Info.plist has no UIBackgroundModes (audio). Without the audio background mode, PiP and background audio do not work, and AVPictureInPictureController is referenced nowhere.

```
config sets allowsAirPlayForMediaPlayback / allowsInlineMediaPlayback / mediaTypesRequiringUserActionForPlayback only. Info.plist has no UIBackgroundModes key.
```

**Fix:** If PiP is intended, add UIBackgroundModes=[audio] to Info.plist and verify inline/PiP playback; otherwise document the limitation.

#### 44. [LOW] Tab thumbnails are never captured — switcher shows blank placeholders
_Half-implemented feature · Browsing Feature Completeness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/TabSwitcherViewController.swift  (lines 127-129, 213-237)`  

BrowserTab declares a thumbnail property and TabCell builds a thumbnailView, but nothing ever calls WKWebView.takeSnapshot to populate it and TabCell.configure never assigns an image, so the switcher shows empty colored boxes instead of page previews.

```
thumbnailView is only given a background color; configure(tab:isActive:) sets title/url/favicon/borders but never assigns thumbnailView content; grep for takeSnapshot returns nothing.
```

**Fix:** Capture a snapshot (takeSnapshot) when switching away from / backgrounding a tab and display tab.thumbnail in the cell.

#### 45. [LOW] Home button never returns to the built-in start page; homepage default and setting are remote-only
_UX gap · Browsing Feature Completeness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 201-203)`  

goHome() loads Prefs.homepage which defaults to https://www.google.com. The custom StartPageView (favorites grid, trackers-blocked stat) only appears for helix://start, which only occurs on a freshly created tab — so the Home button never returns users to the start page, and there is no Settings UI to set the homepage (including to helix://start).

```
`@objc private func goHome() { loadUrl(Prefs.shared.homepage) }`; Prefs.homepage default is `https://www.google.com` (Prefs line 15); showStartPage is only reached when url hasPrefix helix://.
```

**Fix:** Default the homepage to helix://start or honor a start-page option on the Home button, and expose a homepage setting in Settings.

#### 46. [LOW] No stop-loading control; reload button never becomes a stop button
_Missing UX feature · Browsing Feature Completeness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 199, 426-437)`  

There is only a static reload button; while a page is loading the user cannot stop it. The reload button is never swapped to a stop (xmark) icon during loading and stopLoading() is unreachable from the UI (only TabManager calls it internally on tab close).

```
`@objc private func reload() { currentWebView?.reload() }`; the reloadButton image is set once to arrow.clockwise and never toggled in didStartProvisionalNavigation/didFinish.
```

**Fix:** Toggle the reload button between reload and stop based on navigation/progress state, calling stopLoading() while loading.

#### 47. [LOW] TabCell favicon load has no cell-reuse guard — wrong favicons on recycled cells
_Async / cell reuse · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/TabSwitcherViewController.swift  (lines 227-236)`  

configure(tab:) starts a URLSession.shared.dataTask for the favicon and, on completion, sets self?.faviconView.image with no check that the cell still represents the same tab. UICollectionView recycles cells, so when the user scrolls (or reloadData runs while a request is in flight) the late completion can stamp the previous tab's favicon onto a reused cell. There is also no prepareForReuse to reset/cancel.

```
URLSession.shared.dataTask(with: faviconUrl) { [weak self] data, _, _ in
    if let data = data, let image = UIImage(data: data) {
        DispatchQueue.main.async { self?.faviconView.image = image }   // no tab-identity check
    }
}.resume()
```

**Fix:** Capture the target tab id, store it on the cell, and in the completion only apply the image if the cell still shows that id; cancel the in-flight task in prepareForReuse.

#### 48. [LOW] JS alert/confirm panels present on BrowserViewController even when another VC is presented — dialog dropped, completion handler may never fire
_Lifecycle / presentation · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 503-514)`  

runJavaScriptAlertPanel/ConfirmPanel call self.present(alert, ...) unconditionally. If the tab switcher, a menu action sheet, or any modal is currently presented over BrowserViewController, UIKit refuses the present (logs 'attempt to present ... whose view is not in the window hierarchy' / 'already presenting') and the alert never appears. The completionHandler is then never invoked, which leaves the WKWebView's JS execution blocked indefinitely for that frame.

```
func webView(_:runJavaScriptAlertPanelWithMessage:...completionHandler:) {
    let alert = UIAlertController(...)
    alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
    present(alert, animated: true)   // no guard for already-presenting; completionHandler lost if present fails
}
```

**Fix:** Present from the top-most presented view controller (walk presentedViewController) and ensure completionHandler() is still called (e.g. in a deferred fallback) if presentation cannot occur, so the web content does not hang.

#### 49. [LOW] Unwired tab-management code leaves dangling activeTabId / dead memory feature
_Dead code / state corruption · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Tabs/TabManager.swift  (lines 86-107, 180-187)`  

closeOtherTabs(except:), closeTabsToRight(of:), duplicateTab, pinTab, muteTab, createTabGroup, searchTabs and suspendInactiveTabs are defined but never called anywhere (verified by grep). Beyond being dead code, closeTabsToRight can remove the active tab (if it is to the right) without ever updating activeTabId, leaving activeTab == nil and the UI pointing at a non-existent tab. suspendInactiveTabs (the memory-pressure mitigation) is never scheduled or invoked, so retained inactive webviews are never released — a real memory concern for a multi-tab browser.

```
grep closeTabsToRight/closeOtherTabs/suspendInactiveTabs → only their own definitions; no callers.
closeTabsToRight(of:): removes tabs with i > index but never reassigns activeTabId if the active tab was among them.
```

**Fix:** Either wire these into the tab UI (long-press context menu, scene memory-warning hook for suspendInactiveTabs via applicationDidReceiveMemoryWarning) and fix closeTabsToRight to reselect a valid activeTabId, or remove the unused code.

#### 50. [LOW] History & bookmarks use two incompatible storage formats for the same UserDefaults keys
_Data corruption / dead code · Production Quality & Robustness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Data/CoreDataManager.swift  (lines 33-39, 90-96 vs BrowserViewController.swift:388,484 and HistoryViewController.swift:31,92)`  

Two parallel persistence layers write to the SAME keys (`helix_history`, `helix_bookmarks`) using mutually unreadable encodings. `BrowserViewController`, `HistoryViewController`, and `BookmarksViewController` store a native plist array via `UserDefaults.standard.set([[String:String]], forKey:)` and read it via `UserDefaults.standard.array(forKey:)`. `DataManager` (CoreDataManager.swift) stores the same keys as JSON `Data` via `JSONEncoder` and reads via `defaults.data(forKey:)` + `JSONDecoder`. A plist array cannot be read by `data(forKey:)` and a JSON Data blob cannot be read by `array(forKey:)`. Result: `DataManager.getHistory()`/`getBookmarks()`/`addHistory()`/`addBookmark()`/`searchHistory()`/`searchBookmarks()` are silently broken (always return empty / never visible to the real UI). This is a latent landmine: any future code that calls DataManager for history/bookmarks will silently lose or fail to read data, and `clearHistory()` from one layer leaves the other's data orphaned.

```
CoreDataManager.swift:34 `guard let data = defaults.data(forKey: historyKey), let items = try? JSONDecoder().decode([[String: String]].self, from: data)` vs BrowserViewController.swift:484 `var history = UserDefaults.standard.array(forKey: "helix_history") as? [[String: String]] ?? []` and line 489 `UserDefaults.standard.set(history, forKey: "helix_history")`.
```

**Fix:** Pick ONE storage layer. Route all history/bookmark reads and writes through DataManager (the proper abstraction) and delete the inline UserDefaults.array/set code in the view controllers, or vice versa. Keeping both with the same keys guarantees data loss.
**Verifier note:** Treat this as a latent maintainability trap (low severity), not an active data-loss bug. DataManager's history/bookmark methods are dead code today; the live UI consistently uses the UserDefaults plist-array path. To remove the landmine, pick ONE owner and make it the single source of truth: either (a) delete the unused getHistory/getBookmarks/addHistory/addBookmark/searchHistory/searchBookmarks/clearHistory/clearBookmarks methods (and clearAllData's history/bookmark calls) from DataManager, leaving it as the downloads-only store; or (b) route all history/bookmark reads/writes in BrowserViewController, HistoryViewController, BookmarksViewController, and SettingsViewController through DataManager and delete the inline UserDefaults.array/set/removeObject calls. If you choose (b), note that existing users have data already persisted as a plist array under helix_history/helix_bookmarks, so DataManager's data(forKey:)+JSONDecoder reads would return empty against pre-existing data — add a one-time migration (read via array(forKey:), re-encode via JSONEncoder) or this change would orphan real user data. Option (a) is the lower-risk fix given current usage.

#### 51. [LOW] Tab thumbnails are never captured — switcher always shows blank placeholders
_Empty-state UX · Production Quality & Robustness · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/TabSwitcherViewController.swift  (lines 127-129, 213-237)`  

`BrowserTab.thumbnail` is declared but never assigned anywhere, and `TabCell.thumbnailView` is a plain UIView whose background is just `BrandColors.background`. `TabCell.configure` never sets any thumbnail image. The tab switcher therefore shows an identical empty dark rectangle for every tab (65% of each cell), defeating the purpose of a visual tab grid. WKWebView's `takeSnapshot` is never used.

```
BrowserTab.swift:20 `var thumbnail: UIImage?` (never assigned, grep confirms no writes). TabCell.configure (TabSwitcherViewController.swift:213) sets title/url/favicon but never touches `thumbnailView`.
```

**Fix:** Capture `webView.takeSnapshot(...)` (or `snapshotView`) when leaving a tab, store it on `BrowserTab.thumbnail`, and render it in TabCell. Otherwise remove the large blank thumbnail area.

#### 52. [LOW] URL load silently fails for IDN/unicode/space-containing URLs
_Input validation / edge case · Production Quality & Robustness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 266-281)`  

`loadUrl` does `guard let url = URL(string: formatted) else { return }`. `URL(string:)` returns nil for strings containing un-encoded spaces or many internationalized-domain (IDN, non-ASCII host) inputs. When it returns nil the function silently returns with no error page, no feedback — the address bar shows the entered text but nothing loads, leaving the user confused. `UrlUtils.formatUrl` does not percent-encode or IDNA-encode hosts; it only percent-encodes the query for search fallback.

```
BrowserViewController.swift:277 `guard let url = URL(string: formatted) else { return }` with no else-branch handling. UrlUtils.formatUrl returns `https://\(trimmed)` (line 18) without encoding the host.
```

**Fix:** Use a more permissive parser (e.g. percent-encode with `.urlAllowedCharacterSet`, or `URLComponents`) and IDNA-encode hosts; on parse failure, fall back to a search query rather than silently doing nothing.

#### 53. [LOW] JS alert/confirm panels presented without verifying foreground presentation context
_Robustness / edge case · Production Quality & Robustness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 503-514)`  

`runJavaScriptAlertPanelWithMessage` / `runJavaScriptConfirmPanelWithMessage` call `present(alert, animated: true)` unconditionally on `self`. If another modal is already presented (e.g. the menu action sheet, tab switcher, or a previous JS alert), UIKit will fail to present and the alert is dropped — but the WKWebView completion handler is only invoked from the alert's button actions. If presentation fails, the `completionHandler` is never called, which can hang the page's JS (and WebKit may assert/terminate for an un-called completion handler). The title is also hardcoded to 'Helix Browser' rather than showing the originating frame's host, which is a phishing-clarity gap.

```
Lines 504-506: `let alert = UIAlertController(title: "Helix Browser", ...)` then `present(alert, animated: true)` with no check for `presentedViewController` and no fallback that still calls `completionHandler()`.
```

**Fix:** Resolve the top-most presented view controller before presenting, and guarantee the completionHandler is always called (e.g. in a defer or a presentation-failure path). Show the requesting origin in the title.

#### 54. [LOW] Favicon URLSession tasks in cells have no cancellation on reuse — wrong-image flicker and wasted network
_Resource cleanup / performance · Production Quality & Robustness · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/TabSwitcherViewController.swift  (lines 227-232)`  

Each `TabCell.configure` fires a fresh `URLSession.shared.dataTask` for the favicon with no reference held and no cancellation in `prepareForReuse`. On a fast-scrolling grid, recycled cells can receive a late callback from a previously-displayed tab's request and set the wrong favicon (the `[weak self]` guards against a crash but not against displaying a stale image on a reused cell). It also issues a network request per cell appearance with no caching layer.

```
TabSwitcherViewController.swift:228 `URLSession.shared.dataTask(with: faviconUrl) { [weak self] data, _, _ in ... DispatchQueue.main.async { self?.faviconView.image = image } }.resume()` — task not retained, no `prepareForReuse` cancellation.
```

**Fix:** Hold the data task on the cell, cancel it in `prepareForReuse`, and add a small in-memory favicon cache keyed by host. Verify the cell still represents the same tab before assigning the image.

#### 55. [LOW] App Transport Security fully disabled (NSAllowsArbitraryLoads = true)
_Network Security / TLS · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Info.plist  (lines 44-48)`  

ATS is globally disabled with a blanket NSAllowsArbitraryLoads exception. While a browser legitimately needs to load arbitrary user-entered sites (which is the documented justification Apple accepts), enabling it this way also disables ATS's minimum-TLS-version enforcement and forward-secrecy requirements for ALL connections, including any first-party network calls the app itself makes (e.g. the favicon fetch to google.com/s2/favicons in UrlUtils). Combined with the absence of any certificate-validation logic in the navigation delegate, the app provides no transport security guarantees beyond WebKit defaults, and provides no per-domain hardening for its own endpoints. App Review for a browser generally requires the accompanying NSAllowsArbitraryLoadsInWebContent scoping plus a usage justification; a bare NSAllowsArbitraryLoads is a likely review question.

```
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

**Fix:** Replace the blanket NSAllowsArbitraryLoads with NSAllowsArbitraryLoadsInWebContent=true (scopes the exception to WKWebView page content only) so the app's own networking still enforces ATS. Document the browser justification for App Review.
**Verifier note:** In /home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Info.plist (lines 44-48), replace the bare NSAllowsArbitraryLoads=true with NSAllowsArbitraryLoadsInWebContent=true. This scopes the arbitrary-load exception to WKWebView page content (the legitimate browser need) while restoring ATS minimum-TLS-version and forward-secrecy enforcement for the app's own URLSession traffic (e.g. the favicon fetch in UrlUtils/TabSwitcherViewController). Keep a short justification documented for App Review explaining the browser use case. This is a low-severity hardening/best-practice change, not an urgent fix; the favicon endpoint is already HTTPS so there is no active cleartext exposure today.

#### 56. [LOW] JS dialogs (alert/confirm) and address bar do not show the requesting origin
_UI Spoofing / Permission Context · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift  (lines 503-514)`  

runJavaScriptAlertPanel/ConfirmPanel present a UIAlertController titled 'Helix Browser' with the page-supplied message but never display the initiating frame's origin. A malicious page can craft alert/confirm text that impersonates the browser or another site (phishing for credentials), and the user cannot tell which origin is prompting. Production browsers prefix JS dialogs with the page host. Minor but relevant to the security UX of a browser.

```
let alert = UIAlertController(title: "Helix Browser", message: message, preferredStyle: .alert)
```

**Fix:** Title JS dialogs with the requesting origin (e.g. frame.securityOrigin.host) rather than the browser name, so users can attribute the prompt to a site.

#### 57. [LOW] Camera/microphone usage declared but no WebRTC permission delegate is implemented
_Permissions (camera/mic) scoping · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/ios/HelixBrowser/HelixBrowser/Info.plist  (lines 49-52)`  

Info.plist declares NSCameraUsageDescription and NSMicrophoneUsageDescription 'for WebRTC', but the WKUIDelegate does not implement webView(_:requestMediaCapturePermissionFor:initiatedByFrame:type:decisionHandler:). With no per-site permission prompt handled by the app, getUserMedia requests are governed solely by WebKit's default behavior; there is no per-site permission scoping, persistence, or user-facing grant/deny UI, and the privacy claims ('An toàn. Riêng tư.') are not backed by any permission management. There is likewise no handling for geolocation/notifications/clipboard. For an audited browser this is a gap (and a possible review question given the declared but unused capability rationale).

```
<key>NSCameraUsageDescription</key>
<string>Helix Browser cần truy cập camera cho WebRTC</string>
... (no requestMediaCapturePermissionFor implementation exists anywhere in the source)
```

**Fix:** Implement requestMediaCapturePermissionFor to show a per-origin prompt and persist the decision per site (grant/deny/ask), scoped so incognito grants are not persisted. Remove unused usage strings if WebRTC isn't actually supported.

#### 58. [LOW] accessibilityValue misused as data storage instead of for accessibility
_accessibility · Store Readiness, i18n & Accessibility · effort S_  
**Location:** `ios/HelixBrowser/HelixBrowser/Views/StartPageView.swift  (lines 176, 191)`  

The favorite tiles on the start page stash the destination URL in accessibilityValue and read it back on tap, abusing the accessibility API as a key-value bag. As a result VoiceOver reads the raw URL as the tile's value, the tile has no accessibilityLabel and no .button trait, so it is not announced as a tappable favorite.

```
container.accessibilityValue = url ... @objc func favoriteTapped: if let url = gesture.view?.accessibilityValue { onUrlSelected?(url) }
```

**Fix:** Store the URL in a proper model/tag-indexed array, set accessibilityLabel to the site name, and add the .button trait.

#### 59. [LOW] Forced dark mode with hardcoded hex colors — no light mode / no system appearance support
_dark-mode · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/SceneDelegate.swift  (lines 14)`  

The window forces overrideUserInterfaceStyle = .dark and every color is a fixed hex literal with no light variant, so the user's Light Mode preference is permanently ignored and there is no in-app theme toggle. The deliberate dark-only brand is acceptable, but accent purple/pink on the very dark background should be contrast-checked against WCAG.

```
window.overrideUserInterfaceStyle = .dark; BrandColors.swift: background = UIColor(hex: "#0A091E"), textSecondary = UIColor(hex: "#A0A0D0"), etc. (all static fixed hex).
```

**Fix:** Either support system appearance via asset-catalog dynamic colors / an in-app theme toggle, or document the dark-only decision and verify contrast ratios for secondary text and accents.

#### 60. [LOW] Default-browser / deep-link / universal-link handling entirely absent
_store-readiness · Store Readiness, i18n & Accessibility · effort M_  
**Location:** `ios/HelixBrowser/HelixBrowser/SceneDelegate.swift  (lines 6-15)`  

SceneDelegate implements only willConnectTo and sceneDidEnterBackground; there is no scene(_:openURLContexts:) or continue userActivity handler, no .entitlements file (no associated-domains/applinks, no web-browser default entitlement), and no CFBundleURLTypes. The internal helix:// scheme is never registered. The app cannot be set as the iOS default browser nor open http/https URLs handed to it from other apps — inbound URLs are dropped.

```
SceneDelegate has only scene(_:willConnectTo:) and sceneDidEnterBackground; grep for openURL/NSUserActivity/applinks/openURLContexts over the app = 0 hits; no *.entitlements file exists.
```

**Fix:** Handle scene(_:openURLContexts:)/continue userActivity to open inbound URLs in a new tab, and if a default-capable browser is intended, add the com.apple.developer.web-browser entitlement and associated-domains.


## macOS (SwiftUI) — 36 findings

#### 1. [CRITICAL] TLS/SSL certificate errors are silently bypassed — all certs unconditionally trusted
_TLS/Certificate Validation · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 345-352)`  

The WKNavigationDelegate authentication-challenge handler accepts ANY server certificate without ever evaluating its trust. For every server-trust challenge it constructs a credential straight from the (unvalidated) SecTrust and calls completionHandler(.useCredential, ...). It never calls SecTrustEvaluateWithError, never inspects the trust result, and never rejects on failure. This means expired, self-signed, hostname-mismatched, or attacker-supplied (MITM) certificates are all accepted silently with no warning and no interstitial. The lock icon in the address bar (ContentView.swift:49, based only on the 'https' prefix) will still show 'secure'. This completely defeats HTTPS for a web browser and is a textbook MITM hole.

```
func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
    if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
       let trust = challenge.protectionSpace.serverTrust {
        completionHandler(.useCredential, URLCredential(trust: trust))
    } else {
        completionHandler(.performDefaultHandling, nil)
    }
}
```

**Fix:** Do NOT override the server-trust challenge at all (delete the NSURLAuthenticationMethodServerTrust branch) so WebKit performs its own validation and shows its built-in cert-error UI. If a custom handler is required, call SecTrustEvaluateWithError(trust, &error); only on success use URLCredential(trust:); on failure call completionHandler(.cancelAuthenticationChallenge, nil) and present a clear, blocking certificate-error page that requires explicit user action to proceed.
**Verifier note:** Primary fix: delete the entire webView(_:didReceive:completionHandler:) method (or at minimum the NSURLAuthenticationMethodServerTrust branch). With no server-trust override, WebKit performs its own full chain/hostname/expiry validation and presents its built-in certificate-error interstitial automatically. This single deletion restores correct, secure default behavior.

If a custom handler is ever genuinely required (e.g., for client-cert auth or enterprise pinning), it must: call SecTrustEvaluateWithError(trust, &error); on success use URLCredential(trust:) with .useCredential; on failure call completionHandler(.cancelAuthenticationChallenge, nil) and surface a blocking, explicit-user-action certificate-error page (never auto-proceed).

Additionally (separate but compounding): remove NSAllowsArbitraryLoads=true from Info.plist (lines 29-30) so App Transport Security is restored, unless there is a documented, scoped need (use per-domain NSExceptionDomains instead of a global allow).

Lower priority follow-up: the address-bar lock icon (ContentView.swift:49-50) should reflect actual TLS/cert validity rather than a naive "https" prefix check, so it stops giving false assurance.

#### 2. [HIGH] suspendInactiveTabs() never frees WebViews — memory grows unbounded; static cache leaks every tab's WKWebView
_Memory leak / lifecycle · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 232-242 (also WebView.swift:9, 13-16))`  

The advertised 'suspend inactive tabs to save memory' feature is purely cosmetic. suspendInactiveTabs() only flips a Bool (tabs[i].isSuspended = true) and nothing ever consults that flag to release the underlying WKWebView. WebViews are held in the process-global `private static var webViewCache: [UUID: WKWebView]` in WebView.swift and are only removed on explicit tab close (clearCache). So every tab ever visited keeps a live, fully-rendered WKWebView (each with its own renderer/network process) alive forever. For a 'production browser' this means RAM grows without bound as the user opens tabs — a core memory-management feature is missing/stubbed. The timer also runs unconditionally, ignoring the user's `isSuspendInactiveEnabled` preference entirely.

```
private func suspendInactiveTabs() {
    let tenMinutesAgo = Date().addingTimeInterval(-600)
    for i in tabs.indices {
        if tabs[i].id != activeTabId && tabs[i].lastAccessTime < tenMinutesAgo && !tabs[i].isSuspended && !tabs[i].isPinned {
            tabs[i].isSuspended = true   // <-- only sets a flag; WKWebView never freed
        }
    }
}
// WebView.swift: private static var webViewCache: [UUID: WKWebView] = [:]  // retains every webview
```

**Fix:** Actually evict from webViewCache when suspending (webViewCache[id]?.stopLoading(); webViewCache.removeValue(forKey: id)) so the WKWebView/renderer process is released, and re-create + reload (restoring scroll/URL) when the tab becomes active. Gate the timer on prefs.isSuspendInactiveEnabled.
**Verifier note:** Make suspension actually release the WKWebView. In WebView.swift add a `static func suspend(tabId:)` that does `webViewCache[tabId]?.stopLoading()` then `webViewCache.removeValue(forKey: tabId)` so the renderer/network process is freed; have suspendInactiveTabs() call it for each newly-suspended tab. Persist enough state to restore (URL is already in tab.url; optionally capture scroll position via JS before eviction). On reactivation, ContentView/makeNSView already recreates a fresh WKWebView from tab.url on cache miss, so set tabs[index].isSuspended = false and clear the cache entry path is consistent — verify makeNSView's initial load uses tab.url (it does, WebView.swift:74-76). Gate the work on the preference: early-return in suspendInactiveTabs() when `!prefs.isSuspendInactiveEnabled`. Also consider rendering a lightweight placeholder for suspended (non-active) tabs and showing the suspended state in the tab strip so the feature is observable. Optionally cap webViewCache size (LRU) as a backstop regardless of the timer.

#### 3. [HIGH] closeTabsToRight() leaves activeTabId dangling (blank page) and mutates `tabs` while reading it inside removeAll predicate
_Tab state corruption / exclusivity violation · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 146-155)`  

Two bugs. (1) Unlike closeOtherTabs(except:) which sets activeTabId = id, closeTabsToRight does NOT reassign activeTabId. If the currently-active tab is to the right of `id`, it gets removed but activeTabId still points to the deleted UUID. ContentView renders content via `ForEach(viewModel.tabs){ if tab.id == viewModel.activeTabId }`, so no branch matches and the user is left staring at a blank content area with no recoverable selection. (2) The removeAll predicate calls `tabs.firstIndex(...)` — i.e. reads self.tabs — while removeAll is mutating self.tabs in place. This is a Swift exclusive-access violation (can trap with 'Simultaneous accesses ... modification requires exclusive access' under runtime exclusivity checks) and is logically wrong because indices shift mid-removal.

```
func closeTabsToRight(of id: UUID) {
    guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
    let rightTabs = tabs.suffix(from: index + 1).filter { !$0.isPinned }
    rightTabs.forEach { WebView.clearCache(for: $0.id) }
    tabs.removeAll(where: { tab in
        guard let tabIndex = tabs.firstIndex(where: { $0.id == tab.id }) else { return false }  // reads tabs while removeAll mutates it
        return tabIndex > index && !tab.isPinned
    })
    saveTabs()   // <-- never reassigns activeTabId
}
```

**Fix:** Capture the IDs to remove first, then remove by ID set without re-querying the array inside the predicate, and reassign activeTabId to `id` (or nearest surviving tab) if the active tab was removed. e.g. let removeIds = Set(rightTabs.map{$0.id}); tabs.removeAll{ removeIds.contains($0.id) }; if !tabs.contains(where:{$0.id==activeTabId}) { activeTabId = id }.
**Verifier note:** Rewrite closeTabsToRight to (a) capture the IDs to remove up front and remove by membership (no re-query of the array inside the predicate), and (b) reassign activeTabId if the active tab was among those removed. Concretely:

func closeTabsToRight(of id: UUID) {
    guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
    let removeIds = Set(tabs.suffix(from: index + 1).filter { !$0.isPinned }.map { $0.id })
    guard !removeIds.isEmpty else { return }
    removeIds.forEach { WebView.clearCache(for: $0) }
    tabs.removeAll { removeIds.contains($0.id) }
    if !tabs.contains(where: { $0.id == activeTabId }) {
        activeTabId = id   // reselect the anchor tab (guaranteed to survive)
    }
    saveTabs()
}

This eliminates the exclusivity/index-shift hazard (predicate only reads the precomputed Set) and prevents the dangling-selection blank page. Setting activeTabId = id is safe because the anchor tab is never in the removed set. (The same defensive pattern should be considered as a shared helper given closeTab/closeOtherTabs also manipulate selection.)

#### 4. [MEDIUM] Downloads have no live progress, no pause/resume/cancel, and no way to open them
_Downloads · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/DownloadManager.swift  (lines 9, 48-87)`  

DownloadItem.progress is initialized to 0 and only set to 1.0 in `downloadDidFinish`; there is no KVO observation of `WKDownload.progress` / `download.progress.fractionCompleted`, so the progress bar never moves during a download. There is no pause/resume (the `resumeData` returned in `didFailWithError` is discarded) and no cancel API. Crucially, the downloads UI is non-functional: the only download affordance in the toolbar has an empty action — `ToolbarButton(...) {}` — so completed downloads can never be opened or revealed from the app. `localPath` is stored but never surfaced for 'Open' / 'Show in Finder'.

```
var progress: Double = 0   // never updated until completion
... item.isComplete = true; item.progress = 1.0
func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) { ... } // resumeData ignored
// ContentView.swift line 87:
ToolbarButton(icon: "arrow.down.circle.fill", isActive: true, activeColor: BrandColors.accentPurple, help: "Downloads") {}
```

**Fix:** Observe `download.progress.fractionCompleted` via KVO (or `download.progress` Progress object) and push updates into the item; keep `resumeData` to support resume; add cancel; and build a downloads popover/list with Open and Reveal-in-Finder actions wired to `localPath` (NSWorkspace.shared.open / activateFileViewerSelecting).
**Verifier note:** Wire up the download feature so it is observable and actionable:

1. Live progress: in `handleDownload`, observe the download's Progress, e.g. via KVO `download.progress.observe(\.fractionCompleted) { ... }` or Combine `download.progress.publisher(for: \.fractionCompleted)`, and push the value into the item with the existing `updateItem(id:)` helper. Retain the observation token in `downloadMap` (or a parallel map) and invalidate it in `downloadDidFinish`/`didFailWithError`.

2. Cancel: add `func cancel(id: UUID)` that looks up the `WKDownload` and calls `download.cancel { resumeData in ... }`, storing the returned resume data on the item.

3. Pause/resume: persist the `resumeData` from `didFailWithError` (and from cancel) onto the `DownloadItem`, and add a resume path using `WKWebView`'s `resumeDownload(fromResumeData:completionHandler:)` (or re-issue the request) so interrupted downloads can continue.

4. Functional UI: replace the empty `{}` action on ContentView.swift:87 with a real affordance — a `.popover`/menu listing `downloadManager.downloads` showing per-item progress, and per-item actions: Open (`NSWorkspace.shared.open(localPath)`), Reveal in Finder (`NSWorkspace.shared.activateFileViewerSelecting([localPath])`), Cancel, and Pause/Resume. Also stop gating the button solely on `activeDownloadCount > 0` (or show it whenever `!downloads.isEmpty`) so completed downloads remain reachable.

#### 5. [MEDIUM] No 'stop loading' control — reload button cannot abort a load
_Navigation · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/ContentView.swift  (lines 43)`  

The toolbar exposes only a reload button (`arrow.clockwise`) that is hard-coded to call `viewModel.reload()`. There is no Stop button and `stopLoading()` is never called anywhere in the codebase. A production browser must let the user abort a slow/hung page load. While `isLoading` is tracked (and could flip the icon to an 'X' Stop control), the code does not do this.

```
NavButton(icon: "arrow.clockwise", action: { viewModel.reload() }, active: true)   // grep: no stopLoading() anywhere in project
```

**Fix:** Toggle the reload button to a Stop ('xmark') icon while `viewModel.isLoading` is true, and add a `stop()` method that posts a notification handled by the coordinator calling `webView.stopLoading()`.

#### 6. [MEDIUM] Tab audio mute is purely cosmetic — never mutes the WebView
_Tabs / Media · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 172-175)`  

`muteTab` toggles `tabs[index].isMuted` and the UI shows a speaker-slash icon, but nothing ever applies the mute to the WKWebView. There is no use of `WKWebView.setAllMediaPlaybackSuspended`, `mediaPlaybackState`, the private `_setPageMuted:`, or muting via injected JS. The 'Mute tab' menu item therefore does nothing audible.

```
func muteTab(id: UUID) {
    guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
    tabs[index].isMuted.toggle()
}   // no WKWebView audio mute applied anywhere
```

**Fix:** On mute toggle, mute the tab's WKWebView (e.g. inject JS to set `muted=true` on all <video>/<audio> and observe new media, or use the appropriate WebKit muting API) and re-apply on navigation.

#### 7. [MEDIUM] Inactive-tab suspension only sets a flag; the WebView is never unloaded (no memory saved)
_Tabs / Memory · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 232-242)`  

`suspendInactiveTabs` sets `tabs[i].isSuspended = true` after 10 minutes, but the WKWebView for that tab remains fully alive in `WebView.webViewCache` (it is never removed, stopped, or torn down). Nothing in `ContentView`/`WebView` reacts to `isSuspended` to replace the live view with a lightweight placeholder. So the advertised 'save memory by suspending old tabs' (Settings toggle) provides zero memory benefit. The related `isSuspendInactiveEnabled` preference is also never checked before suspending.

```
if tabs[i].id != activeTabId && tabs[i].lastAccessTime < tenMinutesAgo && !tabs[i].isSuspended && !tabs[i].isPinned {
    tabs[i].isSuspended = true
}   // WebView.webViewCache[id] is never cleared on suspend
```

**Fix:** On suspend, remove/teardown the cached WKWebView (and render a placeholder restoring on activation), and gate the whole routine on `prefs.isSuspendInactiveEnabled`.

#### 8. [MEDIUM] Multiple Settings toggles are dead — shown to user but never affect behavior
_Settings wiring · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/Prefs.swift  (lines 12-13, 23, 27-28)`  

Several preferences are defined and surfaced in SettingsView but are never read by any behavior code (verified by grep across all .swift): `defaultZoom` (only mutated in its own Settings UI; new tabs always start at 100% and zoom never uses it), `isSuspendInactiveEnabled` (suspension runs unconditionally), `isConfirmCloseMultiple` (no confirmation dialog exists for closeOtherTabs/closeTabsToRight), `isBlockAutoplayEnabled` (never referenced; autoplay is in fact force-enabled via `mediaTypesRequiringUserActionForPlayback = []`), and `downloadsDir` (DownloadManager hard-codes the system Downloads folder). These are misleading no-op controls.

```
@AppStorage("default_zoom") var defaultZoom: Int = 100  // never read outside SettingsView
@AppStorage("block_autoplay") var isBlockAutoplayEnabled: Bool = false  // 0 references
@AppStorage("downloads_dir") var downloadsDir: String = ""  // DownloadManager uses .downloadsDirectory unconditionally
// WebView.swift: config.mediaTypesRequiringUserActionForPlayback = []  (contradicts block-autoplay)
```

**Fix:** Wire each toggle: apply `defaultZoom` to new tabs/zoom defaults; gate suspension on its flag; show a confirm alert before bulk-closing tabs when enabled; honor `isBlockAutoplayEnabled` by setting `mediaTypesRequiringUserActionForPlayback` accordingly; respect `downloadsDir` (with a security-scoped bookmark). Or remove the toggles that won't be implemented.

#### 9. [MEDIUM] Desktop/mobile site toggle does not reload the current page
_Desktop site / User-Agent · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 83-123)`  

Toggling 'Phiên bản máy tính' (desktop mode) flips `prefs.isDesktopMode`, and `applyUserAgent` updates `webView.customUserAgent` on the next `updateNSView` pass. However nothing reloads the page, so the currently-displayed site keeps rendering with its previously negotiated layout/UA until the user manually navigates or reloads. A desktop-site toggle that requires a manual reload to take effect is a broken UX expectation. There is also no per-tab desktop override (it is a single global preference).

```
private func applyUserAgent(to webView: WKWebView) {
    ...
    if webView.customUserAgent != targetUA { webView.customUserAgent = targetUA }
}   // no reload() triggered when isDesktopMode changes
```

**Fix:** When the desktop-mode preference changes, reload the active tab (and ideally support a per-tab desktop override that reloads that tab).

#### 10. [MEDIUM] Find-in-page lacks match count and uses unreliable window.find() for next/previous
_Find in page · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 175-207)`  

The initial search uses the native `webView.find(_:configuration:)` on macOS 13+ (good) but discards the result (`completionHandler: { _ in }`), so there is no match count / 'no results' feedback in the find bar. Worse, Next/Previous always fall back to JavaScript `window.find()` / `window.find('', false, true)` even on macOS 13+, which is a separate, inconsistent search mechanism from the native find used for the initial query — they do not share highlight state, behave differently across frames, and `window.find()` is non-standard/deprecated. Dismiss only clears the JS selection, not native find highlights.

```
self.lastWebView?.find(text, configuration: .init(), completionHandler: { _ in })  // result ignored
...
findNextObserver: self.lastWebView?.evaluateJavaScript("window.find()")
findPrevObserver: self.lastWebView?.evaluateJavaScript("window.find('', false, true)")
```

**Fix:** Use the native `WKWebView.find(_:configuration:)` consistently for initial/next/previous (with forwards/backwards in WKFindConfiguration) on macOS 13+, surface `WKFindResult.matchFound`/count in the find bar, and clear native highlights on dismiss.

#### 11. [MEDIUM] Page zoom via document.body.style.zoom is unreliable and non-persistent
_Zoom · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 209-217)`  

Zoom is implemented by injecting `document.body.style.zoom = '<scale>'`. The non-standard CSS `zoom` property does not affect `position:fixed` elements correctly, breaks layouts on many sites (especially flex/grid and pages that set their own zoom), and is wiped on every navigation (only re-applied in didFinish at line 282, causing a visible flash). WKWebView provides `pageZoom` (and Safari uses it) which is the correct, robust mechanism. Text-only zoom is also not offered.

```
let scale = Double(zoom) / 100.0
self.lastWebView?.evaluateJavaScript("document.body.style.zoom = '\(scale)'")
```

**Fix:** Use `webView.pageZoom = scale` (macOS 11+) instead of injecting CSS zoom; it persists across same-document changes and renders correctly. Keep per-tab zoom state as-is.

#### 12. [MEDIUM] Tab session restore ignores the restore-tabs preference and loses navigation history
_Session restore · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 46-57, 98-102)`  

On launch the app always restores saved tabs if any exist; `prefs.isRestoreTabsEnabled` is never checked, so disabling 'Restore tabs on startup' in Settings has no effect. Additionally, only the last URL of each tab is persisted (WebTab stores a single `url`), so each restored tab loses its full back/forward history and scroll position — restored tabs cannot go Back to where the user was before quitting. WKWebView's `interactionState`/session history is not captured.

```
init() {
    if let savedTabs = restoreTabs(), !savedTabs.isEmpty {   // no check of prefs.isRestoreTabsEnabled
        self.tabs = savedTabs ...
// WebTab persists only `url`; no per-tab back/forward history
```

**Fix:** Gate restoration on `prefs.isRestoreTabsEnabled`; persist and restore each tab's session (e.g. `WKWebView.interactionState` on macOS 12+) so back/forward and scroll survive a restart.

#### 13. [MEDIUM] Core production-browser features are entirely missing
_Feature completeness · Browsing Feature Completeness · effort L_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/ContentView.swift  (lines 120-143)`  

Several features expected of a shipping browser have no implementation anywhere in the project (verified by grep): Reader mode; Print / Save-as-PDF (no NSPrintOperation, no createPDF/dataForPDF); Share (no NSSharingServicePicker); address-bar search suggestions / URL autocomplete (the address bar is a plain TextField with no suggestion dropdown, and there is no history/bookmark autocomplete on type); Picture-in-Picture toggle UI (the entitlement key is set but there is no PiP control and no per-element control); bookmark folders / editing / import-export (bookmarks are a flat [String:String] list with only add/delete — no folders, no rename/edit, no HTML import/export). The overflow menu offers none of these.

```
// grep across project: 0 matches for NSPrintOperation, createPDF, NSSharingService, reader/readability, suggestion/autocomplete, folder (bookmarks)
TextField("Tìm kiếm hoặc nhập địa chỉ", text: $urlInput).onSubmit { viewModel.loadUrl(urlInput) }  // no suggestions list
```

**Fix:** Prioritize the highest-value gaps for the product: address-bar autocomplete/suggestions (history+bookmarks), Print/Save-as-PDF (NSPrintOperation / WKWebView.createPDF), Share via NSSharingServicePicker, and bookmark folders+import/export. Reader mode and a PiP toggle can follow.

#### 14. [MEDIUM] Ad-block compile is async but apply() reads ruleList synchronously — first page loads have NO ad/tracker blocking (race)
_Race condition · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/AdBlockEngine.swift  (lines 36-40 (compile triggered at WebViewModel.swift:69-71, consumed at WebView.swift:52-54))`  

compileRules() is asynchronous (WKContentRuleListStore lookup/compile both use completion handlers). It is kicked off in WebViewModel.init, but WebView.apply(to:) is called synchronously in makeNSView and only adds the rule list if `self.ruleList` is already non-nil. On a cold start (rule list not yet cached/compiled), the restored tabs' WebViews are created before compilation completes, so `ruleList` is nil and apply() silently does nothing — the user's first page(s) load fully unblocked. There is also no completion-driven re-application once compilation finishes.

```
func apply(to config: WKWebViewConfiguration) {
    if let ruleList = ruleList {   // nil until async compile finishes -> silently skips blocking
        config.userContentController.add(ruleList)
    }
}
// WebViewModel.init: AdBlockEngine.shared.compileRules { _ in }  // fire-and-forget, not awaited
```

**Fix:** Make apply() async: call compileRules and add the returned list in its completion (config.userContentController.add(list)), or block creation of the first WebView until rules are ready. Also re-apply to already-created webviews when compilation finishes.

#### 15. [MEDIUM] Toggling Ad-block / privacy settings off has no runtime effect — stale WebViews keep injected scripts & rule lists
_Logic error / lifecycle · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 51-57 (config only consulted in makeNSView; cache at line 28))`  

Ad-block, anti-fingerprinting, tracker-block, popup-block, HTTPS-upgrade, and user-agent (desktop/mobile) decisions are baked into a WKWebViewConfiguration exactly once, when a WKWebView is first created in makeNSView, and the WKWebView is then cached forever in webViewCache. When the user flips any of these toggles in Settings at runtime, AdBlockEngine.invalidate() / re-apply is never called and existing cached WebViews are never rebuilt, so the change only affects brand-new tabs created after the toggle. Worse, WKUserScripts and WKContentRuleLists cannot be removed from a live userContentController per these flags. The Settings toggles therefore appear to do nothing for the current session's tabs.

```
func makeNSView(context: Context) -> WKWebView {
    if let cached = Self.webViewCache[tabId] { ... return cached }  // returns stale config'd webview
    let config = WKWebViewConfiguration()
    if viewModel.prefs.isAdBlockEnabled { AdBlockEngine.shared.apply(to: config) }  // evaluated once, never re-evaluated
    PrivacyManager.shared.applyPrivacySettings(to: config)
    ...
}
```

**Fix:** On a privacy/adblock toggle, evict & rebuild affected WebViews (clear webViewCache and reload), or remove/add content rule lists and user scripts via userContentController on the cached webviews. At minimum, reload the active tab after a toggle so the user perceives the change.

#### 16. [MEDIUM] Tracker-blocked counter is dead code — incrementTrackersBlocked() is never called; UI stat is always 0
_Logic error (broken feature) · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 344-347)`  

incrementTrackersBlocked(count:) is defined but has zero call sites in the entire codebase (verified by grep). Blocking is performed by WKContentRuleList and injected JS, neither of which reports back to Swift, so trackersBlocked is never incremented at runtime. The Start Page 'Trình theo dõi đã bị chặn' shield (StartPageView.swift:40-63), the menu stat (ContentView.swift:131) and Settings stat (SettingsView.swift:62) therefore always show the persisted value (effectively 0 for new users) — a prominently advertised privacy feature that is non-functional.

```
func incrementTrackersBlocked(count: Int = 1) {
    trackersBlocked += count
    UserDefaults.standard.set(trackersBlocked, forKey: trackersBlockedKey)
}
// grep 'incrementTrackersBlocked' -> only this definition, no callers
```

**Fix:** Report blocks back to Swift via a WKScriptMessageHandler from the tracker/ad-block JS (postMessage on each block) and call incrementTrackersBlocked there, or hide the counter UI until real data exists.

#### 17. [MEDIUM] PrivacyManager.clearCookies passes WKWebsiteDataRecord.DisplayNameKey as a data type — clears nothing
_Logic error / API misuse · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/PrivacyManager.swift  (lines 366-373)`  

WKWebsiteDataRecord.DisplayNameKey is the dictionary key string for a record's display name; it is NOT a valid WKWebsiteDataType identifier. Passing it to fetchDataRecords(ofTypes:)/removeData(ofTypes:) matches no real data category, so clearCookies() is a no-op — cookies are never removed by this path. (To clear cookies the type must be WKWebsiteDataTypeCookies.)

```
func clearCookies(completion: @escaping () -> Void) {
    let dataStore = WKWebsiteDataStore.default()
    dataStore.fetchDataRecords(ofTypes: [WKWebsiteDataRecord.DisplayNameKey]) { records in   // wrong constant
        dataStore.removeData(ofTypes: [WKWebsiteDataRecord.DisplayNameKey], for: records) { completion() }
    }
}
```

**Fix:** Use the proper type set, e.g. let types: Set<String> = [WKWebsiteDataTypeCookies]; dataStore.removeData(ofTypes: types, modifiedSince: .distantPast) { completion() }.

#### 18. [MEDIUM] App and ContentView create two independent WebViewModel instances; the App's @StateObject is dead and runs orphan timers/restore
_State management / wasted resources · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/HelixBrowserApp.swift  (lines 5, 13-14 (and ContentView.swift:4))`  

HelixBrowserApp declares `@StateObject private var viewModel = WebViewModel()` but instantiates ContentView() with no arguments and never injects that model (no .environmentObject, no parameter). ContentView in turn creates its OWN `@StateObject var viewModel = WebViewModel()`. So two WebViewModels exist: the App's one is never rendered/used by any view, yet it still runs restoreTabs(), starts two repeating Combine timers (30s save + 60s suspend), compiles ad-block rules, and writes saved-tabs to UserDefaults — pure waste and a source of duplicate UserDefaults writes. The menu commands happen to still work only because they communicate via NotificationCenter to ContentView's model, masking the disconnect.

```
@main struct HelixBrowserApp: App {
    @StateObject private var viewModel = WebViewModel()   // created, never passed down
    var body: some Scene { WindowGroup { ContentView() ... } }  // no injection
}
// ContentView: @StateObject var viewModel = WebViewModel()   // a second, separate instance
```

**Fix:** Pick a single source of truth: either inject the App's model (.environmentObject(viewModel) / ContentView(viewModel: viewModel)) and remove ContentView's @StateObject, or delete the unused @StateObject from the App.

#### 19. [MEDIUM] WindowGroup allows multiple windows that share a process-global static WebView cache and restore identical tab IDs
_Concurrency / multi-window lifecycle · Correctness & Crashes · effort L_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 9-11 (with ContentView.swift:4, WebViewModel.swift:48-57))`  

SwiftUI WindowGroup permits the user to open multiple browser windows (File > New Window / Cmd-N is provided by default). Each window gets its own ContentView with its own WebViewModel, but WebView's caches `webViewCache` and `pendingURLLoad` are `static` (process-global, keyed only by tabId). Because every WebViewModel.init restores the SAME persisted tab set from UserDefaults (same UUIDs), two windows can hold tabs with identical IDs and then collide on the single shared static WKWebView entry — one window can hijack/share another window's WKWebView instance, and clearCache(for:) in one window can yank the webview out from under another. There is no per-window scoping.

```
private static var webViewCache: [UUID: WKWebView] = [:]
private static var pendingURLLoad: [UUID: String] = [:]
static func clearCache(for tabId: UUID) { webViewCache.removeValue(forKey: tabId); ... }
// restoreTabs() decodes the same saved UUIDs into every window's WebViewModel
```

**Fix:** Either restrict to a single window (use a Window scene / handlesExternalEvents) or scope the webview cache per WebViewModel instance (make it an instance property of the model) and give restored tabs fresh per-window identity.

#### 20. [MEDIUM] App Transport Security disabled globally (NSAllowsArbitraryLoads = true)
_Network Security / ATS · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/Info.plist  (lines 27-31)`  

ATS is turned off for the entire app with NSAllowsArbitraryLoads=true. This allows unrestricted cleartext HTTP and weak-TLS connections from any app-initiated request, removing the OS-level transport-security floor. Combined with the certificate-bypass above, the app accepts both insecure transports and invalid certs. While a browser legitimately needs to load arbitrary user-navigated sites (which WKWebView allows independently of ATS), disabling ATS app-wide also weakens any first-party/app-originated networking (e.g. the favicon fetches to google.com via AsyncImage) and is a frequent App Store review flag without justification.

```
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

**Fix:** Prefer NSAllowsArbitraryLoadsInWebContent=true (scopes the exception to WKWebView page content while keeping ATS enforced for app-originated networking) instead of the blanket NSAllowsArbitraryLoads. Document the justification for App Store review.
**Verifier note:** Replace the blanket NSAllowsArbitraryLoads=true in Info.plist (lines 27-31) with the WKWebView-scoped exception so ATS stays enforced for first-party/app-originated networking (e.g. the favicon AsyncImage fetch and any future endpoints):

<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoadsInWebContent</key>
    <true/>
</dict>

This preserves the legitimate ability to render arbitrary user-navigated sites in WKWebView while keeping the OS transport-security floor for the app's own URLSession/AsyncImage traffic, and is the standard browser pattern App Store review expects. Document the justification in the review notes. Critically, this ATS change must be paired with fixing the separate, more severe cert-validation bypass in WebView.swift (lines 345-352): the didReceive challenge handler should call SecTrustEvaluateWithError on challenge.protectionSpace.serverTrust and only return .useCredential when validation succeeds (otherwise .cancelAuthenticationChallenge / surface a cert-error interstitial). Scoping ATS without fixing the cert bypass leaves the actual MITM exposure intact.

#### 21. [MEDIUM] "Block third-party cookies" setting does the opposite — explicitly ALLOWS cookies
_Cookie / Storage Isolation · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/PrivacyManager.swift  (lines 349-353)`  

When the user enables 'Chặn cookie bên thứ ba' (block third-party cookies — on by default per Prefs.swift:18), the code calls setCookiePolicy(.allow), which sets the cookie policy to ALLOW all cookies. There is no .allowCurrentSessionOnly or per-site restriction, and WKHTTPCookieStore has no API to block third-party cookies this way at all. The setting is a no-op at best and actively counterproductive at worst — the privacy feature advertised in Settings provides zero protection. WKWebView third-party cookie blocking must be done via WKWebpagePreferences / network ITP, not this call.

```
if prefs.isBlockThirdPartyCookies {
    if #available(macOS 12.0, *) {
        config.websiteDataStore.httpCookieStore.setCookiePolicy(.allow)
    }
}
```

**Fix:** Remove the setCookiePolicy(.allow) call. Rely on WKWebsiteDataStore's built-in Intelligent Tracking Prevention (on for the default store) for third-party cookie partitioning, and/or block known third-party cookie sources via the content rule list. If the toggle cannot be honored, do not present it as a working privacy control.
**Verifier note:** Remove the `setCookiePolicy(.allow)` call — it is a no-op that misrepresents protection. To actually honor the toggle: (1) Rely on WKWebsiteDataStore's built-in ITP (already active on the default store) for cross-site cookie partitioning, and document that as the mechanism. (2) For stronger enforcement, add a WKContentRuleList rule using the `block-cookies` action type (WebKit content-blocker supports `{"action": {"type": "block-cookies"}}` with `load-type: ["third-party"]` triggers) so third-party cookie requests are actually stripped; AdBlockEngine.swift already compiles a rule list and is the natural place to add this. (3) If neither can be wired up reliably, do not present the toggle as a working control — either remove it from SettingsView or mark it as relying on system ITP so users are not misled. Whatever path is chosen, the current branch must not call `.allow`, since that is semantically the opposite of the toggle's intent.

#### 22. [MEDIUM] Incognito tabs share the global WKProcessPool and persistent history/cache leaks via shared default store paths
_Private/Incognito Mode Correctness · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 36, 59-63)`  

Private tabs are only partially isolated. (1) Every WKWebView (including incognito) is built with config.processPool = viewModel.processPool — a single shared WKProcessPool across normal and private tabs, so they share the same web-content process state. A correct private mode must use a separate process pool / data store from the outset. (2) The non-persistent data store is set AFTER PrivacyManager.applyPrivacySettings(to: config) ran against config (line 57 vs 62), and the cookie-policy call at PrivacyManager.swift:351 operates on config.websiteDataStore before the swap, so privacy config can be applied to the wrong store. (3) More importantly, onPageFinished only checks activeTabIsIncognito() (WebViewModel.swift:333) to gate history saving — but didFinish always runs onPageFinished for the active tab; if a background incognito tab finishes while a non-incognito tab is active the gating is by active tab, not the loading tab, so an incognito page's URL/title can still be processed for the active (non-incognito) tab and vice-versa, and history correctness depends on which tab is foreground rather than which tab navigated.

```
config.processPool = viewModel.processPool   // shared across all tabs
...
let tab = viewModel.tabs.first(where: { $0.id == tabId })
if tab?.isIncognito == true {
    config.websiteDataStore = WKWebsiteDataStore.nonPersistent()
}
```

**Fix:** Give incognito WebViews a distinct WKWebsiteDataStore.nonPersistent() set BEFORE applyPrivacySettings runs, and use a dedicated process pool for private tabs. Gate history/onPageFinished on the navigating tab's own isIncognito flag (pass tabId through), not on activeTabId. Verify no history, cookies, cache, or local storage from private tabs persists after close.
**Verifier note:** Fix the concrete ordering bug first: in WebView.swift (lines 43-63), create the incognito WKWebsiteDataStore.nonPersistent() and assign it to config.websiteDataStore BEFORE calling PrivacyManager.shared.applyPrivacySettings(to: config), so cookie/store-level settings (PrivacyManager.swift:349-353) apply to the ephemeral store rather than the default persistent one that is then discarded. Optionally give incognito webviews a dedicated WKProcessPool (e.g. a separate lazily-created pool on the viewModel) for defense-in-depth, though the non-persistent store is the primary isolation mechanism. Also review the cookie-policy call at PrivacyManager.swift:351: under an isBlockThirdPartyCookies pref it sets .allow, which appears inverted and warrants a separate look. For history gating: the current activeTabId guard is functionally safe against incognito leakage, but for robustness pass the coordinator's own tabId into onPageFinished and gate history on that tab's isIncognito flag rather than on activeTabId; this also fixes the minor missed-history case when the user switches tabs during the async dispatch. Do NOT prioritize this as a high-severity incognito-history-leak fix, because the claimed cross-contamination does not occur in the current code.

#### 23. [MEDIUM] clearCookies() clears nothing — passes a record-property key as a data type
_Clear Browsing Data · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/PrivacyManager.swift  (lines 366-373)`  

clearCookies() fetches and removes data records of type WKWebsiteDataRecord.DisplayNameKey. DisplayNameKey is a key for reading a record's display-name property, NOT a WKWebsiteDataType. fetchDataRecords(ofTypes:) with this value matches no real data type, so the returned record set is empty and removeData removes nothing. The cookie-clearing path is effectively dead. (The main 'Clear all' path in WebViewModel.clearCookiesAndCache uses allWebsiteDataTypes() correctly, so this specific function appears to be broken/unused, but it is shipped privacy API.)

```
func clearCookies(completion: @escaping () -> Void) {
    let dataStore = WKWebsiteDataStore.default()
    dataStore.fetchDataRecords(ofTypes: [WKWebsiteDataRecord.DisplayNameKey]) { records in
        dataStore.removeData(ofTypes: [WKWebsiteDataRecord.DisplayNameKey], for: records) {
            completion()
        }
    }
}
```

**Fix:** Use the cookie data type: dataStore.removeData(ofTypes: [WKWebsiteDataTypeCookies], modifiedSince: Date(timeIntervalSince1970: 0)) { completion() }.

#### 24. [MEDIUM] Address-bar input allows navigation to file:// URLs with no restriction
_Scheme Exposure (file://) · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/UrlUtils.swift  (lines 19-21)`  

formatUrl explicitly passes through any input starting with file:// (and about:) unchanged, and WebViewModel.loadUrl/WebView load it directly into the WKWebView. There is no allowingReadAccessTo scoping and no confirmation. While WKWebView's default file access is restricted, allowing arbitrary file:// navigation from the omnibox lets a page-triggered navigation or a crafted link read local files the app sandbox can reach, and exposes the local filesystem browsing surface. For an unsandboxed build (no entitlements file is present in the repo) this widens local-file exposure. There is also no guard preventing javascript: scheme entry in the omnibox (javascript: pasted into the address bar would be executed in the current page context — self-XSS / bookmarklet injection).

```
if trimmed.hasPrefix("file://") || trimmed.hasPrefix("about:") {
    return trimmed
}
```

**Fix:** Either block file:// from the omnibox or load it with loadFileURL(_:allowingReadAccessTo:) scoped to the specific file's directory. Explicitly reject javascript: and data: schemes typed/pasted into the address bar (strip or refuse them) to prevent self-XSS.

#### 25. [MEDIUM] Find-in-page (pre-macOS 13) and zoom inject values into evaluateJavaScript with weak/absent escaping
_JavaScript Injection Safety · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 184, 216)`  

Two evaluateJavaScript calls interpolate values into JS source strings. (1) The pre-macOS-13 find fallback escapes only single quotes: window.find('\(text.replacingOccurrences(of: "'", with: "\\'"))'). A search term containing a backslash, newline, or </script> sequence is not handled; a backslash before the closing quote (e.g. text ending in \) breaks out of the string literal, allowing arbitrary JS to run in the page. The find text is user-controlled but low-impact (user attacks own page); however if find text is ever pre-filled from page selection it becomes page-controlled. (2) The zoom handler interpolates a Double into document.body.style.zoom = '\(scale)'; scale is bounded numeric so currently safe, but the pattern is fragile. These are the only string-interpolated JS sinks; the WKUserScripts in PrivacyManager are static (good).

```
self.lastWebView?.evaluateJavaScript("window.find('\(text.replacingOccurrences(of: "'", with: "\\'"))')")
...
self.lastWebView?.evaluateJavaScript("document.body.style.zoom = '\(scale)'")
```

**Fix:** Avoid building JS via string interpolation. For find, use the native find(_:configuration:) API on all supported OS versions or JSON-encode the term (let json = String(data: try JSONEncoder().encode(text), encoding: .utf8)) and inject window.find(\(json)). For zoom, use webView.pageZoom or interpolate a validated numeric only.

#### 26. [MEDIUM] No Safe Browsing / malicious-site protection of any kind
_Safe Browsing · Security & Privacy · effort L_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 228-256)`  

decidePolicyFor navigationAction does HTTPS upgrade and popup handling but performs no phishing/malware reputation check (no Google Safe Browsing, no Apple Fraudulent Website Warning equivalent, no blocklist). A production browser is expected to warn users before navigating to known-malicious URLs. WKWebView does not provide this automatically the way mobile SFSafariViewController does. This is a missing core safety feature rather than a bug.

```
// decidePolicyFor only handles HTTPS upgrade + popup blocking, then:
decisionHandler(.allow)
```

**Fix:** Integrate a Safe Browsing source (e.g. Google Safe Browsing Lookup/Update API or a local blocklist) and present a full-page warning interstitial before allowing navigation to flagged URLs. At minimum document the gap.

#### 27. [MEDIUM] Downloads written to ~/Downloads with no path-traversal sanitization of suggestedFilename
_Download Path Traversal · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/DownloadManager.swift  (lines 49-61)`  

decideDestinationUsing builds the destination as downloadsDirectory.appendingPathComponent(suggestedFilename) using the server-/page-controlled suggestedFilename verbatim. A malicious filename containing path separators or '..' (e.g. '../../foo' or an absolute-looking name) can cause the file to be written outside the intended Downloads directory. It also unconditionally deletes any existing file at the computed path (try? FileManager.removeItem(at: dest)) before download — a crafted filename could be used to delete/overwrite a file outside Downloads. There is no user confirmation of save location, no filename de-duplication, and no MIME/extension sanity check.

```
let dest = downloadsDirectory.appendingPathComponent(suggestedFilename)
// Remove existing file if needed
try? FileManager.default.removeItem(at: dest)
...
completionHandler(dest)
```

**Fix:** Sanitize suggestedFilename to its last path component and strip path separators / '..' (use (suggestedFilename as NSString).lastPathComponent and reject empty/dot names). Verify the resolved destination's standardizedFileURL is still contained within the Downloads directory before writing. De-duplicate (append (1), (2)) instead of deleting existing files, or prompt the user.

#### 28. [LOW] History deduplication only checks the immediately previous entry
_History · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 366-377)`  

`saveHistory` skips a new entry only if `history.first?["url"] == url`. Re-visiting a URL that is not the very latest entry creates duplicate history rows rather than updating the existing entry's timestamp, so the history list fills with repeated entries for frequently visited sites instead of moving them to the top with an updated visit time.

```
if history.first?["url"] == url { return }
history.insert(entry, at: 0)
```

**Fix:** Remove any existing entry with the same URL before inserting at the front (move-to-top with updated timestamp / visit count), instead of only comparing against the first element.

#### 29. [LOW] HTTPS-upgrade can hard-fail HTTP-only sites with no fallback
_Navigation / Address bar · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 234-243)`  

When HTTPS upgrade is enabled, any http:// navigation is cancelled and reloaded as https://. If the target host does not support HTTPS, the load fails and the user is shown the generic error page with no automatic fallback to http and no per-site exception. Real browsers attempt the upgrade but fall back to http (or offer a 'continue to HTTP' option) when HTTPS is unavailable.

```
if Prefs.shared.isHttpsUpgradeEnabled && url.scheme == "http" {
    var components = ...; components?.scheme = "https"
    if let httpsUrl = components?.url { decisionHandler(.cancel); webView.load(URLRequest(url: httpsUrl)); return }
}
```

**Fix:** Track upgraded navigations and, on HTTPS failure for an upgraded URL, fall back to the original http:// URL (HTTPS-Only-with-fallback behavior) or present a 'continue insecurely' prompt.

#### 30. [LOW] No <input type=file> upload support (no open-panel delegate)
_File upload · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 354-416)`  

WebKit on macOS shows the file picker for <input type=file> automatically, so basic upload works without custom code. However the WKUIDelegate does not implement `webView(_:runOpenPanelWith:initiatedByFrame:completionHandler:)`, so the app cannot constrain/observe uploads or support directory/multiple selection customizations, and (depending on App Sandbox configuration) may be relying entirely on default behavior. Worth confirming uploads work under the app's sandbox/entitlements since no explicit handling exists.

```
// WKUIDelegate implements alert/confirm/prompt/createWebView but NOT runOpenPanelWith (grep: 0 matches for runOpenPanel/openPanel)
```

**Fix:** Verify file uploads function under the shipping entitlements; if needed, implement `runOpenPanelWith` to present an NSOpenPanel honoring `parameters.allowsMultipleSelection`/`allowsDirectories`.

#### 31. [LOW] Per-page zoom is applied via document.body.style.zoom and is lost on SPA navigations / not persisted; defaultZoom pref is never used
_Logic error / UX · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 209-217 (pref: Prefs.swift:13, SettingsView.swift:149-190))`  

Zoom is implemented by injecting `document.body.style.zoom = '<scale>'`. It is only re-applied on full didFinish navigations (WebView.swift:282); for single-page apps that swap document.body or navigate via history.pushState without a full load, the inline style is dropped and zoom silently resets. Additionally, the 'Mức thu phóng mặc định' (defaultZoom) preference in Settings is wired to AppStorage but never read anywhere (grep shows no consumer in zoom logic), so changing default zoom does nothing.

```
self.lastWebView?.evaluateJavaScript("document.body.style.zoom = '\(scale)'")
// zoomIn/Out start from `zoomLevels[activeTabId] ?? 100` — never seeded from prefs.defaultZoom; grep 'defaultZoom' shows only SettingsView, never applied
```

**Fix:** Prefer WKWebView.pageZoom (macOS 11+) instead of injecting CSS zoom so it survives in-page navigation, and seed zoomLevels / pageZoom from prefs.defaultZoom when a webview is created.

#### 32. [LOW] moveTab(from:to:) defined but no .onMove wiring — tab reordering by drag is unimplemented
_Missing feature / dead code · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebViewModel.swift  (lines 177-180)`  

moveTab(from:to:) exists but is never called (no .onMove modifier or drag handler in HorizontalTabBar or anywhere — verified by grep). A production browser is expected to support drag-to-reorder tabs; here it is dead code, so users cannot reorder tabs at all (only pin moves tabs to the front).

```
func moveTab(from source: IndexSet, to destination: Int) { tabs.move(fromOffsets: source, toOffset: destination); saveTabs() }
// grep 'moveTab' / 'onMove' -> only the definition; no caller
```

**Fix:** Add drag-and-drop reordering to the tab bar that calls moveTab, or remove the dead method.

#### 33. [LOW] DownloadManager.downloadsDirectory force-unwraps FileManager URL; downloadMap mutated off the main thread
_Crash risk / thread safety · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/DownloadManager.swift  (lines 25-27, 35, 73, 85)`  

downloadsDirectory force-unwraps `FileManager.default.urls(for:.downloadsDirectory,...).first!`. In a sandboxed app without the user-selected/downloads entitlement (no entitlements file exists in the project and the app uses hiddenTitleBar windowing), this array can be empty and the force-unwrap will crash the moment a download starts. Separately, downloadMap (a plain Dictionary) is read/written from WKDownloadDelegate callbacks (handleDownload, downloadDidFinish, didFailWithError, decideDestination) which are not guaranteed to all run on the same thread, while the @Published mutations are dispatched to main — leaving downloadMap itself unsynchronized.

```
var downloadsDirectory: URL {
    FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask).first!
}
private var downloadMap: [WKDownload: UUID] = [:]   // mutated in delegate callbacks without synchronization
```

**Fix:** Guard the downloads directory (fall back to a temp/app-support dir, and surface an error instead of crashing). Confine downloadMap access to the main queue (or a dedicated serial queue).

#### 34. [LOW] Find-in-page next/prev uses window.find() which conflicts with the macOS 13 native find used for the initial search
_Logic error / broken feature · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 181-200)`  

Initial find on macOS 13+ uses the native WKWebView.find(_:configuration:) API, but findNext/findPrev always fall back to evaluateJavaScript("window.find()") / window.find('', false, true). The native find() does not maintain a selection compatible with window.find(), and window.find() with an empty string does not continue the previous search term — so 'next/previous' will not reliably advance through matches found by the native API. The two find mechanisms are mixed inconsistently, breaking iterate-through-matches behavior on modern macOS.

```
// initial: if #available(macOS 13.0, *) { self.lastWebView?.find(text, configuration: .init()) ... }
findNextObserver: self.lastWebView?.evaluateJavaScript("window.find()")
findPrevObserver: self.lastWebView?.evaluateJavaScript("window.find('', false, true)")
```

**Fix:** Use one mechanism consistently: keep the current search text and call find(text, configuration:) with `backwards`/`wraps` options for next/prev on macOS 13+, falling back to window.find(text, ...) only on older OSes.

#### 35. [LOW] Developer extras (Web Inspector) force-enabled in production builds
_Hardening / Attack Surface · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/macos/HelixBrowser/WebView.swift  (lines 49)`  

developerExtrasEnabled is unconditionally set to true via KVC on every WebView config, enabling the Web Inspector / right-click 'Inspect Element' for all users in release builds. This is generally undesirable in a shipping consumer browser (it is a debugging affordance), increases attack surface, and uses private-ish KVC keys that can trip App Store review. minimumFontSize and fullScreenEnabled are also set via setValue(forKey:) KVC rather than supported properties.

```
config.preferences.setValue(true, forKey: "developerExtrasEnabled")
```

**Fix:** Gate developerExtrasEnabled behind #if DEBUG (or use webView.isInspectable on macOS 13.3+ only in debug). Replace KVC string-key configuration with the supported public properties where available.

#### 36. [LOW] No app sandbox / entitlements file in repo; ProcessInfo.processName mutation is unsupported
_Sandboxing / Build Hardening · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/macos/build_dmg.sh  (lines 52-60)`  

The build script (build_dmg.sh) compiles with swiftc and packages a .app with only Info.plist copied — there is no .entitlements file anywhere in the macos/ tree and no codesign / App Sandbox / Hardened Runtime step. The resulting app runs unsandboxed and unsigned, giving web content (in the event of a WebKit exploit, amplified by the cert-bypass above) the app's full unsandboxed filesystem access, and the DMG will be Gatekeeper-blocked. Separately, HelixBrowserApp.swift:9 assigns ProcessInfo.processInfo.processName which is documented as not reliably settable and is a no-op/undefined on macOS.

```
cp "$MACOS_DIR/Info.plist" "$APP_BUNDLE/Contents/Info.plist"
# (no .entitlements, no codesign, no hardened runtime, no sandbox)
hdiutil create -volname "$APP_NAME" -srcfolder "$APP_BUNDLE" -ov -format UDZO "$DMG_NAME"
```

**Fix:** Add a HelixBrowser.entitlements enabling App Sandbox (com.apple.security.app-sandbox), network client, and only the file-access entitlements actually needed; codesign with Hardened Runtime and notarize before DMG creation. Remove the ProcessInfo.processName assignment.


## Linux (Python) — 32 findings

#### 1. [HIGH] Downloads are completely non-functional — no download signal is ever connected
_Downloads · Browsing Feature Completeness · effort L_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-390 (_create_webview); 423-438 (_on_decide_policy))`  

The app has a full downloads DB schema and helper methods, and the settings dialog offers 'Clear Downloads', but the browser never wires up WebKit's download machinery. Clicking any download link, or navigating to a non-displayable MIME type (PDF, zip, exe, etc.), results in nothing happening (or the page being left blank), with no file written, no progress, no destination prompt, no notification. Pause/resume/open-file and a downloads panel are all absent. This is a core browser feature that is entirely missing.

```
_create_webview connects only: load-changed, notify::title, notify::uri, notify::estimated-load-progress, decide-policy. There is NO connection to WebKit2.WebContext 'download-started' (nor any WebKit2.Download handling). _on_decide_policy only handles PolicyDecisionType.NAVIGATION_ACTION and returns False for RESPONSE decisions, so a response whose MIME type cannot be displayed is never converted to a download. database.py has add_download/update_download_status/get_downloads (lines 168-196) but nothing ever calls add_download — grep shows zero callers.
```

**Fix:** Connect to the WebContext 'download-started' signal (webview.get_context().connect('download-started', ...)), handle WebKit2.Download (set destination via 'decide-destination', track 'received-data'/'finished'/'failed', show progress + notification), and in _on_decide_policy handle PolicyDecisionType.RESPONSE: if not decision.is_mime_type_supported(), call decision.download(). Persist rows via db.add_download/update_download_status and build a downloads UI.
**Verifier note:** Wire up WebKitGTK's download machinery. (a) In _create_webview, after creating the webview, connect the context's download-started signal: webview.get_context().connect('download-started', self._on_download_started). Note both the default and the ephemeral incognito context (line 355) need this, so connect on whichever context the webview actually uses (webview.get_context()). (b) Implement _on_download_started(context, download): connect the WebKit2.Download signals — 'decide-destination' (choose a path, e.g. ~/Downloads via GLib.get_user_special_dir(GLib.UserDirectory.DIRECTORY_DOWNLOAD), call download.set_destination(uri), return True), 'created-destination', 'received-data' / notify::estimated-progress (update progress UI), 'finished', and 'failed'. (c) In _on_decide_policy, add a branch: `elif decision_type == WebKit2.PolicyDecisionType.RESPONSE:` and if not decision.is_mime_type_supported(): decision.download(); return True. (d) Persist rows: call db.add_download(url, filename, filepath) when a download starts (capture the returned id) and db.update_download_status(id, 'completed'/'failed'/'cancelled') on finished/failed. (e) Build a minimal Downloads UI (menu entry + panel listing get_downloads(), with open-file/show-in-folder, and ideally pause/resume/cancel) so the existing 'Clear Downloads' setting has corresponding content. While here, note _show_history and _show_bookmarks are empty stubs (pass) — adjacent advertised features that are also non-functional.

#### 2. [HIGH] window.open / target=_blank / popups open nothing — no 'create' signal handler
_Multi-tab / navigation · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 373-377 (_create_webview); 423-438 (_on_decide_policy))`  

Links with target=_blank and JavaScript window.open() calls fire WebKit's 'create' signal (and NEW_WINDOW_ACTION policy). Because neither is handled, such links silently do nothing instead of opening a new tab. Many sites (login flows, OAuth popups, 'open in new tab') rely on this, so they appear broken. A production browser must route 'create' into _new_tab().

```
No webview.connect('create', ...) anywhere. _on_decide_policy only branches on PolicyDecisionType.NAVIGATION_ACTION; PolicyDecisionType.NEW_WINDOW_ACTION is never handled and falls through to 'return False' (default = ignore for new-window). The only window.open interception is the JS POPUP_BLOCKER_JS in privacy_manager.py which can return null for legitimate opens too.
```

**Fix:** Connect the 'create' signal, create a related WebView and a new tab to host it, and return that view; in _on_decide_policy treat NEW_WINDOW_ACTION by opening the target URI in a new tab.
**Verifier note:** Add new-window support in _create_webview. Connect the 'create' signal to a handler that creates a related WebView via WebKit2.WebView.new_with_related_view(webview) (so it shares the same WebContext/session, important for OAuth flows and incognito), wraps it in a new tab/BrowserTab, and RETURNS that WebView so WebKit can host the popup. Wire the related view's 'ready-to-show' / 'load-changed' so the new tab's URL and title update, and connect the same set of signals (load-changed, notify::title/uri/progress, decide-policy, and 'create') to it for nested popups. For target=_blank, the 'create' handler covers it; optionally also handle NEW_WINDOW_ACTION explicitly in _on_decide_policy if you want to redirect certain new-window navigations into the current tab. Be careful not to regress the existing popup blocker: keep is_block_popups gating the ad-domain/unactivated-open blocking in JS, but ensure legitimate user-activated opens still reach the new 'create' path. Add at least a manual test against a real OAuth/login popup and a target=_blank link from the start page.

#### 3. [HIGH] Background-tab load/title/URI handlers corrupt the active tab (signals ignore the emitting webview)
_Multi-tab · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 392-417)`  

All tabs are loaded eagerly: _new_tab loads immediately, and on restart restore_session loads every restored tab's URL up front (browser_window.py lines 34-45). When a background tab finishes loading, changes its <title>, or redirects, these handlers mutate the *currently active* tab's title/url and overwrite the address bar with the background tab's URI, and write the wrong tab's page into history. This is a guaranteed multi-tab correctness/data-integrity bug under normal use (e.g., open tab A, switch to B; A's redirect rewrites B's address bar).

```
def _on_load_changed(self, webview, event): ... tab = self.tab_manager.active_tab  (uses webview.get_uri() but writes history for whichever tab is *active*). def _on_title_changed(self, webview, param): tab = self.tab_manager.active_tab; tab.title = webview.get_title()... def _on_uri_changed(self, webview, param): ... tab = self.tab_manager.active_tab; tab.url = uri; self.url_entry.set_text(uri). The handlers receive the emitting `webview` arg but resolve the tab via active_tab instead of mapping webview->tab.
```

**Fix:** Map the emitting webview back to its BrowserTab (e.g., store tab.id on the webview or keep a webview->tab dict) and update only that tab; only touch url_entry/window title/progress when webview is self._current_webview.
**Verifier note:** Map each emitting webview back to its BrowserTab and update only that tab; only touch shared UI (url_entry, window title, progress bar, nav buttons) when the emitting webview is self._current_webview.

Concretely: when creating a webview, store a back-reference (e.g. `webview.helix_tab = tab` or maintain a `self._webview_to_tab` dict), then in each handler do `tab = self._tab_for(webview)` and guard chrome updates with `if webview is self._current_webview`.

- _on_title_changed: set tab.title for the emitting tab; only call self.set_title(...) when `webview is self._current_webview`. Always refresh the tab bar so background titles update.
- _on_uri_changed: set the emitting tab.url; only call self.url_entry.set_text(uri) and self._update_ssl_icon(uri) when `webview is self._current_webview`.
- _on_load_changed: resolve the emitting tab and key both the incognito and is_save_history checks off THAT tab (`if tab and self.prefs.is_save_history and not tab.is_incognito`), not active_tab; only reset the progress bar / update nav buttons when `webview is self._current_webview`.
- _on_progress similarly should only drive the shared progress_bar for the current webview.

#### 4. [HIGH] WebView signal handlers operate on active_tab instead of the firing webview's tab, corrupting tab state on background loads / session restore
_Lifecycle / state corruption · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 392-417 (handlers); 34-45 (restore loop))`  

Every webview connects load-changed, notify::title, notify::uri and notify::estimated-load-progress to handlers that completely ignore the `webview` argument identifying which view fired, and instead mutate `self.tab_manager.active_tab`, `self.url_entry`, `self.set_title(...)` and `self.progress_bar`. Background webviews remain alive and continue loading after they are detached from the container in `_switch_to_tab` (lines 287-292 remove children but `tab.webview` keeps the object and its in-flight load). The most deterministic trigger is session restore: `__init__` creates a webview for EVERY restored tab and calls `webview.load_uri(tab.url)` for each (lines 35-39), so N background loads run concurrently while only one tab is active. As each background tab fires notify::title / notify::uri, `_on_title_changed` (line 404 `tab = self.tab_manager.active_tab`) and `_on_uri_changed` (line 412) overwrite the ACTIVE tab's title and url with the background page's values, set the window title and address bar to the wrong page, and call `_update_tab_bar()` repeatedly. After restore the active tab's `title`/`url` are whatever background tab happened to finish last. `_on_load_changed` (line 396-401) records history using the firing webview's uri but gated on the ACTIVE tab's `is_incognito` flag, mis-attributing visits.

```
def _on_title_changed(self, webview, param):
    tab = self.tab_manager.active_tab   # <-- ignores `webview`, always the foreground tab
    if tab:
        tab.title = webview.get_title() or UrlUtils.get_display_url(tab.url)
        self._update_tab_bar()
        self.set_title(tab.title + " - Helix Browser")

def _on_uri_changed(self, webview, param):
    uri = webview.get_uri() or ""
    tab = self.tab_manager.active_tab   # <-- same bug
    if tab:
        tab.url = uri
    if not uri.startswith("helix://"):
        self.url_entry.set_text(uri)
```

**Fix:** Map each webview back to its owning BrowserTab (e.g. store the tab on the webview via a dict {webview: tab} or set webview as_an attribute, or use functools.partial to bind the tab when connecting). In every handler resolve `tab = self._tab_for_webview(webview)` and only touch the address bar / window title / progress bar / nav buttons when `tab is self.tab_manager.active_tab`. History should be gated on the loading tab's `is_incognito`, not the active tab's.
**Verifier note:** Bind each webview to its owning BrowserTab and resolve the tab inside every handler from the firing webview, not from active_tab. Concretely: in _create_webview accept/return the tab and store the back-reference (e.g. webview._helix_tab = tab, or maintain self._webview_to_tab = {webview: tab}), then add a helper _tab_for_webview(webview). In _on_title_changed/_on_uri_changed/_on_progress/_on_load_changed do `tab = self._tab_for_webview(webview)`; always update tab.title/tab.url for that tab, but only touch UI chrome (self.url_entry, self.set_title, self.progress_bar, _update_nav_buttons, _update_ssl_icon) when `tab is self.tab_manager.active_tab`. Gate history in _on_load_changed on the LOADING tab's is_incognito (and is_save_history), not the active tab's. Remember to clean up the mapping when a tab/webview is closed to avoid leaks. Optionally defer background webview loads until first activation (lazy/suspended restore) to avoid N concurrent loads at startup.

#### 5. [HIGH] window.open() and target="_blank" links are silently dropped (no create-web-view handler)
_Broken core feature · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-390 (_create_webview); 423-438 (_on_decide_policy))`  

WebKitGTK emits the `create` signal when a page calls window.open() or follows a link with target="_blank" / NEW_WINDOW_ACTION. The webview never connects `create`, and `_on_decide_policy` returns False for NEW_WINDOW_ACTION decisions, so WebKit's default returns NULL and the new-window/new-tab navigation is discarded. The result is that an enormous fraction of real-world sites (any "open in new tab" link, OAuth/login popups, payment popups, web app shortcuts) do nothing at all. For a production browser this is a broken essential feature, and OAuth popups failing silently can leave users unable to sign in.

```
webview.connect("load-changed", self._on_load_changed)
webview.connect("notify::title", self._on_title_changed)
webview.connect("notify::uri", self._on_uri_changed)
webview.connect("notify::estimated-load-progress", self._on_progress)
webview.connect("decide-policy", self._on_decide_policy)
# no webview.connect("create", ...)  -> window.open/_blank dropped
```

**Fix:** Connect the `create` signal: in the handler read navigation_action.get_request().get_uri(), open a new tab via _new_tab(url=...), and return the new tab's WebView so WebKit drives the navigation into it. Optionally honor is_block_popups by suppressing programmatic (non-user-activated) opens only.
**Verifier note:** Connect the WebKit2 `create` signal in _create_webview (alongside the existing connects at lines 373-377):

    webview.connect("create", self._on_create_webview)

Implement the handler to route the navigation into a new tab and return the new WebView so WebKit drives the load into it:

    def _on_create_webview(self, webview, navigation_action):
        uri = navigation_action.get_request().get_uri()
        # create a tab without loading (let WebKit load via the returned view)
        tab = self.tab_manager.create_tab(uri or "helix://start", self.tab_manager.active_tab.is_incognito if self.tab_manager.active_tab else False)
        new_view = self._create_webview(tab.is_incognito)
        tab.webview = new_view
        self._switch_to_tab(tab)
        self._update_tab_bar()
        return new_view   # returning the view lets WebKit drive the navigation; do NOT also call load_uri here

Key correctness notes:
- Return the new WebView (not None); returning None is what currently drops the navigation.
- Do not call load_uri on the returned view for window.open()/blank cases — WebKit will load into it itself. (For programmatic about:blank popups the URI may be empty, which is expected.)
- Inherit incognito/ephemeral context from the opener so popups stay in the right session.

Popup suppression is largely redundant with the existing JS override in privacy_manager.py (POPUP_BLOCKER_JS already returns null for ad URLs and non-user-activated window.open). But target=\"_blank\" link clicks bypass that JS, so if you want is_block_popups to suppress programmatic opens at the native layer too, gate it on navigation_action.is_user_gesture(): when is_block_popups is True and not a user gesture, return None to suppress. Always allow user-gesture-initiated opens so OAuth/login/payment popups work.

#### 6. [HIGH] Downloads are completely non-functional — no WebKit download handler is wired
_Broken core feature · Correctness & Crashes · effort L_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-390 (_create_webview), entire file (no download wiring))`  

Database has a full downloads table and add_download / update_download_status / get_downloads / clear_downloads methods, but nothing ever calls them. The webview's WebContext never connects `download-started`, and `_on_decide_policy` does not convert RESPONSE decisions into downloads (decision.download() / decision_type == RESPONSE with !can_show_mime_type is never handled). As a result, clicking any non-renderable file link (zip, pdf-as-attachment, exe, etc.) either does nothing or navigates to a blank page; the user can never download a file. A browser that cannot download files is not shippable.

```
grep over src/*.py for 'download' shows only database.py methods + settings 'Clear Downloads'; no WebContext.connect('download-started', ...) and no decision.download() anywhere. _on_decide_policy only handles NAVIGATION_ACTION and falls through (return False) for RESPONSE decisions.
```

**Fix:** Wire WebKit2.WebContext 'download-started' (or handle RESPONSE policy decisions where the MIME type can't be displayed) to a download manager that picks a destination, records the row via Database.add_download, and updates status on finished/failed/cancelled via update_download_status.
**Verifier note:** Wire downloads end to end. Preferred approach in _create_webview: obtain the WebContext for both the normal (webview.get_context() or WebKit2.WebContext.get_default()) and ephemeral/incognito contexts, and connect 'download-started'. In the handler, connect the Download's 'decide-destination' (choose a path under GLib.get_user_special_dir(DOWNLOAD) or prompt via a Gtk.FileChooser, then download.set_destination(uri) and return True), and connect 'finished', 'failed', and 'received-data'/'notify::estimated-progress' signals. On start, call db.add_download(url, filename, filepath, filesize) and stash the returned id on the Download object; on finished call db.update_download_status(id, 'completed'); on failed/cancelled call db.update_download_status(id, 'failed'|'cancelled'). Additionally (or alternatively) handle RESPONSE decisions in _on_decide_policy: for decision_type == WebKit2.PolicyDecisionType.RESPONSE, if not decision.is_mime_type_supported() call decision.download() and return True so attachment links reliably start downloads. Finally, surface a downloads UI (the existing _show_history/_show_bookmarks stubs return pass; add a downloads view using get_downloads) so users can see progress and open files.

#### 7. [HIGH] "Block 3rd Party Cookies" toggle is dead — no cookie manager is ever configured
_Cookie & storage isolation · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/prefs.py  (lines 14)`  

is_block_third_party_cookies defaults True and is exposed as a switch in settings (settings_dialog.py:95), but grep for set_accept_policy / CookieAcceptPolicy / get_cookie_manager / NO_THIRD_PARTY across the whole source returns nothing. The pref is never read by browser_window.py and never applied to a WebKitCookieManager, so third-party cookies are always accepted regardless of the toggle. This is a misleading privacy control: users believe third-party cookies are blocked when they are not.

```
"is_block_third_party_cookies": True,   # never wired; grep set_accept_policy / get_cookie_manager => no matches in linux/src
```

**Fix:** On the WebContext's cookie manager call set_accept_policy(WebKit2.CookieAcceptPolicy.NO_THIRD_PARTY) when is_block_third_party_cookies is enabled, and re-apply when the setting changes.
**Verifier note:** Wire the pref to a real cookie policy. In browser_window.py::_create_webview, after obtaining the context, configure the cookie manager, e.g.:

    cookie_mgr = ctx.get_cookie_manager()  # for default WebView, use webview.get_context().get_cookie_manager()
    policy = (WebKit2.CookieAcceptPolicy.NO_THIRD_PARTY
              if self.prefs.is_block_third_party_cookies
              else WebKit2.CookieAcceptPolicy.ALWAYS)
    cookie_mgr.set_accept_policy(policy)

Note that in the current code non-incognito WebViews use the default WebKit2.WebView() (shared default WebContext), so the cookie manager should be fetched via webview.get_context().get_cookie_manager() for those, and via the ephemeral ctx for incognito tabs. Apply the policy at webview creation, and also re-apply to all existing/open WebViews' contexts when the setting is changed in SettingsDialog (the dialog currently only persists the pref; add a callback so the running window updates live, or document that it takes effect on new tabs/restart). Add a regression test or at least a manual check that toggling the switch changes get_accept_policy(). If a live-update path is too invasive, at minimum read the pref at webview creation so the control is no longer a complete no-op.

#### 8. [MEDIUM] File upload (<input type=file>) is broken — no run-file-chooser handler
_File upload · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 373-377)`  

WebKitGTK does not present a native file picker on its own for <input type=file>; the embedder must handle the 'run-file-chooser' signal and run a Gtk.FileChooserDialog, then call request.select_files(...). Without this handler, clicking any file-upload control on a web page (attach a file to Gmail, upload a profile photo, etc.) does nothing — file upload is non-functional across the entire browser.

```
Signal connections in _create_webview: 'load-changed', 'notify::title', 'notify::uri', 'notify::estimated-load-progress', 'decide-policy'. There is no connection to the WebView 'run-file-chooser' signal. grep for 'file-chooser'/'run-file' across src returns nothing.
```

**Fix:** Connect 'run-file-chooser' in _create_webview, run a Gtk.FileChooserDialog (honoring request.get_select_multiple() and request.get_mime_types()), and call request.select_files(paths) on confirm or request.cancel() otherwise.
**Verifier note:** In _create_webview (browser_window.py ~line 377), add: webview.connect("run-file-chooser", self._on_run_file_chooser). Implement the handler to build a Gtk.FileChooserDialog (or Gtk.FileChooserNative) in OPEN mode, set select-multiple from request.get_select_multiple(), and apply Gtk.FileFilter(s) derived from request.get_mime_types() (or get_mime_types_filter()). On Gtk.ResponseType.ACCEPT call request.select_files(dialog.get_filenames()); on cancel call request.select_files([]) with an empty list (note: WebKit2 4.0's WebKitFileChooserRequest has no cancel() method; an empty selection signals cancellation). Return True from the handler to indicate the request was handled. Treat as medium priority since file upload is currently fully broken but the failure is silent and non-crashing.

#### 9. [MEDIUM] History and Bookmarks managers are empty stubs
_History / Bookmarks · Browsing Feature Completeness · effort L_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 519-523)`  

The menu exposes 'History' and 'Bookmarks' entries and the DB fully supports get_history/search_history/delete_history_item and get_bookmarks/search_bookmarks/add/remove, but the UI handlers do nothing. The user can record history and toggle bookmarks (Ctrl+D / star) but has no way to view, search, open, edit, delete, organize into folders, import, or export them. Bookmark folders are supported in the schema (folder column) but never surfaced. History/bookmarks management is effectively missing.

```
def _show_history(self):\n        pass\n\n    def _show_bookmarks(self):\n        pass — both menu items (win.show-history / win.show-bookmarks, lines 219-220, 230-231) invoke these no-op methods.
```

**Fix:** Implement history and bookmarks dialogs (list + search + open/delete; for bookmarks add edit + folder selection). Add bookmark import/export (HTML/Netscape format) for store/UX parity.
**Verifier note:** Implement the two dialogs the menu already points at. For _show_history: a list of db.get_history() with a search box bound to db.search_history(), row activation to open the URL in a new/current tab, per-row delete via db.delete_history_item(), plus an existing-style clear. For _show_bookmarks: a list from db.get_bookmarks() with search via db.search_bookmarks(), open-on-activate, remove via db.remove_bookmark(), an edit affordance (title/folder), and a folder filter/selector that exercises the already-present folder column (also pass folder through _toggle_bookmark/add_bookmark so folders are actually assignable). Lower priority but worth adding for parity: bookmark import/export in Netscape HTML format. Reuse the GTK patterns already in settings_dialog.py for consistency. This is medium priority — schedule it as a feature gap, not an urgent fix.

#### 10. [MEDIUM] Address bar has no search/URL suggestions or autocompletion
_Address bar · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 130-135)`  

Typing in the address bar offers no dropdown of history/bookmark matches and no live search suggestions from the search engine. The DB already has search_history/search_bookmarks ready to power this. Modern browsers are expected to provide omnibox suggestions; its complete absence is a notable UX/completeness gap.

```
self.url_entry = Gtk.Entry(); ...; self.url_entry.connect('activate', self._on_url_activate). No Gtk.EntryCompletion is attached and there is no 'changed' handler that queries db.search_history/search_bookmarks or a search-suggestions endpoint.
```

**Fix:** Attach a Gtk.EntryCompletion (or custom popover) populated on 'changed' from db.search_history/search_bookmarks; optionally fetch search-engine suggestions (e.g. suggestqueries) asynchronously.

#### 11. [MEDIUM] No Stop button / stop-loading action during navigation
_Navigation · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 116-119 (reload button); 467-469 (_reload))`  

There is no way to abort an in-progress page load. A production browser's reload button becomes a Stop (X) button during loading and calls webview.stop_loading(). Users cannot halt a hung or unwanted load.

```
Only a reload button exists (self.reload_btn -> _reload -> webview.reload()). grep for stop_loading/'.stop('/stop-loading across src returns nothing. The reload control never morphs into a Stop control while estimated-load-progress < 1.0.
```

**Fix:** Track load state via load-changed/estimated-load-progress; while loading, switch the reload button to a Stop action that calls self._current_webview.stop_loading().

#### 12. [MEDIUM] Page/text zoom is unimplemented — default_zoom pref is never applied and there are no zoom controls
_Zoom · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/prefs.py  (lines 20)`  

The preference for default zoom exists but is dead — it is never read or applied to any webview, and there is no UI (buttons or keyboard shortcuts) to change zoom. Page zoom and text zoom, standard browser features, are entirely absent.

```
prefs.py defines \"default_zoom\": 100 but grep across src for set_zoom_level/zoom_level/set_zoom returns nothing. _create_webview (browser_window.py 353-390) never calls webview.set_zoom_level(), and _setup_shortcuts (241-254) defines no Ctrl++ / Ctrl+- / Ctrl+0 accelerators.
```

**Fix:** Apply webview.set_zoom_level(prefs.default_zoom/100) on webview creation; add Ctrl++/Ctrl+-/Ctrl+0 shortcuts and persist per-session zoom.

#### 13. [MEDIUM] Desktop-site toggle missing and the user-agent override is bogus
_Desktop-site / user-agent · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 367-368)`  

Two problems: (1) The desktop-site preference has no UI control anywhere, so a user can never change it. (2) When enabled, set_user_agent_with_application_details only appends 'HelixBrowser/3.0' to WebKit's default UA — it does not switch between mobile and desktop UAs (this is a Linux desktop build, so 'desktop mode' is meaningless and the appended token does nothing useful; it can also break UA-sniffing sites). On Linux this whole feature is effectively non-functional.

```
if self.prefs.is_desktop_mode:\n    settings.set_user_agent_with_application_details(\"HelixBrowser\", \"3.0\"). is_desktop_mode defaults False (prefs.py:12) and there is NO settings UI toggle for it (settings_dialog.py never lists is_desktop_mode).
```

**Fix:** Either drop the desktop/mobile UA concept for the desktop build, or implement a real per-tab UA override via settings.set_user_agent(full_ua_string) with a menu toggle and reload.

#### 14. [MEDIUM] No fullscreen / Picture-in-Picture video support (fullscreen request not handled)
_Fullscreen / PiP · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-390)`  

HTML5 fullscreen video (the YouTube/Netflix fullscreen button) will not put the player into real fullscreen because the embedder does not enable WebKit fullscreen nor handle enter-fullscreen by calling window.fullscreen(). There is also no Picture-in-Picture affordance. For a media-focused 'fast, secure, private' browser these are expected features and are absent.

```
_create_webview never sets settings.set_enable_fullscreen(True) and never connects 'enter-fullscreen'/'leave-fullscreen' on the WebView; the window itself only exposes _toggle_maximize (206-212), not fullscreen(). grep for fullscreen/PiP across src returns nothing.
```

**Fix:** Enable settings.set_enable_fullscreen(True); connect 'enter-fullscreen'/'leave-fullscreen' to call self.fullscreen()/self.unfullscreen() and hide/show chrome; optionally add a PiP/document-PiP control.

#### 15. [MEDIUM] HTTPS-upgrade in decide-policy hijacks subframe navigations into the main frame and permanently blocks http-only sites
_Logic error / navigation · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 423-438)`  

When is_https_upgrade is on (default True), every NAVIGATION_ACTION whose URI starts with http:// is ignored and `webview.load_uri()` is called with the https variant. Two problems: (1) NAVIGATION_ACTION fires for subframes/iframes too, but `webview.load_uri()` always loads in the MAIN frame, so an http iframe or subframe navigation tears down and replaces the entire top-level page. (2) For sites that are genuinely http-only (no TLS endpoint), the upgraded https request fails and the user can NEVER reach the site, because every retry is re-upgraded — there is no fallback to http and no per-host exception. This is far more aggressive than browser HTTPS-Only mode (which falls back / shows an interstitial).

```
if self.prefs.is_https_upgrade and uri and uri.startswith("http://"):
    decision.ignore()
    webview.load_uri(uri.replace("http://", "https://", 1))
    return True
```

**Fix:** Only upgrade top-level/main-frame navigations (check the navigation is for the main frame), and implement a fallback: if the https load fails with a TLS/connection error, fall back to the original http URL (with a warning) or remember a per-host exception. Do not blindly re-upgrade subresource/subframe navigations via main-frame load_uri.
**Verifier note:** Gate the upgrade on the main frame and add a fallback path. (1) In _on_decide_policy, only upgrade when the navigation targets the top-level/main frame — inspect the WebKitNavigationAction (e.g. nav.get_frame_name() being None/empty for the main frame, or the equivalent main-frame check) and leave subframe/subresource navigations untouched so an http iframe never triggers a main-frame load_uri(). (2) Connect a load-failed handler to the webview; when an upgraded https navigation fails with a TLS/connection error, fall back to the original http URL (ideally behind a one-click interstitial/warning) and record a per-host exception so subsequent navigations to that host are not re-upgraded into an infinite upgrade loop. (3) Track which URLs were auto-upgraded (e.g. a set of origins) so the fallback can distinguish a genuine https failure from an unrelated load error and avoid re-upgrading on retry.

#### 16. [MEDIUM] JavaScript dialogs (alert/confirm/prompt) and beforeunload are not handled
_Missing feature / robustness · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-390 (_create_webview))`  

settings.set_allow_modal_dialogs(True) is enabled (line 363) but no `script-dialog` signal handler is connected. With WebKitGTK, if the application does not handle script-dialog, alert()/confirm()/prompt() are suppressed and confirm()/beforeunload return their default (false), so pages that gate actions behind confirm() or warn on unload behave incorrectly and the user never sees the prompt. A production browser must surface these dialogs.

```
settings.set_allow_modal_dialogs(True)  # enabled, but...
# no webview.connect("script-dialog", ...) anywhere in _create_webview
```

**Fix:** Connect the 'script-dialog' signal and present a GTK MessageDialog/entry for ALERT, CONFIRM, PROMPT and BEFORE_UNLOAD_CONFIRM, calling dialog.confirm_set_confirmed()/text_input_set_text() appropriately and returning True.

#### 17. [MEDIUM] default_zoom preference is never applied
_Logic error / dead pref · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/prefs.py  (lines 20 (default_zoom); browser_window.py 353-390)`  

Prefs defines default_zoom = 100 and the settings infrastructure persists it, but no code ever calls webview.set_zoom_level(...). The zoom preference has no effect, and there is no zoom-in/zoom-out shortcut either (Ctrl+/- absent from _setup_shortcuts). Users who set a zoom level see nothing change.

```
_DEFAULTS = { ... "default_zoom": 100, ... }  # never read; grep for set_zoom_level / zoom_level returns no matches in src/
```

**Fix:** Apply webview.set_zoom_level(self.prefs.default_zoom / 100.0) in _create_webview, and add Ctrl+Plus / Ctrl+Minus / Ctrl+0 shortcuts that adjust and persist zoom.

#### 18. [MEDIUM] History and Bookmarks menu items are non-functional stubs
_Missing feature · Correctness & Crashes · effort L_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 519-523)`  

The application menu exposes 'History' and 'Bookmarks' entries (lines 219-220) wired to _show_history and _show_bookmarks, but both methods are empty `pass` stubs. Visits are recorded to the DB and bookmarks can be toggled, but there is no way for the user to view, search, open, or manage history or bookmarks. For a shipping browser these are required surfaces, and exposing dead menu items is a visible defect.

```
def _show_history(self):
    pass

def _show_bookmarks(self):
    pass
```

**Fix:** Implement history and bookmarks viewer dialogs (or helix:// internal pages) backed by Database.get_history/get_bookmarks/search_*; allow opening an entry into a tab and deleting items.

#### 19. [MEDIUM] Tab close buttons capture a positional index while title buttons capture the tab object — index can go stale if the tab list changes between renders
_Logic error / state · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 323-334, 341-349)`  

Within _update_tab_bar the title button binds the tab object (t=tab_ref, robust), but the close button binds the integer position (idx=tab_idx=i). _close_tab_by_index then closes self.tab_manager.tabs[idx]. This only stays correct because the bar is rebuilt after every mutation; however the two callbacks reference inconsistent identities, and because a background tab's notify::title fires _update_tab_bar at arbitrary times (see the active_tab handler bug above), it is possible for the displayed bar and the underlying list to disagree momentarily, so a click can close the wrong tab. The pattern is fragile and bug #1 makes the unintended rebuilds frequent.

```
title_btn.connect("clicked", lambda b, t=tab_ref: self._switch_to_tab(t))   # binds object
...
close_btn.connect("clicked", lambda b, idx=tab_idx: self._close_tab_by_index(idx))  # binds index
```

**Fix:** Bind the tab object (or its stable .id) to the close button as well, and add a close_tab_by_id path that looks up the current index, so closing never depends on a possibly-stale positional index.

#### 20. [MEDIUM] No download handling at all; downloads cannot be saved, scanned, or path-validated
_Downloads / Safe Browsing · Security & Privacy · effort L_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-390)`  

The WebView created in _create_webview connects only to load-changed, notify::title, notify::uri, notify::estimated-load-progress and decide-policy. There is no handler for WebKit2's 'download-started' signal (on WebContext) nor for DECIDE_POLICY of type RESPONSE to convert non-displayable MIME responses into downloads. The Database has a full 'downloads' table and add_download/update_download_status methods, but nothing ever calls them. Consequently a production browser feature (downloading files) is entirely absent: there is no chosen download directory, no filename sanitization, no path-traversal protection, no overwrite confirmation, and no MIME/extension validation. Clicking any download link will either do nothing or let WebKit drop the file to an uncontrolled default location with an attacker-supplied filename.

```
webview.connect("load-changed", self._on_load_changed)
        webview.connect("notify::title", self._on_title_changed)
        webview.connect("notify::uri", self._on_uri_changed)
        webview.connect("notify::estimated-load-progress", self._on_progress)
        webview.connect("decide-policy", self._on_decide_policy)   # no download-started, no RESPONSE handling
```

**Fix:** Connect to WebContext 'download-started' (and decide-policy RESPONSE) signals. Prompt the user (or use a configured directory), sanitize the suggested filename (strip path separators and '..'), confirm overwrites, and record entries via Database.add_download/update_download_status. Validate the destination stays within the chosen directory.
**Verifier note:** Treat this as a missing-feature / future-hardening item rather than a live exploit. If/when implementing downloads: (1) connect to WebContext 'download-started' and add a PolicyDecisionType.RESPONSE branch in _on_decide_policy that calls decision.download() when WebKit2.ResponseDecision cannot be displayed; (2) on each WebKitDownload connect 'decide-destination' and set the destination explicitly to a configured/prompted directory -- never leave it unset and never trust the suggested filename; (3) sanitize the suggested filename by taking only the basename, stripping path separators and '..', and rejecting absolute paths; (4) verify os.path.realpath(destination) stays within the chosen directory (defense against traversal/symlink), and confirm before overwriting an existing file; (5) record entries via Database.add_download and update via update_download_status on finished/failed, and surface get_downloads in the UI. Note that the current default behavior is to cancel downloads, not silently write files, so there is no immediate path-traversal exposure to remediate today -- the priority is correctness of the destination-handling code at the time the feature is added.

#### 21. [MEDIUM] Camera/mic/geolocation/notification permission requests are never handled (no prompt, no per-site scoping)
_Permissions · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 360-377)`  

set_enable_media_stream(True) and set_enable_mediasource(True) are enabled but the WebView never connects the 'permission-request' signal. With no handler, WebKitGTK auto-denies every WebKitPermissionRequest, so getUserMedia (camera/mic), Geolocation, and Notification requests silently fail with no user prompt and no way to grant access. There is no permission UI, no per-site storage, and no persistence of decisions anywhere in the codebase (grep for permission-request / permission_request returns nothing). A production browser must let users grant/deny these per-site. This both breaks legitimate sites (media silently fails despite media_stream being enabled) and means there is no consent surface for privacy-sensitive APIs.

```
settings.set_enable_media_stream(True)
        settings.set_enable_mediasource(True)
        ...
        webview.connect("decide-policy", self._on_decide_policy)   # no webview.connect("permission-request", ...) anywhere
```

**Fix:** Connect 'permission-request' on each WebView, present a per-request allow/deny prompt scoped to the requesting origin, and persist decisions per-site (e.g. in the SQLite DB). Default to deny for camera/mic/geolocation until the user grants.
**Verifier note:** Connect WebKitWebView::permission-request on each WebView created in _create_webview() (around line 377, alongside the existing decide-policy connection). In the handler, branch on the request type (WebKit2.UserMediaPermissionRequest, GeolocationPermissionRequest, NotificationPermissionRequest, etc.), present a non-blocking allow/deny prompt (e.g., a GtkInfoBar/popover) scoped to the requesting origin (derive origin from webview.get_uri()), return True, and call request.allow()/request.deny() based on the user's choice. Persist decisions per-origin in the existing SQLite layer (database.py already has a connection) with a new table such as site_permissions(origin TEXT, permission TEXT, decision INTEGER), and consult it before prompting so remembered grants/denials skip the prompt. Default to deny for camera/mic/geolocation when no stored decision exists. This both restores legitimate media functionality and adds the missing per-site consent surface. Lower priority: the same fix should be mirrored in the build/ packaged copy or, better, the build should be regenerated from src so the duplicate does not drift.

#### 22. [MEDIUM] "Clear Data" only clears the app SQLite DB; cookies, cache, localStorage, IndexedDB are never cleared
_Clear browsing data / Privacy leak · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/settings_dialog.py  (lines 106-118, 151-168)`  

The 'Clear Data' section only offers Clear History / Clear Bookmarks / Clear Downloads, each of which only deletes rows from the helix.db SQLite tables. There is no option to clear cookies, HTTP cache, localStorage/sessionStorage, IndexedDB, service workers, or the favicon DB. WebKitGTK exposes WebsiteDataManager.clear() / remove() for exactly this, but it is never used (grep WebsiteDataManager / clear_data returns nothing). For a browser marketed as 'Fast. Secure. Private.', the inability to clear cookies and site storage is a real privacy gap — a user clearing data leaves all tracking cookies and persistent site storage intact on disk.

```
("Clear History", ...), ("Clear Bookmarks", ...), ("Clear Downloads", ...)   # _confirm_clear only calls db.clear_history/clear_bookmarks/clear_downloads; no WebsiteDataManager usage anywhere
```

**Fix:** Add a 'Clear Cookies & Site Data' action that calls WebsiteDataManager.clear() for the relevant WebKit2.WebsiteDataTypes (COOKIES, MEMORY_CACHE, DISK_CACHE, LOCAL_STORAGE, INDEXEDDB_DATABASES, SERVICE_WORKER_REGISTRATIONS, etc.) on the default WebContext, ideally with a time-range selector.
**Verifier note:** Add a "Clear Cookies & Site Data" action to the Clear Data section in settings_dialog.py and wire a new _confirm_clear branch to WebKit's data manager. For WebKit2GTK 4.0, call webkit_web_context.get_website_data_manager() on the default WebContext (the one used by non-incognito WebViews) and invoke its clear(types, timespan, cancellable, callback) with a WebKit2.WebsiteDataTypes mask covering COOKIES | MEMORY_CACHE | DISK_CACHE | LOCAL_STORAGE | INDEXEDDB_DATABASES | SERVICE_WORKER_REGISTRATIONS | OFFLINE_APPLICATION_CACHE | SESSION_STORAGE (and WEBSQL_DATABASES if present), or simply WebsiteDataTypes.ALL. Since the dialog has no direct WebView reference, expose the shared default WebContext (e.g. via WebKit2.WebContext.get_default() or a reference passed from BrowserWindow) so the dialog can reach the manager. Consider a time-range selector (last hour / day / week / all time) mapped to the timespan argument, and clear() is async so surface success/failure to the user. Optionally also clear the favicon database via WebKit2.FaviconDatabase.clear(). Given the "Private" positioning, also consider making history-clear simultaneously clear site data so the two stay consistent.

#### 23. [MEDIUM] TLS certificate errors produce no error page or user explanation (no load-failed-with-tls-errors handler)
_TLS/SSL · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 373-377)`  

There is no handler for the 'load-failed-with-tls-errors' signal and no call to set_tls_errors_policy. The default WebKitGTK policy is FAIL, so the browser does NOT silently proceed past invalid certificates (good) — but because nothing handles the failure, an invalid-certificate navigation results in a blank/aborted load with zero explanation to the user. There is also no 'load-failed' handler at all, so all network/TLS errors render as a blank page. A production browser needs a security interstitial that explains the certificate problem (and only allows an explicit, clearly-warned override).

```
webview.connect("decide-policy", self._on_decide_policy)   # no "load-failed-with-tls-errors" / "load-failed" connected; grep set_tls_errors_policy returns nothing
```

**Fix:** Connect 'load-failed-with-tls-errors' and 'load-failed'. Render a clear certificate-error interstitial showing the host, the GTlsCertificateFlags, and require an explicit, unmistakable user action to proceed (and never persist the exception globally). Add a 'load-failed' fallback page for other errors.

#### 24. [MEDIUM] Non-incognito WebViews use the default global WebContext with no explicit persistent-storage location or isolation control
_Cookie & storage isolation · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 353-358)`  

Incognito tabs correctly use WebContext.new_ephemeral(), but every incognito tab gets its OWN separate ephemeral context (new_ephemeral per webview), so two incognito tabs do not share a session/cookies — opening a second incognito tab to the same site logs you out, which is incorrect incognito behaviour. Conversely, all normal tabs use the bare WebKit2.WebView() default context with no explicit WebsiteDataManager / data directory configured, so persistence location and accept-policy are entirely WebKit defaults and uncontrolled by the app. The app should own a single shared persistent WebContext for normal tabs and a single shared ephemeral context for incognito tabs.

```
if incognito:
            ctx = WebKit2.WebContext.new_ephemeral()
            webview = WebKit2.WebView.new_with_context(ctx)   # new ephemeral PER tab
        else:
            webview = WebKit2.WebView()   # default global context, no explicit data manager
```

**Fix:** Create one shared persistent WebContext (with an explicit WebsiteDataManager base path) for normal tabs and one shared ephemeral WebContext for all incognito tabs, and reuse them across tabs so cookies/session are shared correctly within each mode.

#### 25. [MEDIUM] Forced HTTPS upgrade rewrites every http:// navigation with no HTTPS-failure fallback, breaking HTTP-only sites
_Mixed content / HTTPS · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 429-432)`  

When is_https_upgrade is on (default), decide-policy intercepts any http:// navigation and reloads it as https:// with a blind string replace and return True. There is no fallback: if the destination has no HTTPS endpoint (or presents a cert error), the load simply fails (and, per the TLS finding above, fails with no error page). Real HTTPS-First implementations attempt HTTPS and silently fall back to HTTP on failure. As written, HTTP-only sites become permanently unreachable while the toggle is on. The replace also only handles top-level navigations via decide-policy plus a separate DOM-rewriting script (HTTPS_UPGRADE_JS), which does not cover subresources/redirects loaded by the page engine.

```
if self.prefs.is_https_upgrade and uri and uri.startswith("http://"):
                decision.ignore()
                webview.load_uri(uri.replace("http://", "https://", 1))
                return True
```

**Fix:** Implement HTTPS-First semantics: attempt the HTTPS upgrade, and on connection/TLS failure fall back to the original http:// URL (optionally with a warning), rather than hard-failing. Track which hosts have been upgraded to avoid loops.

#### 26. [MEDIUM] No 'create' (popup/new-window) signal handler — JS window.open and target=_blank are not governed by the popup blocker
_Intent / popup handling · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 373-377)`  

The WebView never connects the WebKit2 'create' signal, which fires for window.open() / target=_blank / JS-initiated new windows. Without it, such navigations are not routed into a new tab and are not subject to any native popup/redirect policy. The only popup defense is POPUP_BLOCKER_JS injected into the page (privacy_manager.py:236), which runs in the page's own JS context and can be trivially defeated by a page that captures references to window.open before the user script runs or uses other navigation primitives. There is no native enforcement layer.

```
webview.connect("decide-policy", self._on_decide_policy)   # no webview.connect("create", ...); popup blocking is only the injected POPUP_BLOCKER_JS
```

**Fix:** Connect the 'create' signal, decide whether to open a real new tab (returning a new WebView) or block it based on user activation and the popup-domain list, enforcing the policy in native code rather than relying solely on injected JS.

#### 27. [MEDIUM] file:// and arbitrary URI schemes are loadable with no scheme allow-listing or file-access hardening
_Scheme / file access exposure · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/url_utils.py  (lines 19)`  

format_url explicitly passes through file:// and about: URLs, and main.py on_open loads any file URI passed on the command line (win.load_url(files[0].get_uri())). The WebView settings never configure file-access policy (no set_allow_file_access_from_file_urls / set_allow_universal_access_from_file_urls; grep returns nothing), so behaviour relies entirely on WebKit defaults, and the app does not restrict navigation to file:// from web content via decide-policy. While decide-policy does run for navigations, it only checks https-upgrade and ad-block; it never blocks a web page from navigating the top frame to a file:// URL. Combined with no scheme allow-list, a page/link can attempt to drive the browser to local files.

```
if trimmed.startswith(("http://", "https://", "file://", "about:")):
            return trimmed   # file:// passed through; no allow_universal/file_access_from_file_urls settings anywhere; decide-policy never blocks file://
```

**Fix:** Explicitly set allow_file_access_from_file_urls and allow_universal_access_from_file_urls to False, and in decide-policy block transitions to file:// (and other privileged schemes) that originate from non-file web content. Allow-list the schemes the browser is willing to load.

#### 28. [LOW] Reader mode, Print / Save-as-PDF, and Share are entirely missing
_Reader / Print / Share · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 214-239 (_build_menu))`  

Three common browser features are absent with no stubs: (1) Reader mode (simplified article view); (2) Print / Save as PDF — WebKit2.PrintOperation / print_to_pdf is never invoked and there is no Ctrl+P; (3) Share. These are completeness gaps relative to a production browser, though lower priority than the broken core features above.

```
The menu offers only New Tab, New Incognito Tab, History, Bookmarks, Find on Page, Settings. grep across src for print/pdf/reader/share returns no functional code (only ad-domain strings). WebKit2.PrintOperation is never used.
```

**Fix:** Add Print (WebKit2.PrintOperation with Save-as-PDF via the GTK print dialog, plus Ctrl+P), and consider Reader mode (inject a Readability-style script) and a Share action.

#### 29. [LOW] No permission-request handling (camera/mic/geolocation/notifications)
_Navigation / permissions · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 363-364, 373-377)`  

Media stream is enabled yet the 'permission-request' signal is never handled, so getUserMedia (camera/mic), geolocation, and notification permission prompts will be auto-denied by WebKit (the default when unhandled) with no user prompt. Sites needing camera/mic/location silently fail. A production browser must prompt and allow/deny per origin.

```
settings.set_enable_media_stream(True) is set, but there is no webview.connect('permission-request', ...). grep for 'permission-request' across src returns nothing.
```

**Fix:** Connect 'permission-request' and present allow/deny UI for WebKit2.UserMediaPermissionRequest, GeolocationPermissionRequest, NotificationPermissionRequest, persisting per-origin choices.

#### 30. [LOW] Database read methods bypass the lock used by writers on a shared check_same_thread=False connection
_Concurrency / robustness · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/database.py  (lines 25 (connect), 84-97 / 109-116 / 137-159 / 186-191 (unlocked reads))`  

The connection is opened with check_same_thread=False and writers serialize with self._lock, but get_history, search_history, is_bookmarked, get_bookmarks, search_bookmarks, _trim_history's COUNT and get_downloads execute without the lock. In the current code all DB access happens on the GTK main thread so this is latent, but the explicit check_same_thread=False and threading.Lock signal an intent to be thread-safe; if any DB call is ever moved off the main thread (e.g. download progress on a worker), concurrent use of the single connection/cursor can raise sqlite3.ProgrammingError ('Recursive use of cursors not allowed') or 'database is locked'.

```
self._conn = sqlite3.connect(self._db_path, check_same_thread=False)
...
def get_history(self, limit=500):
    rows = self._conn.execute(...)  # no `with self._lock:`
```

**Fix:** Either guard all reads with self._lock too (sqlite reads are cheap), or drop check_same_thread=False and keep DB strictly on the main thread, or move to a per-thread/connection-pool model.

#### 31. [LOW] Dead TabManager callback system and a settings toggle that does nothing
_Dead code / missing wiring · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/tab_manager.py  (lines 49-54, 216-223; settings_dialog.py 124)`  

TabManager exposes set_callbacks/_on_tab_changed/_on_tabs_updated and fires _notify_tabs_updated/_notify_tab_changed throughout, but BrowserWindow never calls set_callbacks (verified: zero call sites), so the entire notification pathway is dead and the UI relies on manual _update_tab_bar calls — easy to forget and a source of drift. Separately, settings_dialog builds a 'Suspend inactive tabs' switch bound to key 'is_suspend_inactive', which is not in Prefs._DEFAULTS and is not read anywhere; toggling it persists an orphan key and suspend_inactive_tabs() in TabManager is never invoked, so the feature is inert.

```
grep 'set_callbacks' src/*.py -> only the definition in tab_manager.py (no caller).
settings_dialog.py: section.pack_start(self._make_switch_row("Suspend inactive tabs", "is_suspend_inactive"), ...)  # key absent from Prefs._DEFAULTS; suspend_inactive_tabs() never called
```

**Fix:** Either wire BrowserWindow to set_callbacks and remove the manual _update_tab_bar duplication, or delete the unused callback API. Remove the 'Suspend inactive tabs' toggle or actually schedule TabManager.suspend_inactive_tabs() (and add is_suspend_inactive to _DEFAULTS) and apply suspension to background webviews.

#### 32. [LOW] JavaScript developer extras (Web Inspector) enabled unconditionally in production
_JavaScript injection safety · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/linux/src/browser_window.py  (lines 362)`  

set_enable_developer_extras(True) is called for every WebView with no debug gate. This ships the Web Inspector enabled in release builds. While not a remote exploit, it broadens the attack/abuse surface (any context-menu Inspect, remote inspector exposure) and is typically gated behind a developer/debug preference in production browsers.

```
settings.set_enable_developer_extras(True)
```

**Fix:** Gate set_enable_developer_extras behind an opt-in developer/debug preference (default False) rather than enabling it for all users.


## Windows (C#/WinUI) — 37 findings

#### 1. [HIGH] Downloads feature completely missing — no DownloadStarting handler
_Missing core feature · Browsing Feature Completeness · effort L_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 63-139)`  

There is a `downloads` SQLite table (DatabaseManager.cs:38-41) but no code subscribes to CoreWebView2.DownloadStarting, and DatabaseManager has no insert/update/query methods for downloads. There is no downloads UI, no progress/pause/resume/cancel, no open-file, and no completion notification. Clicking a download link in WebView2 will fall back to the default (silent) behavior with no app-level tracking or UI. Downloads are a mandatory browser feature and are entirely absent.

```
grep for DownloadStarting yields nothing. DatabaseManager only defines the table (DatabaseManager.cs:38-41) with no Add/Get/Update download methods. InitializeWebView (MainWindow.xaml.cs:63-139) wires NavigationStarting/Completed/NewWindowRequested/WebResourceRequested but never DownloadStarting.
```

**Fix:** Subscribe to CoreWebView2.DownloadStarting, track DownloadOperation state/progress, persist to the downloads table, and build a downloads panel with pause/resume/cancel/open and a completion notification.
**Verifier note:** Implement downloads end to end: (1) In InitializeWebView, subscribe to webView.CoreWebView2.DownloadStarting; capture the DownloadOperation, set ResultFilePath if a custom save dir is desired, and subscribe to its StateChanged/BytesReceivedChanged/EstimatedEndTimeChanged events to track progress and final state. (2) Add download persistence methods to DatabaseManager (AddDownload, UpdateDownloadProgress/Status, GetDownloads) that write to the existing downloads table (filename, url, localPath, progress, status, timestamp). (3) Build a downloads UI panel/flyout with per-item progress and pause/resume/cancel/open-file actions (DownloadOperation supports Pause/Resume/Cancel and exposes ResultFilePath), plus a completion notification, and add a Downloads entry to the menu (Menu_Click) and ideally a Ctrl+J shortcut. Also correct the finding's file-path reference: DatabaseManager.cs lives at windows/HelixBrowser/Data/DatabaseManager.cs.

#### 2. [HIGH] Incognito tabs are not isolated — share cookies/storage with normal tabs (privacy leak)
_Privacy / correctness · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 44-66)`  

CreateNewTab sets tab.IsIncognito which only paints an icon in the tab bar (UpdateTabBar line 194-197). The WebView2 for an incognito tab is created with the default CoreWebView2Environment/profile — no separate UserDataFolder and no InPrivate profile. As a result, incognito tabs share cookies, localStorage, cache, and history-cookies with normal browsing. This defeats the purpose of incognito and is a privacy leak: a user who opens an 'incognito' tab is still tracked/persisted identically to normal tabs.

```
MainWindow.xaml.cs:53  var webView = new Microsoft.UI.Xaml.Controls.WebView2();  // default env/profile regardless of tab.IsIncognito
No CoreWebView2Environment.CreateAsync / UserDataFolder / IsInPrivateModeEnabled usage anywhere (grep returned none). IsIncognito is consumed only for the tab-bar icon at MainWindow.xaml.cs:194-197.
```

**Fix:** For incognito tabs, create the WebView2 against a separate in-private CoreWebView2 profile (CoreWebView2Environment with a distinct/ephemeral UserDataFolder, or a profile with IsInPrivateModeEnabled), and clear it on close. Do not save session/history for incognito (session save already filters them, but the runtime isolation is missing).
**Verifier note:** Create incognito WebView2 instances against an isolated, ephemeral profile rather than the default. With the WebView2 SDK already referenced (1.0.2651.64), the cleanest approach is to set CoreWebView2ControllerOptions.IsInPrivateModeEnabled = true for incognito tabs: build a CoreWebView2Environment via CoreWebView2Environment.CreateWithOptionsAsync, create CoreWebView2ControllerOptions with IsInPrivateModeEnabled=true (optionally a distinct ProfileName), and initialize the WebView2 with EnsureCoreWebView2Async(environment) plus the controller options so storage is in-memory/cleared on disposal. Alternatively, point incognito tabs at a separate, ephemeral UserDataFolder (e.g. a temp directory) and delete it on tab/window close. Thread the tab.IsIncognito flag from CreateNewTab into InitializeWebView (it currently is not passed/used there at all). Also ensure NewWindowRequested (line 101-105) propagates the incognito flag so popups spawned from an incognito tab stay incognito — currently CreateNewTab(e.Uri) always creates a normal tab. The existing SaveSession filter (line 384) should be kept, but it is not a substitute for runtime isolation.

#### 3. [HIGH] Closed/switched-away tabs leak their entire WebView2 browser process (never Close/Dispose'd)
_Memory leak / resource leak · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 154-167, 141-152)`  

Each Microsoft.UI.Xaml.Controls.WebView2 owns a CoreWebView2 backed by a dedicated msedgewebview2.exe browser process. Closing a tab merely removes the element from the visual tree (Children.Clear) and sets tab.WebView = null, but never calls webView.Close()/Dispose() (grep for .Close()/.Dispose() across the whole project returns zero hits). The orphaned WebView2 keeps its renderer/browser subprocess and all its memory alive for the entire app session. Opening and closing many tabs (normal browsing) monotonically grows memory and process count until the machine is exhausted. SwitchToTab also never re-attaches the background tab's WebView, so background tabs are detached from the tree but still running and consuming RAM/CPU.

```
private void CloseTab(BrowserTab tab)
{
    if (_tabs.Count <= 1) return;
    if (tab.IsPinned) return;
    var index = _tabs.IndexOf(tab);
    _tabs.Remove(tab);
    tab.WebView = null;            // <-- only nulls the reference; never Close()/Dispose()
    ...
}
// and in SwitchToTab:
WebViewContainer.Children.Clear();   // <-- removes the previous WebView2 from the tree but keeps it alive
```

**Fix:** In CloseTab, before nulling, detach event handlers and call tab.WebView?.Close() (WinUI WebView2 exposes Close()) to terminate the browser process; then remove from container. Keep a reference per-tab and only attach the active tab's WebView to WebViewContainer (don't Clear() and discard). Also Close() all WebViews in OnWindowClosed.
**Verifier note:** In CloseTab, before setting tab.WebView = null, deterministically tear down the WebView2: detach the NavigationStarting/NavigationCompleted/NewWindowRequested/WebResourceRequested handlers (refactor the lambdas at lines 82-117 into named methods or store handler delegates so they can be unsubscribed), remove the element from WebViewContainer.Children if present, then call tab.WebView?.Close() to terminate the underlying CoreWebView2 browser process. Detaching handlers matters because the closures currently root the object graph and would otherwise block GC even after Close().

In OnWindowClosed (lines 373-377), iterate all _tabs and call WebView?.Close() so all remaining browser processes are terminated on app shutdown.

Optional architectural improvement for SwitchToTab: keep each tab's WebView instance alive but toggle Visibility (or use a hosting panel per tab) instead of Children.Clear()/re-add, so switching tabs does not churn the visual tree; this is a refinement, not the core fix.

The core fix is calling Close() in CloseTab and OnWindowClosed plus unsubscribing event handlers.

#### 4. [HIGH] CreateWebResourceResponse called with null content stream to block ad requests — throws/crashes navigation
_Unhandled exception / crash · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 111-117)`  

CoreWebView2Environment.CreateWebResourceResponse expects a content IStream (an empty MemoryStream is the correct way to return no body) and a Headers string; passing a null stream is unsupported and can throw at runtime, and passing "" as the headers argument is malformed (headers must be CRLF-terminated or empty per spec, but the stream is the real problem). The handler runs synchronously on a WebView2 callback for every matched request (and is also wrapped in no try/catch); a throw here surfaces as an unhandled exception in the WebView2 event pump, which can tear down navigation or crash the app. Because the '*' filter matches every request, any ad/tracker URL on any page triggers this path.

```
webView.CoreWebView2.WebResourceRequested += (s, e) =>
{
    if (_adBlock.ShouldBlock(e.Request.Uri))
    {
        e.Response = webView.CoreWebView2.Environment.CreateWebResourceResponse(null, 403, "Blocked", "");
    }
};
```

**Fix:** Use `Environment.CreateWebResourceResponse(new MemoryStream() as IStream-equivalent (use the WinRT InMemoryRandomAccessStream/empty stream), 403, "Blocked", "")`; wrap the handler body in try/catch. Validate against the WebView2 sample for the correct empty-response pattern.
**Verifier note:** Pass an empty (non-null) content stream instead of null, since the C# wrapper rejects null. Use `new MemoryStream()` (System.IO.Stream is accepted directly by the .NET CreateWebResourceResponse overload; no WinRT IStream conversion is needed): `e.Response = webView.CoreWebView2.Environment.CreateWebResourceResponse(new MemoryStream(), 403, "Blocked", "");`. The empty "" headers argument is fine and can stay. Additionally wrap the handler body in try/catch so any future failure in the blocking path cannot crash the app, and consider adding a global Application unhandled-exception handler since the project currently has none. Validate the empty-response pattern against the official WebView2 .NET WebResourceRequested sample.

#### 5. [HIGH] Incognito / private mode is fake — incognito tabs share the same WebView2 profile and persist cookies, cache, history and storage
_Privacy / Incognito correctness · Security & Privacy · effort L_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 44-61, 63-139, 313)`  

CreateNewTab(isIncognito: true) sets only a boolean flag and renders a pink icon. Every tab's WebView2 is created with `new Microsoft.UI.Xaml.Controls.WebView2()` and `EnsureCoreWebView2Async()` using the application's single default CoreWebView2Environment/profile and default UserDataFolder. There is no separate CoreWebView2Environment, no CoreWebView2ControllerOptions with IsInPrivateModeEnabled = true, and no distinct profile name. Consequently an 'incognito' tab writes cookies, HTTP cache, localStorage, IndexedDB and service-worker data to the exact same on-disk store as normal tabs, and reads existing logged-in cookies. The ONLY behavioral difference is that SaveSession() filters out incognito tabs (line 384). This is a guaranteed, demonstrable privacy hole: a user opening a private tab is fully trackable and leaves persistent traces.

```
tab.IsIncognito only: `var tab = new BrowserTab { ... IsIncognito = isIncognito };` then `var webView = new Microsoft.UI.Xaml.Controls.WebView2();` ... `await webView.EnsureCoreWebView2Async();` (no environment/options passed). Menu: `((MenuFlyoutItem)menu.Items[1]).Click += (s, _) => CreateNewTab(isIncognito: true);`. There is no use of IsInPrivateModeEnabled anywhere in the project (grep returns no matches).
```

**Fix:** Create incognito tabs with a private CoreWebView2 controller: build a CoreWebView2Environment and use CoreWebView2ControllerOptions { IsInPrivateModeEnabled = true } (or a dedicated in-memory/ephemeral profile via SetVirtualHostNameToFolderMapping-free isolated UserDataFolder that is deleted on close). Ensure incognito tabs use a separate CoreWebView2Profile so cookies/cache/storage are isolated and discarded when the last incognito tab closes. Block history writes for incognito tabs too.
**Verifier note:** Isolate incognito tabs at the CoreWebView2 environment/profile level rather than via a boolean. Concretely: (1) Create incognito WebView2 instances using a dedicated environment with private mode enabled — build CoreWebView2Environment.CreateWithOptionsAsync and create the controller via CoreWebView2ControllerOptions with IsInPrivateModeEnabled = true (or, equivalently, a named ephemeral CoreWebView2Profile, e.g. environment.CreateCoreWebView2ControllerAsync with a profile whose IsInPrivateModeEnabled = true). (2) Use a separate, isolated UserDataFolder (or distinct profile name) for incognito so cookies/cache/localStorage/IndexedDB/service-worker data never touch the normal profile, and clear/delete that ephemeral store when the last incognito tab closes (e.g. profile.ClearBrowsingDataAsync or deleting the temp UserDataFolder). (3) Plumb tab.IsIncognito into InitializeWebView so it actually branches on environment/profile creation — today it does not. (4) When history recording is eventually wired up (AddHistory is currently dead code), guard it with `if (!tab.IsIncognito && _prefs.IsSaveHistoryEnabled)`. Add a smoke test: open an incognito tab, log into a site, close all incognito tabs, reopen — the session must not be authenticated, and no cookie/cache files should appear in the normal profile's UserDataFolder.

#### 6. [MEDIUM] Browsing history is never recorded — entire history feature non-functional
_Missing core feature · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 90-99)`  

DatabaseManager exposes AddHistory/GetHistory/ClearHistory and a `history` SQLite table, but NOTHING in the app ever calls AddHistory. The NavigationCompleted handler updates the tab title/url but never persists a history entry. A grep across the whole windows tree shows zero callers of AddHistory/GetHistory/ClearHistory outside their own definitions. The Prefs.IsSaveHistoryEnabled flag is likewise never read. A production browser's history (record/search/clear) is entirely dead code here.

```
NavigationCompleted handler (MainWindow.xaml.cs:90-99):
  webView.CoreWebView2.NavigationCompleted += (s, e) => {
      ProgressBar.Value = 100; ...
      tab.Title = webView.CoreWebView2.DocumentTitle;
      tab.Url = webView.CoreWebView2.Source;
      ... // no DatabaseManager.Instance.AddHistory(...) call
  };
Grep result: AddHistory/GetHistory/ClearHistory have no callers anywhere in the codebase.
```

**Fix:** Call DatabaseManager.Instance.AddHistory(tab.Title, tab.Url) inside NavigationCompleted when e.IsSuccess and the tab is not incognito and Prefs.IsSaveHistoryEnabled is true. Build a history view bound to GetHistory and wire ClearHistory.
**Verifier note:** In MainWindow.xaml.cs NavigationCompleted (line 90), after updating tab.Title/Url, record history when the navigation succeeded and recording is allowed: `if (e.IsSuccess && !tab.IsIncognito && _prefs.IsSaveHistoryEnabled && tab.Url != "helix://start" && !string.IsNullOrEmpty(tab.Url)) DatabaseManager.Instance.AddHistory(tab.Title, tab.Url);` (skip the synthetic helix://start page and empty URLs to avoid junk entries). Then wire the existing UI affordances that are already present but dead: attach a Click handler to the "Lịch sử" MenuFlyoutItem (Menu_Click, ~line 306) and fill in the empty Ctrl+H case (line 365) to open a history view bound to GetHistory(), and add a Clear action calling ClearHistory(). This reuses the already-built DB methods and prefs flag rather than adding new infrastructure.

#### 7. [MEDIUM] History menu item and Ctrl+H shortcut are no-ops (no handler)
_Stubbed feature · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 306, 365)`  

The menu adds a 'Lịch sử (Ctrl+H)' item but no Click handler is attached to it (only indices 0, 1, and 5 get handlers). The Ctrl+H keyboard case contains only the comment `/* Show history */` with no code. There is no history view/page in the project at all (Views directory is empty). Clicking the menu item or pressing Ctrl+H does nothing.

```
MainWindow.xaml.cs:306  menu.Items.Add(new MenuFlyoutItem { Text = "Lịch sử (Ctrl+H)", ... });  // no .Click wired
MainWindow.xaml.cs:365  case VirtualKey.H: /* Show history */ e.Handled = true; break;
```

**Fix:** Implement a history view (list bound to GetHistory with search + clear) and wire both the menu item Click and the Ctrl+H case to open it.
**Verifier note:** Implement history end-to-end. (1) Record history: call DatabaseManager.Instance.AddHistory(tab.Title, tab.Url) from the WebView2 NavigationCompleted handler (around MainWindow.xaml.cs:90-99), gated on _prefs.IsSaveHistoryEnabled and skipping incognito tabs and helix:// internal URLs — currently AddHistory is never called so nothing is ever stored. (2) Add a history view (a new page/flyout under the empty Views/ dir, or render via StartPageHtml-style HTML) bound to GetHistory() with search filtering and a Clear button calling ClearHistory(). (3) Wire the menu item: ((MenuFlyoutItem)menu.Items[3]).Click += (s, _) => ShowHistory(); at MainWindow.xaml.cs:315. (4) Replace the empty Ctrl+H stub at line 365 with the same ShowHistory() call. While here, also note the adjacent "Dấu trang (Ctrl+B)" menu item (index [4]) is likewise unwired and could be fixed in the same pass.

#### 8. [MEDIUM] Bookmarks management UI entirely missing — no view, edit, folders, import/export
_Missing core feature · Browsing Feature Completeness · effort L_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 289-298, 307)`  

Bookmark support is limited to a star-toggle (Bookmark_Click) that inserts/deletes a row. There is no way to view the bookmarks list, edit a bookmark, organize folders, or import/export. The 'Dấu trang (Ctrl+B)' menu item (line 307) has no Click handler. DatabaseManager.GetBookmarks() has zero callers, and the bookmarks table has no folder/parent column at all. The Bookmark button also never reflects bookmarked state (no glyph change after toggling).

```
MainWindow.xaml.cs:307  menu.Items.Add(new MenuFlyoutItem { Text = "Dấu trang (Ctrl+B)", ... }); // no handler
GetBookmarks() in DatabaseManager.cs:125 has no callers; bookmarks schema (DatabaseManager.cs:34-37) has no folder/parent column.
```

**Fix:** Add a bookmarks manager view (list/tree with folders, edit, delete, reorder) and import/export (HTML/JSON). Wire the menu item and Ctrl+B. Update the star button glyph based on IsBookmarked after navigation.
**Verifier note:** Real finding; treat as a feature-completeness item rather than a defect. Concrete fixes: (1) Add a Click handler to the "Dấu trang" menu item (MainWindow.xaml.cs:307) and bind a real Ctrl+B shortcut in SetupKeyboardShortcuts (currently only Ctrl+D toggles, line 366) — or fix the menu label to "(Ctrl+D)" to stop advertising a non-existent shortcut. (2) Build a bookmarks manager view (list, then optionally tree/folders) that actually consumes the existing-but-unused GetBookmarks() (DatabaseManager.cs:125), with edit/delete. (3) If folders are desired, add a parent/folder column to the bookmarks schema (DatabaseManager.cs:34-37). (4) Update BookmarkBtn glyph after navigation/toggle to reflect IsBookmarked (XAML uses a static &#xE734;). (5) Import/export (HTML/JSON) is a reasonable but lower-priority enhancement. Suggest tracking this together with the equally-stubbed History view (Ctrl+H stub at line 365) since they share the same gap and data layer.

#### 9. [MEDIUM] Find-in-page uses window.find() — unreliable, no match count, broken incremental search
_Broken feature · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 321-340)`  

Find-in-page is implemented via JS window.find(), which is a non-standard, deprecated API that behaves inconsistently and is unreliable inside WebView2. FindBox_TextChanged calls window.find(text) on every keystroke, which advances the selection through matches as the user types (wrong UX — typing 'foo' searches 'f', then 'fo', then 'foo' each landing on different matches). There is no match counter (e.g. '3/12'), no highlight-all, and no wrap indicator. The string escaping only escapes single quotes (text.Replace("'","\\'")) and not backslashes, so a search term containing a backslash breaks the injected script. WebView2 exposes a proper CoreWebView2.Find API (or document highlight) that should be used instead.

```
MainWindow.xaml.cs:326  await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync($"window.find('{text.Replace(\"'\", \"\\\\'\")}')");
MainWindow.xaml.cs:333  ...ExecuteScriptAsync("window.find()");
MainWindow.xaml.cs:339  ...ExecuteScriptAsync("window.find('', false, true)");
```

**Fix:** Use CoreWebView2's native find API (or a robust injected highlight/search implementation) with match count, next/prev, highlight-all, case-insensitive, and proper string escaping (JsonSerializer.Serialize the term).

#### 10. [MEDIUM] Page/text zoom not implemented despite DefaultZoom pref
_Missing feature · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/Utils/Prefs.cs  (lines 27)`  

Prefs exposes DefaultZoom (default 100) but it is never applied — WebView2.ZoomFactor is never set, and there are no Ctrl++/Ctrl+-/Ctrl+0 shortcuts or zoom UI. Users cannot zoom pages or text, and the configured default zoom has no effect.

```
Prefs.cs:27  public int DefaultZoom { get => GetInt("default_zoom", 100); ... }  — no caller sets WebView.ZoomFactor (grep for ZoomFactor/zoom returned only this pref).
```

**Fix:** Apply tab.WebView.ZoomFactor = Prefs.DefaultZoom/100.0 after EnsureCoreWebView2Async, and add Ctrl++/Ctrl+-/Ctrl+0 handlers plus a zoom control in the menu.

#### 11. [MEDIUM] Desktop-site toggle and custom User-Agent not implemented
_Missing feature · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/Utils/Prefs.cs  (lines 19)`  

Prefs.IsDesktopMode exists but is never read, and CoreWebView2.Settings.UserAgent is never set anywhere. There is no desktop-site toggle in the UI and no per-tab UA override, so the documented desktop/mobile mode does nothing.

```
Prefs.cs:19  public bool IsDesktopMode { ... }  — no consumer. grep for UserAgent/user-agent across windows tree returned no matches.
```

**Fix:** Add a desktop-site toggle that sets CoreWebView2.Settings.UserAgent (and reloads), wired to IsDesktopMode and a per-tab override.

#### 12. [MEDIUM] HTTPS-upgrade preference is a no-op; Do-Not-Track adds an empty filter with no handler
_Stubbed / broken privacy feature · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 78-88)`  

Two privacy prefs are effectively stubs. (1) IsHttpsUpgradeEnabled is never read; the comment '// HTTPS upgrade' at line 81 is immediately followed by the DNT filter, and no code rewrites http:// to https://. UrlUtils.FormatUrl even prepends https:// only for bare hosts but passes through explicit http:// URLs unchanged. (2) When IsDoNotTrackEnabled is true, the code calls AddWebResourceRequestedFilter('*', All) but never subscribes a WebResourceRequested handler to add a 'DNT: 1' header — so no DNT header is ever sent. The filter alone does nothing (and overlaps with the ad-block filter registered later).

```
MainWindow.xaml.cs:78-79  if (_prefs.IsDoNotTrackEnabled)
        webView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);  // no handler adds DNT header
MainWindow.xaml.cs:81  // HTTPS upgrade  (followed by NavigationStarting; nothing upgrades http->https). IsHttpsUpgradeEnabled has no consumer.
```

**Fix:** Either implement DNT by adding the header in a WebResourceRequested handler (and remove the orphan filter), and implement HTTPS upgrade by rewriting http:// navigations to https:// when the pref is on, or remove these prefs so they don't falsely advertise protection.

#### 13. [MEDIUM] Address bar has no search suggestions / autocomplete
_Missing feature · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml  (lines 56-58)`  

The address bar is a plain TextBox with only an Enter handler. There is no suggestion dropdown (no AutoSuggestBox), no history/bookmark-based autocomplete, and no search-engine suggestion fetching. A production address bar is expected to surface suggestions as the user types; this is entirely absent.

```
MainWindow.xaml:56-58  <TextBox ... x:Name="AddressBox" PlaceholderText="Tìm kiếm hoặc nhập địa chỉ" KeyDown="AddressBox_KeyDown" .../>  (no AutoSuggestBox / TextChanged / suggestion list). grep for AutoSuggest/SuggestionChosen/ItemsSource returned nothing.
```

**Fix:** Replace the TextBox with an AutoSuggestBox (or add a custom dropdown) populated from history, bookmarks, and search-engine suggestion APIs as the user types.

#### 14. [MEDIUM] No Stop button / no way to abort an in-progress navigation
_Missing feature · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml  (lines 44-45)`  

The toolbar has Back/Forward/Reload but no Stop control, and the reload button never morphs into a stop button while a page is loading. There is no call to CoreWebView2.Stop() anywhere. Users cannot abort a slow or hung page load — a standard browser capability.

```
MainWindow.xaml:44-45 defines ReloadBtn only; Reload_Click (MainWindow.xaml.cs:277) calls Reload(). grep for .Stop() returned no matches.
```

**Fix:** Toggle the Reload button to a Stop button between NavigationStarting and NavigationCompleted, calling CoreWebView2.Stop() to abort.

#### 15. [MEDIUM] Reader mode, fullscreen handling, PiP, file upload, share, and print/save-as-PDF are all absent
_Missing features · Browsing Feature Completeness · effort L_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 63-139)`  

Several expected browser features have no implementation anywhere: (1) Reader mode — none. (2) HTML5 fullscreen — no handling of CoreWebView2.ContainsFullScreenElementChanged, so a page entering fullscreen (e.g. video) will not expand to fill the window/hide chrome. (3) Picture-in-Picture — no handling. (4) File upload (<input type=file>) relies entirely on WebView2 default; there is no app-level handling/verification but it may work by default — however there is no test or customization. (5) Share — none. (6) Print / Save-as-PDF — no menu item and no call to CoreWebView2.PrintAsync/PrintToPdfAsync. These are common production browser features and are not present.

```
grep across the windows tree for reader/fullscreen/ContainsFullScreenElement/PrintToPdf/PrintAsync/share/PictureInPicture returned no matches. InitializeWebView (MainWindow.xaml.cs:63-139) wires no such events. The menu (MainWindow.xaml.cs:300-317) offers only New tab/Incognito/History/Bookmarks/Find/Settings.
```

**Fix:** Implement HTML5 fullscreen via ContainsFullScreenElementChanged at minimum (important for video). Add print/save-as-PDF (CoreWebView2.PrintToPdfAsync), share, and optionally reader mode and PiP.

#### 16. [MEDIUM] WebView2 instances for closed/background tabs are never disposed — resource/memory leak
_Robustness / resource leak · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 154-167)`  

Each tab owns a live WebView2 (a full browser process). On close, CloseTab only sets tab.WebView = null (line 160) without calling Close()/disposing the CoreWebView2, so the underlying browser process/resources are leaked until GC eventually finalizes (and CoreWebView2 needs explicit Close()). Additionally every open tab keeps a fully live WebView2 (SwitchToTab clears the container but the inactive WebViews stay running), and RestoreSession eagerly creates and navigates a live WebView2 for every restored tab at once — heavy memory use with many tabs.

```
MainWindow.xaml.cs:160  tab.WebView = null;  // no tab.WebView.Close() / CoreWebView2 disposal
SwitchToTab (MainWindow.xaml.cs:144-147) only swaps which WebView is in the container; others remain alive. RestoreSession (MainWindow.xaml.cs:415-421) calls CreateNewTab (which navigates immediately) for every saved tab.
```

**Fix:** Call tab.WebView.Close() (and remove from any container) before nulling on tab close. Consider suspending/lazy-loading background and restored tabs (defer navigation until first activation).

#### 17. [MEDIUM] All tabs share one set of UI event handlers, so background-tab navigation corrupts the active tab's address bar / progress / SSL / nav buttons
_Tab state corruption / wrong conditional · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 82-99)`  

These handlers are registered per-WebView but unconditionally mutate the single shared UI (ProgressBar, AddressBox, SslIcon, BackBtn/ForwardBtn). They never check whether the navigating tab == _activeTab. A background tab finishing/starting navigation (e.g. a NewWindowRequested-created tab, an ad redirect, a slow page completing after the user switched tabs) will overwrite the address bar and SSL icon for the tab the user is actually looking at, flip the progress bar, and the URL the user just typed. With JS-driven redirects or auto-refreshing background tabs this fires repeatedly and the address bar fights the user. This is a guaranteed UX/correctness defect once more than one tab exists.

```
webView.CoreWebView2.NavigationStarting += (s, e) =>
{
    ProgressBar.Visibility = Visibility.Visible;
    ProgressBar.Value = 20;
    AddressBox.Text = e.Uri;            // <-- writes the global address bar for ANY tab
    UpdateSslIcon(e.Uri);
};
webView.CoreWebView2.NavigationCompleted += (s, e) =>
{
    ProgressBar.Value = 100;
    ProgressBar.Visibility = Visibility.Collapsed;
    tab.Title = webView.CoreWebView2.DocumentTitle;
    tab.Url = webView.CoreWebView2.Source;
    AddressBox.Text = tab.Url;          // <-- overwrites whatever the user is typing / the active tab shows
    UpdateNavigationButtons();          // <-- recomputes Back/Forward from _activeTab, not this tab
    ...
};
```

**Fix:** Guard every UI mutation with `if (tab != _activeTab) return;` at the top of each handler, and on tab switch explicitly re-sync ProgressBar/AddressBox/SslIcon/nav buttons from the now-active tab.
**Verifier note:** Guard each per-WebView navigation handler so it only touches window chrome when its tab is active: at the top of the NavigationStarting and NavigationCompleted lambdas add `if (tab != _activeTab) return;` (the `tab` is already captured by closure, so no extra wiring is needed). Keep the per-tab state writes (tab.Title/tab.Url in NavigationCompleted) OUTSIDE the guard so background tabs still track their own title/URL for the tab bar and session save — i.e., update tab.Title/tab.Url unconditionally, then `if (tab != _activeTab) { UpdateTabBar(); return; }` before touching AddressBox/ProgressBar/SslIcon/UpdateNavigationButtons. Separately, fix the incomplete re-sync in SwitchToTab (lines 141-152): in addition to AddressBox and UpdateNavigationButtons it should also call UpdateSslIcon(tab.Url) and reset ProgressBar (Visibility=Collapsed, Value=0) so a stale SSL icon / progress state from the previous tab doesn't persist after switching.

#### 18. [MEDIUM] Browsing history is never recorded despite 'save history' defaulting ON (AddHistory is dead code)
_Broken core feature · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/Data/DatabaseManager.cs  (lines 49-59)`  

DatabaseManager.AddHistory (and GetHistory/ClearHistory) are fully implemented but never invoked anywhere in the codebase (verified via grep across windows/HelixBrowser). Prefs.IsSaveHistoryEnabled defaults to true and there is no other history mechanism. NavigationCompleted does not call AddHistory. Consequently history is always empty; Ctrl+H (case VirtualKey.H) is also a no-op stub (`/* Show history */`). For a production browser, history is a must-have feature and it is entirely non-functional.

```
public void AddHistory(string title, string url) { ... }  // grep shows zero call sites anywhere in the project
```

**Fix:** Call DatabaseManager.Instance.AddHistory(tab.Title, tab.Url) from the active tab's NavigationCompleted handler (guarded by IsSaveHistoryEnabled and IsIncognito==false), and wire Ctrl+H / menu 'Lịch sử' to a real history view.
**Verifier note:** In the NavigationCompleted handler (MainWindow.xaml.cs:90-99), after setting tab.Title/tab.Url, call DatabaseManager.Instance.AddHistory(tab.Title, tab.Url), guarded by _prefs.IsSaveHistoryEnabled && !tab.IsIncognito. Skip blank/about:/error pages and avoid duplicate consecutive entries. Then make the entry points real: replace the Ctrl+H stub at line 365 and wire a .Click handler for the 'Lịch sử' menu item (currently index 3 in Menu_Click gets no handler at lines 312-314) to open a history view backed by GetHistory(), with a clear-history action calling ClearHistory(). Note that simply adding a history view is insufficient unless the menu item's Click is also wired, since it is presently dead.

#### 19. [MEDIUM] Do-Not-Track registers a resource filter with no handler; combined with ad-block it double-registers the '*' filter (DNT feature is entirely non-functional)
_Broken feature / logic error · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 78-79, 108-118)`  

The DNT branch calls AddWebResourceRequestedFilter but never subscribes to WebResourceRequested and never adds a DNT (`DNT: 1`) request header, so 'Do Not Track' (defaulted ON in Prefs) does literally nothing except register a filter that intercepts every request for no reason. When ad-block is also enabled (default ON) the '*'/All filter is registered twice, causing the WebResourceRequested handler to be invoked twice per request (filters are additive), doubling the per-request blocking work for every resource on every page. Intercepting all requests via WebResourceRequested also disables HTTP/2 and the in-process net cache for matched requests in WebView2, hurting performance browser-wide.

```
if (_prefs.IsDoNotTrackEnabled)
    webView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
...
if (_prefs.IsAdBlockEnabled)
{
    webView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
    webView.CoreWebView2.WebResourceRequested += ...
}
```

**Fix:** Remove the no-op DNT filter; implement DNT by setting the `DNT` header (or rely on the anti-fingerprinting script which already sets navigator.doNotTrack). Register the '*' filter exactly once and gate handler logic inside it.

#### 20. [MEDIUM] Anti-fingerprinting / tracker-blocking scripts await AddScriptToExecuteOnDocumentCreatedAsync AFTER the navigation race; initial navigate can fire before scripts are registered
_Incorrect async ordering / race condition · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 63-139)`  

InitializeWebView is async void. The script registration is awaited and the navigation happens after, which is correct for the FIRST page. However: (1) The well-implemented YoutubeAdBlockJs, CosmeticAdBlockJs, and PopupBlockerJs constants in PrivacyScripts.cs are never registered anywhere (grep shows the .cs constants are referenced only by their own definition) — so YouTube/cosmetic/popup blocking is dead code despite being the most useful anti-ad scripts. (2) async void means any exception from EnsureCoreWebView2Async/AddScript throws on the UI thread with no catch, crashing the app if WebView2 runtime initialization fails (e.g. Evergreen runtime missing). There is no error handling around EnsureCoreWebView2Async, which legitimately fails on machines without the WebView2 runtime.

```
private async void InitializeWebView(BrowserTab tab)
{
    await webView.EnsureCoreWebView2Async();
    ...
    if (_prefs.IsBlockFingerprintingEnabled)
        await webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(PrivacyScripts.AntiFingerprintingJs);
    if (_prefs.IsBlockTrackersEnabled)
        await webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(PrivacyScripts.TrackerBlockingJs);
    // Navigate
    if (tab.Url != "helix://start") webView.CoreWebView2.Navigate(tab.Url);

```

**Fix:** Register YoutubeAdBlockJs/CosmeticAdBlockJs/PopupBlockerJs (the real ad-blocking logic) alongside the others. Wrap InitializeWebView body in try/catch and show a fallback UI when EnsureCoreWebView2Async fails instead of letting async void crash the process.

#### 21. [MEDIUM] Find-in-page builds a JS string with naive single-quote escaping — breaks/injects on backslashes, quotes, and newlines
_Injection / logic error · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 321-328)`  

The find query is interpolated into a JS string literal with only single-quotes replaced by \'. A backslash in the query (e.g. searching for `a\b`) is not escaped, so it produces invalid/garbage JS; a trailing backslash escapes the closing quote and causes a syntax error or alters following code; newlines also break the literal. ExecuteScriptAsync would silently fail or behave unexpectedly. While ExecuteScriptAsync runs in the page's JS context (limiting damage), it is still incorrect string handling and a fragile pattern. Additionally window.find() is non-standard and unsupported in Chromium/WebView2's default find behavior, so find-in-page is effectively broken.

```
await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync($"window.find('{text.Replace(\"'\", \"\\\\'\")}')");
```

**Fix:** Use JsonSerializer.Serialize(text) to produce a safe JS string literal, or pass the argument via CoreWebView2.PostWebMessageAsString and read it in injected script. Replace window.find with WebView2's native Find API (CoreWebView2.Find in newer SDKs) or a proper highlight/search implementation.

#### 22. [MEDIUM] Session restore re-navigates every tab eagerly and the WebViews are created/cleared in a tight loop, racing async CoreWebView2 init
_Lifecycle / race condition · Correctness & Crashes · effort L_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 406-431, 44-61)`  

Restoring N tabs synchronously instantiates N WebView2 controls, each kicking off an async EnsureCoreWebView2Async + Navigate (a full browser process per tab) all at once at startup — heavy memory/CPU spike and slow cold start (no lazy/deferred tab loading). Each CreateNewTab also calls SwitchToTab which does WebViewContainer.Children.Clear() then Add, so during restore the container is repeatedly cleared/re-parented while previous WebViews are still asynchronously initializing; a WebView2 element that is mid-EnsureCoreWebView2Async when removed from the tree can have its initialization aborted, and the per-tab NavigationCompleted handlers (which write the shared AddressBox/ProgressBar) all fire against whichever tab is current. ActiveIndex restore at the end can also point at a tab whose WebView is still initializing.

```
foreach (var tabData in session.Tabs)
{
    CreateNewTab(tabData.Url);          // each call: new WebView2, InitializeWebView (async void), SwitchToTab -> Children.Clear()+Add
    var tab = _tabs.Last();
    tab.Title = tabData.Title;
    tab.IsPinned = tabData.IsPinned;
}
```

**Fix:** Defer loading of non-active restored tabs (create the WebView lazily on first switch, show only the title until then). Only attach the active tab's WebView to the container; avoid Clear()/re-add churn. Restore the active tab last and only navigate it.

#### 23. [MEDIUM] No TLS/certificate-error handling and a misleading SSL padlock indicator
_TLS / Certificate handling · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 263-270, 63-139)`  

There is no CoreWebView2.ServerCertificateErrorDetected handler anywhere in the project, so the app relies entirely on WebView2 defaults for cert errors (default is to show an interstitial, which is acceptable), but the security UI actively misleads the user. UpdateSslIcon decides the 'secure' state purely from the URL string prefix: `var isSecure = url.StartsWith("https://");`. It never consults the actual connection security, certificate validity, or mixed-content state. A page reached over HTTPS with an invalid/expired/self-signed certificate (or a downgraded/mixed-content page) will still display the green padlock (). The icon is also updated in NavigationStarting from e.Uri (the requested URL) rather than the committed/final URL, so redirects from https to http would still show green until completion. For a security-critical browser, the lock indicator must reflect real connection state, not the scheme substring.

```
private void UpdateSslIcon(string url) { var isSecure = url.StartsWith("https://"); SslIcon.Glyph = isSecure ? "" : ""; ... }  — and it is called as `UpdateSslIcon(e.Uri);` from NavigationStarting. No ServerCertificateErrorDetected handler exists (grep finds none).
```

**Fix:** Derive the security indicator from real state: handle CoreWebView2.ServerCertificateErrorDetected (do NOT auto-set e.Action = AlwaysAllow; default to cancel and show a clear interstitial), and reflect actual TLS state in the padlock. Update the icon on NavigationCompleted/SourceChanged using the committed URL, and show a distinct 'not secure'/'broken' state for cert errors and mixed content. Never show a secure padlock based solely on the 'https://' prefix.
**Verifier note:** Drive the indicator from real connection state instead of the scheme substring:

1) Move the icon update off NavigationStarting/e.Uri. Update it on NavigationCompleted (and/or SourceChanged) using the committed URL webView.CoreWebView2.Source, and treat e.IsSuccess plus the WebErrorStatus (e.g. CertificateCommonNameIsIncorrect, CertificateExpired, CertificateRevoked, CertificateIsInvalid) to choose a distinct 'broken/not secure' state rather than green.

2) Add a CoreWebView2.ServerCertificateErrorDetected handler. Do NOT set e.Action = AlwaysAllow; leave/return the default (Default/Cancel) so the interstitial stands, and record that the tab hit a cert error so the lock renders a clear 'broken' state. (The current default-interstitial behavior is fine and should be preserved — the fix is the UI signal, plus avoiding any future temptation to auto-allow.)

3) Reflect mixed content / downgrades: distinguish at least three states — secure (https, valid cert, no active mixed content), not-secure (http/file/about), and warning/broken (cert error or mixed content). Consider DownloadStarting/PermissionRequested and resource-request inspection if you want active-mixed-content detection.

4) Never show the secure padlock based solely on a 'https://' prefix.

File: /home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs (UpdateSslIcon lines 263-270; call site line 87 in NavigationStarting; relocate to NavigationCompleted at line 90).

#### 24. [MEDIUM] No site-permission handling for camera/mic/location/notifications/clipboard
_Permissions · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 63-139)`  

There is no CoreWebView2.PermissionRequested handler anywhere (grep confirms). The app delegates all camera, microphone, geolocation, notification and clipboard permission decisions to WebView2's default behavior with no per-site scoping, no persistence control, and crucially no incognito-aware denial. Combined with the fake incognito implementation, a private tab granting geolocation/camera leaves the grant tied to the shared persistent profile. A production browser must mediate these high-risk permissions, scope them per-origin, and never silently persist them in private sessions.

```
No `PermissionRequested` reference exists in the project (grep -rni PermissionRequested returns nothing).
```

**Fix:** Handle CoreWebView2.PermissionRequested, present an explicit per-origin prompt, store decisions per-site (and never persist for incognito), and surface a UI to review/revoke granted permissions.
**Verifier note:** Add a CoreWebView2.PermissionRequested handler in InitializeWebView. (1) For incognito/private tabs, the higher-priority fix is real profile isolation: create the WebView2 via a CoreWebView2Environment with a separate/ephemeral UserDataFolder (or use CoreWebView2ControllerOptions.IsInPrivateModeEnabled / a distinct named profile) so grants never touch the persistent profile; until that exists, default-deny or non-persist permissions for IsIncognito tabs (set e.Handled=true, SavesInProfile=false, and State=Deny or prompt-without-persist). (2) For normal tabs, optionally replace the default WebView2 prompt with an app-level per-origin prompt and store decisions keyed by origin (reuse DatabaseManager), and add a Settings UI to review/revoke granted site permissions. Note for the reviewer: the absent handler does NOT cause silent auto-granting — WebView2 shows its own native prompt by default — so the practical exposure is persistence/incognito leakage and missing management UI, not blind access.

#### 25. [MEDIUM] DevTools permanently enabled in production builds
_Hardening / Attack surface · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 71)`  

Every WebView2 is initialized with `settings.AreDevToolsEnabled = true;` unconditionally, with no debug/release gating and no user setting. This leaves the full DevTools / CDP attack surface enabled in shipping builds, which raises the impact of any local-attacker or malicious-extension scenario and is generally not appropriate for a release browser unless explicitly opted in.

```
settings.AreDevToolsEnabled = true;
```

**Fix:** Gate DevTools behind a build configuration (#if DEBUG) or an explicit advanced user preference that defaults to off in release builds.

#### 26. [MEDIUM] Browsing history is never recorded and there is no 'clear browsing data' feature
_Privacy / Data lifecycle · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/Data/DatabaseManager.cs  (lines 49-89)`  

DatabaseManager.AddHistory and ClearHistory are defined but never called from any code path (grep shows the only references are their own definitions). The IsSaveHistoryEnabled preference (Prefs.cs:18) is defined but never read. The Ctrl+H shortcut and the History menu item are stubs (`case VirtualKey.H: /* Show history */`). More importantly for privacy, there is no 'Clear browsing data' feature at all: even ClearHistory (if it were wired) only deletes the SQLite history rows and does NOT clear WebView2 cookies, cache, localStorage, IndexedDB or service workers (no CoreWebView2.Profile.ClearBrowsingDataAsync call exists). Users cannot purge their cookies/cache/site data, which is a baseline privacy requirement for any browser.

```
`public void AddHistory(...)` and `public void ClearHistory()` are defined in DatabaseManager.cs but never invoked. `case VirtualKey.H: /* Show history */ e.Handled = true; break;` in MainWindow.xaml.cs. No call to ClearBrowsingDataAsync / Profile.ClearBrowsingData exists in the codebase.
```

**Fix:** Implement a Clear Browsing Data UI that calls CoreWebView2Profile.ClearBrowsingDataAsync (cookies, cache, DOM storage, etc.) in addition to deleting SQLite history/bookmarks rows as selected. Wire AddHistory (respecting IsSaveHistoryEnabled and skipping incognito tabs) and the history viewer.

#### 27. [MEDIUM] HTTPS-upgrade preference is a no-op; explicit http:// URLs are loaded without upgrade
_TLS / Mixed content · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 81-88)`  

The code has a comment '// HTTPS upgrade' immediately above the NavigationStarting handler, and Prefs exposes IsHttpsUpgradeEnabled (Prefs.cs:23), but nothing actually performs an HTTP-to-HTTPS upgrade. The NavigationStarting handler only updates the progress bar, address box and SSL icon. UrlUtils.FormatUrl passes any explicit `http://` URL straight through (line 20: `if (trimmed.StartsWith("http://") || trimmed.StartsWith("https://")) return trimmed;`), so typed/linked http URLs load in cleartext even when the upgrade setting is on. The preference gives users a false sense of protection.

```
`// HTTPS upgrade\n webView.CoreWebView2.NavigationStarting += (s, e) => { ProgressBar.Visibility = Visibility.Visible; ... UpdateSslIcon(e.Uri); };` — no scheme rewrite. Prefs: `public bool IsHttpsUpgradeEnabled { get => GetBool("https_upgrade", true); ... }` is never read.
```

**Fix:** In NavigationStarting, when IsHttpsUpgradeEnabled is true and e.Uri begins with http://, cancel and re-navigate to the https:// equivalent (with a fallback strategy), or implement HSTS-style upgrade. Read the preference rather than leaving it dead.

#### 28. [MEDIUM] NewWindowRequested opens any URI/scheme in a new tab without scheme or popup-policy validation
_Custom-scheme / popup handling · Security & Privacy · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 101-105)`  

The NewWindowRequested handler unconditionally opens e.Uri in a new tab: `e.Handled = true; CreateNewTab(e.Uri);`. It does not consult the IsBlockPopupsEnabled preference, does not check IsPopupAd (which AdBlockEngine implements but is never called), and does not validate the scheme. Any page can trigger window.open / target=_blank to arbitrary URIs — including non-web schemes that WebView2 may attempt to launch — and they will open as tabs without user mediation. The popup-blocker JS in PrivacyScripts.PopupBlockerJs is never injected (grep shows AddScriptToExecuteOnDocumentCreatedAsync only injects AntiFingerprinting and TrackerBlocking), so popup blocking effectively does not exist at the native layer.

```
webView.CoreWebView2.NewWindowRequested += (s, e) => { e.Handled = true; CreateNewTab(e.Uri); };  — AdBlockEngine.IsPopupAd(...) and PrivacyScripts.PopupBlockerJs are defined but never referenced.
```

**Fix:** In NewWindowRequested, honor IsBlockPopupsEnabled (require user activation), reject ad/popup domains via IsPopupAd, allow only http/https (and explicitly-supported schemes), and prompt before launching external protocol handlers.

#### 29. [LOW] NavigationCompleted ignores e.IsSuccess and progress bar is faked, not real
_Correctness / UX · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 82-99)`  

NavigationCompleted unconditionally sets title/url and updates the UI without checking e.IsSuccess, so a failed/aborted navigation still updates the address bar to the failed URL. The progress bar is cosmetic: NavigationStarting sets it to 20 and NavigationCompleted jumps it to 100 — there is no real progress tracking, so users get no meaningful loading feedback for slow pages. Also AddressBox.Text is overwritten on every NavigationStarting (line 86), which will clobber whatever the user is typing if a background navigation fires.

```
MainWindow.xaml.cs:84-86  ProgressBar.Value = 20; AddressBox.Text = e.Uri;
MainWindow.xaml.cs:90-96  NavigationCompleted: ProgressBar.Value = 100; tab.Title = DocumentTitle; tab.Url = Source; AddressBox.Text = tab.Url;  // no if (e.IsSuccess) guard, no per-tab guard against background nav updating active UI
```

**Fix:** Guard UI updates with e.IsSuccess and only update chrome for the active tab. Use an indeterminate progress bar or DOMContentLoaded/real signals; avoid overwriting the address box while the box is focused.

#### 30. [LOW] Reading history/bookmarks can throw InvalidCastException when title is NULL
_Robustness / crash · Browsing Feature Completeness · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/Data/DatabaseManager.cs  (lines 74, 137)`  

GetHistory and GetBookmarks call reader.GetString(0) on the title column. The title columns are nullable (no NOT NULL constraint), and AddBookmark/AddHistory store whatever title is passed (DocumentTitle can be empty/null on error pages, becoming DBNull). reader.GetString on a DBNull value throws InvalidCastException, which would crash whichever future view enumerates these rows.

```
DatabaseManager.cs:74  ["title"] = reader.GetString(0),  (history)
DatabaseManager.cs:137 ["title"] = reader.GetString(0),  (bookmarks)
Schema: title TEXT (nullable) at DatabaseManager.cs:32 and :35.
```

**Fix:** Use reader.IsDBNull(0) ? "" : reader.GetString(0) (and same for url), or COALESCE(title,'') in SQL, before exposing these to UI code.

#### 31. [LOW] Pull-to-refresh and home/start-page handling gaps
_UX gaps · Browsing Feature Completeness · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 278, 36)`  

Pull-to-refresh (touch gesture) is not implemented — relevant for touch/tablet Windows devices and listed as an expected feature. Separately, the Home button navigates to Prefs.Homepage (default https://www.google.com) rather than the in-app helix://start page, so the start page (with favorites grid) is shown only on first launch/new tab and is not reachable via the Home button unless the user changes the homepage pref. There is no UI to set the homepage either.

```
Home_Click (MainWindow.xaml.cs:278) => LoadUrl(_prefs.Homepage); Homepage default is https://www.google.com (Prefs.cs:16). New tabs use helix://start (MainWindow.xaml.cs:36/44). No RefreshContainer / pull-to-refresh in MainWindow.xaml.
```

**Fix:** Add a RefreshContainer (or gesture) for pull-to-refresh; consider defaulting Home to helix://start or providing a homepage setting UI so the start page is reachable.

#### 32. [LOW] Keyboard Ctrl+D handler passes a KeyRoutedEventArgs where a RoutedEventArgs is expected (Bookmark_Click(s, e)) — type mismatch will not compile / is fragile
_Logic error · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 366)`  

Inside the KeyDown lambda, `e` is a KeyRoutedEventArgs and `s` is the Content's sender, but Bookmark_Click is declared `(object sender, RoutedEventArgs e)`. KeyRoutedEventArgs derives from RoutedEventArgs so this compiles, but it conflates a key event with a button-click event and passes the keyboard sender as the click sender — the handler ignores both args today (it only reads _activeTab), so it happens to work, yet it is a latent bug: any future use of sender/e in Bookmark_Click (e.g. to locate the bookmark button for a flyout) will misbehave. Minor but worth tightening.

```
case VirtualKey.D: Bookmark_Click(s, e); e.Handled = true; break;
```

**Fix:** Call a parameterless ToggleBookmarkForActiveTab() helper from both Bookmark_Click and the Ctrl+D case instead of forwarding event args.

#### 33. [LOW] Bookmark toggle never refreshes the bookmark button state and never persists/updates title; SQLite opens a fresh connection per call on the UI thread
_Robustness / UX gap · Correctness & Crashes · effort M_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 289-298)`  

Toggling a bookmark does not update BookmarkBtn's glyph/colour, so the user gets no feedback that the page is bookmarked. Each bookmark/history DB operation opens and closes a brand-new SQLiteConnection synchronously on the UI thread (DatabaseManager.GetConnection creates a new connection every call). On a slow disk this blocks the UI; with concurrent calls (e.g. autosave) SQLite's default locking can throw 'database is locked'. There is also no bookmarks/history viewer wired up to GetBookmarks()/GetHistory(), so these remain inaccessible to the user.

```
private void Bookmark_Click(object sender, RoutedEventArgs e)
{
    if (_activeTab == null) return;
    var db = DatabaseManager.Instance;
    if (db.IsBookmarked(_activeTab.Url)) db.RemoveBookmark(_activeTab.Url);
    else db.AddBookmark(_activeTab.Title, _activeTab.Url);
}
```

**Fix:** Reflect bookmarked state on BookmarkBtn after toggling; move DB work off the UI thread (Task.Run) or use a shared connection with WAL; wire GetBookmarks/GetHistory into actual UI surfaces.

#### 34. [LOW] Pinned tabs cannot be closed at all and the last tab cannot be closed, so Ctrl+W silently does nothing in common cases
_UX gap / wrong conditional · Correctness & Crashes · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 154-157)`  

Closing the only remaining tab is a no-op (acceptable), but a pinned tab can NEVER be closed (there is no unpin action anywhere — IsPinned is only ever set true during restore, never toggled by the user). A restored pinned tab is therefore permanently un-closable. Ctrl+W on a pinned/last tab silently does nothing with no feedback. Combined with the lack of an unpin UI, a user can get stuck with a pinned tab they cannot remove.

```
private void CloseTab(BrowserTab tab)
{
    if (_tabs.Count <= 1) return;
    if (tab.IsPinned) return;
    ...
```

**Fix:** Provide an unpin/close-anyway path for pinned tabs; when closing the last tab, replace it with a fresh helix://start tab instead of refusing.

#### 35. [LOW] No download handling — no DownloadStarting interception, path-traversal/MIME safety, or Safe-Browsing checks
_Downloads / Safe Browsing · Security & Privacy · effort L_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 63-139)`  

InitializeWebView wires NavigationStarting, NavigationCompleted, NewWindowRequested and WebResourceRequested but never subscribes to CoreWebView2.DownloadStarting (grep confirms no DownloadStarting handler anywhere). The app therefore performs no validation of download target paths, no sanitization of server-suggested filenames (path-traversal via '..' / absolute paths in Content-Disposition), no MIME/extension mismatch checks, no executable/dangerous-file warnings, and no Safe Browsing reputation check. The DatabaseManager even defines a `downloads` table (id, filename, url, localPath, progress, status) that is never populated, confirming download management was intended but is entirely unimplemented. For a production browser, unguarded downloads are a real malware/RCE delivery vector.

```
No `DownloadStarting` reference exists in the codebase (grep -rni DownloadStarting returns nothing). DatabaseManager.cs defines an unused downloads table: `CREATE TABLE IF NOT EXISTS downloads ( ... filename TEXT, url TEXT, localPath TEXT, progress REAL, status TEXT, timestamp REAL );`.
```

**Fix:** Subscribe to CoreWebView2.DownloadStarting, sanitize the suggested filename (strip path separators and '..'), confirm/clamp the destination into the Downloads folder, warn on dangerous extensions and MIME/extension mismatches, and integrate a reputation/Safe-Browsing check before completing. Persist into the existing downloads table.
**Verifier note:** Treat this as a missing-feature / defense-in-depth item, not a high-severity vuln. Note that WebView2's default download pipeline already sanitizes suggested filenames (no '..'/absolute-path traversal), applies Mark-of-the-Web, and runs SmartScreen reputation, so the path-traversal and Safe-Browsing claims as written do not apply. If you want app-level download management and hardening: subscribe to CoreWebView2.DownloadStarting to (1) populate/track the existing downloads table via new DatabaseManager methods (AddDownload/UpdateProgress/GetDownloads), (2) optionally show a confirmation/warning for dangerous executable extensions and obvious MIME/extension mismatches, and (3) optionally clamp/confirm the destination directory. Either way, do not rely on app code for path-traversal or reputation safety since those are already enforced by the embedded engine.

#### 36. [LOW] Find-in-page injects user text into ExecuteScriptAsync with incomplete escaping
_JavaScript injection · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/MainWindow.xaml.cs  (lines 326)`  

FindBox_TextChanged builds JavaScript by interpolating the search text into a single-quoted string literal, escaping only single quotes: `$"window.find('{text.Replace(\"'\", \"\\\\'\")}')"`. Backslashes are not escaped (so a trailing backslash neutralizes the closing quote), and newlines/other control characters are not handled, allowing the search box to break out of the string and inject arbitrary JS into the page's execution context. Because the input is the user's own find query (not remote page content), the practical risk is self-injection rather than remote XSS, but it is still an injection bug and can corrupt page state.

```
await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync($"window.find('{text.Replace(\"'\", \"\\\\'\")}')");
```

**Fix:** Do not hand-escape. Use JsonSerializer.Serialize(text) (or System.Text.Json) to produce a safe JS string literal, e.g. ExecuteScriptAsync($"window.find({JsonSerializer.Serialize(text)})"). Better still, use CoreWebView2's built-in Find API (CoreWebView2.Find) where available.

#### 37. [LOW] Address bar accepts file:// and about: navigations with no gating; file access not explicitly restricted
_file:// scheme exposure · Security & Privacy · effort S_  
**Location:** `/home/thien/Projects/helix_browser/windows/HelixBrowser/Utils/UrlUtils.cs  (lines 21)`  

FormatUrl explicitly passes through file:// and about: URLs unchanged, and LoadUrl navigates to them. While WebView2's defaults restrict file:// origins from reaching arbitrary remote content, the app makes no deliberate decision here: there is no setting controlling local file access and no restriction on local-file navigation. For a privacy/security-focused browser this should be a conscious, documented policy (local file access is a known exfiltration/SOP-bypass surface) rather than an accidental allow.

```
if (trimmed.StartsWith("file://") || trimmed.StartsWith("about:")) return trimmed;
```

**Fix:** Decide and enforce an explicit local-file policy: either disallow user-initiated file:// navigation, or keep it but verify WebView2 file-access settings are constrained, and never expose it from web-origin-initiated navigations. Document the choice.
