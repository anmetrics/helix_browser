import Foundation
import WebKit

class TabManager {
    static let shared = TabManager()

    private(set) var tabs: [BrowserTab] = []
    private(set) var activeTabId: String = ""
    private(set) var tabGroups: [TabGroup] = []
    var trackersBlocked: Int {
        get { UserDefaults.standard.integer(forKey: "trackers_blocked") }
        set { UserDefaults.standard.set(newValue, forKey: "trackers_blocked") }
    }

    var onTabsChanged: (() -> Void)?
    var onActiveTabChanged: ((BrowserTab?) -> Void)?

    private let savedTabsKey = "helix_saved_tabs"
    private let savedGroupsKey = "helix_saved_groups"

    private init() {
        if Prefs.shared.isRestoreTabsEnabled {
            restoreTabs()
        }
        if tabs.isEmpty {
            let tab = BrowserTab()
            tabs.append(tab)
            activeTabId = tab.id
        }
    }

    var activeTab: BrowserTab? {
        return tabs.first(where: { $0.id == activeTabId })
    }

    var activeTabIndex: Int? {
        return tabs.firstIndex(where: { $0.id == activeTabId })
    }

    // MARK: - Tab CRUD

    @discardableResult
    func createTab(url: String = "helix://start", isIncognito: Bool = false) -> BrowserTab {
        let formatted = url.hasPrefix("helix://") ? url : UrlUtils.formatUrl(url)
        let tab = BrowserTab(url: formatted, isIncognito: isIncognito)

        if let currentIndex = activeTabIndex {
            tabs.insert(tab, at: currentIndex + 1)
        } else {
            tabs.append(tab)
        }
        activeTabId = tab.id
        onTabsChanged?()
        onActiveTabChanged?(tab)
        saveTabs()
        return tab
    }

    func closeTab(id: String) {
        guard tabs.count > 1 else { return }
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        if tab.isPinned { return }

        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        tab.webView?.stopLoading()
        tab.webView = nil
        tabs.remove(at: index)

        if activeTabId == id {
            let newIndex = min(index, tabs.count - 1)
            activeTabId = tabs[newIndex].id
            onActiveTabChanged?(tabs[newIndex])
        }
        onTabsChanged?()
        saveTabs()
    }

    func switchToTab(id: String) {
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        activeTabId = id
        tab.lastAccessTime = Date()
        tab.isSuspended = false
        onActiveTabChanged?(tab)
    }

    func closeOtherTabs(except id: String) {
        tabs.filter { $0.id != id && !$0.isPinned }.forEach {
            $0.webView?.stopLoading()
            $0.webView = nil
        }
        tabs.removeAll(where: { $0.id != id && !$0.isPinned })
        activeTabId = id
        onTabsChanged?()
        saveTabs()
    }

    func closeTabsToRight(of id: String) {
        guard let index = tabs.firstIndex(where: { $0.id == id }) else { return }
        let rightTabs = tabs.suffix(from: index + 1).filter { !$0.isPinned }
        rightTabs.forEach { $0.webView = nil }
        tabs.removeAll(where: { tab in
            guard let i = tabs.firstIndex(where: { $0.id == tab.id }) else { return false }
            return i > index && !tab.isPinned
        })
        onTabsChanged?()
        saveTabs()
    }

    func duplicateTab(id: String) {
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        createTab(url: tab.url, isIncognito: tab.isIncognito)
    }

    func pinTab(id: String) {
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        tab.isPinned.toggle()
        let pinned = tabs.filter { $0.isPinned }
        let unpinned = tabs.filter { !$0.isPinned }
        tabs = pinned + unpinned
        onTabsChanged?()
        saveTabs()
    }

    func muteTab(id: String) {
        guard let tab = tabs.first(where: { $0.id == id }) else { return }
        tab.isMuted.toggle()
        onTabsChanged?()
    }

    // MARK: - Tab Groups

    func createTabGroup(name: String, tabIds: [String]) {
        let group = TabGroup(name: name)
        tabGroups.append(group)
        for id in tabIds {
            if let tab = tabs.first(where: { $0.id == id }) {
                tab.groupId = group.id
                tab.groupName = name
            }
        }
        saveTabs()
    }

    // MARK: - Search

    func searchTabs(query: String) -> [BrowserTab] {
        if query.isEmpty { return tabs }
        return tabs.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.url.localizedCaseInsensitiveContains(query)
        }
    }

    // MARK: - Persistence

    func saveTabs() {
        let saveable = tabs.filter { !$0.isIncognito }
        if let data = try? JSONEncoder().encode(saveable) {
            UserDefaults.standard.set(data, forKey: savedTabsKey)
        }
        if let data = try? JSONEncoder().encode(tabGroups) {
            UserDefaults.standard.set(data, forKey: savedGroupsKey)
        }
    }

    private func restoreTabs() {
        if let data = UserDefaults.standard.data(forKey: savedTabsKey),
           let restored = try? JSONDecoder().decode([BrowserTab].self, from: data), !restored.isEmpty {
            tabs = restored
            activeTabId = restored.first?.id ?? ""
        }
        if let data = UserDefaults.standard.data(forKey: savedGroupsKey),
           let groups = try? JSONDecoder().decode([TabGroup].self, from: data) {
            tabGroups = groups
        }
    }

    // MARK: - Memory Management

    func suspendInactiveTabs() {
        let tenMinutesAgo = Date().addingTimeInterval(-600)
        for tab in tabs where tab.id != activeTabId && tab.lastAccessTime < tenMinutesAgo && !tab.isPinned {
            tab.isSuspended = true
            tab.webView?.stopLoading()
            tab.webView = nil
        }
    }
}
