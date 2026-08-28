# GOSHA MOBILE IOS HANDOFF

Последнее обновление: `2026-05-17`

Этот файл нужен как точка продолжения работы по iOS/App Store версии клиента `Гоша` внутри `GOSHA_MOBILE`.

## Где лежит iOS-каркас

```bash
cd /Users/maksim/Developer/GOSHA_MOBILE/ios/GoshaMobileIOS
```

Важные файлы:

- `ios/AGENTS.md`
- `ios/GoshaMobileIOS/README.md`
- `ios/GoshaMobileIOS/project.yml`
- `ios/GoshaMobileIOS/GoshaMobileIOS/...`

## Что уже сделано

- В `GOSHA_MOBILE` добавлен отдельный iOS-каркас:
  - `ios/GoshaMobileIOS`
- Каркас больше не привязан к старому серверному контуру.
- `AppConfig` переведён на текущий контур `GOSHA_PLATFORM`:
  - панель приходит из runtime `GoshaPanelBaseURL`
  - `MCP/WebSocket` контур приходит из runtime-пакета подключения
  - локальный портал `http://192.168.4.1`
- iOS-клиент уже читает:
  - `bundle.mobile_profile`
  - честный runtime-блок `connectivity`
- В iOS-модели добавлены:
  - `MobileProfile`
  - `SelfhostXiaozhiBundle`
  - расширенный `RobotRuntimeSnapshot`
- Подключение к Wi-Fi робота теперь рассчитано на:
  - основной префикс `GOSHA-`
  - запасной переходный префикс `Xiaozhi-`
- Есть product shell с вкладками:
  - `Робот`
  - `Аккаунт`
  - `Поддержка`
- Локально собран совместимый `XcodeGen 2.30.0`:
  - `/Users/maksim/bin/xcodegen`
- Через него уже сгенерирован Xcode-проект:
  - `ios/GoshaMobileIOS/GoshaMobileIOS.xcodeproj`
- Локальная проверка Swift уже прошла:

```bash
SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)
xcrun swiftc -typecheck -sdk "$SDK" -target x86_64-apple-ios15.0-simulator $(find /Users/maksim/Developer/GOSHA_MOBILE/ios/GoshaMobileIOS/GoshaMobileIOS -name '*.swift' | sort)
```

- И уже подтверждена сборка проекта через `xcodebuild`:

```bash
xcodebuild -project /Users/maksim/Developer/GOSHA_MOBILE/ios/GoshaMobileIOS/GoshaMobileIOS.xcodeproj -scheme GoshaMobileIOS -sdk iphonesimulator -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

- В `Info.plist` уже добавлены:
  - явные URL/SSID-ключи runtime-конфига;
  - `ATS`-исключение для dev-панели `TEMP_NL_RELAY_HTTP_HOST`;
  - `ATS`-исключение для локального портала `192.168.4.1`;
  - `NSLocalNetworkUsageDescription`
- И уже подтверждён запуск приложения в iOS Simulator:

```bash
xcrun simctl install booted /Users/maksim/Library/Developer/Xcode/DerivedData/.../Debug-iphonesimulator/Гоша.app
xcrun simctl launch booted com.maxcorp.gosha.mobile.ios
```

## Что ещё не закрыто

- Живой запуск на реальном iPhone ещё не выполнен.
- На этой машине это сейчас упирается в toolchain:
  - локальный `Xcode 13.2.1` не умеет готовить подключённый iPhone на `iOS 26.1`
  - `xcodebuild` даёт `Could not locate device support files`
- Уже был сделан обратимый эксперимент с подменой `DeviceSupport/DDI`:
  - временно созданы `26.1` и `26.1 (23B85)` как ссылки на `15.2`
  - ошибка сдвинулась с `Could not locate device support files` до попытки mount:
    - `AMDeviceMountImage`
    - `0xE8000007`
    - `The argument is invalid`
  - это подтвердило, что одного alias/symlink недостаточно
  - для следующей попытки нужен уже настоящий `DeveloperDiskImage` именно под `26.1 (23B85)`
  - временные ссылки после проверки уже удалены
- Встроенный `MobileDeviceUpdater` на машине тоже был проверен:
  - сервис живой и делает `SU query` по тегу `DEVICESUPPORT`
  - в доступных логах нет подтверждения, что он скачал подходящий asset для этого iPhone
- Локальный портал `192.168.4.1` и на Android, и для будущего iOS-сценария остаётся чувствительной точкой.
- Legal и panel пока работают по `http`, а не по production `https`.
- Нет production signing, app icon и App Store metadata.

## Что делать следующим

1. Для живого device-run выбрать один из двух путей:
   - взять iPhone/iOS, который поддерживает `Xcode 13.2.1`;
   - либо перенести проект на более новый Mac/Xcode.
2. Если всё же продолжать hack-ветку на этой машине:
   - добыть настоящий `DeviceSupport/DeveloperDiskImage` для `26.1 (23B85)` из более нового Xcode;
   - подложить его сюда;
   - повторить mount и device-run.
3. Открыть `ios/GoshaMobileIOS/GoshaMobileIOS.xcodeproj` в Xcode и проверить UI уже как полноценное приложение.
4. Настроить signing для реального iPhone.
5. Пройти живой сценарий на iPhone:
   - код подключения;
   - активация;
   - вход в `GOSHA-*`;
   - открытие `192.168.4.1`;
   - повторная проверка `runtime` после выхода робота из hotspot.
6. После живого прогона дополировать тексты и поведение для двух честных состояний:
   - локальный адрес найден;
   - робот подтверждён только через платформу.
7. Затем перейти к production assets и App Store пакету.
