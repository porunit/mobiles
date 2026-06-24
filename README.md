# Investment Ecosystem — РМП ИТМО (весна 2026)

Экосистема ПО брокера для трейдинга и инвестиций: приём котировок с «биржи», выдача
данных клиентам, покупка/продажа инструментов в терминале. Учебный MVP, рассчитанный
на обслуживание до 10 000 клиентских сессий.

Проект по курсу «Разработка мобильных приложений», Университет ИТМО.

## Состав системы

| Модуль | Назначение | Стек | Итерация |
|--------|-----------|------|----------|
| `1-android-app` | Нативный мобильный терминал | Kotlin, Jetpack Compose, Retrofit | 1 |
| `2-cross-platform-app` | Кросс-платформенный клиент | React Native, Expo | 2 |
| `3-gateway` | API-шлюз (JWT, passthrough) | Kotlin, Ktor | 1 |
| `4-db-service` | Торговое ядро + работа с БД | Kotlin, Spring Boot, JPA | 1 |
| `5-load-imitator` | Имитатор нагрузки 10k клиентов | Kotlin, корутины | 2 |
| `6-quotes-receiver` | Мост котировок `/dev` → RabbitMQ | Go | 1 |
| `7-linux-driver` | Эмулятор биржи (модуль ядра) | C, Linux char-device | 1 |

Инфраструктура: RabbitMQ (брокер) · Redis (кэш) · PostgreSQL (состояние) ·
ClickHouse (аналитика) · OpenTelemetry → Jaeger/Prometheus/Grafana.

## Документация

- [Техническое задание](docs/TZ.md) — ГОСТ 19.201-78
- [Архитектура системы](docs/ARCHITECTURE.md) — целевой образ, потоки, контракты
- [База знаний (Obsidian)](knowledge-base/) — концепции и решения

## Быстрый старт (инфраструктура)

```bash
cp docker/.env.example docker/.env      # при необходимости поправить пароли
docker compose -f docker/docker-compose.yaml up -d   # поднять инфраструктуру
```

Доступы после старта: RabbitMQ UI <http://localhost:15672> (admin/rmp_dev_pass) ·
Grafana <http://localhost:3000> · Jaeger <http://localhost:16686> ·
Prometheus <http://localhost:9090>.

Прикладные сервисы добавляются по мере реализации (профиль `apps`):

```bash
docker compose -f docker/docker-compose.yaml --profile apps up -d --build
```

> **macOS:** модуль ядра (`7-linux-driver`) собирается и грузится только в Linux-ядре
> VM Docker Desktop, не в самой macOS. Подробности — в [ARCHITECTURE.md](docs/ARCHITECTURE.md).
