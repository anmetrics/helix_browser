import Foundation
import SwiftUI

class Prefs: ObservableObject {
    static let shared = Prefs()

    // General
    @AppStorage("save_history") var isSaveHistoryEnabled: Bool = true
    @AppStorage("desktop_mode") var isDesktopMode: Bool = true
    @AppStorage("search_engine") var searchEngine: String = "google"
    @AppStorage("homepage") var homepage: String = "https://www.google.com"
    @AppStorage("downloads_dir") var downloadsDir: String = ""
    @AppStorage("default_zoom") var defaultZoom: Int = 100

    // Privacy & Security
    @AppStorage("ad_block") var isAdBlockEnabled: Bool = true
    @AppStorage("block_trackers") var isBlockTrackersEnabled: Bool = true
    @AppStorage("block_third_party_cookies") var isBlockThirdPartyCookies: Bool = true
    @AppStorage("do_not_track") var isDoNotTrackEnabled: Bool = true
    @AppStorage("https_upgrade") var isHttpsUpgradeEnabled: Bool = true
    @AppStorage("block_fingerprinting") var isBlockFingerprintingEnabled: Bool = true
    @AppStorage("block_popups") var isBlockPopupsEnabled: Bool = true
    @AppStorage("block_autoplay") var isBlockAutoplayEnabled: Bool = false

    // Tab behavior
    @AppStorage("restore_tabs") var isRestoreTabsEnabled: Bool = true
    @AppStorage("suspend_inactive_tabs") var isSuspendInactiveEnabled: Bool = true
    @AppStorage("confirm_close_multiple") var isConfirmCloseMultiple: Bool = true

    private init() {}
}
