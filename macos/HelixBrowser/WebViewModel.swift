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
                // Update last access time
                if let index = tabs.firstIndex(where: { $0.id == activeTabId }) {
                    tabs[index].lastAccessTime = Date()
                }
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
    @Published var zoomLevels: [UUID: Int] = [:]
    @Published var tabGroups: [TabGroup] = []
    @Published var trackersBlocked: Int = 0
    @Published var isTabSearchVisible: Bool = false
    @Published var tabSearchQuery: String = ""

    @ObservedObject var prefs = Prefs.shared

    // Browser Engine
    let processPool = WKProcessPool()
    private var cancellables = Set<AnyCancellable>()

    // Persistence keys
    private let historyKey = "helix_history"
    private let bookmarksKey = "helix_bookmarks"
    private let savedTabsKey = "helix_saved_tabs"
    private let savedGroupsKey = "helix_saved_groups"
    private let trackersBlockedKey = "helix_trackers_blocked"

    init() {
        // Restore saved session or create new tab
        if let savedTabs = restoreTabs(), !savedTabs.isEmpty {
            self.tabs = savedTabs
            self.activeTabId = savedTabs.first?.id ?? UUID()
            self.currentUrlString = savedTabs.first?.url ?? "helix://start"
        } else {
            let firstTab = WebTab(url: "helix://start", title: "Helix Browser")
            self.tabs = [firstTab]
            self.activeTabId = firstTab.id
            self.currentUrlString = firstTab.url
        }

        // Restore tab groups
        if let data = UserDefaults.standard.data(forKey: savedGroupsKey),
           let groups = try? JSONDecoder().decode([TabGroup].self, from: data) {
            self.tabGroups = groups
        }

        // Restore tracker count
        self.trackersBlocked = UserDefaults.standard.integer(forKey: trackersBlockedKey)

        // Pre-compile ad block rules
        if prefs.isAdBlockEnabled {
            AdBlockEngine.shared.compileRules { _ in }
        }

        // Auto-save tabs periodically
        Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.saveTabs() }
            .store(in: &cancellables)

        // Suspend inactive tabs after 10 minutes
        Timer.publish(every: 60, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.suspendInactiveTabs() }
            .store(in: &cancellables)
    }

    // MARK: - Session Persistence

    func saveTabs() {
        let saveable = tabs.filter { !$0.isIncognito }
        if let data = try? JSONEncoder().encode(saveable) {
            UserDefaults.standard.set(data, forKey: savedTabsKey)
        }
        if let data = try? JSONEncoder().encode(tabGroups) {
            UserDefaults.standard.set(data, forKey: savedGroupsKey)
        }
    }

    private func restoreTabs() -> [WebTab]? {
        guard let data = UserDefaults.standard.data(forKey: savedTabsKey),
              let tabs = try? JSONDecoder().decode([WebTab].self, from: data) else { return nil }
        return tabs
    }

    // MARK: - Tab Management

    func createNewTab(url: String = "helix://start", isIncognito: Bool = false) {
        let formatted = url.hasPrefix("helix://") ? url : UrlUtils.formatUrl(url)
        let newTab = WebTab(url: formatted, title: "Tab mới", isIncognito: isIncognito)

        // Insert after current tab
        if let currentIndex = tabs.firstIndex(where: { $0.id == activeTabId }) {
            tabs.insert(newTab, at: currentIndex + 1)
        } else {
            tabs.append(newTab)
        }
        activeTabId = newTab.id
        saveTabs()
    }

    func closeTab(id: UUID) {
        guard tabs.count > 1 else { return }
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        // Don't allow closing pinned tabs without confirmation
        if tab.isPinned { return }

        if let index = tabs.firstIndex(where: { $0.id == id }) {
            tabs.remove(at: index)
            WebView.clearCache(for: id)
            if activeTabId == id {
                // Switch to nearest tab
                let newIndex = min(index, tabs.count - 1)
                activeTabId = tabs[newIndex].id
            }
        }
        saveTabs()
    }

    func closeOtherTabs(except id: UUID) {
        let otherIds = tabs.filter { $0.id != id && !$0.isPinned }.map { $0.id }
        otherIds.forEach { WebView.clearCache(for: $0) }
        tabs.removeAll(where: { $0.id != id && !$0.isPinned })
        activeTabId = id
        saveTabs()
    }

    func closeTabsToRight(of id: UUID) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        let rightTabs = tabs.suffix(from: index + 1).filter { !$0.isPinned }
        rightTabs.forEach { WebView.clearCache(for: $0.id) }
        tabs.removeAll(where: { tab in
            guard let tabIndex = tabs.firstIndex(where: { $0.id == tab.id }) else { return false }
            return tabIndex > index && !tab.isPinned
        })
        saveTabs()
    }

    func duplicateTab(id: UUID) {
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        createNewTab(url: tab.url, isIncognito: tab.isIncognito)
    }

    func pinTab(id: UUID) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        tabs[index].isPinned.toggle()
        // Move pinned tabs to front
        let pinned = tabs.filter { $0.isPinned }
        let unpinned = tabs.filter { !$0.isPinned }
        tabs = pinned + unpinned
        saveTabs()
    }

    func muteTab(id: UUID) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        tabs[index].isMuted.toggle()
    }

    func moveTab(from source: IndexSet, to destination: Int) {
        tabs.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }

    // MARK: - Tab Groups

    func createTabGroup(name: String, tabIds: [UUID], colorHex: String = "#8B8BFF") {
        let group = TabGroup(name: name, colorHex: colorHex)
        tabGroups.append(group)
        for id in tabIds {
            if let index = tabs.firstIndex(where: { $0.id == id }) {
                tabs[index].groupId = group.id
                tabs[index].groupName = name
            }
        }
        saveTabs()
    }

    func addTabToGroup(tabId: UUID, groupId: UUID) {
        guard let group = tabGroups.first(where: { $0.id == groupId }),
              let index = tabs.firstIndex(where: { $0.id == tabId }) else { return }
        tabs[index].groupId = groupId
        tabs[index].groupName = group.name
        saveTabs()
    }

    func removeTabFromGroup(tabId: UUID) {
        guard let index = tabs.firstIndex(where: { $0.id == tabId }) else { return }
        tabs[index].groupId = nil
        tabs[index].groupName = nil
        saveTabs()
    }

    func deleteTabGroup(groupId: UUID) {
        tabGroups.removeAll(where: { $0.id == groupId })
        for i in tabs.indices where tabs[i].groupId == groupId {
            tabs[i].groupId = nil
            tabs[i].groupName = nil
        }
        saveTabs()
    }

    // MARK: - Tab Search

    var filteredTabs: [WebTab] {
        if tabSearchQuery.isEmpty { return tabs }
        return tabs.filter {
            $0.title.localizedCaseInsensitiveContains(tabSearchQuery) ||
            $0.url.localizedCaseInsensitiveContains(tabSearchQuery)
        }
    }

    // MARK: - Memory Management

    private func suspendInactiveTabs() {
        let tenMinutesAgo = Date().addingTimeInterval(-600)
        for i in tabs.indices {
            if tabs[i].id != activeTabId &&
               tabs[i].lastAccessTime < tenMinutesAgo &&
               !tabs[i].isSuspended &&
               !tabs[i].isPinned {
                tabs[i].isSuspended = true
            }
        }
    }

    // MARK: - Navigation

    func loadUrl(_ input: String) {
        let formatted = input.hasPrefix("helix://") ? input : UrlUtils.formatUrl(input)
        if let index = tabs.firstIndex(where: { $0.id == activeTabId }) {
            tabs[index].url = formatted
            tabs[index].isSuspended = false
            if currentUrlString != formatted {
                currentUrlString = formatted
            }
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

    // MARK: - Privacy

    func incrementTrackersBlocked(count: Int = 1) {
        trackersBlocked += count
        UserDefaults.standard.set(trackersBlocked, forKey: trackersBlockedKey)
    }

    func clearAllBrowsingData() {
        clearHistory()
        clearCookiesAndCache()
        trackersBlocked = 0
        UserDefaults.standard.set(0, forKey: trackersBlockedKey)
    }

    func clearCookiesAndCache() {
        let dataStore = WKWebsiteDataStore.default()
        let types = WKWebsiteDataStore.allWebsiteDataTypes()
        dataStore.fetchDataRecords(ofTypes: types) { records in
            dataStore.removeData(ofTypes: types, for: records) {}
        }
    }

    // MARK: - History

    private func saveHistory(title: String, url: String) {
        var history = getHistory()
        let entry: [String: String] = [
            "title": title.isEmpty ? url : title,
            "url": url,
            "timestamp": String(Date().timeIntervalSince1970)
        ]
        if history.first?["url"] == url { return }
        history.insert(entry, at: 0)
        if history.count > 5000 { history = Array(history.prefix(5000)) }
        UserDefaults.standard.set(history, forKey: historyKey)
    }

    func getHistory() -> [[String: String]] {
        return UserDefaults.standard.array(forKey: historyKey) as? [[String: String]] ?? []
    }

    func clearHistory() {
        UserDefaults.standard.removeObject(forKey: historyKey)
        objectWillChange.send()
    }

    func deleteHistoryItem(url: String) {
        var history = getHistory()
        history.removeAll(where: { $0["url"] == url })
        UserDefaults.standard.set(history, forKey: historyKey)
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
                "url": url,
                "favicon": activeTab.faviconUrl ?? "",
                "timestamp": String(Date().timeIntervalSince1970)
            ]
            bookmarks.append(entry)
        }
        UserDefaults.standard.set(bookmarks, forKey: bookmarksKey)
        objectWillChange.send()
    }

    func getBookmarks() -> [[String: String]] {
        return UserDefaults.standard.array(forKey: bookmarksKey) as? [[String: String]] ?? []
    }

    func deleteBookmark(url: String) {
        var bookmarks = getBookmarks()
        bookmarks.removeAll(where: { $0["url"] == url })
        UserDefaults.standard.set(bookmarks, forKey: bookmarksKey)
        objectWillChange.send()
    }

    func isBookmarked(url: String) -> Bool {
        return getBookmarks().contains(where: { $0["url"] == url })
    }

    func isCurrentPageBookmarked() -> Bool {
        return isBookmarked(url: currentUrlString)
    }
}
