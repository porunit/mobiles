# Архитектура системы

Документ описывает целевую архитектуру экосистемы трейдинга/инвестиций: из каких
частей состоит система, как части связаны и как взаимодействуют. Это основной
проектный документ — он ведётся вместе с кодом и обновляется при изменениях.

> Диаграмма топологии — `docs/diagrams/architecture.svg` (и в чате проекта).

## 1. Обзор

Система имитирует экосистему брокера: данные о котировках поступают с «биржи»
(эмулятор на C: user-space-демон в итер. 1, модуль ядра Linux — шаг 2), проходят через конвейер обработки и доходят до
мобильного терминала клиента; клиент совершает сделки купли/продажи, которые
исполняются атомарно и сохраняются в БД.

Целевая нагрузка — до 10 000 клиентских сессий. Система спроектирована как набор
независимых сервисов, общающихся через единый контракт REST API и брокер сообщений.

## 2. Принципы

1. **Реальный конвейер котировок.** Цена доходит до клиента строго по цепочке
   драйвер → receiver → брокер → DB-сервис → шлюз. Ни один сервис не выдумывает
   цены самостоятельно (ключевая ошибка эталона `Investment-pp` — см. §10).
2. **Единый контракт API.** Все клиенты (Android, React Native) и сервисы (шлюз,
   DB-сервис) говорят на одном языке — `/api/v1/*`. Контракт — источник истины.
3. **Шлюз реально терминирует запросы.** Ktor-шлюз — единственная публичная точка
   входа; он проверяет JWT и проксирует запрос в DB-сервис, возвращая реальный ответ.
4. **Корректность сделок важнее скорости.** Списание/зачисление баланса защищено
   пессимистичной блокировкой строки (`SELECT … FOR UPDATE`).
5. **Наблюдаемость с первого дня.** Все сервисы экспортируют трейсы/метрики/логи
   через OpenTelemetry.

## 3. Состав системы

| Модуль | Назначение | Стек | Итер. |
|--------|-----------|------|-------|
| `1-android-app` | Нативный мобильный терминал | Kotlin, Jetpack Compose, Retrofit, MVVM | 1 |
| `2-cross-platform-app` | Кросс-платформенный клиент | React Native, Expo | 2 |
| `3-gateway` | API-шлюз: JWT, passthrough, rate-limit | Kotlin, Ktor, HttpClient CIO | 1 |
| `4-db-service` | Торговое ядро, работа с БД, консьюмер котировок | Kotlin, Spring Boot 3, JPA | 1 |
| `5-load-imitator` | Имитатор 10k клиентов | Kotlin, корутины | 2 |
| `6-quotes-receiver` | Мост `/dev` → RabbitMQ | Go | 1 |
| `7-linux-driver` | Эмулятор биржи (NDJSON через FIFO; char-device — шаг 2) | C: user-space (итер. 1) → модуль ядра Linux | 1 |

Инфраструктура: **RabbitMQ** (брокер) · **Redis** (кэш) · **PostgreSQL** (состояние) ·
**ClickHouse** (аналитика, тики, логи/трейсы) · **OpenTelemetry Collector** →
Jaeger (трейсы) / Prometheus (метрики) / Grafana (панели).

## 4. Единый контракт API (`/api/v1`, через шлюз)

| Метод | Путь | Auth | Назначение |
|-------|------|------|-----------|
| POST | `/auth/register`, `/auth/login` | — | регистрация / вход → `AuthResponse` |
| GET | `/quotes`, `/quotes/{ticker}` | — | котировки из реального конвейера |
| GET | `/quotes/{ticker}/history` | — | свечи (ClickHouse, итер. 2) |
| GET | `/instruments`, `/stocks?page=&size=` | — | каталог инструментов |
| GET | `/portfolio`, `/portfolio/positions` | JWT | портфель, баланс, P&L |
| POST | `/orders/buy`, `/orders/sell` | JWT | сделка; цена **только серверная** |
| GET | `/orders?page=&size=` | JWT | история сделок |
| GET/POST | `/wallet/balance`, `/wallet/deposit`, `/wallet/withdraw` | JWT | кошелёк |
| GET/PUT/DELETE | `/users/me` | JWT | профиль |
| GET | `/analytics/*` | mix | аналитика (ClickHouse, итер. 2) |
| GET | `/health` | — |健康 |

**Соглашения.** Деньги и количество — JSON-строки с десятичным значением (точность
`BigDecimal`); время — ISO-8601 UTC; ошибки — единое тело
`{ timestamp, status, error, code, message, path, traceId }` (клиент ветвится по
стабильному `code`); пагинация `?page=&size=` → `{ content, totalElements, totalPages, … }`.

**JWT.** HS256, общий секрет шлюза и DB-сервиса. Claims: `sub`=userId, `email`,
`roles`, `typ`, `iss`, `aud`, `exp`. Access-токен TTL 24ч (итер. 1; refresh-токены —
итер. 2). Шлюз проверяет JWT на входе, DB-сервис перепроверяет (defence in depth).

Полный контракт с телами запросов/ответов — `docs/openapi/` (OpenAPI, ведётся отдельно).

## 5. Сквозные потоки данных

### 5.1 Котировка: биржа → клиент
1. `7-linux-driver` — эмулятор биржи: random-walk fixed-point (×10⁶), 200 тиков/с на
   инструмент. **Итерация 1** — user-space-демон на C (`userspace/quote_gen.c`): создаёт
   FIFO на общем томе `dev_financial_quotes` и стримит **NDJSON** (одна `QuoteTick` на
   строку). **Шаг 2** — модуль ядра с char-device `/dev/financial_quotes` и управлением
   через sysfs `/sys/kernel/financial_quotes/`, отдающий тот же wire-контракт.
2. `6-quotes-receiver` (Go) читает устройство (FIFO), парсит NDJSON, и **только
   публикует** в RabbitMQ:
   exchange `quotes.topic`, routing key `quote.tick.<SYMBOL>`, с publisher-confirms.
3. `4-db-service` — `QuoteTickListener` на очереди `quotes.consume.q` (ручной `ack`
   после обработки): трансформирует `QuoteTick → Quote` (join с `instruments`),
   пишет в Redis `quote:last:{symbol}` (TTL 5с) и батчом в ClickHouse `quote_ticks`.
4. Клиент: `GET /quotes` через шлюз → `QuoteQueryService` (Redis → fallback Postgres)
   → реальная свежая цена.

### 5.2 Сделка: клиент → состояние
1. Android → шлюз `POST /orders/buy {ticker, quantity}` (JWT).
2. Шлюз проверяет JWT, проксирует в DB-сервис (клиентский `price` отбрасывается).
3. DB-сервис: `@Transactional` + `SELECT … FOR UPDATE` строки пользователя; проверка
   баланса; цена из `quote:last`; обновление позиции; запись `Order` в PostgreSQL.
4. Синхронный ответ — реальный `Order`. Параллельно — батч в ClickHouse `order_history`.

## 6. Модель данных

### PostgreSQL (состояние)
`users` (email, password_hash, cash_balance `NUMERIC(20,8)`), `instruments`,
`orders`, `portfolio_positions` (уникальный `(user_id, instrument_id)` — строка под
`FOR UPDATE`). DDL — `4-db-service/.../db/migration/V1__core.sql`. Деньги — fixed-point
`NUMERIC(20,8)`. Миграции — Flyway (итер. 2; в вертикали допустим `ddl-auto=update`).

### Redis (кэш, не брокер)
| Ключ | TTL | Назначение |
|------|-----|-----------|
| `quote:last:{symbol}` | 5с | последняя котировка |
| `instrument:all` | 5м | список инструментов |
| `portfolio:{userId}` | 30с | кэш портфеля |
| `session:jwt:deny:{jti}` | = TTL токена | отзыв токенов (итер. 2) |
| `ratelimit:{userId}:{window}` | 1с | rate-limit шлюза |

Источник истины сделок — всегда блокировка строки в Postgres; Redis — best-effort.

### RabbitMQ (брокер)
Topology-as-code в `docker/rabbitmq/definitions.json`:
- exchange `quotes.topic` (topic) → `quotes.consume.q` (TTL 10с, `drop-head`) +
  `quotes.clickhouse.q` (без TTL — аналитика ловит всё); DLX `dlx.quotes`. Очереди
  `*.dlx.q` ограничены policy `dlx-bound` (max-length 50k, `drop-head`), чтобы DLX
  не рос безгранично. В итер. 1 у `quotes.clickhouse.q` ещё нет консьюмера — db-service
  (§5.1 шаг 3) пишет `quote_ticks` напрямую из `quotes.consume.q`; отдельный
  analytics-worker для `quotes.clickhouse.q` — позже.
- exchange `orders.topic` → `orders.clickhouse.q` (итер. 2); DLX `dlx.orders`.

### ClickHouse (аналитика)
`quote_ticks` (MergeTree, партиции по дням, TTL 90д) + `quote_ohlc_1m`
(AggregatingMergeTree + materialized view) + `order_history` (TTL 365д). Логи и трейсы
OTel — таблицы `otel_logs`/`otel_traces` (создаёт коллектор). DDL —
`docker/clickhouse/init/01_schema.sql`.

## 7. Развёртывание

Единый `docker/docker-compose.yaml`. Инфраструктура запускается по умолчанию;
прикладные сервисы — профиль `apps`, имитатор — профиль `imitator`.

Порты (в dev публикуются на loopback `127.0.0.1`, не на `0.0.0.0`): шлюз **8080**
(единственный публичный для клиентов), DB-сервис 8081, go-receiver 8090,
RabbitMQ 5672/15672, Redis 6379, PostgreSQL 5432, ClickHouse 8123/9000,
OTel 4317/4318/8889, Jaeger 16686, Prometheus 9090, Grafana 3000.

### C-драйвер на macOS
**Итерация 1 (текущая).** `driver-container` — обычный (непривилегированный) контейнер,
запускающий user-space-демон `quote_gen` (`7-linux-driver/Dockerfile`, статический бинарь).
Он создаёт FIFO `financial_quotes` на named-volume `dev_financial_quotes` и стримит NDJSON;
`go-receiver` монтирует тот же volume `:ro` и читает FIFO. Кросс-платформенно, без
привилегий и без зависимости от ядра хоста.

**Шаг 2 (реальный модуль ядра).** На macOS нет ядра Linux — Docker Desktop запускает всё
в LinuxKit-VM, поэтому модуль грузится в ядро **этой VM**. Механика: Dockerfile собирает
`financial_quotes.ko`; контейнер `driver-container` с `privileged`, `cap_add: [SYS_MODULE,
MKNOD]` и mount `/lib/modules:ro` делает `insmod` и `mknod` char-device на том же
named-volume (bind-mount Linux-устройства на mac невозможен). Оба контейнера делят одно
ядро VM; wire-контракт (NDJSON) не меняется, поэтому `go-receiver` остаётся прежним.

## 8. Наблюдаемость

Все сервисы → OTel Collector `:4317`. Трейсы → Jaeger (+ архив в ClickHouse), метрики
→ Prometheus (scrape коллектора `:8889`), логи → ClickHouse. Единая панель — Grafana
с тремя источниками (Prometheus, ClickHouse, Jaeger). Трейс начинается на go-receiver
(драйвер не эмитит OTLP; его телеметрия — логи/счётчики). Заголовок контекста —
W3C `traceparent`.

## 9. Тестирование

- **Модульные:** торговая логика, расчёт портфеля, трансформация `QuoteTick→Quote`,
  парсер receiver, генератор драйвера.
- **Интеграционные** (Testcontainers): publish тика → `GET /quotes/{ticker}`;
  доверие JWT шлюз↔DB; сделка без гонок.
- **Системные:** полная вертикаль E2E (драйвер → … → Android).
- **Обязательные** (которых не было у эталона): OpenAPI-conformance, тест
  трансформации котировки, E2E вертикали, негативный «DB не выдумывает цены»,
  JWT-доверие шлюз↔DB.
- **10k клиентов:** экстраполированный бенчмарк (прогон 1k → расчёт), с тюнингом пула
  Hikari и `max_connections` Postgres и сэмплингом трейсов ≤10%. Стенд (CPU/RAM)
  фиксируется в отчёте; это не утверждение, что ноутбук держит 10k вживую.

## 10. Уроки эталона `Investment-pp`

Что в эталоне сломано и что мы делаем иначе:
- Ktor-шлюз публиковал заказы в очередь, которую никто не читал, и не был в compose →
  у нас шлюз терминирует запросы и развёрнут.
- DB-сервис **выдумывал цены сам** (`MarketDataService.tick()`) → у нас он консьюмер
  реального конвейера.
- C-драйвер → Go-receiver был тупиком → у нас это единственный источник котировок.
- Не было единого контракта (RN говорил на «диалекте шлюза») → у нас один контракт.
- Не было ClickHouse, OpenTelemetry, тестов → добавлено.

## 11. Журнал решений

| Решение | Выбор | Причина |
|---------|-------|---------|
| Бэкенд DB-сервиса | Spring Boot + JPA | как в эталоне (по согласованию) |
| Брокер | RabbitMQ | как в эталоне; Redis — только кэш |
| ClickHouse + OTel | внедряем полноценно | требование задания |
| C-драйвер | user-space NDJSON-эмулятор (FIFO) в итер. 1; реальный модуль ядра — шаг 2 | требование задания; user-space снимает зависимость от ядра хоста на старте, контракт не меняется |
| Источник цен | реальный конвейер | исправление главной ошибки эталона |
| Refresh-токены | итерация 2 | упрощение вертикали; access TTL 24ч |
| Заказ → ClickHouse | прямой батч-JDBC (итер. 1) | проще; брокерный путь — итер. 2 |
