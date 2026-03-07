import Foundation

struct WebTab: Identifiable, Codable, Equatable {
    let id: UUID
    var url: String
    var title: String
    var faviconUrl: String?
    var isIncognito: Bool
    
    init(id: UUID = UUID(), url: String = "helix://start", title: String = "New Tab", faviconUrl: String? = nil, isIncognito: Bool = false) {
        self.id = id
        self.url = url
        self.title = title
        self.faviconUrl = faviconUrl
        self.isIncognito = isIncognito
    }
}
