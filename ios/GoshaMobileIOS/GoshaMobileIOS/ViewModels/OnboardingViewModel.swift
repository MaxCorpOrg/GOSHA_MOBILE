import Foundation
import SwiftUI

@MainActor
final class OnboardingViewModel: ObservableObject {
    enum Step: String {
        case welcome
        case registration
        case wifi
        case menu
    }

    @Published var draft: OnboardingDraft
    @Published var bundle: OnboardingBundle?
    @Published var runtimeSnapshot: RobotRuntimeSnapshot?
    @Published var currentStep: Step = .welcome
    @Published var busyMessage = ""
    @Published var statusMessage = "Введите код подключения, который был выдан для вашего робота."
    @Published var errorMessage: String?
    @Published var currentSSID = ""
    @Published var didAcceptPrivacyConsent = false
    @Published var presentedDocument: PresentedDocument?

    var isBusy: Bool { !busyMessage.isEmpty }
    var shouldShowProductShell: Bool { draft.hasCompletedRuntimeConfirmation }
    var robotWiFiHint: String { draft.robotWiFiDisplayHint }

    private let store: OnboardingDraftStore
    private let apiClient: PanelAPIClient

    init(
        store: OnboardingDraftStore = OnboardingDraftStore(),
        apiClient: PanelAPIClient = PanelAPIClient()
    ) {
        self.store = store
        self.apiClient = apiClient
        self.draft = store.load()

        if draft.hasCompletedRuntimeConfirmation {
            currentStep = .menu
            statusMessage = "Робот уже был подтверждён. Можно открыть статус устройства, аккаунт и поддержку."
        } else if !draft.robotId.isEmpty {
            currentStep = .wifi
            statusMessage = "Черновик робота найден. Можно проверить состояние или продолжить Wi-Fi онбординг."
        } else if !draft.onboardingCode.isEmpty {
            currentStep = .registration
            statusMessage = "Код уже введён. Можно завершить активацию."
        }

        Task {
            await refreshCurrentSSID()
            if !draft.robotId.isEmpty {
                await refreshRobotRuntime(showBusy: false)
            }
        }
    }

    func updateDraft(_ mutate: (inout OnboardingDraft) -> Void) {
        mutate(&draft)
        store.save(draft)
    }

    func resetForNextRobot() {
        bundle = nil
        runtimeSnapshot = nil
        didAcceptPrivacyConsent = false
        draft = store.resetForNextRobot(from: draft)
        currentStep = .welcome
        statusMessage = "Черновик очищен. Можно начать подключение следующего робота."
        errorMessage = nil
    }

    func openPrivacyPolicy() {
        guard let url = URL(string: AppConfig.privacyPolicyURL) else {
            errorMessage = "Не удалось открыть политику конфиденциальности."
            return
        }
        presentedDocument = PresentedDocument(
            id: "privacy",
            title: "Политика конфиденциальности",
            subtitle: "Документ открывается внутри приложения.",
            url: url
        )
    }

    func openTermsOfUse() {
        guard let url = URL(string: AppConfig.termsOfUseURL) else {
            errorMessage = "Не удалось открыть условия пользования."
            return
        }
        presentedDocument = PresentedDocument(
            id: "terms",
            title: "Условия пользования",
            subtitle: "Документ открывается внутри приложения.",
            url: url
        )
    }

    func openRobotPortal() {
        do {
            let url = try RobotWiFiJoiner.portalURL()
            presentedDocument = PresentedDocument(
                id: "portal",
                title: "Настройка Wi-Fi робота",
                subtitle: "Если портал не загрузился, сначала подключитесь к сети \(robotWiFiHint) или к переходной сети Xiaozhi-*.",
                url: url
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func resolveCode() async {
        errorMessage = nil
        let code = draft.onboardingCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else {
            errorMessage = "Введите код подключения."
            return
        }

        busyMessage = "Проверяю код подключения..."
        defer { busyMessage = "" }

        do {
            let bundle = try await apiClient.resolveCode(baseURL: draft.panelBaseURL, code: code)
            self.bundle = bundle
            updateDraft { draft in
                draft.apply(bundle: bundle)
                draft.onboardingCode = code
            }
            currentStep = .registration
            statusMessage = bundle.instruction.isEmpty
                ? "Код найден. Можно завершить активацию и перейти к Wi-Fi настройке."
                : bundle.instruction
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func activateCode() async {
        errorMessage = nil
        let code = draft.onboardingCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else {
            errorMessage = "Сначала введите код подключения."
            return
        }

        if draft.hasContactDetails && !didAcceptPrivacyConsent {
            errorMessage = "Если вы заполняете имя, почту или телефон, нужно подтвердить согласие на обработку этих данных."
            return
        }

        busyMessage = "Активирую код и сохраняю владельца..."
        defer { busyMessage = "" }

        do {
            let bundle = try await apiClient.activateCode(
                baseURL: draft.panelBaseURL,
                code: code,
                ownerName: draft.ownerName.trimmingCharacters(in: .whitespacesAndNewlines),
                ownerEmail: draft.ownerEmail.trimmingCharacters(in: .whitespacesAndNewlines),
                ownerPhone: draft.ownerPhone.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            self.bundle = bundle
            updateDraft { draft in
                draft.apply(bundle: bundle)
                draft.onboardingCode = code
            }
            currentStep = .wifi
            statusMessage = "Код активирован. Теперь подключите телефон к Wi-Fi робота \(robotWiFiHint), откройте встроенный портал и затем перепроверьте runtime."
            await refreshRobotRuntime(showBusy: false)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func refreshRobotRuntime(showBusy: Bool = true) async {
        errorMessage = nil
        guard !draft.robotId.isEmpty else {
            statusMessage = "Робот ещё не выбран. Сначала введите и активируйте код."
            return
        }

        if showBusy {
            busyMessage = "Проверяю, появился ли робот в сети..."
        }
        defer {
            if showBusy {
                busyMessage = ""
            }
        }

        do {
            let runtime = try await apiClient.fetchRobotRuntime(
                baseURL: draft.panelBaseURL,
                robotId: draft.robotId,
                panelClientToken: draft.panelClientToken,
                onboardingCode: draft.onboardingCode
            )
            runtimeSnapshot = runtime

            if let runtime = runtime, runtime.connected {
                updateDraft { draft in
                    draft.hasCompletedRuntimeConfirmation = true
                    if !runtime.localHost.isEmpty {
                        draft.robotHost = runtime.localHost
                    }
                }
                currentStep = .menu
                if !runtime.localHost.isEmpty {
                    statusMessage = "Робот найден в локальной сети: \(runtime.localHost)"
                } else if runtime.freshDeviceContact {
                    statusMessage = "Робот уже на связи с платформой. Локальный адрес ещё уточняется автоматически."
                } else {
                    statusMessage = "Панель подтвердила робота. Можно открыть статус устройства, аккаунт и поддержку."
                }
            } else {
                if draft.hasCompletedRuntimeConfirmation {
                    currentStep = .menu
                    statusMessage = "Робот уже был подключён раньше, но сейчас панель не подтвердила runtime. Проверьте локальную сеть и повторите запрос."
                } else {
                    currentStep = .wifi
                    statusMessage = "Робот пока не подтверждён. Подключитесь к \(robotWiFiHint), откройте портал и после настройки повторно запросите runtime."
                }
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func joinRobotNetwork() async {
        errorMessage = nil
        busyMessage = "Подключаю телефон к Wi-Fi робота..."
        defer { busyMessage = "" }

        do {
            try await RobotWiFiJoiner.joinRobotNetwork()
            await refreshCurrentSSID()
            currentStep = .wifi
            statusMessage = "Телефон попробовал подключиться к сети \(robotWiFiHint). Если робот ещё использует старый префикс Xiaozhi-*, iOS попробует его как запасной вариант."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func refreshCurrentSSID() async {
        let ssid = await RobotWiFiJoiner.currentSSID()
        currentSSID = ssid
    }
}
