import Foundation

struct RobotUser: Codable, Identifiable, Hashable {
    let userId: String
    let name: String
    let contact: String
    let role: String

    var id: String { userId }
}

struct SelfhostXiaozhiBundle: Codable, Hashable {
    let provider: String
    let otaURL: String
    let activateURL: String
    let websocketURL: String
    let mcpEndpointBase: String
}

struct MobileProfile: Codable, Hashable {
    let brand: String
    let panelURL: String
    let mcpEndpointBase: String
    let websocketURL: String
    let portalURL: String
    let robotWifiPrefixes: [String]
    let preferredBackendMode: String
}

struct OnboardingBundle: Codable, Hashable {
    let code: String
    let panelURL: String
    let panelClientToken: String
    let robotId: String
    let robotName: String
    let cloudEndpoint: String
    let planCode: String
    let planName: String
    let billingStart: String
    let billingEnd: String
    let paymentStatus: String
    let ownerName: String
    let ownerEmail: String
    let ownerPhone: String
    let ownerCompany: String
    let ownerContact: String
    let ownerComment: String
    let instruction: String
    let users: [RobotUser]
    let selfhostXiaozhi: SelfhostXiaozhiBundle?
    let mobileProfile: MobileProfile?
}

struct RobotRuntimeSnapshot: Codable, Hashable {
    let robotId: String
    let connected: Bool
    let mode: String
    let transportState: String
    let target: String
    let localHost: String
    let connectivityEvidence: String
    let verifiedNow: Bool
    let freshDeviceContact: Bool
    let lastSeenISO: String
    let boardName: String
    let appVersion: String
}

struct OnboardingDraft: Codable, Hashable {
    var panelBaseURL: String = AppConfig.panelBaseURL
    var hubBaseURL: String = AppConfig.defaultHubBaseURL
    var robotId: String = ""
    var robotName: String = ""
    var token: String = ""
    var robotHost: String = ""
    var robotPort: Int = 8080
    var robotPath: String = "/ws"
    var cloudEndpoint: String = ""
    var ownerName: String = ""
    var ownerEmail: String = ""
    var ownerPhone: String = ""
    var clientCompany: String = ""
    var clientContact: String = ""
    var clientComment: String = ""
    var planCode: String = "start"
    var planName: String = ""
    var billingStart: String = ""
    var billingEnd: String = ""
    var paymentStatus: String = "trial"
    var subscriptionNote: String = ""
    var onboardingCode: String = ""
    var panelClientToken: String = ""
    var wifiReconnectPending: Bool = false
    var hasCompletedRuntimeConfirmation: Bool = false
    var mobileBrand: String = "GOSHA"
    var portalURL: String = AppConfig.robotPortalURL
    var mobileWebsocketURL: String = ""
    var preferredBackendMode: String = ""
    var robotWifiPrefixesCSV: String = AppConfig.robotSSIDPrefixes.joined(separator: ",")

    var hasContactDetails: Bool {
        [ownerName, ownerEmail, ownerPhone].contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    var robotWiFiPrefixes: [String] {
        let values = robotWifiPrefixesCSV
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return values.isEmpty ? AppConfig.robotSSIDPrefixes : values
    }

    var primaryRobotWiFiPrefix: String {
        robotWiFiPrefixes.first ?? AppConfig.primaryRobotSSIDPrefix
    }

    var robotWiFiDisplayHint: String {
        primaryRobotWiFiPrefix + "*"
    }

    mutating func apply(bundle: OnboardingBundle) {
        onboardingCode = bundle.code.isEmpty ? onboardingCode : bundle.code
        panelBaseURL = bundle.mobileProfile?.panelURL.ifEmpty(bundle.panelURL).ifEmpty(panelBaseURL) ?? panelBaseURL
        panelClientToken = bundle.panelClientToken.isEmpty ? panelClientToken : bundle.panelClientToken
        robotId = bundle.robotId
        robotName = bundle.robotName
        cloudEndpoint = bundle.cloudEndpoint
        planCode = bundle.planCode
        planName = bundle.planName
        billingStart = bundle.billingStart
        billingEnd = bundle.billingEnd
        paymentStatus = bundle.paymentStatus
        ownerName = bundle.ownerName.isEmpty ? ownerName : bundle.ownerName
        ownerEmail = bundle.ownerEmail.isEmpty ? ownerEmail : bundle.ownerEmail
        ownerPhone = bundle.ownerPhone.isEmpty ? ownerPhone : bundle.ownerPhone
        clientCompany = bundle.ownerCompany.isEmpty ? clientCompany : bundle.ownerCompany
        clientContact = bundle.ownerContact.isEmpty ? clientContact : bundle.ownerContact
        clientComment = bundle.ownerComment.isEmpty ? clientComment : bundle.ownerComment

        if let mobileProfile = bundle.mobileProfile {
            mobileBrand = mobileProfile.brand.isEmpty ? mobileBrand : mobileProfile.brand
            portalURL = mobileProfile.portalURL.isEmpty ? portalURL : mobileProfile.portalURL
            mobileWebsocketURL = mobileProfile.websocketURL.isEmpty ? mobileWebsocketURL : mobileProfile.websocketURL
            preferredBackendMode = mobileProfile.preferredBackendMode.isEmpty ? preferredBackendMode : mobileProfile.preferredBackendMode
            if !mobileProfile.robotWifiPrefixes.isEmpty {
                robotWifiPrefixesCSV = mobileProfile.robotWifiPrefixes.joined(separator: ",")
            }
            if !mobileProfile.mcpEndpointBase.isEmpty {
                hubBaseURL = mobileProfile.mcpEndpointBase
            }
        }

        if let selfhost = bundle.selfhostXiaozhi {
            if !selfhost.mcpEndpointBase.isEmpty {
                hubBaseURL = selfhost.mcpEndpointBase
            }
            if !selfhost.websocketURL.isEmpty {
                mobileWebsocketURL = selfhost.websocketURL
            }
        }

        if hubBaseURL.isEmpty, !bundle.cloudEndpoint.isEmpty {
            hubBaseURL = bundle.cloudEndpoint.split(separator: "?").first.map(String.init) ?? bundle.cloudEndpoint
        }
    }
}

struct PresentedDocument: Identifiable, Hashable {
    let id: String
    let title: String
    let subtitle: String
    let url: URL
}

private extension String {
    func ifEmpty(_ fallback: @autoclosure () -> String) -> String {
        isEmpty ? fallback() : self
    }
}
