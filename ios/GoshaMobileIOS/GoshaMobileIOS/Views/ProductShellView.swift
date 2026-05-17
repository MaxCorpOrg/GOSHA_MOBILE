import SwiftUI

struct ProductShellView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        TabView {
            NavigationView {
                ProductRobotView(viewModel: viewModel)
            }
            .tabItem {
                Label("Робот", systemImage: "dot.radiowaves.left.and.right")
            }

            NavigationView {
                ProductAccountView(viewModel: viewModel)
            }
            .tabItem {
                Label("Аккаунт", systemImage: "person.crop.circle")
            }

            NavigationView {
                ProductSupportView(viewModel: viewModel)
            }
            .tabItem {
                Label("Поддержка", systemImage: "questionmark.circle")
            }
        }
        .accentColor(Color(red: 0.13, green: 0.44, blue: 0.83))
    }
}

private struct ProductRobotView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        AppSurface {
            VStack(spacing: 18) {
                CardSection(accent: runtimeAccent) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(viewModel.draft.robotName.ifBlank(viewModel.draft.robotId.ifBlank("Статус робота")))
                            .font(.system(size: 28, weight: .bold, design: .rounded))
                            .foregroundColor(Color(red: 0.12, green: 0.20, blue: 0.33))

                        Text(viewModel.statusMessage)
                            .font(.system(size: 15, weight: .medium, design: .rounded))
                            .foregroundColor(Color(red: 0.26, green: 0.33, blue: 0.47))

                        if let runtime = viewModel.runtimeSnapshot {
                            runtimeGrid(runtime)
                        } else {
                            Text("Последний runtime-снимок ещё не загружен. Запросите проверку из панели.")
                                .font(.footnote)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                CardSection(accent: Color(red: 0.48, green: 0.33, blue: 0.80)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Управление подключением")
                            .font(.headline)

                        if !viewModel.currentSSID.isEmpty {
                            Label("Текущий Wi-Fi: \(viewModel.currentSSID)", systemImage: "wifi")
                                .font(.system(size: 14, weight: .medium, design: .rounded))
                        }

                        Text("Если робот перестал отвечать, можно заново подключиться к \(viewModel.robotWiFiHint), открыть локальный портал и затем повторно проверить `connectivity`.")
                            .font(.footnote)
                            .foregroundColor(.secondary)

                        Button("Проверить состояние в панели") {
                            Task { await viewModel.refreshRobotRuntime() }
                        }
                        .buttonStyle(PrimaryActionButtonStyle())

                        Button("Подключиться к Wi-Fi робота") {
                            Task { await viewModel.joinRobotNetwork() }
                        }
                        .buttonStyle(SecondaryActionButtonStyle())

                        Button("Открыть портал 192.168.4.1") {
                            viewModel.openRobotPortal()
                        }
                        .buttonStyle(SecondaryActionButtonStyle())
                    }
                }

                if let errorMessage = viewModel.errorMessage, !errorMessage.isEmpty {
                    CardSection(accent: Color(red: 0.80, green: 0.24, blue: 0.24)) {
                        Text(errorMessage)
                            .font(.system(size: 14, weight: .semibold, design: .rounded))
                            .foregroundColor(Color(red: 0.68, green: 0.16, blue: 0.20))
                    }
                }
            }
        }
        .navigationTitle("Робот")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var runtimeAccent: Color {
        if viewModel.runtimeSnapshot?.connected == true {
            return Color(red: 0.12, green: 0.66, blue: 0.42)
        }
        return Color(red: 0.95, green: 0.62, blue: 0.18)
    }

    private func runtimeGrid(_ runtime: RobotRuntimeSnapshot) -> some View {
        VStack(spacing: 10) {
            runtimeRow("Подключение", value: runtime.connected ? "Подтверждено" : "Не подтверждено")
            if !runtime.connectivityEvidence.isEmpty {
                runtimeRow("Источник", value: runtimeEvidence(runtime.connectivityEvidence))
            }
            runtimeRow("Режим", value: runtime.mode.ifBlank("—"))
            runtimeRow("Transport", value: runtime.transportState.ifBlank("—"))
            runtimeRow("Локальный адрес", value: runtime.localHost.ifBlank("—"))
            runtimeRow("Последний сигнал", value: runtime.lastSeenISO.ifBlank("—"))
            runtimeRow("Плата", value: runtime.boardName.ifBlank("—"))
            runtimeRow("Версия", value: runtime.appVersion.ifBlank("—"))
        }
    }

    private func runtimeRow(_ title: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 130, alignment: .leading)
            Text(value)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
            Spacer(minLength: 0)
        }
    }

    private func runtimeEvidence(_ value: String) -> String {
        switch value {
        case "local_host":
            return "локальный адрес телефона"
        case "probe_verified":
            return "живая проверка панели"
        case "fresh_device_contact":
            return "свежий сигнал устройства в платформе"
        default:
            return value
        }
    }
}

private struct ProductAccountView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        AppSurface {
            VStack(spacing: 18) {
                CardSection(accent: Color(red: 0.14, green: 0.66, blue: 0.55)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Аккаунт и подписка")
                            .font(.headline)

                        accountRow("Код", value: viewModel.draft.onboardingCode.ifBlank("—"))
                        accountRow("Робот", value: viewModel.draft.robotName.ifBlank(viewModel.draft.robotId.ifBlank("—")))
                        accountRow("Тариф", value: viewModel.draft.planName.ifBlank(viewModel.draft.planCode.ifBlank("—")))
                        accountRow("Оплата", value: viewModel.draft.paymentStatus.ifBlank("—"))
                        accountRow("Начало", value: viewModel.draft.billingStart.ifBlank("—"))
                        accountRow("Окончание", value: viewModel.draft.billingEnd.ifBlank("—"))
                        accountRow("Панель", value: viewModel.draft.panelBaseURL.ifBlank("—"))
                    }
                }

                CardSection(accent: Color(red: 0.13, green: 0.44, blue: 0.83)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Контакты владельца")
                            .font(.headline)

                        accountRow("Имя", value: viewModel.draft.ownerName.ifBlank("Не указано"))
                        accountRow("Почта", value: viewModel.draft.ownerEmail.ifBlank("Не указана"))
                        accountRow("Телефон", value: viewModel.draft.ownerPhone.ifBlank("Не указан"))
                        accountRow("Wi-Fi робота", value: viewModel.draft.robotWiFiPrefixes.joined(separator: ", "))
                    }
                }

                if let bundle = viewModel.bundle, !bundle.users.isEmpty {
                    CardSection(accent: Color(red: 0.90, green: 0.35, blue: 0.34)) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Пользователи робота")
                                .font(.headline)

                            ForEach(bundle.users) { user in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(user.name.ifBlank(user.userId))
                                        .font(.system(size: 15, weight: .bold, design: .rounded))
                                    Text(user.contact.ifBlank("Контакт не указан"))
                                        .font(.footnote)
                                        .foregroundColor(.secondary)
                                    Text(user.role)
                                        .font(.caption)
                                        .foregroundColor(Color(red: 0.56, green: 0.25, blue: 0.25))
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14)
                                .background(Color.white.opacity(0.78))
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            }
                        }
                    }
                }

                CardSection(accent: Color(red: 0.73, green: 0.28, blue: 0.24)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Сервисные действия")
                            .font(.headline)

                        Text("Сброс удалит текущий черновик и вернёт приложение в onboarding для следующего робота.")
                            .font(.footnote)
                            .foregroundColor(.secondary)

                        Button("Подключить другого робота") {
                            viewModel.resetForNextRobot()
                        }
                        .buttonStyle(SecondaryActionButtonStyle())
                    }
                }
            }
        }
        .navigationTitle("Аккаунт")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func accountRow(_ title: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 110, alignment: .leading)
            Text(value)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
            Spacer(minLength: 0)
        }
    }
}

private struct ProductSupportView: View {
    @ObservedObject var viewModel: OnboardingViewModel

    var body: some View {
        AppSurface {
            VStack(spacing: 18) {
                CardSection(accent: Color(red: 0.95, green: 0.62, blue: 0.18)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Что проверить сначала")
                            .font(.headline)

                        supportBullet("Робот должен видеть домашний Wi-Fi именно в диапазоне 2.4 GHz.")
                        supportBullet("Если после настройки робот пропал, дождитесь его перезагрузки и повторите runtime-проверку.")
                        supportBullet("Если нужен повторный provisioning, заново подключитесь к \(viewModel.robotWiFiHint) и откройте портал внутри приложения.")
                    }
                }

                CardSection(accent: Color(red: 0.18, green: 0.43, blue: 0.78)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Документы и поддержка")
                            .font(.headline)

                        Text("Юридические документы вынесены в отдельный продуктовый экран, чтобы их можно было открыть не только из onboarding.")
                            .font(.footnote)
                            .foregroundColor(.secondary)

                        Button("Открыть политику конфиденциальности") {
                            viewModel.openPrivacyPolicy()
                        }
                        .buttonStyle(PrimaryActionButtonStyle())

                        Button("Открыть условия пользования") {
                            viewModel.openTermsOfUse()
                        }
                        .buttonStyle(SecondaryActionButtonStyle())
                    }
                }

                CardSection(accent: Color(red: 0.14, green: 0.66, blue: 0.55)) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Диагностика")
                            .font(.headline)

                        accountLikeRow("Статус", value: viewModel.statusMessage)
                        accountLikeRow("Wi-Fi", value: viewModel.currentSSID.ifBlank("Сеть не определена"))

                        if viewModel.isBusy {
                            accountLikeRow("Операция", value: viewModel.busyMessage)
                        }
                    }
                }
            }
        }
        .navigationTitle("Поддержка")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func supportBullet(_ text: String) -> some View {
        Label {
            Text(text)
                .font(.system(size: 14, weight: .medium, design: .rounded))
        } icon: {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(Color(red: 0.95, green: 0.62, blue: 0.18))
        }
    }

    private func accountLikeRow(_ title: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(title)
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundColor(.secondary)
                .frame(width: 110, alignment: .leading)
            Text(value)
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
            Spacer(minLength: 0)
        }
    }
}

private struct AppSurface<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
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
                    content
                }
                .padding(16)
            }
        }
    }
}
