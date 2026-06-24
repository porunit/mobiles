# 4-db-service — торговое ядро и работа с БД (Spring Boot)

**Итерация 1.** Статус: спроектировано, реализация не начата.

Ядро системы: аутентификация, инструменты, портфель, сделки, потребление котировок.
Стек — Spring Boot 3 + Spring Data JPA (`[[adr-0001-backend-stack]]`). Порт `8081`.

## Что делает
- Отдаёт канонический REST `/api/v1/*` (см. `docs/ARCHITECTURE.md` §4).
- **Консьюмер котировок**: `QuoteTickListener` на `quotes.consume.q` (ручной `ack`),
  трансформация `QuoteTick → Quote` (join с `instruments`), запись в Redis
  `quote:last:{symbol}` (TTL 5с) и батчом в ClickHouse `quote_ticks`.
- **Сделки**: `@Transactional` + `SELECT … FOR UPDATE` (`[[pessimistic-locking]]`),
  цена из Redis, запись `Order` в Postgres + батч в ClickHouse `order_history`.
- **НЕ выдумывает цены** (удалён `MarketDataService.tick()` эталона).

## Данные
PostgreSQL: `users, instruments, orders, portfolio_positions`
(DDL — `src/main/resources/db/migration/V1__core.sql`). Деньги `NUMERIC(20,8)`.

## Безопасность
JWT HS256 (общий секрет со шлюзом), claims `sub`=userId/`email`/`roles`. Перепроверяет
токен даже после шлюза (defence in depth). `BigDecimal` ↔ строка на границе клиента.

## ENV
`POSTGRES_*`, `REDIS_*`, `RABBITMQ_*`, `RABBITMQ_QUOTES_QUEUE=quotes.consume.q`,
`CLICKHOUSE_*`, `JWT_SECRET`, `JWT_ACCESS_TTL_SECONDS=86400`, `OTEL_*`.

## Обязательные тесты
Трансформация `QuoteTick→Quote`; сделка без гонок; негативный «DB не выдумывает цены»
(драйвер выключен → `/quotes` не врёт); JWT-доверие со шлюзом.
