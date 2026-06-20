import Foundation
import WebKit
import UIKit

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
    private let savedActiveTabKey = "helix_saved_active_tab"
    private let savedInteractionStatesKey = "helix_saved_interaction_states"

    /// Persisted WKWebView `interactionState` blobs keyed by tab id, used to restore
    /// back/forward history and scroll position after process death. Captured in
    /// `saveTabs()` from each live (non-incognito) WebView and applied at WebView
    /// re-creation time via `interactionState(forTabId:)`.
    private var interactionStates: [String: Data] = [:]

    private var memoryWarningObserver: NSObjectProtocol?

    private init() {
        if Prefs.shared.isRestoreTabsEnabled {
            restoreTabs()
        }
        if tabs.isEmpty {
            let tab = BrowserTab()
            tabs.append(tab)
            activeTabId = tab.id
        }
        memoryWarningObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.suspendInactiveTabs()
        }
    }

    deinit {
        if let observer = memoryWarningObserver {
            NotificationCenter.default.removeObserver(observer)
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
        interactionStates[id] = nil
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

        // Persist the currently active tab id so it can be re-activated on relaunch
        // instead of always falling back to the first tab.
        UserDefaults.standard.set(activeTabId, forKey: savedActiveTabKey)

        captureInteractionStates(for: saveable)
    }

    /// Captures each live (non-incognito) WebView's navigation/scroll `interactionState`
    /// so back/forward history and scroll position survive process death. The opaque
    /// state object is securely archived to `Data` for persistence.
    private func captureInteractionStates(for saveable: [BrowserTab]) {
        guard #available(iOS 15.0, *) else { return }
        for tab in saveable {
            guard let state = tab.webView?.interactionState else { continue }
            if let data = try? NSKeyedArchiver.archivedData(withRootObject: state, requiringSecureCoding: true) {
                interactionStates[tab.id] = data
            }
        }
        // Drop states for tabs that no longer exist to avoid unbounded growth.
        let liveIds = Set(saveable.map { $0.id })
        interactionStates = interactionStates.filter { liveIds.contains($0.key) }
        UserDefaults.standard.set(interactionStates, forKey: savedInteractionStatesKey)
    }

    /// Returns the restorable WKWebView `interactionState` object for the given tab id,
    /// if one was persisted. Callers that create a fresh WebView for a restored tab should
    /// assign the result to `webView.interactionState` (iOS 15+) before falling back to a
    /// plain URL load, so back/forward history and scroll position are restored.
    @available(iOS 15.0, *)
    func interactionState(forTabId id: String) -> Any? {
        guard let data = interactionStates[id] else { return nil }
        guard let unarchived = try? NSKeyedUnarchiver.unarchiveTopLevelObjectWithData(data) else { return nil }
        return unarchived
    }

    private func restoreTabs() {
        if let data = UserDefaults.standard.data(forKey: savedTabsKey),
           let restored = try? JSONDecoder().decode([BrowserTab].self, from: data), !restored.isEmpty {
            tabs = restored
            // Re-activate the previously active tab if it still exists; otherwise fall
            // back to the first restored tab.
            let savedActiveId = UserDefaults.standard.string(forKey: savedActiveTabKey)
            if let savedActiveId = savedActiveId, restored.contains(where: { $0.id == savedActiveId }) {
                activeTabId = savedActiveId
            } else {
                activeTabId = restored.first?.id ?? ""
            }
        }
        if let data = UserDefaults.standard.data(forKey: savedGroupsKey),
           let groups = try? JSONDecoder().decode([TabGroup].self, from: data) {
            tabGroups = groups
        }
        if let stored = UserDefaults.standard.dictionary(forKey: savedInteractionStatesKey) as? [String: Data] {
            interactionStates = stored
        }
    }

    // MARK: - Memory Management

    func suspendInactiveTabs() {
        let tenMinutesAgo = Date().addingTimeInterval(-600)
        for tab in tabs where tab.id != activeTabId && tab.lastAccessTime < tenMinutesAgo && !tab.isPinned {
            // Capture the latest navigation/scroll state before discarding the WebView so
            // it can be restored on re-activation (non-incognito tabs only).
            if #available(iOS 15.0, *), !tab.isIncognito,
               let state = tab.webView?.interactionState,
               let data = try? NSKeyedArchiver.archivedData(withRootObject: state, requiringSecureCoding: true) {
                interactionStates[tab.id] = data
            }
            tab.isSuspended = true
            tab.webView?.stopLoading()
            tab.webView = nil
        }
        UserDefaults.standard.set(interactionStates, forKey: savedInteractionStatesKey)
    }
}
