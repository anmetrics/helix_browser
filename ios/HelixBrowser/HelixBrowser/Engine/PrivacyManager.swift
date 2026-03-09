import Foundation
import WebKit

class PrivacyManager {
    static let shared = PrivacyManager()
    private init() {}

    var antiFingerprintingScript: WKUserScript {
        let js = """
        (function() {
            const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
            HTMLCanvasElement.prototype.toDataURL = function(type) {
                if (this.width <= 16 && this.height <= 16) return origToDataURL.apply(this, arguments);
                const ctx = this.getContext('2d');
                if (ctx) {
                    const d = ctx.getImageData(0, 0, this.width, this.height);
                    for (let i = 0; i < d.data.length; i += 4) d.data[i] ^= (Math.random() * 2 | 0);
                    ctx.putImageData(d, 0, 0);
                }
                return origToDataURL.apply(this, arguments);
            };
            const origGetParam = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(p) {
                if (p === 37445) return 'Helix Graphics';
                if (p === 37446) return 'Helix Renderer';
                return origGetParam.apply(this, arguments);
            };
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 4 });
            Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
            if (navigator.getBattery) navigator.getBattery = undefined;
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    var doNotTrackScript: WKUserScript {
        let js = """
        Object.defineProperty(navigator, 'doNotTrack', { get: () => '1' });
        Object.defineProperty(window, 'doNotTrack', { get: () => '1' });
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    var trackerBlockingScript: WKUserScript {
        let js = """
        (function() {
            const blocked = ["google-analytics.com","googletagmanager.com","connect.facebook.net",
                "hotjar.com","mixpanel.com","segment.io","optimizely.com","scorecardresearch.com",
                "quantserve.com","newrelic.com","nr-data.net","fullstory.com","mouseflow.com",
                "crazyegg.com","clarity.ms","heapanalytics.com","amplitude.com"];
            const origFetch = window.fetch;
            window.fetch = function(input) {
                const url = typeof input === 'string' ? input : input.url;
                for (const d of blocked) { if (url && url.includes(d)) return Promise.reject(new TypeError('Blocked')); }
                return origFetch.apply(this, arguments);
            };
            const origXHR = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(m, url) {
                for (const d of blocked) { if (url && url.includes(d)) return; }
                return origXHR.apply(this, arguments);
            };
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    // MARK: - YouTube Ad Blocking Script
    var youtubeAdBlockScript: WKUserScript {
        let js = """
        (function() {
            if (!location.hostname.includes('youtube.com')) return;
            const style = document.createElement('style');
            style.textContent = `
                .ytp-ad-module, .ytp-ad-overlay-container, .ytp-ad-text-overlay,
                .ytp-ad-player-overlay, .ytp-ad-player-overlay-instream-info,
                .ytp-ad-survey, .ytp-ad-image-overlay,
                #player-ads, #masthead-ad,
                ytd-promoted-sparkles-web-renderer, ytd-display-ad-renderer,
                ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,
                ytd-banner-promo-renderer, ytd-promoted-video-renderer,
                ytd-compact-promoted-video-renderer, ytd-video-masthead-ad-v3-renderer,
                ytd-primetime-promo-renderer, .ytd-mealbar-promo-renderer,
                ytd-statement-banner-renderer, .ytp-suggested-action,
                ytd-merch-shelf-renderer,
                ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],
                div#panels.ytd-watch-flexy > ytd-engagement-panel-section-list-renderer:has(ytd-ads-engagement-panel-content-renderer)
                { display: none !important; height: 0 !important; overflow: hidden !important; }
                .ytp-ad-skip-button-slot { opacity: 1 !important; }
            `;
            (document.head || document.documentElement).appendChild(style);

            function skipAds() {
                const skipBtns = document.querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, ' +
                    'button.ytp-ad-skip-button-modern, .ytp-ad-skip-button-container button'
                );
                skipBtns.forEach(btn => { try { btn.click(); } catch(e) {} });
                const video = document.querySelector('video.html5-main-video, .html5-video-player video');
                if (video) {
                    const adPlaying = document.querySelector('.ad-showing, .ad-interrupting');
                    if (adPlaying) {
                        video.playbackRate = 16;
                        video.muted = true;
                        if (video.duration && isFinite(video.duration)) video.currentTime = video.duration;
                    }
                }
                document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay').forEach(el => el.remove());
            }

            const adObserver = new MutationObserver(skipAds);
            adObserver.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
            setInterval(skipAds, 500);

            const adUrlPatterns = ['/api/stats/ads', '/pagead/', '/get_midroll_info', '/ptracking',
                '/api/stats/atr', 'googleads.g.doubleclick.net', 'imasdk.googleapis.com',
                'securepubads.g.doubleclick.net', '/youtubei/v1/log_event', 'play.google.com/log'];
            const origFetch2 = window.fetch;
            window.fetch = function(input) {
                const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                if (adUrlPatterns.some(p => url.includes(p))) return Promise.resolve(new Response('', {status: 200}));
                return origFetch2.apply(this, arguments);
            };
            const origXhrOpen2 = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                if (typeof url === 'string' && adUrlPatterns.some(p => url.includes(p))) { this._blocked = true; }
                return origXhrOpen2.apply(this, arguments);
            };
            const origXhrSend2 = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function() {
                if (this._blocked) { this._blocked = false; return; }
                return origXhrSend2.apply(this, arguments);
            };
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
    }

    // MARK: - Cosmetic Ad Hiding
    var cosmeticAdBlockScript: WKUserScript {
        let js = """
        (function() {
            const style = document.createElement('style');
            style.textContent = `
                [id*="google_ads"], [class*="google-ad"],
                [id*="ad-container"], [class*="ad-container"],
                [id*="adBanner"], [class*="adBanner"], [class*="ad-banner"],
                iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
                ins.adsbygoogle, div[data-ad], div[data-ad-slot],
                .ad-wrapper, .ad-unit, .ad-slot, .ad-block, .ad-placement,
                .sponsored-content, .sponsored-ad, .native-ad,
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
            const adDomains = ['popads.net','popcash.net','propellerads.com','juicyads.com',
                'exoclick.com','trafficjunky.net','revcontent.com','mgid.com',
                'adsterra.com','hilltopads.net','clickadu.com','ad-maven.com',
                'googlesyndication.com','doubleclick.net','adnxs.com','taboola.com','outbrain.com'];
            function isAdUrl(url) {
                if (!url) return false;
                try { const u = new URL(url, location.href); return adDomains.some(d => u.hostname.includes(d)); }
                catch(e) { return false; }
            }
            const origOpen = window.open;
            window.open = function(url) {
                if (isAdUrl(url)) return null;
                if (!navigator.userActivation || !navigator.userActivation.isActive) {
                    if (url && url !== 'about:blank') return null;
                }
                return origOpen.apply(this, arguments);
            };
            document.addEventListener('click', function(e) {
                const target = e.target.closest('a');
                if (target && target.href && isAdUrl(target.href)) { e.preventDefault(); e.stopPropagation(); }
            }, true);
        })();
        """
        return WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: false)
    }

    func applyPrivacySettings(to config: WKWebViewConfiguration) {
        let prefs = Prefs.shared
        if prefs.isBlockFingerprintingEnabled {
            config.userContentController.addUserScript(antiFingerprintingScript)
        }
        if prefs.isDoNotTrackEnabled {
            config.userContentController.addUserScript(doNotTrackScript)
        }
        if prefs.isBlockTrackersEnabled {
            config.userContentController.addUserScript(trackerBlockingScript)
        }
        if prefs.isAdBlockEnabled {
            config.userContentController.addUserScript(youtubeAdBlockScript)
            config.userContentController.addUserScript(cosmeticAdBlockScript)
        }
        if prefs.isBlockPopupsEnabled {
            config.userContentController.addUserScript(popupBlockerScript)
        }
    }

    func clearAllData(completion: @escaping () -> Void) {
        let dataStore = WKWebsiteDataStore.default()
        let types = WKWebsiteDataStore.allWebsiteDataTypes()
        dataStore.removeData(ofTypes: types, modifiedSince: Date(timeIntervalSince1970: 0)) { completion() }
    }
}
