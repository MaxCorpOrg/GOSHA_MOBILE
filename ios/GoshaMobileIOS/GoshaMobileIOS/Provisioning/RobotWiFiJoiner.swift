import Foundation
import NetworkExtension

enum RobotWiFiJoiner {
    enum WiFiError: LocalizedError {
        case invalidPortalURL
        case configurationFailed(String)

        var errorDescription: String? {
            switch self {
            case .invalidPortalURL:
                return "Некорректный локальный адрес портала робота."
            case .configurationFailed(let message):
                return message
            }
        }
    }

    static func joinRobotNetwork() async throws {
        let prefixes = AppConfig.robotSSIDPrefixes
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !prefixes.isEmpty else {
            throw WiFiError.configurationFailed("В приложении не настроены префиксы Wi-Fi робота.")
        }

        var lastError: Error?
        for prefix in prefixes {
            do {
                try await join(prefix: prefix)
                return
            } catch {
                lastError = error
            }
        }

        if let lastError = lastError {
            throw lastError
        }
    }

    private static func join(prefix: String) async throws {
        let configuration = NEHotspotConfiguration(ssidPrefix: prefix)
        configuration.joinOnce = true

        let _: Void = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            NEHotspotConfigurationManager.shared.apply(configuration) { error in
                if let nsError = error as NSError? {
                    if
                        nsError.domain == NEHotspotConfigurationErrorDomain,
                        nsError.code == NEHotspotConfigurationError.alreadyAssociated.rawValue
                    {
                        continuation.resume(returning: ())
                        return
                    }
                    continuation.resume(throwing: WiFiError.configurationFailed(nsError.localizedDescription))
                    return
                }
                continuation.resume(returning: ())
            }
        }
    }

    static func currentSSID() async -> String {
        await withCheckedContinuation { continuation in
            NEHotspotNetwork.fetchCurrent { network in
                continuation.resume(returning: network?.ssid ?? "")
            }
        }
    }

    static func portalURL() throws -> URL {
        guard let url = URL(string: AppConfig.robotPortalURL) else {
            throw WiFiError.invalidPortalURL
        }
        return url
    }
}
