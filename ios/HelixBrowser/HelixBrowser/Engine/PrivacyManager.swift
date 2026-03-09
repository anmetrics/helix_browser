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
    }

    func clearAllData(completion: @escaping () -> Void) {
        let dataStore = WKWebsiteDataStore.default()
        let types = WKWebsiteDataStore.allWebsiteDataTypes()
        dataStore.removeData(ofTypes: types, modifiedSince: Date(timeIntervalSince1970: 0)) { completion() }
    }
}
