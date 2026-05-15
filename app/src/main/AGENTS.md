# AGENTS.md

Эта папка отвечает за Android-клиент `Гоша` на уровне пользовательского интерфейса, локального портала и подключения к роботу.

## Перед работой прочитать

1. `/home/max/GOSHA_MOBILE/AGENTS.md`
2. `/home/max/GOSHA_MOBILE/docs/PROJECT_STATUS_RU.md`
3. `/home/max/GOSHA_MOBILE/docs/NEW_CHAT_CHECKPOINT_RU.md`
4. Если правка затрагивает серверный пакет подключения:
   - `/home/max/GOSHA_PLATFORM/docs/GOSHA_PROJECT_MAP_RU.md`
   - `/home/max/GOSHA_PLATFORM/docs/PROJECT_STATUS_RU.md`

## Что здесь искать

- `java/com/maxcorp/edgeconnector`
  - основной исходный код приложения
  - это исторический путь исходников после одноразового копирования, а не старый продуктовый бренд
- `res/layout`
  - экраны приложения
- `res/values`
  - строки, цвета, размеры

## Главные правила

- В пользовательском тексте показывать бренд `Гоша`.
- В логике сети робота принимать оба префикса:
  - `GOSHA-`
  - `Xiaozhi-`
- Не возвращать клиент на старые адреса `8876/8890`.
- После Android-правок обязательно проверять:
  - `./gradlew --no-daemon assembleClientDebug`
  - `./gradlew --no-daemon testClientDebugUnitTest`
