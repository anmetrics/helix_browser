import Foundation

struct WebTab: Identifiable, Codable, Equatable {
    let id: UUID
    var url: String
    var title: String
    var faviconUrl: String?
    var isIncognito: Bool
    var isPinned: Bool
    var groupId: UUID?
    var groupName: String?
    var lastAccessTime: Date
    var isMuted: Bool
    var isSuspended: Bool

    init(id: UUID = UUID(), url: String = "helix://start", title: String = "New Tab", faviconUrl: String? = nil, isIncognito: Bool = false, isPinned: Bool = false, groupId: UUID? = nil, groupName: String? = nil) {
        self.id = id
        self.url = url
        self.title = title
        self.faviconUrl = faviconUrl
        self.isIncognito = isIncognito
        self.isPinned = isPinned
        self.groupId = groupId
        self.groupName = groupName
        self.lastAccessTime = Date()
        self.isMuted = false
        self.isSuspended = false
    }
}

struct TabGroup: Identifiable, Codable, Equatable {
    let id: UUID
    var name: String
    var colorHex: String

    init(id: UUID = UUID(), name: String, colorHex: String = "#8B8BFF") {
        self.id = id
        self.name = name
        self.colorHex = colorHex
    }
}
