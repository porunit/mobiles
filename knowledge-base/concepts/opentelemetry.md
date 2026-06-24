# OpenTelemetry (OTel)

Единый стандарт наблюдаемости: **трейсы** (путь запроса через сервисы), **метрики**
(числовые показатели), **логи**. Сервисы экспортируют телеметрию в **OTel Collector**,
который раскладывает её по бэкендам.

## В проекте
- Все сервисы → Collector `:4317` (OTLP gRPC). Конфиг — `docker/otel/config.yaml`.
- Трейсы → **Jaeger** (UI `:16686`) + архив в ClickHouse.
- Метрики → **Prometheus** (scrape экспортёра коллектора `:8889`).
- Логи → **ClickHouse** (`otel_logs`).
- Единая панель — **Grafana** (`:3000`) с тремя источниками.

## Контекст и распространение
- Заголовок W3C **`traceparent`** связывает спаны через HTTP и AMQP. Spring Java-agent
  и otel-go SDK подхватывают его автоматически.
- Трейс начинается на `go-receiver` — модуль ядра (C) не умеет OTLP, его телеметрия —
  счётчики и `dmesg`.

## Как инструментируем
- Spring Boot (`db-service`) — Java-agent (авто-спаны JDBC/JPA/AMQP/HTTP) + Micrometer.
- Ktor (`gateway`) — OTel Kotlin SDK + Ktor instrumentation.
- Go (`go-receiver`) — otel-go SDK (спаны read → parse → publish).

Связано с `[[clickhouse-aggregatingmergetree]]` (хранилище логов/трейсов) и
`[[rabbitmq-topic-exchange]]` (распространение контекста через очереди).
