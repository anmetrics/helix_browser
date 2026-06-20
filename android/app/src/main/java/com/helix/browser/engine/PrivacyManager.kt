package com.helix.browser.engine

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.preference.PreferenceManager
import com.helix.browser.HelixApp
import com.helix.browser.data.HistoryRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PrivacyManager {

    private const val TAG = "PrivacyManager"

    // Background scope for off-main-thread persistence work (Room).
    // PrivacyManager is a process-lifetime singleton, so this scope lives as
    // long as the app; a SupervisorJob keeps one failed clear from cancelling
    // future ones, and the exception handler guarantees we never crash the
    // process from a fire-and-forget DB wipe.
    private val ioExceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "Background privacy task failed", e)
    }
    private val ioScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + ioExceptionHandler)

    private const val KEY_BLOCK_TRACKERS = "block_trackers"
    private const val KEY_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
    private const val KEY_DO_NOT_TRACK = "do_not_track"
    private const val KEY_HTTPS_UPGRADE = "https_upgrade"
    private const val KEY_HTTPS_ONLY_MODE = "https_only_mode"
    private const val KEY_ANTI_FINGERPRINTING = "anti_fingerprinting"
    private const val KEY_BLOCK_POPUPS = "block_popups"
    private const val KEY_RESTORE_TABS = "restore_tabs"
    private const val KEY_SUSPEND_INACTIVE_TABS = "suspend_inactive_tabs"
    private const val KEY_TRACKERS_BLOCKED_COUNT = "trackers_blocked_count"

    private val trackerDomains = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "facebook.net",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "optimizely.com",
        "scorecardresearch.com",
        "quantserve.com",
        "newrelic.com",
        "nr-data.net",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "luckyorange.com",
        "heapanalytics.com",
        "amplitude.com",
        "clarity.ms"
    )

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    // --- Preference Getters ---

    fun isBlockTrackersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_TRACKERS, true)

    fun isBlockThirdPartyCookiesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, true)

    fun isDoNotTrackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DO_NOT_TRACK, true)

    fun isHttpsUpgradeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HTTPS_UPGRADE, true)

    /**
     * Strict HTTPS-Only mode: when true the browser refuses to load any
     * http:// resource. The WebViewClient redirects to an interstitial
     * instead of upgrading silently. HTTPS-Only implies https_upgrade.
     */
    fun isHttpsOnlyModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HTTPS_ONLY_MODE, false)

    fun isAntiFingerPrintingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANTI_FINGERPRINTING, false)

    fun isBlockPopupsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_POPUPS, true)

    fun isRestoreTabsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESTORE_TABS, true)

    fun isSuspendInactiveTabsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUSPEND_INACTIVE_TABS, false)

    fun getTrackersBlockedCount(context: Context): Int =
        prefs(context).getInt(KEY_TRACKERS_BLOCKED_COUNT, 0)

    // Serialize counter updates: SharedPreferences itself is thread-safe for
    // individual put/get, but the read-modify-write here is not. Multiple
    // WebView intercept threads can fire incrementTrackersBlocked
    // concurrently and lose updates without this lock.
    private val trackerCountLock = Any()

    fun incrementTrackersBlocked(context: Context, count: Int = 1) {
        synchronized(trackerCountLock) {
            val p = prefs(context)
            val current = p.getInt(KEY_TRACKERS_BLOCKED_COUNT, 0)
            p.edit().putInt(KEY_TRACKERS_BLOCKED_COUNT, current + count).apply()
        }
    }

    fun resetTrackersBlockedCount(context: Context) {
        prefs(context).edit().putInt(KEY_TRACKERS_BLOCKED_COUNT, 0).apply()
    }

    // --- Tracker Detection ---

    fun isTracker(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            trackerDomains.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        } catch (e: Exception) {
            false
        }
    }

    // --- HTTPS Upgrade ---

    fun upgradeToHttps(url: String): String? {
        return if (url.startsWith("http://")) {
            url.replaceFirst("http://", "https://")
        } else {
            null
        }
    }

    // --- Third-party Cookie Blocking ---

    fun applyThirdPartyCookiePolicy(context: Context) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(
            null,
            !isBlockThirdPartyCookiesEnabled(context)
        )
    }

    fun applyThirdPartyCookiePolicy(context: Context, webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptThirdPartyCookies(
            webView,
            !isBlockThirdPartyCookiesEnabled(context)
        )
    }

    // --- Anti-Fingerprinting JavaScript ---

    fun getAntiFingerPrintingScript(): String {
        return """
        (function() {
            // Block Canvas fingerprinting
            const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL = function(type) {
                if (this.width > 16 && this.height > 16) {
                    const ctx = this.getContext('2d');
                    if (ctx) {
                        const imageData = ctx.getImageData(0, 0, this.width, this.height);
                        for (let i = 0; i < imageData.data.length; i += 4) {
                            imageData.data[i] = imageData.data[i] ^ 1;
                        }
                        ctx.putImageData(imageData, 0, 0);
                    }
                }
                return originalToDataURL.apply(this, arguments);
            };

            const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
            CanvasRenderingContext2D.prototype.getImageData = function() {
                const imageData = originalGetImageData.apply(this, arguments);
                if (imageData.width > 16 && imageData.height > 16) {
                    for (let i = 0; i < imageData.data.length; i += 4) {
                        imageData.data[i] = imageData.data[i] ^ 1;
                    }
                }
                return imageData;
            };

            // Block WebGL fingerprinting
            const getParameterProto = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(param) {
                if (param === 37445) return 'Generic GPU';
                if (param === 37446) return 'Generic Renderer';
                return getParameterProto.apply(this, arguments);
            };

            try {
                const getParameterProto2 = WebGL2RenderingContext.prototype.getParameter;
                WebGL2RenderingContext.prototype.getParameter = function(param) {
                    if (param === 37445) return 'Generic GPU';
                    if (param === 37446) return 'Generic Renderer';
                    return getParameterProto2.apply(this, arguments);
                };
            } catch(e) {}

            // Spoof hardware fingerprint surfaces. Real values leak unique
            // signal across browsers; rounding to common buckets makes the
            // device blend with the population.
            try {
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: function(){ return 4; } });
            } catch(e) {}
            try {
                Object.defineProperty(navigator, 'deviceMemory', { get: function(){ return 4; } });
            } catch(e) {}
            // Round screen metrics to nearest 50px to defeat resolution fingerprinting
            // while preserving usable viewport size.
            try {
                var rd = function(v){ return Math.round(v / 50) * 50; };
                Object.defineProperty(screen, 'width',  { get: function(){ return rd(screen.availWidth); } });
                Object.defineProperty(screen, 'height', { get: function(){ return rd(screen.availHeight); } });
                Object.defineProperty(screen, 'colorDepth', { get: function(){ return 24; } });
                Object.defineProperty(screen, 'pixelDepth', { get: function(){ return 24; } });
            } catch(e) {}
            // Battery API is widely abused for cross-site fingerprinting; return
            // a stable, full-battery promise so callers don't observe drift.
            try {
                if (navigator.getBattery) {
                    navigator.getBattery = function(){
                        return Promise.resolve({
                            charging: true, chargingTime: 0, dischargingTime: Infinity, level: 1.0,
                            addEventListener: function(){}, removeEventListener: function(){}
                        });
                    };
                }
            } catch(e) {}
            // WebRTC leaks local IPs through ICE candidates even on VPN. Strip
            // candidate strings before they reach the page.
            try {
                if (window.RTCPeerConnection) {
                    var OrigRTC = window.RTCPeerConnection;
                    window.RTCPeerConnection = function(){
                        var pc = new OrigRTC(arguments[0], arguments[1]);
                        var origAdd = pc.addIceCandidate;
                        pc.addIceCandidate = function(c){
                            if (c && c.candidate && /(\d{1,3}\.){3}\d{1,3}/.test(c.candidate)) {
                                return Promise.resolve();
                            }
                            return origAdd.apply(pc, arguments);
                        };
                        return pc;
                    };
                    window.RTCPeerConnection.prototype = OrigRTC.prototype;
                }
            } catch(e) {}

            // Block AudioContext fingerprinting
            if (window.AudioContext || window.webkitAudioContext) {
                const OriginalAudioContext = window.AudioContext || window.webkitAudioContext;
                const originalCreateAnalyser = OriginalAudioContext.prototype.createAnalyser;
                OriginalAudioContext.prototype.createAnalyser = function() {
                    const analyser = originalCreateAnalyser.apply(this, arguments);
                    const originalGetFloatFrequencyData = analyser.getFloatFrequencyData;
                    analyser.getFloatFrequencyData = function(array) {
                        originalGetFloatFrequencyData.apply(this, arguments);
                        for (let i = 0; i < array.length; i++) {
                            array[i] = array[i] + (Math.random() * 0.1 - 0.05);
                        }
                    };
                    return analyser;
                };
            }
        })();
        """.trimIndent()
    }

    // --- Do Not Track Header Script ---

    fun getDoNotTrackScript(): String {
        return """
        (function() {
            Object.defineProperty(navigator, 'doNotTrack', {
                get: function() { return '1'; },
                configurable: false,
                enumerable: true
            });
        })();
        """.trimIndent()
    }

    // --- Tracker Blocking JavaScript ---

    fun getTrackerBlockingScript(): String {
        val domainsJs = trackerDomains.joinToString(",") { "\"$it\"" }
        return """
        (function() {
            const trackerDomains = [$domainsJs];

            function isTrackerUrl(url) {
                try {
                    const hostname = new URL(url).hostname.toLowerCase();
                    return trackerDomains.some(function(domain) {
                        return hostname === domain || hostname.endsWith('.' + domain);
                    });
                } catch(e) { return false; }
            }

            // Block fetch to tracker domains
            const originalFetch = window.fetch;
            window.fetch = function(input, init) {
                const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                if (url && isTrackerUrl(url)) {
                    return Promise.reject(new TypeError('Blocked by Helix Privacy'));
                }
                return originalFetch.apply(this, arguments);
            };

            // Block XMLHttpRequest to tracker domains
            const originalXHROpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                if (url && isTrackerUrl(url)) {
                    this._helixBlocked = true;
                }
                return originalXHROpen.apply(this, arguments);
            };
            const originalXHRSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function() {
                if (this._helixBlocked) {
                    this.dispatchEvent(new Event('error'));
                    return;
                }
                return originalXHRSend.apply(this, arguments);
            };

            // Block sendBeacon to tracker domains
            const originalSendBeacon = navigator.sendBeacon;
            if (originalSendBeacon) {
                navigator.sendBeacon = function(url, data) {
                    if (url && isTrackerUrl(url)) {
                        return false;
                    }
                    return originalSendBeacon.apply(this, arguments);
                };
            }
        })();
        """.trimIndent()
    }

    // --- YouTube Ad Blocking JavaScript ---

    fun getYoutubeAdBlockScript(): String {
        return """
        (function() {
            if (window._helixYtAdBlock) return;
            window._helixYtAdBlock = true;
            if (!location.hostname.includes('youtube.com')) return;

            // === CSS: Hide all ad elements ===
            var style = document.createElement('style');
            style.textContent = '' +
                // Video player ads
                '.ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text-overlay,' +
                '.ytp-ad-player-overlay, .ytp-ad-player-overlay-instream-info,' +
                '.ytp-ad-survey, .ytp-ad-image-overlay, .ytp-ad-action-interstitial,' +
                '.ytp-ad-skip-ad-slot, .video-ads, .ytp-ad-progress-list,' +
                '.ytp-ad-persistent-progress-bar-container,' +
                // Page-level ads
                '#player-ads, #masthead-ad, #panels ads,' +
                'ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer,' +
                'ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,' +
                'ytd-banner-promo-renderer, ytd-promoted-video-renderer,' +
                'ytd-compact-promoted-video-renderer, ytd-video-masthead-ad-v3-renderer,' +
                'ytd-primetime-promo-renderer, .ytd-mealbar-promo-renderer,' +
                'ytd-statement-banner-renderer, .ytp-suggested-action,' +
                'ytd-merch-shelf-renderer, ytd-action-companion-ad-renderer,' +
                'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],' +
                '#related ytd-promoted-sparkles-web-renderer,' +
                'ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer,' +
                // Mobile YouTube ads
                '.ytm-ad-break-banner, .ytm-rich-item-ad-renderer,' +
                'ytm-promoted-video-renderer, .ad-container,' +
                '.ytm-action-companion-ad-renderer' +
                '{ display: none !important; height: 0 !important; max-height: 0 !important; ' +
                '  overflow: hidden !important; visibility: hidden !important; pointer-events: none !important; }' +
                // Make skip button always visible and clickable
                '.ytp-ad-skip-button-slot, .ytp-ad-skip-button-container ' +
                '{ opacity: 1 !important; visibility: visible !important; pointer-events: auto !important; }';
            (document.head || document.documentElement).appendChild(style);

            // === Auto-skip ads as fast as possible ===
            function skipAds() {
                // Click ALL skip button variants
                var skipSelectors = [
                    '.ytp-ad-skip-button',
                    '.ytp-ad-skip-button-modern',
                    '.ytp-skip-ad-button',
                    'button.ytp-ad-skip-button-modern',
                    '.ytp-ad-skip-button-container button',
                    '.ytp-ad-overlay-close-button',
                    '.ytp-ad-overlay-close-container',
                    'button[id^="skip-button"]',
                    '.videoAdUiSkipButton',
                    '.ytp-ad-skip-button-slot button',
                    // Mobile skip buttons
                    '.skip-button',
                    '.ytm-skip-ad-button'
                ];
                skipSelectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(btn) {
                        try { btn.click(); btn.dispatchEvent(new MouseEvent('click', {bubbles: true})); } catch(e) {}
                    });
                });

                // Force-skip video ad by jumping to end
                var video = document.querySelector('video.html5-main-video, .html5-video-player video, video');
                if (video) {
                    var player = document.querySelector('.html5-video-player');
                    var adShowing = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
                    if (!adShowing) {
                        adShowing = !!document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');
                    }
                    if (adShowing) {
                        video.muted = true;
                        video.playbackRate = 16;
                        if (video.duration && isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration - 0.1;
                        }
                    }
                }

                // Remove overlay ads
                document.querySelectorAll(
                    '.ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-action-interstitial'
                ).forEach(function(el) { el.remove(); });
            }

            // Run immediately and very frequently
            skipAds();
            var skipInterval = setInterval(skipAds, 300);

            // Watch for DOM changes to catch ads the moment they appear
            var adObserver = new MutationObserver(function(mutations) {
                skipAds();
            });
            adObserver.observe(document.documentElement, {
                childList: true, subtree: true,
                attributes: true, attributeFilter: ['class', 'src', 'style']
            });

            // === Block ad network requests via fetch/XHR ===
            var adUrlPatterns = [
                '/api/stats/ads', '/pagead/', '/get_midroll_info', '/ptracking',
                '/api/stats/atr', 'googleads.g.doubleclick.net', 'imasdk.googleapis.com',
                'securepubads.g.doubleclick.net', '/youtubei/v1/log_event',
                'play.google.com/log', 'doubleclick.net', 'googlesyndication.com',
                '/ad_break', 'google_companion_ad', '/get_video_info',
                'googleadservices.com', 's0.2mdn.net', 'ad.youtube.com',
                '/generate_204', '/error_204'
            ];

            if (!window._helixFetchPatched) {
                window._helixFetchPatched = true;
                var origFetch = window.fetch;
                window.fetch = function(input, init) {
                    var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                    if (url && adUrlPatterns.some(function(p) { return url.includes(p); })) {
                        return Promise.resolve(new Response('', {status: 200}));
                    }
                    return origFetch.apply(this, arguments);
                };
                var origXhrOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    if (typeof url === 'string' && adUrlPatterns.some(function(p) { return url.includes(p); })) {
                        this._helixBlocked = true;
                    }
                    return origXhrOpen.apply(this, arguments);
                };
                var origXhrSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.send = function() {
                    if (this._helixBlocked) { this._helixBlocked = false; return; }
                    return origXhrSend.apply(this, arguments);
                };
            }

            // === Intercept YouTube player config to remove ads ===
            try {
                var origParse = JSON.parse;
                JSON.parse = function(text) {
                    var result = origParse.apply(this, arguments);
                    if (result && typeof result === 'object') {
                        // Remove ad-related player response data
                        if (result.adPlacements) result.adPlacements = [];
                        if (result.playerAds) result.playerAds = [];
                        if (result.adSlots) result.adSlots = [];
                        if (result.adBreakParams) delete result.adBreakParams;
                        if (result.playerResponse) {
                            try {
                                var pr = typeof result.playerResponse === 'string' ? origParse(result.playerResponse) : result.playerResponse;
                                if (pr.adPlacements) pr.adPlacements = [];
                                if (pr.playerAds) pr.playerAds = [];
                                if (pr.adSlots) pr.adSlots = [];
                                if (typeof result.playerResponse === 'string') result.playerResponse = JSON.stringify(pr);
                                else result.playerResponse = pr;
                            } catch(e) {}
                        }
                    }
                    return result;
                };
            } catch(e) {}

            // === Handle YouTube SPA navigation ===
            var lastUrl = location.href;
            setInterval(function() {
                if (location.href !== lastUrl) {
                    lastUrl = location.href;
                    skipAds();
                }
            }, 1000);
        })();
        """.trimIndent()
    }

    // --- Cosmetic Ad Hiding (generic sites) ---

    fun getCosmeticAdBlockScript(): String {
        return """
        (function() {
            var style = document.createElement('style');
            style.textContent = '' +
                '[id*="google_ads"], [class*="google-ad"],' +
                '[id*="ad-container"], [class*="ad-container"],' +
                '[id*="adBanner"], [class*="adBanner"], [class*="ad-banner"],' +
                'iframe[src*="doubleclick"], iframe[src*="googlesyndication"],' +
                'ins.adsbygoogle, div[data-ad], div[data-ad-slot],' +
                '.ad-wrapper, .ad-unit, .ad-slot, .ad-block, .ad-placement,' +
                '.sponsored-content, .sponsored-ad, .native-ad,' +
                'amp-ad, amp-sticky-ad, amp-auto-ads' +
                '{ display: none !important; }';
            (document.head || document.documentElement).appendChild(style);
        })();
        """.trimIndent()
    }

    // --- Popup & Redirect Blocker ---

    fun getPopupBlockerScript(): String {
        return """
        (function() {
            var adDomains = ['popads.net','popcash.net','propellerads.com','juicyads.com',
                'exoclick.com','trafficjunky.net','revcontent.com','mgid.com',
                'adsterra.com','hilltopads.net','clickadu.com','ad-maven.com',
                'googlesyndication.com','doubleclick.net','adnxs.com','taboola.com','outbrain.com'];
            function isAdUrl(url) {
                if (!url) return false;
                try { var u = new URL(url, location.href); return adDomains.some(function(d) { return u.hostname.includes(d); }); }
                catch(e) { return false; }
            }
            var origOpen = window.open;
            window.open = function(url) {
                if (isAdUrl(url)) return null;
                if (!navigator.userActivation || !navigator.userActivation.isActive) {
                    if (url && url !== 'about:blank') return null;
                }
                return origOpen.apply(this, arguments);
            };
            document.addEventListener('click', function(e) {
                var target = e.target.closest ? e.target.closest('a') : null;
                if (target && target.href && isAdUrl(target.href)) { e.preventDefault(); e.stopPropagation(); }
            }, true);
        })();
        """.trimIndent()
    }

    // --- Combined Privacy Injection Script ---

    fun getPrivacyScripts(context: Context): String {
        val scripts = StringBuilder()
        if (isAntiFingerPrintingEnabled(context)) {
            scripts.append(getAntiFingerPrintingScript())
            scripts.append("\n")
        }
        if (isDoNotTrackEnabled(context)) {
            scripts.append(getDoNotTrackScript())
            scripts.append("\n")
        }
        if (isBlockTrackersEnabled(context)) {
            scripts.append(getTrackerBlockingScript())
            scripts.append("\n")
        }
        // YouTube ad blocking + cosmetic filtering. Reads the same "block_ads"
        // key the Settings switch writes (the prior "ad_block" key was a typo
        // that silently disabled cosmetic filtering for everyone).
        val adBlockEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("block_ads", true)
        if (adBlockEnabled) {
            scripts.append(getYoutubeAdBlockScript())
            scripts.append("\n")
            scripts.append(getCosmeticAdBlockScript())
            scripts.append("\n")
        }
        if (isBlockPopupsEnabled(context)) {
            scripts.append(getPopupBlockerScript())
            scripts.append("\n")
        }
        return scripts.toString()
    }

    // --- Clear All Browsing Data ---

    // Wipes every "browsing data" surface the user expects "Clear all browsing
    // data" to erase: cookies, WebView HTTP cache, DOM/Web storage, autofill
    // form data, the WebView's own back/forward history, remembered SSL error
    // decisions, the tracker counter AND the persistent Room visit history.
    //
    // Bookmarks are intentionally NOT touched: like Chrome/flagship browsers,
    // bookmarks are user-saved content, not "browsing data".
    //
    // Threading: WebView / CookieManager / WebStorage APIs MUST run on the
    // thread that owns the WebView (the main thread); constructing a WebView off
    // the main thread throws. So the WebView work stays on the caller's (main)
    // thread, and only the Room history delete is dispatched to a background IO
    // dispatcher so no disk I/O happens on the main thread.
    //
    // Resolves the HistoryRepository from the application singleton. Prefer the
    // explicit (Context, HistoryRepository) overload from tests.
    fun clearAllBrowsingData(context: Context) {
        clearAllBrowsingData(context, runCatching { HelixApp.instance.historyRepository }.getOrNull())
    }

    fun clearAllBrowsingData(context: Context, historyRepository: HistoryRepository?) {
        // Clear cookies. Each external WebView call is isolated so one failing
        // surface does not abort the rest of the wipe (fail-soft, never leave
        // data behind silently because an earlier step threw).
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cookies", e)
        }

        // Clear WebView cache
        try {
            WebView(context).clearCache(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear WebView cache", e)
        }

        // Clear WebStorage (localStorage, sessionStorage, IndexedDB)
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear web storage", e)
        }

        // Clear form data and history from WebView database
        try {
            val webView = WebView(context)
            webView.clearFormData()
            webView.clearHistory()
            webView.clearSslPreferences()
            webView.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear WebView form/history/SSL data", e)
        }

        // Reset tracker count
        try {
            resetTrackersBlockedCount(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset tracker count", e)
        }

        // Clear the persistent Room visit history off the main thread. Without
        // this the user's full URL/title log survived "Clear all browsing data",
        // contradicting the UI's promise. clearAll() is a suspend DB call, so it
        // runs on the IO dispatcher; failures are logged by ioExceptionHandler
        // rather than crashing the caller.
        val repo = historyRepository
        if (repo == null) {
            Log.w(TAG, "HistoryRepository unavailable; browsing history not cleared")
            return
        }
        ioScope.launch {
            repo.clearAll()
        }
    }
}
