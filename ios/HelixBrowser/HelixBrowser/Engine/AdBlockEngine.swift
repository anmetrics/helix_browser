import Foundation
import WebKit

class AdBlockEngine {
    static let shared = AdBlockEngine()

    private var ruleList: WKContentRuleList?
    private let store = WKContentRuleListStore.default()
    private let ruleListID = "helix-adblock-ios-v3"

    private init() {}

    func compileRules(completion: @escaping (WKContentRuleList?) -> Void) {
        store?.lookUpContentRuleList(forIdentifier: ruleListID) { [weak self] list, _ in
            if let list = list {
                self?.ruleList = list
                completion(list)
                return
            }
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

    func apply(to config: WKWebViewConfiguration) {
        if let ruleList = ruleList {
            config.userContentController.add(ruleList)
        }
    }

    func invalidate() {
        store?.removeContentRuleList(forIdentifier: ruleListID) { _ in }
        ruleList = nil
    }

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
        {"trigger": {"url-filter": "openx\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "casalemedia\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "clarity\\\\.ms"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "fullstory\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "heapanalytics\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "cookiebot\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "onetrust\\\\.com"}, "action": {"type": "block"}},

        {"trigger": {"url-filter": "youtube\\\\.com/api/stats/ads"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/pagead"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/get_midroll_info"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/ptracking"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/api/stats/atr"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/error_204.*adformat"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/generate_204.*adformat"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "youtube\\\\.com/youtubei/v1/log_event"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "googleads\\\\.g\\\\.doubleclick\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "static\\\\.doubleclick\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "s0\\\\.2mdn\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "imasdk\\\\.googleapis\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "tpc\\\\.googlesyndication\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "securepubads\\\\.g\\\\.doubleclick\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "fundingchoicesmessages\\\\.google\\\\.com"}, "action": {"type": "block"}},

        {"trigger": {"url-filter": "popads\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "popcash\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "propellerads\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "juicyads\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "exoclick\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "trafficjunky\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "revcontent\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "mgid\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "adsterra\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "hilltopads\\\\.net"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "clickadu\\\\.com"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": "ad-maven\\\\.com"}, "action": {"type": "block"}},

        {"trigger": {"url-filter": ".*\\\\/ads\\\\/"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/ad\\\\.js"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/advert"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/ad-banner"}, "action": {"type": "block"}},
        {"trigger": {"url-filter": ".*\\\\/pop(under|up).*\\\\.js"}, "action": {"type": "block"}}
    ]
    """
}
