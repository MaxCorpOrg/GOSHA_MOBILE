# AGENTS.md

`ios/` — локальный iOS-каркас клиента `Гоша` для App Store.

## Перед правками

1. Прочитай:
   - `../AGENTS.md`
   - `../docs/NEW_CHAT_CHECKPOINT_RU.md`
   - `../docs/AGENT_CHECKPOINT_RU.md`
   - `../docs/PROJECT_STATUS_RU.md`
   - `<PLATFORM_WORKSPACE>/docs/GOSHA_PROJECT_MAP_RU.md`
2. Проверь, не ломает ли изменение:
   - `/api/mobile/resolve-code`
   - `/api/mobile/activate-code`
   - `/api/mobile/robots/<robot_id>/runtime`
   - шаг подключения к `GOSHA-` и переходную совместимость с `Xiaozhi-`
   - открытие локального портала `http://192.168.4.1`

## Правила

- Пользовательский бренд приложения — `Гоша`.
- Не возвращай iOS-клиент к старым адресам `8876/8890`.
- Техническая поддержка `Xiaozhi-` допустима только как переходный механизм.
- Runtime-решения в iOS должны опираться на блок `connectivity` из `GOSHA_PLATFORM`, а не на старые косвенные эвристики.
- Для App Store поток нужно держать честным:
  - код подключения;
  - явный шаг входа в Wi-Fi робота;
  - локальный портал;
  - повторная проверка состояния после выхода из hotspot.

## Локальная проверка

```bash
cd <LEGACY_IOS_WORKSPACE>
SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)
xcrun swiftc -typecheck -sdk "$SDK" -target x86_64-apple-ios15.0-simulator $(find GoshaMobileIOS -name '*.swift' | sort)
```
