import Foundation

struct UrlUtils {
    static func isUrl(_ input: String) -> Bool {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.hasPrefix("http://") ||
               trimmed.hasPrefix("https://") ||
               trimmed.hasPrefix("ftp://") ||
               (trimmed.contains(".") && !trimmed.contains(" "))
    }
    
    static func formatUrl(_ input: String) -> String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "helix://start" }
        if trimmed.hasPrefix("helix://") { return trimmed }
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return trimmed
        }
        if trimmed.hasPrefix("file://") || trimmed.hasPrefix("about:") {
            return trimmed
        }
        if isUrl(trimmed) {
            return "https://\(trimmed)"
        }
        return buildSearchQuery(trimmed, engine: Prefs.shared.searchEngine)
    }
    
    static func buildSearchQuery(_ query: String, engine: String) -> String {
        let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        switch engine.lowercased() {
        case "bing":
            return "https://www.bing.com/search?q=\(encoded)"
        case "duckduckgo":
            return "https://duckduckgo.com/?q=\(encoded)"
        case "yahoo":
            return "https://search.yahoo.com/search?p=\(encoded)"
        case "brave":
            return "https://search.brave.com/search?q=\(encoded)"
        default:
            return "https://www.google.com/search?q=\(encoded)"
        }
    }
    
    static func getDisplayUrl(_ url: String) -> String {
        guard let host = URL(string: url)?.host else { return url }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }
    
    static func getFaviconUrl(_ url: String) -> String {
        guard let host = URL(string: url)?.host else { return "" }
        return "https://www.google.com/s2/favicons?domain=\(host)&sz=64"
    }
    
    static func isSearchQuery(_ input: String) -> Bool {
        return !isUrl(input)
    }
}
