# OpenTelemetry (OTel)

Единый стандарт наблюдаемости: **трейсы** (путь запроса через сервисы), **метрики**
(числовые показатели), **логи**. Сервисы экспортируют телеметрию в **OTel Collector**,
который раскладывает её по бэкендам.

## В проекте
- Все сервисы → Collector `:4317` (OTLP gRPC). Конфиг — `docker/otel/config.yaml`.
- Трейсы → **Jaeger** (UI `:16686`) + архив в ClickHouse.
- Метрики → **Prometheus** двумя путями: (1) SDK/JVM-сервисы (db-service, gateway)
  шлют в Collector, Prometheus скрейпит его экспортёр `:8889`; (2) `go-receiver`
  отдаёт Prometheus-метрики напрямую на `:8090/metrics` (scrape-job `go-receiver`).
- Логи → **ClickHouse** (`otel_logs`).
- Единая панель — **Grafana** (`:3000`) с тремя источниками.

## Контекст и распространение
- Заголовок W3C **`traceparent`** связывает спаны через HTTP и AMQP. Spring Java-agent
  распространяет его полностью автоматически (HTTP/AMQP); в `go-receiver` контекст
  **вручную** инжектится в AMQP-заголовки (otel-go SDK сам AMQP не инструментирует).
- Трейс начинается на `go-receiver` — драйвер (C) не эмитит OTLP, его телеметрия —
  логи и счётчики.

## Как инструментируем
- Spring Boot (`db-service`) — Java-agent (авто-спаны JDBC/JPA/AMQP/HTTP) + Micrometer.
- Ktor (`gateway`) — OTel Kotlin SDK + Ktor instrumentation.
- Go (`go-receiver`) — otel-go SDK (спаны read → parse → publish).

Связано с `[[clickhouse-aggregatingmergetree]]` (хранилище логов/трейсов) и
`[[rabbitmq-topic-exchange]]` (распространение контекста через очереди).
