import SwiftUI

struct BrandColors {
    static let background = Color(hex: "#0A091E")
    static let toolbar = Color(hex: "#141235")
    static let addressBar = Color(hex: "#1E1B45")
    static let textPrimary = Color(hex: "#FFFFFF")
    static let textSecondary = Color(hex: "#A0A0D0")
    static let accentPurple = Color(hex: "#8B8BFF")
    static let accentPink = Color(hex: "#FF7EB3")
    static let secureGreen = Color(hex: "#00E676")
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default: (a, r, g, b) = (255, 1, 1, 1)
        }
        self.init(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: Double(a) / 255)
    }
}
