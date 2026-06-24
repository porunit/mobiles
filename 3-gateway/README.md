# 3-gateway — API-шлюз (Ktor)

**Итерация 1.** Статус: спроектировано, реализация не начата.

Единственная публичная точка входа для клиентов. Проверяет JWT и проксирует запросы в
DB-сервис, **возвращая реальный ответ** (в эталоне шлюз был оторван — см.
`[[adr-0002-real-quote-pipeline]]`). Стек — Kotlin + Ktor. Порт `8080`.

## Что делает
- Терминирует канонический `/api/v1/*` (см. `docs/ARCHITECTURE.md` §4).
- **Enforce JWT** на защищённых маршрутах (кроме `/auth/*`, `/health`); невалидный
  токен → `401` с единым телом ошибки (не проксирует).
- HTTP-passthrough в `DB_SERVICE_BASE_URL` через Ktor `HttpClient` (CIO, пул под 10k),
  сохраняя метод/тело/`Idempotency-Key`/контекст трейса.
- **Отбрасывает клиентский `price`** в `/orders/*` (цена только серверная).
- Rate-limit (Redis), CORS только для web-сборки RN.
- НЕ кэширует котировки и НЕ ходит в receiver — всё через DB-сервис.

## ENV
`GATEWAY_PORT=8080` · `DB_SERVICE_BASE_URL=http://db-service:8081` · `JWT_SECRET`
(тот же, что у DB-сервиса) · `JWT_ISSUER` · `JWT_AUDIENCE` · `CORS_ALLOWED_HOSTS` ·
`RATE_LIMIT_RPS` · `OTEL_*`.

## Файлы (план)
`Application.kt` · `plugins/Security.kt` (JWT) · `plugins/Routing.kt` (проксирование) ·
`plugins/Monitoring.kt` (OTel) · `client/DbServiceClient.kt` · `Dockerfile`.
