import Foundation

enum AppConfig {
    private static func stringValue(for key: String, fallback: String) -> String {
        let raw = Bundle.main.object(forInfoDictionaryKey: key) as? String
        let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? fallback : trimmed
    }

    private static func arrayValue(for key: String, fallback: [String]) -> [String] {
        if let values = Bundle.main.object(forInfoDictionaryKey: key) as? [String] {
            let normalized = values
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            if !normalized.isEmpty {
                return normalized
            }
        }
        if let csv = Bundle.main.object(forInfoDictionaryKey: key) as? String {
            let normalized = csv
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            if !normalized.isEmpty {
                return normalized
            }
        }
        return fallback
    }

    static let panelBaseURL = stringValue(for: "GoshaPanelBaseURL", fallback: "http://151.241.228.232:18876")
    static let privacyPolicyURL = stringValue(for: "GoshaPrivacyPolicyURL", fallback: "http://151.241.228.232:18876/gosha/privacy")
    static let termsOfUseURL = stringValue(for: "GoshaTermsOfUseURL", fallback: "http://151.241.228.232:18876/gosha/terms")
    static let robotPortalURL = stringValue(for: "GoshaRobotPortalURL", fallback: "http://192.168.4.1")
    static let robotSSIDPrefixes = arrayValue(for: "GoshaRobotSSIDPrefixes", fallback: ["GOSHA-", "Xiaozhi-"])
    static let primaryRobotSSIDPrefix = robotSSIDPrefixes.first ?? "GOSHA-"
    static let defaultHubBaseURL = "ws://151.241.228.232:18080/mcp"
}
