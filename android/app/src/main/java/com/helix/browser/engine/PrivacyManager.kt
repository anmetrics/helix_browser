package com.helix.browser.engine

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.preference.PreferenceManager

object PrivacyManager {

    private const val KEY_BLOCK_TRACKERS = "block_trackers"
    private const val KEY_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
    private const val KEY_DO_NOT_TRACK = "do_not_track"
    private const val KEY_HTTPS_UPGRADE = "https_upgrade"
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

    fun isAntiFingerPrintingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANTI_FINGERPRINTING, false)

    fun isBlockPopupsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_POPUPS, true)

    fun isRestoreTabsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESTORE_TABS, false)

    fun isSuspendInactiveTabsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUSPEND_INACTIVE_TABS, false)

    fun getTrackersBlockedCount(context: Context): Int =
        prefs(context).getInt(KEY_TRACKERS_BLOCKED_COUNT, 0)

    fun incrementTrackersBlocked(context: Context, count: Int = 1) {
        val current = getTrackersBlockedCount(context)
        prefs(context).edit().putInt(KEY_TRACKERS_BLOCKED_COUNT, current + count).apply()
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
            if (!location.hostname.includes('youtube.com')) return;
            var style = document.createElement('style');
            style.textContent = '' +
                '.ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text-overlay,' +
                '.ytp-ad-player-overlay, .ytp-ad-player-overlay-instream-info,' +
                '.ytp-ad-survey, .ytp-ad-image-overlay,' +
                '#player-ads, #masthead-ad,' +
                'ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer,' +
                'ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,' +
                'ytd-banner-promo-renderer, ytd-promoted-video-renderer,' +
                'ytd-compact-promoted-video-renderer, ytd-video-masthead-ad-v3-renderer,' +
                'ytd-primetime-promo-renderer, .ytd-mealbar-promo-renderer,' +
                'ytd-statement-banner-renderer, .ytp-suggested-action,' +
                'ytd-merch-shelf-renderer' +
                '{ display: none !important; height: 0 !important; overflow: hidden !important; }' +
                '.ytp-ad-skip-button-slot { opacity: 1 !important; }';
            (document.head || document.documentElement).appendChild(style);

            function skipAds() {
                var skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                    'button.ytp-ad-skip-button-modern, .ytp-ad-skip-button-container button'
                );
                skipBtns.forEach(function(btn) { try { btn.click(); } catch(e) {} });
                var video = document.querySelector('video.html5-main-video, .html5-video-player video');
                if (video) {
                    var adPlaying = document.querySelector('.ad-showing, .ad-interrupting');
                    if (adPlaying) {
                        video.playbackRate = 16;
                        video.muted = true;
                        if (video.duration && isFinite(video.duration)) video.currentTime = video.duration;
                    }
                }
                document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay').forEach(function(el) { el.remove(); });
            }

            var adObserver = new MutationObserver(skipAds);
            adObserver.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
            setInterval(skipAds, 500);

            var adUrlPatterns = ['/api/stats/ads', '/pagead/', '/get_midroll_info', '/ptracking',
                '/api/stats/atr', 'googleads.g.doubleclick.net', 'imasdk.googleapis.com',
                'securepubads.g.doubleclick.net', '/youtubei/v1/log_event', 'play.google.com/log'];
            var origFetch = window.fetch;
            window.fetch = function(input) {
                var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                if (adUrlPatterns.some(function(p) { return url.includes(p); })) return Promise.resolve(new Response('', {status: 200}));
                return origFetch.apply(this, arguments);
            };
            var origXhrOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                if (typeof url === 'string' && adUrlPatterns.some(function(p) { return url.includes(p); })) { this._blocked = true; }
                return origXhrOpen.apply(this, arguments);
            };
            var origXhrSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function() {
                if (this._blocked) { this._blocked = false; return; }
                return origXhrSend.apply(this, arguments);
            };
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
        // YouTube ad blocking + cosmetic filtering (always included when ad block is on)
        val adBlockEnabled = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("ad_block", true)
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

    fun clearAllBrowsingData(context: Context) {
        // Clear cookies
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        // Clear WebView cache
        WebView(context).clearCache(true)

        // Clear WebStorage (localStorage, sessionStorage, IndexedDB)
        WebStorage.getInstance().deleteAllData()

        // Clear form data and history from WebView database
        val webView = WebView(context)
        webView.clearFormData()
        webView.clearHistory()
        webView.clearSslPreferences()
        webView.destroy()

        // Reset tracker count
        resetTrackersBlockedCount(context)
    }
}
