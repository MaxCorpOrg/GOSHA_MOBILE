# Android CI и матрица приёмки

## Назначение

Этот документ фиксирует воспроизводимую матрицу для Android-кандидата
`clientDebug`. CI закрывает только статические, unit/build/lint и
read-only проверки. Живой runtime smoke, то есть быстрый живой прогон,
здесь не подтверждается: для него
нужны отдельное разрешение оператора, телефон и робот.

## CI-контур

- Запуск: Draft/Open pull request и ручной `workflow_dispatch`.
- Права workflow: только `contents: read`.
- Toolchain: JDK 17, Android SDK `platforms;android-34`,
  `build-tools;34.0.0`.
- Gradle-задачи: `testClientDebugUnitTest`, `assembleClientDebug`,
  `lintClientDebug`, `:app:verifyReleaseRuntimeConfig`.
- Release-config guard проверяется в двух режимах:
  - пустые URL должны падать;
  - обязательные тестовые URL используют только
    `https://panel.example.invalid`.
- CI не использует production secrets, production signing, `assembleClientRelease`,
  release publication, `adb`, телефон, робот, motion, flash или trim.
- CI сохраняет `app-client-debug.apk` и рядом файл
  `app-client-debug.apk.sha256`.

## Матрица последующей живой приёмки

| Блок | Что проверять | Доказательство | Ограничение |
| --- | --- | --- | --- |
| Onboarding | Fresh install не имеет встроенного адреса панели; activation принимает адреса только из runtime-пакета | Экран регистрации, сохранённый draft без production endpoint | Не использовать реальные URL в документах |
| Identity | `robot_id` и `device_id` не смешиваются; чужой `device_id` не подтверждает host | Локальный read-only `gosha.identity.get/result`, unit-тесты identity | Не делать общий subnet sweep без ожидаемой личности |
| Network loss | Временная потеря домашнего `Wi-Fi` переводит состояние в честный `running/unavailable` | Runtime-события без SSID и без локальных секретов | Не объявлять восстановление до локального подтверждения |
| Network return | Возврат домашнего `Wi-Fi` завершает recovery только после прямой локальной проверки робота | Один `correlation_id`, `ready/completed` после local probe | Не считать сигнал панели прямым local host |
| Background | Foreground service сохраняет свежий `mobile_presence` после `HOME`; OEM-подсказка не закрывается нажатием `Позже` | Журнал службы, runtime панели, версия background guidance | Нужен реальный телефон |
| Events | Outbox сохраняет порядок, не дублирует события и не публикует устаревший run | Unit-тесты `RuntimeEventReporter` и `ConnectorRunRegistry` | Не передавать SSID, токены и production endpoints |
| No-motion | Проверки не вызывают motion, flash, trim, gateway или firmware deployment | `scripts/verify_android_no_motion_config_matrix.sh`, CI logs | Только read-only до отдельного разрешения |

## Минимальный критерий кандидата

- CI прошёл все Gradle и guard-проверки.
- Debug APK создан как CI artifact.
- SHA-256 APK зафиксирован в artifact и отчёте.
- Живой smoke отдельно помечен как не выполненный, если не было телефона и
  робота.
