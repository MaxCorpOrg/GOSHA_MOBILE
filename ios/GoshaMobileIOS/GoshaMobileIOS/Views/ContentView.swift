import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = OnboardingViewModel()

    var body: some View {
        Group {
            if viewModel.shouldShowProductShell {
                ProductShellView(viewModel: viewModel)
            } else {
                NavigationView {
                    onboardingContent
                }
            }
        }
        .sheet(item: $viewModel.presentedDocument) { document in
            NavigationView {
                WebDocumentView(document: document)
            }
        }
        .task {
            await viewModel.refreshCurrentSSID()
        }
    }

    private var onboardingContent: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.96, green: 0.98, blue: 1.0),
                    Color(red: 0.89, green: 0.94, blue: 1.0),
                    Color(red: 0.96, green: 0.93, blue: 0.88),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 18) {
                    heroCard
                    statusCard
                    codeCard
                    ownerCard
                    wifiCard
                    summaryCard
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .navigationTitle("Гоша")
    }

    private var heroCard: some View {
        CardSection(accent: Color(red: 0.18, green: 0.43, blue: 0.78)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("iOS-клиент для App Store")
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color(red: 0.12, green: 0.20, blue: 0.33))
                        Text("Код подключения, честная runtime-проверка через `connectivity`, локальный портал робота и product shell внутри одного iOS-приложения.")
                            .font(.system(size: 15, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.27, green: 0.34, blue: 0.47))
                    }
                    Spacer(minLength: 12)
                    VStack(spacing: 8) {
                        Text(stepTitle(viewModel.currentStep))
                            .font(.system(size: 13, weight: .semibold, design: .rounded))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.white.opacity(0.78))
                            .clipShape(Capsule())
                        if !viewModel.currentSSID.isEmpty {
                            Text(viewModel.currentSSID)
                                .font(.system(size: 12, weight: .medium, design: .rounded))
                                .foregroundColor(Color(red: 0.25, green: 0.31, blue: 0.45))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.68))
                                .clipShape(Capsule())
                        }
                    }
                }

                HStack(spacing: 10) {
                    Button("Политика") {
                        viewModel.openPrivacyPolicy()
                    }
                    .buttonStyle(SecondaryActionButtonStyle())

                    Button("Условия") {
                        viewModel.openTermsOfUse()
                    }
                    .buttonStyle(SecondaryActionButtonStyle())
                }
            }
        }
    }

    private var statusCard: some View {
        CardSection(accent: statusColor) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Состояние")
                        .font(.headline)
                    Spacer()
                    if viewModel.isBusy {
                        ProgressView()
                    }
                }

                Text(viewModel.statusMessage)
                    .font(.system(size: 15, weight: .medium, design: .rounded))
                    .foregroundColor(.primary)

                if let errorMessage = viewModel.errorMessage, !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(Color(red: 0.68, green: 0.16, blue: 0.20))
                }

                if viewModel.isBusy {
                    Text(viewModel.busyMessage)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    private var codeCard: some View {
        CardSection(accent: Color(red: 0.95, green: 0.62, blue: 0.18)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("1. Код подключения")
                    .font(.headline)

                TextField("Например, MJ6SG97A", text: binding(\.onboardingCode))
                    .textInputAutocapitalization(.characters)
                    .disableAutocorrection(true)
                    .padding(14)
                    .background(Color.white.opacity(0.82))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                Button("Найти робота по коду") {
                    Task { await viewModel.resolveCode() }
                }
                .buttonStyle(PrimaryActionButtonStyle())
            }
        }
    }

    private var ownerCard: some View {
        CardSection(accent: Color(red: 0.14, green: 0.66, blue: 0.55)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("2. Владелец и согласие")
                    .font(.headline)

                Text("Имя, почту и телефон можно заполнить сразу или позже. Если поля заполнены, приложение требует отдельное согласие на обработку этих данных.")
                    .font(.footnote)
                    .foregroundColor(.secondary)

                Group {
                    formField("Имя", text: binding(\.ownerName))
                    formField("Почта", text: binding(\.ownerEmail), keyboard: .emailAddress)
                    formField("Телефон", text: binding(\.ownerPhone), keyboard: .phonePad)
                }

                Toggle(isOn: $viewModel.didAcceptPrivacyConsent) {
                    Text("Подтверждаю согласие на обработку введённых контактных данных")
                        .font(.system(size: 14, weight: .medium, design: .rounded))
                }
                .toggleStyle(SwitchToggleStyle(tint: Color(red: 0.14, green: 0.66, blue: 0.55)))

                Button("Активировать код") {
                    Task { await viewModel.activateCode() }
                }
                .buttonStyle(PrimaryActionButtonStyle())
            }
        }
    }

    private var wifiCard: some View {
        CardSection(accent: Color(red: 0.48, green: 0.33, blue: 0.80)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("3. Wi-Fi онбординг робота")
                    .font(.headline)

                Text("На iOS поток остаётся явным: подключаемся к \(viewModel.robotWiFiHint), открываем встроенный портал `192.168.4.1`, затем повторно спрашиваем `runtime` у панели и проверяем `connectivity`.")
                    .font(.footnote)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 8) {
                    Label("Робот должен быть в режиме настройки", systemImage: "dot.radiowaves.left.and.right")
                    Label("Домашняя сеть для робота должна быть 2.4 GHz", systemImage: "wifi")
                    Label("В переходный период iOS допускает и старый префикс Xiaozhi-*", systemImage: "arrow.triangle.2.circlepath")
                }
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(Color(red: 0.25, green: 0.28, blue: 0.39))

                Button("Подключиться к Wi-Fi робота") {
                    Task { await viewModel.joinRobotNetwork() }
                }
                .buttonStyle(PrimaryActionButtonStyle())

                Button("Открыть портал 192.168.4.1") {
                    viewModel.openRobotPortal()
                }
                .buttonStyle(SecondaryActionButtonStyle())

                Button("Проверить состояние робота") {
                    Task { await viewModel.refreshRobotRuntime() }
                }
                .buttonStyle(SecondaryActionButtonStyle())
            }
        }
    }

    private var summaryCard: some View {
        CardSection(accent: Color(red: 0.90, green: 0.35, blue: 0.34)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("4. Сводка")
                    .font(.headline)

                summaryRow("Робот", value: viewModel.draft.robotName.ifBlank(viewModel.draft.robotId))
                summaryRow("Тариф", value: viewModel.draft.planName.ifBlank(viewModel.draft.planCode))
                summaryRow("Панель", value: viewModel.draft.panelBaseURL)
                summaryRow("Wi-Fi префиксы", value: viewModel.draft.robotWiFiPrefixes.joined(separator: ", "))

                if let runtime = viewModel.runtimeSnapshot {
                    summaryRow("Подключение", value: runtime.connected ? "Подтверждено" : "Пока не подтверждено")
                    if !runtime.connectivityEvidence.isEmpty {
                        summaryRow("Источник", value: runtime.connectivityEvidence)
                    }
                    if !runtime.localHost.isEmpty {
                        summaryRow("Локальный адрес", value: runtime.localHost)
                    }
                    if !runtime.mode.isEmpty {
                        summaryRow("Режим", value: runtime.mode)
                    }
                    if !runtime.lastSeenISO.isEmpty {
                        summaryRow("Последний сигнал", value: runtime.lastSeenISO)
                    }
                    if !runtime.boardName.isEmpty {
                        summaryRow("Плата", value: runtime.boardName)
                    }
                    if !runtime.appVersion.isEmpty {
                        summaryRow("Версия", value: runtime.appVersion)
                    }
                }

                Button("Очистить черновик и начать заново") {
                    viewModel.resetForNextRobot()
                }
                .buttonStyle(SecondaryActionButtonStyle())
            }
        }
    }

    private func binding<T>(_ keyPath: WritableKeyPath<OnboardingDraft, T>) -> Binding<T> {
        Binding(
            get: { viewModel.draft[keyPath: keyPath] },
            set: { newValue in
                viewModel.updateDraft { draft in
                    draft[keyPath: keyPath] = newValue
                }
            }
        )
    }

    private func formField(_ title: String, text: Binding<String>, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 13, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
            TextField(title, text: text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .padding(14)
                .background(Color.white.opacity(0.82))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    private func summaryRow(_ title: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 110, alignment: .leading)
            Text(value.isEmpty ? "—" : value)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
            Spacer(minLength: 0)
        }
    }

    private func stepTitle(_ step: OnboardingViewModel.Step) -> String {
        switch step {
        case .welcome:
            return "Старт"
        case .registration:
            return "Активация"
        case .wifi:
            return "Wi-Fi"
        case .menu:
            return "Готово"
        }
    }

    private var statusColor: Color {
        if viewModel.errorMessage != nil {
            return Color(red: 0.80, green: 0.24, blue: 0.24)
        }
        switch viewModel.currentStep {
        case .welcome:
            return Color(red: 0.15, green: 0.49, blue: 0.76)
        case .registration:
            return Color(red: 0.90, green: 0.62, blue: 0.12)
        case .wifi:
            return Color(red: 0.48, green: 0.33, blue: 0.80)
        case .menu:
            return Color(red: 0.12, green: 0.66, blue: 0.42)
        }
    }
}

struct CardSection<Content: View>: View {
    let accent: Color
    @ViewBuilder let content: Content

    var body: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(Color.white.opacity(0.72))
            HStack(spacing: 0) {
                Rectangle()
                    .fill(accent)
                    .frame(width: 8)
                content
                    .padding(18)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .stroke(Color.white.opacity(0.55), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 20, x: 0, y: 12)
    }
}

struct PrimaryActionButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .bold, design: .rounded))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [
                                Color(red: 0.13, green: 0.44, blue: 0.83),
                                Color(red: 0.09, green: 0.69, blue: 0.62),
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct SecondaryActionButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .semibold, design: .rounded))
            .foregroundColor(Color(red: 0.13, green: 0.31, blue: 0.58))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.white.opacity(0.82))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color(red: 0.80, green: 0.86, blue: 0.94), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.9 : 1)
            .scaleEffect(configuration.isPressed ? 0.99 : 1)
    }
}

extension String {
    func ifBlank(_ fallback: @autoclosure () -> String) -> String {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback() : self
    }
}
