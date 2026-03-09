import Foundation

class Prefs {
    static let shared = Prefs()
    private let defaults = UserDefaults.standard

    private init() {}

    var searchEngine: String {
        get { defaults.string(forKey: "search_engine") ?? "google" }
        set { defaults.set(newValue, forKey: "search_engine") }
    }

    var homepage: String {
        get { defaults.string(forKey: "homepage") ?? "https://www.google.com" }
        set { defaults.set(newValue, forKey: "homepage") }
    }

    var isAdBlockEnabled: Bool {
        get { defaults.object(forKey: "ad_block") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "ad_block") }
    }

    var isSaveHistoryEnabled: Bool {
        get { defaults.object(forKey: "save_history") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "save_history") }
    }

    var isDesktopMode: Bool {
        get { defaults.bool(forKey: "desktop_mode") }
        set { defaults.set(newValue, forKey: "desktop_mode") }
    }

    var isBlockTrackersEnabled: Bool {
        get { defaults.object(forKey: "block_trackers") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "block_trackers") }
    }

    var isBlockThirdPartyCookies: Bool {
        get { defaults.object(forKey: "block_third_party_cookies") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "block_third_party_cookies") }
    }

    var isDoNotTrackEnabled: Bool {
        get { defaults.object(forKey: "do_not_track") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "do_not_track") }
    }

    var isHttpsUpgradeEnabled: Bool {
        get { defaults.object(forKey: "https_upgrade") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "https_upgrade") }
    }

    var isBlockFingerprintingEnabled: Bool {
        get { defaults.object(forKey: "block_fingerprinting") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "block_fingerprinting") }
    }

    var isBlockPopupsEnabled: Bool {
        get { defaults.object(forKey: "block_popups") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "block_popups") }
    }

    var isRestoreTabsEnabled: Bool {
        get { defaults.object(forKey: "restore_tabs") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "restore_tabs") }
    }

    var defaultZoom: Int {
        get { defaults.object(forKey: "default_zoom") as? Int ?? 100 }
        set { defaults.set(newValue, forKey: "default_zoom") }
    }
}
