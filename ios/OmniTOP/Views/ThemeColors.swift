import SwiftUI

extension Color {
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&int)
        let r, g, b: UInt64
        switch cleaned.count {
        case 6:
            (r, g, b) = ((int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (r, g, b) = (245, 158, 11)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue: Double(b) / 255.0,
            opacity: 1.0
        )
    }
}

enum OmniTheme {
    static let accent = Color(hex: "#f59e0b")
    static let liveGreen = Color(hex: "#10b981")
    static let nextBlue = Color(hex: "#3b82f6")
    static let unmarkedRed = Color(hex: "#ef4444")

    static func background(isDark: Bool) -> Color {
        isDark ? Color(hex: "#0d1117") : Color(hex: "#f8fafc")
    }

    static func surface(isDark: Bool) -> Color {
        isDark ? Color(hex: "#161b22") : Color.white
    }

    static func surfaceSecondary(isDark: Bool) -> Color {
        isDark ? Color(hex: "#21262d") : Color(hex: "#f1f5f9")
    }

    static func textPrimary(isDark: Bool) -> Color {
        isDark ? Color(hex: "#f0f6fc") : Color(hex: "#0f172a")
    }

    static func textSecondary(isDark: Bool) -> Color {
        isDark ? Color(hex: "#8b949e") : Color(hex: "#64748b")
    }
}
