# 3-gateway — API-шлюз (Ktor)

**Итерация 1.** Статус: **реализован и проверен** (JWT на входе + passthrough +
CORS + rate-limit).

Единственная публичная точка входа для клиентов. Проверяет JWT и проксирует запросы в
DB-сервис, **возвращая реальный ответ** (в эталоне шлюз был оторван — см.
`[[adr-0002-real-quote-pipeline]]`). Стек — Kotlin + Ktor. Порт `8080`.

## Что делает
- Терминирует канонический `/api/v1/*` (см. `docs/ARCHITECTURE.md` §4).
- **Enforce JWT** на защищённых маршрутах (кроме `/auth/*`, `/health`); невалидный
  токен → `401` с единым телом ошибки (не проксирует).
- HTTP-passthrough в `DB_SERVICE_BASE_URL` через Ktor `HttpClient` (CIO, пул под 10k),
  сохраняя метод/тело/`Idempotency-Key`/контекст трейса.
- Цена сделки — только серверная: DB-сервис игнорирует любой клиентский `price`
  (в контракте `/orders/*` его нет), шлюз прозрачно проксирует.
- Rate-limit per-IP (Ktor RateLimit, in-memory; распределённый на Redis — итер. 2),
  CORS только для web-сборки RN.
- НЕ кэширует котировки и НЕ ходит в receiver — всё через DB-сервис.

## ENV
`GATEWAY_PORT=8080` · `DB_SERVICE_BASE_URL=http://db-service:8081` · `JWT_SECRET`
(тот же, что у DB-сервиса) · `JWT_ISSUER` · `JWT_AUDIENCE` · `CORS_ALLOWED_HOSTS` ·
`RATE_LIMIT_RPS` · `OTEL_*`.

## Реализация
`Application.kt` (плагины + маршруты: JWT-auth, RateLimit, CORS, Micrometer) ·
`Proxy.kt` (прозрачный reverse-proxy в DB-сервис). OTel — через java-agent в `Dockerfile`,
сборка — fat jar (Ktor Gradle plugin).
