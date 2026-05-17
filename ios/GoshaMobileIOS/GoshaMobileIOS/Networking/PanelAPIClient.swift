import Foundation

struct PanelAPIClient {
    private typealias JSON = [String: Any]

    enum APIError: LocalizedError {
        case invalidResponse
        case invalidURL(String)
        case invalidPayload
        case backend(String)

        var errorDescription: String? {
            switch self {
            case .invalidResponse:
                return "Панель вернула непонятный ответ."
            case .invalidURL(let raw):
                return "Некорректный URL панели: \(raw)"
            case .invalidPayload:
                return "Не удалось разобрать данные панели."
            case .backend(let message):
                return message
            }
        }
    }

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func resolveCode(baseURL: String, code: String) async throws -> OnboardingBundle {
        let root = try await request(
            baseURL: baseURL,
            path: "/api/mobile/resolve-code",
            method: "POST",
            body: ["code": code]
        )
        guard bool(root["ok"]) else {
            throw APIError.backend(string(root["error"]).ifEmpty("Не удалось обработать код"))
        }
        return try parseBundle(root["bundle"])
    }

    func activateCode(
        baseURL: String,
        code: String,
        ownerName: String,
        ownerEmail: String,
        ownerPhone: String
    ) async throws -> OnboardingBundle {
        let root = try await request(
            baseURL: baseURL,
            path: "/api/mobile/activate-code",
            method: "POST",
            body: [
                "code": code,
                "owner": [
                    "name": ownerName,
                    "email": ownerEmail,
                    "phone": ownerPhone,
                ],
            ]
        )
        guard bool(root["ok"]) else {
            throw APIError.backend(string(root["error"]).ifEmpty("Не удалось активировать код"))
        }
        return try parseBundle(root["bundle"])
    }

    func fetchRobotRuntime(
        baseURL: String,
        robotId: String,
        panelClientToken: String,
        onboardingCode: String
    ) async throws -> RobotRuntimeSnapshot? {
        let root = try await request(
            baseURL: baseURL,
            path: "/api/mobile/robots/\(robotId)/runtime",
            method: "GET",
            headers: mobileHeaders(panelClientToken: panelClientToken, onboardingCode: onboardingCode)
        )
        guard bool(root["ok"]) else {
            throw APIError.backend(string(root["error"]).ifEmpty("Не удалось загрузить состояние робота"))
        }

        let item = object(root["data"]) ?? firstRobot(in: array(root["robots"]), robotId: robotId)
        guard let item = item else {
            return nil
        }
        return parseRobotRuntimeSnapshot(item, robotId: robotId)
    }

    private func request(
        baseURL: String,
        path: String,
        method: String,
        body: Any? = nil,
        headers: [String: String] = [:]
    ) async throws -> JSON {
        let normalizedBaseURL = normalizeBaseURL(baseURL)
        guard let url = URL(string: normalizedBaseURL + path) else {
            throw APIError.invalidURL(normalizedBaseURL + path)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }

        if let body = body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            if
                let json = try? JSONSerialization.jsonObject(with: data, options: []) as? JSON,
                let message = json["error"] as? String,
                !message.isEmpty
            {
                throw APIError.backend(message)
            }
            throw APIError.invalidResponse
        }

        guard let json = try JSONSerialization.jsonObject(with: data, options: []) as? JSON else {
            throw APIError.invalidPayload
        }
        return json
    }

    private func parseBundle(_ raw: Any?) throws -> OnboardingBundle {
        guard let bundle = object(raw) else {
            throw APIError.invalidPayload
        }

        let subscription = object(bundle["subscription"]) ?? [:]
        let owner = object(bundle["owner"]) ?? [:]
        let billing = object(subscription["billing"]) ?? [:]
        let users = array(bundle["users"]).compactMap(parseUser)

        return OnboardingBundle(
            code: string(bundle["code"]),
            panelURL: string(bundle["panel_url"]),
            panelClientToken: string(bundle["panel_client_token"]),
            robotId: string(bundle["robot_id"]),
            robotName: string(bundle["robot_name"]).ifEmpty(string(bundle["robot_id"])),
            cloudEndpoint: string(bundle["cloud_endpoint"]),
            planCode: string(subscription["plan_code"]).ifEmpty("start"),
            planName: string(subscription["plan_name"]).ifEmpty("Старт"),
            billingStart: string(billing["start_date"]),
            billingEnd: string(billing["end_date"]),
            paymentStatus: string(billing["payment_status"]),
            ownerName: string(owner["name"]),
            ownerEmail: string(owner["email"]),
            ownerPhone: string(owner["phone"]),
            ownerCompany: string(owner["company"]),
            ownerContact: string(owner["contact"]),
            ownerComment: string(owner["comment"]),
            instruction: string(bundle["instruction"]),
            users: users,
            selfhostXiaozhi: parseSelfhostXiaozhiBundle(bundle["selfhost_xiaozhi"]),
            mobileProfile: parseMobileProfile(bundle["mobile_profile"])
        )
    }

    private func parseUser(_ raw: Any) -> RobotUser? {
        guard let item = object(raw) else {
            return nil
        }
        return RobotUser(
            userId: string(item["user_id"]),
            name: string(item["name"]),
            contact: string(item["contact"]),
            role: string(item["role"]).ifEmpty("client")
        )
    }

    private func parseSelfhostXiaozhiBundle(_ raw: Any?) -> SelfhostXiaozhiBundle? {
        guard let item = object(raw) else {
            return nil
        }
        return SelfhostXiaozhiBundle(
            provider: string(item["provider"]),
            otaURL: string(item["ota_url"]),
            activateURL: string(item["activate_url"]),
            websocketURL: string(item["websocket_url"]),
            mcpEndpointBase: string(item["mcp_endpoint_base"])
        )
    }

    private func parseMobileProfile(_ raw: Any?) -> MobileProfile? {
        guard let item = object(raw) else {
            return nil
        }
        let robotWifiPrefixes: [String] = array(item["robot_wifi_prefixes"]).compactMap { value in
            let normalized = string(value).trimmingCharacters(in: .whitespacesAndNewlines)
            return normalized.isEmpty ? nil : normalized
        }
        return MobileProfile(
            brand: string(item["brand"]),
            panelURL: string(item["panel_url"]),
            mcpEndpointBase: string(item["mcp_endpoint_base"]),
            websocketURL: string(item["websocket_url"]),
            portalURL: string(item["portal_url"]),
            robotWifiPrefixes: robotWifiPrefixes,
            preferredBackendMode: string(item["preferred_backend_mode"])
        )
    }

    private func parseRobotRuntimeSnapshot(_ item: JSON, robotId: String) -> RobotRuntimeSnapshot {
        let diagnostics = object(item["diagnostics"]) ?? [:]
        let control = object(item["control"]) ?? [:]
        let cloudConsole = object(item["cloud_console"]) ?? [:]
        let connectivity = object(item["connectivity"]) ?? [:]
        let detection = object(item["detection"]) ?? [:]

        return buildRobotRuntimeSnapshot(
            robotId: robotId,
            diagnosticsTarget: string(diagnostics["target"]),
            fallbackWSURL: string(control["fallback_ws_url"]),
            diagnosticsMode: string(diagnostics["mode"]),
            controlTransport: string(control["transport"]),
            transportState: string(diagnostics["transport_state"]),
            connectivityHasConnected: connectivity["connected"] != nil,
            connectivityConnected: bool(connectivity["connected"]),
            connectivityLocalHost: string(connectivity["local_host"]),
            connectivityEvidence: string(connectivity["evidence"]),
            connectivityVerifiedNow: bool(connectivity["verified_now"]) || bool(detection["verified_now"]),
            connectivityFreshDeviceContact: bool(connectivity["fresh_device_contact"]),
            connectivityLastSeenISO: string(connectivity["last_seen_iso"]),
            connectivityBoardName: string(connectivity["board_name"]),
            connectivityAppVersion: string(connectivity["app_version"]),
            cloudLastSeenISO: string(cloudConsole["last_seen_iso"]),
            cloudBoardName: string(cloudConsole["board_name"]),
            cloudAppVersion: string(cloudConsole["app_version"])
        )
    }

    private func buildRobotRuntimeSnapshot(
        robotId: String,
        diagnosticsTarget: String,
        fallbackWSURL: String,
        diagnosticsMode: String,
        controlTransport: String,
        transportState: String,
        connectivityHasConnected: Bool,
        connectivityConnected: Bool,
        connectivityLocalHost: String,
        connectivityEvidence: String,
        connectivityVerifiedNow: Bool,
        connectivityFreshDeviceContact: Bool,
        connectivityLastSeenISO: String,
        connectivityBoardName: String,
        connectivityAppVersion: String,
        cloudLastSeenISO: String,
        cloudBoardName: String,
        cloudAppVersion: String
    ) -> RobotRuntimeSnapshot {
        let target = diagnosticsTarget.ifEmpty(fallbackWSURL)
        let mode = diagnosticsMode.ifEmpty(controlTransport)
        let directLocalHost = parseLocalHost(target)
        let normalizedConnectivityLocalHost = directRobotHostOrBlank(connectivityLocalHost)
        let localHost = normalizedConnectivityLocalHost.ifEmpty(directLocalHost)

        let connected: Bool
        if !localHost.isEmpty {
            connected = true
        } else if connectivityHasConnected {
            connected = connectivityConnected
        } else {
            connected = mode == "cloud-mcp" && transportState == "reachable"
        }

        return RobotRuntimeSnapshot(
            robotId: robotId,
            connected: connected,
            mode: mode,
            transportState: transportState,
            target: target,
            localHost: localHost,
            connectivityEvidence: connectivityEvidence,
            verifiedNow: connectivityVerifiedNow,
            freshDeviceContact: connectivityFreshDeviceContact,
            lastSeenISO: connectivityLastSeenISO.ifEmpty(cloudLastSeenISO),
            boardName: connectivityBoardName.ifEmpty(cloudBoardName),
            appVersion: connectivityAppVersion.ifEmpty(cloudAppVersion)
        )
    }

    private func firstRobot(in robots: [Any], robotId: String) -> JSON? {
        robots.compactMap(object).first { string($0["robot_id"]) == robotId }
    }

    private func normalizeBaseURL(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return AppConfig.panelBaseURL
        }
        if trimmed.contains("://") {
            return trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
        return "http://" + trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func mobileHeaders(panelClientToken: String, onboardingCode: String) -> [String: String] {
        var headers: [String: String] = [:]
        if !panelClientToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            headers["X-Mobile-Token"] = panelClientToken
        }
        if !onboardingCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            headers["X-Mobile-Code"] = onboardingCode
        }
        return headers
    }

    private func parseLocalHost(_ rawURL: String) -> String {
        guard let components = URLComponents(string: rawURL.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return ""
        }
        guard let host = components.host, !host.isEmpty else {
            return ""
        }
        let path = components.path.isEmpty ? "/" : components.path
        if path != "/ws" {
            return ""
        }
        return directRobotHostOrBlank(host)
    }

    private func directRobotHostOrBlank(_ host: String) -> String {
        let normalized = host.lowercased()
        if normalized.isEmpty || normalized == "localhost" || normalized == "0.0.0.0" || normalized.hasPrefix("127.") {
            return ""
        }
        if normalized.hasSuffix(".local") || normalized.hasPrefix("10.") || normalized.hasPrefix("192.168.") {
            return host
        }
        if normalized.hasPrefix("172.") {
            let pieces = normalized.split(separator: ".")
            if pieces.count > 1, let second = Int(pieces[1]), (16...31).contains(second) {
                return host
            }
        }
        return ""
    }

    private func object(_ raw: Any?) -> JSON? {
        raw as? JSON
    }

    private func array(_ raw: Any?) -> [Any] {
        raw as? [Any] ?? []
    }

    private func string(_ raw: Any?) -> String {
        if let value = raw as? String {
            return value
        }
        if let value = raw {
            return String(describing: value)
        }
        return ""
    }

    private func bool(_ raw: Any?) -> Bool {
        raw as? Bool ?? false
    }
}

private extension String {
    func ifEmpty(_ fallback: @autoclosure () -> String) -> String {
        isEmpty ? fallback() : self
    }
}
