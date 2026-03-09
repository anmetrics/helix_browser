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
        return "\n".join(scripts)
