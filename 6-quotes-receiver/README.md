# 6-quotes-receiver — мост котировок (Go)

**Итерация 1.** Статус: **реализован и проверен end-to-end**.

Читает котировки из устройства драйвера и **публикует** их в RabbitMQ.
Единственный источник котировок для платформы (см. `[[adr-0002-real-quote-pipeline]]`).

## Что делает
- Читает `/dev-shared/financial_quotes` (env `DEVICE_PATH`) как **NDJSON-стрим**
  (одна `QuoteTick` на строку); переоткрывает FIFO при реконнекте писателя.
- Публикует в exchange `quotes.topic`, routing key `quote.tick.<SYMBOL>`, с
  **publisher confirms** (не auto-ack, не теряем сообщения).
- HTTP: `GET /healthz`, `GET /metrics` (Prometheus) на `:8090`.
- OTel: спаны read → parse → publish; трейс начинается здесь.

## Только producer
В отличие от эталона, receiver **не** потребляет очередь и **не** отдаёт котировки
клиентам напрямую — это делает DB-сервис. См. `[[rabbitmq-topic-exchange]]`.

## ENV
`DEVICE_PATH` · `RABBITMQ_URL` · `QUOTES_EXCHANGE=quotes.topic` ·
`QUOTES_ROUTING_PREFIX=quote.tick` · `PUBLISHER_CONFIRMS=true` ·
`OTEL_EXPORTER_OTLP_ENDPOINT` · `OTEL_SERVICE_NAME`.

## Файлы (план)
`main.go` · `internal/driver` (reader) · `internal/publisher` (amqp091) ·
`internal/quotes` (модель/парсер) · `Dockerfile`.
