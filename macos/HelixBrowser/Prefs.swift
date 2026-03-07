import Foundation
import SwiftUI

class Prefs: ObservableObject {
    static let shared = Prefs()
    
    @AppStorage("save_history") var isSaveHistoryEnabled: Bool = true
    @AppStorage("desktop_mode") var isDesktopMode: Bool = true
    @AppStorage("search_engine") var searchEngine: String = "google"
    @AppStorage("homepage") var homepage: String = "https://www.google.com"
    @AppStorage("ad_block") var isAdBlockEnabled: Bool = true
    @AppStorage("downloads_dir") var downloadsDir: String = ""
    @AppStorage("default_zoom") var defaultZoom: Int = 100
    
    private init() {}
}
