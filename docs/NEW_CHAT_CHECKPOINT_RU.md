# NEW CHAT CHECKPOINT

Короткая точка входа для следующего агента в `GOSHA_MOBILE`.

## Сначала прочитать

1. `../AGENTS.md`
2. `/home/max/GOSHA_PLATFORM/docs/GOSHA_PROJECT_MAP_RU.md`
3. `NEW_CHAT_CHECKPOINT_RU.md`
4. `AGENT_CHECKPOINT_RU.md`
5. `PROJECT_STATUS_RU.md`
6. Если работаешь с Android-кодом:
   - `../app/AGENTS.md`
7. Если работаешь с iOS-кодом:
   - `../ios/AGENTS.md`
   - `GOSHA_MOBILE_IOS_HANDOFF_RU.md`

## Что уже сделано

- Создан отдельный Android-проект `GOSHA_MOBILE`.
- Для проекта уже настроен `origin`:
  - `git@github.com:MaxCorpOrg/GOSHA_MOBILE.git`
- Ветка `main` уже отправлена в GitHub и отслеживает:
  - `origin/main`
- Публичный URL проекта:
  - `https://github.com/MaxCorpOrg/GOSHA_MOBILE`
- Новый клиент переводится на бренд `Гоша`.
- Старый контур `AI_ROBOT` больше не является рабочим корнем этого проекта.
- В клиент добавляется чтение `mobile_profile` из `GOSHA_PLATFORM`.
- В переходный период клиент принимает `GOSHA-` и `Xiaozhi-`.
- Проект уже подтверждён локальной сборкой:
  - `assembleClientDebug`
  - `testClientDebugUnitTest`
- В репозиторий уже добавлен отдельный iOS-каркас:
  - `ios/GoshaMobileIOS`
- Для iOS уже создан отдельный GitHub-репозиторий:
  - `https://github.com/MaxCorpOrg/GOSHA_MOBILE_IOS`
- Локальный отдельный рабочий корень iOS:
  - `/Users/maksim/Developer/GOSHA_MOBILE_IOS`
- iOS-каркас уже переведён на новый контракт `GOSHA_PLATFORM`:
  - `bundle.mobile_profile`
  - runtime `connectivity`
  - `GOSHA-` + переходный `Xiaozhi-`
- Для iOS уже локально собран `XcodeGen 2.30.0`:
  - `/Users/maksim/bin/xcodegen`
- Через него уже создан:
  - `ios/GoshaMobileIOS/GoshaMobileIOS.xcodeproj`
- Локальная Swift-проверка уже подтверждена:
  - `xcrun swiftc -typecheck ...`
- И уже подтверждена сборка через:
  - `xcodebuild ... CODE_SIGNING_ALLOWED=NO build`
- В iOS `Info.plist` уже добавлены:
  - явные `panel/legal/portal` URL;
  - `GOSHA-` + `Xiaozhi-` SSID prefixes;
  - `ATS`-исключения для `151.241.228.232:18876` и `192.168.4.1`;
  - `NSLocalNetworkUsageDescription`
- И уже подтверждён запуск в iOS Simulator:
  - `xcrun simctl install booted .../Гоша.app`
  - `xcrun simctl launch booted com.maxcorp.gosha.mobile.ios`
- Для репозиториев `GOSHA_PLATFORM` и `GOSHA_MOBILE` уже поднят локальный фоновой sync:
  - скрипт `/Users/maksim/bin/gosha_repo_sync.sh`
  - `launchd` job `com.maxcorp.gosha-repo-sync`
  - отчёт `/Users/maksim/Developer/.gosha-sync/last_report.txt`
- Общая карта связанных контуров теперь зафиксирована в:
  - `/home/max/GOSHA_PLATFORM/docs/GOSHA_PROJECT_MAP_RU.md`
- Исправлена честность runtime-снимка:
  - Android больше не считает робота "подключённым через панель" только из-за настроенного endpoint
  - новый источник истины — блок `connectivity` из `GOSHA_PLATFORM`
- Исправлена и следующая нечестность:
  - если панель уже подтвердила робота, но `local_host` ещё не пришёл, приложение больше не называет это "робот найден в вашей сети"
  - теперь это отдельное честное состояние:
    - робот уже на связи с платформой;
    - локальный адрес ещё уточняется
- Каноническая Android-проверка после этой точки:
  - `./gradlew --no-daemon clean assembleClientDebug testClientDebugUnitTest lintClientDebug`
  - запускать последовательно одним вызовом, а не несколькими параллельными задачами

## Где остановились

- В `GOSHA_MOBILE` уже унифицированы правила общения агента:
  - корневой и локальные `AGENTS.md` требуют понятный русский технический язык;
  - для типовых терминов закреплены русские формы и пояснения.
- Нужно пройти живой сценарий на телефоне и роботе уже после фикса `connectivity`.
- Для iOS основной дальнейший контур теперь отдельный:
  - `GOSHA_MOBILE_IOS`
- iOS-каркас уже доведён до реального Xcode-проекта и первого запуска в симуляторе.
- Живой запуск iOS на подключённом iPhone сейчас заблокирован несовместимостью:
  - на машине `Xcode 13.2.1`
  - на телефоне `iOS 26.1`
- Нужно проверить и дополировать локальный портал `192.168.4.1`.
- Нужно сделать чуть понятнее тексты про то, найден робот локально, через панель или ещё не подтверждён.
- Нужно живьём перепроверить новую ветку "подтверждён только через платформу, локальный адрес ещё уточняется".
- Нужно отдельно держать в голове, что часть претензий к тембру голоса идёт не из мобильного клиента, а из серверного `TTS`-движка платформы.

## Что делать следующим

1. Установить `GOSHA_MOBILE` на телефон.
2. Убедиться, что новый пакет подключения из `GOSHA_PLATFORM` читается без регрессий.
3. Пройти живой сценарий подключения робота и убедиться, что больше нет ложного "подключено через панель".
4. После исправления локального портала повторно проверить плавность возврата из режима точки доступа.
5. Для iOS:
   - перейти в отдельный репозиторий `GOSHA_MOBILE_IOS`;
   - взять совместимый с `Xcode 13.2.1` iPhone/iOS или перейти на более новый Mac/Xcode;
   - затем открыть `GoshaMobileIOS.xcodeproj` в Xcode;
   - настроить signing;
   - пройти живой сценарий на iPhone.
6. Если появятся новые локальные `AGENTS.md`, держать их в том же русском техническом стиле.
