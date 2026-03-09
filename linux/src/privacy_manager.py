"""Privacy management for Helix Browser"""


class PrivacyManager:
    _instance = None

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def __init__(self):
        self.trackers_blocked = 0

    ANTI_FINGERPRINT_JS = """
    (function() {
        // Canvas fingerprinting protection
        const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
        HTMLCanvasElement.prototype.toDataURL = function(type) {
            const ctx = this.getContext('2d');
            if (ctx) {
                const imgData = ctx.getImageData(0, 0, this.width, this.height);
                for (let i = 0; i < imgData.data.length; i += 4) {
                    imgData.data[i] = imgData.data[i] ^ 1;
                }
                ctx.putImageData(imgData, 0, 0);
            }
            return origToDataURL.apply(this, arguments);
        };

        // WebGL fingerprinting protection
        const getParam = WebGLRenderingContext.prototype.getParameter;
        WebGLRenderingContext.prototype.getParameter = function(param) {
            if (param === 37445) return 'Intel Inc.';
            if (param === 37446) return 'Intel Iris OpenGL Engine';
            return getParam.apply(this, arguments);
        };

        // AudioContext fingerprinting protection
        if (window.AudioContext || window.webkitAudioContext) {
            const AudioCtx = window.AudioContext || window.webkitAudioContext;
            const origCreateOscillator = AudioCtx.prototype.createOscillator;
            AudioCtx.prototype.createOscillator = function() {
                const osc = origCreateOscillator.apply(this, arguments);
                const origConnect = osc.connect;
                osc.connect = function(dest) {
                    if (dest instanceof AnalyserNode) {
                        return osc;
                    }
                    return origConnect.apply(this, arguments);
                };
                return osc;
            };
        }

        // Navigator properties
        Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 4 });
        Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
        Object.defineProperty(navigator, 'platform', { get: () => 'Linux x86_64' });

        // Battery API blocking
        if (navigator.getBattery) {
            navigator.getBattery = () => Promise.reject('Battery API blocked');
        }

        // Screen resolution normalization
        Object.defineProperty(screen, 'colorDepth', { get: () => 24 });
    })();
    """

    DO_NOT_TRACK_JS = """
    (function() {
        Object.defineProperty(navigator, 'doNotTrack', { get: () => '1' });
        Object.defineProperty(window, 'doNotTrack', { get: () => '1' });
    })();
    """

    TRACKER_DOMAINS = [
        "googlesyndication.com", "doubleclick.net", "google-analytics.com",
        "googletagmanager.com", "facebook.com/tr", "adservice.google.com",
        "amazon-adsystem.com", "ads.yahoo.com", "adnxs.com", "outbrain.com",
        "taboola.com", "criteo.com", "scorecardresearch.com", "quantserve.com",
        "hotjar.com", "mixpanel.com", "segment.io", "optimizely.com",
        "newrelic.com", "nr-data.net", "clarity.ms", "fullstory.com",
        "heapanalytics.com", "amplitude.com", "mouseflow.com",
    ]

    @classmethod
    def get_tracker_blocking_js(cls):
        domains_js = ", ".join(f'"{d}"' for d in cls.TRACKER_DOMAINS)
        return f"""
        (function() {{
            const blockedDomains = [{domains_js}];
            function shouldBlock(url) {{
                try {{
                    const u = new URL(url);
                    return blockedDomains.some(d => u.hostname.includes(d) || u.href.includes(d));
                }} catch(e) {{ return false; }}
            }}

            const origFetch = window.fetch;
            window.fetch = function(input, init) {{
                const url = typeof input === 'string' ? input : input.url;
                if (shouldBlock(url)) {{
                    return Promise.resolve(new Response('', {{ status: 200 }}));
                }}
                return origFetch.apply(this, arguments);
            }};

            const origXhrOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {{
                if (shouldBlock(url)) {{
                    this._blocked = true;
                }}
                return origXhrOpen.apply(this, arguments);
            }};

            const origXhrSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function() {{
                if (this._blocked) return;
                return origXhrSend.apply(this, arguments);
            }};

            if (navigator.sendBeacon) {{
                const origBeacon = navigator.sendBeacon;
                navigator.sendBeacon = function(url, data) {{
                    if (shouldBlock(url)) return true;
                    return origBeacon.apply(this, arguments);
                }};
            }}
        }})();
        """

    HTTPS_UPGRADE_JS = """
    (function() {
        document.querySelectorAll('a[href^="http:"]').forEach(function(a) {
            a.href = a.href.replace(/^http:/, 'https:');
        });
        new MutationObserver(function(mutations) {
            mutations.forEach(function(m) {
                m.addedNodes.forEach(function(node) {
                    if (node.querySelectorAll) {
                        node.querySelectorAll('a[href^="http:"]').forEach(function(a) {
                            a.href = a.href.replace(/^http:/, 'https:');
                        });
                    }
                });
            });
        }).observe(document.body, { childList: true, subtree: true });
    })();
    """

    YOUTUBE_AD_BLOCK_JS = """
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
            'ytd-merch-shelf-renderer,' +
            'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]' +
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
    """

    COSMETIC_AD_BLOCK_JS = """
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
    """

    POPUP_BLOCKER_JS = """
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
    """

    def get_all_scripts(self, prefs):
        scripts = []
        if getattr(prefs, "is_block_fingerprinting_enabled", True):
            scripts.append(self.ANTI_FINGERPRINT_JS)
        if getattr(prefs, "is_do_not_track_enabled", True):
            scripts.append(self.DO_NOT_TRACK_JS)
        if getattr(prefs, "is_block_trackers_enabled", True):
            scripts.append(self.get_tracker_blocking_js())
        if getattr(prefs, "is_https_upgrade_enabled", True):
            scripts.append(self.HTTPS_UPGRADE_JS)
        # YouTube ad blocking + cosmetic filtering + popup blocking
        if getattr(prefs, "is_ad_block_enabled", True):
            scripts.append(self.YOUTUBE_AD_BLOCK_JS)
            scripts.append(self.COSMETIC_AD_BLOCK_JS)
        if getattr(prefs, "is_block_popups_enabled", True):
            scripts.append(self.POPUP_BLOCKER_JS)
        return "\n".join(scripts)
