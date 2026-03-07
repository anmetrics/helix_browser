import Foundation
import Combine
import SwiftUI
import WebKit

class WebViewModel: ObservableObject {
    @Published var tabs: [WebTab] = []
    @Published var activeTabId: UUID = UUID() {
        didSet {
            if let activeTab = tabs.first(where: { $0.id == activeTabId }) {
                currentUrlString = activeTab.url
            }
        }
    }
    
    @Published var currentUrlString: String = "helix://start"
    @Published var isLoading: Bool = false
    @Published var progress: Double = 0.0
    @Published var canGoBack: Bool = false
    @Published var canGoForward: Bool = false
    @Published var isFindBarVisible: Bool = false
    @Published var findText: String = ""
    @Published var zoomLevels: [UUID: Int] = [:] // Per-tab zoom %
    
    @ObservedObject var prefs = Prefs.shared
    
    // Browser Engine
    let processPool = WKProcessPool()
    private var cancellables = Set<AnyCancellable>()
    
    // Persistence keys
    private let historyKey = "helix_history"
    private let bookmarksKey = "helix_bookmarks"
    
    init() {
        let firstTab = WebTab(url: "helix://start", title: "Helix Browser")
        self.tabs = [firstTab]
        self.activeTabId = firstTab.id
        self.currentUrlString = firstTab.url
        
        // Pre-compile ad block rules
        if prefs.isAdBlockEnabled {
            AdBlockEngine.shared.compileRules { _ in }
        }
    }
    
    // MARK: - Tab Management
    
    func createNewTab(url: String = "helix://start", isIncognito: Bool = false) {
        let formatted = url.hasPrefix("helix://") ? url : UrlUtils.formatUrl(url)
        let newTab = WebTab(url: formatted, title: "New Tab", isIncognito: isIncognito)
        tabs.append(newTab)
        activeTabId = newTab.id
    }
    
    func closeTab(id: UUID) {
        guard tabs.count > 1 else { return }
        if let index = tabs.firstIndex(where: { $0.id == id }) {
            tabs.remove(at: index)
            WebView.clearCache(for: id)
            if activeTabId == id {
                activeTabId = tabs.last?.id ?? tabs[0].id
            }
        }
    }
    
    func closeOtherTabs(except id: UUID) {
        let otherIds = tabs.filter { $0.id != id }.map { $0.id }
        otherIds.forEach { WebView.clearCache(for: $0) }
        tabs.removeAll(where: { $0.id != id })
        activeTabId = id
    }
    
    func duplicateTab(id: UUID) {
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        createNewTab(url: tab.url)
    }
    
    // MARK: - Navigation
    
    func loadUrl(_ input: String) {
        let formatted = input.hasPrefix("helix://") ? input : UrlUtils.formatUrl(input)
        if let index = tabs.firstIndex(where: { $0.id == activeTabId }) {
            tabs[index].url = formatted
            if currentUrlString != formatted {
                currentUrlString = formatted
            }
            // Signal WebView that this is an explicit navigation request
            WebView.requestLoad(tabId: activeTabId, url: formatted)
        }
    }
    
    func reload() {
        NotificationCenter.default.post(name: NSNotification.Name("WebViewReload"), object: activeTabId)
    }
    
    func goBack() {
        NotificationCenter.default.post(name: NSNotification.Name("WebViewGoBack"), object: activeTabId)
    }
    
    func goForward() {
        NotificationCenter.default.post(name: NSNotification.Name("WebViewGoForward"), object: activeTabId)
    }
    
    func goHome() {
        loadUrl(prefs.homepage)
    }
    
    // MARK: - Zoom
    
    func zoomIn() {
        let current = zoomLevels[activeTabId] ?? 100
        let newZoom = min(current + 10, 300)
        zoomLevels[activeTabId] = newZoom
        applyZoom(to: activeTabId)
    }
    
    func zoomOut() {
        let current = zoomLevels[activeTabId] ?? 100
        let newZoom = max(current - 10, 25)
        zoomLevels[activeTabId] = newZoom
        applyZoom(to: activeTabId)
    }
    
    func resetZoom() {
        zoomLevels[activeTabId] = 100
        applyZoom(to: activeTabId)
    }
    
    func applyZoom(to id: UUID) {
        let zoom = zoomLevels[id] ?? 100
        NotificationCenter.default.post(
            name: NSNotification.Name("WebViewZoom"),
            object: ["tabId": id, "zoom": zoom] as [String: Any]
        )
    }
    
    // MARK: - Find In Page
    
    func findInPage(_ text: String) {
        NotificationCenter.default.post(name: NSNotification.Name("WebViewFind"), object: ["tabId": activeTabId, "text": text])
    }
    
    func findNext() {
        NotificationCenter.default.post(name: NSNotification.Name("WebViewFindNext"), object: activeTabId)
    }
    
    func findPrevious() {
        NotificationCenter.default.post(name: NSNotification.Name("WebViewFindPrev"), object: activeTabId)
    }
    
    func dismissFind() {
        isFindBarVisible = false
        findText = ""
        NotificationCenter.default.post(name: NSNotification.Name("WebViewFindDismiss"), object: activeTabId)
    }
    
    // MARK: - Page Events
    
    func onPageFinished(url: String, title: String) {
        if let index = tabs.firstIndex(where: { $0.id == activeTabId }) {
            tabs[index].url = url
            tabs[index].title = title.isEmpty ? UrlUtils.getDisplayUrl(url) : title
            tabs[index].faviconUrl = UrlUtils.getFaviconUrl(url)
            self.currentUrlString = url
        }
        
        if prefs.isSaveHistoryEnabled && !activeTabIsIncognito() {
            saveHistory(title: title, url: url)
        }
    }
    
    private func activeTabIsIncognito() -> Bool {
        return tabs.first(where: { $0.id == activeTabId })?.isIncognito ?? false
    }
    
    // MARK: - History
    
    private func saveHistory(title: String, url: String) {
        var history = getHistory()
        let entry: [String: String] = [
            "title": title.isEmpty ? url : title,
            "url": url,
            "timestamp": String(Date().timeIntervalSince1970)
        ]
        // Deduplicate consecutive entries
        if history.first?["url"] == url { return }
        history.insert(entry, at: 0)
        if history.count > 1000 { history = Array(history.prefix(1000)) }
        UserDefaults.standard.set(history, forKey: historyKey)
    }
    
    func getHistory() -> [[String: String]] {
        return UserDefaults.standard.array(forKey: historyKey) as? [[String: String]] ?? []
    }
    
    func clearHistory() {
        UserDefaults.standard.removeObject(forKey: historyKey)
        objectWillChange.send()
    }
    
    // MARK: - Bookmarks
    
    func toggleBookmark() {
        guard let activeTab = tabs.first(where: { $0.id == activeTabId }) else { return }
        var bookmarks = getBookmarks()
        let url = activeTab.url
        
        if let index = bookmarks.firstIndex(where: { $0["url"] == url }) {
            bookmarks.remove(at: index)
        } else {
            let entry: [String: String] = [
                "title": activeTab.title,
                "url": url
            ]
            bookmarks.append(entry)
        }
        UserDefaults.standard.set(bookmarks, forKey: bookmarksKey)
        objectWillChange.send()
    }
    
    func getBookmarks() -> [[String: String]] {
        return UserDefaults.standard.array(forKey: bookmarksKey) as? [[String: String]] ?? []
    }
    
    func isBookmarked(url: String) -> Bool {
        return getBookmarks().contains(where: { $0["url"] == url })
    }
    
    func isCurrentPageBookmarked() -> Bool {
        return isBookmarked(url: currentUrlString)
    }
}
