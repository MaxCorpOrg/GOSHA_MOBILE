# AGENT CHECKPOINT

## Свежая контрольная точка 2026-08-28

- Рабочая ветка `codex/mobile-runtime-config-hardening-20260828` поверх `61b8d1e` доводит исправление P1 по runtime-конфигурации Android release.
- Release-путь теперь требует явные `http(s)` значения для `GOSHA_PANEL_BASE_URL`, `RUSTORE_PRIVACY_POLICY_URL` и `RUSTORE_TERMS_OF_USE_URL` как в Gradle gate, так и в `build_rustore_release.sh`. Без них выпуск останавливается fail-closed.
- Debug default для адреса панели остаётся пустым; activation на fresh install теперь не пытается идти в пустой endpoint и показывает понятную ошибку настройки панели.
- Runtime/test/examples не закрепляют временный relay или реальные публичные IP: example values и unit fixtures используют `*.example.test`.
- Последовательные локальные проверки без APK/install/device/live: negative и positive `:app:verifyReleaseRuntimeConfig`, `testClientDebugUnitTest` — 154 tests, 0 failures/errors/skips; `assembleClientDebug` — `PASS`; `lintClientDebug` — 0 errors и 84 прежних warnings; `git diff --check` — `PASS`; secret/hardcoded endpoint scans по product/test файлам — чисто.
- Новый commit уже создан и проверен одним независимым read-only reviewer GPT-5.5/xhigh: P0/P1/P2 нет. Существующая AI Office карточка `task-20260828-ai-robots-app-roadmap` обновлена, отчёт опубликован в её Mattermost root.
- Осталось: решить, как вводить локальный branch в Draft PR `#51` и запускать ли внешнюю CI. Телефон, робот, production, relay и AI Office worker не включать без отдельного разрешения.

## Свежая контрольная точка 2026-08-27

- Platform quality gate уже закрыт; Android Draft PR `#51` теперь тоже получил terminal AI Office `PASS`, но остаётся Draft/Open и не сливается.
- Проверенный Android code head: `26530d8`. Terminal task `task-20260827T092032Z-immutable-terminal-android-pr-51-gate-at-26530d8` на фактическом профиле `GPT-5.5 / xhigh` завершился без P0/P1/P2.
- Предыдущие immutable tasks на `de450fc` и `e72faf3` были `NO-GO`. Их findings закрыты: stale connector/presence/runtime/status, delayed stale command windows и ложное подтверждение фонового режима больше не проходят.
- Полный Android gate на текущем кандидате: 146 unit tests, 0 failures/errors/skips; `assembleClientDebug` — `PASS`; `lintClientDebug` — 0 errors и 84 прежних warnings; `git diff --check` — `PASS`.
- APK, телефон, prefs, live robot, firmware и relay в этой работе не изменялись.
- Из-за неисправной левой сервы flash, motion и trim остаются запрещены. Следующий шаг — firmware gate, но только неподвижный read-only и без прошивки робота. Operator command gateway остаётся blocked.

## Предыдущая контрольная точка 2026-08-26

- Platform quality gate закрыт; продолжение идёт строго через Android Draft PR `#51`, без перехода к firmware раньше Android PASS.
- AI Office reviewer GPT-5.5/xhigh проверил неизменяемый Android-коммит `62a11aa` и оставил два P1 без P0: stale foreground presence после замены физического устройства при том же `robot_id` и использование неавторитетного сохранённого `expected_device_id` в post-portal generic discovery.
- Исправлено: foreground service перед локальным probe, публикацией presence и обработкой Hub-команд требует совпадения текущих `robot_id`, `expected_device_id` и `robot_host`; устаревший запуск останавливается, а superseded coroutine не может отменить новый connector job.
- Исправлено: если saved device id неавторитетен, discovery не возвращает его как последний fallback; без bundle, panel hint или локально проверенного device id поток остаётся fail-closed.
- При проверке найден и устранён отдельный дефект доставки notification: `RobotJsonRpcProxy.notify()` ждёт нормального WebSocket close-handshake перед успехом.
- Re-review первого исправления нашёл окно между новым persisted draft и новым `ACTION_START`, где поздний `stopSelf()` мог остановить новый service. Текущая остановка привязана к `startId` и идёт через `stopSelfResult(oldStartId)`; superseded coroutine не отменяет более новый start request.
- Review `task-20260826T103629Z-android-startid-fence-gate-at-221f6d8` подтвердил `startId`-fence, но оставил P1: WebSocket listener старого запуска мог отправить payload после задержанного identity-result, если run был superseded уже после предварительной проверки service.
- Коммит `1753a4d` закрыл delayed-identity сценарий, но строгий GPT-5.5 review нашёл остаточный `check -> send` TOCTOU и поздний `agent_status` после probe: P1 + P2, без P0.
- Исправлено: `RobotJsonRpcProxy.call/notify` принимает атомарный барьер run, внутри которого под `connectorStateLock` одновременно проверяются `config + startId + captured Job + draft identity` и выполняется неблокирующий enqueue кадра. Hub `pong`, `mcp_response` и `agent_status` используют тот же барьер; после probe/presence стоят повторные проверки.
- Полный `testClientDebugUnitTest` (133 теста) + `assembleClientDebug + lintClientDebug` и `git diff --check` проходят. Нужен повторный read-only review исправленного HEAD; AI Office terminal review пока недоступен из-за исчерпанных workspace credits, worker оставлен в `draining`.
- APK, телефон, prefs и live robot не изменялись; flash, motion и trim по-прежнему запрещены из-за левой сервы.

## Свежая контрольная точка 2026-08-25

- Текущий live-канал `gosha-main/voice` принят: робот разговаривает, подключён и общается через временную связку `TEMP_NL_RELAY -> PRIMARY_PLATFORM_SERVER`.
- Временный relay сохраняется только до переноса на нормальный `FUTURE_PRODUCTION_SERVER` в конце месяца. Его нельзя закреплять в коде, прошивке, панели или переносимой документации как production-адрес.
- Портовые роли текущего серверного контура:
  - `18876` — HTTP: панель, mobile API, OTA/config handoff;
  - `18080` — voice `WebSocket` и `MCP`.
- Источник адресов для Android и связанных компонентов: runtime/env платформы, пакет подключения и сохранённые мобильные значения `panel_url` / `cloud_endpoint` / `edge_hub_url`. При миграции временного relay на новый сервер нужно сохранить эти runtime/saved значения и не прошивать конкретный relay.
- Реальные публичные IP, токены, onboarding-коды, Wi‑Fi-пароли, файлы подписи и runtime/env-секреты не писать в Git; использовать только символические имена `TEMP_NL_RELAY`, `PRIMARY_PLATFORM_SERVER`, `FUTURE_PRODUCTION_SERVER`.
- `http://192.168.4.1` — только локальный AP-портал onboarding робота. Не использовать его как platform/panel/relay/OTA/voice endpoint.
- Текущий Android quality gate для `feature/mobile-triangle-runtime`: `NO-GO` для установки и merge. Причина: fail-closed identity закрыт для общего subnet sweep, но ещё не закрыт для preferred/saved/panel-hint host и foreground service.
- Минимальные исправления перед новым APK: expected `device_id` обязателен для preferred/saved/panel-hint путей; `ConnectorForegroundService` должен подтверждать локальный host через identity-aware read-only probe, а не только через открытый `WebSocket`; смена expected `device_id` не должна переиспользовать старый сохранённый host.
- Нужные тесты: mismatch `device_id` на preferred/saved/panel-hint отклоняется; foreground service не публикует `home_wifi_local` при identity mismatch; миграция relay/new-server URL сохраняет токены/device identifiers и не хардкодит `TEMP_NL_RELAY`.
- APK-кандидат, телефон, сохранённые данные приложения и live robot не трогать без явного одобрения оператора.

## Свежая контрольная точка 2026-08-24

- Подключение нового робота к домашнему Wi-Fi через его точку настройки завершилось, но робот получает сетевую ошибку маршрута до публичного узла платформы.
- В приложении старый `robot_id` отделён от аппаратного `device_id`. Для общего поиска по подсети теперь обязателен ожидаемый `device_id`, подтверждённый локальным read-only контрактом `gosha.identity.get/result`.
- Старый адрес робота в локальных настройках отсутствует; приложение не должно закреплять первый ответивший узел только по имени `gosha-main`.
- Проверки `testClientDebugUnitTest`, `assembleClientDebug` и `lintClientDebug` прошли.
- Патч не установлен на телефон. Не выдавать прежнюю живую приёмку и контрольную сумму старого APK за проверку этого кандидата.
- Следующий безопасный шаг: восстановить прямую доступность публичного узла, получить свежую привязку нового устройства, затем установить Android-кандидат и повторить регистрацию.

## Свежая контрольная точка 2026-07-23

- Рабочее дерево: `<MOBILE_TRIANGLE_WORKTREE>`.
- Ветка: `feature/mobile-triangle-runtime`, база `fe7eded`.
- Текущий этап: мобильная сторона масштабируемого контура «робот — приложение — панель» поверх уже закрытого Android P2.
- Добавлены постоянный идентификатор установки, сеанс процесса, сквозной идентификатор восстановления Wi-Fi и ограниченная очередь повторной доставки событий.
- Исправлена граница завершения `wifi_recovery`: событие возврата сети сохраняет задачу в `running`, а `completed` публикуется только после прямой локальной проверки робота.
- Терминальный `timed_out` вынесен в проверяемую политику; позднее обнаружение робота не переписывает завершённую задачу.
- Ограниченные повторы запускаются и для активной задачи без сигнала панели, поэтому недоступный робот завершает recovery через `timed_out`, а не оставляет вечный `running`.
- Пользовательский статус после `timed_out` также конечный и больше не обещает автоматические повторы.
- Живой Android P1 повторно закрыт на `TECNO LI9` уже после этого исправления: потеря и возврат Wi-Fi обработаны без смены PID, карточка и `ConnectorForegroundService` восстановились, платформа получила три согласованных фазы с одним `correlation_id`, очередь доставки пуста.
- Исправлены две найденные ИИ-офисом границы: скрытый Android SSID больше не мешает обнаружению Wi-Fi, а адрес панели не считается прямым локальным подтверждением; локальная проверка имеет максимум три отложенных повтора.
- `2026-08-24` подготовлен Android P1-патч для нового робота:
  - пакет подключения теперь различает top-level `edge_hub_url` и MCP-поля `mobile_profile.mcp_endpoint_base` / `cloud_endpoint`;
  - фоновая служба не блокирует `mobile_presence` из-за отсутствующего или ещё не готового edge Hub;
  - Hub-сессия считается готовой только после `agent_ready`, heartbeat закрывается в `finally`;
  - автоматический post-provision поиск не делает общий обход подсети и не должен закреплять чужое `ws://<host>:8080/ws`; общий sweep доступен только через явный флаг.
- В live-проверке `2026-08-24` уточнено: явный post-provision return может запускать общий поиск только с ожидаемым `robot_id`; найденный generic host подтверждается read-only `self.get_system_info` перед сохранением.
- В той же точке `WifiInfoHelper` исключает `TRANSPORT_VPN`, чтобы при активном VPN использовать настоящий Wi‑Fi Network, а не VPN-underlay.
- Предыдущий принятый APK установлен с сохранением данных; SHA-256 `0bc800b567eb87e3bcb7250e374a23416da3063a7f3463ebbff0ae0a35590c52`. Кандидат `2026-08-24` ещё не установлен.
- `testClientDebugUnitTest`, `assembleClientDebug` и `lintClientDebug` завершены успешно.
- Доказательство общего прогона лежит в `<PRIVATE_VALIDATION_EVIDENCE>/task-20260723T111058Z-read-only/live-validation/acceptance-evidence.json`, SHA-256 `600c3d526dddf95a39c38c1cf126a952d02a2e6b7b506f8a591b6f94d7690f0e`.
- Главный следующий шаг: зафиксировать и отправить мобильную ветку; затем отдельным P1 перевести операторские команды `MCP` на собственный шлюз платформы.
- Серверный контракт находится в отдельной ветке `feature/triangle-runtime-events` репозитория `GOSHA_PLATFORM`; прошивочный источник — в `feature/firmware-triangle-runtime` репозитория `GOSHA_FIRMWARE`.

## Что это за проект

`GOSHA_MOBILE` — отдельный Android-клиент платформы `Гоша`.

## Текущая рабочая точка

- Каноническое Android-дерево для текущего контура:
  - `<MOBILE_PORTAL_WORKTREE>`
  - ветка `feature/mobile-hotspot-portal`
- `2026-07-21` на `TECNO LI9` и `gosha-main` живьём закрыт P2 повторных системных запросов сети:
  - полный путь `MENU -> портал -> done.html -> MENU` завершён без повторного диалога приложения и без отката назад;
  - панель подтвердила свежий `home_wifi_local` и `local_host = 192.168.1.159`;
  - после `HOME` в течение `213` секунд сохранились PID `25698`, `ConnectorForegroundService isForeground=true`, успешные выполненные проверки робота и свежая фоновая запись панели;
  - продуктовый код менять не потребовалось;
  - журнал: `<PRIVATE_VALIDATION_EVIDENCE>/task-20260721T100653Z-android-wi-fi-p2-tecno-li9-gosha-main/live-validation/android-adb.log`;
  - SHA-256 журнала: `ca1629784a6406a5573483ccdbac3423aa2f2df933f4c8feca4d14cb73c11e8a`;
  - обезличенный снимок панели и `dumpsys`: `<PRIVATE_VALIDATION_EVIDENCE>/task-20260721T100653Z-android-wi-fi-p2-tecno-li9-gosha-main/live-validation/acceptance-evidence.json`, SHA-256 `b64b46ecf98330b1df865b14ec58e65c5a9a5b17f6255b8a95460e8e45745fdd`;
  - независимый `reviewer` AI_OFFICE не нашёл P0/P1/P2 и рекомендовал `checkpoint`.
- Для репозитория уже настроен удалённый `origin`:
  - `git@github.com:MaxCorpOrg/GOSHA_MOBILE.git`
- Ветка `main` уже отправлена в GitHub и отслеживает:
  - `origin/main`
- Публичный адрес репозитория:
  - `https://github.com/MaxCorpOrg/GOSHA_MOBILE`
- Общая карта связанных контуров:
  - `<PLATFORM_WORKSPACE>/docs/GOSHA_PROJECT_MAP_RU.md`
- Проект создан из старого Android-клиента как самостоятельная копия.
- Новый идентификатор приложения:
  - `com.maxcorp.gosha.mobile`

## Что уже сделано

- В `GOSHA_MOBILE` уже унифицированы правила общения агента:
  - корневой и локальные `AGENTS.md` требуют понятный русский технический язык;
  - русско-английский суржик в обычном тексте запрещён;
  - для типовых слов уже закреплены русские формы и пояснения.
- Для параллельной ручной разработки теперь отдельно зафиксирована политика `git worktree`:
  - ручные рабочие деревья выносятся в `<TASK_WORKTREE>`
  - публикационный контур `RuStore` и flow локального портала не должны жить в одной ветке без явной причины
- Перенесён Android-проект в отдельную папку `<MOBILE_WORKSPACE>`.
- Начат перевод сборки на новый идентификатор приложения.
- Добавлена поддержка нового серверного блока `mobile_profile`.
- В коде включена переходная поддержка двух префиксов сети робота:
  - `GOSHA-`
  - `Xiaozhi-`
- Пользовательские тексты переводятся на бренд `Гоша`.
- Проект уже подтверждён локальной сборкой:
  - `assembleClientDebug`
  - `testClientDebugUnitTest`
- Выполнен живой прогон на телефоне `TECNO LI9`:
  - приложение установлено через `adb`
  - обезличенный activation code принят
  - сохранены новые runtime endpoint значения без публикации конкретного временного адреса
  - телефон переведён в сеть `GOSHA-A-1BE1`
  - открыт экран локального портала робота
- Исправлена ложная мобильная эвристика:
  - раньше Android мог считать робота "подключённым через панель" просто по настроенному `control`
  - теперь `PanelApiClient` читает `connectivity` из `GOSHA_PLATFORM`
  - live-route через панель считается подтверждённым только по реальным сигналам:
    - `local_host`
    - `probe_verified`
    - `fresh_device_contact`
- Исправлена ещё одна нечестная развилка UX:
  - если панель уже подтвердила робота, но `local_host` ещё пустой, приложение больше не пишет так, будто робот найден прямо в локальной сети
  - теперь для такого случая используется честный статус:
    - робот уже на связи с платформой;
    - локальный адрес не подтверждён;
    - повторная локальная проверка произойдёт при следующем входе или возобновлении меню
  - diagnostics-карточка тоже различает:
    - локально найденный робот;
    - подтверждение только через платформу
- Текущий путь исходников Android пока лежит в старом дереве:
  - `<MOBILE_WORKSPACE>/app/src/main/java/com/maxcorp/edgeconnector`
  Это технический хвост копирования, а не старый продуктовый бренд.
- В этот же репозиторий уже добавлен отдельный iOS-каркас:
  - `<LEGACY_IOS_WORKSPACE>`
- Для iOS уже создан отдельный GitHub-репозиторий:
  - `https://github.com/MaxCorpOrg/GOSHA_MOBILE_IOS`
- Локальный отдельный рабочий корень iOS:
  - `<IOS_WORKSPACE>`
- iOS-каркас уже адаптирован к текущему контракту `GOSHA_PLATFORM`:
  - `bundle.mobile_profile`
  - честный runtime `connectivity`
  - `GOSHA-` как основной Wi-Fi префикс
  - `Xiaozhi-` как переходный запасной префикс
- Для iOS уже локально собран `XcodeGen 2.30.0`:
  - `xcodegen`
- Через него уже создан:
  - `<LEGACY_IOS_WORKSPACE>/GoshaMobileIOS.xcodeproj`
- Локальная Swift-проверка уже подтверждена:
  - `xcrun swiftc -typecheck ...`
- И уже подтверждена Xcode-сборка без signing для симулятора:
  - `xcodebuild ... CODE_SIGNING_ALLOWED=NO build`
- В iOS `Info.plist` уже добавлены:
  - явные `panel/legal/portal` URL;
  - `GOSHA-` + `Xiaozhi-` SSID prefixes;
  - `ATS`-исключения для `TEMP_NL_RELAY_HTTP_HOST` и `192.168.4.1`;
  - `NSLocalNetworkUsageDescription`
- И уже подтверждён симуляторный install/launch:
  - `xcrun simctl install booted .../Гоша.app`
  - `xcrun simctl launch booted com.maxcorp.gosha.mobile.ios`
- Добавлен локальный механизм актуализации репозиториев:
  - `<LOCAL_SYNC_HELPER>`
  - `launchd` job `com.maxcorp.gosha-repo-sync`
  - отчёт статуса:
    - `<LOCAL_SYNC_REPORT>`

## Главный ближайший приоритет

1. Основной серверный пакет подключения уже подтверждён на живом телефоне.
2. Ложное состояние "подключено через панель" уже закрыто.
3. Текущий главный мобильный блокер уже смещён:
   - `2026-05-27` подтверждено на живом телефоне, что после нажатия `Подключить робота` приложение открывает встроенный `HotspotPortalActivity`
   - подтверждено, что страница робота реально загружается внутри приложения
   - подтверждены живые `200 OK` для `http://192.168.4.1` и внутренних запросов портала
   - поверх этого уже реализована отдельная стабилизация Android-контура:
     - флаг `setupCompleted`
     - тихая фоновая стабилизация в `MENU`
     - отправка `mobile_presence` в панель
     - автоматический запуск `ConnectorForegroundService` при появлении реального `robotHost`
   - незакрытый хвост теперь уже не в самом открытии портала, а в живой проверке пост-регистрационной стабильности:
     - полный `submit`
     - возврат в `MENU`
     - отсутствие самопроизвольного отката назад
     - корректный сигнал в панель администратора
4. Для этого блока поднято отдельное рабочее дерево:
   - `<MOBILE_PORTAL_WORKTREE>`
   Рабочая ветка:
   - `feature/mobile-hotspot-portal`
5. В этой ветке уже сделана рабочая прикладная правка:
   - локальный портал снова открывается прямо внутри приложения;
   - `HotspotPortalActivity` загружает HTML и ресурсы через `RobotPortalClient`, а не через нестабильный прямой маршрут `WebView`;
   - в экране сохранены диагностические сообщения `HotspotPortal`, `GoshaRobotWifi`, `RobotPortalClient`;
   - в локальном состоянии появился флаг `setupCompleted`, чтобы приложение не возвращало пользователя назад после уже успешного подключения;
   - мобильный клиент теперь сам отправляет в панель `mobile_presence`;
   - `ConnectorForegroundService` зарегистрирован в `AndroidManifest` и оформлен под Android 14 как `dataSync`;
   - локальные проверки уже подтверждены:
     - `./gradlew --no-daemon testClientDebugUnitTest`
     - `./gradlew --no-daemon assembleClientDebug`
     - `./gradlew --no-daemon lintClientDebug`
   - лишние пакеты `com.maxcorp.edgeconnector` и `com.maxcorp.edgeconnector.admin` удалены с телефона;
   - на устройстве оставлен только `com.maxcorp.gosha.mobile`;
   - `RobotWifiConnector.bindToCurrentRobotWifi()` больше не рвёт активную сеть робота преждевременным `release()`;
   - сеть робота теперь ищется не только по `SSID`, но и по подсети `192.168.4.x`, поэтому `Network` робота стабильно находится на этом телефоне.
   - следующая локальная серия `2026-05-27` уже добивает скорость и понятность:
     - `MENU` должен сразу показывать, что приложение проверяет робота, не видит домашний `Wi‑Fi`, видит рядом сеть робота или уже нашло локальный адрес;
     - шаг повторного подключения `Wi‑Fi` теперь сам уточняет текущее состояние робота;
     - локальный поиск сначала проверяет сохранённый `robotHost`;
     - сокращены паузы после `submit` и после возврата из локального портала.
   - `2026-06-30` живой прогон уточнил промежуточную реальность:
     - робот реально был доступен в домашней сети по `192.168.1.159:8080` с рабочего компьютера;
     - runtime панели нёс `board_ip = 192.168.1.159`;
     - на телефоне одновременно был активен VPN `run.nlab.iproxy` / `org.amnezia.vpn`, из-за чего приложение не могло локально проверить робота;
     - в клиент уже добавлены `Wi‑Fi`-привязанный `HTTP`-клиент, подхват `board_ip` из панели и честные предупреждения про VPN в `MENU` и на шаге повторного `Wi‑Fi`.
   - `2026-07-01` живой прогон на `TECNO LI9` подтвердил текущую рабочую точку:
     - системная геолокация на телефоне обязана быть включена, иначе приложение не получает свежий `Wi‑Fi`-скан;
     - после включения геолокации `MENU` честно видит рядом сеть `GOSHA-A-1BE1`;
     - системный Android-диалог на временную сеть робота открывается штатно;
     - после выбора `GOSHA-A-1BE1` встроенный `HotspotPortalActivity` снова открывает локальный портал прямо внутри приложения;
     - живой журнал подтвердил завершение портального шага:
       - страница дошла до `done.html`;
       - зафиксировано `Exit config mode request sent`;
       - робот вышел из своей точки доступа;
     - после возврата телефона в домашний `Wi‑Fi` приложение уже нашло робота локально по адресу `192.168.0.102`;
     - в `MENU` убран ложный статичный верхний статус, а промежуточный случай «робот уже вышел из режима подключения, но телефон ещё не вернулся домой» теперь показан отдельным честным текстом;
   - `2026-07-02` поверх этого внесён сетевой фикс:
     - `RobotWifiConnector` больше не пересаживает весь процесс приложения на сеть робота через `bindProcessToNetwork(...)`;
     - сеть робота остаётся только адресным локальным маршрутом для портала и локального `ws`;
     - панель и внешний контур должны снова ходить по обычному интернет-маршруту телефона;
   - `2026-07-02` тем же днём подтверждён живой эффект этого и следующего фикса:
     - на телефоне `TECNO LI9` при домашнем `Wi‑Fi` `AX3000T-4039` и активном VPN `run.nlab.iproxy` приложение больше не бросает локальную проверку заранее;
     - `MainActivity` теперь продолжает локальный поиск через `Wi‑Fi`-привязанный клиент даже при активном VPN;
     - из-за этого найден живой `robotHost = 192.168.1.159`;
     - панель получила свежее:
       - `mobile_presence = home_wifi_local`;
       - `local_host = 192.168.1.159`;
       - `connectivity.connected = true`;
     - `MENU` выдержан дольше 60 секунд без самопроизвольного отката назад;
     - ложная отправка `not_found` из VPN-ветки удалена, поэтому приложение больше не затирает панель неверным отрицательным сигналом.
   - `2026-07-02` следующим живым прогоном закрыт скрытый Android-дефект локального подтверждения робота:
     - робот и панель уже отвечали корректно, но ручной проверочный обмен запрос-ответ по `ws://<host>:8080/ws` внутри `LocalRobotDiscovery` выполнялся в главном потоке;
     - из-за `NetworkOnMainThreadException` локальный поиск на телефоне затягивался и мог ложно не подтверждать живой `robotHost`;
     - теперь этот проверочный обмен вынесен в рабочий поток;
     - в тот же контур добавлены:
       - быстрая приоритетная проверка сохранённого `robotHost`;
       - быстрый TCP-предфильтр `:8080`;
       - отдельный ручной `ws`-проверочный обмен уже вне главного потока;
     - живой результат после этого подтверждён:
       - `MENU` снова быстро показывает `Робот в сети`;
       - `robot_host` и `connector_robot_host` закрепляются как `192.168.1.159`;
       - `ConnectorForegroundService` живёт дольше 3 минут после сворачивания;
       - runtime панели остаётся свежим с `mobile_presence = home_wifi_local` и `local_host = 192.168.1.159`.
   - `2026-07-10` через `AI_OFFICE` проведён полный живой Android Wi-Fi цикл на `TECNO LI9`:
     - офисная задача: `task-20260709T172634Z-android-wi-fi`;
     - телефон виден как `119022547O005961`, системная геолокация включена, VPN выключен;
     - домашняя сеть: `AX3000T-4039`, телефон: `192.168.1.147`, робот после возврата: `192.168.1.159`;
     - путь `MENU -> Подключить к новому Wi-Fi -> GOSHA-A-1BE1 -> встроенный портал -> done.html -> MENU` завершён успешно;
     - системный выбор сети открылся сразу, но строка `GOSHA-A-1BE1` появилась примерно через `49` секунд; в это время Android показывал индикатор, а приложение — честный статус ожидания;
     - портал получил `200` и `63441` байт, после отправки настроек `done.html` пришёл примерно через `3,8` секунды;
     - возврат в домашний Wi-Fi занял около `2` секунд, локальный робот найден и передан панели примерно через `13` секунд;
     - после успешного возврата приложение осталось в `MENU` и назад в подключение не откатилось.
   - По живому журналу исправлена гонка портала:
     - раньше `MainActivity` начинала локальный поиск, пока `HotspotPortalActivity` ещё был открыт, освобождала сеть робота и получала пустой ответ портала;
     - теперь проверка после портала запускается только после результата `ActivityResultLauncher`;
     - правило покрыто тестами `ProvisionCoordinatorTest`.
   - Фоновая выдержка выявила отдельную особенность `TECNO/Transsion`:
     - оболочка `Hiber` замораживала UID через `5` секунд после `HOME` и уничтожала сокеты, несмотря на активную foreground-службу;
     - разрешение уведомлений и стандартный `deviceidle whitelist` по отдельности не устранили заморозку;
     - штатный выбор `Гоша -> Нет ограничений` в `com.transsion.batterylab.app_saving` вызвал `setAppMode(uid, 1)` и удалил приложение из карты `Hiber`;
     - после этого контрольные точки `T+0`, `T+30`, `T+120`, `T+195` сохранили один PID, активный `ConnectorForegroundService`, свежий `mobile_presence = home_wifi_local` и `local_host = 192.168.1.159`;
     - за интервал не было новых `FrozenState` и `socketDestroy`.
   - В клиент добавлены отдельный запрос уведомлений, точная OEM-инструкция, постоянные кнопки фоновых настроек и корректное закрытие проверочной WebSocket-сессии.
   - Финальный `clientDebug` собран и установлен:
     - SHA-256 `d353aafb47491f443c8290f999b6827ef1044077cbb189623fec872655e60ec7`;
     - `testClientDebugUnitTest`, `assembleClientDebug` и `lintClientDebug` завершились успешно.
   - `2026-07-11` через `AI_OFFICE` закрыт отдельный P0 локальных Android-`WebSocket`-сеансов:
     - главная задача: `task-20260711T063743Z-p0-android-tecno-li9-gosha-main`;
     - `LocalRobotProbeCoordinator` исключает параллельные локальные сеансы между экраном, службой и функциональными командами;
     - служба ведёт состояния `executed / skipped / stale`, счётчики и адаптивный интервал 10–60 секунд;
     - только `executed + ok=true` может обновлять `mobile_presence`;
     - `LocalWsHandshakeProbe` использует штатный `OkHttp WebSocket`, корректный close-frame и окно закрытия 1500 мс;
     - свежий успешный результат службы переиспользуется экраном не дольше 5 секунд, только для совпадающего сохранённого адреса и подсети; это убирает второй WebSocket и гонку ложного `robot not found`;
     - регрессионные тесты покрывают координатор, ограничение частоты, устаревший кеш, `SocketFactory`, отмену coroutine, close-frame и последовательное подтверждение адресов;
     - финальный установленный APK совпадает со сборкой, SHA-256 `66e18603d942583506c6d45c1bc4c40b2303ca68f8ce90f880e437c25e6729e4`;
     - живой прогон после `HOME` длился 234 секунды: PID `25464` не изменился, `ConnectorForegroundService` остался foreground-службой; основной журнал содержит 17 выполненных проверок, все `ok=true`;
     - панель получила свежий `mobile_presence = home_wifi_local`, `local_host = 192.168.1.159`;
     - поздний post-check дошёл до `executed=56` без `executed ok=false`;
     - журналы: `20260711-66e18603-final3-adb-260s.log` и `20260711-66e18603-final3-postcheck.log` в папке задачи AI_OFFICE.
   - `2026-07-11` по задаче `task-20260711T103436Z-2-done-html-14-29-06-wi-fi-14-2` внесена узкая Android-правка после воспроизведения дефекта второго цикла:
     - если после `done.html` панель уже видит робота, но локальный адрес ещё не подтверждён, Android больше не считает это завершением пост-портального поиска;
     - `CONNECTED_VIA_PANEL` теперь оставляет цикл возврата активным, запоминает `localHostHint` / `board_ip` и подставляет его в следующие вызовы `discoverRobotLocally(...)`;
     - после возврата телефона в домашний `Wi‑Fi` приложение делает ограниченные повторные локальные проверки, а не висит в `MENU` с бесконечным текстом про поиск адреса;
     - после исчерпания попыток показывается конечный честный статус: робот на связи с платформой, локальный адрес не подтверждён;
     - `CONNECTED_LOCALLY` по-прежнему завершает сценарий сразу, запускает `ConnectorForegroundService` и отправляет `mobile_presence = home_wifi_local`;
     - внешние raw TCP-проверки и внешние `WebSocket`-сеансы к `:8080` не добавлялись.
     - локальная проверка пройдена:
       - `ANDROID_HOME=<ANDROID_SDK_ROOT> ./gradlew --no-daemon testClientDebugUnitTest assembleClientDebug lintClientDebug`.
   - Живые циклы №2 и №3 на `TECNO LI9` завершили проверку этой правки:
     - в цикле №2 `done.html` пришёл в `14:29:06`, телефон вернулся в домашний `Wi-Fi` в `14:29:10`, но старый APK больше 3 минут показывал поиск локального адреса и не запускал `ConnectorForegroundService`; ручное возобновление экрана сразу нашло робота по `192.168.1.159`;
     - исправленный APK имеет SHA-256 `ed6a97dc1c4bc60aa334df17ff8bf18269e3c6aad79a6dbd51ab6b8c84ecbe09`; контрольная сумма установленного `base.apk` совпала;
     - в цикле №3 отправка настроек произошла в `16:05:51`, `done.html` получен через 3,9 секунды, домашний `Wi-Fi` подтверждён в `16:06:00`, а робот автоматически найден по `192.168.1.159` в `16:06:20` без открытия диагностического экрана;
     - `MENU` показал конечный статус «Робот в сети» и не вернул пользователя назад в подключение;
     - после сворачивания приложения на 196 секунд сохранились PID `31472`, `ConnectorForegroundService` с `isForeground=true` и последовательные `executed ok=true`;
     - панель после выдержки показывала свежий `mobile_presence = home_wifi_local`, `local_host = 192.168.1.159`, возраст сигнала 8 секунд;
     - полный журнал цикла №3: `<PRIVATE_VALIDATION_EVIDENCE>/task-20260711-cycle3-post-panel-only-fix/20260711-cycle3-complete-adb.log`, SHA-256 `12d2b3f039a2797acad5e1ed18f85dab4b1cbc885d3f427c761b646061515b09`.
   - Дополнительный контрольный цикл на сети `4G-CPE-1884` также завершён:
     - `GOSHA-A-1BE1` выбран системным Android-диалогом, встроенный портал открыт;
     - первый `POST /submit` в `20:43:06` завершился без ответа, повторный запрос в `20:44:12` получил `200` и `done.html` в `20:44:15`;
     - после возврата домой робот автоматически найден по `192.168.0.103` на седьмой попытке в `20:44:55`, примерно через `33` секунды, при честном видимом состоянии загрузки;
     - `MENU` показал «Робот в сети» без отката назад;
     - после сворачивания больше чем на три минуты служба сохранила `isForeground=true`, проверки — `executed ok=true`;
     - runtime панели был свежим: `home_wifi_local`, `local_host = 192.168.0.103`, возраст `1` секунда, `connectivity.evidence = local_host`;
     - журнал: `<PRIVATE_VALIDATION_EVIDENCE>/manual-20260711-android-cycle3/cycle3-adb.log`, SHA-256 `fca5bb635297d846aee0de32ca9840414674981e067f011913249d23341ca731`.
   - По задаче `task-20260711T165838Z-android-wi-fi-home-max-ai-office-local-only-ai-o` разобран первый неуспешный `POST /submit`:
     - запрос в `20:43:06` шёл через сеть робота `candidate=574`, но не получил HTTP-ответ (`code=0`, пустые `type` и `bytes=0`);
     - последующая попытка `candidate=default` ушла через обычный маршрут телефона с исходным адресом `10.202.109.230`, то есть не могла корректно достучаться до портала `192.168.4.1`;
     - внесена узкая правка: при наличии сети робота `RobotPortalClient` не использует обычный маршрут телефона для `192.168.4.1` / `robot.local`, но сохраняет его для внешних URL и для случая, когда сеть робота неизвестна;
     - добавлен unit-тест политики маршрута портала;
     - статус после отправки настроек стал честнее: приложение ждёт ответ портала или переход к завершению, а не утверждает преждевременно, что настройки уже приняты;
     - действие `MENU` переподключения переименовано в «Сменить Wi‑Fi робота», поднято выше и оставлено основной кнопкой; информационная кнопка теперь «Статус и диагностика» во вторичном стиле;
     - локальная проверка пройдена:
       - `ANDROID_HOME=<ANDROID_SDK_ROOT> ./gradlew --no-daemon assembleClientDebug testClientDebugUnitTest lintClientDebug`.
   - Независимый reviewer не нашёл P0/P1, после чего установлен APK SHA-256 `5b7f253ddba0736b216b6d520352901d657edd8bc04371171498fb135fd90ca1` и выполнен живой цикл:
     - портал и ресурсы шли только через сеть робота; после временного `code=0` на `/scan` и первого `POST /submit` перехода на `candidate=default` не было;
     - первый `POST /submit` в `21:28:10` через `candidate=576` всё ещё дал `code=0`;
     - повторный запрос в `21:29:02` через `candidate=578` получил `200`, затем `done.html` получил `200`;
     - телефон вернулся в `4G-CPE-1884`, робот найден по `192.168.0.103`, `MENU` и свежий `home_wifi_local` восстановились;
     - Android-P1 запасного маршрута закрыт; причина первого `code=0` требует следующего прогона с заранее включённым UART прошивки;
     - журнал `<PRIVATE_VALIDATION_EVIDENCE>/manual-20260711-portal-route-fix-live/portal-route-fix-adb.log`, SHA-256 `0836673d4d4cab3aa8ec8405b11bf5e50057c43bf547e05d4062def1129d815c`.
   - `2026-07-12` по задаче `task-20260712T065931Z-gosha-mobile-home-max-worktrees-gosha-mobile-portal-featu` внесена Android-правка восстановления портала после ручного выключения/включения `Wi‑Fi` телефона:
     - потерянный `Network` робота сбрасывается при следующем обращении к `RobotWifiConnector`;
     - `HotspotPortalActivity` при потере маршрута к `192.168.4.1` показывает статус переподключения и заново запускает системный запрос сети `GOSHA-*` / `Xiaozhi-*`;
     - `RobotPortalClient` больше никогда не добавляет `candidate=default` для `192.168.4.1` и `robot.local`;
     - выход назад до отправки формы возвращает пользователя в шаг повторного подключения, а не запускает пост-портальный поиск;
     - добавлены регрессионные unit-тесты политики маршрута и отмены портала до отправки;
     - локальная проверка пройдена:
       - `ANDROID_HOME=<ANDROID_SDK_ROOT> ./gradlew --no-daemon assembleClientDebug testClientDebugUnitTest lintClientDebug`.
   - Внешний P1 операторской диагностики платформы исправлен отдельно:
     - ветка `hotfix/edge-hub-probe-state`, коммит `10bcbf1`;
     - draft PR `https://github.com/MaxCorpOrg/GOSHA_PLATFORM/pull/25`;
     - платформа учитывает `robot_ws_probe_state` и возраст кеша, сохраняя совместимость со старым `robot_ws_ok`.
   - основные файлы последней серии этой точки:
     - `app/src/main/java/com/maxcorp/edgeconnector/MainActivity.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/LocalRobotDiscovery.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/LocalPortProbe.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/LocalWsHandshakeProbe.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/RobotPortalClient.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/RobotWifiConnector.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/WifiInfoHelper.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/ProvisionCoordinator.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/RobotConnectivityResolver.kt`
     - `app/src/main/java/com/maxcorp/edgeconnector/OnboardingCoordinator.kt`
     - `app/src/main/res/layout/activity_main.xml`
     - `app/src/main/res/values/strings.xml`
     - `app/src/main/java/com/maxcorp/edgeconnector/HotspotPortalActivity.kt`
     - `app/src/test/java/com/maxcorp/edgeconnector/RobotPortalClientRoutePolicyTest.kt`
     - `app/src/test/java/com/maxcorp/edgeconnector/ProvisionCoordinatorTest.kt`
6. Актуальный результат и следующий приоритет:
   - правка `2026-07-12` прошла независимые review без оставшихся P0/P1/P2, перенесена в каноническую ветку коммитом `86fd525` и отправлена в GitHub;
   - живой цикл подтвердил восстановление после ручного выключения/включения `Wi‑Fi` без перезапуска приложения и без `candidate=default` для `192.168.4.1`;
   - `POST /submit` получил `200` примерно через `3,3` секунды, затем `done.html` и `/exit` получили `200`;
   - робот вернулся в `AX3000T-4039` с адресом `192.168.1.159`, приложение осталось в `MENU`, панель получила свежий `home_wifi_local` с `local_host`;
   - после `HOME` больше `218` секунд сохранились PID `19519`, `ConnectorForegroundService` и выполненные успешные проверки робота;
   - установленный APK имеет SHA-256 `2d107ddf596bbbaf1e6eaff680078fa3ef41552bd29cf5bb1f9d4ff9650683d1`;
   - Android-P2 по повторным системным запросам сети закрыт кодом в канонических коммитах `64d3ce2` и `d2f04fe`: `HotspotPortalActivity.onResume()` сверяет фактическое состояние `Wi‑Fi`, сбрасывает cooldown при `Enabled/Enabling`, сразу вызывает `requestRobotWifiReconnectIfNeeded()` до `submit/completed`, сохраняет blocked-статус при `Disabled/Disabling` и не дублирует активный запрос;
   - этот P2 покрыт `PortalWifiReconnectPolicyTest` для сценария `onStop -> Wi‑Fi enabled -> onResume`, `Enabling`, active request и `submitted/completed` guard; локально пройдены `git diff --check` и `ANDROID_HOME=<ANDROID_SDK_ROOT> ./gradlew --no-daemon assembleClientDebug testClientDebugUnitTest lintClientDebug`;
   - первичный reviewer нашёл P2 пропущенного `WIFI_STATE_CHANGED_ACTION` во время `onStop`, fixer закрыл его, финальный reviewer не нашёл P0/P1/P2;
   - канонический прогон `assembleClientDebug testClientDebugUnitTest lintClientDebug` завершился `BUILD SUCCESSFUL in 2m 21s`;
   - живое подтверждение этого P2 завершено `2026-07-21`: новый диалог после `submit` не появился, возврат в `MENU`, панель и фоновая служба подтверждены;
   - не использовать отвергнутый общий ограничитель громкости `30`: он сделал подсказку слишком тихой;
   - P0 локальных `WebSocket`-подключений Android закрыт кодом, тестами и живой выдержкой;
   - чистая парная диагностика прошивки и живой Android-контроль политики маршрута портала подтверждены;
   - платформенный P1 по `robot_ws_probe_state` слит в `GOSHA_PLATFORM` PR `#25`, merge-коммит `ae72eea`; не дублировать его в Android-коде;
   - следующий основной Android-приоритет — восстановление без полного перезапуска после временной потери домашнего Wi-Fi или недоступности робота;
   - затем обработать единичный `code=0` на `/scan` без JavaScript-ошибки и без возврата `candidate=default`;
   - отдельно исправить правило OEM-подсказки: нажатие `Позже` или простое открытие настроек не должно навсегда скрывать инструкцию до подтверждения режима `Нет ограничений`;
   - сохранять живые логи `HotspotPortal`, `GoshaRobotWifi`, `RobotPortalClient`, `ConnectorForegroundService`, `MaxRobotFlow` и UART прошивки;
7. Для iOS ближайшая точка продолжения теперь зафиксирована в:
   - `docs/GOSHA_MOBILE_IOS_HANDOFF_RU.md`
   Там следующий шаг:
   - при iOS-first работе перейти в отдельный репозиторий `GOSHA_MOBILE_IOS`;
   - использовать совместимый с `Xcode 13.2.1` iPhone/iOS или более новый Mac/Xcode;
   - либо сначала достать настоящий `DeviceSupport/DDI` для `26.1 (23B85)` из более нового Xcode;
   - затем открыть готовый `.xcodeproj` в Xcode;
   - настроить signing;
   - проверить живой сценарий на iPhone.
8. Отдельно позже добить живую радиопроверку переходного префикса `Xiaozhi-*`.
9. Не путать мобильные симптомы с голосовым движком:
   - жалобы на тембр и разнообразие голоса закрываются в `GOSHA_PLATFORM`, а не в Android-клиенте.
10. Если позже появятся новые локальные `AGENTS.md`, переносить в них тот же блок правил русского технического языка.
