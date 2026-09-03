# Гоша (iOS / App Store)

Локальный iOS-каркас клиента `Гоша`, перенесённый в `GOSHA_MOBILE` и привязанный к текущему контракту `GOSHA_PLATFORM`.

Что уже есть:

- SwiftUI onboarding под код подключения;
- вызовы `/api/mobile/resolve-code`, `/api/mobile/activate-code` и `/api/mobile/robots/<robot_id>/runtime`;
- чтение `bundle.mobile_profile` и честного runtime-блока `connectivity`;
- локальное хранение черновика робота и владельца;
- попытка подключения к `GOSHA-*` с запасным переходным префиксом `Xiaozhi-*`;
- встроенный `WKWebView` для legal-страниц и локального портала `http://192.168.4.1`;
- post-onboarding shell с вкладками:
  - `Робот`
  - `Аккаунт`
  - `Поддержка`

## Структура

- `GoshaMobileIOS/App` — точка входа и runtime config;
- `GoshaMobileIOS/Models` — модели onboarding/runtime/draft;
- `GoshaMobileIOS/Networking` — клиент панели;
- `GoshaMobileIOS/Persistence` — сохранение черновика;
- `GoshaMobileIOS/Provisioning` — подключение к Wi-Fi робота;
- `GoshaMobileIOS/ViewModels` — состояние UI;
- `GoshaMobileIOS/Views` — SwiftUI интерфейс и `WKWebView`.

## Генерация Xcode-проекта

В папке лежит `project.yml` для `XcodeGen`:

```bash
cd <LEGACY_IOS_WORKSPACE>
xcodegen generate
open GoshaMobileIOS.xcodeproj
```

На этой машине `XcodeGen 2.30.0` уже собран локально в:

```bash
xcodegen
```

Поэтому здесь можно генерировать проект так:

```bash
cd <LEGACY_IOS_WORKSPACE>
xcodegen generate
```

## Локальная проверка Swift

```bash
cd <LEGACY_IOS_WORKSPACE>
SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)
xcrun swiftc -typecheck -sdk "$SDK" -target x86_64-apple-ios15.0-simulator $(find GoshaMobileIOS -name '*.swift' | sort)
```

## Текущая Xcode-проверка

Проект уже сгенерирован локально:

- `ios/GoshaMobileIOS/GoshaMobileIOS.xcodeproj`

И уже подтверждена сборка без signing для симулятора:

```bash
xcodebuild -project <LEGACY_IOS_WORKSPACE>/GoshaMobileIOS.xcodeproj -scheme GoshaMobileIOS -sdk iphonesimulator -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

## Runtime config

Значения читаются из `Info.plist`:

- `GoshaPanelBaseURL`
- `GoshaPrivacyPolicyURL`
- `GoshaTermsOfUseURL`
- `GoshaRobotPortalURL`
- `GoshaRobotSSIDPrefixes`

Текущие значения по умолчанию совпадают с новым контуром `GOSHA_PLATFORM`:

- панель `18876`;
- server-side `WebSocket/MCP` контур `18080`;
- локальный портал `http://192.168.4.1`.

Для dev-контура в `Info.plist` уже добавлены:

- `ATS`-исключение для панели `151.241.228.232:18876`;
- `ATS`-исключение для локального портала `192.168.4.1`;
- `NSLocalNetworkUsageDescription` для живого сценария с роботом в локальной сети.

## Текущие ограничения

- legal и panel пока сидят на `http`, а не на production `https`;
- ещё нет production assets, final signing и App Store metadata;
- iOS-поток не может полностью повторить Android-автономию в управлении Wi-Fi.
