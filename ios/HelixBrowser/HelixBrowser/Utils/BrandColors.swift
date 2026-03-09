import UIKit

struct BrandColors {
    static let background = UIColor(hex: "#0A091E")
    static let toolbar = UIColor(hex: "#141235")
    static let addressBar = UIColor(hex: "#1E1B45")
    static let textPrimary = UIColor.white
    static let textSecondary = UIColor(hex: "#A0A0D0")
    static let accentPurpleUI = UIColor(hex: "#8B8BFF")
    static let accentPinkUI = UIColor(hex: "#FF7EB3")
    static let secureGreen = UIColor(hex: "#00E676")

    static let accentPurpleCG = CGColor(red: 0x8B/255, green: 0x8B/255, blue: 0xFF/255, alpha: 1)
    static let accentPinkCG = CGColor(red: 0xFF/255, green: 0x7E/255, blue: 0xB3/255, alpha: 1)

    static var gradientLayer: CAGradientLayer {
        let layer = CAGradientLayer()
        layer.colors = [accentPurpleCG, accentPinkCG]
        layer.startPoint = CGPoint(x: 0, y: 0)
        layer.endPoint = CGPoint(x: 1, y: 1)
        return layer
    }
}

extension UIColor {
    convenience init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default: (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, alpha: Double(a) / 255)
    }
}
