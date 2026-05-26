# START HERE

Если новый агент входит в `GOSHA_MOBILE`, начинай отсюда.

## Сначала прочитать

1. `AGENTS.md`
2. `/home/max/GOSHA_PLATFORM/docs/GOSHA_PROJECT_MAP_RU.md`
3. `README_RU.md`
4. `docs/NEW_CHAT_CHECKPOINT_RU.md`
5. `docs/AGENT_CHECKPOINT_RU.md`
6. `docs/PROJECT_STATUS_RU.md`
7. Если работаешь с Android-кодом:
   - `app/AGENTS.md`
8. Если работаешь с iOS-кодом:
   - `ios/AGENTS.md`
   - `docs/GOSHA_MOBILE_IOS_HANDOFF_RU.md`
   - при iOS-first задаче предпочитай отдельный репозиторий:
     - `/Users/maksim/Developer/GOSHA_MOBILE_IOS`
     - `https://github.com/MaxCorpOrg/GOSHA_MOBILE_IOS`

## Что это за проект

- Это отдельный Android-клиент платформы `Гоша`.
- Теперь это Android-first репозиторий.
- Он больше не должен развиваться как часть `AI_ROBOT`.
- Для iOS уже выделен отдельный репозиторий:
  - `https://github.com/MaxCorpOrg/GOSHA_MOBILE_IOS`
- Старый клиент из `AI_ROBOT` остаётся легаси-контуром для сравнения и миграции.

## Что важно помнить

- Пользовательский бренд: `Гоша`
- Новый рабочий контур по умолчанию:
  - панель `18876`
  - серверный контур `18080`
- Клиент должен принимать оба префикса Wi‑Fi робота:
  - `GOSHA-`
  - `Xiaozhi-`
- В текстах и интерфейсе пользователю показывать только `GOSHA-`
- Если нужно быстро свериться, где серверный голос, панель и прошивка, смотри:
  - `/home/max/GOSHA_PLATFORM/docs/GOSHA_PROJECT_MAP_RU.md`

## Обязательный старт нового чата

Перед тем как предлагать работу, агент обязан:

1. Прочитать этот файл и актуальные checkpoint-документы.
2. Выполнить `git status --short --branch`.
3. Выполнить `git log --oneline -1`.
4. Если задача затрагивает общий контур `Гоша`, свериться со свежим входным файлом на рабочем столе.
5. В первом содержательном ответе явно сообщить:
   - текущую ветку;
   - текущую стадию;
   - следующий приоритетный шаг.
