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

        if prefs.isBlockThirdPartyCookies {
            if #available(macOS 12.0, *) {
                config.websiteDataStore.httpCookieStore.setCookiePolicy(.allow) // WKWebView handles this
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
