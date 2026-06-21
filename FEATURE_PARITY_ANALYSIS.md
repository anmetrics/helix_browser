# Helix Browser — Feature-Parity Analysis & Auto-Remediation Roadmap

**Compared against:** Chrome (Stable + Enterprise), Microsoft Edge, Brave, Firefox, Safari, Arc.
**Scope:** all five Helix platforms — Android (Kotlin), iOS (Swift/UIKit), macOS (SwiftUI), Windows (C#/WinUI3), Linux (Python/GTK).
**Method:** ground-truth — every claim below was produced by reading/grepping the actual source (200 implemented features catalogued, 106 feature rows scored, 55 gaps remediated), then adversarially re-verified against the code (7 status corrections applied). Competitor columns reflect each browser's shipping state as of an early-2026 knowledge cutoff; treat them as directional, not contractual.

> Companion documents already in the repo: `AUDIT_REPORT.md` (210 line-level findings), `FIXES_APPLIED.md`, `FIXES_ROUND2.md`. This document is additive — it is the **feature-parity + remediation** layer, not a re-run of those bug audits.

---

## 0. TL;DR verdict (read this first)

**Helix is a thin native shell over each OS's system WebView — not a Chromium or Gecko fork.** That single architectural fact governs everything below:

| Platform | Helix shell | Underlying engine | Consequence |
|---|---|---|---|
| Android | `HelixWebView` (android.webkit) | Android System WebView (Blink) | Engine features inherited from GMS/WebView |
| iOS / macOS | WKWebView | WebKit | No extension API, no EME control, gated DevTools |
| Windows | WebView2 / `CoreWebView2` | Blink (Edge runtime) | Closest to "real Chromium"; SmartScreen/extensions *possible* |
| Linux | `WebKit2GTK 4.0` | WebKit | No Widevine CDM, no Safe Browsing backend |

Three consequences you must internalize:

1. **"Chrome-class enterprise browser" is not reachable on this architecture.** Brave, Edge, and Arc are all *Chromium forks*; Safari/Firefox ship their own engines. A WebView shell can never independently own the renderer, the network stack, site isolation, the extension engine, DevTools/CDP, or the DRM module. Reaching genuine Chrome parity would mean **forking Chromium** — a different company at a different scale (hundreds of engineers, multi-year). This document is honest about that line.
2. **But most of the gap surface is *product* features, which a WebView shell *can* own** — sync, password manager, profiles, AI, managed config, download manager, OS-keychain integration. **28 of 55 gaps are fully feasible in the WebView shell; only 6 truly require an engine fork.** The realistic, valuable target is a **premium privacy-respecting cross-platform WebView browser with sync + identity + AI + enterprise config** — not "Chrome-class," but a real, shippable, monetizable product.
3. **The codebase is better than its reputation in places and worse in others.** Android is a genuinely capable browser (real tab groups, session restore, PiP, reader mode, per-site permissions, careful security hygiene). iOS/macOS/Windows/Linux carry **dead code and decorative settings that actively lie to users** (iOS tab-groups/search coded but unreachable; iOS "trackers blocked" hardwired to 0; macOS memory-saver/mute/autoplay toggles that free nothing/do nothing; Windows SSL lock icon that only checks `https://` prefix with no cert validation).

**Release readiness: Alpha→Beta (product-wide). Not Production, not Enterprise-Ready, not Chrome-Class.** Android alone is Beta-grade; the other four platforms are Alpha. See §7.

---

## 1. The scoreboard

**106 feature rows scored across 15 domains:**

| Helix status | Count | Share |
|---|---:|---:|
| ✅ Supported | 7 | 7% |
| 🟡 Partial | 23 | 22% |
| ❌ Missing | 76 | 72% |

**55 remediation gaps, by priority and feasibility:**

| Priority | Gaps | Eng-weeks | | Feasibility | Gaps |
|---|---:|---:|---|---|---:|
| P0 (ship-blocker) | 1 | 7 | | Feasible in WebView shell | 28 |
| P1 (table-stakes) | 25 | 105 | | Partially feasible | 12 |
| P2 (competitive) | 22 | 78.5 | | Platform-API-dependent | 9 |
| P3 (nice-to-have) | 7 | 14.5 | | **Requires engine fork** | **6** |
| **Total** | **55** | **~205** | | | |

(~205 engineer-weeks is the *naive itemized sum* for one senior engineer. It excludes the sync/AI backends, cross-platform porting multiplication, design, QA, security review, and PM. Realistic program: **12–18 months** — see §11.)

---

## 2. Domain-level parity matrix (executive view)

Per-feature × per-browser matrices live in the **Appendix (Part B)**. This is the condensed domain roll-up. "Helix coverage" = supported / partial / missing across that domain's rows. "Feasibility" = can a WebView shell even do this.

| # | Domain | Helix (S/P/M) | Overall feasibility | Headline gap | Worst-case risk |
|---|---|:---:|---|---|---|
| 1 | Tab Management | 1 / 4 / 3 | ✅ feasible | "Sleeping tabs" frees no RAM (flag-only) | high |
| 2 | Profile System | 0 / 2 / 4 | 🟡 partial | No profiles at all (only incognito split) | high |
| 3 | Synchronization | 0 / 0 / 9 | 🟡 partial | Zero sync / no account / no backend | high |
| 4 | Password Manager | 0 / 0 / 7 | 🟡 partial | No credential storage/autofill anywhere | high |
| 5 | AI Features | 0 / 0 / 10 | ✅ feasible | Zero AI — the marquee competitor differentiator | high |
| 6 | Privacy | 0 / 3 / 4 | 🟡 partial | JS-shim only; 17-domain hardcoded tracker list | high |
| 7 | Security / Safe Browsing | 0 / 3 / 2 | 🟡 partial | **No protection floor on Linux/Windows (P0)** | critical |
| 8 | Developer Tools | 0 / 4 / 1 | 🔧 engine | Inspection OFF on Android & iOS (1-line fix) | med |
| 9 | Enterprise Management | 0 / 0 / 8 | 🟡 partial | No managed config / MDM / SSO / cert injection | critical |
| 10 | Extension Marketplace | 0 / 0 / 6 | ⛔ **engine fork** | No runtime engine; WebViews can't host one | critical |
| 11 | Browser Wallet / Web3 | 0 / 0 / 4 | ✅ feasible | No `window.ethereum`; injection points exist | high |
| 12 | Download Manager | 0 / 0 / 4 | 🟡 partial | No pause/resume, no malware scan of bytes | critical |
| 13 | Media / DRM | 3 / 4 / 0 | 🟡 partial | Widevine only on Android; iOS has no PiP | high |
| 14 | Performance | 0 / 2 / 5 | 🟡 partial | No prefetch/prerender; memory-saver is a stub off-Android | high |
| 15 | OS Integration | 3 / 1 / 9 | 🟡 partial | No keychain/biometric/notifications anywhere | high |

Legend: ✅ feasible-in-webview · 🟡 partially-feasible · 🔧 platform-API-dependent · ⛔ requires-engine-fork.

**The shape of the data:** Helix today is a *competent single-engine browser front-end*. Every domain that is purely **product** (tabs, downloads, wallet, AI, privacy UI, keychain) is buildable; every domain that is **engine** (extensions, in-app DevTools panels, state partitioning, real DRM) is blocked or constrained. The 7 "supported" rows cluster in **Media/DRM** (Android PiP, fullscreen, audio) and **OS Integration** (Apple-Silicon/ARM64/X11 native builds) — i.e. things inherited cheaply — while the 76 "missing" rows are the product features that make a browser a *platform* rather than a *viewer*.

---

## 3. Final Deliverable 1–8 — Gap inventory by category

The 12 requested deliverables follow. Items 1–8 are the categorized gap lists; 9–12 are roadmap/effort/team. Full remediation (architecture, code, migration, testing) for each gap is in **Part B**, cross-referenced by `[domain]`.

### Deliverable 1 — Missing Feature List (the table-stakes that are simply absent)

| Feature | Status | Platforms affected | Priority | `[domain]` |
|---|---|---|---|---|
| Account / sign-in + sync (bookmarks, history, tabs, passwords, settings) | ❌ absent | all 5 | P1 | sync |
| Password manager (save/fill/generate/health/breach) | ❌ absent | all 5 | P1 | password-manager |
| Passkeys / WebAuthn / FIDO2 | ❌ absent | all 5 | P1/P2 | password-manager |
| User profiles (multi-account) + Guest mode + Managed profiles | ❌ absent | all 5 | P1 | profile-system |
| AI assistant / summarize / translate / smart search | ❌ absent | all 5 | P1 | ai-features |
| Real "sleeping tabs" / Memory Saver (off-Android) | 🟡 stub | iOS/macOS/Win/Linux | P1 | tab-management, performance |
| Vertical tabs / split view / workspaces | ❌ absent | all 5 | P2 | tab-management |
| Download pause/resume, malware scan, categorization | ❌ absent | all 5 | P1 | download-manager |
| OS keychain + biometric unlock + native notifications | ❌ absent | all 5 | P1/P2 | os-integration |
| Crypto wallet / Web3 provider | ❌ absent | all 5 | P2 | browser-wallet-web3 |
| Extension support | ❌ absent (engine-blocked) | all (WebView2 partial) | P3 | extension-marketplace |
| Reader mode | ❌ absent | **iOS, macOS** (present Android/Linux) | P2 | (inventory) |
| Picture-in-Picture | ❌ absent | **iOS** (present Android/macOS) | P1 | media-drm |
| Per-site permissions UI | ❌ absent | **iOS, macOS** (present Android) | P2 | (inventory) |

### Deliverable 2 — Security Gaps

- **P0 — No first-party Safe Browsing / phishing-malware floor.** Protection swings from *GMS Safe Browsing* (Android) → *WebKit Google Safe Browsing, unconfigured* (iOS/macOS) → **nothing** (Linux WebKitGTK; Windows WebView2 with SmartScreen never enabled by Helix). Helix never handles a hit, customizes the interstitial, or owns a testable safety contract. `[security-safe-browsing]`
- **P1 — No download protection.** `enqueueDownload` validates only URL scheme; the confirmation sheet shows name/host/size but issues **no warning for executables** (`.apk/.exe/.dmg/.msi/.bat/.scr`) and runs no reputation/hash check on any platform. `[security-safe-browsing, download-manager]`
- **P1 — Downloaded bytes are never scanned** for malware on any platform. `[download-manager]`
- **Windows — fake SSL indicator.** `UpdateSslIcon` only checks `url.StartsWith("https://")` — no cert validation, no mixed-content awareness. A user-visible lock that lies. `[inventory: Windows]`
- **macOS — no entitlements file at all** → no App Sandbox, no Hardened Runtime, `NSAllowsArbitraryLoads=true` still present; notarization-blocking. `[os-integration]`
- **Cross-platform — wallet absence is a *latent* security win, but if added without an origin-permission + tx-verification model first, becomes a drainer vector.** `[browser-wallet-web3]`

### Deliverable 3 — Privacy Gaps

- **Anti-tracking is a frozen ~17-domain hardcoded list + JS shims**, not network-level enforcement, with **no updatable filter list** (no EasyList subscription) on any platform. A real privacy browser cannot ship a static blocklist. `[privacy-features]`
- **macOS third-party-cookie logic has inverted/decorative behavior**; several macOS privacy toggles (autoplay, fingerprinting) persist a pref nothing reads. `[privacy-features, inventory: macOS]`
- **Anti-fingerprinting is JS-injection only** — trivially detectable/bypassable, cannot touch the network-stack/TLS/font fingerprint surface (that's engine-owned). Honest framing required in UI. `[privacy-features]`
- **No state/site partitioning (dFPI)** — cookies/localStorage/cache/IndexedDB are not keyed by top-level site. This one is **engine-owned** (requires-engine-fork). `[privacy-features]`
- **No DoH/DoT / encrypted-DNS control**, **no Private Relay/proxy**. `[privacy-features]`
- **"Do Not Track" is a `navigator.doNotTrack` JS property override** — no real DNT/Sec-GPC request header is sent (Android). `[inventory: Android]`
- **iOS "trackers blocked" counter is hardwired to 0** (never incremented) — a privacy claim shown to users that is literally always false. `[inventory: iOS]`

### Deliverable 4 — Architecture Gaps

- **WebView-shell ceiling** (the master constraint): extensions, in-app DevTools network/perf/memory panels, site isolation, EME/Widevine module, prerender — all engine-owned. 6 gaps are flatly `requires-engine-fork`. `[extension-marketplace, developer-tools, privacy-features, performance-resource]`
- **No identity/sync backend exists** — there is no server, no account, no OAuth, no E2E-crypto layer. This is greenfield infra, not a code tweak. `[synchronization]`
- **Schema is not sync-ready:** `Bookmark`/`HistoryItem` use autoincrement `Long` PKs (collide across devices), a single timestamp, and **no tombstones** — naive sync would duplicate or resurrect records. `[synchronization]`
- **Five divergent storage stacks** (Android Room, iOS/macOS UserDefaults+JSON, Windows SQLite, Linux SQLite) with **duplicated, partly-dead data layers** (iOS `DataManager.add*` API exists but is never called; the live path writes inline UserDefaults arrays). A sync/profile layer must unify these. `[inventory: iOS/macOS]`
- **MainActivity.kt is a 3,087-line god-object** owning pool/omnibox/menus/downloads/PiP/permissions — a refactor risk for any new feature. `[inventory: Android]`
- **Per-platform feature drift is severe** — the same "feature" (tab groups, memory saver, reader mode) is full on one platform, stub on another, absent on a third. There is no shared feature contract. `[all]`

### Deliverable 5 — Performance Gaps

- **Memory Saver is real only on Android** (LRU pool cap `MAX_LIVE_WEBVIEWS=6`, `onTrimMemory` eviction); **iOS is memory-warning-only; macOS/Linux/Windows are pure flag-flip stubs that free zero RAM** behind toggles that therefore lie. `[performance-resource, tab-management]`
- **Even Android only sleeps tabs from `onPause`** — a long foreground session with many tabs never reclaims until backgrounded. `[performance-resource]`
- **No predictive loading / preconnect / prefetch / DNS-prefetch anywhere** — the omnibox computes a top suggestion but never warms its connection. `[performance-resource]`
- **No Speculation Rules / prerender** (engine-owned). `[performance-resource]`
- **No battery-saver / power-aware throttling** on any platform. `[performance-resource]`
- **No `onRenderProcessGone` recovery, no `setRendererPriorityPolicy`** (Android) — a crashed renderer is unhandled. `[performance-resource]`

### Deliverable 6 — UX Gaps

- **Dead/unreachable features that should be wired or removed:** iOS tab groups/search/pin/duplicate/mute/close-others all coded in `TabManager.swift`, zero UI callers; macOS `isTabSearchVisible`/`tabSearchQuery` unused. `[tab-management]`
- **Decorative settings that don't work:** macOS autoplay-block, restore-tabs, confirm-close-multiple, default-zoom, downloads-dir all persist a pref nothing reads; tab mute & suspend are UI-only no-ops. `[inventory: macOS]`
- **Missing standard affordances by platform:** iOS lacks pull-to-refresh, reader mode, homepage config, bookmark folders; macOS lacks a downloads list UI (the badge is a dead button), print/save-PDF, screenshot. `[inventory: iOS/macOS]`
- **Find-in-page inconsistency:** Android has a match counter; macOS/iOS show none; iOS silently no-ops pre-iOS16. `[inventory]`
- **Localization is hardcoded Vietnamese** on iOS/macOS (no i18n framework), while Android has a 14-language switcher — inconsistent and not release-grade for global launch. `[inventory: iOS/macOS]`
- **Groups can't collapse/expand or drag-reorder** even where they exist (Android/macOS). `[tab-management]`

### Deliverable 7 — Enterprise Gaps

- **Zero managed-config ingestion** — no Android `RestrictionsManager`/`android:restrictions`, no Windows ADMX/registry policy, no Apple Managed App Config. Every setting is end-user-mutable. `[enterprise-management]`
- **No enterprise root-CA / private trust store** — and Helix *hardens* against untrusted roots (good for consumers, blocks corporate MITM proxies). No admin path to inject a CA. `[enterprise-management]`
- **No SSO / IWA / Kerberos / SAML/OIDC broker**, no MSAL/AppAuth dependency. Azure AD conditional-access / device-trust SSO is **structurally impossible in a WebView shell**. `[enterprise-management]`
- **No remote config / kill-switch / fleet management.** `[enterprise-management]`
- Net: "Enterprise Ready" is not on the near roadmap; a *managed-config subset* (policy JSON → locked prefs, forced search engine, blocked-URL list) is the achievable first step. `[enterprise-management]`

### Deliverable 8 — AI Feature Gaps

- **Zero AI of any kind** — verified by grep across all five platforms (only false positives like `clear_cache_summary`, `translatesAutoresizingMaskIntoConstraints`). No assistant, summarize, translate, smart-search, tab-organization, writing, or agents; no local-model bundle, no inference client. `[ai-features]`
- This is the **single biggest competitive-differentiation gap**: Edge ships Copilot, Brave ships Leo, Arc ships Max, Chrome ships Gemini integration. A privacy browser is well-positioned to lead with **on-device / hybrid** summarization+translation. `[ai-features]`
- **No model-routing abstraction and no monetization wiring** — `BillingManager` exposes exactly one SKU (`helix_premium_monthly`, fail-closed) but nothing consumes it; AI is the obvious premium anchor. `[ai-features]`
- All AI sub-features are **feasible-in-webview** (they're product features over page text), except deep agentic control which is partially feasible. `[ai-features]`

---

## 4. Deliverable 9 — Complete Fix Roadmap

Sequenced into four waves. Each item references its `[domain]` in Part B for architecture/code/migration/testing. "wk" = itemized senior-eng weeks (single platform unless noted; ×N for cross-platform port).

### Wave 0 — Truth & Safety (≈4 weeks) — *do these before anything else; they stop the product lying*
1. **P0 Safe-Browsing floor on every platform** — enable + handle hits, custom interstitial; Linux needs a first-party threat-list lookup (no engine backend exists). `[security-safe-browsing]` · 7wk
2. **Download protection** — dangerous-file-type warning + mark-of-the-web + hash/reputation check. `[security-safe-browsing, download-manager]` · 3wk
3. **Delete or wire dead code & decorative settings** — iOS tab-group/search UI (or remove), macOS no-op toggles, iOS "trackers blocked" counter, Windows real cert validation (stop the fake lock). `[tab-management, privacy, inventory]` · 3wk
4. **Fill the Play-billing licensing key** (already-flagged release blocker) and gate at least one premium feature. `[inventory: Android]` · 0.5wk
5. **macOS entitlements + Hardened Runtime + notarization**; remove `NSAllowsArbitraryLoads`. `[os-integration]` · 1wk
6. **1-line DevTools fixes** — `setWebContentsDebuggingEnabled` (Android), `isInspectable` (iOS). `[developer-tools]` · 0.5wk

### Wave 1 — Identity & the data spine (≈10 weeks) — *unblocks sync, profiles, password sync*
7. **Account/sign-in + Helix Sync backend** (OAuth/passwordless, opaque store). `[synchronization]` · 14wk (backend-heavy)
8. **E2E / zero-knowledge crypto layer** (Argon2id → Keystore/Keychain → HKDF → XChaCha20-Poly1305). `[synchronization]` · 6wk
9. **Sync-ready schema migration** — stable UUIDs, change timestamps, tombstones across all five stores. `[synchronization]` · 2wk
10. **Profiles + Guest mode** on the new data spine; per-profile storage isolation (fix Android shared-cookie-jar incognito). `[profile-system]` · 14wk

### Wave 2 — Flagship product features (≈14 weeks)
11. **Password manager** — encrypted vault, save/fill/generate, breach check; **OS keychain + biometric unlock**. `[password-manager, os-integration]` · 10wk
12. **AI assistant + summarize + translate**, hybrid local/cloud routing, premium-gated. `[ai-features]` · 12wk
13. **Real Memory Saver / Sleeping Tabs** (true WebView teardown + restore) on all platforms; predictive preconnect/prefetch. `[tab-management, performance-resource]` · 5wk
14. **Updatable filter lists** (EasyList subscription) + native request-level blocking; honest privacy UI. `[privacy-features]` · 5wk
15. **Download manager v2** — pause/resume, categorization. `[download-manager]` · 6wk

### Wave 3 — Differentiators & enterprise-lite (≈12 weeks)
16. **Managed-config subset** — policy JSON → locked prefs, forced search engine, URL blocklist, enterprise-CA injection where the engine allows. `[enterprise-management]` · 9wk
17. **Vertical tabs / split view / workspaces.** `[tab-management]` · 8wk
18. **Web3 wallet** — origin-permission model + tx-verification UI *first*, then EIP-1193 provider. `[browser-wallet-web3]` · 12wk
19. **iOS PiP + reader mode + per-site permissions** (close the iOS/macOS feature drift). `[media-drm, inventory]` · 5wk
20. **Native notifications + Linux Wayland/Secret-Service hardening.** `[os-integration]` · 6wk

### Explicitly out of near-term scope (engine-fork territory)
- WebExtension/MV3 runtime engine (only WebView2 even exposes `AddBrowserExtensionAsync`; the other four cannot host extensions at all).
- In-app DevTools network/perf/memory/Lighthouse panels (engine inspector, not embeddable).
- State partitioning (dFPI), prerender/Speculation Rules, network-stack anti-fingerprinting, custom Widevine CDM.
- Azure AD conditional-access / device-trust SSO.

---

## 5. Deliverable 10 — Chrome-Parity Roadmap (and the honest ceiling)

"Chrome parity" splits into three tiers:

**Tier A — Achievable on the WebView shell (the real target).** Sync, profiles, password manager + passkeys (where the platform authenticator allows), AI, managed config, download manager, memory saver, updatable privacy lists, OS-keychain/biometric/notifications, vertical tabs. Delivered by Waves 0–3 ≈ **12–18 months**. This yields a browser that *feels* Chrome-class to a consumer for everyday use.

**Tier B — Partial parity, engine-permitting.** Safe Browsing (lookups inherited; interstitial/telemetry Helix-owned), Widevine (engine module; Helix owns EME enablement + per-site UX; Linux needs a bundled CDM), DevTools (flip the inspector on; can't build in-app panels), WebView2 extensions on Windows only.

**Tier C — Unreachable without forking the engine.** True site isolation, full extension ecosystem cross-platform, in-app profiler/Lighthouse, prerender, network-stack fingerprint defense, conditional-access SSO. **To get these, Helix would have to become a Chromium fork** (the Brave/Edge/Arc strategy). That is a strategic company decision, not a backlog item — budget *years and a browser-platform team* if pursued. Recommendation: **do not** pursue a fork; differentiate on privacy + AI + cross-platform breadth instead.

---

## 6. Deliverable 11 — Estimated Engineering Effort

| Layer | Itemized eng-weeks | Notes |
|---|---:|---|
| P0/P1 gap work (itemized) | ~112 | Single-platform sums; many ×2–5 for full cross-platform |
| P2/P3 gap work (itemized) | ~93 | |
| **Itemized gap total** | **~205** | From the 55 remediation specs |
| Sync/identity backend + infra | +40–60 | Servers, ops, security review (not in itemized sum) |
| AI infra (model hosting/routing, eval, safety) | +25–40 | |
| Cross-platform port multiplication | +60–90 | Most "feasible" gaps are scored on one platform |
| Design, QA/test automation, PM, sec review | +35% overhead | Applied across all of the above |

**Bottom line:** roughly **1,000–1,300 engineer-weeks** for Tier-A Chrome-feel parity across all five platforms — i.e. a focused **team of 12–18 for 12–18 months**, not a solo or small effort. A *single-platform (Android) Tier-A* slice is far cheaper: ~**150–200 eng-weeks**, a 5–6 person pod for ~6 months, and is the recommended first commercial milestone.

---

## 7. Deliverable 12 — Recommended Team Structure & Release Readiness

### Recommended team structure

- **Platform pods (4):** Android (3), Apple/iOS+macOS (4), Windows (2), Linux (2). Each owns its shell + feature-contract conformance.
- **Sync & Identity backend team (3–4):** account service, zero-knowledge store, E2E crypto, device management. Owns the data spine all platforms depend on.
- **AI team (3):** model routing (on-device + cloud), summarize/translate/assistant, eval + safety, premium metering.
- **Privacy & Security guild (2, cross-cutting):** filter-list pipeline, Safe-Browsing contract, download protection, threat modeling, the security review gate.
- **Design + UX (2)** and **QA/Release Engineering (2):** cross-platform feature-parity matrix as a living contract; CI device labs (Android is the only currently CI-verifiable target — extend to iOS/macOS/Windows/Linux).
- **1 EM + 1 PM + 1 security lead.** Total ≈ **14–18 people** for the full program; a **5–6 person Android-first pod** for the first milestone.

### Release readiness classification (per platform)

| Platform | Class | Why | Blockers to next tier |
|---|---|---|---|
| **Android** | **Beta** | Genuinely capable: real tab groups, session restore, PiP, reader mode, per-site permissions, careful security hygiene, memory mgmt | Billing key empty; no sync/passwords/profiles/AI; Safe-Browsing hit not handled; DevTools off |
| **iOS** | **Alpha** | Dead unreachable tab features; no reader mode; no PiP; no per-site permission UI; "trackers blocked" always 0 | Wire-or-remove dead code; PiP; reader; real privacy stat; i18n |
| **macOS** | **Alpha→Beta** | Solid shell + sidebar + shortcuts, but decorative no-op settings (memory saver/mute/autoplay/downloads-dir); no downloads UI; no entitlements | Make settings real; downloads list; entitlements/notarization |
| **Windows** | **Alpha** | WebView2 shell works, but fake SSL lock (no cert validation); downloads not wired; no fullscreen mgmt; SmartScreen not enabled | Real cert state; wire downloads; enable SmartScreen |
| **Linux** | **Alpha** | Functional GTK shell, but **no Safe Browsing backend at all**; deprecated X11-era APIs; downloads not wired | Threat-list lookup; Wayland; libsecret; downloads |

**Overall product classification: Alpha→Beta.** It is **not Production** (security floor inconsistent, settings that lie, dead code), **not Enterprise-Ready** (zero managed config/SSO/CA), and **not Chrome-Class** (no sync/profiles/passwords/AI/extensions; and the architecture caps the last one permanently). The fastest credible path to a **Production** rating is **Wave 0 + an Android-first Wave 1–2 slice**.

---

## Part B — Per-domain remediation detail (auto-generated, verified)

> Each domain below lists: overall feasibility, the verified summary, the full feature × browser parity matrix, any adversarial-verifier corrections to Helix's status, and — for the most material gaps — architecture, implementation plan, a real-language code example anchored in actual classes, migration strategy, testing strategy, and effort. Generated from the verified workflow output; competitor columns are early-2026 directional.

---

## B1. Tab Management  `[tab-management]`

**Overall feasibility:** feasible-in-webview


Verified against the real code, Helix's tab management is the most mature of any domain on Android and surprisingly hollow elsewhere. Tab Groups are genuinely shipped on Android (model + TabSwitcher UI) and macOS (context-menu UI, ContentView.swift:367-378) but are dead/unreachable code on iOS (TabManager.swift implements createTabGroup/searchTabs/pinTab with ZERO Views callers) and only stub data fields on Windows/Linux. Tab Search is fully wired only on Android (TabSwitcherActivity search box -> TabManager.searchTabs:439); macOS has unused isTabSearchVisible/tabSearchQuery fields and iOS has an uncalled searchTabs. Session Restore is the one truly cross-platform-complete feature (Android SharedPrefs, iOS/macOS UserDefaults, Windows/Linux session.json). The biggest correctness gap is Sleeping Tabs / Memory Saver: isSuspended is set after 10 min on all three native platforms but NO platform actually tears down the WebView/WKWebView to reclaim memory, and there is no Memory Saver UI or savings stat - so the feature looks present but delivers none of its benefit, the exact thing Edge/Chrome market. Vertical Tabs, Split Tabs, and Workspace Tabs are absent everywhere (Arc/Edge own these). Crucially, every gap here is product-layer and feasible in a WebView shell - none require an engine fork; the WebView owns rendering, not tab orchestration. The highest-ROI work is the cheap reachability fixes (surface iOS/macOS group + search UI that already exist) and making sleeping tabs real on Android (the only CI-verifiable platform), followed by vertical tabs and collapsible groups for desktop parity. Verification caveat per project memory: only Android is locally buildable, so all iOS/macOS/Windows/Linux code examples are static-review-grade until an Xcode/MSBuild/dotnet pipeline confirms them.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Tab Groups (named, colored, persisted) | 🟡 partial | ✅ | ✅ | ✅ | ❌ | ✅ | 🟡 | med | M |
| Tab Search (quick switcher / find-tab) | 🟡 partial | ✅ | ✅ | ✅ | 🟡 | ❌ | ✅ | med | S |
| Vertical Tabs (side tab strip) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | **high** | L |
| Split Tabs / Split View (two pages side-by-side) | ❌ missing | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ | med | L |
| Sleeping Tabs (true unload + restore) | 🟡 partial | ✅ | ✅ | ✅ | 🟡 | 🟡 | ✅ | **high** | M |
| Memory Saver (automatic RAM reclamation + UI) | 🟡 partial | ✅ | ✅ | ✅ | 🟡 | 🟡 | ✅ | **high** | M |
| Workspace Tabs (multi-workspace separation of tab sets) | ❌ missing | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | low | L |
| Session Restore (tabs+groups across launches/crash) | ✅ supported | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | low | S |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B1.1 — Sleeping Tabs / Memory Saver is a flag, not a real unload. BrowserTab.isSuspended is set after 10 min (TabManager.kt:553-566, MainActivity.kt:2682) but on Android the WebView is NOT actually destroyed when suspended — switchToTab just clears the flag (TabManager.kt:236-247) and the WebView pool eviction is driven separately by onTrimMemory, not by the suspend timer. iOS (TabManager.swift:249-259) and macOS (WebViewModel.swift:232-242) only toggle isSuspended with no WKWebView teardown/reload-on-resume. So no memory is reclaimed by 'sleeping' and there is no user-facing Memory Saver UI, savings stat, or per-site exclusion list.

`P1` · feasibility: `feasible-in-webview` · ~3 eng-weeks


**Why it matters.** Tab-heavy users are the exact cohort that churns to Edge/Chrome/Brave for their advertised RAM savings. On Android, a pool of live WebViews each carrying a full Blink renderer is the #1 driver of OOM tab reloads (the bug class FIXES already chase via onTrimMemory). A 'sleeping tab' that keeps its renderer alive delivers zero of the promised benefit while implying it does, which is both a competitive gap and a correctness/perception bug. Edge markets exact MB saved; Helix shows nothing.


**Recommended architecture.** Make suspension actually reclaim the renderer. Android: in MainActivity's WebView pool, add suspendTab(tabId) that PixelCopy-snapshots a placeholder thumbnail, calls webView.onPause()+webView.destroy() (or removes from the pool and frees it), and stores the WebView's WebBackForwardList / Bundle via saveState into BrowserTab so resume can restore scroll+history. Add a resume path keyed off switchToTab that recreates the WebView and restoreState()s it. Extend BrowserTab with a serialized navState: ByteArray? and add TabManager.markSuspended(id). Add a MemorySaverManager helper that owns the timer, the per-origin exclusion set (reuse the existing per-site prefs store), and a running reclaimedTabs counter surfaced in SettingsActivity. iOS/macOS: in TabManager.suspendInactiveTabs nil out tab.webView after capturing tab.interactionState (the iOS captureInteractionStates path already exists, TabManager.swift:200-219) and rebuild the WKWebView on switchToTab.


**Implementation plan.**
1. Add navState: ByteArray? and suspendedAt: Long to BrowserTab; bump the JSON (de)serializer in TabManager.saveTabs/restoreTabs to round-trip them.
1. Create engine/MemorySaverManager.kt owning the suspend timer, threshold pref (Off/Auto/Aggressive), per-origin exclusion set, and a persisted reclaimedCount.
1. In MainActivity's WebView pool, implement suspendTab(id): snapshot thumbnail via existing PixelCopy path, webView.saveState(bundle) -> serialize to BrowserTab.navState, detach from pool, webView.destroy().
1. Implement resumeTab(id): create a fresh pooled WebView, restoreState(bundle) from navState, fall back to loadUrl(tab.url) if null; wire into the existing switchToTab flow.
1. Replace the no-op TabManager.suspendInactiveTabs flag-set with a callback into MemorySaverManager.suspendTab so the model and pool stay in sync; keep current+pinned+excluded tabs alive.
1. Add a Memory Saver settings section (mode toggle, per-site exclusion list, 'X tabs put to sleep' stat) in SettingsActivity and a sleeping-tab visual badge in TabSwitcher adapters.
1. Port the WKWebView nil-out/rebuild equivalent to iOS TabManager and macOS WebViewModel using the existing interactionState capture.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/MemorySaverManager.kt`:

```kotlin
package com.helix.browser.engine

import android.os.Bundle
import android.os.Parcel
import android.webkit.WebView
import com.helix.browser.tabs.BrowserTab
import com.helix.browser.tabs.TabManager

/**
 * Actually reclaims renderer memory for idle tabs by tearing the WebView down
 * and persisting its navigation state, instead of only flipping isSuspended.
 * The MainActivity WebView pool calls suspend()/resume(); TabManager stays the
 * source of truth for which tabs exist.
 */
class MemorySaverManager(private val tabManager: TabManager) {

    enum class Mode { OFF, AUTO, AGGRESSIVE }
    var mode: Mode = Mode.AUTO

    private val excludedOrigins = mutableSetOf<String>()
    var reclaimedCount = 0
        private set

    fun isExcluded(tab: BrowserTab): Boolean = originOf(tab.url) in excludedOrigins

    /** Tear down [webView]; returns the serialized nav state to stash on the tab. */
    fun suspend(tab: BrowserTab, webView: WebView): ByteArray? {
        if (mode == Mode.OFF || tab.isPinned || isExcluded(tab)) return null
        val bundle = Bundle()
        // saveState returns null if there is no history worth keeping.
        val hasState = webView.saveState(bundle) != null
        webView.onPause()
        webView.loadUrl("about:blank")
        webView.destroy()
        tab.isSuspended = true
        tab.navState = if (hasState) marshall(bundle) else null
        reclaimedCount++
        return tab.navState
    }

    /** Rebuild a freshly-created [webView] for a tab being switched back to. */
    fun resume(tab: BrowserTab, webView: WebView) {
        tab.isSuspended = false
        val state = tab.navState
        if (state != null) {
            webView.restoreState(unmarshall(state))
        } else if (tab.url.isNotEmpty()) {
            webView.loadUrl(tab.url)
        }
        tab.navState = null
        tab.lastAccessTime = System.currentTimeMillis()
    }

    private fun originOf(url: String): String =
        runCatching { android.net.Uri.parse(url).host ?: "" }.getOrDefault("")

    private fun marshall(b: Bundle): ByteArray {
        val p = Parcel.obtain()
        return try { p.writeBundle(b); p.marshall() } finally { p.recycle() }
    }

    private fun unmarshall(bytes: ByteArray): Bundle {
        val p = Parcel.obtain()
        return try {
            p.unmarshall(bytes, 0, bytes.size); p.setDataPosition(0)
            p.readBundle(javaClass.classLoader) ?: Bundle()
        } finally { p.recycle() }
    }
}
```

**Migration.** Additive: BrowserTab gains nullable navState/suspendedAt; existing persisted tab JSON (no navState key) deserializes with navState=null, so old sessions simply reload by URL on first resume - no data loss, no DB migration (storage is SharedPreferences JSON, not Room). Marshalled WebBackForwardList Parcels are intentionally NOT persisted to disk across app restarts (they are version-fragile); navState lives in-memory only, while saveTabs continues to persist url/title so cold-start restore is unchanged.


**Testing.** Unit: MemorySaverManager.suspend skips pinned/excluded/OFF, increments reclaimedCount, returns null when saveState is empty; resume restores by navState then falls back to url. Instrumented (Android, the CI-verified platform): open 8 tabs, fast-forward the suspend timer, assert Debug.getPss() drops and that switching back restores scroll position + back-stack via WebBackForwardList size. Regression: ensure current+pinned tabs are never torn down and that onTrimMemory eviction still coexists without double-destroy.


#### Gap B1.2 — iOS tab groups, tab search, pin, duplicate, mute, close-others/close-to-right are fully implemented in TabManager.swift (createTabGroup:157, searchTabs:171, pinTab:139, duplicateTab:134) but have ZERO UI callers anywhere in Views/*.swift - confirmed by grep returning nothing. They are dead, unreachable code. macOS has the inverse for search: WebViewModel exposes isTabSearchVisible/tabSearchQuery (WebViewModel.swift:30-31) but no SwiftUI view references them.

`P1` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Helix already paid the engineering cost for these features on iOS and macOS, then shipped them invisible. A user on iPhone/iPad literally cannot create a tab group or search tabs despite the model supporting it, so the product looks two tiers behind Safari/Arc on the platform where Apple users most expect tab groups. This is the cheapest parity win in the whole domain: surface existing logic, no new engine work.


**Recommended architecture.** iOS: add a long-press UIContextMenuConfiguration on tab cells in the tab switcher (BrowserViewController / tab grid) wiring to TabManager.pinTab/duplicateTab/muteTab/createTabGroup/addTabToGroup, plus a search bar bound to TabManager.searchTabs and grouped sections in the tab-switcher collection view (mirror Android's TabListItem.Header/TabItem flattening). macOS: add a tab-search overlay SwiftUI view (TabSearchOverlay) bound to WebViewModel.isTabSearchVisible/tabSearchQuery, triggered by a Cmd+Shift+A menu item, listing filtered tabs from a new WebViewModel.filteredTabs computed property.


**Implementation plan.**
1. iOS: build TabContextMenuProvider returning UIContextMenuConfiguration with Pin/Duplicate/Mute/Add-to-Group/New-Group/Close-Others actions calling the existing TabManager methods.
1. iOS: add a UISearchController to the tab switcher; filter the data source via TabManager.searchTabs(query:).
1. iOS: render grouped sections (header per TabGroup using groupName/color) in the tab-switcher collection view.
1. macOS: add WebViewModel.filteredTabs computed from tabSearchQuery; build TabSearchOverlay.swift bound to isTabSearchVisible.
1. macOS: add a Cmd+Shift+A menu command toggling isTabSearchVisible and an Esc/click-out dismissal.
1. Localize the new strings (both bundles are Vietnamese-default) and add VoiceOver labels.

**Code example** — `ios/HelixBrowser/HelixBrowser/Views/TabContextMenu.swift`:

```swift
import UIKit

/// Surfaces the already-implemented (but previously unreachable) TabManager
/// operations as an iOS long-press context menu on a tab cell.
enum TabContextMenu {
    static func configuration(for tab: BrowserTab,
                              manager: TabManager,
                              reload: @escaping () -> Void)
        -> UIContextMenuConfiguration {
        UIContextMenuConfiguration(identifier: tab.id as NSString,
                                   previewProvider: nil) { _ in
            let pin = UIAction(
                title: tab.isPinned ? "Bỏ ghim" : "Ghim tab",
                image: UIImage(systemName: "pin")) { _ in
                    manager.pinTab(id: tab.id); reload()
                }
            let duplicate = UIAction(
                title: "Nhân đôi",
                image: UIImage(systemName: "plus.square.on.square")) { _ in
                    manager.duplicateTab(id: tab.id); reload()
                }
            let mute = UIAction(
                title: tab.isMuted ? "Bật tiếng" : "Tắt tiếng",
                image: UIImage(systemName: "speaker.slash")) { _ in
                    manager.muteTab(id: tab.id); reload()
                }
            let newGroup = UIAction(
                title: "Nhóm mới",
                image: UIImage(systemName: "square.grid.2x2")) { _ in
                    manager.createTabGroup(name: "Nhóm mới", tabIds: [tab.id])
                    reload()
                }
            let groupMenu = UIMenu(
                title: "Thêm vào nhóm",
                children: manager.tabGroups.map { group in
                    UIAction(title: group.name) { _ in
                        manager.addTabToGroup(tabId: tab.id, groupId: group.id)
                        reload()
                    }
                } + [newGroup])
            let closeOthers = UIAction(
                title: "Đóng tab khác",
                attributes: .destructive) { _ in
                    manager.closeOtherTabs(except: tab.id); reload()
                }
            return UIMenu(children: [pin, duplicate, mute, groupMenu, closeOthers])
        }
    }
}
```

**Migration.** None required - this only exposes existing model APIs and the existing persisted TabGroup store (savedGroupsKey). Previously-created groups (if any survived) render immediately; no schema change.


**Testing.** iOS UI test: long-press a tab cell, assert each menu action calls the corresponding TabManager method and the data source reloads; assert grouped sections render with correct headers; assert search filters down to matching titles/urls. macOS: snapshot test TabSearchOverlay visibility toggling with isTabSearchVisible and that filteredTabs honors tabSearchQuery. Note: per the verification-asymmetry memory, iOS/macOS cannot be CI-compiled here, so flag as static-review-only until an Xcode build runs.


#### Gap B1.3 — Vertical Tabs are absent on every platform (grep for 'vertical tab' / side strip returns only LinearLayout.VERTICAL layout noise, no feature). Helix only has a horizontal phone tab bar + tablet desktop tab bar (Android) and horizontal strips elsewhere.

`P2` · feasibility: `feasible-in-webview` · ~4 eng-weeks


**Why it matters.** Vertical tabs are the single most-requested power-user layout and now ship in Chrome, Edge, Brave, Firefox, and are Arc's entire identity. For tablet/desktop Helix (Android tablet, macOS, Windows, Linux) the horizontal strip collapses to favicons past ~20 tabs, which is precisely where vertical tabs win. Lacking it caps Helix's appeal to the heavy-tab segment that drives browser switching.


**Recommended architecture.** Pure UI/layout work over the existing TabManager model - no engine involvement. macOS (SwiftUI, cleanest target): add a VerticalTabSidebar view bound to WebViewModel.tabs/tabGroups/activeTabId, toggled by a layout pref, replacing the horizontal tab bar in ContentView when enabled; reuse existing context menu and group color logic. Windows (WinUI3): swap the horizontal tab StackPanel for a NavigationView/ItemsRepeater in a left pane. Android tablet: add a vertical RecyclerView variant of DesktopTabAdapter shown in a start-anchored drawer for the tablet desktop tab bar. Linux GTK: a Gtk.ListBox in a left Gtk.Paned. Driven by a new 'tabLayout' pref (horizontal/vertical).


**Implementation plan.**
1. Add a tabLayout pref (horizontal/vertical) to each platform's Prefs store.
1. macOS: build VerticalTabSidebar.swift (List of tabs grouped by TabGroup, active highlight, drag-reorder), conditionally swap it into ContentView's layout.
1. Wire existing tab context menu, group colors, pin ordering, and close affordances into the vertical row.
1. Windows/Linux/Android-tablet: implement the equivalent side container reusing each platform's existing tab model + adapters.
1. Add a settings toggle and persist the choice; restore on launch.
1. Handle responsive collapse (icon-only narrow sidebar) mirroring the existing >20-tab favicon-tier logic.

**Code example** — `macos/HelixBrowser/VerticalTabSidebar.swift`:

```swift
import SwiftUI

/// Left-edge vertical tab strip, driven entirely by the existing WebViewModel
/// (tabs / tabGroups / activeTabId). No engine changes - layout only.
struct VerticalTabSidebar: View {
    @ObservedObject var viewModel: WebViewModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 2) {
                ForEach(viewModel.tabGroups) { group in
                    Text(group.name)
                        .font(.caption).foregroundColor(.secondary)
                        .padding(.horizontal, 8).padding(.top, 6)
                    ForEach(viewModel.tabs.filter { $0.groupId == group.id }) { tab in
                        row(tab, accent: Color(hex: group.colorHex))
                    }
                }
                ForEach(viewModel.tabs.filter { $0.groupId == nil }) { tab in
                    row(tab, accent: nil)
                }
            }.padding(.vertical, 4)
        }
        .frame(width: 220)
        .background(.ultraThinMaterial)
    }

    @ViewBuilder
    private func row(_ tab: WebTab, accent: Color?) -> some View {
        HStack(spacing: 6) {
            if tab.isPinned { Image(systemName: "pin.fill").font(.caption2) }
            Text(tab.title.isEmpty ? tab.url : tab.title)
                .lineLimit(1).font(.system(size: 12))
            Spacer()
            Button { viewModel.closeTab(id: tab.id) } label: {
                Image(systemName: "xmark").font(.caption2)
            }.buttonStyle(.plain).opacity(0.6)
        }
        .padding(.horizontal, 8).padding(.vertical, 5)
        .background(tab.id == viewModel.activeTabId
                    ? Color.accentColor.opacity(0.18) : .clear)
        .overlay(alignment: .leading) {
            if let accent { Rectangle().fill(accent).frame(width: 3) }
        }
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .contentShape(Rectangle())
        .onTapGesture { viewModel.switchToTab(id: tab.id) }
        .contextMenu { TabRowMenu(viewModel: viewModel, tab: tab) }
    }
}
```

**Migration.** None - tabLayout is a new additive pref defaulting to horizontal, so existing users see no change until they opt in. No data model or persistence schema change.


**Testing.** macOS snapshot tests: sidebar renders grouped sections with correct active highlight and group accent bars; tapping a row calls switchToTab; close button calls closeTab. Manual: verify horizontal/vertical toggle persists across relaunch and that pinned tabs sort first. Static-review-only on macOS/Windows/Linux per the no-local-build constraint; do the reference implementation on whichever desktop target gets a build pipeline first.


#### Gap B1.4 — Tab Search is only fully reachable on Android (TabSwitcherActivity search box -> TabManager.searchTabs, TabManager.kt:439). macOS has model fields but no UI; iOS has searchTabs:171 but no caller; Windows/Linux have no tab search at all even though tab counts can be large.

`P2` · feasibility: `feasible-in-webview` · ~1 eng-weeks


**Why it matters.** Chrome/Edge/Brave/Arc all ship a Cmd/Ctrl+Shift+A tab search palette; it is the standard escape hatch once a window has many tabs. On Helix desktop (macOS/Windows/Linux) there is no way to jump to a tab by title/url, forcing horizontal scrolling through favicon-only tabs. It is the lowest-complexity (S) gap and a recognized table-stakes affordance.


**Recommended architecture.** macOS: bind a TabSearchOverlay SwiftUI sheet to the already-present WebViewModel.isTabSearchVisible/tabSearchQuery, add WebViewModel.filteredTabs, trigger via a Cmd+Shift+A NSMenuItem in the existing menu-bar builder. Windows (WinUI3): a search TextBox + ListView popup over the in-memory tab list in MainWindow. Linux (GTK): a Gtk.SearchEntry + Gtk.ListBox popover reusing tab_manager tab list. iOS: a UISearchController in the tab switcher backed by TabManager.searchTabs.


**Implementation plan.**
1. macOS: add filteredTabs computed property and wire the existing isTabSearchVisible/tabSearchQuery into a new TabSearchOverlay view with keyboard up/down + Enter to switch.
1. macOS: register a Cmd+Shift+A menu command toggling the overlay.
1. Windows/Linux: add a search popup over the tab list with the same activate-on-Enter behavior.
1. iOS: attach a UISearchController to the tab switcher calling TabManager.searchTabs.
1. Highlight matched substrings and show group/pin badges in results.

**Code example** — `macos/HelixBrowser/TabSearchOverlay.swift`:

```swift
import SwiftUI

/// Quick tab switcher (Cmd+Shift+A). Binds to the WebViewModel fields that
/// already exist but were never surfaced (isTabSearchVisible / tabSearchQuery).
struct TabSearchOverlay: View {
    @ObservedObject var viewModel: WebViewModel
    @FocusState private var focused: Bool

    private var results: [WebTab] {
        let q = viewModel.tabSearchQuery.lowercased()
        guard !q.isEmpty else { return viewModel.tabs }
        return viewModel.tabs.filter {
            $0.title.lowercased().contains(q) || $0.url.lowercased().contains(q)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            TextField("Tìm tab...", text: $viewModel.tabSearchQuery)
                .textFieldStyle(.plain).padding(10).focused($focused)
                .onSubmit { if let first = results.first { activate(first) } }
            Divider()
            List(results) { tab in
                HStack {
                    if tab.isPinned { Image(systemName: "pin.fill").font(.caption2) }
                    VStack(alignment: .leading) {
                        Text(tab.title.isEmpty ? tab.url : tab.title).lineLimit(1)
                        Text(tab.url).font(.caption).foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                    Spacer()
                    if let g = tab.groupName { Text(g).font(.caption2)
                        .foregroundColor(.secondary) }
                }
                .contentShape(Rectangle())
                .onTapGesture { activate(tab) }
            }
        }
        .frame(width: 460, height: 360)
        .onAppear { focused = true }
        .onExitCommand { dismiss() }
    }

    private func activate(_ tab: WebTab) {
        viewModel.switchToTab(id: tab.id); dismiss()
    }
    private func dismiss() {
        viewModel.isTabSearchVisible = false
        viewModel.tabSearchQuery = ""
    }
}
```

**Migration.** None - read-only over existing tab list and existing (unused) ViewModel fields. No persistence change.


**Testing.** macOS: unit-test the filter (title/url, case-insensitive, empty query returns all); snapshot the overlay; verify Cmd+Shift+A toggles isTabSearchVisible and Enter activates the top result via switchToTab. iOS: search controller filters to matching tabs. Static-review-only on non-Android targets.


#### Gap B1.5 — Tab Groups exist on Android (full UI) and macOS (context-menu UI) but groups cannot be COLLAPSED/EXPANDED and have no drag-to-reorder, and are entirely missing as a UI on iOS (model-only), Windows, and Linux (model stub fields GroupId/groupId only). There is also no save-group / group-restore as a named workspace.

`P2` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Collapsible groups are the core ergonomic of Chrome/Edge/Brave tab groups - without collapse, a group is just a colored label and does not reduce visual clutter, which is the whole point. And three of five platforms cannot make a group at all, so cross-platform parity is inconsistent: a user who groups tabs on macOS sees them ungrouped on iOS/Windows/Linux even though session/group data could sync later.


**Recommended architecture.** Add a collapsed: Boolean to TabGroup (Android TabManager.TabGroup, macOS/iOS TabGroup struct) and honor it in the switcher list builders: Android TabSwitcherActivity.buildItems already emits TabListItem.Header + TabItem rows (TabSwitcherActivity.kt:197-213); when a group is collapsed, emit only the Header with a count chip and skip member TabItems. Add a header tap-to-toggle. Surface the same group context menu on Windows (MainWindow tab strip) and Linux (tab_manager already has group fields) and wire the iOS context menu from gap #2. Persist collapsed in the existing group JSON.


**Implementation plan.**
1. Add collapsed flag to TabGroup on each platform and round-trip it in save/restore (Android JSON group object already serializes id/name/color/tabIds).
1. Android: in buildItems, when group.collapsed is true emit only the Header (with member count); make the header onHeaderClick toggle collapsed and call updateList.
1. Add a collapse chevron + member-count chip to the group header view in the adapter.
1. macOS: render collapsed groups as a single disclosure row in the (future vertical) sidebar / tab bar.
1. iOS/Windows/Linux: expose create/add/rename/recolor group UI (iOS via gap #2 menu; Windows/Linux via tab right-click) so groups are reachable everywhere.
1. Optional: a 'save group' action persisting a named group definition for later one-tap reopen (workspace-lite).

**Code example** — `android/app/src/main/java/com/helix/browser/ui/TabSwitcherActivity.kt`:

```kotlin
// In buildItems(): honor a collapsed flag on TabGroup so a collapsed group
// shows only its header chip and hides member tabs (Chrome-style).
private fun buildItems(): List<TabListItem> {
    val tabs = tabManager.tabs
    val filtered: List<BrowserTab> = if (searchQuery.isEmpty()) tabs
        else tabs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true)
        }
    val filteredIds = filtered.mapTo(HashSet()) { it.id }
    val items = mutableListOf<TabListItem>()

    for (group in tabManager.tabGroups) {
        val members = filtered.filter { it.groupId == group.id }
        if (members.isEmpty()) continue
        items.add(
            TabListItem.Header(
                groupId = group.id,
                name = group.name,
                color = group.color,
                count = group.tabIds.count { it in filteredIds },
                collapsed = group.collapsed
            )
        )
        // Searching force-expands so matches are never hidden.
        val showMembers = !group.collapsed || searchQuery.isNotEmpty()
        if (showMembers) {
            members.forEach { items.add(TabListItem.TabItem(it.copy(), group.color)) }
        }
    }

    val knownGroupIds = tabManager.tabGroups.mapTo(HashSet()) { it.id }
    filtered.filter { it.groupId == null || it.groupId !in knownGroupIds }
        .forEach { items.add(TabListItem.TabItem(it.copy(), null)) }
    return items
}

// Header tap toggles collapse and rebuilds the list.
private fun onGroupHeaderToggle(groupId: String) {
    tabManager.findGroup(groupId)?.let { it.collapsed = !it.collapsed }
    updateList()
}
```

**Migration.** Additive: TabGroup.collapsed defaults to false; the group JSON gains an optional 'collapsed' key read with optBoolean(...,false), so pre-existing persisted groups load expanded. TabListItem.Header gains a collapsed field (default false) for back-compat with any cached lists. No Room migration (groups live in SharedPreferences JSON).


**Testing.** Android (CI-verified): unit-test buildItems with a collapsed group asserts only the Header is emitted and member count is correct; assert searchQuery!="" force-expands. Instrumented: tap header toggles visibility and persists across saveTabs/restoreTabs. iOS/Windows/Linux: verify group create/rename/recolor reachable and that collapsed survives a relaunch (static review on non-Android).


---

## B2. Profile System  `[profile-system]`

**Overall feasibility:** partially-feasible


Helix has NO profile system on any of its five platforms — verified by direct code reading, not the inventory. The only multi-identity construct is a binary normal/incognito split: Android tabs/BrowserTab.kt:11, iOS BrowserTab.swift:10, macOS WebTab, Windows BrowserTab, and Linux tab_manager.py:17 each expose only `isIncognito`. AppDatabase.kt (v2) stores bookmarks/history with no profileId column, and Prefs/PrivacyManager are global SharedPreferences singletons (PrivacyManager.kt:64). There is no Profile/Account/Guest/Managed class, no sync, and no MDM/RestrictionsManager/DevicePolicy code anywhere (the lone 'work profile' hit at SettingsActivity.kt:395 is an unrelated RoleManager comment). Profile Isolation is only PARTIAL and notably weaker on Android: incognito there shares the global CookieManager jar and merely wipes per-WebView on teardown (clearIncognitoData, MainActivity.kt:2826-2827), whereas iOS (nonPersistent, BrowserViewController.swift:400), macOS (WebView.swift:62), Windows (IsInPrivateModeEnabled, MainWindow.xaml.cs:73) and Linux (WebContext.new_ephemeral, browser_window.py:355) use truly isolated/ephemeral stores. Overall feasibility is partially-feasible: multiple profiles, guest mode, and managed/MDM policy are buildable in a WebView shell as a product layer (Profile entity + ProfileManager threading profileId through Room/Prefs, per-profile WKWebsiteDataStore on iOS17+/CoreWebView2Environment/WebContext, RestrictionsManager-based PolicyManager), but TRUE per-profile cookie isolation on Android is platform-api-dependent — WebView.setDataDirectorySuffix is one-shot per process, forcing each profile/incognito into a separate process for engine-level cookie isolation. Profile Sync additionally depends on the (currently absent) account/sync backend, so it is the heaviest lift. The four most material gaps: Multiple Profiles (P1, ~10wk), Android Profile Isolation hardening (P1, ~4wk), Guest Mode (P2, ~3wk), and Managed Profiles/MDM (P2, ~4wk).


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Multiple Profiles (named user profiles, each with own bookmarks/history/cookies/settings) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | XL |
| Enterprise / Work Profiles (org-attached profile, SSO sign-in, managed identity) | ❌ missing | ✅ | ✅ | 🟡 | 🟡 | 🟡 | ❌ | med | XL |
| Profile Isolation (per-profile storage / cookie partitioning) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Sync Profiles (cross-device profile + its data) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | XL |
| Guest Mode (ephemeral, no-trace, no-profile-data session) | 🟡 partial | ✅ | ✅ | ✅ | ❌ | 🟡 | ❌ | med | M |
| Managed Profiles (MDM/policy-pushed configuration, restrictions) | ❌ missing | ✅ | ✅ | 🟡 | ✅ | ✅ | ❌ | med | L |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B2.1 — No multiple-profile system on any platform — only a single shared profile with a binary normal/incognito split. Verified: Android tabs/BrowserTab.kt:11 carries only `isIncognito`; AppDatabase.kt:13 has bookmarks/history tables with NO profileId column; PrivacyManager/Prefs are global SharedPreferences singletons (PrivacyManager.kt:64 PreferenceManager.getDefaultSharedPreferences). iOS BrowserTab.swift:10 / macOS WebTab / Windows BrowserTab / Linux tab_manager.py:17 all expose only `isIncognito`. No `profile`/`account`/`guest`/`managed` class exists anywhere (grep returned only incognito + an unrelated work-profile comment in SettingsActivity.kt:395).

`P1` · feasibility: `partially-feasible` · ~10 eng-weeks


**Why it matters.** Profiles are a flagship-browser table-stakes feature (work vs personal, shared family device, separate cookie/login identities). Without them, a shared device leaks one person's logins/history/bookmarks to all users, and a power user cannot keep work and personal Google/Microsoft logins side-by-side — a top reason users pick Chrome/Edge/Arc. This is the single largest competitive gap in this domain and is fully buildable in a WebView shell.


**Recommended architecture.** Add a Profile entity + ProfileManager singleton and thread a profileId through ALL persistence. Android: new data/Profile.kt (@Entity), bump AppDatabase to v3 adding profileId columns to bookmarks/history (with a migration backfilling profileId=defaultProfile), key SharedPreferences per-profile via context.getSharedPreferences("helix_prefs_"+profileId,...), and crucially give each profile its own WebView data directory via WebView.setDataDirectorySuffix(profileId) at process start (process-global, so a profile switch must restart the process or use a profile-pinned process — document this engine constraint). iOS/macOS: map each profile to a distinct WKWebsiteDataStore (iOS17+ WKWebsiteDataStore(forIdentifier:) for true on-disk isolation; pre-17 falls back to nonPersistent or a single store) created in BrowserViewController.createWebView / WebView.swift:43, plus per-profile UserDefaults suite. Windows: per-profile CoreWebView2Environment with a distinct userDataFolder (already wraps default in App.xaml.cs:22). Linux: per-profile WebKit2.WebContext with its own data manager (analogous to the ephemeral context at browser_window.py:355).


**Implementation plan.**
1. Define Profile model (id, name, color/avatar, isGuest, isManaged) and a ProfileManager that owns the active profile and persists the profile list.
1. Android: migrate Room to v3 adding `profileId` to bookmarks+history with an additive Migration that backfills the default profile id; scope BookmarkDao/HistoryDao queries by profileId.
1. Make Prefs/PrivacyManager per-profile (suffix the SharedPreferences name with profileId) and route WebView storage via setDataDirectorySuffix(profileId) chosen at first WebView creation per process.
1. Build a profile switcher UI (avatar in toolbar -> bottom sheet) that lets the user create/rename/delete/switch profiles; switching restarts the WebView storage scope (process restart on Android due to setDataDirectorySuffix being one-shot).
1. Replicate per-profile WKWebsiteDataStore(forIdentifier:) on iOS/macOS and per-profile CoreWebView2Environment/WebContext on Windows/Linux.
1. Treat the existing incognito path as the always-available 'Guest/private' profile so it composes with the new system.

**Code example** — `android/app/src/main/java/com/helix/browser/data/Profile.kt`:

```kotlin
package com.helix.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,          // stable UUID; default profile = "default"
    val name: String,
    val colorHex: String = "#5B8DEF",
    val isManaged: Boolean = false,      // pushed by MDM, read-only settings
    val createdAt: Long = System.currentTimeMillis(),
)

// Migration 2 -> 3: additive, backfills the implicit default profile so all
// existing local bookmarks/history are owned by it (no data loss on upgrade).
// In AppDatabase.kt add:
//   val MIGRATION_2_3 = object : Migration(2, 3) {
//     override fun migrate(db: SupportSQLiteDatabase) {
//       db.execSQL("CREATE TABLE IF NOT EXISTS profiles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, colorHex TEXT NOT NULL, isManaged INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
//       db.execSQL("INSERT OR IGNORE INTO profiles VALUES ('default','Personal','#5B8DEF',0,${System.currentTimeMillis()})")
//       db.execSQL("ALTER TABLE bookmarks ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
//       db.execSQL("ALTER TABLE history ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
//     }
//   }
```

**Migration.** Additive Room migration v2->v3 that creates the profiles table, inserts a 'default' profile, and adds a NOT NULL profileId DEFAULT 'default' column to bookmarks+history so every existing row is owned by the default profile (zero data loss, fully backward compatible). SharedPreferences: on first launch of the new version, copy the existing global helix_prefs into the default profile's suffixed prefs. WebView storage: existing on-disk WebView data becomes the default profile's data (default profile uses no setDataDirectorySuffix, preserving the current directory).


**Testing.** Unit-test the Room v2->v3 migration with Room's MigrationTestHelper (assert default profile created and rows backfilled). Instrumented test: create profile B, add a bookmark/cookie in B, switch to default, assert it is NOT visible; assert per-profile WebView cookie isolation by logging into a test origin in B and confirming default profile sees no session. Manual cross-platform: verify per-profile WKWebsiteDataStore(forIdentifier:) keeps cookies separate on iOS17+, and CoreWebView2 userDataFolder isolation on Windows.


#### Gap B2.2 — Profile Isolation is only partial and, on Android specifically, weaker than peers. Android incognito does NOT use a separate cookie store — it shares the global CookieManager jar and merely wipes per-WebView data on teardown via clearIncognitoData (MainActivity.kt:2826-2827, mirrored pattern noted at :2090). By contrast iOS (BrowserViewController.swift:400 nonPersistent), macOS (WebView.swift:62), Windows (IsInPrivateModeEnabled, MainWindow.xaml.cs:73) and Linux (WebContext.new_ephemeral, browser_window.py:355) all use truly isolated/ephemeral stores.

`P1` · feasibility: `platform-api-dependent` · ~4 eng-weeks


**Why it matters.** On Android, an incognito session's cookies live in the same jar as normal browsing until teardown, so a crash or abrupt kill can leave private-session cookies behind, and concurrent normal+incognito tabs are not cookie-isolated at the engine level — a real privacy correctness gap versus the 'true in-private' guarantee the other four platforms already provide and versus every competitor.


**Recommended architecture.** Android System WebView does not expose multiple persistent cookie jars in-process (the cookie store is global to the process). The realistic fix is to host incognito (and each non-default profile) in a separate :incognito process with its own setDataDirectorySuffix, so the OS gives it an isolated cookie/storage directory. Add an IncognitoWebViewService (android:process=":incognito") or, more simply, gate the whole profile/incognito storage scope on setDataDirectorySuffix at process start and run incognito tabs in a dedicated process. Keep the current clearIncognitoData teardown as defence-in-depth.


**Implementation plan.**
1. Confirm the constraint: WebView.setDataDirectorySuffix must be called once before any WebView is created in a process, so true per-profile cookie isolation requires process separation on Android.
1. Introduce a dedicated :incognito process (multiprocess WebView host) whose WebView storage uses an ephemeral data directory suffix, giving incognito its own cookie jar.
1. Route all incognito tabs to the isolated process; keep normal tabs in the main process.
1. Retain clearIncognitoData on teardown as belt-and-suspenders; add a session-cookie-only removal (removeSessionCookies) on incognito-process death.
1. Document that iOS/macOS/Windows/Linux already meet this bar; Android is the one to harden.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/WebViewStorage.kt`:

```kotlin
package com.helix.browser.engine

import android.app.Application
import android.os.Process
import android.webkit.WebView

/**
 * Pins this PROCESS to a single WebView storage scope. Must run before any
 * WebView is instantiated (setDataDirectorySuffix is one-shot per process),
 * so each profile / incognito runs in its own process to get an isolated
 * cookie + storage directory — the only way Android WebView grants true
 * per-profile cookie isolation (the in-process cookie jar is global).
 */
object WebViewStorage {
    fun pinForCurrentProcess(app: Application) {
        val proc = currentProcessName(app)
        val suffix = when {
            proc.endsWith(":incognito") -> "incognito_" + Process.myPid() // ephemeral
            else -> null // default profile keeps the legacy (unsuffixed) directory
        }
        if (suffix != null) WebView.setDataDirectorySuffix(suffix)
    }

    private fun currentProcessName(app: Application): String =
        app.applicationContext.let { ctx ->
            ctx.packageManager.runCatching { }
            android.os.Build.VERSION.SDK_INT.let { /* use Application.getProcessName() on P+ */ }
            Application.getProcessName()
        }
}
```

**Migration.** No data migration needed for the default profile (it keeps the existing unsuffixed WebView directory, so all current cookies/storage are preserved). Incognito gains a fresh ephemeral directory each process launch — by design no carry-over. Coordinate with the Multiple-Profiles migration so the default profile's directory is never suffixed.


**Testing.** Instrumented test launching the :incognito process, logging into a test origin in incognito, then asserting the main-process CookieManager has no cookie for that origin (true isolation) and that killing the incognito process leaves no cookie behind. Regression-test that normal browsing cookies survive process restart unchanged.


#### Gap B2.3 — No Guest Mode as a distinct, discoverable user-facing state. The closest construct is incognito, but it is framed as private browsing, not as a 'someone else borrowed my device, leave no trace and touch none of my data' guest session (which Chrome/Edge/Brave expose as a separate top-level entry alongside profiles).

`P2` · feasibility: `feasible-in-webview` · ~3 eng-weeks


**Why it matters.** On shared/family/kiosk devices, Guest Mode is the safe handoff: the borrower sees none of the owner's bookmarks/history/logins and leaves nothing behind. Helix's incognito half-covers the 'leave nothing' part on iOS/macOS/Windows/Linux but does not present a guest identity and (on Android) is not cookie-isolated. Cheap to add once the Profile system exists, and a meaningful trust/UX win.


**Recommended architecture.** Model Guest as a special transient Profile (isGuest=true) that always uses an ephemeral data store (the existing nonPersistent/ephemeral plumbing) and is excluded from session restore, history, and the profile list persistence. Reuse: iOS BrowserViewController.createWebView(isIncognito), macOS WebView.swift, Linux WebContext.new_ephemeral, Windows IsInPrivateModeEnabled. Surface it in the new profile switcher as a top-level 'Guest' entry, and wipe its data on exit.


**Implementation plan.**
1. Add isGuest to the Profile model; a guest profile is never written to the profiles table and never restored.
1. Map guest sessions to the platform ephemeral data store already used by incognito.
1. Add a 'Guest window/session' entry to the profile switcher UI; entering it hides the owner's tabs/bookmarks/history.
1. On guest exit, destroy the ephemeral store and return to the previous profile.

**Code example** — `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift`:

```swift
// Guest mode reuses the ephemeral data-store plumbing already proven for
// incognito (see createWebView(isIncognito:) -> WKWebsiteDataStore.nonPersistent()).
// A guest session is an incognito session that ALSO hides the owner's data
// surfaces (bookmarks/history/tabs) for the duration of the borrow.
func enterGuestSession() {
    isGuestSession = true
    tabManager.hideOwnerTabs()                 // stash owner tabs, show none
    let guest = tabManager.createTab(isIncognito: true) // ephemeral store
    select(tab: guest)
    // Bookmarks/History views read `isGuestSession` and render empty.
}

func exitGuestSession() {
    // nonPersistent() store is discarded with its WebViews; nothing persisted.
    tabManager.closeGuestTabs()
    tabManager.restoreOwnerTabs()
    isGuestSession = false
}
```

**Migration.** None — guest data is ephemeral by definition and never persisted, so there is nothing to migrate and no back-compat concern. Must ensure guest sessions are excluded from the session-restore JSON already produced by TabManager (filter isGuest like the existing isIncognito filter at TabManager.swift:182 / WebViewModel.swift:89 / tab_manager.py:179).


**Testing.** UI test: enter guest, confirm owner bookmarks/history/tabs are not visible; browse + log into a site; exit guest; confirm zero residue (no cookie, no history row, owner tabs restored intact). Verify guest tabs are not written to session restore across an app kill.


#### Gap B2.4 — No Managed Profiles / enterprise policy support. There is zero MDM/policy code on any platform — no Android RestrictionsManager/getApplicationRestrictions, no DevicePolicy, no managed-configuration manifest, no Windows ADMX/registry policy read, no Apple Managed App Config. (The only 'work profile' string is an unrelated RoleManager comment at SettingsActivity.kt:395.)

`P2` · feasibility: `partially-feasible` · ~4 eng-weeks


**Why it matters.** Without managed configuration, Helix cannot be deployed by any organization that requires policy enforcement (forced settings, blocklists, locked-down homepage, disabled incognito). This blocks all enterprise/education distribution — a significant TAM exclusion versus Chrome/Edge/Firefox which all ship rich policy engines. It is buildable in a WebView shell but only covers product-layer policies (the engine-owned policies like site-isolation tuning cannot be exposed).


**Recommended architecture.** Add a PolicyManager that reads OS-delivered managed config and overrides Prefs/PrivacyManager values, marking them read-only in Settings. Android: RestrictionsManager.applicationRestrictions + a restrictions metadata XML; the values map to existing Prefs/PrivacyManager keys (e.g. force https_only_mode, lock search engine, disable incognito). iOS/macOS: read UserDefaults key 'com.apple.configuration.managed' (Managed App Config / MDM). Windows: read HKLM policy registry keys. Linux: a /etc/helix/policies.json file. A managed profile (isManaged=true in the Profile model) consumes these and disables user edits.


**Implementation plan.**
1. Define a canonical policy schema mapping to existing Prefs/PrivacyManager keys (homepage, search engine, https-only, block-incognito, blocklist URL).
1. Android: declare an app-restrictions XML and read RestrictionsManager.getApplicationRestrictions() at startup, listening for ACTION_APPLICATION_RESTRICTIONS_CHANGED.
1. Override the corresponding Prefs/PrivacyManager getters so policy values win and the Settings rows render disabled/locked.
1. Add platform readers: Apple Managed App Config (UserDefaults), Windows policy registry, Linux policy file.
1. Mark the active profile isManaged and gate destructive actions (delete profile, disable policy) behind it.

**Code example** — `android/app/src/main/java/com/helix/browser/policy/PolicyManager.kt`:

```kotlin
package com.helix.browser.policy

import android.content.Context
import android.content.RestrictionsManager

/**
 * Reads MDM-pushed application restrictions and surfaces them as read-only
 * overrides for existing Prefs / PrivacyManager keys. No engine-level policy
 * is attempted (site-isolation/sandbox are owned by Android System WebView);
 * only product-layer settings are enforceable in a WebView shell.
 */
object PolicyManager {
    data class Policy(
        val forcedHomepage: String?,
        val lockedSearchEngine: String?,
        val forceHttpsOnly: Boolean?,
        val incognitoDisabled: Boolean?,
    )

    fun load(context: Context): Policy {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val b = rm.applicationRestrictions // empty Bundle when unmanaged
        return Policy(
            forcedHomepage = b.getString("Homepage")?.takeIf { it.isNotBlank() },
            lockedSearchEngine = b.getString("SearchEngine")?.takeIf { it.isNotBlank() },
            forceHttpsOnly = if (b.containsKey("HttpsOnly")) b.getBoolean("HttpsOnly") else null,
            incognitoDisabled = if (b.containsKey("DisableIncognito")) b.getBoolean("DisableIncognito") else null,
        )
    }

    /** True when any key is set, i.e. the device is enrolled and managed. */
    fun isManaged(context: Context): Boolean =
        !(context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager)
            .applicationRestrictions.isEmpty
}
```

**Migration.** No stored-data migration. Policy values are layered ON TOP of existing Prefs at read time, so an unmanaged install behaves exactly as today (empty restrictions Bundle -> no overrides). When a device becomes managed, policy keys win and locked Settings rows disable; when un-enrolled, the user's own Prefs resume. Must ensure PrivacyManager getters consult PolicyManager first.


**Testing.** Use Android's TestDPC to push a restrictions Bundle and assert forced homepage/search-engine/https-only take effect and the Settings rows are disabled; assert ACTION_APPLICATION_RESTRICTIONS_CHANGED re-applies live. Assert an unmanaged device sees identical behavior to current builds (no regression).


---

## B3. Synchronization  `[synchronization]`

**Overall feasibility:** partially-feasible


VERIFIED GROUND TRUTH: Helix has ZERO synchronization capability across all five platforms. Confirmed by reading the data layer and grepping the whole tree: no backend, no account/sign-in, no OAuth, no CloudKit/iCloud (no .entitlements files in ios/), no Firebase, and no remote endpoint touching user data. The only network code is favicon fetch (MainActivity.kt:1729) and search-suggestion fetch (BrowserViewModel.kt:391). Storage is strictly local: Android Room 'helix_browser.db' v2 with only Bookmark+HistoryItem entities and no sync columns (AppDatabase.kt:11-14; Bookmark.kt uses an autoincrement Long PK + single timestamp, no syncId/lastModified/tombstone); iOS/macOS use UserDefaults+JSON ('Lightweight data manager using UserDefaults + JSON', CoreDataManager.swift:3); Windows/Linux use local SQLite. Android even EXCLUDES its DB and all SharedPreferences from cloud backup (backup_rules.xml:5-12), so there is not even an OS-level data-transfer story. Every competitor (Chrome, Edge, Brave, Firefox, Safari, Arc) ships full multi-device sync; Brave/Firefox/Safari additionally ship zero-knowledge/E2E sync, which is the bar Helix's privacy positioning must meet. FEASIBILITY: partially-feasible. Sync touches only Helix-owned local data, NOT WebView/engine internals, so NO engine fork is required for bookmarks/history/tabs/settings/passwords — all buildable in the native shell. The blocker is product/infra: a backend service does not exist and must be built. Extensions sync is genuinely out of scope (no WebExtension execution engine in a WebView shell — that would require an engine fork, so there is nothing to sync). RECOMMENDED SEQUENCE: (1) make the schema sync-ready now via an additive, non-destructive MIGRATION_2_3 adding syncId/lastModified/deleted (cheap, gates everything, ~2 wks); (2) build AccountManager + auth (~4 wks); (3) design E2E zero-knowledge IN from day one (XChaCha20-Poly1305 + Argon2id master key in Keystore/Keychain + recovery mnemonic, ~6 wks); (4) build the sync engine and ship Bookmarks+Settings+Tabs first, History behind explicit opt-in, Passwords only after a secure credential store exists (ABSENT everywhere today), Extensions never (~14 wks). Priorities: P1 for sync capability and E2E (privacy-brand-critical), P2 for the schema and account prerequisites that must land first.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Bookmarks sync | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| History sync | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | L |
| Open tabs / session sync (continue-on-other-device, recently-closed) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Settings / preferences sync (search engine, privacy toggles, homepage) | ❌ missing | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ | med | M |
| Password / credential sync | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | XL |
| Extensions sync (installed extensions + settings) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ❌ | 🟡 | low | XL |
| End-to-end encryption of synced data | ❌ missing | 🟡 | 🟡 | ✅ | ✅ | ✅ | 🟡 | **CRIT** | L |
| Zero-knowledge architecture (server cannot read user data) | ❌ missing | 🟡 | ❌ | ✅ | ✅ | ✅ | ❌ | **high** | L |
| Account system / sign-in (prerequisite for all sync) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B3.1 — No synchronization backend or account system exists at all. Every platform stores 100% locally: Android Room DB 'helix_browser.db' (AppDatabase.kt:49, entities Bookmark+HistoryItem only, v2, no sync columns), iOS/macOS UserDefaults+JSON (CoreDataManager.swift:3,13 'Lightweight data manager using UserDefaults + JSON'), Windows/Linux SQLite. The only HTTP in the app is favicon fetch (MainActivity.kt:1729) and search-suggestion fetch (BrowserViewModel.kt:391). Android explicitly EXCLUDES the DB from cloud backup (backup_rules.xml:9). No account, sign-in, OAuth, CloudKit, Firebase, or remote endpoint exists.

`P1` · feasibility: `partially-feasible` · ~14 eng-weeks


**Why it matters.** Sync is the #1 retention and switching-cost feature of a modern browser. Without it a user who installs Helix on their phone cannot get their desktop bookmarks/tabs/history, so Helix can never become anyone's primary browser — it is structurally capped at 'try it and uninstall.' Every competitor ships full sync, giving them lock-in Helix entirely lacks. It also blocks the password-manager and passkey roadmap, which are useless without cross-device sync.


**Recommended architecture.** Build the sync layer ENTIRELY in the native shell (no engine fork — sync touches Helix-owned local data, not WebView internals). New zero-knowledge 'Helix Sync Service' storing only opaque encrypted blobs keyed by (accountId, collection, recordId, version). New Android package data/sync/: SyncEngine.kt (orchestrator), SyncApi.kt (Retrofit/OkHttp), SyncCrypto.kt (libsodium E2E), BookmarkSyncAdapter.kt/HistorySyncAdapter.kt/SettingsSyncAdapter.kt, AccountManager.kt. Extend Room via MIGRATION_2_3 adding syncId/lastModified/deleted to Bookmark and HistoryItem (AppDatabase.kt). Mirror on iOS/macOS (SyncEngine.swift + extend CoreDataManager JSON), Windows (DatabaseManager.cs), Linux (database.py). Use BookmarkRepository as the single local read/write+merge API. Ship Bookmarks+Settings+Tabs first; defer Passwords to the credential-manager workstream; never attempt Extensions sync (no extension engine exists in a WebView shell).


**Implementation plan.**
1. Phase 0 — schema readiness: add syncId(UUID, unique), lastModified(epoch ms), deleted(tombstone) to every syncable record; on Android add MIGRATION_2_3 bumping AppDatabase to v3 with additive ALTER TABLEs (mirror MIGRATION_1_2) and backfill legacy rows.
1. Phase 1 — account + key derivation: AccountManager (email+password or federated). Derive sync master key client-side via Argon2id from a passphrase (never sent to server); store in Android Keystore / iOS Keychain. Server stores only an independent auth verifier.
1. Phase 2 — crypto: SyncCrypto seals each record's JSON with XChaCha20-Poly1305 under a per-collection HKDF subkey; server receives only ciphertext+nonce+syncId+version.
1. Phase 3 — protocol: Bookmarks-first delta sync — pull records with serverVersion>lastSynced, decrypt, three-way merge into Room (last-writer-wins per lastModified, tombstones for deletes), then push local changes through BookmarkRepository.
1. Phase 4 — expand: add HistorySyncAdapter and SettingsSyncAdapter (settings smallest/lowest-risk), then Tabs/session reusing TabManager's existing SharedPreferences JSON session payload.
1. Phase 5 — UX: add a Sync section to SettingsActivity (sign-in, 'Sync now', per-collection toggles, recovery key, sign-out-and-wipe). Run via WorkManager periodic job + on-foreground.
1. Phase 6 — passwords/passkeys only after a secure credential store exists (ABSENT today); separate XL workstream. Extensions sync: out of scope (no WebExtension engine).

**Code example** — `android/app/src/main/java/com/helix/browser/data/sync/BookmarkSyncAdapter.kt`:

```kotlin
package com.helix.browser.data.sync

import com.helix.browser.data.Bookmark
import com.helix.browser.data.BookmarkRepository
import kotlinx.coroutines.flow.first

/**
 * Serializes/merges the Bookmark collection for the Helix Sync Service. The
 * server only ever receives ciphertext from [crypto]; plaintext bookmarks never
 * leave the device. Merge is last-writer-wins on syncId with tombstones.
 */
class BookmarkSyncAdapter(
    private val repo: BookmarkRepository,
    private val crypto: SyncCrypto
) {
    companion object { const val COLLECTION = "bookmarks" }

    suspend fun collectLocalChanges(since: Long): List<EncryptedRecord> =
        repo.getAllBookmarks().first()
            .filter { it.lastModified > since }
            .map { b ->
                val sealed = crypto.seal(COLLECTION, SyncJson.encode(b))
                EncryptedRecord(b.syncId, COLLECTION, sealed.nonce, sealed.ciphertext, b.deleted, b.lastModified)
            }

    suspend fun applyRemote(records: List<EncryptedRecord>) {
        for (rec in records) {
            val remote = SyncJson.decodeBookmark(crypto.open(COLLECTION, rec.nonce, rec.ciphertext))
            val local = repo.getBySyncId(remote.syncId)
            if (local != null && local.lastModified >= remote.lastModified) continue
            if (remote.deleted) local?.let { repo.removeBookmark(it) } else repo.upsertFromSync(remote)
        }
    }
}
```

**Migration.** All sync columns land via the additive MIGRATION_2_3 (see the schema gap) — no destructive migration, every existing bookmark/history row is preserved and assigned a fresh syncId. First sync after sign-in is a clean one-way export of local data to the server, so existing users lose nothing. Sync is strictly opt-in: a user who never signs in keeps a byte-identical local-only experience. On sign-out the choice is keep-local vs wipe-local. Merge conflicts resolve last-writer-wins per record via lastModified; tombstones (deleted=true) prevent resurrection of deleted items across devices.


**Testing.** Unit-test the merge logic with two simulated clients (add/edit/delete on each, assert convergence and no duplicate/resurrected rows). Round-trip property test: seal->open returns the original Bookmark JSON. Integration test against a staging Sync Service: register -> push bookmarks from client A -> pull on client B -> assert tree equality including folders. Conflict tests: concurrent edit of the same bookmark on two devices resolves to the newer lastModified. Tombstone test: delete on A propagates to B and survives a re-sync. Android is the only CI-verifiable platform (lintDebug+unit+assemble), so land and gate the merge/crypto unit tests there first.


#### Gap B3.2 — No end-to-end encryption / zero-knowledge design. Because no sync exists yet, E2E must be designed in from day one. The risk: a naive 'store bookmarks/history on our server' sync would make Helix a privacy liability that contradicts its heavy privacy positioning (anti-fingerprinting, tracker blocking, HTTPS-only). History sync in particular is a complete browsing-behavior dossier.

`P1` · feasibility: `feasible-in-webview` · ~6 eng-weeks


**Why it matters.** Helix markets itself on privacy. Server-readable sync of history/passwords would be a reputational and GDPR/CCPA catastrophe and would put Helix BEHIND Brave/Firefox/Safari, which all offer zero-knowledge sync. E2E is the differentiator that extends Helix's privacy story to the cloud; getting it wrong is worse than not shipping sync.


**Recommended architecture.** SyncCrypto.kt (Android) / SyncCrypto.swift (Apple) / SyncCrypto.cs (Windows) / sync_crypto.py (Linux). Master key derived from a user passphrase via Argon2id, stored ONLY in Android Keystore / iOS+macOS Keychain (hardware-backed where available), never transmitted. Per-collection subkeys via HKDF. Per-record sealing with XChaCha20-Poly1305 (libsodium). Server stores opaque {syncId, collection, nonce, ciphertext, version} plus an auth verifier cryptographically independent of the encryption key. A BIP39-style recovery mnemonic enables recovery after device loss. The WebView is never involved, so this is fully native-shell feasible with no engine dependency.


**Implementation plan.**
1. Pick one audited crypto lib per platform: lazysodium-android on Android, swift-sodium on Apple, libsodium P/Invoke or NSec on Windows, PyNaCl on Linux — never roll custom crypto.
1. Implement key hierarchy: passphrase -> Argon2id -> masterKey (Keystore/Keychain) -> HKDF -> per-collection subkeys. Wrap masterKey under a passphrase-derived KEK; store wrapped key + recovery mnemonic.
1. Implement seal()/open() (XChaCha20-Poly1305, fresh 24-byte nonce per record), authenticating (collection||syncId) as associated data to prevent record-confusion.
1. Make the server contract carry only ciphertext+nonce+version+tombstone; add a contract test asserting no plaintext field is ever accepted.
1. Add a key-rotation path (re-encrypt all records under a new subkey) and an explicit 'forgot passphrase = lose synced data' UX warning.
1. Add a recovery-key export screen requiring confirmation before first sync.

**Code example** — `android/app/src/main/java/com/helix/browser/data/sync/SyncCrypto.kt`:

```kotlin
package com.helix.browser.data.sync

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.utils.Key

/**
 * Zero-knowledge record sealing. The master key lives only in Android Keystore
 * (via [KeyVault]); the Helix Sync Service receives nothing but [seal] outputs.
 * XChaCha20-Poly1305, fresh nonce per record, collection bound as AAD.
 */
class SyncCrypto(private val keyVault: KeyVault) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    data class Sealed(val nonce: ByteArray, val ciphertext: ByteArray)

    private fun subkey(collection: String): Key = keyVault.deriveSubkey(collection)

    fun seal(collection: String, plaintext: ByteArray): Sealed {
        val nonce = sodium.nonce(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher = ByteArray(plaintext.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        check(sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            cipher, longArrayOf(cipher.size.toLong()), plaintext, plaintext.size.toLong(),
            collection.toByteArray(), collection.length.toLong(), null, nonce, subkey(collection).asBytes
        )) { "AEAD encrypt failed" }
        return Sealed(nonce, cipher)
    }

    fun open(collection: String, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val plain = ByteArray(ciphertext.size - AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        check(sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            plain, longArrayOf(plain.size.toLong()), null, ciphertext, ciphertext.size.toLong(),
            collection.toByteArray(), collection.length.toLong(), nonce, subkey(collection).asBytes
        )) { "AEAD decrypt/auth failed" }
        return plain
    }
}
```

**Migration.** No data migration needed — E2E is a new layer over the already-migrated schema. Back-compat concern is forward-only: define a wire format version byte prepended to every ciphertext so future cipher/KDF changes can be decoded. Since the architecture is zero-knowledge from v1, there is no plaintext-server-to-E2E migration to manage (unlike Chrome's historical move). A passphrase change triggers re-wrapping the master key only (cheap); a key rotation re-encrypts all records (batched background job).


**Testing.** Known-answer tests against libsodium test vectors for XChaCha20-Poly1305 and Argon2id. Tamper test: flipping any ciphertext/nonce/AAD byte must fail open() (authentication). Cross-platform interop test: seal on Android, open on iOS/macOS/Windows/Linux with the same key to prove identical wire format. Negative test asserting the network payload contains no plaintext substring of a known bookmark title. Key-loss test: wrong passphrase cannot derive a working subkey.


#### Gap B3.3 — No schema is sync-ready: every syncable entity lacks a stable cross-device id, a change timestamp, and a tombstone. Bookmark.kt uses an autoGenerate Long PK (id) and a single timestamp; HistoryItem is similar. Autoincrement Longs collide across devices and deletes leave no trace, so naive sync would duplicate or resurrect records. iOS/macOS JSON and Windows/Linux SQLite share the gap.

`P2` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Without per-record syncId + lastModified + tombstones, any sync implementation will silently corrupt the user's bookmark tree (duplicate folders, resurrected deletes, lost edits). This foundational data-model work gates ALL sync collections; shipping sync on the current schema would generate support-destroying data-loss bugs. Doing it now (additive, non-destructive) is cheap; retrofitting after sync ships is extremely painful.


**Recommended architecture.** Extend Bookmark.kt and HistoryItem.kt with syncId:String (UUID, unique index), lastModified:Long, deleted:Boolean; add MIGRATION_2_3 in AppDatabase.kt (v3) with additive ALTER TABLEs and one-time backfill. Add getBySyncId / upsertFromSync to BookmarkRepository and a tombstone-aware delete. Make BookmarksHtml import assign fresh syncIds. Mirror on iOS/macOS CoreDataManager JSON records and Windows DatabaseManager.cs / Linux database.py. Keep id as the local PK (existing UI keyed on id untouched); use syncId only for the sync join.


**Implementation plan.**
1. Add the three fields to Bookmark.kt and HistoryItem.kt with safe defaults (syncId via randomUUID at insert, lastModified=timestamp, deleted=false).
1. Write MIGRATION_2_3 mirroring MIGRATION_1_2's additive style: ALTER TABLE adds the columns, then UPDATE backfills syncId=hex(randomblob(16)) and lastModified for legacy rows; create a UNIQUE index on syncId.
1. Bump @Database version to 3 and append MIGRATION_2_3 to ALL_MIGRATIONS; keep fallbackToDestructiveMigrationOnDowngrade only.
1. Convert delete paths for synced data to soft-delete (deleted=true + bump lastModified) with periodic hard-purge of old tombstones; keep hard delete for never-synced/incognito data.
1. Replicate the field additions in iOS/macOS/Windows/Linux record models so the wire format is identical across platforms.

**Code example** — `android/app/src/main/java/com/helix/browser/data/AppDatabase.kt`:

```kotlin
// v2 -> v3: make bookmarks & history sync-ready. Additive only — no row is
// rewritten destructively. Adds a stable cross-device id, a change clock and a
// tombstone, then backfills legacy rows so first sync is a clean export.
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("bookmarks", "history")) {
            db.execSQL("ALTER TABLE $table ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $table ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE $table SET syncId = lower(hex(randomblob(16))), " +
                    "lastModified = COALESCE(timestamp, strftime('%s','now')*1000) WHERE syncId = ''"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_syncId ON $table(syncId)")
        }
    }
}

private val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
```

**Migration.** This IS the migration work. It is purely additive (ALTER TABLE ADD COLUMN + UPDATE backfill + CREATE INDEX), matching the existing safe MIGRATION_1_2 pattern, so it runs instantly even on large bookmark sets and rewrites no user data. exportSchema=true is already set, so the generated schema JSON gives Room a migration test fixture. Downgrades remain destructive-only (unchanged). iOS/macOS JSON records gain the fields with default-on-read fallbacks so old persisted blobs deserialize without loss.


**Testing.** Use Room's MigrationTestHelper to open a v2 DB with seeded bookmarks/history, run MIGRATION_2_3, and assert: row counts unchanged, every row has a non-empty unique syncId, lastModified populated, the UNIQUE index exists. Extend the existing test dir (android/app/src/test/.../data, alongside BookmarksHtmlTest). Add a fuzz test inserting then soft-deleting a bookmark and asserting it is excluded from getAllBookmarks but retained as a tombstone for sync.


#### Gap B3.4 — No account/sign-in surface anywhere. SettingsActivity (Android) and the iOS/macOS/Windows/Linux settings screens have no account section, and there is no auth client — no OAuth, no email/password, no token storage. Sync cannot exist without identity.

`P2` · feasibility: `partially-feasible` · ~4 eng-weeks


**Why it matters.** Account+identity is the gating dependency for sync and for any cross-device premium entitlement (today premium is a local Play-billing entitlement only, BillingManager.kt, so it cannot follow a user to a second device). This is a relatively small, self-contained item that unblocks the entire sync roadmap, so it should land first.


**Recommended architecture.** AccountManager.kt + a small AuthApi (OkHttp/Retrofit) against the Helix Sync Service auth endpoint; tokens in Keystore-backed EncryptedSharedPreferences. New 'Account & Sync' row + AccountActivity launched from SettingsActivity (mirror its existing activity-launch pattern). Reuse the signature-verification rigor already in BillingManager.kt for token validation. Optionally federate Play/Apple sign-in to reduce password handling. WebView uninvolved (no engine constraint). 'Partially-feasible' only because it requires standing up a backend that does not exist today.


**Implementation plan.**
1. Stand up the Helix Sync Service auth + opaque-blob store. Endpoints: POST /auth/register, /auth/login (short-lived access + refresh token), /sync/{collection} pull/push.
1. Add AccountManager.kt: register/login/logout, token refresh, Keystore-backed token storage via EncryptedSharedPreferences.
1. Wire an 'Account & Sync' entry into SettingsActivity launching AccountActivity (sign-in form + recovery-key display + per-collection toggles + sign-out-and-wipe).
1. Bind sync enablement to a verified account; gate History sync behind explicit opt-in (privacy-sensitive).
1. Mirror sign-in UI on iOS SettingsViewController, macOS SettingsView, Linux settings_dialog.py (Windows needs a Settings screen built first — it has none).
1. Add integration tests against a staging Sync Service (register -> login -> push -> pull on a second simulated client).

**Code example** — `android/app/src/main/java/com/helix/browser/data/sync/AccountManager.kt`:

```kotlin
package com.helix.browser.data.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Owns Helix Sync identity. Access/refresh tokens persist only in Keystore-backed
 * EncryptedSharedPreferences; the sync encryption key is handled by [KeyVault]
 * and is NEVER sent to the server.
 */
class AccountManager(context: Context, private val api: AuthApi) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "helix_account",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isSignedIn: Boolean get() = prefs.getString("refresh", null) != null

    suspend fun signIn(email: String, password: String) {
        val tokens = api.login(email, password)
        prefs.edit().putString("access", tokens.access).putString("refresh", tokens.refresh).apply()
    }

    fun signOutAndWipe(keyVault: KeyVault) {
        prefs.edit().clear().apply()
        keyVault.clear()
    }

    suspend fun freshAccessToken(): String {
        val refresh = prefs.getString("refresh", null) ?: error("not signed in")
        val access = api.refresh(refresh)
        prefs.edit().putString("access", access).apply()
        return access
    }
}
```

**Migration.** No local-data migration — account is net-new. Back-compat: the app must run fully signed-out (current behavior) so existing users are unaffected until they opt in. Tokens are stored under a new EncryptedSharedPreferences file ('helix_account'), separate from existing prefs, and are excluded from cloud backup (extend backup_rules.xml to also exclude it). Sign-out offers keep-local vs wipe-local so no data is lost on logout.


**Testing.** Unit-test token persistence/refresh with a faked AuthApi (login stores tokens, refresh rotates access, signOutAndWipe clears both + calls KeyVault.clear). Instrumented test that EncryptedSharedPreferences round-trips on a real Keystore. Integration test against staging: register -> login -> authenticated /sync call -> 401 after sign-out. UI test that the Settings 'Account & Sync' row launches AccountActivity and reflects signed-in/out state.


---

## B4. Password Manager  `[password-manager]`

**Overall feasibility:** partially-feasible


Verified against the real codebase: the entire Password Manager domain is ABSENT across all five Helix platforms. The only password-adjacent code is HTTP Basic/Digest realm auth stored in Android's WebViewDatabase (HelixWebViewClient.kt:318-355) - which is network auth, not HTML form login - plus android settings.savePassword=false (HelixWebView.kt:210, also a no-op since API18) and TLS-only URLCredential(trust:) on iOS/macOS (BrowserViewController.swift:655, WebView.swift:345). Windows and Linux have zero references. No androidx.credentials dependency, no FIDO, no assetlinks.json, no keychain credential items, no WebAuthn/passkey code anywhere. Feasibility split: credential storage, password generation, password health, and breach detection are all PRODUCT-layer and fully buildable in a WebView shell via JS form-detection bridges + native encrypted vaults (Android Keystore / iOS+macOS keychain / Windows Credential Manager / Linux libsecret) - these are the real, addressable gaps. WebAuthn/FIDO2/passkeys are ENGINE/OS-owned: Android System WebView already bridges navigator.credentials to Credential Manager (so Android is near-parity with config work), but WKWebView does NOT expose system passkey UI to embedded web content, making true in-WebView passkeys on iOS/macOS impossible without delegating to ASWebAuthenticationSession/Safari - a documented WebKit boundary, not something Helix can reimplement without an engine fork. Recommended sequencing: P1 credential storage+generation+fill on Android first (only CI-verifiable platform), then port to keychain platforms; P2 passkey capability-probe + system-agent fallback; P3 k-anonymity breach detection layered on top of storage. Estimated ~12 engineer-weeks for the material P1-P3 work, with FIDO2 roaming-authenticator support explicitly out of scope (engine-owned).


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Credential storage (save/fill HTML login forms) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Password generation (strong-password suggestion on signup) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | M |
| Password health (weak/reused/old audit dashboard) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | med | M |
| Breach / leak detection (compromised-credential alerts) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | **high** | M |
| Passkeys (create/use discoverable WebAuthn credentials, sync) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ✅ | 🟡 | **high** | XL |
| WebAuthn (navigator.credentials API for 2FA security keys) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| FIDO2 / CTAP (external roaming authenticators - USB/NFC/BLE keys) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | XL |

### ⚖️ Verifier corrections (adversarial re-check vs code) — confidence: high

- **WebAuthn (navigator.credentials API for 2FA security keys)**: `partial` → **`missing`** — Zero WebAuthn code in the entire repo: grep for navigator.credentials/PublicKeyCredential/isUserVerifyingPlatform across all .kt/.swift/.cs/.py/.js/.html returns no hits (exit 1). The only 'credential' code is HTTP Basic/Digest auth in android/.../engine/HelixWebViewClient.kt:221-355 (onReceivedHttpAuthRequest), unrelated to navigator.credentials. iOS/macOS use WKWebView (macos/HelixBrowser/WebView.swift:35,65) which does not expose the WebAuthn API; no experimental WKPreferences enabling it. Android uses stock android.webkit.WebView (HelixWebView.kt:11,23) with no WebAuthn configuration. Helix contributes nothing -> missing, not partial.
- **FIDO2 / CTAP (external roaming authenticators - USB/NFC/BLE keys)**: `partial` → **`missing`** — No CTAP/FIDO code anywhere (grep ctap|fido|securitykey returns no source hits). AndroidManifest.xml (lines 6-18) declares no USB-host or NFC uses-permission/uses-feature, which are required to drive USB/NFC roaming authenticators. No navigator.credentials plumbing exists on any platform. Helix provides no FIDO2/CTAP support -> missing, not partial.

### Gaps & remediation


#### Gap B4.1 — No credential storage / autofill of HTML login forms on ANY platform. Verified: android/.../engine/HelixWebView.kt:210 sets settings.savePassword=false (also a no-op since API18); HelixWebViewClient.kt:318-355 stores only HTTP Basic/Digest realm credentials in WebViewDatabase (network auth, not form login); iOS BrowserViewController.swift:655 and macOS WebView.swift:345 only build URLCredential(trust:) for TLS server-trust; Windows/Linux have zero credential references. There is no save-password prompt, no encrypted vault, no form fill.

`P1` · feasibility: `feasible-in-webview` · ~6 eng-weeks


**Why it matters.** Login is the single most-repeated browser interaction. Every flagship competitor saves and fills passwords; a browser that silently refuses to remember any login feels broken to a returning user and is a top-3 reason users abandon a new browser on day one. It also pushes users toward weaker, memorable, reused passwords (security regression) since Helix offers no generation either. This is the largest product-layer parity hole in the Password Manager domain and is fully buildable without an engine fork.


**Recommended architecture.** Add a PasswordManager subsystem per platform. Android: new data/credential package - CredentialDao + CredentialEntity in the existing Room AppDatabase (bump to v3, additive migration like the v1->v2 bookmark-folders migration), a CredentialVault wrapping Android Keystore (AES-GCM master key in StrongBox/TEE) for at-rest encryption, and a JS bridge (HelixWebView.addJavascriptInterface) injected by an AutofillScript that detects type=password inputs, posts form submissions to native for a save-prompt, and fills on page load. Surface a PasswordsActivity (mirror BookmarksActivity/HistoryActivity) for the vault UI, gated by BiometricPrompt. iOS/macOS: store items as kSecClassInternetPassword in the iOS/macOS keychain (the real native credential store WKWebView lacks), fill via WKUserScript + WKScriptMessageHandler, gate with LocalAuthentication. Windows: WebView2 exposes no autofill API, so a CredentialStore over Windows Credential Manager (CredWrite/CredRead) + an injected AddScriptToExecuteOnDocumentCreated fill script. Linux: libsecret-backed store + WebKitUserContentManager script.


**Implementation plan.**
1. Android first (only CI-verifiable platform): add CredentialEntity(host, username, encryptedPassword, iv, createdAt, lastUsed) + CredentialDao to AppDatabase, bump version, write an additive Migration_2_3.
1. Add CredentialVault using AndroidKeyStore (generateKey AES/GCM/NoPadding, setUserAuthenticationRequired optional) to encrypt/decrypt the password column so plaintext never hits SQLite.
1. Write AutofillScript.js injected at onPageFinished: scan for password fields, on submit postMessage(host, user, pass) to a @JavascriptInterface bridge; on load, if a saved credential matches the origin, request fill via biometric-gated callback.
1. Add a 'Save password?' Snackbar/dialog from MainActivity on the submit message (suppressed in incognito, mirroring existing savePassword=false incognito logic).
1. Build PasswordsActivity (list/search/edit/delete/reveal) reusing the BookmarksActivity pattern, entry gated by BiometricPrompt.authenticate.
1. Wire 'Clear browsing data' (PrivacyManager.kt:615 region) to optionally include the credential table.
1. Port the same shape to iOS/macOS keychain + LocalAuthentication and Windows Credential Manager / Linux libsecret once Android is validated.

**Code example** — `android/app/src/main/java/com/helix/browser/data/CredentialVault.kt`:

```kotlin
package com.helix.browser.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM at-rest encryption for saved-login passwords. The master key lives in
 * the AndroidKeyStore (TEE/StrongBox when available) and never leaves it, so the
 * Room `credentials.encryptedPassword` column is unreadable without the device.
 */
object CredentialVault {
    private const val KEY_ALIAS = "helix_credential_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    data class Sealed(val ciphertext: ByteArray, val iv: ByteArray)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun seal(plaintext: String): Sealed {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Sealed(c.doFinal(plaintext.toByteArray(Charsets.UTF_8)), c.iv)
    }

    fun open(ciphertext: ByteArray, iv: ByteArray): String {
        val c = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }
        return String(c.doFinal(ciphertext), Charsets.UTF_8)
    }
}
```

**Migration.** Additive only. Bump Room AppDatabase to v3 with a Migration(2,3) that CREATEs the credentials table (matching the existing non-destructive v1->v2 folder migration pattern); existing bookmarks/history rows untouched. The credential table is excluded from cloud backup (extend the existing fullBackupContent/dataExtractionRules exclusion already applied to DB+prefs). No legacy data to import (savePassword was always false, so no WebView password store exists to migrate). On iOS/macOS keychain items use kSecAttrAccessibleWhenUnlockedThisDeviceOnly so nothing silently syncs before sync is designed.


**Testing.** Unit-test CredentialVault seal/open round-trip + tamper (flipped ciphertext byte must throw AEADBadTagException). Instrument the AutofillScript with Robolectric/Espresso against a local login fixture to assert submit->save-prompt and load->fill. Add a CredentialDao Room test for upsert/dedup-by-(host,user) and that incognito tabs never write. Manually verify on a real device that BiometricPrompt gates the PasswordsActivity reveal and that 'Clear data' purges the table. Android is the only CI-verifiable platform (lint+unit+assembleRelease/R8), so gate merge on that.


#### Gap B4.2 — Passkeys (discoverable WebAuthn credentials) cannot be created/used by embedded web content on iOS/macOS WKWebView, and are unmanaged on the WebView2/WebKitGTK platforms - so passwordless sign-in is broken on most Helix platforms even though it is the industry direction.

`P2` · feasibility: `platform-api-dependent` · ~4 eng-weeks


**Why it matters.** Passkeys are now the default sign-in path being pushed by Google, Apple, Microsoft, GitHub, and major banks. A user who created a passkey in Safari/Chrome and then opens that site in Helix on iPhone will find the passkey button does nothing, because WKWebView does not surface the ASAuthorizationController/Credential Manager passkey UI to web content the way Safari does. That is a hard, visible failure on the exact accounts users care most about. On Android the System WebView DOES route WebAuthn to Credential Manager (so Helix is closer to parity there), which makes the cross-platform inconsistency itself a support and trust problem.


**Recommended architecture.** This is largely engine/OS-owned. Android: verify and, where needed, set WebSettingsCompat WEB_AUTHENTICATION support and ensure no setting suppresses navigator.credentials; add a Digital Asset Links assetlinks.json so the app's own origin can participate - mostly a config/verification task, NOT a reimplementation. iOS/macOS: WKWebView genuinely does not let an app inject passkey assertion UI for arbitrary web origins (security boundary owned by WebKit); the only honest options are (a) accept WebAuthn-via-WKWebView limitations and document them, (b) offer SFSafariViewController / ASWebAuthenticationSession for login flows that need passkeys (delegates to system Safari which CAN do passkeys), or (c) ship a constrained subset only for Helix's own first-party login. Do NOT claim a full in-WebView passkey authenticator - that requires WebKit-level CTAP integration Helix cannot provide. Add a PasskeyCapability probe class per platform that feature-detects and falls back gracefully (e.g., route to ASWebAuthenticationSession on iOS).


**Implementation plan.**
1. Android: write an instrumentation test that calls navigator.credentials.get on a known passkey test site inside HelixWebView to confirm the System WebView already bridges to Credential Manager; if it works, mark Android 'supported' with zero engine work and add assetlinks.json for first-party.
1. iOS/macOS: confirm via a WKWebView test that navigator.credentials.create returns NotAllowedError (expected), then implement an ASWebAuthenticationSession fallback path triggered when a navigation is a known login/passkey flow, so the user can complete sign-in in the system agent.
1. Add a PasskeyCapability probe + a user-facing 'Passkeys use your system on this device' notice rather than a silently dead button.
1. Document in the audit that full in-WebView passkey authenticator on WKWebView is out of scope (WebKit-owned).
1. Defer FIDO2 roaming-authenticator/CTAP work entirely - it is delivered by the host engine + OS and is not Helix-implementable.

**Code example** — `ios/HelixBrowser/HelixBrowser/Engine/PasskeyCapability.swift`:

```swift
import WebKit
import AuthenticationServices

/// WKWebView does not expose the system passkey UI (ASAuthorization) to embedded
/// web content the way Safari does, so navigator.credentials.create/get for
/// passkeys fails inside Helix. This probes the limitation and offers an
/// ASWebAuthenticationSession fallback that completes the flow in the system agent.
final class PasskeyCapability: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?

    /// Returns false on WKWebView: web-content-initiated passkey ceremonies are
    /// blocked by WebKit's security boundary. Callers should surface the fallback.
    var supportsInlinePasskeys: Bool { false }

    /// Hands a login URL to the system browser agent, which CAN use passkeys,
    /// then returns the callback URL to the caller (e.g. an OAuth/redirect login).
    func authenticate(loginURL: URL,
                      callbackScheme: String,
                      completion: @escaping (Result<URL, Error>) -> Void) {
        let s = ASWebAuthenticationSession(
            url: loginURL,
            callbackURLScheme: callbackScheme
        ) { url, error in
            if let url = url { completion(.success(url)) }
            else if let error = error { completion(.failure(error)) }
        }
        s.presentationContextProvider = self
        s.prefersEphemeralWebBrowserSession = false
        s.start()
        self.session = s
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        return UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first ?? ASPresentationAnchor()
    }
}
```

**Migration.** No data migration - this is capability detection plus a fallback flow. Back-compat: behavior is purely additive (a previously-dead passkey button now either works via System WebView on Android or routes to the system agent on iOS). No stored schema changes.


**Testing.** Android instrumentation test asserting navigator.credentials.get succeeds against a passkey demo origin inside HelixWebView (confirms the engine bridge). iOS/macOS unit test asserting supportsInlinePasskeys==false and that authenticate(loginURL:) starts an ASWebAuthenticationSession (mock the session). Manual end-to-end on a real iPhone signing into a passkey-enabled site via the fallback. Negative test: passkey button on iOS must show the system-agent notice, never silently fail.


#### Gap B4.3 — No breach / leak detection: Helix never checks saved or entered credentials against known-compromised datasets, so a user reusing a leaked password gets no warning.

`P3` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Chrome, Edge, Firefox, and Safari all proactively warn 'this password appeared in a data breach' - it is now an expected safety feature and a concrete reason users trust a browser with their logins. Without it (and without storage), Helix offers zero credential-safety value-add. Once credential storage exists (gap 1), breach detection is a cheap, high-trust addition using the k-anonymity HaveIBeenPwned range API, which never sends the full hash.


**Recommended architecture.** Add a BreachChecker that, on save and on a periodic vault scan, SHA-1s the stored password, sends only the first 5 hex chars of the hash to the HIBP range endpoint (k-anonymity - the full password/hash never leaves the device), and matches the suffix locally. Surface results in the PasswordsActivity health view alongside weak/reused detection computed entirely on-device. Reuse the existing networking the app already does for omnibox suggestions; gate behind a privacy toggle (mirror the existing privacy-toggle pattern in SettingsActivity).


**Implementation plan.**
1. After credential storage lands, add BreachChecker with the HIBP k-anonymity range lookup (send hash prefix only).
1. Compute weak/reused on-device by scanning the decrypted vault in memory (length/charset entropy + duplicate-password grouping by hash).
1. Add a 'Password Checkup' screen in PasswordsActivity showing compromised/reused/weak counts.
1. Add a privacy/settings toggle to enable online breach checks (off => only on-device weak/reused).
1. Show a non-blocking warning chip when a compromised credential is about to be filled.

**Code example** — `android/app/src/main/java/com/helix/browser/data/BreachChecker.kt`:

```kotlin
package com.helix.browser.data

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * k-anonymity breach lookup against the HaveIBeenPwned range API. Only the first
 * 5 hex chars of the SHA-1 are sent; the full hash/password never leaves device.
 */
object BreachChecker {
    /** @return number of times the password appeared in known breaches, 0 if clean. */
    fun timesPwned(password: String): Int {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
        val prefix = sha1.substring(0, 5)
        val suffix = sha1.substring(5)
        val conn = (URL("https://api.pwnedpasswords.com/range/$prefix").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Add-Padding", "true")
            connectTimeout = 8000
            readTimeout = 8000
        }
        return try {
            if (conn.responseCode != 200) return 0
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith(suffix, ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0 // fail open: a network error must not block login
        } finally {
            conn.disconnect()
        }
    }
}
```

**Migration.** No schema change required (operates on existing credential rows from gap 1). Optionally add a lastChecked/compromised flag column via another additive Room migration if you want to cache results. Off by default until the user opts in, so no behavior change for privacy-conscious users.


**Testing.** Unit test the hash-prefix splitting and suffix matching against a fixed mock range response (e.g. a known leaked password must return its count; a random strong one returns 0). Network-failure test must return 0 (fail open). Verify on-device weak/reused detection groups duplicates correctly. Manual test the Password Checkup screen counts. Must run off the main thread (reuse the existing background-executor pattern used for omnibox suggestions/history queries).


---

## B5. AI Features  `[ai-features]`

**Overall feasibility:** feasible-in-webview


Helix has ZERO AI capability today, verified directly against the code: a case-insensitive grep for ai/llm/gpt/openai/anthropic/claude/gemini/summar/translate/onnx/coreml/mediapipe across all five platforms (Android/iOS/macOS/Windows/Linux) returns only false positives (clear_cache_summary settings strings in SettingsActivity.kt:145; translatesAutoresizingMaskIntoConstraints in iOS layout files; a local variable named ai in macos/HistoryView.swift:113). There is no AI module, no model bundle, no inference client, and no on-device ML dependency anywhere. So every AI sub-feature (assistant, summarization, smart search, AI tab organization, translation, writing assistant, agent workflows; local/cloud/hybrid LLM) is MISSING.\n\nFeasibility is GOOD because none of this requires an engine fork. AI features are product-layer (Helix's responsibility), not engine-owned: they run as a native UI plus either a cloud API call or a platform on-device ML API, with the WebView used only to extract page text (via the existing ReaderMode JS pipeline) and inject results back. Critically, the plumbing already exists in spirit: BrowserViewModel.fetchEngineSuggestions (android/.../viewmodel/BrowserViewModel.kt:391) is a textbook off-main-thread, timeout-bounded, fail-soft HttpURLConnection call that an AiClient should copy almost verbatim, and BillingManager.kt:24 already has a verified, fail-closed subscription SKU (helix_premium_monthly) that is the natural metering hook for cloud inference.\n\nThe honest constraints: (1) Helix being a WebView shell does NOT inherit Chrome's Translate or Edge's Copilot - those are browser-app features on top of Blink, not WebView capabilities, so Helix must build its own. (2) Cloud inference must go through a Helix-operated proxy that holds the provider key and verifies the Play purchase token; a client-embedded key would leak. (3) On-device LLM is only realistic on Apple platforms (Translation framework today, Foundation Models on newer OSes) and via ML Kit translate on Android; the Android system WebView exposes no LLM, so chat/summarize on Android are cloud-only.\n\nRecommended sequencing: build the InferenceRouter + AiEntitlement model/monetization layer first (P1, 2wk, de-risks everything), then Page Summarization (P1, 4wk - highest ROI, reuses ReaderMode extraction), then AI Translation (P1, 5wk - Apple on-device is free/private; Android cloud), then the Assistant sidebar (P2, 6wk - the marquee surface, needs a Room migration for chat history). Smart search, AI tab organization, writing assistant, and agent workflows are lower priority and layer onto the same router. Verification asymmetry applies: only Android changes can be CI-verified in this repo; iOS/macOS lack an .xcodeproj so all Swift work is static-review-only.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| AI Assistant (sidebar chat over page/web) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ❌ | ✅ | **high** | XL |
| Page Summarization (TL;DR of current page) | ❌ missing | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ | **high** | L |
| Smart Search (AI answers / generative omnibox) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ❌ | ✅ | med | M |
| AI Tab Organization (auto-group / name tabs) | ❌ missing | ✅ | 🟡 | ❌ | ❌ | ❌ | ✅ | med | L |
| AI Translation (full-page / inline neural translate) | ❌ missing | ✅ | ✅ | 🟡 | ✅ | ✅ | 🟡 | **high** | L |
| AI Writing Assistant (compose/rewrite in text fields) | ❌ missing | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | med | L |
| AI Agent Workflows (multi-step browse/act) | ❌ missing | 🟡 | 🟡 | ❌ | ❌ | ❌ | 🟡 | low | XL |
| Local LLM (on-device inference) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ✅ | ❌ | med | XL |
| Cloud LLM (server API inference) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ❌ | ✅ | **high** | M |
| Hybrid LLM (route local vs cloud) | ❌ missing | ✅ | 🟡 | ✅ | ❌ | ✅ | ❌ | low | L |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B5.1 — No Page Summarization anywhere in the product. Verified zero references: grep for summar/translate/llm/ai/openai/anthropic/gemini/onnx/coreml/mediapipe across all five platforms returns only false positives (clear_cache_summary settings strings in SettingsActivity.kt:145; translatesAutoresizingMaskIntoConstraints in iOS view files; a local var named ai in macos/HistoryView.swift:113). There is no AI module, no model bundle, and no inference API client of any kind.

`P1` · feasibility: `feasible-in-webview` · ~4 eng-weeks


**Why it matters.** Page summarization is the single highest-ROI, lowest-cost AI feature and is now table-stakes: Edge (Copilot), Brave (Leo), Arc, and Firefox all ship a one-tap TL;DR. It is the most-used AI browsing action and the clearest paywall hook for Helix's existing premium SKU. Without it, Helix has zero AI surface while every flagship competitor has at least summarize. It also directly reuses the existing ReaderMode article-extraction pipeline, so the marginal build cost is small relative to user-visible value.


**Recommended architecture.** Add android/app/src/main/java/com/helix/browser/ai/AiClient.kt (a cloud LLM client modeled exactly on BrowserViewModel.fetchEngineSuggestions: off-main-thread HttpURLConnection with connect/read/overall timeouts, fail-soft). Add ai/AiConfig.kt (endpoint + model + provider enum, BYO-key field in Prefs). Add ai/PageContentExtractor.kt that reuses the ReaderMode JS extraction (engine/ReaderMode.kt) to pull clean article text via WebView.evaluateJavascript, capped to N tokens. Add ui/AiSummarySheet.kt (a BottomSheetDialog showing the loaded summary). Gate behind BillingManager.isPremium (billing/BillingManager.kt, PRODUCT_ID helix_premium_monthly). Wire a menu entry in MainActivity.kt. The Anthropic Messages API is a natural backend: POST to a Helix-operated proxy (never embed a provider key client-side) that forwards to the model.


**Implementation plan.**
1. Add a Helix-operated server proxy endpoint (e.g. https://ai.helix.app/v1/summarize) that injects the provider key server-side, enforces per-user rate limits, and verifies the Play purchase token sent from the client so only premium users consume quota.
1. Create PageContentExtractor.kt: inject the existing ReaderMode extraction JS into the current tab's HelixWebView via evaluateJavascript, returning sanitized article text + title + URL; truncate to a safe token budget (~12k chars).
1. Create AiClient.kt by copying the structure of BrowserViewModel.fetchEngineSuggestions (Dispatchers.IO, withTimeoutOrNull, HttpURLConnection, charset-aware read, fail-soft to a friendly error). POST JSON {url,title,text,task:'summarize'} with the Play purchase token in a header.
1. Create AiSummarySheet.kt (BottomSheetDialogFragment) with a loading spinner, the rendered summary (markdown-lite), a copy button, and a 'regenerate'/'longer' action.
1. Wire MainActivity overflow menu -> if BillingManager.isPremium grant, extract+call+show sheet; else route to launchSubscription(activity).
1. Add Prefs flag ai_enabled and an opt-in privacy notice (page text leaves the device) shown on first use; respect incognito by disabling the feature in private tabs.

**Code example** — `android/app/src/main/java/com/helix/browser/ai/AiClient.kt`:

```kotlin
package com.helix.browser.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud summarization client. Mirrors BrowserViewModel.fetchEngineSuggestions:
 * fully off the main thread, hard connect/read + overall timeouts, fail-soft.
 * The provider key lives ONLY on the Helix proxy; the client sends the verified
 * Play purchase token so the proxy can gate premium quota.
 */
object AiClient {
    private const val ENDPOINT = "https://ai.helix.app/v1/summarize"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val OVERALL_TIMEOUT_MS = 30_000L

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Error(val message: String) : Result
    }

    suspend fun summarize(
        pageUrl: String,
        title: String,
        articleText: String,
        purchaseToken: String,
    ): Result = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val payload = JSONObject().apply {
                    put("task", "summarize")
                    put("url", pageUrl)
                    put("title", title)
                    put("text", articleText)
                }.toString()
                conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Helix-Purchase-Token", purchaseToken)
                }
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode == 402) return@withContext Result.Error("Premium required")
                if (conn.responseCode != HttpURLConnection.HTTP_OK)
                    return@withContext Result.Error("Service unavailable")
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8)
                    .use(BufferedReader::readText)
                val summary = JSONObject(body).optString("summary").trim()
                if (summary.isEmpty()) Result.Error("Empty response") else Result.Success(summary)
            } catch (_: Exception) {
                Result.Error("Network error")
            } finally {
                try { conn?.disconnect() } catch (_: Exception) { /* ignore */ }
            }
        }
    } ?: Result.Error("Timed out")
}
```

**Migration.** No data migration needed; AiClient is additive and stateless. Add a Prefs boolean ai_enabled defaulting false (opt-in) so existing installs are unaffected. The proxy-token contract reuses the Purchase object BillingManager already verifies (fail-closed), so no new client-side secret is introduced and no schema/Room migration is required. Back-compat: feature simply hidden if isPremium is false or ai_enabled is off.


**Testing.** Unit test AiClient against a mock HttpURLConnection/local stub server for 200/402/timeout/malformed-JSON paths (assert fail-soft Error, never crash). Instrument PageContentExtractor on real pages (article, SPA, paywalled) to confirm bounded text and incognito disablement. Manual: premium-on vs free (must route to launchSubscription), offline (graceful Error), very long pages (truncation). Run the existing Android CI gate (lintDebug, unit tests, assembleRelease/R8) since Android is the only CI-verified platform.


#### Gap B5.2 — No AI Translation. The only translate hits are translatesAutoresizingMaskIntoConstraints in iOS layout code (e.g. DownloadsViewController.swift:32). There is no full-page translation, no inline neural translate, and no use of any platform translation API. Helix being a WebView shell does NOT get Chrome's built-in Translate for free, because that UI is a Chrome-browser feature layered on Blink, not a WebView capability the host exposes.

`P1` · feasibility: `platform-api-dependent` · ~5 eng-weeks


**Why it matters.** Translation is a daily-driver feature for a huge non-English audience and Helix's bundle is already shipping Vietnamese-localized (the macOS/iOS UI is hardcoded Vietnamese), implying a non-English user base that will expect to translate foreign pages. Chrome, Edge, Firefox, and Safari all ship full-page translate; its absence is a concrete reason a bilingual user keeps Chrome installed. On Apple platforms there is a fully on-device, free, private system API (the Translation framework, iOS 17.4+/macOS 14.4+) so iOS/macOS can ship this with NO server cost and NO privacy exposure.


**Recommended architecture.** Per-platform split. iOS/macOS: add Engine/PageTranslator.swift using Apple's Translation framework (TranslationSession) for on-device, free, private translation; trigger from a toolbar button in BrowserViewController.swift / WebView.swift. Android: no free on-device full-page translate API in the system WebView, so add ai/TranslationClient.kt (same HttpURLConnection-via-proxy pattern as AiClient) translating extracted text blocks, plus a JS pass over text nodes; optionally bundle ML Kit on-device translation models for offline language pairs. Reuse PageContentExtractor for text node enumeration.


**Implementation plan.**
1. iOS/macOS: detect page language (NLLanguageRecognizer), expose a 'Translate page' toolbar action, run TranslationSession.translate over visible text nodes harvested via WKWebView evaluateJavascript, write results back into the DOM; cache per-tab to avoid re-translating on scroll.
1. Android: add TranslationClient.kt routing to the Helix proxy (or ML Kit on-device packs for offline pairs); enumerate text nodes via injected JS, batch them, swap text in place, preserve layout.
1. Add a language-pair picker and an auto-detect-and-offer banner shown when page language != UI language.
1. Gate cloud translation (Android proxy path) behind premium quota; keep Apple on-device path free.
1. Respect incognito (Android cloud path disabled) and add a per-site 'never translate' pref.

**Code example** — `ios/HelixBrowser/HelixBrowser/Engine/PageTranslator.swift`:

```swift
import Foundation
import Translation
import NaturalLanguage
import WebKit

/// On-device, free, private full-page translation using Apple's Translation
/// framework (iOS 17.4+). No server, no key, no data leaves the device.
@available(iOS 17.4, *)
final class PageTranslator {

    /// Harvest visible text nodes, translate them, and write them back.
    func translatePage(in webView: WKWebView, to target: Locale.Language) async {
        let harvestJS = """
        (function(){
          const out=[]; const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
          let n, i=0; while((n=w.nextNode())){ const t=n.nodeValue.trim();
            if(t.length>1){ n.parentElement.setAttribute('data-hx-i', i);
              out.push({i:i, t:t}); i++; } }
          return JSON.stringify(out);
        })();
        """
        guard let raw = try? await webView.evaluateJavaScript(harvestJS) as? String,
              let data = raw.data(using: .utf8),
              let nodes = try? JSONDecoder().decode([TextNode].self, from: data),
              !nodes.isEmpty else { return }

        let source = detectLanguage(of: nodes.first?.t ?? "")
        let session = TranslationSession(installedSource: source, target: target)
        let requests = nodes.map {
            TranslationSession.Request(sourceText: $0.t, clientIdentifier: String($0.i))
        }
        guard let responses = try? await session.translations(from: requests) else { return }

        let escaped = responses.map { resp -> String in
            let safe = resp.targetText.replacingOccurrences(of: "\\", with: "\\\\")
                                      .replacingOccurrences(of: "'", with: "\\'")
            return "m['\(resp.clientIdentifier)']='\(safe)';"
        }.joined()
        let applyJS = "(function(){const m={}; \(escaped) " +
            "document.querySelectorAll('[data-hx-i]').forEach(e=>{" +
            "const k=e.getAttribute('data-hx-i'); if(m[k]!==undefined) e.textContent=m[k];});})();"
        _ = try? await webView.evaluateJavaScript(applyJS)
    }

    private func detectLanguage(of text: String) -> Locale.Language {
        let r = NLLanguageRecognizer(); r.processString(text)
        return Locale.Language(identifier: r.dominantLanguage?.rawValue ?? "en")
    }

    struct TextNode: Decodable { let i: Int; let t: String }
}
```

**Migration.** Additive per-platform feature; no persisted-data migration. Add a per-site never-translate set to Prefs/UserDefaults (new key, defaults empty). iOS/macOS gain it with zero backend; Android's cloud path reuses the same premium purchase-token contract as AiClient, so no new secret. Existing users see a new toolbar action only.


**Testing.** iOS/macOS: unit-test language detection (NLLanguageRecognizer) and DOM round-trip on fixture pages; verify offline behavior (models must be downloaded - handle the not-installed case by prompting download). Android: mock-proxy tests for batch translate, layout-preservation snapshot tests, incognito disablement. Cross-platform: verify mixed-language pages and that re-translate is idempotent (data-hx-i indexing). iOS/macOS cannot be CI-verified here (no Xcode / no .xcodeproj in repo), so flag as static-review-only.


#### Gap B5.3 — No AI Assistant / conversational sidebar over the page or open tabs. No chat UI, no conversation store, no streaming client. This is the marquee differentiator competitors brand (Edge Copilot, Brave Leo, Arc Max).

`P2` · feasibility: `feasible-in-webview` · ~6 eng-weeks


**Why it matters.** The assistant is the headline AI surface users now compare browsers on, and it is the strongest recurring-revenue anchor for Helix's existing subscription SKU (helix_premium_monthly). Lacking it entirely means Helix cannot market any AI story. However it is also the most expensive and the easiest to ship as a thin, shallow version that disappoints, so it should follow summarization (which proves the proxy + extraction plumbing) rather than precede it.


**Recommended architecture.** Reuse the same proxy + AiClient transport, upgraded to streaming (Server-Sent Events over HttpURLConnection input stream, or OkHttp if added). Add ai/AssistantRepository.kt (conversation state, message list, page-context attach via PageContentExtractor), ai/ChatMessage.kt (Room entity for history persistence in AppDatabase with a new migration), and ui/AssistantPanelFragment.kt (a side sheet / bottom sheet with streamed tokens). The Anthropic Messages API streaming format (event: content_block_delta) is the model contract; the proxy translates Helix's request into it and relays SSE. Gate behind BillingManager.isPremium with metered quota enforced server-side.


**Implementation plan.**
1. Extend the Helix proxy to support a streaming /v1/chat endpoint that relays model SSE token deltas and enforces premium quota via the Play purchase token.
1. Add AssistantRepository.kt holding an in-memory conversation plus optional attached page context (from PageContentExtractor); expose a Kotlin Flow<String> of streamed deltas.
1. Add a ChatMessage Room entity + DAO and an AppDatabase migration (v2->v3, additive, non-destructive) to persist per-conversation history; exclude in incognito.
1. Build AssistantPanelFragment.kt: message list, input box, 'use this page' chip, stop/regenerate, copy; render streamed tokens incrementally.
1. Wire entry points: overflow menu 'Ask Helix AI', and a long-press 'Ask about selection' context action feeding selected text as context.
1. Quota/empty-state UX: free users see a teaser that routes to launchSubscription; incognito disables the panel.

**Code example** — `android/app/src/main/java/com/helix/browser/ai/AssistantRepository.kt`:

```kotlin
package com.helix.browser.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Streaming chat over the Helix proxy. Emits incremental token deltas. */
class AssistantRepository {
    private val history = mutableListOf<Pair<String, String>>() // role to text

    fun ask(
        prompt: String,
        pageContext: String?,
        purchaseToken: String,
    ): Flow<String> = flow {
        history.add("user" to prompt)
        val messages = JSONArray().apply {
            pageContext?.takeIf { it.isNotBlank() }?.let {
                put(JSONObject().put("role", "user").put("content", "Page context:\n$it"))
            }
            history.forEach { (role, text) ->
                put(JSONObject().put("role", role).put("content", text))
            }
        }
        val body = JSONObject().put("messages", messages).put("stream", true).toString()
        var conn: HttpURLConnection? = null
        val assembled = StringBuilder()
        try {
            conn = (URL("https://ai.helix.app/v1/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 8_000; readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("X-Helix-Purchase-Token", purchaseToken)
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) { emit("[unavailable]"); return@flow }
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = runCatching { JSONObject(data).optString("delta") }.getOrNull()
                    if (!delta.isNullOrEmpty()) { assembled.append(delta); emit(delta) }
                }
            }
        } catch (_: Exception) {
            emit("[network error]")
        } finally {
            if (assembled.isNotEmpty()) history.add("assistant" to assembled.toString())
            try { conn?.disconnect() } catch (_: Exception) { /* ignore */ }
        }
    }.flowOn(Dispatchers.IO)
}
```

**Migration.** Requires an AppDatabase Room migration (additive: new chat_messages table, v2->v3, non-destructive, following the existing v1->v2 bookmark-folders migration precedent). Conversations excluded in incognito and never backed up to cloud (consistent with the existing backup-exclusion of DB/prefs). No change to existing tables; safe rollback by leaving the new table unused if the feature is disabled.


**Testing.** Unit-test the SSE parser (partial chunks, [DONE], malformed data lines) and quota/402 handling against a mock stream server. Room migration test (v2->v3) asserting existing bookmark/history rows survive. UI test: streaming render does not block the main thread, stop cancels the coroutine/flow, incognito hides the panel. Run Android CI gate (lint, unit, assembleRelease/R8). Server-side quota enforcement tested separately on the proxy.


#### Gap B5.4 — No model strategy (Local / Cloud / Hybrid) and no monetization wiring for AI. BillingManager.kt exposes exactly one SKU (PRODUCT_ID = helix_premium_monthly, billing/BillingManager.kt:24) verified fail-closed, but nothing consumes it for AI, and there is no abstraction to route a request to an on-device vs server model.

`P1` · feasibility: `partially-feasible` · ~2 eng-weeks


**Why it matters.** Every concrete AI feature above needs the same two decisions: where does inference run, and who pays. Cloud inference has real per-request cost that must be metered against the subscription or it becomes an unbounded liability; on-device inference is free and private but only viable for small models and only where the platform exposes an API (Apple Foundation Models / Translation framework; Android has no system WebView LLM). Without a single routing + entitlement layer, each feature reinvents transport, quota, and privacy handling inconsistently. Defining this first de-risks all the feature work and lets Helix advertise a credible privacy-respecting hybrid story.


**Recommended architecture.** Add ai/InferenceRouter.kt as the single entry point all AI features call. It picks a backend by capability + pref + entitlement: on-device (Apple Translation/Foundation Models on iOS/macOS; ML Kit translate on Android; otherwise none) vs cloud (AiClient -> Helix proxy gated by BillingManager.isPremium). Add ai/AiEntitlement.kt wrapping BillingManager to expose remaining-quota and a premium gate. Cloud key NEVER ships in the client - the proxy holds it and verifies the Play purchase token. Add a Prefs ai_processing_mode enum (on-device-only / hybrid / cloud) so privacy-conscious users can forbid cloud.


**Implementation plan.**
1. Define an Inference interface (summarize/chat/translate) with two implementations: CloudInference (AiClient/AssistantRepository over the proxy) and OnDeviceInference (platform-specific: Apple frameworks; Android ML Kit or no-op).
1. Add InferenceRouter that selects an implementation from device capability + ai_processing_mode pref + AiEntitlement.isPremium, falling back gracefully (on-device-only mode never calls the network).
1. Add AiEntitlement bridging BillingManager.isPremium and exposing a server-reported remaining-quota value (returned in proxy responses).
1. Centralize the first-run privacy disclosure and incognito disablement in the router so every feature inherits it.
1. Have summarization, assistant, and translation call only InferenceRouter, never transport classes directly.

**Code example** — `android/app/src/main/java/com/helix/browser/ai/InferenceRouter.kt`:

```kotlin
package com.helix.browser.ai

import com.helix.browser.billing.BillingManager
import com.helix.browser.utils.Prefs

/** Single decision point for where AI inference runs and whether it is allowed. */
class InferenceRouter(
    private val billing: BillingManager,
    private val prefs: Prefs,
) {
    enum class Mode { ON_DEVICE_ONLY, HYBRID, CLOUD }

    sealed interface Decision {
        data class Cloud(val purchaseToken: String) : Decision
        object OnDevice : Decision               // Android: ML Kit translate only
        data class Blocked(val reason: String) : Decision
    }

    /** @param needsServerModel true for chat/summary (no on-device LLM on Android). */
    fun route(isIncognito: Boolean, needsServerModel: Boolean): Decision {
        if (isIncognito) return Decision.Blocked("Disabled in private tabs")
        if (!prefs.aiEnabled) return Decision.Blocked("Turn on AI in Settings")
        val mode = prefs.aiProcessingMode
        // Android has no on-device LLM via the system WebView; only ML Kit translate.
        if (mode == Mode.ON_DEVICE_ONLY) {
            return if (needsServerModel) Decision.Blocked("Needs cloud; current mode is on-device only")
                   else Decision.OnDevice
        }
        if (!needsServerModel && mode == Mode.HYBRID) return Decision.OnDevice
        val token = billing.activePurchaseToken()
            ?: return Decision.Blocked("Helix Premium required")
        return if (billing.isPremium.value) Decision.Cloud(token)
               else Decision.Blocked("Helix Premium required")
    }
}
```

**Migration.** Additive. New Prefs keys ai_enabled (default false) and ai_processing_mode (default HYBRID); existing installs default to AI off. Requires adding an activePurchaseToken() accessor to BillingManager that returns the already-verified Purchase token (no new verification logic, reuses the fail-closed path). No Room/schema change. Fully back-compatible: with AI off, router always returns Blocked and no behavior changes.


**Testing.** Unit-test the routing truth table exhaustively: every (Mode x isPremium x needsServerModel x incognito) combination, asserting on-device-only never yields Cloud and incognito always Blocked. Verify BillingManager.activePurchaseToken returns null when no verified active purchase (fail-closed). Confirm no provider key is referenced anywhere in client code (grep gate in CI). Run Android lint + unit suite.


---

## B6. Privacy  `[privacy-features]`

**Overall feasibility:** partially-feasible


Verified against the real code (Android engine/PrivacyManager.kt 691 lines, AdBlockEngine.kt, iOS/macOS PrivacyManager.swift, linux/src/privacy_manager.py, windows PrivacyScripts.cs). Of the 7 sub-features: Anti-Tracking, Anti-Fingerprinting and Cookie Isolation are PARTIAL (JS-shim + config-flag based, not true network-layer enforcement); Site Partitioning, DoH, DoT and Private Relay are entirely MISSING (grep confirms zero dns/doh/proxy/partition/setProxyOverride references in any source). The anti-tracking layer is the weakest: a frozen ~17-domain hardcoded list (PrivacyManager.kt:43-62) matched via injected page JS that hooks fetch/XHR/sendBeacon (PrivacyManager.kt:297-351) AFTER load, runs in the page's own context (detectable/bypassable, misses img/script/iframe/early requests), with no updatable filter list. Confirmed concrete bugs: macOS third-party-cookie toggle is INVERTED (PrivacyManager.swift:349-353 calls setCookiePolicy(.allow) when the user enables blocking); Android HelixWebView.kt:45 unconditionally re-enables third-party cookies at init, overriding PrivacyManager policy; Linux has no CookieManager accept-policy call so its toggle is a no-op. Anti-fingerprinting is genuinely present (canvas/WebGL/audio noise, hw/screen/battery spoof, WebRTC IP strip — PrivacyManager.kt:164-279) but is best-effort JS, not engine-level, so it is detectable and weaker than Brave/Safari. Overall feasibility is partially-feasible: anti-tracking and cookie isolation can be hardened to native enforcement (Android shouldInterceptRequest, iOS/macOS WKContentRuleList, Linux CookieManager) and DoH can ship as a platform-API-dependent OS handoff, but TRUE state/site partitioning is engine-owned and requires a fork — the honest shippable substitute is per-site/per-profile WKWebsiteDataStore isolation plus a Profiles feature, and proxy/Private Relay should integrate a third party rather than be built. Most material, fixable wins first: fix the cookie-policy bugs and promote tracker blocking to the network layer with an updatable EasyPrivacy list.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Anti-Tracking (block tracker requests) | 🟡 partial | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Anti-Fingerprinting | 🟡 partial | ❌ | 🟡 | ✅ | 🟡 | ✅ | ❌ | med | L |
| Cookie Isolation (block 3rd-party cookies) | 🟡 partial | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | M |
| Site Partitioning / State Partitioning (dFPI) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | XL |
| DNS-over-HTTPS (DoH) | ❌ missing | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ | med | L |
| DNS-over-TLS (DoT) | ❌ missing | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | low | M |
| Private Relay / built-in proxy / VPN | ❌ missing | ❌ | 🟡 | ✅ | ✅ | ✅ | ❌ | med | XL |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B6.1 — Anti-tracking and cookie isolation are config-flag + JS-shim only, with a tiny hardcoded ~17-domain tracker list and an inverted-logic bug on macOS; there is no native request-level enforcement on most platforms and no updatable filter list.

`P1` · feasibility: `partially-feasible` · ~5 eng-weeks


**Why it matters.** The injected JS tracker shim (PrivacyManager.kt:297-351, privacy_manager.py:90, macOS/iOS PrivacyManager.swift) only hooks fetch/XHR/sendBeacon AFTER the document loads, runs inside the page's own JS context (trivially detectable and bypassable, and misses img/script/iframe/CSS-loaded trackers and any request before the shim executes), and matches against ~17 frozen domains while EasyPrivacy tracks 50k+. On macOS the third-party-cookie toggle calls setCookiePolicy(.allow) when the user ENABLES blocking (PrivacyManager.swift:349-353) so cookies are never actually blocked. Linux has no CookieManager.set_accept_policy call at all (privacy_manager.py), so its toggle is a pure no-op. Net effect: users believe they are protected from tracking but the engine still issues the requests and stores the cookies. This is a trust/credibility and competitive-table-stakes failure versus Brave/Firefox/Safari which all block at the network layer.


**Recommended architecture.** Move enforcement to the host engine's NATIVE request/cookie surface on each platform instead of page JS. Android: extend AdBlockEngine.shouldInterceptRequest to also consult a tracker blocklist (it already intercepts ad domains at AdBlockEngine.kt:149) and fix the third-party cookie default in HelixWebView.kt:45 (currently unconditionally sets setAcceptThirdPartyCookies(this, true), overriding the PrivacyManager policy at init). iOS/macOS: add tracker rules into the existing compiled WKContentRuleList in AdBlockEngine.swift (network-level, runs before page JS) and fix the inverted macOS PrivacyManager.swift:349-353 to use .disallow plus set config.defaultWebpagePreferences / HTTPCookieAcceptPolicy. Linux: add a WebKit2 CookieManager set_accept_policy(WEBKIT_COOKIE_POLICY_ACCEPT_NO_THIRD_PARTY) in privacy_manager.py and use the WebKit Resource-Load policy / a WebKitWebContext intercept. Windows: configure CoreWebView2.CookieManager and add WebResourceRequested filtering in PrivacyScripts wiring. Introduce a shared FilterListManager that downloads+caches EasyPrivacy and compiles to each engine's native format.


**Implementation plan.**
1. Fix the macOS inverted cookie policy (PrivacyManager.swift:349-353): when isBlockThirdPartyCookies is true, set the data store / config to actually disallow third-party cookies (HTTPCookieAcceptPolicy.onlyFromMainDocumentDomain), and add the equivalent native call on iOS
1. Fix Android HelixWebView.kt:45 so init does not unconditionally re-enable third-party cookies, then call PrivacyManager.applyThirdPartyCookiePolicy after creation
1. Add a WebKit2 CookieManager.set_accept_policy(NO_THIRD_PARTY) on Linux (privacy_manager.py) and CoreWebView2 cookie policy on Windows so those toggles stop being no-ops
1. Promote tracker blocking to the network layer: feed the tracker domain set into Android AdBlockEngine.shouldInterceptRequest, into the iOS/macOS WKContentRuleList JSON, and into Linux decide-policy / Windows WebResourceRequested
1. Add FilterListManager that fetches EasyPrivacy on a schedule, caches it locally, and recompiles per-platform native rules; keep JS cosmetic shim only as a supplement
1. Increment the real tracker counter from the native intercept (Android already has incrementTrackersBlocked at PrivacyManager.kt:109; iOS/macOS counters are still hardwired to 0)

**Code example** — `macos/HelixBrowser/PrivacyManager.swift`:

```swift
// FIX inverted third-party-cookie logic (was: setCookiePolicy(.allow) when blocking).
func applyCookiePolicy(to config: WKWebViewConfiguration, prefs: Prefs) {
    let store = config.websiteDataStore
    if prefs.isBlockThirdPartyCookies {
        // WebKit honors this on the HTTPCookieStorage backing the data store.
        store.httpCookieStore.getAllCookies { _ in }
        HTTPCookieStorage.shared.cookieAcceptPolicy = .onlyFromMainDocumentDomain
    } else {
        HTTPCookieStorage.shared.cookieAcceptPolicy = .always
    }
}

// Promote tracker blocking to the compiled content-rule list so it runs at the
// network layer BEFORE page JS (unlike the bypassable injected shim).
static func trackerContentRules(domains: [String]) -> String {
    let triggers = domains.map { domain -> [String: Any] in
        ["trigger": ["url-filter": "https?://([^/]+\\.)?\(NSRegularExpression.escapedPattern(for: domain))",
                      "load-type": ["third-party"]],
         "action": ["type": "block"]]
    }
    let data = try! JSONSerialization.data(withJSONObject: triggers)
    return String(data: data, encoding: .utf8)!
}
```

**Migration.** No persisted-data migration needed; these are policy/config changes applied at WebView creation. The tracker filter list is additive cache (new file under app support dir); ship a bundled EasyPrivacy snapshot so behavior is correct offline on first run before the first network refresh. Existing per-origin cookie data is unaffected.


**Testing.** Unit-test the rule-compilation (domain in -> JSON/intercept decision out) and the cookie-policy mapping on each platform. Integration: load a page embedding a known tracker (e.g. google-analytics) and a third-party iframe that sets a cookie, assert via the engine that the request was blocked and no third-party cookie persisted. Add a regression test pinning macOS to .disallow when the toggle is ON. Android is the only CI-verified platform, so gate the others behind manual Xcode/MSBuild/py runs as the repo notes.


#### Gap B6.2 — No DNS-over-HTTPS (DoH) / encrypted-DNS control surface on any platform.

`P2` · feasibility: `platform-api-dependent` · ~4 eng-weeks


**Why it matters.** Every flagship competitor (Chrome, Edge, Brave, Firefox, Arc) ships user-selectable DoH; it prevents the ISP/network from seeing and tampering with the plaintext DNS of every site the user visits, and is a headline privacy checkbox reviewers look for. Helix has zero DNS code (verified: no doh/dns-over-https/setProxyOverride references in any source). On Android the WebView resolves through the OS resolver, so Helix cannot set per-app DoH purely in-process without the platform Private DNS setting. This is a visible feature-parity hole on the privacy comparison tables Helix would be judged against.


**Recommended architecture.** This is owned by the host network stack, not the WebView, so a true per-app DoH resolver requires intercepting the network layer. Realistic constrained approach per platform: Android = deep-link the user to Settings.ACTION_PRIVATE_DNS / surface a guided 'Private DNS' onboarding plus a NetworkManager helper class; Windows WebView2 = no per-control DoH API, document as OS-level. macOS/iOS = ship a bundled NEDNSSettingsManager / configuration profile (Network Extension, DNSSettings) to set system-wide encrypted DNS, the only Apple-sanctioned per-app-installed path. Add a DnsSettingsManager class on each platform that either applies the NE profile (Apple) or routes the user to OS settings (Android/Windows/Linux uses systemd-resolved/NetworkManager guidance).


**Implementation plan.**
1. Add DnsSettingsManager abstraction with a 'configure encrypted DNS' entry point and a 'status' query
1. Apple platforms: implement via NetworkExtension NEDNSSettingsManager with a DoH server (e.g. Cloudflare/NextDNS), gated behind a settings toggle; request the com.apple.developer.networking.networkextension entitlement
1. Android: implement guided flow to Private DNS (Settings.ACTION_PRIVATE_DNS) since WebView cannot override the OS resolver in-process; persist a 'recommended provider' hint
1. Windows/Linux: surface documentation + deep links to OS encrypted-DNS settings (WebView2 and WebKit2GTK both defer DNS to the OS)
1. Add a Settings row 'Secure DNS' with provider picker and clear copy that on Android/Windows/Linux it configures the OS, not just the browser

**Code example** — `android/app/src/main/java/com/helix/browser/engine/DnsSettingsManager.kt`:

```kotlin
package com.helix.browser.engine

import android.content.Context
import android.content.Intent
import android.provider.Settings

// WebView resolves DNS through the OS resolver, so per-app DoH is not settable
// in-process. The honest, ship-today path is to route the user to Android's
// Private DNS (DoT/strict mode) and recommend a provider.
object DnsSettingsManager {
    const val RECOMMENDED_HOST = "one.one.one.one" // Cloudflare DoT/DoH

    fun openPrivateDnsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_PRIVATE_DNS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
```

**Migration.** No data migration. Persist only a single 'secure DNS enabled/provider' preference alongside existing privacy flags in SharedPreferences/UserDefaults/prefs.json. On Apple platforms uninstalling the app should remove the installed NE DNS profile.


**Testing.** Apple: verify NEDNSSettingsManager profile installs and that resolved DNS goes encrypted (inspect with a DNS leak test page in-browser). Android/Windows/Linux: UI test that the settings deep-link resolves and that the row copy correctly states it configures the OS. Manual network-capture (tcpdump) to confirm DNS is no longer plaintext when enabled.


#### Gap B6.3 — No site/state partitioning of storage (cookies, localStorage, cache, IndexedDB are NOT keyed by top-level site) and no built-in proxy / Private Relay.

`P2` · feasibility: `requires-engine-fork` · ~8 eng-weeks


**Why it matters.** State partitioning (Chrome CHIPS/storage partitioning, Firefox dFPI/Total Cookie Protection, Safari ITP partitioning, Brave ephemeral 3p storage) is the modern defense against cross-site tracking that survives third-party-cookie blocking (via localStorage, cache, IndexedDB super-cookies). Helix has zero partitioning code and relies on a single shared WKProcessPool / shared CookieManager / one default data store per profile (verified WebViewModel.swift:36, HelixWebView). A built-in proxy/relay (Brave/Firefox/Safari ship one) is similarly absent. Without partitioning, blocking third-party cookies alone leaves users trackable; this is the single biggest TECHNICAL privacy gap versus the field.


**Recommended architecture.** Storage partitioning is an engine-internal, network-stack-and-storage-keying capability owned by Blink/WebKit. A WebView wrapper CANNOT add double-keyed storage without forking the engine. Realistic constrained alternatives that ARE buildable in-shell: (1) Apple platforms can approximate per-top-site isolation by allocating a separate WKWebsiteDataStore per eTLD+1 / per profile (data-store-per-site is the only sanctioned partitioning hook, at a memory cost) instead of the single shared store in WebView.swift:43 and WebViewModel.swift:360. (2) Lean on the platforms' OWN partitioning: modern WebView2/WebKit2GTK/WKWebView already partition third-party storage by default on recent runtimes, so Helix should NOT disable it and should surface a 'strict site isolation' toggle that maps to per-data-store profiles. (3) Proxy: do NOT build a VPN; integrate an EXISTING provider or rely on OS-level VPN. Add a real-but-honest 'Profiles' feature (already an absent product gap) where each profile = its own WKWebsiteDataStore, giving manual isolation.


**Implementation plan.**
1. Document clearly that true per-frame state partitioning requires the host engine and is inherited, not Helix-built (set expectations in the privacy UI copy)
1. Stop weakening inherited protections: audit that Helix never sets flags re-enabling cross-site storage; confirm third-party cookie default is blocking after fixing HelixWebView.kt:45
1. Apple: prototype WKWebsiteDataStore-per-top-site (or per-profile) as an opt-in 'Strict isolation' mode, measuring memory; wire it through TabManager so a tab adopts the data store for its site
1. Build a Profiles feature (separate data stores) so privacy-conscious users get hard isolation between contexts; this is the realistic shippable partition surface
1. For proxy: ship integration with an OS VPN / a third-party proxy provider rather than a Helix-built relay; expose as a settings toggle, clearly labeled as third-party

**Code example** — `macos/HelixBrowser/WebView.swift`:

```swift
// Approximate state partitioning on WebKit by giving each profile/site its own
// data store instead of the single shared WKWebsiteDataStore.default() at line 43.
// NOTE: true per-frame double-keyed storage is engine-owned; this is the only
// sanctioned per-site isolation hook available to a WKWebView shell.
private static var dataStores: [String: WKWebsiteDataStore] = [:]

static func dataStore(for tab: WebTab, strictIsolation: Bool) -> WKWebsiteDataStore {
    if tab.isIncognito { return .nonPersistent() }
    guard strictIsolation, let site = tab.url.flatMap(eTLDPlusOne) else {
        return .default()
    }
    if let existing = dataStores[site] { return existing }
    // macOS 14+: identifier-keyed persistent stores enable per-site separation.
    let store: WKWebsiteDataStore
    if #available(macOS 14.0, *) {
        store = WKWebsiteDataStore(forIdentifier: deterministicUUID(for: site))
    } else {
        store = .default()
    }
    dataStores[site] = store
    return store
}
```

**Migration.** Per-site/per-profile data stores start empty, so enabling strict isolation will appear to log the user out of sites (expected, like switching to Total Cookie Protection). Gate behind an explicit opt-in toggle with clear copy. Provide a 'reset isolation' that clears the keyed stores. No change to existing default-profile data unless the user opts in.


**Testing.** Verify cross-site read isolation: site A in profile/store X cannot read a cookie/localStorage value written under top-level site B. Memory regression test for N simultaneous per-site stores. Manual run on the EFF Cover Your Tracks / a partitioning test page to confirm third-party storage is keyed. Engine-inherited behavior should be validated, not unit-tested, since it is not Helix code.


---

## B7. Security / Safe Browsing  `[security-safe-browsing]`

**Overall feasibility:** partially-feasible


VERIFIED GROUND TRUTH: Helix ships effectively NO first-party Safe Browsing across all 5 platforms. The single reference is Android setting `safeBrowsingEnabled = true` (android/.../engine/HelixWebView.kt:85-88), which is a pure delegation to the engine's built-in Google Safe Browsing (GMS) — Helix neither owns the lists, the interstitial, nor any telemetry; iOS/macOS get WebKit's Google Safe Browsing transparently, but Helix configures nothing; Windows/WebView2 inherits SmartScreen only if enabled by the host Edge runtime (not set by Helix); Linux/WebKitGTK has NO Safe Browsing backend at all (GNOME Web has none) — verified by grep returning zero phishing/malware references in linux/src. AdBlockEngine.kt and PrivacyManager.kt blocklists contain ONLY ad/tracker domains (grep for malware|phish|virus|scam|fraud = 0 hits). The download path (MainActivity.kt:2337 enqueueDownload, :2305 confirm sheet, :2350 sanitizeDownloadFileName) does scheme + filename sanitization only — NO dangerous-file-type warning, NO download reputation, NO mark-of-the-web. shouldInterceptRequest (HelixWebViewClient.kt:57) and shouldOverrideUrlLoading (:390) are wired for ad/tracker blocking but check NO threat list. NET: engine-level Safe Browsing is INHERITED (not Helix's to reimplement and cannot be forked), but a first-party URL/download reputation layer using a hosted threat-list API (Google Safe Browsing v4/v5 Lookup, or a self-hosted list) IS feasible-in-webview as an HTTP lookup in the navigation/download hooks. The realistic, honest answer: ship a thin Helix Safe Browsing layer (hash-prefix lookup + interstitial + dangerous-download warning) that ADDS a checkable, consistent, cross-platform safety floor rather than relying on inconsistent per-engine inheritance — Linux especially has zero protection today.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Safe Browsing lists (URL threat-list lookup before navigation) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Malware Detection (block known malware-hosting sites/resources) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Download Protection (dangerous-file-type warning, download reputation, mark-of-web) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | 🟡 | ✅ | **high** | M |
| URL Reputation (real-time / enhanced lookups, typo-squat & newly-seen-domain signals) | ❌ missing | ✅ | ✅ | 🟡 | 🟡 | ✅ | 🟡 | med | L |
| Phishing Detection (block known social-engineering / credential-harvest pages + interstitial) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **CRIT** | L |

### ⚖️ Verifier corrections (adversarial re-check vs code) — confidence: high

- **Safe Browsing lists (URL threat-list lookup before navigation)**: `missing` → **`partial`** — android/.../engine/HelixWebView.kt:85-88 sets `safeBrowsingEnabled = true` on every WebView (SDK_INT >= O), and AndroidManifest.xml has no EnableSafeBrowsing opt-out, so Android WebView's native Google Safe Browsing performs URL threat-list lookups before navigation. This is a real, active engine-level capability (not a stub), but Helix owns no threat list, no custom lookup, and no custom interstitial — it is fully delegated to the WebView engine, hence partial rather than missing or fully supported.
- **Malware Detection (block known malware-hosting sites/resources)**: `missing` → **`partial`** — Same `safeBrowsingEnabled = true` at android/.../engine/HelixWebView.kt:87 activates Google Safe Browsing, which blocks known malware-hosting sites/resources at the engine level. AdBlockEngine.kt contains only ad/tracker domain sets (no malware/threat lists), so the malware coverage comes purely from the platform engine — partial, not a Helix-implemented detector.
- **Phishing Detection (block known social-engineering / credential-harvest pages + interstitial)**: `missing` → **`partial`** — `safeBrowsingEnabled = true` (android/.../engine/HelixWebView.kt:87) enables Google Safe Browsing's social-engineering/phishing blocklist and the WebView's built-in interstitial. There is no Helix `onSafeBrowsingHit` override, no Helix phishing list, and no Helix interstitial (the `proceed_anyway` string in res/values is unused per lint), so phishing protection exists via the engine — partial, not missing.

### Gaps & remediation


#### Gap B7.1 — No first-party Phishing/Malware URL threat-list lookup or interstitial. Helix relies entirely on whatever the host WebView happens to enable: Android delegates to GMS Safe Browsing via the single flag `safeBrowsingEnabled = true` (engine/HelixWebView.kt:85-88) but never handles the hit or customizes the interstitial; iOS/macOS WKWebView inherit WebKit's Google Safe Browsing transparently with no Helix involvement; Windows/WebView2 does NOT have SmartScreen enabled by Helix; Linux/WebKitGTK has NO Safe Browsing backend whatsoever (grep over linux/src for phish|malware|threat = 0 hits). So protection is inconsistent and on Linux/Windows essentially absent.

`P0` · feasibility: `feasible-in-webview` · ~7 eng-weeks


**Why it matters.** Phishing is the #1 consumer browser threat. A user on Helix-Linux or Helix-Windows visiting a credential-harvesting page gets ZERO warning — no interstitial, no block — while every competitor (Chrome/Edge/Brave/Firefox/Safari/Arc) shows a red full-page warning. This is a direct credential-theft and account-takeover exposure, a trust/brand killer, and a likely app-store / enterprise-procurement blocker. It is the single highest-severity security parity gap in the product.


**Recommended architecture.** Add a new engine module `engine/SafeBrowsingEngine` per platform (Android: SafeBrowsingEngine.kt; iOS/macOS: SafeBrowsingEngine.swift; Windows: Engine/SafeBrowsingEngine.cs; Linux: safe_browsing.py). It implements the Google Safe Browsing v4 Update API (local hash-prefix database, periodic update, privacy-preserving — only 4-byte SHA-256 prefixes leave the device, and only on a partial match) OR a self-hosted equivalent list service. Wire the check into the EXISTING navigation hook: on Android in HelixWebViewClient.shouldOverrideUrlLoading (HelixWebViewClient.kt:390) and shouldInterceptRequest (:57) for main-frame + sub-resource; render a full-page interstitial reusing the existing buildSslErrorPage pattern in HelixWebViewClient.kt. Persist the local hash DB in Room (new SafeBrowsingPrefix entity) so it survives restarts; update on a WorkManager job.


**Implementation plan.**
1. 1. Define the threat-list source: Google Safe Browsing v4 Update API (free, requires an API key + ToS acceptance) for MALWARE, SOCIAL_ENGINEERING, UNWANTED_SOFTWARE, POTENTIALLY_HARMFUL_APPLICATION threat types; fall back to a Helix-hosted hash-prefix mirror to avoid a hard Google dependency and to cover Linux/Windows uniformly.
1. 2. Build SafeBrowsingEngine with a local store of 4-byte SHA-256 hash prefixes (Room table on Android), a canonicalization routine (RFC-3986 + Safe Browsing URL canonicalization: lowercase host, strip fragments, percent-decode, expand IP, generate the 30 host/path permutation combos), and a lookup that hashes each combo and checks the local prefix set.
1. 3. On a local prefix match, perform the v4 fullHashes:find network call (only the matching prefixes are sent) to confirm the full hash and threat type; cache positive/negative results with the API-provided TTL.
1. 4. Add a periodic update job (Android WorkManager; iOS BGTaskScheduler; desktop a timer) calling threatListUpdates:fetch with the stored client state for incremental delta updates.
1. 5. Wire the lookup into shouldOverrideUrlLoading for main-frame navigations (block + interstitial) and optionally shouldInterceptRequest for sub-resources (block silently). Add a `proceed anyway` flow gated behind an explicit secondary confirmation, matching Chrome.
1. 6. Render a dedicated red interstitial (reuse the buildSslErrorPage HTML-escaping + restrictive-CSP pattern already in HelixWebViewClient.kt) naming the host and threat type; add a Settings toggle `Safe Browsing protection` (default ON) and an Enhanced/Standard choice.
1. 7. Replicate the engine on iOS/macOS/Windows/Linux in the corresponding navigation-decision delegate; for Linux this is the ONLY protection that platform will have.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/SafeBrowsingEngine.kt`:

```kotlin
package com.helix.browser.engine

import android.net.Uri
import java.security.MessageDigest

/**
 * First-party Safe Browsing using the Google Safe Browsing v4 Update API model:
 * a local set of 4-byte SHA-256 host/path-permutation hash prefixes, refreshed by
 * a background job. Only prefixes that locally match are confirmed over the network
 * (fullHashes:find), so full URLs never leave the device. This ADDS a consistent
 * threat layer on top of (or, on Linux, in place of) the host WebView's own.
 */
object SafeBrowsingEngine {

    enum class Threat { SOCIAL_ENGINEERING, MALWARE, UNWANTED_SOFTWARE }

    /** In-memory mirror of the Room-backed prefix table; loaded at startup. */
    @Volatile private var prefixSet: Set<Int> = emptySet()

    fun setPrefixes(prefixes: Set<Int>) { prefixSet = prefixes }

    /**
     * Returns the matching threat for a main-frame URL, or null if clean.
     * Synchronous over the LOCAL set only — safe to call on the WebViewClient
     * thread. The caller confirms a positive match over the network before blocking.
     */
    fun checkLocal(url: String): Threat? {
        val candidates = canonicalize(url) ?: return null
        for (combo in candidates) {
            val prefix = prefix32(combo)
            if (prefixSet.contains(prefix)) {
                // Local prefix hit -> caller must confirm via fullHashes:find.
                return Threat.SOCIAL_ENGINEERING // placeholder; confirmer sets real type
            }
        }
        return null
    }

    /** SHA-256 then take the first 4 bytes as a big-endian Int (the v4 prefix). */
    private fun prefix32(s: String): Int {
        val h = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return ((h[0].toInt() and 0xFF) shl 24) or
               ((h[1].toInt() and 0xFF) shl 16) or
               ((h[2].toInt() and 0xFF) shl 8) or
               (h[3].toInt() and 0xFF)
    }

    /**
     * Safe Browsing URL canonicalization: produce up to 30 host x path
     * permutations to check (5 host suffixes x 6 path prefixes).
     */
    private fun canonicalize(rawUrl: String): List<String>? {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase()?.trim('.') ?: return null
        val path = (uri.path ?: "/").ifEmpty { "/" }
        val query = uri.query

        val hosts = hostSuffixes(host)
        val paths = pathPrefixes(path, query)
        return buildList { for (h in hosts) for (p in paths) add("$h$p") }
    }

    private fun hostSuffixes(host: String): List<String> {
        val labels = host.split('.')
        val out = linkedSetOf(host)
        // Up to 4 trailing label-combinations, per the v4 spec.
        var i = maxOf(0, labels.size - 5)
        while (i < labels.size - 1) {
            out.add(labels.subList(i, labels.size).joinToString("."))
            i++
        }
        return out.toList()
    }

    private fun pathPrefixes(path: String, query: String?): List<String> {
        val out = linkedSetOf<String>()
        query?.let { out.add("$path?$it") }
        out.add(path)
        val segs = path.trimStart('/').split('/').filter { it.isNotEmpty() }
        var acc = "/"
        for (s in segs.take(4)) { acc += "$s/"; out.add(acc) }
        return out.toList()
    }
}
```

**Migration.** Purely additive. New Room entity SafeBrowsingPrefix + a v2->v3 additive migration (the DB already has one real migration precedent for bookmark folders, so the pattern exists). No existing data touched. The hash DB is rebuilt from the network on first run and excluded from cloud backup (the project already excludes DB/prefs from backup). On Settings, add the toggle defaulting ON; existing installs pick it up on update.


**Testing.** Unit-test canonicalize() against the official Google Safe Browsing canonicalization test vectors (the spec publishes exact input->permutation cases) and prefix32() against known SHA-256 prefixes. Use the GSB testing URLs (testsafebrowsing.appspot.com/s/malware.html, /phishing.html) for end-to-end interstitial assertions in an instrumented Espresso test on Android. Add a delta-update parser test with recorded threatListUpdates:fetch fixtures. Verify fail-OPEN on network/API failure does NOT block legitimate navigation (availability) while a confirmed hit DOES block, and that `proceed anyway` requires the secondary confirmation.


#### Gap B7.2 — No Download Protection: no dangerous-file-type warning, no download URL reputation, no mark-of-the-web. enqueueDownload (MainActivity.kt:2337) validates only the URL scheme (http/https) and sanitizeDownloadFileName (:2350) strips path-traversal/reserved chars; the confirmation sheet (showDownloadConfirmation, :2305) shows name/host/size but issues NO warning for executables (.apk/.exe/.dmg/.msi/.bat/.scr) and runs NO reputation check. iOS/macOS/Windows/Linux download paths likewise have no reputation step.

`P1` · feasibility: `feasible-in-webview` · ~3 eng-weeks


**Why it matters.** Drive-by and social-engineered malware downloads are a primary infection vector. Chrome/Edge/Brave check every download against Safe Browsing download-reputation and show an explicit 'this file may harm your device' warning for dangerous types; Helix silently hands the file to the OS DownloadManager. A Helix user is materially more likely to install malware than on any competitor. On Android specifically, an unwarned .apk download is a direct sideload-malware path. This is both a real user-harm gap and a Play-policy / enterprise-security concern.


**Recommended architecture.** Extend the existing download flow rather than add a new module: (1) a `DangerousFileClassifier` helper (extension + MIME -> risk tier) consulted inside showDownloadConfirmation (MainActivity.kt:2305) to inject a red warning row and require a second tap for dangerous types; (2) reuse SafeBrowsingEngine to check the download URL/host against the threat list before enqueue in enqueueDownload (:2337). Optionally add a DownloadReputation lookup (GSB does not expose a public file-hash reputation API to third parties, so use file-type + source-host reputation only — be honest that full Chrome-grade per-binary reputation is not third-party-available).


**Implementation plan.**
1. 1. Add DangerousFileClassifier with tiers: DANGEROUS (apk, exe, dmg, msi, bat, cmd, scr, jar, app, deb, pkg), COMMON_ARCHIVE (zip, 7z, rar — warn-on-open only), SAFE (everything else).
1. 2. In showDownloadConfirmation, when tier == DANGEROUS, render a prominent warning row (reuse text_secondary/red color tokens) and relabel the action to require explicit confirmation, mirroring Chrome's 'Keep dangerous file?' flow.
1. 3. In enqueueDownload, call SafeBrowsingEngine.checkLocal(url) for the source host; if it matches a threat, refuse the enqueue and surface a blocking dialog instead of a toast.
1. 4. Where supported, set DownloadManager mark-of-the-web/notification flags so the OS scanner (Play Protect / Defender) gets a clean handoff.
1. 5. Replicate the classifier + interstitial in iOS WKDownload, macOS DownloadManager, Windows DownloadStarting (note: Windows has NO download handler today — this also closes that absent feature), and Linux (note: Linux has NO functional downloads today either).

**Code example** — `android/app/src/main/java/com/helix/browser/util/DangerousFileClassifier.kt`:

```kotlin
package com.helix.browser.util

/** Classifies a download by extension + MIME into a risk tier so the download
 *  confirmation sheet can warn before saving executable content. */
object DangerousFileClassifier {

    enum class Risk { DANGEROUS, ARCHIVE, SAFE }

    private val dangerousExt = setOf(
        "apk", "exe", "msi", "dmg", "pkg", "app", "deb", "rpm",
        "bat", "cmd", "com", "scr", "jar", "vbs", "ps1", "sh", "bin"
    )
    private val archiveExt = setOf("zip", "7z", "rar", "tar", "gz", "iso")

    fun classify(fileName: String, mimeType: String?): Risk {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return when {
            ext in dangerousExt -> Risk.DANGEROUS
            mime == "application/vnd.android.package-archive" -> Risk.DANGEROUS
            mime == "application/x-msdownload" ||
                mime == "application/x-executable" -> Risk.DANGEROUS
            ext in archiveExt -> Risk.ARCHIVE
            else -> Risk.SAFE
        }
    }

    /** True when the download confirmation sheet must show a red warning and
     *  require a second, explicit confirmation before enqueueing. */
    fun requiresWarning(fileName: String, mimeType: String?): Boolean =
        classify(fileName, mimeType) == Risk.DANGEROUS
}
```

**Migration.** No data migration — stateless classifier plus an extra branch in the existing confirmation sheet. Back-compat: the default path for SAFE files is byte-for-byte the current behavior, so no regression risk for the common case. Add a Settings toggle `Warn about dangerous downloads` (default ON).


**Testing.** Unit-test classify() across the extension/MIME table including the Android .apk MIME. Espresso test: trigger a .apk and a .exe download and assert the warning row + second-confirmation appear, and that a .pdf/.jpg download shows the normal sheet unchanged. Verify a download whose host is on the Safe Browsing list is refused at enqueue. Confirm path-traversal sanitization (existing sanitizeDownloadFileName behavior) is preserved.


#### Gap B7.3 — Cross-platform inconsistency / zero-protection floor on Linux and Windows. Even treating engine-inherited Safe Browsing as out-of-scope, the protection a Helix user actually receives swings from 'GMS Safe Browsing' (Android) to 'WebKit Google Safe Browsing' (iOS/macOS, but unconfigured) to 'NOTHING' (Linux WebKitGTK; Windows WebView2 with SmartScreen not enabled by Helix). There is no single Helix-owned, testable safety contract; the product cannot truthfully claim 'Helix protects you from phishing' on any platform.

`P1` · feasibility: `partially-feasible` · ~5 eng-weeks


**Why it matters.** A security feature that exists on some platforms and silently vanishes on others is a liability: users assume consistent protection, marketing/store listings can't make an honest blanket claim, and the weakest platform (Linux) defines the real exposure. For procurement/enterprise and for app-store security questionnaires, 'inherited, varies by OS, none on Linux' is effectively 'no Safe Browsing'. Owning a thin first-party layer (gaps #1/#2) is what converts this from inherited-and-uneven to a guaranteed Helix floor.


**Recommended architecture.** Make SafeBrowsingEngine (gap #1) the SINGLE cross-platform source of truth, shipped on all 5 platforms with one shared threat-list source (Helix-hosted hash-prefix mirror to avoid divergent per-OS behavior and per-OS Google ToS). Where the host engine ALSO has Safe Browsing (Android GMS, WebKit), keep it enabled as defense-in-depth but do not depend on it for the product claim. On Windows, additionally set the WebView2 SmartScreen setting explicitly (CoreWebView2Settings) so the host layer is at least turned on; on Linux, SafeBrowsingEngine is the only layer.


**Implementation plan.**
1. 1. Stand up a Helix-hosted threat-list mirror (or proxy to GSB) so every platform updates from one source with identical semantics.
1. 2. Port SafeBrowsingEngine to Swift (iOS/macOS), C# (Windows, in Engine/), and Python (Linux, safe_browsing.py), each wired into that platform's navigation-decision delegate and download handler.
1. 3. On Windows, explicitly enable CoreWebView2Settings SmartScreen + add the navigation-blocking interstitial; on Linux wire WebKitGTK's WebKitPolicyDecision in the existing decide-policy handler in browser_window.py.
1. 4. Define a single documented safety contract ('Helix Safe Browsing: phishing + malware URL blocking and dangerous-download warnings on all platforms') and a per-platform conformance test asserting the GSB test URLs are blocked.
1. 5. Add a per-platform Settings row so the feature is discoverable and the protection state is user-visible.

**Code example** — `linux/src/safe_browsing.py`:

```python
import hashlib
from urllib.parse import urlsplit


class SafeBrowsingEngine:
    """First-party Safe Browsing for the WebKitGTK shell, which otherwise has
    NO threat protection at all. Holds a local set of 4-byte SHA-256 hash
    prefixes refreshed from the Helix threat-list mirror; only locally matched
    prefixes are confirmed over the network, so full URLs never leave the box."""

    def __init__(self):
        self._prefixes = set()  # set[bytes] of 4-byte prefixes

    def set_prefixes(self, prefixes):
        self._prefixes = set(prefixes)

    def check_local(self, url):
        """Return True if any host/path permutation hits the local prefix set.
        Caller must confirm a hit over the network before blocking."""
        for combo in self._canonicalize(url):
            digest = hashlib.sha256(combo.encode("utf-8")).digest()
            if digest[:4] in self._prefixes:
                return True
        return False

    def _canonicalize(self, url):
        parts = urlsplit(url)
        if parts.scheme not in ("http", "https") or not parts.hostname:
            return []
        host = parts.hostname.lower().strip(".")
        path = parts.path or "/"
        query = parts.query
        combos = []
        for h in self._host_suffixes(host):
            for p in self._path_prefixes(path, query):
                combos.append(h + p)
        return combos

    def _host_suffixes(self, host):
        labels = host.split(".")
        out = [host]
        i = max(0, len(labels) - 5)
        while i < len(labels) - 1:
            out.append(".".join(labels[i:]))
            i += 1
        return out

    def _path_prefixes(self, path, query):
        out = []
        if query:
            out.append(path + "?" + query)
        out.append(path)
        acc = "/"
        for seg in [s for s in path.strip("/").split("/") if s][:4]:
            acc += seg + "/"
            out.append(acc)
        return out


# Wired in browser_window.py's existing decide-policy handler:
#   if self.safe_browsing.check_local(uri) and self._confirm_threat(uri):
#       decision.ignore()
#       webview.load_html(self._build_threat_interstitial(uri), uri)
#       return True
```

**Migration.** Additive on every platform; no existing storage schema changes beyond a local prefix cache file/table per platform (excluded from any sync/backup). The shared mirror means one update pipeline. Where a host engine already had Safe Browsing, behavior only gets stricter (defense-in-depth), never weaker.


**Testing.** A single cross-platform conformance suite: assert that testsafebrowsing.appspot.com phishing/malware URLs are blocked with an interstitial on each of Android, iOS, macOS, Windows, Linux; assert clean URLs load; assert fail-open on mirror outage. On Linux specifically, regression-test that decide-policy still allows normal navigation and that the interstitial renders via load_html with the threat host named and HTML-escaped.


---

## B8. Developer Tools  `[developer-tools]`

**Overall feasibility:** platform-api-dependent


Developer Tools in Helix are entirely engine-owned and only partially opted into. Verified from source: macOS sets developerExtrasEnabled (macos/HelixBrowser/WebView.swift:49), Linux sets set_enable_developer_extras(True) (linux/src/browser_window.py:368), and Windows sets AreDevToolsEnabled=true (windows/HelixBrowser/MainWindow.xaml.cs:84) — so those three platforms get the full inherited inspector (right-click Inspect on WebKit; F12 Edge/Chromium DevTools on Windows, which includes Network, Performance, Memory, and Lighthouse for free). But Android NEVER calls WebView.setWebContentsDebuggingEnabled anywhere in android/app/src (zero source hits; only build-artifact noise), and iOS never sets WKWebView.isInspectable (zero references) — so the two flagship mobile platforms have NO inspection path at all. None of the five sub-features (DevTools, Network, Performance, Memory, Lighthouse) is first-party — they are panels of the host engine's inspector. The two highest-value, genuinely buildable fixes are one-line opt-ins: enable setWebContentsDebuggingEnabled on Android (P1) and isInspectable on iOS (P2), each gated behind a developer toggle for release-build security. Building a real in-app Network/Performance/Memory panel or a Lighthouse-equivalent requires the engine's debugging protocol/heap sampling and is requires-engine-fork (P3) — the honest path is to ship the engine's remote inspector (chrome://inspect, Safari Web Inspector, WebKitGTK inspector) plus, optionally, a clearly-labeled headers-only diagnostic network log fed by the existing shouldInterceptRequest/WebResourceRequested hooks, and to point users to external Lighthouse for audits. Relevant files: android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt, ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift, macos/HelixBrowser/WebView.swift:49, windows/HelixBrowser/MainWindow.xaml.cs:84, linux/src/browser_window.py:368.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| DevTools (Inspect Element / element + console inspector) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | S |
| Network Inspector (request/response waterfall, headers, timing) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | L |
| Performance Profiler (CPU/flame chart, frame timeline) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | low | XL |
| Memory Profiler (heap snapshot, allocation timeline) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | low | XL |
| Lighthouse-equivalent (perf/SEO/a11y/PWA audit) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ❌ | ✅ | low | XL |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B8.1 — Android DevTools/inspection completely off — setWebContentsDebuggingEnabled is never called anywhere in android/app/src (verified: zero hits in source; only build-artifact noise). Android System WebView ships a full Blink DevTools backend reachable from desktop Chrome at chrome://inspect, but ONLY if the app opts in with WebView.setWebContentsDebuggingEnabled(true). Helix never does, so the entire phone platform has no inspection path.

`P1` · feasibility: `feasible-in-webview` · ~0.5 eng-weeks


**Why it matters.** Web developers and power users cannot debug pages, inspect network requests, or read console errors on the most-used Helix platform. It also blocks Helix's own QA/support from diagnosing site-compat bugs in the field. Every competitor (Chrome/Edge/Brave on Android via chrome://inspect, Safari/Firefox on their mobile builds via remote inspector) supports this; Helix is the only one with it silently disabled. Enabling it is a one-line, engine-supported, zero-fork change — leaving it off is pure omission, not a platform limit.


**Recommended architecture.** Add the opt-in in engine/HelixWebView.kt (the WebView subclass) guarded by BuildConfig.DEBUG plus a hidden Settings toggle (SettingsActivity + Prefs) so release users can opt in. WebView.setWebContentsDebuggingEnabled is a static, process-wide call, so set it once at HelixWebView class init / Application.onCreate rather than per-instance. Do NOT enable unconditionally in release (it exposes the page to any USB-attached host) — gate it.


**Implementation plan.**
1. Add Prefs.isWebInspectionEnabled(context) flag (default = BuildConfig.DEBUG).
1. In HelixWebView's init/companion (or HelixApplication.onCreate), call WebView.setWebContentsDebuggingEnabled(prefs flag || BuildConfig.DEBUG).
1. Add a 'Developer / Enable USB web inspection' row in SettingsActivity that flips the pref and re-applies it (the static call takes effect for WebViews created afterwards, so prompt to restart or apply at next tab creation).
1. Surface a small persistent warning when enabled in a release build (security: any attached host can inspect the user's authenticated pages).
1. Document the chrome://inspect#devices workflow in the QA runbook.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt`:

```kotlin
// In HelixWebView.kt — enable the engine's built-in DevTools backend (Blink remote debugging).
// WebView.setWebContentsDebuggingEnabled is PROCESS-WIDE and static, so call it once.
companion object {
    private var inspectionConfigured = false

    /** Opt into the WebView's inherited DevTools backend (reachable from desktop chrome://inspect).
     *  Gated: always on in debug builds; in release only when the user explicitly enables it,
     *  because it lets any USB-attached host inspect the user's authenticated pages. */
    fun configureWebInspection(context: Context) {
        if (inspectionConfigured) return
        inspectionConfigured = true
        val enabled = BuildConfig.DEBUG || Prefs.isWebInspectionEnabled(context)
        WebView.setWebContentsDebuggingEnabled(enabled)
    }
}

// Call site (HelixApplication.onCreate or before first WebView is created):
//   HelixWebView.configureWebInspection(applicationContext)
```

**Migration.** No data migration. Pure runtime flag; default preserves current release behavior (off) while turning it on for debug builds. The Prefs key is additive — no schema/Room change.


**Testing.** Instrumented: assert WebView.setWebContentsDebuggingEnabled is invoked with true in a debug build. Manual: attach a desktop Chrome, open chrome://inspect#devices, confirm the Helix tab is inspectable and that toggling the Settings row off (then recreating a tab) removes it. Security test: confirm release builds default to NOT inspectable until the toggle is set, and the warning banner appears when enabled.


#### Gap B8.2 — iOS has no inspection at all — WKWebView.isInspectable is never set. On iOS/macOS 16.4+ Apple gates remote Safari Web Inspector behind webView.isInspectable = true; without it the page is invisible to Web Inspector. ios/HelixBrowser has zero references to isInspectable / developerExtras (verified). So iOS is the second platform with no debugging path, while macOS/Linux/Windows already enable their inspectors.

`P2` · feasibility: `feasible-in-webview` · ~0.5 eng-weeks


**Why it matters.** iOS is a flagship consumer platform; developers building/testing sites in Helix on iPhone cannot inspect anything, and Helix support cannot reproduce/diagnose iOS-only WebKit rendering bugs. Safari, and any modern WKWebView-based browser that opts in, exposes this. It is a single property assignment guarded by an availability check — not a fork, not even a new screen.


**Recommended architecture.** Set isInspectable on the WKWebView right after creation in BrowserViewController.swift (the per-tab WKWebView factory), guarded by #available(iOS 16.4,*) and a DEBUG / Prefs flag. Mirror the existing macOS approach (developerExtrasEnabled in WebView.swift:49) so behavior is consistent across Apple platforms.


**Implementation plan.**
1. Locate the WKWebView creation in BrowserViewController.swift (config + init).
1. After init, add `if #available(iOS 16.4, *) { webView.isInspectable = (DEBUG || Prefs.webInspectionEnabled) }`.
1. Add a Prefs.webInspectionEnabled flag + a Settings row (SettingsViewController) to opt in for release, defaulting off (security parity with the Android plan).
1. Document the Mac Safari > Develop > [device] remote-inspect workflow for QA.
1. Verify the macOS sibling already sets developerExtrasEnabled (WebView.swift:49) — keep wording/UX consistent.

**Code example** — `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift`:

```swift
// After constructing each tab's WKWebView in BrowserViewController.
// iOS 16.4+ requires isInspectable=true for Safari Web Inspector to see the page.
let webView = WKWebView(frame: .zero, configuration: config)
if #available(iOS 16.4, *) {
    #if DEBUG
    webView.isInspectable = true
    #else
    webView.isInspectable = Prefs.shared.isWebInspectionEnabled
    #endif
}
// Then connect from a Mac: Safari > Develop > <device> > <Helix page>.
```

**Migration.** None. Additive UserDefaults flag (Prefs), default false in release to preserve current behavior. No persistence schema change.


**Testing.** Manual: DEBUG build on a device, open Safari Web Inspector from a paired Mac, confirm the Helix WebView appears and DOM/console/network panels work. Verify release build is NOT inspectable until the Settings toggle is enabled. Static: confirm the #available guard compiles on the deployment target (iOS .xcodeproj is generated externally — flag that this is review-only until an Xcode build, per the project's verification-asymmetry note).


#### Gap B8.3 — Network Inspector / Performance / Memory / Lighthouse panels cannot be built INSIDE the WebView shell — they require driving the engine's debugging protocol, which Helix does not own and cannot embed in-app on most platforms. The network waterfall, CPU flame chart, heap snapshots, and Lighthouse audits that competitors ship are panels of the engine's own inspector (Blink DevTools front-end over CDP on Android/Windows; WebKit Web Inspector over the Inspector protocol on iOS/macOS/Linux). Helix today only flips the inspector ON for macOS/Linux/Windows and OFF for Android/iOS; it never adds any first-party network/perf/memory UI.

`P3` · feasibility: `requires-engine-fork` · ~2 eng-weeks


**Why it matters.** These are the marquee 'developer tools' a power user expects, but they are the clearest engine-owned surface in this whole product. Re-implementing a real Network panel means tapping the WebView's request stream (Android WebViewClient.shouldInterceptRequest already sees URLs but NOT response bodies, timing, or HTTP/2 frames; WKWebView exposes almost nothing). A genuine Performance/Memory profiler needs sampling the JS engine's call stacks and heap — only the engine can do that. So on Android/iOS the honest answer is: ship the engine's existing remote inspector (the two gaps above) rather than build first-party panels; a from-scratch in-app Network panel would be a degraded, headers-only subset and a perpetual maintenance trap.


**Recommended architecture.** Recommended realistic path, no fork: (1) Rely on the engine's remote inspector enabled by the two gaps above (chrome://inspect for Android/Windows-WebView2, Safari Web Inspector for iOS/macOS, WebKitGTK inspector for Linux) — these already contain Network, Timelines/Performance, and Memory/Heap panels for free. (2) Optionally add a LIGHTWEIGHT first-party 'Network log' as a diagnostic-only subset: an AdBlockEngine-adjacent interceptor (Android HelixWebViewClient.shouldInterceptRequest, Windows CoreWebView2.WebResourceRequested/Responded, Linux WebKit2 resource-load-started) that records URL + method + status + size into a HelixNetworkLog and renders it in a simple RecyclerView. Be explicit that this is NOT a real Network panel (no response bodies on Android, no precise timing) and NOT a substitute for Lighthouse.


**Implementation plan.**
1. Do gaps #1 and #2 first — they deliver real Network/Performance/Memory/Console via the engine inspector at near-zero cost.
1. If a first-party in-app diagnostic is still wanted, add HelixNetworkLog (ring buffer) fed by the existing per-platform resource-request hooks (Android shouldInterceptRequest; Windows WebResourceResponseReceived; Linux WebKit2 'resource-load-started').
1. Render it in a minimal list UI; clearly label it 'Diagnostics' not 'DevTools'.
1. Do NOT attempt a CPU/heap profiler or Lighthouse in-app — document them as engine-fork-only and point users to the remote inspector / external Lighthouse CLI against the page URL.
1. For 'Lighthouse-equivalent', the realistic offering is a link/instructions to run Lighthouse externally (PageSpeed Insights / lighthouse CLI) on the current tab URL — not an embedded audit engine.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/HelixNetworkLog.kt`:

```kotlin
package com.helix.browser.engine

import android.webkit.WebResourceRequest

/** Lightweight DIAGNOSTIC network log — NOT a real Network panel.
 *  Android's shouldInterceptRequest sees the request line but NOT response
 *  bodies, timing, or HTTP/2 frames (those are engine-owned), so this is a
 *  bounded, headers-and-URL-only ring buffer for support/QA — explicitly not
 *  Chrome's Network tab. Fed from HelixWebViewClient.shouldInterceptRequest. */
object HelixNetworkLog {
    data class Entry(val method: String, val url: String, val ts: Long)

    private const val CAP = 500
    private val ring = ArrayDeque<Entry>(CAP)

    @Synchronized
    fun record(request: WebResourceRequest) {
        if (ring.size >= CAP) ring.removeFirst()
        ring.addLast(Entry(request.method, request.url.toString(), System.currentTimeMillis()))
    }

    @Synchronized
    fun snapshot(): List<Entry> = ring.toList()

    @Synchronized
    fun clear() = ring.clear()
}
// Wire in HelixWebViewClient.shouldInterceptRequest: HelixNetworkLog.record(request)
// before returning the (possibly null) intercept response.
```

**Migration.** No persistence; the log is an in-memory ring buffer cleared on process death. No schema/back-compat impact. If later persisted, gate behind the same developer toggle and exclude from cloud backup (consistent with existing DB/prefs backup exclusion).


**Testing.** Unit test the ring buffer cap/eviction and snapshot ordering. Manual: load a page, confirm entries appear; confirm clear() empties it. Crucially, document/verify the limitation set (no bodies/timing on Android) so it is not mis-sold as a Network panel. For the engine inspectors, regression-test that enabling them (gaps #1/#2) actually exposes working Network/Performance/Memory panels via chrome://inspect and Safari Web Inspector.


---

## B9. Enterprise Management  `[enterprise-management]`

**Overall feasibility:** partially-feasible


Enterprise Management is entirely absent in Helix across all five platforms - verified by ground-truth grep, not the inventory. There is NO android:restrictions resource (android/app/src/main/res/xml/ holds only backup_rules, data_extraction_rules, file_paths, network_security_config, preferences), NO RestrictionsManager/getApplicationRestrictions call anywhere in android/app/src/main/java, NO MSAL/AppAuth/EMM dependency in build.gradle, and NO policy/managed keys in any Prefs (windows/HelixBrowser/Utils/Prefs.cs, linux/src/prefs.py). Every setting is end-user-mutable local state; the only 'managed/policy/certificate' source hits are false positives (FileProvider meta-data, GTK scroll-policy, WKWebView/WebViewClient TLS cert validation at HelixWebViewClient.kt:170 and WebView.swift:346, ACTION_MANAGE_DEFAULT_APPS_SETTINGS). Competitively this is the widest B2B/EDU gap: Chrome/Edge ship full Group Policy + Cloud management; Edge additionally owns the deepest moat with transparent Azure AD/Entra PRT SSO and conditional access. The pragmatic remediation order is (P1) a cross-platform managed-config layer (ManagedPolicy reading Android RestrictionsManager / Apple com.apple.configuration.managed / Windows HKLM Policies / Linux /etc) that is the substrate for everything else and is fully feasible-in-webview at ~5 weeks; (P1) enterprise root-CA trust which is platform-api-dependent (must defer to the OS/MDM cert store, never blind-trust) at ~4 weeks; and (P2) SSO, where ONLY an app-account OIDC/PKCE flow plus Windows WebView2 IWA-allowlist is feasible - transparent PRT/Kerberos/conditional-access SSO requires the Edge/Chromium identity + network stack and is explicitly requires-engine-fork, out of scope for a WebView shell.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Group Policies (admin-pushed browser config: homepage, blocklists, disabled features) | ❌ missing | ✅ | ✅ | ✅ | ✅ | 🟡 | ❌ | **high** | L |
| Device Management / MDM (managed-app config, EMM enrollment, app restrictions) | ❌ missing | ✅ | ✅ | 🟡 | 🟡 | ✅ | ❌ | **high** | M |
| Remote Configuration (server-fetched policy, dynamic update without redeploy) | ❌ missing | ✅ | ✅ | 🟡 | 🟡 | ❌ | ❌ | med | M |
| Enterprise Certificates (custom root CA injection / private trust store) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | M |
| SSO (SAML/OIDC integrated auth, IWA, Kerberos negotiate) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | **high** | XL |
| Azure AD / Entra ID (conditional access, device-trust, primary-refresh-token SSO) | ❌ missing | ✅ | ✅ | 🟡 | ❌ | 🟡 | ❌ | **CRIT** | XL |
| Okta (org sign-in, SCIM provisioning, device assurance) | ❌ missing | ✅ | ✅ | 🟡 | 🟡 | 🟡 | ❌ | med | L |
| Google Workspace (managed Chrome enrollment, Cloud policy, context-aware access) | ❌ missing | ✅ | 🟡 | ❌ | ❌ | ❌ | ❌ | med | L |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B9.1 — No managed-config / app-restrictions ingestion on any platform. Verified: no android:restrictions resource exists (android/app/src/main/res/xml/ contains only backup_rules, data_extraction_rules, file_paths, network_security_config, preferences), no RestrictionsManager/getApplicationRestrictions call anywhere in android/app/src/main/java, and no policy/managed keys in any Prefs (windows/HelixBrowser/Utils/Prefs.cs, linux/src/prefs.py, Android utils). Every setting is end-user-mutable local state (SharedPreferences / UserDefaults / SQLite / prefs.json).

`P1` · feasibility: `feasible-in-webview` · ~5 eng-weeks


**Why it matters.** This is the single highest-leverage enterprise gap because it is the substrate every other enterprise feature reads from. Without a managed-policy layer, an admin cannot pin the homepage/search engine, force HTTPS-only, disable incognito, mandate the ad/tracker blocklists, or lock any toggle - the user can always override. No IT department can deploy Helix at scale via Intune/Jamf/Workspace ONE, which excludes Helix from the entire B2B/EDU market. It is also a prerequisite for Group Policy, Remote Config, Enterprise Certs, and SSO allowlists.


**Recommended architecture.** Add a platform-native managed-config source feeding a new precedence-aware settings reader. Android: register a BroadcastReceiver for ACTION_APPLICATION_RESTRICTIONS_CHANGED, read RestrictionsManager.getApplicationRestrictions(), ship res/xml/app_restrictions.xml declared via <meta-data android:name="android.content.APP_RESTRICTIONS"> in AndroidManifest.xml. Introduce a ManagedPolicy object (android/app/src/main/java/com/helix/browser/enterprise/ManagedPolicy.kt) that wraps the existing Prefs/SharedPreferences reads so callers get policy-over-user precedence and a per-key isManaged() to lock the UI control in SettingsActivity.kt. iOS/macOS: read UserDefaults key com.apple.configuration.managed (the MDM AppConfig channel) in a ManagedPolicy.swift and layer it above the DataManager/Prefs reads. Windows: read HKLM\SOFTWARE\Policies\Helix via a PolicyStore.cs above Utils/Prefs.cs. Linux: read /etc/helix-browser/policies/*.json in prefs.py.


**Implementation plan.**
1. Define the canonical policy schema (keys: HomepageUrl, DefaultSearchEngine, ForceHttpsOnly, IncognitoModeAvailability, AdBlockEnabledLocked, ManagedBookmarks, URLBlocklist, URLAllowlist) shared across platforms as a JSON spec doc.
1. Android: add res/xml/app_restrictions.xml enumerating those keys with restrictionType; declare the APP_RESTRICTIONS meta-data in AndroidManifest.xml application block.
1. Android: create enterprise/ManagedPolicy.kt that holds a Bundle from RestrictionsManager, registered/refreshed via a BroadcastReceiver on ACTION_APPLICATION_RESTRICTIONS_CHANGED.
1. Refactor utils/Prefs reads used by MainActivity/SettingsActivity to go through ManagedPolicy.get(key) which returns managed value if present else user value; expose isManaged(key).
1. SettingsActivity.kt: disable+annotate ('Managed by your organization') any control whose key isManaged().
1. Mirror with ManagedPolicy.swift (iOS+macOS, reading com.apple.configuration.managed), PolicyStore.cs (Windows registry), and policy loading in prefs.py (Linux /etc dir).
1. Wire enforcement points: omnibox/home (HomepageUrl), HelixWebViewClient HTTPS upgrade (ForceHttpsOnly), TabManager incognito creation (IncognitoModeAvailability), AdBlockEngine (locked enable).

**Code example** — `android/app/src/main/java/com/helix/browser/enterprise/ManagedPolicy.kt`:

```kotlin
package com.helix.browser.enterprise

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle

/**
 * Single source of truth for admin-pushed (EMM/MDM) configuration.
 * Managed values take precedence over user SharedPreferences and lock the UI.
 */
object ManagedPolicy {
    const val KEY_HOMEPAGE = "HomepageUrl"
    const val KEY_SEARCH_ENGINE = "DefaultSearchEngine"
    const val KEY_FORCE_HTTPS = "ForceHttpsOnly"
    const val KEY_INCOGNITO = "IncognitoModeAvailability" // 0=enabled,1=disabled

    @Volatile private var restrictions: Bundle = Bundle.EMPTY

    /** Call on app start and from the ACTION_APPLICATION_RESTRICTIONS_CHANGED receiver. */
    fun refresh(context: Context) {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
        restrictions = rm?.applicationRestrictions ?: Bundle.EMPTY
    }

    fun isManaged(key: String): Boolean = restrictions.containsKey(key)

    /** Managed string wins; otherwise fall back to the user-set default. */
    fun string(key: String, userValue: String): String =
        if (restrictions.containsKey(key)) restrictions.getString(key, userValue) else userValue

    fun bool(key: String, userValue: Boolean): Boolean =
        if (restrictions.containsKey(key)) restrictions.getBoolean(key, userValue) else userValue

    fun incognitoDisabled(): Boolean = restrictions.getInt(KEY_INCOGNITO, 0) == 1
}
```

**Migration.** Purely additive. Default empty restrictions Bundle => zero behavior change for consumer installs; all existing Prefs/SharedPreferences values remain the fallback, so no data migration or schema bump is needed. On Android the Room DB is untouched. Back-compat: ManagedPolicy.get(key) returns the user value verbatim when no policy is set, so unmanaged devices behave identically to today.


**Testing.** Unit-test ManagedPolicy precedence (managed-present vs absent) with a mocked RestrictionsManager. Instrumented test: push a test config via `adb shell dpm set-app-restrictions` (or TestDPC) and assert SettingsActivity locks the controls and the homepage/HTTPS/incognito enforcement fires. Manual: enroll a device in a free Intune/TestDPC tenant, push app_restrictions, verify the 'Managed by your organization' annotation and that the user cannot override. iOS/macOS: inject com.apple.configuration.managed via a test mobileconfig profile and assert ManagedPolicy.swift reads it.


#### Gap B9.2 — No enterprise root-CA / private trust store. HelixWebViewClient.kt:170 does the opposite - it hardens cert handling with a full-page interstitial for untrusted roots and refuses override in HTTPS-Only. There is no path for an admin to inject a corporate/MITM-proxy CA, and on Android (WebView) and Windows (WebView2) the engine only trusts the OS store, which an unenrolled app cannot extend.

`P1` · feasibility: `platform-api-dependent` · ~4 eng-weeks


**Why it matters.** Most enterprises run TLS-inspecting proxies (Zscaler, Palo Alto, Netskope) whose root CA must be trusted or every HTTPS site throws a cert error. Today Helix would block all traffic behind such a proxy with the interstitial, making it unusable on corporate networks. This is a hard blocker for any managed deployment, independent of any other feature.


**Recommended architecture.** Trust-store extension is engine/OS-owned, so the realistic design is to (a) DEFER to the OS/MDM-installed CA store rather than re-implement, and (b) on platforms where the WebView ignores user-installed CAs, surface a clear managed-trust path. Android: respect user/admin CAs by adding a <certificates src="user"/> trust-anchors block (gated to managed installs) in res/xml/network_security_config.xml, and read an admin EnterpriseRootCA policy key via ManagedPolicy to relax HelixWebViewClient.onReceivedSslError ONLY for admin-pinned cert fingerprints. iOS/macOS WKWebView: handle the .serverTrust challenge in the existing didReceive challenge delegate (WebView.swift:346) by evaluating against an MDM-delivered anchor set from ManagedPolicy. Windows: WebView2 inherits the Windows cert store, so document MDM cert deployment - no code change needed. Linux: WebKitGTK uses the system GTlsDatabase; honor /etc CA bundle.


**Implementation plan.**
1. Add policy keys EnterpriseRootCAFingerprints (SHA-256 pins) and AllowUserInstalledCAs to the ManagedPolicy schema from gap 1.
1. Android: add a managed-only network_security_config trust-anchors variant including 'user' certs; do NOT enable for consumer builds.
1. Android: in HelixWebViewClient.onReceivedSslError, if the offending cert chain matches an admin-pinned fingerprint from ManagedPolicy, proceed() instead of showing the interstitial; otherwise keep current fail-closed behavior.
1. iOS/macOS: in the WebView.swift didReceive serverTrust challenge, add SecTrustSetAnchorCertificates with MDM-delivered anchors before SecTrustEvaluateWithError.
1. Document Windows (WebView2 -> Windows store via MDM) and Linux (system trust) deployment - no app trust code.
1. Add UI badge in the page-info/security sheet indicating 'Connection secured via your organization's certificate'.

**Code example** — `macos/HelixBrowser/WebView.swift`:

```swift
// In the existing URLAuthenticationChallenge delegate (around WebView.swift:346),
// honor MDM-delivered enterprise anchors before the system evaluation.
func webView(_ webView: WKWebView,
             didReceive challenge: URLAuthenticationChallenge,
             completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
    guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
          let trust = challenge.protectionSpace.serverTrust else {
        completionHandler(.performDefaultHandling, nil); return
    }
    // Inject admin-pushed root CAs (from MDM com.apple.configuration.managed) as extra anchors.
    let anchors = ManagedPolicy.shared.enterpriseAnchorCertificates() // [SecCertificate]
    if !anchors.isEmpty {
        SecTrustSetAnchorCertificates(trust, anchors as CFArray)
        SecTrustSetAnchorCertificatesOnly(trust, false) // also keep system roots
    }
    var error: CFError?
    if SecTrustEvaluateWithError(trust, &error) {
        completionHandler(.useCredential, URLCredential(trust: trust))
    } else {
        // Preserve existing fail-closed behavior: no blind trust.
        completionHandler(.cancelAuthenticationChallenge, nil)
    }
}
```

**Migration.** Additive and gated behind managed installs. Consumer builds keep the current fail-closed cert behavior (HelixWebViewClient interstitial, system-only anchors) byte-for-byte because anchors list is empty and the managed network_security_config variant is not shipped to non-managed builds. No persisted data changes.


**Testing.** Stand up a TLS-inspection proxy (mitmproxy) with a custom root; without policy, assert Helix shows the interstitial (unchanged). With the CA pushed via MDM/policy, assert pages load and the security sheet shows the org-cert badge. Negative test: a NON-pinned bad cert must still be rejected (no regression of the WebViewClient:170 hardening). iOS: verify via a test mobileconfig delivering a PEM anchor.


#### Gap B9.3 — No SSO / integrated authentication. Helix only has HTTP Basic/Digest auth (Android onReceivedHttpAuthRequest with WebViewDatabase). There is no SAML/OIDC broker, no Negotiate/Kerberos, no Azure AD primary-refresh-token bridge, and no MSAL/AppAuth dependency in build.gradle. Conditional Access / device-trust SSO is structurally impossible in a WebView shell.

`P2` · feasibility: `requires-engine-fork` · ~10 eng-weeks


**Why it matters.** Enterprises gate SaaS apps behind Azure AD/Okta conditional access that requires a managed/compliant browser identity. Edge gets seamless SSO via the OS broker (WAM/PRT); Helix users would be forced to re-auth on every app and would FAIL device-compliance conditional-access policies, locking them out of corporate M365/Okta-protected apps entirely. This is the deepest enterprise moat and the reason Edge dominates managed Windows fleets.


**Recommended architecture.** Full transparent SSO (PRT injection into network requests, device-bound credentials, Negotiate/Kerberos in the WebView network stack) is owned by the engine's network stack and the OS identity broker - it CANNOT be re-implemented in a WebView wrapper without forking Blink/WebKit. Realistic constrained subset: (1) a foreground OIDC/SAML login flow using the OS auth broker for Helix's OWN account/sync (AppAuth on Android, ASWebAuthenticationSession on iOS/macOS, WebAuthenticationBroker on Windows), surfacing org sign-in but NOT injecting PRT into arbitrary site traffic; (2) on Windows, rely on WebView2's inherited Windows-Integrated-Auth (it already participates in the OS broker for allowlisted hosts) and expose an AuthServerAllowlist policy key. Add an EnterpriseAuth module (e.g. android/.../enterprise/EnterpriseAuth.kt using net.openid:appauth) for app-level SSO only.


**Implementation plan.**
1. Scope decision: ship app-account SSO + Windows IWA allowlist; explicitly mark site-traffic PRT/Kerberos as out-of-scope (engine fork).
1. Add AuthServerAllowlist and AuthNegotiateDelegateAllowlist policy keys to ManagedPolicy (Windows WebView2 honors these via CoreWebView2 settings).
1. Android: add net.openid:appauth dependency; create enterprise/EnterpriseAuth.kt performing an OIDC code+PKCE flow against an admin-configured issuer for Helix sync/account.
1. iOS/macOS: implement the same with ASWebAuthenticationSession in a new EnterpriseAuth.swift.
1. Windows: set CoreWebView2 IWA allowlist from PolicyStore so allowlisted intranet hosts get seamless Negotiate via the OS.
1. Document the hard limit: transparent conditional-access/device-trust SSO requires the Edge/Chromium identity stack and is not buildable in Helix's WebView shell.

**Code example** — `android/app/src/main/java/com/helix/browser/enterprise/EnterpriseAuth.kt`:

```kotlin
package com.helix.browser.enterprise

import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.*

/**
 * App-level enterprise SSO (OIDC code+PKCE) for Helix's own account/sync.
 * NOTE: this does NOT inject tokens into arbitrary site traffic - transparent
 * PRT/Kerberos SSO is engine/OS-owned and out of scope for a WebView shell.
 */
class EnterpriseAuth(context: Context) {
    private val service = AuthorizationService(context)

    fun buildSignInIntent(issuerHost: String, clientId: String, redirect: Uri): Intent {
        val config = AuthorizationServiceConfiguration(
            Uri.parse("https://$issuerHost/authorize"),
            Uri.parse("https://$issuerHost/token")
        )
        val request = AuthorizationRequest.Builder(
            config, clientId, ResponseTypeValues.CODE, redirect
        ).setScopes("openid", "profile", "email").build() // PKCE auto-added
        return service.getAuthorizationRequestIntent(request)
    }

    fun handleResponse(data: Intent, onToken: (String?) -> Unit) {
        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (resp == null) { onToken(null); return }
        service.performTokenRequest(resp.createTokenExchangeRequest()) { tr, _ ->
            onToken(tr?.accessToken)
        }
    }
}
```

**Migration.** New module, no impact on existing local-only storage. Tokens stored in the OS keychain/EncryptedSharedPreferences, not the Room/UserDefaults stores. Consumer builds never invoke EnterpriseAuth unless an admin configures an issuer via ManagedPolicy, so behavior is unchanged for non-enterprise users.


**Testing.** Integration test the OIDC flow against a test Okta/Entra tenant (auth code + PKCE round-trip). Windows: configure an IWA allowlist policy and verify an intranet host gets 401->seamless Negotiate without a prompt. Explicit non-goal test documented: confirm Helix does NOT and cannot satisfy a conditional-access device-trust policy that requires Edge - so QA does not chase an impossible requirement.


---

## B10. Extension Marketplace  `[extension-marketplace]`

**Overall feasibility:** requires-engine-fork


Verified against the real codebase: Helix has ZERO extension-marketplace infrastructure on every platform — no runtime engine, no store, no review pipeline, no signing, no malware/automated review. grep across android/ios/macos/linux/windows source (excluding /build/) returns no webextension/chrome.runtime/manifest.json/content_script/.crx/.xpi hits; every 'extension' token is a Swift/Kotlin language keyword (e.g. BrowserViewController.swift:519, BrandColors.swift:14) or file-extension string handling (MainActivity.kt:2393). The only 'signing' is Play-billing RSA (BillingManager.kt:28) and the only 'malware'/'safe browsing' reference is the inherited WebView flag (HelixWebView.kt:85-87). All six competitors (Chrome/Edge/Brave/Firefox/Safari/Arc) ship full extension ecosystems, so the parity gap is total. CRITICAL feasibility fact: the runtime engine requires-engine-fork on 4 of 5 platforms — Android System WebView, WKWebView (iOS/macOS), and WebKit2GTK 4.0 expose NO extension API; only WebView2 (Windows, Microsoft.Web.WebView2 1.0.2651.64, csproj:17) offers ICoreWebView2Profile7.AddBrowserExtensionAsync, which Helix does not call. Honest recommendation: do NOT build a marketplace (review/malware/signing pipelines presuppose a third-party submission service + security team that cannot live in a WebView client). The only realistic, low-risk path is a Windows-only, feature-flagged, hash-pinned curated allow-list of 3-5 Helix-vetted bundled extensions via a new Engine/ExtensionManager.cs, with the other four platforms explicitly documented as extension-incapable due to engine limitations. All four remediation items are correctly P3 — this is a strategic/architectural ceiling, not a ship blocker, and should be de-scoped rather than partially faked.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Extension Store (discovery, catalog, install UI) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | XL |
| Review Pipeline (human + policy review before publish) | ❌ missing | ✅ | ✅ | 🟡 | ✅ | ✅ | 🟡 | med | XL |
| Signing System (publisher signing / package integrity) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Malware Detection (static/dynamic scan of submissions) | ❌ missing | ✅ | ✅ | 🟡 | ✅ | ✅ | ❌ | **high** | XL |
| Automated Review (CI scanning of API/permission abuse) | ❌ missing | ✅ | ✅ | 🟡 | ✅ | 🟡 | ❌ | med | L |
| Runtime Extension Engine (WebExtension/MV3 execution) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **CRIT** | XL |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B10.1 — Runtime Extension Engine — there is no WebExtension/MV3 runtime in any Helix shell, and the host WebViews cannot provide one. Verified absent: zero hits for webextension/chrome.runtime/manifest.json/content_script/.crx/.xpi across android/ios/macos/linux/windows source (grep excluded /build/). The only 'extension' tokens are Swift/Kotlin language keywords (e.g. ios/.../BrowserViewController.swift:519, macos/.../BrandColors.swift:14) and file-extension string handling (MainActivity.kt:2393 getMimeTypeFromExtension). Android System WebView, WKWebView (iOS/macOS), and WebKit2GTK 4.0 (Linux) expose NO extension-loading API whatsoever. Only WebView2 (windows, Microsoft.Web.WebView2 1.0.2651.64 in HelixBrowser.csproj:17) exposes ICoreWebView2Profile7.AddBrowserExtensionAsync — and Helix does not call it (no CoreWebView2Profile/BrowserExtension references in windows/*.cs).

`P3` · feasibility: `requires-engine-fork` · ~4 eng-weeks


**Why it matters.** Extensions (uBlock Origin, password managers, Dark Reader, Vimium) are the #1 reason power users stay on Chrome/Firefox. A browser with no extension runtime is a non-starter for the enthusiast segment and caps Helix at 'casual secondary browser'. Four of five Helix platforms physically cannot run extensions without forking their engine; claiming otherwise would be dishonest. This single gap makes a true cross-platform 'Extension Marketplace' impossible.


**Recommended architecture.** On 4 of 5 platforms this is impossible without replacing the system WebView with an embedded Chromium (e.g. CEF / chromiumembedded, or GeckoView on Android) — i.e. abandoning the thin-shell architecture and shipping a ~150-200MB engine per platform, owning the security update treadmill. The ONLY realistic, honest path is a Windows-only constrained pilot using the native WebView2 extension API: a new windows/HelixBrowser/Engine/ExtensionManager.cs that wraps CoreWebView2Profile.AddBrowserExtensionAsync, plus a curated, side-loaded, allow-listed set of unpacked MV3 extensions Helix itself vets and bundles. No user-facing 'install arbitrary extension' marketplace — that requires the review/malware/signing pipeline below, which is its own multi-quarter effort. Recommend NOT building a marketplace; instead ship a small bundled allow-list of 3-5 vetted extensions on Windows only and document the limitation everywhere else.


**Implementation plan.**
1. Decision gate: confirm leadership accepts that extensions are Windows-only (WebView2) and that other platforms stay extension-less; document this in AUDIT_REPORT.md parity section so it stops being re-flagged.
1. Add windows/HelixBrowser/Engine/ExtensionManager.cs wrapping CoreWebView2Profile.AddBrowserExtensionAsync for a hardcoded allow-list of unpacked extension folders bundled in the MSIX.
1. Bundle 3-5 Helix-vetted MV3 extensions (each pinned to a reviewed commit) under windows/HelixBrowser/Assets/extensions/<id>/.
1. Add a minimal Extensions toggle list in the (still-missing) Windows Settings UI — enable/disable only, no install-from-web.
1. Gate behind a feature flag in Utils/Prefs.cs and ship dark; do not advertise as a 'marketplace'.
1. Explicitly mark Android/iOS/macOS/Linux as 'extensions not supported (engine limitation)' in their about/settings screens to set expectations.

**Code example** — `windows/HelixBrowser/Engine/ExtensionManager.cs`:

```csharp
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using Microsoft.Web.WebView2.Core;

namespace HelixBrowser.Engine
{
    // Windows-ONLY. WebView2 is the only engine in the Helix stack that can host
    // unpacked MV3 extensions (ICoreWebView2Profile7.AddBrowserExtensionAsync).
    // This loads a fixed, Helix-vetted allow-list bundled in the MSIX; it is NOT
    // a marketplace and never side-loads arbitrary user-supplied packages.
    public sealed class ExtensionManager
    {
        // id -> relative asset folder of the unpacked, reviewed extension
        private static readonly Dictionary<string, string> AllowList = new()
        {
            ["ublock-origin"] = "Assets/extensions/ublock-origin",
            ["dark-reader"]    = "Assets/extensions/dark-reader",
        };

        public async Task LoadBundledAsync(CoreWebView2 webView)
        {
            // Profile7 carries the extension API; older runtimes return null -> no-op.
            var profile = webView?.Profile as CoreWebView2Profile;
            if (profile == null) return;
            profile.AreBrowserExtensionsEnabled = true;

            var baseDir = AppContext.BaseDirectory;
            foreach (var kvp in AllowList)
            {
                var path = Path.Combine(baseDir, kvp.Value);
                if (!Directory.Exists(path)) continue;  // not bundled in this build
                try
                {
                    await profile.AddBrowserExtensionAsync(path);
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine(
                        $"Helix: failed to load extension '{kvp.Key}': {ex.Message}");
                }
            }
        }
    }
}
```

**Migration.** No data migration: greenfield, additive, feature-flagged off by default. Bundled extensions live in the MSIX so there is no user-installed state to migrate. If later removed, simply delete the Assets/extensions/* folders and the toggle list; no schema in Data/DatabaseManager.cs is touched.


**Testing.** Manual MSBuild smoke test on Windows (the platform is currently static-only per project notes — first establish a real build). Verify AddBrowserExtensionAsync succeeds against the WebView2 runtime version pinned at 1.0.2651.64, that disabling the flag yields zero extension processes, and that incognito (ephemeral) profiles do NOT load the extensions. Confirm graceful no-op on machines whose WebView2 runtime predates Profile7.


#### Gap B10.2 — Signing System + Package Integrity — Helix has no extension package format, no publisher key registry, and no signature verification. The only signing code in the repo is Google Play purchase verification (BillingManager.kt:28, SHA1withRSA), which is unrelated to extensions.

`P3` · feasibility: `partially-feasible` · ~2 eng-weeks


**Why it matters.** Without signing, any extension distribution channel is a malware vector: tampered packages, supply-chain injection, and impersonation of legitimate publishers. Chrome/Edge/Firefox/Safari all enforce signed packages; an unsigned channel would be an immediate security and store-rejection liability. However this only matters IF Helix runs extensions at all — which (per the runtime gap) it largely cannot.


**Recommended architecture.** Only meaningful as a precondition to the Windows-only constrained pilot. Recommend NOT building a publisher-signing PKI (that presupposes a third-party submission marketplace which Helix has no review staff for). Instead enforce integrity on the bundled allow-list via a build-time manifest of SHA-256 hashes checked at load in ExtensionManager.cs. A real publisher-signing system would add a backend (key registry, CRL, .helixext package format) — out of scope until a marketplace exists, which it should not for a 5-engine shell.


**Implementation plan.**
1. Generate a build-time extensions.lock manifest (id -> SHA-256 of the extension folder tree) checked into windows/HelixBrowser/Assets/extensions/.
1. In ExtensionManager.LoadBundledAsync, recompute the folder hash and refuse to load on mismatch (defense against on-disk tampering of the installed MSIX).
1. Sign the MSIX itself (standard Windows app signing) so the whole bundle, extensions included, inherits OS-level integrity.
1. Document that arbitrary third-party extension submission/signing is explicitly out of scope.

**Code example** — `windows/HelixBrowser/Engine/ExtensionManager.cs`:

```csharp
using System.Security.Cryptography;

// Added to ExtensionManager: verify a bundled extension folder against the
// build-time hash before handing it to WebView2. Rejects tampered installs.
private static bool VerifyIntegrity(string path, string expectedSha256)
{
    using var sha = SHA256.Create();
    var files = Directory.GetFiles(path, "*", SearchOption.AllDirectories);
    System.Array.Sort(files, System.StringComparer.Ordinal);
    foreach (var f in files)
    {
        var rel = System.Text.Encoding.UTF8.GetBytes(Path.GetRelativePath(path, f));
        sha.TransformBlock(rel, 0, rel.Length, null, 0);
        var bytes = File.ReadAllBytes(f);
        sha.TransformBlock(bytes, 0, bytes.Length, null, 0);
    }
    sha.TransformFinalBlock(System.Array.Empty<byte>(), 0, 0);
    var actual = System.Convert.ToHexString(sha.Hash!).ToLowerInvariant();
    return actual == expectedSha256.ToLowerInvariant();
}
```

**Migration.** None — integrity manifest is a build artifact, no persisted user state. Back-compat trivial: missing manifest -> treat as no-bundled-extensions (fail closed).


**Testing.** Unit test VerifyIntegrity with a known folder fixture (matching + tampered cases). Confirm a single-byte change to any bundled file flips the result to reject. Validate the load path refuses tampered folders without crashing the browser.


#### Gap B10.3 — Review Pipeline + Automated Review + Malware Detection — Helix has no submission backend, no automated scanner, and no human review process. These are organizational/cloud-infrastructure capabilities, not browser-shell code; nothing in the repo touches them. 'malware' appears only as a comment about the inherited WebView Safe Browsing flag (HelixWebView.kt:85-87).

`P3` · feasibility: `platform-api-dependent` · ~1 eng-weeks


**Why it matters.** A review/scan/malware pipeline only exists to police a third-party submission marketplace. Building one is a multi-team, ongoing-cost commitment (Chrome and Mozilla run dedicated security teams plus automated reanalysis of already-published items). For a thin 5-engine WebView shell that can only run extensions on one platform via a curated allow-list, standing up review infrastructure is unjustifiable — the curated allow-list IS the review process (done once, by Helix, manually).


**Recommended architecture.** Do NOT build a submission/review/malware backend. The honest architecture is: a documented internal manual-vetting checklist for the handful of bundled Windows extensions (permission audit, source review at a pinned commit, hash pinning via the Signing gap above). If a marketplace is ever pursued, it requires a cloud service stack (submission API, sandboxed dynamic-analysis runners, static permission/API-abuse linters, human reviewers, takedown tooling) that lives entirely outside this repository and outside the WebView shell — treat as a separate product with its own staffing, not a Helix client feature.


**Implementation plan.**
1. Author an internal EXTENSION_VETTING.md checklist (permissions requested, host permissions, remote-code prohibitions, pinned upstream commit, manual diff review).
1. For each bundled extension, record the reviewed commit SHA and the integrity hash from the Signing gap.
1. Re-run the checklist whenever a bundled extension is version-bumped; gate the bump behind the same MSIX signing release process.
1. Explicitly record in AUDIT_REPORT.md that an automated review/malware-scanning marketplace pipeline is OUT OF SCOPE (requires a cloud service + security team, not buildable in the client).

**Code example** — `windows/HelixBrowser/Engine/ExtensionManager.cs`:

```csharp
// There is no client-side 'malware detection' to implement for a curated
// allow-list: vetting happens once, offline, by humans. The only runtime
// enforcement is refusing anything NOT on the reviewed allow-list. This guard
// makes that explicit and is the client's entire 'review pipeline' surface.
private static bool IsReviewed(string extensionId)
{
    // AllowList is the manually-vetted, hash-pinned set. Anything else is denied;
    // Helix never executes an unreviewed/user-supplied extension package.
    return AllowList.ContainsKey(extensionId);
}
```

**Migration.** None — process/documentation plus a runtime allow-list check. No data or schema impact.


**Testing.** Process testing: confirm the vetting checklist is completed and committed for every bundled extension before a release. Runtime test that IsReviewed denies any id not in the allow-list and that the loader skips it silently.


---

## B11. Browser Wallet / Web3  `[browser-wallet-web3]`

**Overall feasibility:** partially-feasible


VERIFIED GROUND TRUTH: Helix has ZERO Web3/wallet code on any of the five platforms. A case-insensitive grep for web3|ethereum|wallet|eip-1193|metamask|window.ethereum|0x[40-hex] across all .kt/.swift/.cs/.py files returned no matches; doc/manifest grep also clean. All four sub-features (Crypto Wallet, EIP-1193 provider injection, Wallet Isolation, Transaction Verification) are MISSING. This is correct per the WebView-shell architecture: a wallet is a PRODUCT feature, not an engine feature, so it is buildable-by-Helix and NOT gated on an engine fork. The injection plumbing already exists and is proven: Android injects privacy JS via WebView.evaluateJavascript at HelixWebViewClient.kt:80/97 and — critically — already runs a native<->JS bridge via webView.addJavascriptInterface(HelixHomeBridge(webView), \"HelixHome\") at MainActivity.kt:839 with @JavascriptInterface methods at MainActivity.kt:3024/3042. iOS/macOS have WKUserScript injection (PrivacyManager.swift:33, addUserScript at :227+) and can add a WKScriptMessageHandler bridge; Linux uses WebKit2GTK UserContentManager; Windows uses WebView2 AddScriptToExecuteOnDocumentCreated + postMessage. So an EIP-1193 provider (window.ethereum) is genuinely feasible-in-webview. The HARD part is NOT injection — it is the native wallet backend: secure key storage (Android Keystore / iOS Secure Enclave + Keychain), BIP-39/BIP-32/secp256k1 signing, EIP-712 typed-data signing, a JSON-RPC node connection, and a trustworthy transaction-confirmation UI. Building a real crypto wallet is a security-critical, multi-month effort with custody/regulatory liability; the honest near-term alternative is WalletConnect v2 (no key custody in-app) plus an EIP-1193 shim, deferring native key management. Competitively this is NOT table-stakes: Chrome/Edge/Firefox/Safari ship NO built-in wallet (they intentionally avoid custody); only Brave (native Brave Wallet) and Arc (limited, via extensions) have any wallet story, and Brave required a Chromium fork to do it natively at engine level. So Helix shipping nothing here is roughly at parity with the mainstream majority — risk is LOW-to-MED competitive, not a ship blocker.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Crypto Wallet (native key custody, send/receive, balances) | ❌ missing | ❌ | ❌ | ✅ | ❌ | ❌ | 🟡 | med | XL |
| Web3 / EIP-1193 provider injection (window.ethereum) | ❌ missing | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | med | L |
| Wallet Isolation (origin allowlist, no key leak to page JS, per-site connect permissions) | ❌ missing | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | **high** | M |
| Transaction Verification (human-readable tx/typed-data confirmation, anti-phishing, simulation) | ❌ missing | ❌ | ❌ | 🟡 | ❌ | ❌ | ❌ | **high** | L |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B11.1 — No EIP-1193 provider (window.ethereum) — dApps see no wallet; every web3 site shows 'No wallet detected'

`P2` · feasibility: `feasible-in-webview` · ~7 eng-weeks


**Why it matters.** This is the single load-bearing primitive of browser web3: every dApp (Uniswap, OpenSea, Aave, ENS) detects a wallet by probing window.ethereum and calling eth_requestAccounts. Without it Helix is invisible to the entire web3 ecosystem. It is also the cheapest, lowest-custody-risk entry point: injecting the provider and bridging it to WalletConnect v2 (where the user's existing mobile wallet holds the keys) lets Helix support dApps WITHOUT Helix ever touching a private key, sidestepping the custody/regulatory liability that makes a full native wallet XL effort. Mainstream browsers (Chrome/Edge/Firefox/Safari) deliberately ship nothing here, so a clean WalletConnect-backed provider would actually LEAD them while staying behind Brave.


**Recommended architecture.** Add a new engine module Web3ProviderBridge.kt under android/app/src/main/java/com/helix/browser/engine/. Mirror the EXISTING proven pattern at MainActivity.kt:839 (addJavascriptInterface(HelixHomeBridge, "HelixHome")): register a second interface webView.addJavascriptInterface(Web3ProviderBridge(...), "HelixWeb3"). The bridge's @JavascriptInterface request(payloadJson) method parses an EIP-1193 RPC request, routes read-only calls (eth_chainId, eth_blockNumber, eth_call, eth_getBalance) to a JSON-RPC HTTP client (OkHttp), and routes account/signing calls (eth_requestAccounts, eth_sendTransaction, personal_sign, eth_signTypedData_v4) to a WalletConnect v2 session (com.walletconnect:sign SDK) — Helix never holds keys; the user's paired mobile wallet signs. Inject an EIP-1193 shim (window.ethereum proxying to HelixWeb3.request and re-emitting events) at document-start by appending it to the existing getPrivacyScripts() string consumed by HelixWebViewClient.injectPrivacyScripts (HelixWebViewClient.kt:91-104), gated behind a new Prefs flag isWeb3Enabled (default OFF). Results flow back via webView.evaluateJavascript('window.__helixWeb3Resolve(...)'). On iOS/macOS the equivalent is a WKScriptMessageHandler named 'helixWeb3' added in PrivacyManager.swift alongside the existing addUserScript calls (PrivacyManager.swift:227+).


**Implementation plan.**
1. Add Prefs.isWeb3Enabled (default false) and a Settings toggle 'Experimental: Web3 / dApp support' so the provider is opt-in and ships dark.
1. Write the injected EIP-1193 shim JS (window.ethereum with request(), on()/removeListener() EventEmitter, isHelix=true, chainId/networkVersion, legacy enable()) that forwards to the native bridge and resolves via a __helixWeb3Resolve callback keyed by a monotonic request id.
1. Create Web3ProviderBridge.kt with @JavascriptInterface request(payloadJson); validate origin against a per-tab connected-origin allowlist before exposing accounts.
1. Integrate WalletConnect v2 (Sign SDK) for eth_requestAccounts + all signing methods so Helix delegates custody; wire a pairing-QR / deep-link UI.
1. Add an OkHttp JSON-RPC client with a curated public-RPC endpoint per chainId for read-only methods (eth_call/getBalance/chainId).
1. Re-emit accountsChanged / chainChanged / connect / disconnect events into the page when the WC session changes.
1. Append the shim to getPrivacyScripts() only when isWeb3Enabled; re-inject on SPA navigations as the privacy scripts already do (HelixWebViewClient.kt:87).
1. Replicate the shim + a WKScriptMessageHandler bridge on iOS/macOS PrivacyManager.swift; defer Windows/Linux.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/Web3ProviderBridge.kt`:

```kotlin
package com.helix.browser.engine

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * EIP-1193 native bridge. Injected JS (window.ethereum) calls HelixWeb3.request();
 * read-only JSON-RPC is answered locally, account/signing is delegated to a
 * WalletConnect v2 session so Helix NEVER holds a private key (no custody).
 * Modeled on the existing HelixHomeBridge pattern (MainActivity.kt:839/3024).
 */
class Web3ProviderBridge(
    private val webView: WebView,
    private val currentOrigin: () -> String?,
    private val isConnected: (origin: String) -> Boolean,
    private val requestConnect: (origin: String, onResult: (accounts: List<String>?) -> Unit) -> Unit,
    private val rpcCall: (method: String, params: String) -> String?,   // OkHttp JSON-RPC, read-only
    private val wcSign: (method: String, params: String, onResult: (resultOrNull: String?) -> Unit) -> Unit
) {
    private companion object { const val TAG = "Web3ProviderBridge"; const val MAX_PAYLOAD = 64 * 1024 }

    @JavascriptInterface
    fun request(payloadJson: String?) {
        val raw = payloadJson ?: return
        if (raw.length > MAX_PAYLOAD) { Log.w(TAG, "oversized payload"); return }
        val origin = currentOrigin() ?: return reject(0, "no origin")
        val msg = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val id = msg.optInt("id", -1)
        val method = msg.optString("method")
        val params = msg.optString("params", "[]")
        when (method) {
            // Account exposure is origin-gated: a page only sees accounts after an explicit connect.
            "eth_requestAccounts", "eth_accounts" -> {
                if (isConnected(origin)) wcSign("eth_accounts", "[]") { resolve(id, it ?: "[]") }
                else requestConnect(origin) { accts ->
                    if (accts == null) reject(id, "User rejected")
                    else resolve(id, org.json.JSONArray(accts).toString())
                }
            }
            // Signing & state-changing calls are NEVER auto-approved — they go to the paired wallet UI.
            "eth_sendTransaction", "personal_sign", "eth_signTypedData_v4", "eth_sign" -> {
                if (!isConnected(origin)) return reject(id, "Unauthorized: connect first")
                wcSign(method, params) { res -> if (res == null) reject(id, "User rejected") else resolve(id, res) }
            }
            // Read-only calls answered by local JSON-RPC, no wallet needed.
            "eth_chainId", "net_version", "eth_blockNumber", "eth_call", "eth_getBalance", "eth_gasPrice" ->
                rpcCall(method, params)?.let { resolve(id, it) } ?: reject(id, "RPC error")
            else -> reject(id, "Unsupported method: $method")
        }
    }

    private fun resolve(id: Int, jsonResult: String) = dispatch("window.__helixWeb3Resolve($id, $jsonResult, null)")
    private fun reject(id: Int, error: String) = dispatch("window.__helixWeb3Resolve($id, null, \"${error.replace("\"", "\\\"")}\")")
    private fun dispatch(call: String) {
        webView.post {
            runCatching { webView.evaluateJavascript(call, null) }
                .onFailure { Log.d(TAG, "dispatch failed: ${it.message}") }
        }
    }
}
```

**Migration.** No data migration: net-new feature behind Prefs.isWeb3Enabled (default false), so existing users are unaffected and no schema/Room change is needed. WalletConnect session state persists in its own SDK store; if removed later, deleting that store is sufficient. window.ethereum is only injected on opt-in, so pages that feature-detect a wallet behave exactly as today (none present) until the user enables it.


**Testing.** Unit-test Web3ProviderBridge routing with a fake WebView + mocked rpcCall/wcSign verifying: read-only methods never reach wcSign; signing methods reject when origin not connected; oversized/malformed payloads are dropped. Instrumented test: load a known test dApp (a local page calling window.ethereum.request({method:'eth_chainId'})) and assert the resolved chainId. Security test: assert a page CANNOT read accounts before connect and that the injected shim exposes no private key material. Manual: connect MetaMask-mobile via WalletConnect QR and complete an eth_signTypedData_v4 round-trip on a testnet.


#### Gap B11.2 — No wallet isolation / origin-permission model — needed before any provider ships, or any page could silently access accounts/sign

`P1` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** A browser wallet's entire threat model is that arbitrary, possibly malicious, page JavaScript runs in the same WebView as the provider. Without strict isolation (per-origin connect allowlist, accounts hidden until explicit user connect, signing always behind a native confirmation, and the EIP-1193 object frozen so a page cannot monkey-patch it to spoof another wallet) the wallet becomes a drainer's dream. This is the difference between a feature and a liability: the single most common cause of crypto loss is malicious approvals, not broken crypto. It is HIGH risk because if Helix ships a provider WITHOUT this, it directly enables fund theft. It must land in the same release as, and gate, the provider itself.


**Recommended architecture.** A Web3PermissionManager (Android: new class beside engine/PrivacyManager.kt, persisted in SharedPreferences keyed by origin) holding the connected-origin set and selected account/chain. The Web3ProviderBridge consults it via the isConnected/requestConnect lambdas shown in the provider codeExample. The injected shim must Object.freeze(window.ethereum) and define it as non-configurable/non-writable to resist tampering. Per-tab scoping: connection state lives per BrowserTab (tabs/BrowserTab.kt) so connecting on one site never leaks to another, and incognito tabs (already isolated, MainActivity.kt:840) start with an empty allowlist and never persist connections.


**Implementation plan.**
1. Define the permission record (origin, accounts[], chainId, grantedAt) and a Web3PermissionManager with isConnected(origin)/connect()/revoke()/listConnected().
1. Persist non-incognito grants; force incognito grants to be in-memory only and cleared on tab close (reuse the incognito teardown that already wipes per-tab data).
1. Wire requestConnect to a native connect sheet showing origin + the accounts about to be exposed, with explicit Approve/Reject.
1. Freeze the injected provider object and guard against re-definition; expose isHelix=true but no internal handles.
1. Add a Settings > Connected Sites screen (mirror the existing SitePermissionsActivity pattern) to review/revoke per-origin web3 access.
1. Gate ALL account-exposing and signing bridge methods on isConnected(origin).

**Code example** — `android/app/src/main/java/com/helix/browser/engine/Web3PermissionManager.kt`:

```kotlin
package com.helix.browser.engine

import android.content.Context
import android.net.Uri

/**
 * Per-origin web3 connect allowlist. The Web3ProviderBridge must consult this
 * before exposing accounts or routing a signing request. Incognito origins are
 * held in-memory only and dropped on tab teardown (parity with the scoped
 * incognito data wipe already done in MainActivity).
 */
class Web3PermissionManager(context: Context) {
    private val prefs = context.getSharedPreferences("helix_web3_perms", Context.MODE_PRIVATE)
    private val ephemeral = HashSet<String>()  // incognito-only grants

    /** Normalize to scheme://host[:port]; reject anything not https. */
    private fun originOf(url: String): String? {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (u.scheme?.lowercase() != "https") return null   // no wallet on plaintext
        val host = u.host ?: return null
        val port = if (u.port > 0) ":${u.port}" else ""
        return "https://$host$port"
    }

    fun isConnected(url: String, incognito: Boolean): Boolean {
        val origin = originOf(url) ?: return false
        return if (incognito) ephemeral.contains(origin) else prefs.getBoolean(origin, false)
    }

    fun grant(url: String, incognito: Boolean) {
        val origin = originOf(url) ?: return
        if (incognito) ephemeral.add(origin) else prefs.edit().putBoolean(origin, true).apply()
    }

    fun revoke(url: String) {
        val origin = originOf(url) ?: return
        ephemeral.remove(origin)
        prefs.edit().remove(origin).apply()
    }

    fun clearEphemeral() = ephemeral.clear()

    fun connectedOrigins(): List<String> = prefs.all.keys.toList()
}
```

**Migration.** New SharedPreferences file helix_web3_perms; no existing data touched. Incognito grants never persist, so closing incognito leaves no residue (matches existing incognito guarantees). Revocation simply removes the key.


**Testing.** Unit tests: isConnected false for http:// and for unconnected https origins; grant/revoke round-trip; incognito grant absent from persistent store and cleared by clearEphemeral. Integration: connecting site A must not expose accounts to site B in another tab; after revoke, the provider's eth_accounts returns empty and a subsequent signing call rejects 'Unauthorized'. Security: attempt to redefine/overwrite window.ethereum from page JS and assert the frozen provider resists it.


#### Gap B11.3 — No transaction-verification / signing-confirmation UI — users would blind-sign drainer transactions

`P1` · feasibility: `feasible-in-webview` · ~3 eng-weeks


**Why it matters.** Even with WalletConnect delegating the final cryptographic signature to a mobile wallet, the in-browser confirmation sheet is where users decide whether to trust a dApp request, and it is where phishing is caught or missed. A trustworthy sheet must decode eth_sendTransaction (to, value, decoded method selector, gas) and EIP-712 typed data into human-readable terms, flag unlimited-approval (approve(spender, MAX_UINT)) and setApprovalForAll patterns, show the verified origin prominently, and never auto-approve. This is HIGH risk: the dominant cause of crypto loss is deceptive approvals, not cryptography. Brave invests heavily here (transaction simulation/insights); a bare provider without it would make Helix a more dangerous place to sign than doing nothing.


**Recommended architecture.** A TransactionConfirmationSheet (Android: a BottomSheetDialogFragment under ui/, styled like the existing page-info / permission sheets) driven by a TxDecoder that parses the calldata: detects ERC-20 transfer/approve and ERC-721/1155 setApprovalForAll by 4-byte selector, formats value/gas, and surfaces an 'unlimited approval' / 'transfers all NFTs' warning banner. The bridge's wcSign lambda must await this sheet's user decision BEFORE forwarding the request to the WalletConnect session. Origin shown is the verified per-tab origin from Web3PermissionManager, not a page-supplied string.


**Implementation plan.**
1. Build TxDecoder: 4-byte selector table for transfer/transferFrom/approve/setApprovalForAll/permit; decode amounts with token decimals via an eth_call to decimals() where possible.
1. Build TransactionConfirmationSheet showing verified origin, action summary, decoded params, gas, and a red warning row for unlimited/blanket approvals.
1. Insert the sheet as a mandatory await in the signing path inside Web3ProviderBridge before wcSign forwards to WalletConnect.
1. Render EIP-712 typed-data domains/messages in a readable key/value tree for eth_signTypedData_v4.
1. Optional later: integrate a transaction-simulation/insights API (Blockaid/Tenderly) for balance-change preview, behind a network toggle.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/TxDecoder.kt`:

```kotlin
package com.helix.browser.engine

import java.math.BigInteger

/** Decodes EVM calldata enough to power a human-readable confirmation sheet. */
object TxDecoder {
    private val MAX_UINT = BigInteger("f".repeat(64), 16)

    data class Decoded(val summary: String, val isHighRisk: Boolean, val warning: String?)

    fun decode(to: String?, valueHex: String?, dataHex: String?): Decoded {
        val data = dataHex?.removePrefix("0x").orEmpty()
        if (data.length < 8) {
            val eth = weiToEth(valueHex)
            return Decoded("Send $eth ETH to ${short(to)}", false, null)
        }
        val selector = data.substring(0, 8).lowercase()
        val args = data.substring(8)
        return when (selector) {
            "095ea7b3" -> { // approve(address spender, uint256 amount)
                val spender = addrArg(args, 0)
                val amount = uintArg(args, 1)
                val unlimited = amount >= MAX_UINT / BigInteger.TWO
                Decoded(
                    "Approve token spending by ${short(spender)}",
                    unlimited,
                    if (unlimited) "UNLIMITED approval: ${short(spender)} could move ALL of this token. Approve only if you trust this site." else null
                )
            }
            "a22cb465" -> Decoded( // setApprovalForAll(address operator, bool approved)
                "Grant access to ALL your NFTs in this collection",
                true,
                "This lets ${short(addrArg(args, 0))} transfer EVERY NFT you own in this collection."
            )
            "a9059cbb" -> Decoded("Transfer tokens to ${short(addrArg(args, 0))}", false, null)
            else -> Decoded("Contract interaction with ${short(to)} (method 0x$selector)", false,
                "Helix can't decode this call. Verify the site before signing.")
        }
    }

    private fun addrArg(args: String, i: Int) = "0x" + args.substring(i * 64 + 24, i * 64 + 64)
    private fun uintArg(args: String, i: Int) = BigInteger(args.substring(i * 64, i * 64 + 64), 16)
    private fun weiToEth(hex: String?): String {
        val wei = runCatching { BigInteger(hex?.removePrefix("0x").orEmpty().ifEmpty { "0" }, 16) }.getOrDefault(BigInteger.ZERO)
        return wei.toBigDecimal().movePointLeft(18).stripTrailingZeros().toPlainString()
    }
    private fun short(a: String?) = a?.let { if (it.length > 12) it.take(8) + "…" + it.takeLast(4) else it } ?: "?"
}
```

**Migration.** Pure UI/logic addition; no persisted data, no back-compat concern. It sits in front of the WalletConnect signing path that itself ships dark behind Prefs.isWeb3Enabled.


**Testing.** Unit-test TxDecoder against canonical calldata vectors: approve with MAX_UINT flagged unlimited; approve with a small amount not flagged; setApprovalForAll always high-risk; plain ETH send formats value correctly; unknown selector returns the generic 'can't decode' warning. UI test: confirmation sheet shows the verified origin and that rejecting it causes the bridge to return 'User rejected' to the page. Adversarial: feed malformed/short calldata and assert no crash and a conservative warning.


---

## B12. Download Manager  `[download-manager]`

**Overall feasibility:** partially-feasible


Verified against the real code, Helix's download manager is MISSING all four advanced sub-features on every platform. Pause/Resume: absent — Android delegates to system android.app.DownloadManager which DownloadsActivity.kt:173-175 explicitly notes has no per-id pause; macOS DownloadManager.swift:76 receives WKDownload resumeData but discards it; iOS stores flat [String:String] dicts (no live handle); Windows/Linux have NO download wiring connected at all (the DB tables exist but no DownloadStarting/decide-destination/download-started signal is hooked). Parallel/segmented downloads: absent everywhere (single-stream only) — but this is LOW priority since only Edge ships it among competitors. Virus scanning: absent — the only safety is the inherited WebView navigation flag (HelixWebView.kt:87 safeBrowsingEnabled), which does NOT scan saved bytes; this is the highest-security gap. Smart categorization: absent — one static ic_download icon (item_download.xml) and a flat list; trivially fixable. The most material remediations: (P1) build a self-managed resumable HelixDownloadEngine to replace fire-and-forget system DownloadManager for pause/resume — partially-feasible via OkHttp HTTP Range requests, ~5 wk; (P1) download safety via Safe Browsing hash-prefix lookup + OS quarantine handoff (macOS xattr / Windows Zone.Identifier) since engine-grade scanning would require an engine fork — partially-feasible, ~4 wk; (P2) MIME-derived categorization, purely UI, feasible-in-webview, ~1 wk. Parallel download is intentionally deprioritized (P3, low risk). True engine-level download protection (Chrome's network-stack-integrated ML reputation) is NOT replicable in a WebView wrapper — the realistic alternative is URL/hash reputation plus OS-level quarantine handoff.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Pause / Resume in-progress downloads | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Parallel / multi-segment (accelerated) downloads | ❌ missing | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | low | XL |
| Virus / malware scanning of downloaded files | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **CRIT** | L |
| Smart categorization (group by file type / icon-per-type) | ❌ missing | 🟡 | ✅ | 🟡 | 🟡 | 🟡 | ✅ | med | M |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B12.1 — Pause / Resume of in-progress downloads is absent on every platform. Android delegates to the system android.app.DownloadManager whose Request has no per-id pause API (DownloadsActivity.kt:173-175 documents this; 'Cancel == remove'). macOS receives WKDownload resumeData in didFailWithError (DownloadManager.swift:76) but discards it and never calls download.cancel(completionHandler:) to capture resume data on a user pause. iOS persists flat [String:String] dictionaries with no live WKDownload handle (DownloadsViewController.swift / CoreDataManager.swift:118-150). Windows and Linux have NO download wiring at all (no DownloadStarting / decide-destination signal connected).

`P1` · feasibility: `partially-feasible` · ~5 eng-weeks


**Why it matters.** Pause/resume is the single most-requested download-manager capability and table stakes in every competitor. Without it, a user on a metered or flaky mobile connection who loses signal mid-download loses the entire transfer and must restart from zero — for a multi-GB file this is a severe, visible regression versus Chrome/Firefox. It is also the foundation for resilient large-file UX and for a 'Downloads' surface that feels like a real browser rather than a fire-and-forget shim.


**Recommended architecture.** Stop delegating Android to system DownloadManager for user-initiated file downloads; introduce a self-managed HelixDownloadEngine. Android: new android/app/src/main/java/com/helix/browser/engine/HelixDownloadEngine.kt (a foreground Service using OkHttp + HTTP Range requests, writing to a .part file in app scoped storage then MediaStore-publishing on completion), a Room DownloadEntity table in data/AppDatabase.kt, and rewire DownloadsActivity/DownloadsAdapter onto it (pause/resume actions replace the cancel-only button). macOS: extend DownloadManager.swift to store the WKDownload reference per item, add pause(id:) that calls download.cancel { resumeData in ... } persisting resumeData, and resume(id:) via webView's resumeDownload(fromResumeData:). iOS: same WKDownload retention pattern in a real DownloadManager.swift (currently none — iOS only has a passive store). Windows: add CoreWebView2.DownloadStarting handler exposing DownloadOperation.Pause()/Resume()/CanResume. Linux: connect WebKit2 'download-started' + Download 'cancel'/restart (WebKit lacks true resume, so fall back to range re-request).


**Implementation plan.**
1. Add a DownloadEntity (id, url, filename, dir, totalBytes, downloadedBytes, status, etag, lastModified, resumeSupported) and DAO to AppDatabase with an additive Room migration (v2->v3).
1. Implement HelixDownloadEngine.kt as a started+foreground Service: OkHttp call with 'Range: bytes=<downloaded>-' header, append to <file>.part, throttle DB progress writes to ~1/sec, post an ongoing notification with Pause/Resume/Cancel actions.
1. Persist ETag/Last-Modified on first response; on resume send If-Range to guarantee the server returns 206 for the same body or 200 to restart cleanly.
1. Rewire enqueueDownload() in MainActivity.kt (line ~2337) to call HelixDownloadEngine instead of system DownloadManager.enqueue(); keep system DownloadManager only as a fallback when the engine can't (e.g. blob:).
1. Rewrite DownloadsActivity.queryDownloads() to read the Room table via Flow instead of polling DownloadManager.query(); replace the cancel-only btnAction in DownloadsAdapter with pause/resume/cancel states.
1. macOS/iOS: retain WKDownload per DownloadItem; implement pause via cancel{resumeData} and resume via WKWebView.resumeDownload(fromResumeData:); persist resumeData to disk so it survives relaunch.
1. Windows/Linux: wire the previously-absent download-started signals and surface Pause/Resume where the platform API allows (WebView2 native; WebKitGTK best-effort range re-request).

**Code example** — `android/app/src/main/java/com/helix/browser/engine/HelixDownloadEngine.kt`:

```kotlin
package com.helix.browser.engine

import android.content.Context
import com.helix.browser.data.AppDatabase
import com.helix.browser.data.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * Self-managed resumable downloader. Unlike android.app.DownloadManager this
 * keeps the byte offset + validator (ETag/Last-Modified) in Room so a paused or
 * crashed transfer can continue with a ranged GET instead of restarting.
 */
class HelixDownloadEngine(private val ctx: Context, private val db: AppDatabase) {

    private val client = OkHttpClient()

    /** Resume (or start) the download identified by [id]; suspends until paused, done, or failed. */
    suspend fun resume(id: Long) = withContext(Dispatchers.IO) {
        val dao = db.downloadDao()
        val item = dao.byId(id) ?: return@withContext
        val partFile = File(item.dir, item.filename + ".part")
        val have = if (partFile.exists()) partFile.length() else 0L

        val builder = Request.Builder().url(item.url)
        if (have > 0 && item.etag != null) {
            // If-Range makes the server send 206 for the SAME body, else a clean 200.
            builder.header("Range", "bytes=$have-").header("If-Range", item.etag)
        }

        dao.updateStatus(id, DownloadEntity.STATUS_RUNNING)
        client.newCall(builder.build()).execute().use { resp ->
            val partial = resp.code == 206
            val out = RandomAccessFile(partFile, "rw")
            out.seek(if (partial) have else 0L)
            if (!partial) dao.updateProgress(id, 0L) // server restarted: rewind

            val total = (if (partial) have else 0L) +
                (resp.body?.contentLength() ?: -1L).coerceAtLeast(0L)
            dao.updateTotal(id, total)
            resp.header("ETag")?.let { dao.updateValidator(id, it) }

            val sink = resp.body!!.source()
            val buf = ByteArray(64 * 1024)
            var written = if (partial) have else 0L
            var lastFlush = System.currentTimeMillis()
            while (true) {
                if (dao.statusOf(id) == DownloadEntity.STATUS_PAUSED) {
                    out.fd.sync(); out.close(); return@withContext // offset is safe on disk
                }
                val n = sink.inputStream().read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                written += n
                if (System.currentTimeMillis() - lastFlush > 1000) {
                    dao.updateProgress(id, written); lastFlush = System.currentTimeMillis()
                }
            }
            out.fd.sync(); out.close()
            partFile.renameTo(File(item.dir, item.filename))
            dao.updateProgress(id, written)
            dao.updateStatus(id, DownloadEntity.STATUS_SUCCESSFUL)
        }
    }

    /** Cooperative pause: the running loop observes the flag and flushes its offset. */
    suspend fun pause(id: Long) = withContext(Dispatchers.IO) {
        db.downloadDao().updateStatus(id, DownloadEntity.STATUS_PAUSED)
    }
}
```

**Migration.** Additive Room migration v2->v3 adding a downloads table (no destructive change; existing bookmark/history tables untouched, matching the existing v1->v2 additive pattern). In-flight system-DownloadManager downloads from a prior install are unaffected — they remain queryable via a one-time legacy read path so old entries still display until cleared. Persist resumeData/ETag so resume survives process death.


**Testing.** Unit-test the resume offset logic with a fake OkHttp interceptor returning 206/200 and a truncated body to assert correct seek/rewind. Instrumented Android test: start a 50MB download against a local MockWebServer that supports Range, kill the Service mid-stream, relaunch, assert the .part offset continues and the final SHA-256 matches the full file. Manual matrix test on airplane-mode toggle for real-network resilience. For macOS/iOS, assert resumeData round-trips through cancel->relaunch->resumeDownload.


#### Gap B12.2 — Downloaded files are never scanned for malware. There is no download-file safety check on any platform. The only 'safe browsing' present is the inherited WebView navigation flag (HelixWebView.kt:87 safeBrowsingEnabled=true), which gates page navigation, NOT the bytes a user saves to disk. Chrome/Edge/Brave/Firefox/Safari all run download URLs/hashes through a reputation service (Google Safe Browsing download protection / Microsoft SmartScreen / Apple notarization-and-XProtect handoff) before or as the file lands.

`P1` · feasibility: `partially-feasible` · ~4 eng-weeks


**Why it matters.** A browser that lets users download .apk/.exe/.dmg/.zip with zero reputation check is a malware-distribution vector and a concrete liability. This is the highest-SECURITY-impact gap in the domain: a phishing site can serve a trojan and Helix saves it silently with a green checkmark. It is also a Play/App-Store review and enterprise-procurement red flag. Competitors treat download protection as non-optional.


**Recommended architecture.** Helix cannot re-implement engine-grade download protection (Chrome's is wired into the network stack and ML backend — that part is requires-engine-fork). The feasible subset is a reputation lookup at enqueue time: add android/app/src/main/java/com/helix/browser/engine/DownloadSafetyChecker.kt that, before HelixDownloadEngine starts, calls the Google Safe Browsing v4 Update API (downloadable hash-prefix lists, privacy-preserving) on the download URL, and after completion computes the file SHA-256 and checks it against the same lists. On a match, quarantine the .part file (don't publish to MediaStore) and show a red interstitial in DownloadsActivity. On macOS, hand off to the OS by setting the com.apple.quarantine xattr on the saved file so Gatekeeper/XProtect scans on first open (DownloadManager.swift decideDestinationUsing). On Windows, set the Zone.Identifier ADS so SmartScreen engages. Linux: best-effort ClamAV invocation if present, else URL reputation only.


**Implementation plan.**
1. Provision a Safe Browsing API key (or use the URL-reputation Lookup API for v1) and add a DownloadSafetyChecker with a small local hash-prefix cache refreshed on a WorkManager job.
1. In enqueueDownload() (MainActivity.kt ~2337) gate the start: if the URL host or download URL hash-prefix matches, block with a confirm-anyway dialog.
1. After HelixDownloadEngine completes, compute SHA-256 of the file off-main and re-check the full hash before publishing to the Downloads collection.
1. Add a 'dangerous' status to DownloadEntity + a red banner + 'Delete'/'Keep anyway' actions in DownloadsAdapter.
1. macOS: in decideDestinationUsing, after writing, set kLSQuarantineAgentName via setxattr com.apple.quarantine so the system scans on open. Windows: write Zone.Identifier ADS. Linux: shell out to clamscan if available.
1. Telemetry-free: keep all matching local (hash-prefix lists), never send full URLs unless using the explicit Lookup API with user consent.

**Code example** — `macos/HelixBrowser/DownloadManager.swift`:

```swift
// In DownloadManager: WKDownloadDelegate, hand the saved file to the OS scanner
// by tagging it with the quarantine xattr so Gatekeeper/XProtect scans on open.
func download(_ download: WKDownload,
              decideDestinationUsing response: URLResponse,
              suggestedFilename: String,
              completionHandler: @escaping (URL?) -> Void) {
    let dest = downloadsDirectory.appendingPathComponent(suggestedFilename)
    try? FileManager.default.removeItem(at: dest)
    if let id = downloadMap[download] {
        updateItem(id: id) { $0.localPath = dest }
    }
    completionHandler(dest)
}

func downloadDidFinish(_ download: WKDownload) {
    if let id = downloadMap[download],
       let item = downloads.first(where: { $0.id == id }),
       let path = item.localPath {
        applyQuarantine(to: path, sourceURL: item.url)
        updateItem(id: id) { $0.isComplete = true; $0.progress = 1.0 }
        DispatchQueue.main.async { self.activeDownloadCount = max(0, self.activeDownloadCount - 1) }
    }
    downloadMap.removeValue(forKey: download)
}

/// Mark the file as quarantined so macOS XProtect/Gatekeeper scans it the first
/// time the user opens it -- the WebView shell cannot scan bytes itself, so we
/// delegate to the OS, which is the realistic (non-engine-fork) path.
private func applyQuarantine(to file: URL, sourceURL: String) {
    let props = "0083;\(Int(Date().timeIntervalSince1970));HelixBrowser;\(sourceURL)"
    props.withCString { cstr in
        _ = setxattr(file.path, "com.apple.quarantine", cstr, strlen(cstr), 0, 0)
    }
}
```

**Migration.** Add a nullable 'safetyState' column to the download record (Room v3 additive on Android; new key in the iOS/macOS dictionary, defaulting to 'unknown' for legacy rows so existing entries render without a verdict). No destructive change. The OS-handoff approach (quarantine/Zone.Identifier) needs no schema at all.


**Testing.** Use the EICAR test string served from a local server to assert quarantine/scan triggers on macOS/Windows and that the file is flagged. Unit-test the hash-prefix matcher against a seeded Safe Browsing test list (Google publishes test hashes). Verify privacy: assert no full URL leaves the device in update-API mode. Negative test: a benign file must complete and publish normally.


#### Gap B12.3 — No smart categorization. Every download row renders the same static ic_download icon (item_download.xml line ~13) regardless of file type, and the Downloads screen is a single flat reverse-chronological list (DownloadsActivity.renderList). There is no grouping by type (Images / Documents / Video / Audio / Archives / Apps), no per-type icon, and no filtering. iOS/macOS likewise show one icon set keyed only by status, not by MIME.

`P2` · feasibility: `feasible-in-webview` · ~1 eng-weeks


**Why it matters.** As a downloads list grows, a flat untyped list is hard to scan — finding 'that PDF from last week' among installers and images is slow. Type-aware icons and category sections are a standard polish item in Edge/Arc and signal a mature product. It is also a prerequisite for useful filtering and for the safety UX (apps/executables deserve a distinct visual treatment). Low security risk but a visible quality/competitive gap that is cheap to close.


**Recommended architecture.** Pure UI/data-derivation, no engine dependency. Add a DownloadCategory enum derived from MIME type / extension (a small mapper, e.g. DownloadCategory.kt) consumed by DownloadsAdapter to (a) pick a per-type vector icon and (b) optionally drive section headers. On Android, switch DownloadsAdapter from a plain ListAdapter to a sectioned list (category headers) or add a filter chip-row in activity_downloads.xml. The DownloadItem data class already carries enough to derive category from localUri/title extension; add a computed category property. Mirror on macOS DownloadManager (DownloadItem gets a category) and iOS DownloadCell.configure (choose SF Symbol by extension).


**Implementation plan.**
1. Add DownloadCategory.kt mapping extension/MIME -> {IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APP, OTHER} with a representative drawable per category.
1. Give DownloadItem a `category` computed val derived from title/localUri extension (and mimeType when available from DownloadManager.getMimeTypeForDownloadedFile).
1. In DownloadsAdapter.bind(), set the icon ImageView from item.category instead of the static ic_download; tint executables/apps distinctly.
1. Add an optional chip filter row (All / Images / Docs / Video / Apps) to activity_downloads.xml that filters the submitted list; keep newest-first within a filter.
1. Mirror the mapping on iOS DownloadCell.configure (SF Symbol per category) and macOS DownloadItem; reuse the same category names for cross-platform consistency.
1. Add per-category vector drawables under res/drawable (doc, image, video, audio, zip, apk).

**Code example** — `android/app/src/main/java/com/helix/browser/ui/adapter/DownloadCategory.kt`:

```kotlin
package com.helix.browser.ui.adapter

import androidx.annotation.DrawableRes
import com.helix.browser.R
import com.helix.browser.ui.DownloadItem

/** Type buckets used to give each download row a meaningful icon + grouping. */
enum class DownloadCategory(@DrawableRes val iconRes: Int) {
    IMAGE(R.drawable.ic_dl_image),
    VIDEO(R.drawable.ic_dl_video),
    AUDIO(R.drawable.ic_dl_audio),
    DOCUMENT(R.drawable.ic_dl_document),
    ARCHIVE(R.drawable.ic_dl_archive),
    APP(R.drawable.ic_dl_app),
    OTHER(R.drawable.ic_download);

    companion object {
        fun of(item: DownloadItem): DownloadCategory {
            val name = (item.localUri ?: item.title).substringAfterLast('/')
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> IMAGE
                "mp4", "mkv", "webm", "mov", "avi", "m4v" -> VIDEO
                "mp3", "wav", "flac", "aac", "ogg", "m4a" -> AUDIO
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub" -> DOCUMENT
                "zip", "rar", "7z", "tar", "gz", "bz2" -> ARCHIVE
                "apk", "exe", "msi", "dmg", "deb", "appimage" -> APP
                else -> OTHER
            }
        }
    }
}
```

**Migration.** None required. Category is derived at render time from existing fields (title/localUri/MIME), so no schema change and full back-compat with existing rows. The only additions are static drawable resources.


**Testing.** Pure unit tests on DownloadCategory.of() across a table of extensions and edge cases (no extension, double extension like .tar.gz, uppercase, query-string in URI). Snapshot/UI test that DownloadsAdapter binds the correct icon per category and that the filter chips narrow the list correctly. No network or device-state dependence.


---

## B13. Media / DRM  `[media-drm]`

**Overall feasibility:** partially-feasible


Verified against the real code. Media/DRM splits cleanly into engine-owned (cannot be built in a WebView shell) and product-plumbing (buildable). Android is by far the strongest: it actually grants Widevine via HelixWebChromeClient.grantProtectedMediaOnly (HelixWebChromeClient.kt:117-129), ships full Picture-in-Picture (manifest android:supportsPictureInPicture=true at AndroidManifest.xml:52; auto-PiP on onUserLeaveHint + manual entry + a play/pause RemoteAction in MainActivity), manages HTML5 fullscreen video (onShowCustomView/onHideCustomView wired to exitFullscreenIfActive), and disables the user-gesture media gate (HelixWebView.kt:75). macOS sets the PiP flag (WebView.swift:42) plus AirPlay/inline/fullScreenEnabled. The gaps: iOS has NO PiP at all (allowsPictureInPictureMediaPlayback is never set in BrowserViewController.swift:393-401) despite being the platform where it matters most - a cheap, high-visibility P1 fix (one flag + an Info.plist background mode). DRM/Widevine is engine-owned: it works inherited on Android/iOS/macOS/Windows but Linux WebKitGTK 4.0 ships no Widevine CDM, so Netflix/Disney+ are broken on Linux and cannot be fixed without an engine/build change - this is platform-api-dependent, not buildable. Windows and Linux also lack HTML5 fullscreen-video management (no ContainsFullScreenElementChanged on Windows, no enter/leave-fullscreen signals on Linux), an easy P2 win. Audio processing/autoplay is effectively complete everywhere. Codec/hardware-decode is fully engine-owned (requires-engine-fork) - Helix can only probe and message, not add decoders. Net: PiP-on-iOS and fullscreen-on-Windows/Linux are the genuine buildable wins; DRM parity is mostly inherited with Linux as the honest hard limit.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| DRM (EME / encrypted-media handling) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | L |
| Widevine (CDM module / license proxy) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | **high** | XL |
| Audio Processing (WebAudio, decode, autoplay policy) | ✅ supported | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | low | S |
| Video Decoding (codecs: H.264/VP9/AV1/HEVC, MSE) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | L |
| Hardware Acceleration (GPU compositing/decode) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | M |
| Picture-in-Picture (PiP) | ✅ supported | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | M |
| HTML5 Fullscreen video (chrome management) | ✅ supported | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | S |

### ⚖️ Verifier corrections (adversarial re-check vs code) — confidence: high

- **Picture-in-Picture (PiP)**: `partial` → **`supported`** — android/app/src/main/AndroidManifest.xml:52 (android:supportsPictureInPicture="true"); MainActivity.kt:1460-1501 manual + auto-PiP (onUserLeaveHint:1481, enterPipNow:1495), buildPipParams with 16:9 aspect + play/pause RemoteAction (1570-1597), onPictureInPictureModeChanged collapses chrome + seeds controls (1639-1653), detectVideoPlaying JS probe (1507) and togglePipPlayback receiver (1534, 1614). This is a complete production implementation, not partial.
- **HTML5 Fullscreen video (chrome management)**: `partial` → **`supported`** — HelixWebChromeClient.kt:56-62 onShowCustomView/onHideCustomView fully overridden and routed to host; MainActivity.kt:807-815 onEnterFullscreen adds the custom video view to webViewContainer + hideSystemUI(), onExitFullscreen + leak-safe exitFullscreenIfActive (660-665) calling onCustomViewHidden; hideSystemUI/showSystemUI use insetsController on API R+ with immersive-sticky fallback (2530-2540); AndroidManifest.xml:49 configChanges=orientation|screenSize lets rotation persist fullscreen. Complete, not partial.

### Gaps & remediation


#### Gap B13.1 — iOS has no Picture-in-Picture support at all. WKWebView is created in BrowserViewController.swift:393-401 with allowsAirPlayForMediaPlayback, allowsInlineMediaPlayback and empty mediaTypesRequiringUserActionForPlayback, but allowsPictureInPicture is NEVER set on the WKWebViewConfiguration. Android (MainActivity enterPictureInPictureMode + auto-PiP onUserLeaveHint, manifest android:supportsPictureInPicture=true) and macOS (WebView.swift:42 allowsPictureInPictureMediaPlayback) both ship it. iOS, the platform where background-video PiP matters most, is the only one with zero PiP.

`P1` · feasibility: `feasible-in-webview` · ~0.5 eng-weeks


**Why it matters.** PiP is a flagship, user-visible expectation on iOS: every competing browser (Safari, Chrome, Edge, Brave, Arc, Firefox iOS) lets a YouTube/Netflix/news video keep playing in a floating window when the user leaves the tab or app. Without it, Helix iOS pauses or kills the video on backgrounding, which reads as broken vs Safari and is a common 1-star review trigger. It is a low-effort, high-visibility parity win because WebKit owns the actual PiP rendering - Helix only has to flip a config flag and (optionally) honor the user gesture.


**Recommended architecture.** Modify BrowserViewController.createWebView(isIncognito:) (BrowserViewController.swift:390-401) to set config.allowsPictureInPictureMediaPlayback = true. Add an Info.plist UIBackgroundModes entry 'audio' so media (and PiP) keeps running when backgrounded. Optionally add a small PiPCoordinator helper that observes AVPictureInPictureController state if a custom PiP button is wanted, but the WebKit-managed auto-PiP requires only the flag plus background mode.


**Implementation plan.**
1. Set config.allowsPictureInPictureMediaPlayback = true in createWebView before the WKWebView is instantiated.
1. Add 'audio' to UIBackgroundModes in ios/HelixBrowser/HelixBrowser/Info.plist so playback/PiP survives app backgrounding.
1. Ensure the AVAudioSession category is .playback (activate it once at app launch) so PiP audio is not ducked/stopped.
1. Verify the native PiP button now appears in the WKWebView video controls; no custom UI needed for the WebKit-managed path.
1. QA against YouTube/Vimeo/HLS sample, both inline and fullscreen entry, on a real device (PiP requires hardware, not simulator).

**Code example** — `ios/HelixBrowser/HelixBrowser/Views/BrowserViewController.swift`:

```swift
private func createWebView(isIncognito: Bool) -> WKWebView {
    let config = WKWebViewConfiguration()
    config.processPool = isIncognito ? incognitoProcessPool : processPool
    config.allowsAirPlayForMediaPlayback = true
    config.allowsInlineMediaPlayback = true
    config.mediaTypesRequiringUserActionForPlayback = []
    // Enable WebKit-managed Picture-in-Picture (parity with macOS/Android).
    // Requires UIBackgroundModes 'audio' in Info.plist and an active
    // AVAudioSession(.playback) so the floating window keeps playing when
    // the app is backgrounded.
    config.allowsPictureInPictureMediaPlayback = true

    if isIncognito {
        config.websiteDataStore = WKWebsiteDataStore.nonPersistent()
    }
    let webView = WKWebView(frame: .zero, configuration: config)
    return webView
}
```

**Migration.** No data migration. Pure capability flip; existing tabs/sessions unaffected. The only back-compat note is the new UIBackgroundModes entry, which is additive and does not change persisted state. App Review may ask why 'audio' background mode is declared - justify as 'media playback / Picture-in-Picture', standard and accepted.


**Testing.** Manual on a physical iPhone/iPad (PiP is unavailable in Simulator): play YouTube/HLS, swipe to Home, confirm the floating PiP window appears and audio continues; confirm play/pause from the PiP controls works; confirm restoring the app returns video inline. Regression-test incognito tabs (separate process pool) and that backgrounding a non-video tab does not keep a silent session alive. Add a smoke checklist item since iOS has no CI build in-repo.


#### Gap B13.2 — DRM/Widevine grant plumbing exists ONLY on Android; iOS/macOS/Windows/Linux have no EME permission handling, and Linux WebKitGTK 4.0 ships without a Widevine CDM so protected streaming (Netflix/Spotify/Disney+/Amazon) cannot play at all. Android grants RESOURCE_PROTECTED_MEDIA_ID in HelixWebChromeClient.grantProtectedMediaOnly (HelixWebChromeClient.kt:117-129). On WebKit/WebView2 the CDM is engine-owned, but Helix still controls (a) whether EME is enabled, (b) the per-site permission UX, and on Linux (c) whether a Widevine CDM is bundled at all.

`P1` · feasibility: `platform-api-dependent` · ~3 eng-weeks


**Why it matters.** Premium streaming is the single biggest 'why doesn't this work in your browser' complaint. On Linux, WebKit2GTK 4.0 with no Widevine means Netflix/Disney+/Amazon Prime show 'this browser is not supported' or black video - a hard product failure vs Chrome/Edge/Brave on Linux which bundle Widevine. On iOS/macOS, WKWebView inherits Apple's FairPlay/Widevine support so DRM usually works, but Helix never surfaces a per-site DRM/EME permission, so the behavior is silent and uncontrollable. This blocks Helix from being a daily-driver browser for streaming users.


**Recommended architecture.** This is engine-owned at the CDM level and CANNOT be reimplemented in a WebView wrapper. Realistic scope per platform: (1) Linux: switch to WebKit2GTK 4.1/WPE with the Widevine CDM, or document Widevine as unsupported - the CDM cannot be shipped by Helix legally/technically without Google's distribution, so this is effectively 'requires a different engine build'. Add to linux/src/browser_window.py the WebKitGTK settings (set_enable_encrypted_media if available on 4.1) gated behind an engine-capability check. (2) iOS/macOS: WKWebView already does FairPlay; add a per-site EME indicator only - no new CDM. (3) Windows: WebView2 inherits Edge's Widevine/PlayReady; expose a permission/indicator only. Add a shared PermissionRequested/EME hook where the platform exposes one.


**Implementation plan.**
1. Audit each engine's actual EME capability: Android WebView (Widevine L3/L1 inherited), WKWebView (FairPlay + Widevine via Apple), WebView2 (Widevine+PlayReady inherited), WebKitGTK 4.0 (NO Widevine).
1. On Linux, evaluate migrating the gi.require_version to WebKit2GTK 4.1 / WPE which can load an external Widevine CDM, and add a build-time CDM provisioning step; if not feasible, explicitly document 'protected/DRM video unsupported on Linux' in settings_dialog About.
1. On Windows, add a CoreWebView2 PermissionRequested handler in MainWindow.xaml.cs to allow/track EME/protected-media requests, mirroring Android's per-resource grant.
1. On iOS/macOS, add a lightweight per-origin 'this site used protected content' indicator; do not attempt to gate the CDM.
1. Add an integration matrix test page (EME requestMediaKeySystemAccess for com.widevine.alpha / com.apple.fps) to confirm per-engine support and surface a clear unsupported message instead of a black screen.

**Code example** — `windows/HelixBrowser/MainWindow.xaml.cs`:

```csharp
// After EnsureCoreWebView2Async completes for a tab's WebView2:
private void WireProtectedMedia(Microsoft.UI.Xaml.Controls.WebView2 webView)
{
    var core = webView.CoreWebView2;
    if (core == null) return;
    core.PermissionRequested += (sender, args) =>
    {
        // WebView2 routes EME/Widevine via the OtherSensors / generic
        // permission surface. Protected media has no PII; grant by default
        // to match Edge/Chrome so Netflix/Spotify play, but record it so the
        // site-info panel can show "used protected content".
        if (args.PermissionKind == Microsoft.Web.WebView2.Core.CoreWebView2PermissionKind.OtherSensors)
        {
            args.State = Microsoft.Web.WebView2.Core.CoreWebView2PermissionState.Allow;
        }
    };
}
```

**Migration.** No persisted-data migration. On Linux a potential engine version bump (4.0 to 4.1/WPE) is a packaging change, not a data change - session.json/prefs.json formats are unaffected. The honest migration note: if Widevine cannot be provisioned on Linux, the migration is a documentation/expectation change (mark DRM unsupported) rather than code.


**Testing.** Per-engine EME capability page in CI-adjacent manual QA: call navigator.requestMediaKeySystemAccess('com.widevine.alpha', ...) and 'com.apple.fps' and assert resolve/reject per platform. Real-site smoke: Netflix/Spotify Web/Disney+ on each platform; expect playback on Android/iOS/macOS/Windows and a clear 'unsupported' message (not a black screen) on Linux. Verify the Windows permission grant does not over-grant camera/mic (only OtherSensors).


#### Gap B13.3 — Windows (WebView2) and Linux (WebKitGTK) have no HTML5 fullscreen-video management. Android fully manages it (HelixWebChromeClient.onShowCustomView/onHideCustomView at lines 56-62 -> MainActivity.exitFullscreenIfActive at 660, system-UI hide). MainWindow.xaml.cs has no ContainsFullScreenElementChanged handler and linux/src/browser_window.py wires no enter/leave-fullscreen signal. The result: pressing the fullscreen button on a video either does nothing useful, leaves browser chrome overlapping the video, or traps the user with no clean exit.

`P2` · feasibility: `feasible-in-webview` · ~1 eng-weeks


**Why it matters.** Fullscreen video is table-stakes: a user watching YouTube/Twitch expects the video to fill the screen and the browser chrome (tab strip, address bar, window decorations) to disappear, then restore on Esc. On Windows the WebView2 element fullscreens within its bounds but the app's tab strip and title bar remain, so it is not true fullscreen; on Linux's custom client-side-decorated window the CSD buttons and tab bar stay visible over the video. This is an immediately visible quality gap vs every competitor and is cheap to fix because the engine fires a fullscreen-element event the app just needs to react to.


**Recommended architecture.** Windows: subscribe to CoreWebView2.ContainsFullScreenElementChanged in MainWindow.xaml.cs; on enter, hide the tab strip + custom title bar and call AppWindow.SetPresenter(FullScreen); on leave, restore. Linux: connect WebKit2 WebView 'enter-fullscreen'/'leave-fullscreen' signals in browser_window.py and toggle self.fullscreen()/unfullscreen() plus hide the custom header/tab bar. No new files needed - extend the existing window classes.


**Implementation plan.**
1. Windows: after EnsureCoreWebView2Async, subscribe core.ContainsFullScreenElementChanged.
1. Windows: in the handler, when core.ContainsFullScreenElement is true, collapse the tab strip + custom title-bar Grid and set the AppWindow OverlappedPresenter to full-screen; on exit, restore previous chrome + presenter.
1. Linux: in _create_webview (browser_window.py:~360), connect 'enter-fullscreen' and 'leave-fullscreen' on the WebKit2.WebView.
1. Linux: in the enter handler call self.window.fullscreen() and hide the CSD header/tab bar; in leave call unfullscreen() and show them; return False to let WebKit proceed.
1. Test Esc/double-click exit and that exiting via the site's own control also restores chrome on both platforms.

**Code example** — `linux/src/browser_window.py`:

```python
# Inside _create_webview, after settings are configured:
webview.connect("enter-fullscreen", self._on_enter_fullscreen)
webview.connect("leave-fullscreen", self._on_leave_fullscreen)

def _on_enter_fullscreen(self, webview):
    # Hide our client-side-decorated chrome so the <video> truly fills
    # the screen, then fullscreen the GTK window. Return False so WebKit
    # continues into its fullscreen state.
    self.header_bar.hide()
    self.tab_bar.hide()
    self.window.fullscreen()
    return False

def _on_leave_fullscreen(self, webview):
    self.window.unfullscreen()
    self.header_bar.show()
    self.tab_bar.show()
    return False
```

**Migration.** No data or back-compat impact - purely additive window-state handling. Persisted prefs/session formats unchanged. Save the pre-fullscreen window geometry in memory so unfullscreen restores the prior size.


**Testing.** Manual: YouTube/Twitch fullscreen button -> video fills screen, chrome gone; Esc and the site's own exit control both restore chrome and prior window size; double-toggle does not desync chrome visibility; on Linux confirm the CSD min/max/close buttons return. Linux is only py_compile-verified in this repo, so add a manual checklist; Windows has no in-repo MSBuild, so verify on a dev machine.


#### Gap B13.4 — Video-decoding / codec capability and hardware acceleration are entirely engine-inherited and unverified per platform, with a concrete risk on Linux WebKitGTK 4.0 (often software-only VP9/AV1 and no HEVC) and on Windows where WebView2 hardware decode depends on the Edge runtime + GPU drivers. Helix sets enable_mediasource on Linux (browser_window.py:371) and relies on defaults everywhere else, but never probes or surfaces codec support, so AV1/HEVC/high-bitrate content may stutter or fail silently with no fallback messaging.

`P3` · feasibility: `requires-engine-fork` · ~1.5 eng-weeks


**Why it matters.** If YouTube serves AV1 or a site serves HEVC/4K and the engine lacks hardware decode, playback stutters, drains battery, or shows a black frame - and the user blames Helix, not the engine. Chrome/Edge/Brave negotiate codecs and fall back gracefully; Helix does no negotiation and has no diagnostics, so failures are opaque. This is a competitive/UX risk rather than a buildable feature: Helix cannot add a decoder, but it can detect capability and message clearly.


**Recommended architecture.** True hardware decode / codec support is owned by the host engine (Blink/WebKit GPU pipeline) and cannot be added in a wrapper - adding AV1/HEVC HW decode would require forking the engine, which is explicitly out of scope. The realistic Helix-side work is a capability probe: a small CodecSupport helper per platform that calls MediaSource.isTypeSupported / HTMLVideoElement.canPlayType for the codecs of interest and surfaces a non-blocking notice when an unsupported codec is requested, plus ensuring no app setting disables GPU acceleration (Android keeps hardwareAccelerated default; verify none of the platforms force software rendering).


**Implementation plan.**
1. Add a JS capability probe injected at document-end that records MediaSource.isTypeSupported for vp9/av01/hvc1/avc1 and posts results back to native via the existing message bridges.
1. Confirm no platform forces software rendering: Android manifest does not set hardwareAccelerated=false (verified default-on); review macOS/iOS/Windows/Linux for any GPU-disabling flag.
1. On Linux, document that AV1/HEVC may be software-decoded under WebKitGTK 4.0 and evaluate the same 4.1/WPE migration noted for DRM, which also improves codec/HW-decode.
1. Surface a lightweight 'this video uses a format your device may not accelerate' info chip instead of failing silently.
1. Add a manual codec smoke matrix (YouTube AV1 forced, HEVC sample, 4K60) per platform.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/HelixWebView.kt`:

```kotlin
// Capability probe injected after page load. HW decode itself is owned by
// Android System WebView (Blink) and cannot be added by Helix; this only
// reports what the engine supports so the UI can warn instead of stuttering.
fun probeCodecSupport(onResult: (Map<String, Boolean>) -> Unit) {
    val js = """
        (function(){
          var ms = window.MediaSource;
          function ok(t){ try { return !!ms && ms.isTypeSupported(t); } catch(e){ return false; } }
          return JSON.stringify({
            vp9:  ok('video/webm; codecs=\"vp9\"'),
            av1:  ok('video/mp4; codecs=\"av01.0.05M.08\"'),
            hevc: ok('video/mp4; codecs=\"hvc1.1.6.L93.B0\"'),
            h264: ok('video/mp4; codecs=\"avc1.640028\"')
          });
        })();
    """.trimIndent()
    runCatching {
        evaluateJavascript(js) { raw ->
            val map = runCatching {
                val o = org.json.JSONObject(raw.trim('"').replace("\\\"", "\""))
                o.keys().asSequence().associateWith { o.getBoolean(it) }
            }.getOrDefault(emptyMap())
            onResult(map)
        }
    }
}
```

**Migration.** None - read-only capability probing, no persisted state. Back-compat safe.


**Testing.** Per-platform codec smoke matrix: force YouTube AV1 (youtube.com/account_playback), play an HEVC fragment and a 4K60 clip; confirm the probe map matches observed playback and that the info chip appears only when a requested codec is unsupported. Measure CPU/battery during AV1 on Linux to confirm the software-decode warning is warranted. No CI for codec HW; manual on real devices.


---

## B14. Performance / Resource  `[performance-resource]`

**Overall feasibility:** partially-feasible


Verified against the real code, not the inventory. Across the 7 sub-features Helix is materially behind every flagship. (1) Memory Saver is genuinely implemented only on Android (LRU pool cap MAX_LIVE_WEBVIEWS=6, onTrimMemory/onLowMemory eviction, suspend-on-pause via evictSuspendedWebViews — MainActivity.kt:2681,2764-2813,3071) but iOS evicts only on memory-warning (TabManager.swift:41-46) and macOS/Linux/Windows are pure flag-flip STUBS that free no RAM (macOS WebViewModel.swift:232-242, Linux tab_manager.py:150-155) behind user-facing toggles that therefore lie. Even Android only suspends on onPause, never on a foreground timer. (2) Battery Saver: entirely absent everywhere — the only battery code spoofs navigator.getBattery for anti-fingerprinting (PrivacyManager.kt:229). (3) Predictive Loading / Prefetching / Prerendering / Speculative Rendering: a full grep for preconnect/prefetch/prerender/dns-prefetch/speculationrules returns ZERO across all five platforms; HelixWebView only sets cacheMode=LOAD_DEFAULT (HelixWebView.kt:71). (4) GPU scheduling is engine-owned; Android relies on default hardwareAccelerated (no explicit manifest flag, AndroidManifest.xml:20) + setLayerType(LAYER_TYPE_HARDWARE) (HelixWebView.kt:111), with NO setRendererPriorityPolicy and NO onRenderProcessGone recovery — a stability gap. Feasibility split honestly: Memory Saver, Battery Saver, omnibox Predictive preconnect, renderer-priority and onRenderProcessGone are all feasible-in-webview / platform-api-dependent and should ship (P1/P2). Prefetching is partially feasible via injected resource hints. True cross-document Prerendering and real GPU scheduling are ENGINE-OWNED — a WebView wrapper cannot spin up an isolated prerender/render process — so only constrained subsets (Speculation Rules injection the engine MAY honor, or a single guarded off-screen WebView) are reachable; full parity there is requires-engine-fork and out of scope. Highest-leverage wins: fix the desktop Memory-Saver stubs to actually destroy/restore webviews, and add omnibox-driven preconnect for instant-feeling URL navigation.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Memory Saver (tab discard/sleeping that actually frees RAM) | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | M |
| Battery Saver / power-aware throttling | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | med | M |
| GPU Scheduling / hardware-accelerated compositing | 🟡 partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | low | L |
| Predictive Loading (omnibox-driven preconnect/preresolve) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **high** | M |
| Speculative Rendering (Speculation Rules API / NoState prefetch) | ❌ missing | ✅ | ✅ | 🟡 | ❌ | ❌ | ✅ | med | L |
| Prefetching (link/resource prefetch, dns-prefetch) | ❌ missing | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ | **high** | M |
| Prerendering (full-page prerender of next navigation) | ❌ missing | ✅ | ✅ | 🟡 | ❌ | ❌ | ✅ | med | XL |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B14.1 — Memory Saver is real on Android but a no-op stub on macOS/Linux/Windows and memory-warning-only on iOS. macOS WebViewModel.suspendInactiveTabs() (macos/HelixBrowser/WebViewModel.swift:232-242) and Linux tab_manager.suspend_inactive_tabs() (linux/src/tab_manager.py:150-155) only flip tabs[i].isSuspended = true and never destroy the WKWebView / WebKitWebView or reload-on-return, so zero RAM is reclaimed. Android does free memory (MainActivity.evictSuspendedWebViews + enforceWebViewPoolCap, MAX_LIVE_WEBVIEWS=6, MainActivity.kt:2764-2785,3071) but suspendInactiveTabs() is only invoked from onPause (MainActivity.kt:2681), so a long foreground session with many tabs never sleeps until the app is backgrounded or onTrimMemory fires.

`P1` · feasibility: `feasible-in-webview` · ~3 eng-weeks


**Why it matters.** A multi-tab session on macOS/Linux/Windows grows WebView memory unbounded and the user-facing 'Suspend inactive tabs' toggle (macOS SettingsView.swift:196, Linux settings_dialog.py:124) is a lie — it persists a pref that frees nothing. On heavy sites the desktop builds will be OOM-killed or swap-thrash where Chrome/Edge Memory Saver would have reclaimed hundreds of MB. This is both a competitive gap and a trust/QA defect (decorative setting).


**Recommended architecture.** Make suspend actually destroy + restore the per-tab webview on every platform. macOS: in WebView.swift webViewCache, on suspend remove and .stopLoading()/teardown the cached WKWebView for the tab id and persist tab.url + interactionState; recreate lazily on switch (mirror Android). Linux: in browser_window.py destroy the WebKit2.WebView GTK widget for suspended tabs and rebuild from tab.url. Android: add a foreground idle timer (Handler/coroutine) in MainActivity that calls tabManager.suspendInactiveTabs()+evictSuspendedWebViews() while running, not only onPause; surface a Memory-Saver mode (Off / Auto / Aggressive) in SettingsActivity controlling SUSPEND_TIMEOUT_MS (TabManager.kt:71).


**Implementation plan.**
1. Android: extract a suspendIdleTabs() helper and schedule it on a 30s coroutine tick in onResume, cancel in onPause; add timeout tiers (5/10/30 min) read from Prefs.
1. macOS: in WebViewModel.suspendInactiveTabs, after setting isSuspended call WebView.discard(tabId:) which removes the entry from webViewCache and tears down the WKWebView; in the NSViewRepresentable, when a suspended tab becomes active, recreate the WKWebView and load(tab.url) restoring interactionState.
1. Linux: wire suspend to actually .destroy() the WebKit2.WebView and drop it from the tab->view map; recreate+load_uri on activation.
1. Windows: implement the same discard/recreate on the WebView2 control (Close() the CoreWebView2 for suspended tabs).
1. Add a visible 'sleeping tab' affordance in each tab strip and a Memory-Saver settings row that maps to the timeout tiers.
1. Persist URL + scroll/nav state before discard so restore is seamless.

**Code example** — `macos/HelixBrowser/WebViewModel.swift`:

```swift
private func suspendInactiveTabs() {
    let cutoff = Date().addingTimeInterval(-suspendTimeout)
    for i in tabs.indices where tabs[i].id != activeTabId
        && tabs[i].lastAccessTime < cutoff
        && !tabs[i].isSuspended
        && !tabs[i].isPinned {
        // Capture restore state BEFORE tearing the WKWebView down.
        if let wv = WebView.webViewCache[tabs[i].id] {
            tabs[i].url = wv.url?.absoluteString ?? tabs[i].url
            tabs[i].interactionState = wv.interactionState as? Data
            wv.stopLoading()
            wv.navigationDelegate = nil
            wv.removeFromSuperview()
            WebView.webViewCache.removeValue(forKey: tabs[i].id) // <- actually frees RAM
        }
        tabs[i].isSuspended = true
    }
}

// On reactivation the NSViewRepresentable rebuilds the WKWebView and:
//   if let state = tab.interactionState { wv.interactionState = state }
//   else { wv.load(URLRequest(url: URL(string: tab.url)!)) }
```

**Migration.** No schema change; tab JSON already stores url/isSuspended (WebTab.swift:14, BrowserTab.swift:16). Add an optional interactionState blob with a nil-safe decode (decodeIfPresent) so old session files still load. On macOS interactionState is opaque Data, omit on encode when nil.


**Testing.** Instrument RAM with a 20-tab session: assert resident memory drops after the suspend timeout (XCTest measure block on macOS, Espresso + Debug.getMemoryInfo on Android). Assert a suspended tab restores to the same URL + scroll position on activation. Add a unit test that the Settings toggle, when ON, results in webViewCache.count shrinking; when OFF, count is stable.


#### Gap B14.2 — No Predictive Loading / Prefetching anywhere. A full grep for prefetch/prerender/preconnect/dns-prefetch/Speculation Rules across all five platforms returns nothing; HelixWebView only sets cacheMode=WebSettings.LOAD_DEFAULT (HelixWebView.kt:71). The omnibox already computes a top suggestion (BrowserViewModel inline-autocomplete) but never warms the connection to it.

`P1` · feasibility: `partially-feasible` · ~2 eng-weeks


**Why it matters.** Chrome/Edge/Brave preconnect (TCP+TLS) to the omnibox's top prediction the moment you start typing, shaving 100-400ms off the eventual navigation. Helix navigates cold every time, so it feels measurably slower than every competitor on the single most-used interaction (typing a URL). This is the highest-leverage perceived-performance win available to a WebView shell.


**Recommended architecture.** Predictive preconnect is achievable in-webview by injecting <link rel=preconnect>/<link rel=dns-prefetch> hints, or natively. Android: add a PredictiveLoader object that, on each debounced omnibox suggestion, calls WebView.startSafeBrowsing-style warmup — concretely use the current tab's WebView to evaluateJavascript an injected link-rel-preconnect into document.head, or use HttpURLConnection/OkHttp to open+close a HEAD/handshake to the predicted origin off-main-thread (TCP/TLS warm in the OS connection cache the WebView shares is NOT guaranteed across the WebView's own network stack, so the JS link-hint route is the reliable one). Hook it from BrowserViewModel where the top suggestion is already known. iOS/macOS: inject the same link-rel hints via WKUserScript or evaluateJavaScript on the active web content.


**Implementation plan.**
1. Create engine/PredictiveLoader.kt with preconnect(origin: String) that injects '<link rel=preconnect crossorigin>' + '<link rel=dns-prefetch>' into the active WebView's document.head via evaluateJavascript, debounced and deduped per origin.
1. In BrowserViewModel, when the debounced top omnibox suggestion resolves to a URL, extract its origin and call PredictiveLoader.preconnect on the foreground WebView.
1. Gate behind a 'Preload pages for faster browsing' setting (default ON in normal, FORCED OFF in incognito to avoid leaking typed prefixes to third parties).
1. Add a host allowlist guard so we never preconnect to the literal text the user is still typing if it is not yet a plausible host.
1. Mirror via WKUserScript on iOS/macOS and document.head injection on Linux/Windows.
1. Clear the per-session dedupe set on tab switch and on incognito teardown.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/PredictiveLoader.kt`:

```kotlin
object PredictiveLoader {
    private val warmed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Inject resource hints so the engine opens TCP+TLS to the predicted origin
     *  while the user is still typing. No-op in incognito (caller-gated). */
    fun preconnect(webView: android.webkit.WebView?, predictedUrl: String) {
        val origin = runCatching { java.net.URI(predictedUrl).let { "${it.scheme}://${it.host}" } }
            .getOrNull() ?: return
        if (!origin.startsWith("https://")) return
        if (!warmed.add(origin)) return // dedupe per session
        val js = """(function(o){
            var p=document.createElement('link');p.rel='preconnect';p.href=o;p.crossOrigin='anonymous';
            var d=document.createElement('link');d.rel='dns-prefetch';d.href=o;
            document.head.appendChild(p);document.head.appendChild(d);})('${'$'}origin');"""
        webView?.post { runCatching { webView.evaluateJavascript(js, null) } }
    }

    fun reset() = warmed.clear()
}
```

**Migration.** Pure additive; no persisted data. New Prefs boolean preload_pages default true (false-forced in incognito). Reset() must be called from MainActivity incognito teardown and onClearBrowsingData.


**Testing.** Use a local instrumented server (MockWebServer) and assert a TCP connection / TLS handshake is observed to the predicted origin before navigation commits. Unit-test origin extraction + https-only + dedupe. Manually verify with chrome://net-export-equivalent timing that TTFB drops on the predicted navigation. Verify zero preconnects fire in incognito.


#### Gap B14.3 — No Speculative Rendering / Prerendering. The Speculation Rules API (<script type=speculationrules>) and full next-page prerender are entirely absent; setSupportMultipleWindows is even left off (HelixWebView.kt:58) and onCreateWindow is unwired, so the shell has no notion of warming a hidden navigation.

`P2` · feasibility: `requires-engine-fork` · ~4 eng-weeks


**Why it matters.** Chrome/Edge/Arc prerender the top omnibox prediction so the next page paints instantly (near-0ms navigation). Helix cannot match instant-paint. However, true cross-document prerendering with a separate render process is an ENGINE capability — a WebView wrapper cannot spin up an out-of-tab prerender process. The honest position is: full prerender requires the engine; only a constrained subset (Speculation Rules injection that the engine MAY honor, or an off-screen second WebView for same-origin same-site cases) is reachable.


**Recommended architecture.** Cross-document prerender with process isolation is owned by Blink/WebKit and cannot be re-implemented in the wrapper. Realistic constrained alternative: (a) inject Speculation Rules JSON for the predicted URL and let the underlying engine prerender if it supports the API (WebView2/Blink-based Edge may honor it; WKWebView/WebKit2GTK largely will not); (b) for a small subset, maintain ONE hidden off-screen HelixWebView that loads the predicted URL and is promoted to the visible tab on commit, with strict guards (https-only, idempotent GET, no cross-site, discard on mismatch). Add engine/SpeculativeLoader.kt to encapsulate both paths and feature-detect per platform.


**Implementation plan.**
1. Document that full prerender is engine-owned and out of scope for the wrapper; ship only the constrained subset.
1. Add SpeculativeLoader that injects a speculationrules script for the predicted URL on WebView2/Blink platforms (feature-detect HTMLScriptElement.supports('speculationrules')).
1. For the off-screen-WebView subset on Android, prototype a single hidden HelixWebView prewarmed with the predicted GET; on navigation match, swap it into the pool; on any mismatch or POST, destroy it. Strictly cap to one to bound memory.
1. Hard-gate behind a power/data/battery check and incognito-off.
1. Measure: only keep the off-screen path if memory cost < benefit on mid-tier devices.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/SpeculativeLoader.kt`:

```kotlin
object SpeculativeLoader {
    /** Best-effort: hand the engine a Speculation Rules hint for the predicted
     *  navigation. The WRAPPER cannot prerender itself — only the underlying
     *  Blink/WebKit engine can, and only if it supports the API. Fail-soft. */
    fun speculate(webView: android.webkit.WebView?, predictedUrl: String) {
        if (!predictedUrl.startsWith("https://")) return
        val rules = """{"prerender":[{"source":"list","urls":["$predictedUrl"]}]}"""
        val js = """(function(r){
            if(!HTMLScriptElement.supports||!HTMLScriptElement.supports('speculationrules'))return;
            var s=document.createElement('script');s.type='speculationrules';s.textContent=r;
            document.head.appendChild(s);})('${rules.replace("'", "\\'")}');"""
        webView?.post { runCatching { webView.evaluateJavascript(js, null) } }
    }
}
```

**Migration.** Additive, no persisted state. Must be incognito-gated and battery/data-saver-gated. Off-screen-WebView path (if pursued) must integrate with MAX_LIVE_WEBVIEWS accounting in MainActivity so it cannot push live tabs out.


**Testing.** Feature-detect test: assert speculationrules script is injected only where supported. On the off-screen path, assert the hidden WebView is destroyed on URL mismatch and that the live-WebView count never exceeds MAX_LIVE_WEBVIEWS+1. Verify no speculation in incognito or when data-saver is on.


#### Gap B14.4 — No Battery Saver / power-aware throttling. There is zero battery-state code; the only battery reference spoofs navigator.getBattery for anti-fingerprinting (PrivacyManager.kt:229-233). No reduction of background-tab timers, animation, or prefetch when the device is low/charging-off.

`P2` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Chrome/Edge/Safari throttle background tabs and reduce work on battery; Firefox/Edge expose explicit efficiency modes. Helix keeps every background WebView fully live until onPause, so a background video/animation tab drains battery. Pairing a battery check with the existing suspend machinery is cheap and directly improves device battery life and thermals.


**Recommended architecture.** Add engine/PowerManagerBridge.kt that observes BatteryManager / PowerManager.isPowerSaveMode and ACTION_BATTERY_CHANGED. When in power-save or below a threshold and not charging: (1) shorten the Memory-Saver suspend timeout, (2) call webView.onPause() on all non-foreground pooled WebViews proactively (already have the loop at MainActivity.kt:2671), (3) disable PredictiveLoader/SpeculativeLoader. Surface a 'Battery Saver: Auto/On/Off' setting. iOS: observe ProcessInfo.isLowPowerModeEnabled and do the same with the suspend path.


**Implementation plan.**
1. Add PowerManagerBridge registering a BroadcastReceiver for power-save + battery level.
1. On entering power-save: set an aggressive suspend timeout, pause background WebViews, gate off predictive/speculative loaders.
1. Add a Battery Saver settings row (Auto = follow OS power-save) in SettingsActivity.
1. iOS: hook NSProcessInfoPowerStateDidChange to ProcessInfo.processInfo.isLowPowerModeEnabled and route to TabManager.suspendInactiveTabs.
1. Ensure restore on power-save exit (un-pause foreground tab).

**Code example** — `android/app/src/main/java/com/helix/browser/engine/PowerManagerBridge.kt`:

```kotlin
class PowerManagerBridge(
    private val context: android.content.Context,
    private val onPowerSaveChanged: (Boolean) -> Unit
) {
    private val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: android.content.Intent) {
            onPowerSaveChanged(isSaving())
        }
    }
    fun isSaving(): Boolean = pm.isPowerSaveMode
    fun register() {
        context.registerReceiver(receiver,
            android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onPowerSaveChanged(isSaving())
    }
    fun unregister() = runCatching { context.unregisterReceiver(receiver) }
}
```

**Migration.** Additive. New Prefs enum battery_saver_mode (auto/on/off, default auto). No data migration. When ON, it overrides predictive/speculative gating and tightens SUSPEND_TIMEOUT_MS.


**Testing.** Use adb shell dumpsys battery set / cmd power set-mode to simulate power-save; assert background WebViews receive onPause and PredictiveLoader.preconnect is suppressed. Unit-test the mode resolution (auto follows OS flag). On iOS, toggle Low Power Mode in simulator and assert suspendInactiveTabs runs.


#### Gap B14.5 — GPU Scheduling / hardware acceleration is engine-owned and only coarsely controllable. Android relies on the default hardwareAccelerated=true (the manifest <application> has NO explicit android:hardwareAccelerated, AndroidManifest.xml:20-32) plus per-WebView setLayerType(LAYER_TYPE_HARDWARE) (HelixWebView.kt:111). There is no renderer-priority policy (setRendererPriorityPolicy) and no onRenderProcessGone recovery (grep returns none).

`P2` · feasibility: `platform-api-dependent` · ~2 eng-weeks


**Why it matters.** Real GPU rasterization/compositing scheduling is inside Blink/WebKit and cannot be tuned by a wrapper — correctly out of scope. But two cheap, in-scope wins are missing: (1) renderer priority for background tabs (Android WebView.setRendererPriorityPolicy lets the OS reclaim background renderer CPU/GPU), and (2) onRenderProcessGone handling so a GPU/renderer crash recovers gracefully instead of killing the tab/app. Lacking the latter is a stability risk, not just performance.


**Recommended architecture.** GPU scheduling itself: requires-engine-fork (do not attempt). In-scope: in HelixWebView, set setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT for foreground, RENDERER_PRIORITY_BOUND/WAIVED when a tab is backgrounded — driven from MainActivity tab-switch). Add onRenderProcessGone override in HelixWebViewClient to detach the dead WebView and recreate from tab.url, preventing app death. Keep setLayerType but make it lifecycle-aware.


**Implementation plan.**
1. Add onRenderProcessGone(view, detail) to HelixWebViewClient: if !detail.didCrash() it was an OOM reclaim — remove from pool, recreate lazily from tab.url; return true to keep the app alive.
1. In MainActivity tab switch, call setRendererPriorityPolicy with IMPORTANT for the new foreground WebView and a lower priority for the one being backgrounded (API 26+).
1. Document GPU scheduling as engine-owned / out of scope in code comments to prevent future false claims.
1. Verify LAYER_TYPE_HARDWARE interaction with backgrounded/paused tabs.

**Code example** — `android/app/src/main/java/com/helix/browser/engine/HelixWebViewClient.kt`:

```kotlin
override fun onRenderProcessGone(
    view: android.webkit.WebView,
    detail: android.webkit.RenderProcessGoneDetail
): Boolean {
    // Renderer (GPU/JS process) died — OS-reclaimed or crashed. A wrapper cannot
    // schedule the GPU, but it MUST survive a dead renderer. Detach the corpse and
    // let MainActivity rebuild this tab from its persisted URL on next focus.
    android.util.Log.w("HelixWebView", "render process gone, crashed=" + detail.didCrash())
    (view.parent as? android.view.ViewGroup)?.removeView(view)
    onRenderProcessGoneListener?.invoke(view) // MainActivity: evict from pool + recreate
    view.destroy()
    return true // handled — do NOT let the app die
}
```

**Migration.** Additive; no persisted data. Requires a callback hook from HelixWebViewClient into MainActivity's pool (add onRenderProcessGoneListener). Tab URL is already persisted, so recreation is lossless to the last committed URL (scroll state may reset on a hard renderer crash).


**Testing.** Simulate renderer death with chrome://crash equivalent / adb shell am send-trim-memory to force OOM; assert the app survives and the tab reloads from tab.url instead of crashing. Espresso test: background a tab, assert renderer priority dropped (verify via no exception + behavior). Confirm no regression in foreground compositing (visual smoke test).


---

## B15. OS Integration  `[os-integration]`

**Overall feasibility:** partially-feasible


Verified against source: every OS-integration secret/biometric/notification sub-feature is MISSING in Helix. grep for keychain|kSecClass|SecItem|SecretService|libsecret|CredentialManager|PasswordVault|LocalAuthentication|Windows.Hello|BiometricPrompt|UNUserNotification returned zero functional hits across Android/iOS/macOS/Windows/Linux. macOS has NO .entitlements file (so no App Sandbox, no Hardened Runtime config, no keychain-access-group) and still carries NSAllowsArbitraryLoads. Linux DEBIAN/control has no libsecret dependency and uses the deprecated X11-era Gdk.Screen.get_default() (main.py:37), so Wayland is only implicit/partial. Android manifest has no POST_NOTIFICATIONS and onPermissionRequest (HelixWebChromeClient.kt:72) ignores web notifications. Windows is unpackaged (WindowsPackageType=None) with no taskbar/jumplist/badge/Hello/Credential-Manager code, though WindowsAppSDK 1.5 is referenced (APIs reachable). The ONLY supported items: Apple Silicon via a real arm64+x86_64 universal binary (build_dmg.sh:25-45), Windows ARM64 (RuntimeIdentifiers win-x64;win-arm64), and basic Linux X11. Overall feasibility is partially-feasible: secret stores, biometric gates, taskbar, and macOS entitlements are all buildable in a WebView shell and are the foundation for the bigger password-manager/passkey gap. The genuinely engine-constrained piece is in-page web Notification delivery on WKWebView (macOS/iOS) and Android WebView, where the host gets no public delegate -- there Helix can only do APP-level notifications, not arbitrary web notifications, without an engine fork. Handoff/Continuity is Safari-only and low priority. Most material gaps to fund: (1) OS keychain/secret-store abstraction P1, (2) biometric gate P2, (3) native/web notifications P2, (4) macOS entitlements+notarization P2, (5) Wayland modernization P3. Relevant files: /home/thien/Projects/helix_browser/macos/HelixBrowser/Info.plist, /home/thien/Projects/helix_browser/macos/build_dmg.sh, /home/thien/Projects/helix_browser/linux/src/main.py, /home/thien/Projects/helix_browser/linux/src/browser_window.py, /home/thien/Projects/helix_browser/linux/build/helix-browser_3.0.0_amd64/DEBIAN/control, /home/thien/Projects/helix_browser/android/app/src/main/AndroidManifest.xml, /home/thien/Projects/helix_browser/android/app/src/main/java/com/helix/browser/engine/HelixWebChromeClient.kt, /home/thien/Projects/helix_browser/windows/HelixBrowser/HelixBrowser.csproj.


### Parity matrix

| Feature | Helix | Chr | Edg | Brv | FF | Saf | Arc | Risk | Cx |
|---|---|:--:|:--:|:--:|:--:|:--:|:--:|---|:--:|
| Windows: Native Notifications (web Notification API + toast) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | med | M |
| Windows: Windows Hello (biometric unlock for saved passwords/payments) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ❌ | ✅ | **high** | L |
| Windows: Credential Manager / OS password vault integration | ❌ missing | 🟡 | ✅ | 🟡 | ❌ | ❌ | 🟡 | **high** | L |
| Windows: Taskbar integration (jumplist, thumbnail toolbar, overlay/progress badge) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ❌ | 🟡 | low | M |
| macOS: Keychain (saved-credential storage) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ✅ | ✅ | **high** | L |
| macOS: Touch ID (biometric gate for credentials/autofill) | ❌ missing | ✅ | ✅ | ✅ | 🟡 | ✅ | ✅ | **high** | M |
| macOS: Handoff / Continuity (NSUserActivity, iCloud tab handoff) | ❌ missing | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | low | L |
| macOS: Apple Silicon native (arm64 universal binary) | ✅ supported | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | low | S |
| Linux: Secret Service / libsecret (gnome-keyring/KWallet credential storage) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | **high** | M |
| Linux: Wayland native support (no XWayland fallback issues, fractional scaling) | 🟡 partial | 🟡 | 🟡 | 🟡 | ✅ | ❌ | ❌ | med | M |
| Linux: X11 support | ✅ supported | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | low | S |
| Cross-platform: Web Notification API (page notifications + OS delivery) | ❌ missing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | med | L |
| Windows ARM64 native build | ✅ supported | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | low | S |

*Verifier confidence: high; no status corrections.*

### Gaps & remediation


#### Gap B15.1 — OS keychain / secret-store integration is entirely absent on every platform (no Keychain on macOS/iOS, no Credential Manager/DPAPI on Windows, no Secret Service/libsecret on Linux, no Android Keystore). Verified: grep for keychain|kSecClass|SecItem|SecretService|libsecret|CredentialManager|PasswordVault returned zero hits across all source trees. macOS ships with NO .entitlements file at all (find macos -iname '*.entitlements' = 0 results), so even the keychain-access-group entitlement is missing. Linux DEBIAN/control Depends list has no libsecret.

`P1` · feasibility: `feasible-in-webview` · ~4 eng-weeks


**Why it matters.** This is the foundational dependency for a password manager, autofill, and passkeys — the single largest product-parity gap vs every shipping browser. Without a secret store, Helix cannot persist credentials safely; storing them in UserDefaults/SharedPreferences/SQLite plaintext (the only storage that exists today) would be a critical security defect. Every competitor uses the OS secret store. Users cannot adopt Helix as a primary browser if it cannot remember a single login.


**Recommended architecture.** Add a per-platform SecretStore abstraction that the future password-manager feature depends on. macOS/iOS: new KeychainStore.swift wrapping Security.framework SecItemAdd/SecItemCopyMatching with kSecClassGenericPassword + kSecAttrAccessibleWhenUnlockedThisDeviceOnly, plus add a HelixBrowser.entitlements with keychain-access-groups. Windows: new Engine/CredentialStore.cs using Windows.Security.Credentials.PasswordVault (WinRT, already reachable via WindowsAppSDK 1.5 referenced in the csproj) with a DPAPI ProtectedData fallback for unpackaged builds. Linux: new secret_store.py wrapping libsecret via gi.repository.Secret (add gir1.2-secret-1 to DEBIAN/control Depends). Android: KeyStoreSecretStore.kt using AndroidKeyStore-backed AES-GCM + EncryptedSharedPreferences.


**Implementation plan.**
1. Define a common logical interface (store(service, account, secret), retrieve, delete, list) documented once and implemented natively per platform.
1. macOS/iOS: implement KeychainStore.swift over Security.framework; add HelixBrowser.entitlements (keychain-access-groups, com.apple.security.app-sandbox if sandboxing) and wire it into build_dmg.sh codesign step.
1. Windows: implement CredentialStore.cs over PasswordVault; because WindowsPackageType=None (unpackaged, confirmed in csproj), add a DPAPI ProtectedData fallback keyed to the user profile since PasswordVault requires package identity.
1. Linux: add gir1.2-secret-1 dependency and implement secret_store.py over libsecret with a clear 'no keyring available' degraded path.
1. Android: implement KeyStoreSecretStore.kt with EncryptedSharedPreferences (Jetpack Security) backed by AndroidKeyStore.
1. Unit-test round-trip store/retrieve/delete on the one buildable platform (Android) and add static review for the rest.

**Code example** — `macos/HelixBrowser/KeychainStore.swift`:

```swift
import Foundation
import Security

/// Native macOS/iOS secret store backing the (future) Helix password manager.
/// Replaces the insecure UserDefaults persistence used elsewhere in the app.
enum KeychainStore {
    private static let service = "com.helix.browser.credentials"

    @discardableResult
    static func save(account: String, secret: Data) -> Bool {
        let base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(base as CFDictionary)

        var attrs = base
        attrs[kSecValueData as String] = secret
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        return SecItemAdd(attrs as CFDictionary, nil) == errSecSuccess
    }

    static func read(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    @discardableResult
    static func delete(account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
```

**Migration.** No existing credential data to migrate (no secret store exists today). The migration concern is forward: any future password-manager records MUST land in KeychainStore, never UserDefaults. On macOS, adding an .entitlements file changes the code-signing identity requirements — existing unsigned/ad-hoc dev builds keep working; notarized release builds must re-sign with the new entitlement. No user-facing data format change.


**Testing.** XCTest round-trip (save/read/delete, overwrite, missing-account returns nil) on macOS via xcodebuild; manual Keychain Access.app inspection to confirm items are device-only and not iCloud-synced. Because the iOS .xcodeproj is not in the repo, iOS coverage is static review only. Add a kSecAttrAccessible assertion test to prevent accidental cloud-sync regressions.


#### Gap B15.2 — Biometric unlock (Windows Hello, macOS/iOS Touch ID/Face ID, Android BiometricPrompt) is entirely absent. Verified: grep for LocalAuthentication|LAContext|UserConsentVerifier|Windows.Hello|BiometricPrompt returned zero functional hits (the only 'FaceID'/'biometric' matches are anti-fingerprinting JS, unrelated).

`P2` · feasibility: `platform-api-dependent` · ~3 eng-weeks


**Why it matters.** Biometric gating is the expected UX for unlocking saved passwords, autofilling payment cards, and authorizing passkey use. Without it, any future credential store is either always-unlocked (insecure) or password-gated (poor UX). It is also the user-facing half of WebAuthn/passkey support, which every competitor ships. Its absence blocks a trustworthy password-manager launch.


**Recommended architecture.** A thin BiometricGate per platform invoked before reading from the SecretStore. macOS/iOS: BiometricGate.swift using LocalAuthentication LAContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics). Windows: Engine/BiometricGate.cs using Windows.Security.Credentials.UI.UserConsentVerifier.RequestVerificationAsync (WinRT, reachable via WindowsAppSDK). Android: BiometricGate.kt using androidx.biometric.BiometricPrompt. Note WebAuthn/passkey ceremonies themselves are engine-owned (WebView decides) and cannot be driven by Helix — this gate only protects Helix's OWN credential store, not in-page WebAuthn.


**Implementation plan.**
1. Add BiometricGate per platform exposing a single authenticate(reason) -> Bool/async.
1. Gate every SecretStore.read used for autofill behind BiometricGate when a 'require biometric' pref is on; fall back to OS account password when biometrics unavailable.
1. Windows: handle unpackaged identity — UserConsentVerifier works without package identity but check UserConsentVerifierAvailability first and degrade gracefully.
1. Add a Settings toggle ('Require biometric to autofill') wired to each platform Prefs store.
1. Android: implement and unit/instrumentation-test BiometricPrompt flow (only CI-buildable platform).

**Code example** — `windows/HelixBrowser/Engine/BiometricGate.cs`:

```csharp
using System.Threading.Tasks;
using Windows.Security.Credentials.UI;

namespace HelixBrowser.Engine
{
    // Gates access to the OS-backed credential store with Windows Hello.
    // Used before autofilling saved passwords from CredentialStore.
    public static class BiometricGate
    {
        public static async Task<bool> AuthenticateAsync(string reason)
        {
            var availability = await UserConsentVerifier.CheckAvailabilityAsync();
            if (availability != UserConsentVerifierAvailability.Available)
            {
                // No Hello / no enrolled biometric: caller decides whether to
                // fall back to account password or deny autofill.
                return false;
            }

            var result = await UserConsentVerifier.RequestVerificationAsync(reason);
            return result == UserConsentVerificationResult.Verified;
        }
    }
}
```

**Migration.** No data migration. Back-compat: when the device has no enrolled biometric or the API is unavailable (older Windows SKU, Linux entirely — there is no standard Linux biometric browser API), the gate must degrade to a non-biometric path (account password prompt or simply the existing always-unlocked behavior) rather than locking the user out.


**Testing.** Android instrumentation test with BiometricPrompt under androidx.test on the CI-verified platform. Windows/macOS: manual verification on hardware with and without enrolled biometrics, plus the unavailable-API degraded path. Assert that a denied/failed biometric never returns credential data.


#### Gap B15.3 — Native notification delivery for the web Notification API is missing on every desktop platform. Android onPermissionRequest (HelixWebChromeClient.kt:72) does not handle the notification resource and AndroidManifest has no POST_NOTIFICATIONS permission; macOS/iOS have no UNUserNotificationCenter and no NSUsageDescription; Windows has no AppNotificationManager usage despite WindowsAppSDK 1.5 being referenced; Linux has no Notify/libnotify and no org.freedesktop.Notifications usage.

`P2` · feasibility: `partially-feasible` · ~4 eng-weeks


**Why it matters.** Sites that rely on web notifications (chat, email, calendar, PWAs) silently fail or are blocked. On WebKit-based engines (macOS/iOS/Linux WebKit2GTK) the engine cannot deliver notifications unless the host app implements the permission + delivery delegate, so this is a genuine functional gap, not merely cosmetic. It degrades Helix as a daily driver for web-app-heavy users.


**Recommended architecture.** Wire the engine's notification-permission and show-notification callbacks to OS notification centers. WebKit2GTK (Linux): connect WebKitWebView 'permission-request' for WebKitNotificationPermissionRequest and 'show-notification' signals, deliver via Gio.Notification (add to browser_window.py). WKWebView (macOS/iOS): there is no public WKWebView notification delegate, so this is partially-feasible only — Helix cannot surface in-page web notifications on WebKit without engine support; the realistic scope is APP-level notifications (download complete) via UNUserNotificationCenter, not arbitrary web notifications. Windows WebView2: handle CoreWebView2.PermissionRequested for Notification and forward to Microsoft.Windows.AppNotifications.AppNotificationManager. Android: Android WebView does not surface web notifications to the host, so app-level NotificationManagerCompat only.


**Implementation plan.**
1. Linux (most feasible): connect 'permission-request' and 'show-notification' on the WebKitWebView in browser_window.py; map to Gio.Notification + application.send_notification.
1. Windows: subscribe to CoreWebView2.PermissionRequested (PermissionKind.Notifications) in MainWindow.xaml.cs, persist per-origin decision, and deliver via AppNotificationManager.Default.Show.
1. macOS/iOS/Android: scope to APP notifications only (download-complete, update-available) via UNUserNotificationCenter / NotificationManagerCompat; document that in-page web notifications are engine-constrained on WebKit/Android WebView.
1. Add POST_NOTIFICATIONS to AndroidManifest and the macOS/iOS usage strings where app notifications are used.
1. Add a per-origin notification permission store and a Settings page listing granted origins (Linux/Windows where web notifications are reachable).

**Code example** — `linux/src/browser_window.py`:

```python
# In the WebView factory where a WebKit2.WebView is created, connect the
# notification signals so page Notification API calls reach the desktop.

def _wire_notifications(self, webview):
    webview.connect('permission-request', self._on_permission_request)
    webview.connect('show-notification', self._on_show_notification)

def _on_permission_request(self, webview, request):
    from gi.repository import WebKit2
    if isinstance(request, WebKit2.NotificationPermissionRequest):
        origin = webview.get_uri() or ''
        if self.prefs.is_notifications_allowed(origin):
            request.allow()
        else:
            request.deny()
        return True
    return False

def _on_show_notification(self, webview, notification):
    from gi.repository import Gio
    notif = Gio.Notification.new(notification.get_title() or 'Helix')
    notif.set_body(notification.get_body() or '')
    # self.app is the Gtk.Application instance owning this window.
    self.app.send_notification(str(notification.get_id()), notif)
    notification.connect('clicked', lambda n: webview.grab_focus())
    return True  # we handled delivery
```

**Migration.** No stored data to migrate; introduces a new per-origin notification-permission preference. Back-compat: default deny (no notifications) preserves current behavior, so enabling the feature is purely additive and cannot regress existing users.


**Testing.** Linux: launch against a test page calling Notification.requestPermission()/new Notification(), assert a desktop notification appears via the session bus (org.freedesktop.Notifications). Windows: manual verify AppNotification toast. macOS/iOS/Android app-notification path: trigger a download-complete and assert the OS notification. Add a regression test that an un-granted origin produces no notification.


#### Gap B15.4 — Linux Wayland support is only implicit and uses legacy X11-era APIs. main.py:37 calls Gdk.Screen.get_default() (deprecated, X11-centric) and the custom client-side-decorated window does set_decorated(False) + manual drag-move, which behaves differently under Wayland (no global window positioning, different move semantics). There is no GDK_BACKEND handling, no Wayland-specific fractional-scaling handling, and the .desktop file has no Wayland hints.

`P3` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Wayland is the default session on current Ubuntu/Fedora/GNOME. A CSD window built with X11 assumptions can show blurry fractional scaling, broken window dragging, or wrong monitor metrics under Wayland — exactly the surfaces Gdk.Screen returns. This makes Helix feel broken on the most common modern Linux desktop, hurting the one fully-open-source platform's credibility.


**Recommended architecture.** Modernize the GTK3 display code in linux/src/browser_window.py and main.py: replace Gdk.Screen.get_default() monitor/geometry queries with Gdk.Display.get_default().get_monitor_at_window() / get_monitors(); guard X11-only paths behind a backend check (Gdk.Display class-name contains 'wayland'); for window move under Wayland use Gtk.Window.begin_move_drag with the real event device rather than absolute move(). Optionally ship a documented GDK_BACKEND=x11 escape hatch. (A full fix is bounded by GTK3 itself; GTK4 would be cleaner but is a larger rewrite.)


**Implementation plan.**
1. Replace all Gdk.Screen usage with Gdk.Display/Gdk.Monitor APIs for geometry and scale-factor.
1. Detect backend via Gdk.Display.get_default() class / GDK_BACKEND and branch CSD drag/maximize logic accordingly.
1. Use begin_move_drag/begin_resize_drag (event-device based) instead of manual absolute moves for Wayland correctness.
1. Honor get_scale_factor() for HiDPI/fractional scaling in any pixel-based layout.
1. Add a documented GDK_BACKEND=x11 fallback for users hitting Wayland-specific issues.

**Code example** — `linux/src/browser_window.py`:

```python
from gi.repository import Gdk, Gtk

def _current_monitor_geometry(self):
    # Wayland-safe replacement for the deprecated Gdk.Screen.get_default().
    display = Gdk.Display.get_default()
    gdk_window = self.get_window()
    monitor = (display.get_monitor_at_window(gdk_window)
               if gdk_window is not None
               else display.get_primary_monitor() or display.get_monitor(0))
    geo = monitor.get_geometry()
    return geo, monitor.get_scale_factor()

def _is_wayland(self):
    return 'wayland' in type(Gdk.Display.get_default()).__name__.lower()

def _on_titlebar_drag(self, widget, event):
    # On Wayland absolute window.move() is unsupported; use the compositor
    # move protocol via begin_move_drag instead.
    if event.button == 1:
        self.begin_move_drag(event.button, int(event.x_root), int(event.y_root), event.time)
    return False
```

**Migration.** Pure code modernization, no persisted data. Back-compat: X11 sessions keep working because the Display/Monitor APIs are valid on both backends; the only behavioral change is more correct dragging/scaling under Wayland. Ship behind no flag; provide GDK_BACKEND=x11 as a manual fallback.


**Testing.** Run under both a Wayland session and an X11 session (GDK_BACKEND=x11). Verify: window drag-move works, maximize/restore correct, monitor geometry and scale-factor correct on a HiDPI/fractional-scaled display, no blurry rendering. Since this host can py_compile but not run a GUI, coverage is manual on a real Linux desktop.


#### Gap B15.5 — macOS ships with NO entitlements file and no App Sandbox / Hardened Runtime configuration. Verified: find macos -iname '*.entitlements' = 0 results; build_dmg.sh has no codesign --entitlements step; Info.plist still carries NSAllowsArbitraryLoads=true in this copy. Without entitlements, future Keychain access groups, the network client entitlement, and notarization-required Hardened Runtime are all missing.

`P2` · feasibility: `feasible-in-webview` · ~2 eng-weeks


**Why it matters.** Distributing a notarized macOS app (required for Gatekeeper to allow it without scary warnings) needs Hardened Runtime + a signed entitlements file. The absence blocks any keychain-access-group usage and means the universal binary, while it builds, is not in a distributable/notarizable state. This is a release-readiness gap for the macOS target.


**Recommended architecture.** Add macos/HelixBrowser/HelixBrowser.entitlements declaring com.apple.security.app-sandbox (if sandboxing) or at minimum the Hardened Runtime exceptions plus keychain-access-groups for the future KeychainStore; update build_dmg.sh codesign invocation to pass --options runtime --entitlements HelixBrowser.entitlements and add a notarization (notarytool) step. Remove NSAllowsArbitraryLoads (ATS) consistent with the iOS ATS-restore already done in the audit.


**Implementation plan.**
1. Create HelixBrowser.entitlements with keychain-access-groups and com.apple.security.network.client (and app-sandbox if going MAS).
1. Update build_dmg.sh: codesign --deep --options runtime --entitlements HelixBrowser.entitlements with a Developer ID Application identity, then xcrun notarytool submit + stapler staple.
1. Remove NSAllowsArbitraryLoads from Info.plist (mirror the iOS ATS fix).
1. Verify the universal (arm64+x86_64) binary still passes spctl --assess after notarization.

**Code example** — `macos/HelixBrowser/HelixBrowser.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Hardened-Runtime + Keychain entitlements for notarized distribution. -->
<!-- Referenced from build_dmg.sh: codesign --options runtime
     --entitlements HelixBrowser.entitlements -->
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.network.client</key>
    <true/>
    <key>keychain-access-groups</key>
    <array>
        <string>$(AppIdentifierPrefix)com.helix.browser</string>
    </array>
    <!-- Allow JIT for the WebKit JS engine under Hardened Runtime. -->
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
</dict>
</plist>
```

**Migration.** No user data migration. Signing-identity change: existing ad-hoc dev builds keep running locally; release builds must be signed with a Developer ID and notarized. Removing NSAllowsArbitraryLoads could break plain-HTTP test pages — acceptable and consistent with the security posture (HTTPS-upgrade already exists).


**Testing.** After signing+notarizing the universal binary, run codesign --verify --deep --strict, spctl --assess --type execute, and confirm Gatekeeper launches it cleanly on both Apple Silicon and Intel macs. Cannot be verified on this Linux host (no Xcode/codesign) — static + on-Mac manual only.

