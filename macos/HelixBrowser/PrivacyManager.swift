import Foundation
import WebKit

class PrivacyManager {
    static let shared = PrivacyManager()

    private init() {}

    // MARK: - Anti-Fingerprinting JavaScript
    var antiFingerprintingScript: WKUserScript {
        let js = """
        (function() {
            // Block canvas fingerprinting
            const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL = function(type) {
                if (this.width <= 16 && this.height <= 16) return origToDataURL.apply(this, arguments);
                const ctx = this.getContext('2d');
                if (ctx) {
                    const imageData = ctx.getImageData(0, 0, this.width, this.height);
                    for (let i = 0; i < imageData.data.length; i += 4) {
                        imageData.data[i] = imageData.data[i] ^ (Math.random() * 2 | 0);
                    }
                    ctx.putImageData(imageData, 0, 0);
                }
                return origToDataURL.apply(this, arguments);
            };

            // Block WebGL fingerprinting
            const getParameterOrig = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(param) {
                if (param === 37445) return 'Helix Graphics';
                if (param === 37446) return 'Helix Renderer';
                return getParameterOrig.apply(this, arguments);
            };

            // Block AudioContext fingerprinting
            const origGetFloatFrequencyData = AnalyserNode.prototype.getFloatFrequencyData;
            AnalyserNode.prototype.getFloatFrequencyData = function(array) {
                origGetFloatFrequencyData.apply(this, arguments);
                for (let i = 0; i < array.length; i++) {
                    array[i] += (Math.random() - 0.5) * 0.01;
                }
            };

            // Reduce navigator information leakage
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 4 });
            Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });

            // Block battery API
            if (navigator.getBattery) {
                navigator.getBattery = undefined;
            }
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    // MARK: - Do Not Track
    var doNotTrackScript: WKUserScript {
        let js = """
        Object.defineProperty(navigator, 'doNotTrack', { get: () => '1' });
        Object.defineProperty(window, 'doNotTrack', { get: () => '1' });
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    // MARK: - HTTPS Upgrade
    var httpsUpgradeScript: WKUserScript {
        let js = """
        (function() {
            const observer = new MutationObserver(function(mutations) {
                document.querySelectorAll('a[href^="http://"]').forEach(function(a) {
                    a.href = a.href.replace('http://', 'https://');
                });
            });
            observer.observe(document.documentElement, { childList: true, subtree: true });
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
    }

    // MARK: - Tracker Blocking Script
    var trackerBlockingScript: WKUserScript {
        let trackerDomains = [
            "google-analytics.com", "googletagmanager.com", "facebook.net/en_US/fbevents",
            "connect.facebook.net", "hotjar.com", "mixpanel.com", "segment.io",
            "optimizely.com", "scorecardresearch.com", "quantserve.com",
            "newrelic.com", "nr-data.net", "fullstory.com", "mouseflow.com",
            "crazyegg.com", "luckyorange.com", "inspectlet.com", "heapanalytics.com",
            "amplitude.com", "rudderstack.com", "mxpnl.com", "clarity.ms"
        ]
        let domainsJs = trackerDomains.map { "\"\($0)\"" }.joined(separator: ",")
        let js = """
        (function() {
            const blockedDomains = [\(domainsJs)];
            const origFetch = window.fetch;
            window.fetch = function(input, init) {
                const url = typeof input === 'string' ? input : input.url;
                for (const domain of blockedDomains) {
                    if (url && url.includes(domain)) {
                        return Promise.reject(new TypeError('Blocked by Helix Privacy'));
                    }
                }
                return origFetch.apply(this, arguments);
            };

            const origXHR = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                for (const domain of blockedDomains) {
                    if (url && url.includes(domain)) {
                        return;
                    }
                }
                return origXHR.apply(this, arguments);
            };

            const origSendBeacon = navigator.sendBeacon;
            if (origSendBeacon) {
                navigator.sendBeacon = function(url, data) {
                    for (const domain of blockedDomains) {
                        if (url && url.includes(domain)) return true;
                    }
                    return origSendBeacon.apply(this, arguments);
                };
            }
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    // MARK: - YouTube Ad Blocking Script
    var youtubeAdBlockScript: WKUserScript {
        let js = """
        (function() {
            if (!location.hostname.includes('youtube.com')) return;

            // CSS to hide all YouTube ad containers
            const style = document.createElement('style');
            style.textContent = `
                .ytp-ad-module,
                .ytp-ad-overlay-container,
                .ytp-ad-text-overlay,
                .ytp-ad-overlay-close-button,
                .ytp-ad-overlay-ad-info-button-container,
                .ytp-ad-player-overlay,
                .ytp-ad-player-overlay-instream-info,
                .ytp-ad-survey,
                .ytp-ad-image-overlay,
                #player-ads,
                #masthead-ad,
                #panels ytd-ads-engagement-panel-content-renderer,
                ytd-promoted-sparkles-web-renderer,
                ytd-display-ad-renderer,
                ytd-ad-slot-renderer,
                ytd-in-feed-ad-layout-renderer,
                ytd-banner-promo-renderer,
                ytd-promoted-video-renderer,
                ytd-compact-promoted-video-renderer,
                ytd-video-masthead-ad-v3-renderer,
                ytd-primetime-promo-renderer,
                tp-yt-paper-dialog:has(yt-mealbar-promo-renderer),
                .ytd-mealbar-promo-renderer,
                ytd-statement-banner-renderer,
                ytd-popup-container:has(.ytd-enforcement-message-view-model),
                .ytp-suggested-action,
                .ytp-endscreen-content,
                .ytd-merch-shelf-renderer,
                ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],
                div#panels.ytd-watch-flexy > ytd-engagement-panel-section-list-renderer:has(ytd-ads-engagement-panel-content-renderer)
                { display: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; overflow: hidden !important; }

                .ytp-ad-skip-button-slot { opacity: 1 !important; }
            `;
            (document.head || document.documentElement).appendChild(style);

            // Auto-skip ads & speed up video ads
            function skipAds() {
                // Click skip button
                const skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                    'button.ytp-ad-skip-button-modern, .ytp-ad-skip-button-container button'
                );
                skipBtns.forEach(btn => { try { btn.click(); } catch(e) {} });

                // Speed up video ad to end it faster
                const video = document.querySelector('.html5-video-player video, video.html5-main-video');
                if (video) {
                    const adPlaying = document.querySelector('.ad-showing, .ad-interrupting');
                    if (adPlaying) {
                        video.playbackRate = 16;
                        video.muted = true;
                        // Try to seek to end
                        if (video.duration && isFinite(video.duration)) {
                            video.currentTime = video.duration;
                        }
                    }
                }

                // Remove overlay ads
                document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay').forEach(el => el.remove());
            }

            // Run repeatedly to catch dynamically loaded ads
            const adObserver = new MutationObserver(skipAds);
            adObserver.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
            setInterval(skipAds, 500);

            // Block YouTube ad requests via fetch/XHR interception
            const adUrlPatterns = [
                '/api/stats/ads', '/pagead/', '/get_midroll_info', '/ptracking',
                '/api/stats/atr', 'googleads.g.doubleclick.net', 'imasdk.googleapis.com',
                'securepubads.g.doubleclick.net', '/youtubei/v1/log_event',
                '/generate_204?', 'play.google.com/log'
            ];
            const origFetch = window.fetch;
            window.fetch = function(input, init) {
                const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                if (adUrlPatterns.some(p => url.includes(p))) {
                    return Promise.resolve(new Response('', {status: 200}));
                }
                return origFetch.apply(this, arguments);
            };
            const origXhrOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                if (typeof url === 'string' && adUrlPatterns.some(p => url.includes(p))) {
                    this._blocked = true;
                }
                return origXhrOpen.apply(this, arguments);
            };
            const origXhrSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function() {
                if (this._blocked) { this._blocked = false; return; }
                return origXhrSend.apply(this, arguments);
            };
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
    }

    // MARK: - Cosmetic Ad Hiding (generic sites)
    var cosmeticAdBlockScript: WKUserScript {
        let js = """
        (function() {
            const style = document.createElement('style');
            style.textContent = `
                [id*="google_ads"], [id*="GoogleAds"], [class*="google-ad"],
                [id*="ad-container"], [id*="ad_container"], [class*="ad-container"], [class*="ad_container"],
                [id*="adBanner"], [class*="adBanner"], [class*="ad-banner"], [id*="ad-banner"],
                iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
                iframe[src*="advertising"], iframe[src*="adserver"],
                ins.adsbygoogle, div[data-ad], div[data-ad-slot],
                .ad-wrapper, .ad-unit, .ad-slot, .ad-block, .ad-placement,
                .sponsored-content, .sponsored-ad, .native-ad,
                div[aria-label="advertisement"], div[role="complementary"][aria-label*="ad" i],
                amp-ad, amp-sticky-ad, amp-auto-ads
                { display: none !important; }
            `;
            (document.head || document.documentElement).appendChild(style);
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
    }

    // MARK: - Popup & Redirect Blocker
    var popupBlockerScript: WKUserScript {
        let js = """
        (function() {
            const adDomains = [
                'popads.net', 'popcash.net', 'propellerads.com', 'juicyads.com',
                'exoclick.com', 'trafficjunky.net', 'revcontent.com', 'mgid.com',
                'adsterra.com', 'hilltopads.net', 'clickadu.com', 'ad-maven.com',
                'richpush.co', 'trafficstars.com', 'pushcrew.com', 'onesignal.com',
                'googlesyndication.com', 'doubleclick.net', 'adnxs.com',
                'taboola.com', 'outbrain.com', 'criteo.com'
            ];
            function isAdUrl(url) {
                if (!url) return false;
                try {
                    const u = new URL(url, location.href);
                    return adDomains.some(d => u.hostname.includes(d));
                } catch(e) { return false; }
            }

            // Block window.open to ad domains
            const origOpen = window.open;
            window.open = function(url) {
                if (isAdUrl(url)) return null;
                // Block suspicious window.open without user gesture
                if (!navigator.userActivation || !navigator.userActivation.isActive) {
                    if (url && url !== 'about:blank') return null;
                }
                return origOpen.apply(this, arguments);
            };

            // Block click-hijacking: prevent event listeners that redirect to ad domains
            document.addEventListener('click', function(e) {
                const target = e.target.closest('a');
                if (target && target.href && isAdUrl(target.href)) {
                    e.preventDefault();
                    e.stopPropagation();
                    return false;
                }
            }, true);

            // Block meta refresh & JS redirects to ad domains
            const origAssign = location.assign;
            const origReplace = location.replace;
            Object.defineProperty(location, 'assign', {
                value: function(url) { if (!isAdUrl(url)) origAssign.call(this, url); }
            });
            Object.defineProperty(location, 'replace', {
                value: function(url) { if (!isAdUrl(url)) origReplace.call(this, url); }
            });
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    // MARK: - Apply Privacy Settings
    func applyPrivacySettings(to config: WKWebViewConfiguration) {
        let prefs = Prefs.shared

        if prefs.isBlockFingerprintingEnabled {
            config.userContentController.addUserScript(antiFingerprintingScript)
        }

        if prefs.isDoNotTrackEnabled {
            config.userContentController.addUserScript(doNotTrackScript)
        }

        if prefs.isHttpsUpgradeEnabled {
            config.userContentController.addUserScript(httpsUpgradeScript)
        }

        if prefs.isBlockTrackersEnabled {
            config.userContentController.addUserScript(trackerBlockingScript)
        }

        // YouTube ad blocking + cosmetic filtering + popup blocking (always on when ad block is on)
        if prefs.isAdBlockEnabled {
            config.userContentController.addUserScript(youtubeAdBlockScript)
            config.userContentController.addUserScript(cosmeticAdBlockScript)
        }

        if prefs.isBlockPopupsEnabled {
            config.userContentController.addUserScript(popupBlockerScript)
        }

        if prefs.isBlockThirdPartyCookies {
            if #available(macOS 12.0, *) {
                config.websiteDataStore.httpCookieStore.setCookiePolicy(.allow)
            }
        }
    }

    // MARK: - Clear Data
    func clearAllData(completion: @escaping () -> Void) {
        let dataStore = WKWebsiteDataStore.default()
        let types = WKWebsiteDataStore.allWebsiteDataTypes()
        let since = Date(timeIntervalSince1970: 0)
        dataStore.removeData(ofTypes: types, modifiedSince: since) {
            completion()
        }
    }

    func clearCookies(completion: @escaping () -> Void) {
        let dataStore = WKWebsiteDataStore.default()
        dataStore.fetchDataRecords(ofTypes: [WKWebsiteDataRecord.DisplayNameKey]) { records in
            dataStore.removeData(ofTypes: [WKWebsiteDataRecord.DisplayNameKey], for: records) {
                completion()
            }
        }
    }

    func clearCache(completion: @escaping () -> Void) {
        let dataStore = WKWebsiteDataStore.default()
        let types: Set<String> = [
            WKWebsiteDataTypeDiskCache,
            WKWebsiteDataTypeMemoryCache,
            WKWebsiteDataTypeOfflineWebApplicationCache
        ]
        dataStore.removeData(ofTypes: types, modifiedSince: Date(timeIntervalSince1970: 0)) {
            completion()
        }
    }
}
