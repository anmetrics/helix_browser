import Foundation

/// Lightweight data manager using UserDefaults + JSON for history, bookmarks, and downloads
/// (No Core Data dependency needed for this scope)
class DataManager {
    static let shared = DataManager()

    private let historyKey = "helix_history"
    private let bookmarksKey = "helix_bookmarks"
    private let downloadsKey = "helix_downloads"
    private let maxHistory = 5000

    private let defaults = UserDefaults.standard

    // MARK: - History

    func addHistory(url: String, title: String) {
        var items = getHistory()
        // Remove duplicate if exists
        items.removeAll { $0["url"] == url }
        let entry: [String: String] = [
            "url": url,
            "title": title,
            "timestamp": String(Date().timeIntervalSince1970)
        ]
        items.insert(entry, at: 0)
        if items.count > maxHistory {
            items = Array(items.prefix(maxHistory))
        }
        saveHistory(items)
    }

    func getHistory() -> [[String: String]] {
        guard let data = defaults.data(forKey: historyKey),
              let items = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return []
        }
        return items
    }

    func deleteHistoryItem(url: String) {
        var items = getHistory()
        items.removeAll { $0["url"] == url }
        saveHistory(items)
    }

    func clearHistory() {
        defaults.removeObject(forKey: historyKey)
    }

    func searchHistory(query: String) -> [[String: String]] {
        let q = query.lowercased()
        return getHistory().filter {
            ($0["title"] ?? "").lowercased().contains(q) ||
            ($0["url"] ?? "").lowercased().contains(q)
        }
    }

    private func saveHistory(_ items: [[String: String]]) {
        if let data = try? JSONEncoder().encode(items) {
            defaults.set(data, forKey: historyKey)
        }
    }

    // MARK: - Bookmarks

    func addBookmark(url: String, title: String) -> Bool {
        var items = getBookmarks()
        if items.contains(where: { $0["url"] == url }) { return false }
        let entry: [String: String] = [
            "url": url,
            "title": title,
            "created_at": String(Date().timeIntervalSince1970)
        ]
        items.insert(entry, at: 0)
        saveBookmarks(items)
        return true
    }

    func removeBookmark(url: String) {
        var items = getBookmarks()
        items.removeAll { $0["url"] == url }
        saveBookmarks(items)
    }

    func isBookmarked(url: String) -> Bool {
        return getBookmarks().contains { $0["url"] == url }
    }

    func getBookmarks() -> [[String: String]] {
        guard let data = defaults.data(forKey: bookmarksKey),
              let items = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return []
        }
        return items
    }

    func searchBookmarks(query: String) -> [[String: String]] {
        let q = query.lowercased()
        return getBookmarks().filter {
            ($0["title"] ?? "").lowercased().contains(q) ||
            ($0["url"] ?? "").lowercased().contains(q)
        }
    }

    func clearBookmarks() {
        defaults.removeObject(forKey: bookmarksKey)
    }

    private func saveBookmarks(_ items: [[String: String]]) {
        if let data = try? JSONEncoder().encode(items) {
            defaults.set(data, forKey: bookmarksKey)
        }
    }

    // MARK: - Downloads

    func addDownload(url: String, filename: String, filesize: Int64 = 0) {
        var items = getDownloads()
        let entry: [String: String] = [
            "url": url,
            "filename": filename,
            "filesize": String(filesize),
            "status": "downloading",
            "created_at": String(Date().timeIntervalSince1970)
        ]
        items.insert(entry, at: 0)
        saveDownloads(items)
    }

    func updateDownloadStatus(url: String, status: String) {
        var items = getDownloads()
        if let idx = items.firstIndex(where: { $0["url"] == url }) {
            items[idx]["status"] = status
            if status == "completed" || status == "failed" {
                items[idx]["completed_at"] = String(Date().timeIntervalSince1970)
            }
        }
        saveDownloads(items)
    }

    func getDownloads() -> [[String: String]] {
        guard let data = defaults.data(forKey: downloadsKey),
              let items = try? JSONDecoder().decode([[String: String]].self, from: data) else {
            return []
        }
        return items
    }

    func clearDownloads() {
        defaults.removeObject(forKey: downloadsKey)
    }

    private func saveDownloads(_ items: [[String: String]]) {
        if let data = try? JSONEncoder().encode(items) {
            defaults.set(data, forKey: downloadsKey)
        }
    }

    // MARK: - Clear All Data

    func clearAllData() {
        clearHistory()
        clearBookmarks()
        clearDownloads()
    }
}
