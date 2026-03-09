import Foundation
import WebKit
import UIKit

class BrowserTab: Codable {
    let id: String
    var url: String
    var title: String
    var faviconUrl: String?
    var isIncognito: Bool
    var isPinned: Bool
    var groupId: String?
    var groupName: String?
    var lastAccessTime: Date
    var isMuted: Bool
    var isSuspended: Bool

    // Non-codable properties
    var webView: WKWebView?
    var thumbnail: UIImage?

    enum CodingKeys: String, CodingKey {
        case id, url, title, faviconUrl, isIncognito, isPinned, groupId, groupName, lastAccessTime, isMuted, isSuspended
    }

    init(url: String = "helix://start", title: String = "Tab mới", isIncognito: Bool = false) {
        self.id = UUID().uuidString
        self.url = url
        self.title = title
        self.isIncognito = isIncognito
        self.isPinned = false
        self.lastAccessTime = Date()
        self.isMuted = false
        self.isSuspended = false
    }

    required init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        url = try c.decode(String.self, forKey: .url)
        title = try c.decode(String.self, forKey: .title)
        faviconUrl = try c.decodeIfPresent(String.self, forKey: .faviconUrl)
        isIncognito = try c.decodeIfPresent(Bool.self, forKey: .isIncognito) ?? false
        isPinned = try c.decodeIfPresent(Bool.self, forKey: .isPinned) ?? false
        groupId = try c.decodeIfPresent(String.self, forKey: .groupId)
        groupName = try c.decodeIfPresent(String.self, forKey: .groupName)
        lastAccessTime = try c.decodeIfPresent(Date.self, forKey: .lastAccessTime) ?? Date()
        isMuted = try c.decodeIfPresent(Bool.self, forKey: .isMuted) ?? false
        isSuspended = try c.decodeIfPresent(Bool.self, forKey: .isSuspended) ?? false
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(url, forKey: .url)
        try c.encode(title, forKey: .title)
        try c.encodeIfPresent(faviconUrl, forKey: .faviconUrl)
        try c.encode(isIncognito, forKey: .isIncognito)
        try c.encode(isPinned, forKey: .isPinned)
        try c.encodeIfPresent(groupId, forKey: .groupId)
        try c.encodeIfPresent(groupName, forKey: .groupName)
        try c.encode(lastAccessTime, forKey: .lastAccessTime)
        try c.encode(isMuted, forKey: .isMuted)
        try c.encode(isSuspended, forKey: .isSuspended)
    }
}

struct TabGroup: Codable {
    let id: String
    var name: String
    var colorHex: String

    init(name: String, colorHex: String = "#8B8BFF") {
        self.id = UUID().uuidString
        self.name = name
        self.colorHex = colorHex
    }
}
