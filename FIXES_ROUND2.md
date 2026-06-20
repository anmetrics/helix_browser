# Helix Browser — Round 2: Deep Audit + Bug/Parity/UX Fixes

_Follows [AUDIT_REPORT.md](AUDIT_REPORT.md) / [FIXES_APPLIED.md](FIXES_APPLIED.md). Goal: Chrome-grade, production-ready, bug-free, best-in-class UX._

## Deep audit (whole codebase, all 5 platforms, adversarially verified)
**434 confirmed findings**: 154 bugs (5 critical, 37 high, 73 medium, 39 low), 172 Chrome feature-parity gaps, 108 UX issues. (72-agent run; 4 refuted.)

## Verification status
| Platform | Gate | Result |
|---|---|---|
| **Android** | Full CI: `lintDebug` (0 errors) + `testDebugUnitTest` + `assembleDebug` + `assembleRelease` (R8) | ✅ **green** — debug + release-unsigned APKs build |
| iOS / macOS | Static review (no Swift toolchain; iOS has no `.xcodeproj` in repo) | ⚠️ needs Xcode build |
| Linux | `python -m py_compile` | ✅ parses |
| Windows | Static review (no dotnet; WebView2 1.0.2651 / WinAppSDK 1.5 present) | ⚠️ needs MSBuild |

## Fixed this round

### Android — every confirmed bug + UX issue (30 bugs + 16 UX), CI-verified
Highlights:
- **Reactive Bookmarks/History search** (was completely dead — query consumed nothing).
- **Live omnibox suggestions** — search-engine suggest + history + bookmarks, debounced, off-main-thread, URL/incognito-safe (the critical Chrome-parity gap).
- **Real download manager** — off-main-thread query, per-item cancel/retry/delete, determinate progress, recycler-bug fixes.
- **Settings now actually apply** — JavaScript toggle, desktop-site, DNT/anti-fingerprinting/dark re-applied to all open tabs on change; Brave engine added.
- **"Clear all data" now clears history** (Room DB); LIKE-wildcard escaping; ad-block default-on.
- **Tab state-corruption fixes** — `pinTab` stale-index, DiffUtil in-place mutation detection, bitmap recycle-while-referenced (UAF), FindListener cross-thread read.
- **Security** — exported Activity no longer loads attacker `data:` URLs; page-info "clear cookies" actually deletes; fullscreen-video leak fixed; geolocation/permission callbacks cancelled on teardown.
- **Perf** — PixelCopy off-thread thumbnails (no main-thread hitch); PiP keeps video playing.
- **UX** — omnibox clear (X) + paste-and-go; find-in-page raises keyboard + Enter→next; history date grouping + clean URLs; Material-styled error/SSL interstitials; better disabled-button contrast.

### Android — Chrome-parity feature (CI-verified)
- **Bookmark folders + editing** — nested folders, create/rename/delete (re-parent children, Chrome-style), move (with move-into-descendant prevention), edit title/url, nested Netscape HTML import/export. **Room v1→v2 migration** (additive `ALTER TABLE`, validated against exported `schemas/2.json`, no destructive fallback — existing installs upgrade safely).

### Cross-platform CRITICAL bugs (all 5)
- **iOS** — HTTPS auto-upgrade no longer downgrades to **cleartext http on a TLS/cert error** (was an MITM downgrade hole; now only genuine connection errors fall back; cert errors show the error page).
- **macOS** — incognito tabs no longer fetch a remote (Google) favicon → no host leak.
- **Linux** — incognito loads can no longer leak into the persistent history DB (event now attributed to the webview's own incognito flag, not the active tab).
- **Windows** — closed tabs now `Close()` the WebView2 (was leaking the whole msedgewebview2 process tree); incognito tabs use a **true in-private** CoreWebView2 (ephemeral profile) instead of the shared default profile.

### Android — Chrome-parity wave 2 (CI-verified)
Eight more parity features, all green through lint + tests + debug + release/R8:
- **Inline omnibox autocomplete** — top local history/bookmark host completes the typed prefix (forward-typing only, incognito-safe, never on search queries).
- **HTTP Basic/Digest auth** — `onReceivedHttpAuthRequest` credential dialog with WebViewDatabase save/prefill (was unhandled).
- **History time-range clear** — Last hour / 24h / 7 days / All (Chrome's "Clear browsing data" range), off-main-thread.
- **Most-visited tiles** — start page now renders top sites from history (frequency-ranked), falls back to defaults when empty / history-off.
- **Download confirmation sheet** — editable filename + host + size preview before enqueue.
- **Per-tab desktop mode** — desktop flag stored per `BrowserTab` (persisted in session), applied to the current tab instead of globally.
- **Save as PDF** — wired through the existing `PrintDocumentAdapter` path.
- **DRM / Protected Media** — `onPermissionRequest` grants `RESOURCE_PROTECTED_MEDIA_ID` so Widevine streaming works.

### Android — Chrome-parity wave 3 (CI-verified)
- **Tab groups** — create/name/color groups, grouped section headers in the grid, add/remove/ungroup, **multi-select mode** (batch close/group/share), and **close-with-undo** on every close path.
- **Omnibox answer cards** — inline calculator (safe recursive-descent evaluator, no `eval`) + unit conversions; tap to copy.
- **Tab-to-search keywords** — `yt`/`w`/`gh`/`maps` etc. (stored in prefs, http(s)-only templates).
- **Accessibility settings** — force-enable-zoom (overrides `user-scalable=no`) + minimum font size, applied live to open tabs.
- **In-app PDF viewer** — `PdfRenderer`-backed `PdfViewerActivity` (lazy per-page render, bounded memory, PFD/bitmap cleanup); PDFs route here instead of an external app.
- **Screenshot** — visible + best-effort full-page capture, shared via FileProvider.

## Remaining roadmap (not yet done)
**Verifiable next (Android):** remaining medium/low polish; new-tab most-visited tiles; in-app theme control (needs a light palette designed first).

**Needs the platform's compiler before trusting:** iOS/macOS/Windows high-severity bugs (~37 high across the non-Android platforms) — fixes can be written but must be built in Xcode/MSBuild.

**Large features (deliberately deferred — Staff-level: don't ship half-baked):**
- Autofill / password manager — needs secure credential storage + design.
- Page translation — needs a translation service/API.
- Built-in PDF viewer — needs a renderer/library.
- Full **iOS localization** — UI is hardcoded Vietnamese; needs a base-language decision + String Catalog across 7 view files + 14 locales.

**Release blockers (human/project):**
- Paste the Play Console licensing key into `BillingManager.kt` (premium is fail-closed until then).
- iOS: add `PrivacyInfo.xcprivacy` to the target, supply LaunchScreen + AppIcon, build in Xcode.
- Windows: build in MSBuild to confirm the WebView2 in-private + Close() edits.

_172 parity gaps + remaining bugs across all platforms are catalogued in the audit data; prioritize per platform as desired._
