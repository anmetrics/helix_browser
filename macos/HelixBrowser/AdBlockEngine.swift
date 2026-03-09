import Foundation
import WebKit

class AdBlockEngine {
    static let shared = AdBlockEngine()
    
    private var ruleList: WKContentRuleList?
    private let store = WKContentRuleListStore.default()
    private let ruleListID = "helix-adblock-rules"
    
    private init() {}
    
    /// Compile and cache ad-block rules
    func compileRules(completion: @escaping (WKContentRuleList?) -> Void) {
        // Check cache first
        store?.lookUpContentRuleList(forIdentifier: ruleListID) { [weak self] list, error in
            if let list = list {
                self?.ruleList = list
                completion(list)
                return
            }
            // Compile from JSON
            self?.store?.compileContentRuleList(forIdentifier: self?.ruleListID ?? "", encodedContentRuleList: Self.adBlockJSON) { list, error in
                if let error = error {
                    print("[AdBlock] Compile error: \(error.localizedDescription)")
                    completion(nil)
                    return
                }
                self?.ruleList = list
                completion(list)
            }
        }
    }
    
    /// Apply rules to a WKWebView configuration
    func apply(to config: WKWebViewConfiguration) {
        if let ruleList = ruleList {
            config.userContentController.add(ruleList)
        }
    }
    
    /// Remove rules from config
    func remove(from config: WKWebViewConfiguration) {
        config.userContentController.removeAllContentRuleLists()
    }
    
    /// Rebuild rules (e.g. when toggling)
    func invalidate() {
        store?.removeContentRuleList(forIdentifier: ruleListID) { _ in }
        ruleList = nil
    }
    
    // MARK: - Built-in Ad Block Rules (WebKit Content Blocker JSON format)
    // Comprehensive rules blocking ads, trackers, annoyances, and cookie banners
    private static let adBlockJSON = """
    [
        {"trigger": {"url-filter": ".*", "resource-type": ["popup"]}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "googlesyndication\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "doubleclick\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "google-analytics\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "googletagmanager\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "facebook\\\\.com/tr"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "connect\\\\.facebook\\\\.net.*fbevents"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "adservice\\\\.google\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "pagead2\\\\.googlesyndication\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "amazon-adsystem\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "ads\\\\.yahoo\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "ad\\\\.doubleclick\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "adnxs\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "outbrain\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "taboola\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "criteo\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "scorecardresearch\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "quantserve\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "hotjar\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "mixpanel\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "segment\\\\.io"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "optimizely\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "zedo\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "rubiconproject\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "pubmatic\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "moatads\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "mediavine\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "adsrvr\\\\.org"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "smartadserver\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "bidswitch\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "sharethrough\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "indexexchange\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "openx\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "casalemedia\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "advertising\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "newrelic\\\\.com.*beacon"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "nr-data\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "clarity\\\\.ms"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "fullstory\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "heapanalytics\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "amplitude\\\\.com/api"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "mouseflow\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "crazyegg\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "luckyorange\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "inspectlet\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "cookiebot\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "onetrust\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "consensu\\\\.org"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\.ads\\\\."}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/ads\\\\/"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/ad\\\\.js"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/advert"}, "action": {"type": "block"}},
        {
            "trigger": {
                "url-filter": ".*",
                "resource-type": ["image", "style-sheet", "script"],
                "if-domain": ["*doubleclick.net", "*googlesyndication.com", "*adnxs.com", "*moatads.com"]
            },
            "action": {"type": "block"}
        },
        {
            "trigger": {"url-filter": ".*", "resource-type": ["raw"]},
            "action": {"type": "block"},
            "comment": "Block web beacons / tracking pixels via sendBeacon"
        }
    ]
    """
}
