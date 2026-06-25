# RUNBOOK — запуск всей системы и проверка

Полный гайд: поднять бэкенд, клиентов (Android + RN) и убедиться, что всё работает.
Для каждого шага указан **ожидаемый результат** — что именно ты должен увидеть.

> Денежные суммы и количества во всём API — **строки** с десятичным значением
> (`"217.34626900"`), а не числа.

---

## 0. Предварительно

| Слой | Нужно установить |
|------|------------------|
| Бэкенд | Docker Desktop (запущен) |
| Android | Android Studio + SDK (API 34), JDK 17, AVD-эмулятор или телефон с USB-отладкой |
| RN/Expo | Node.js 20+, `npx expo`, приложение **Expo Go** (для телефона) |

Все порты сервисов в dev публикуются на `127.0.0.1` (хардненинг). Как клиент видит шлюз:

| Клиент | `BASE_URL` API | Действие |
|--------|----------------|----------|
| Android-эмулятор | `http://10.0.2.2:8080` | работает как есть |
| Android по USB | `http://localhost:8080` | `adb reverse tcp:8080 tcp:8080` |
| iOS-симулятор | `http://localhost:8080` | работает как есть |
| Телефон по Wi-Fi | `http://<LAN-IP>:8080` | в `docker/docker-compose.yaml` поменять `127.0.0.1:8080:8080` → `8080:8080`, добавить origin в `CORS_ALLOWED_HOSTS` |

---

## 1. Бэкенд

```bash
cd docker

# 1.1 инфраструктура
docker compose --env-file .env up -d

# 1.2 котировочный конвейер (итерация 1)
docker compose --env-file .env up -d --build driver-container go-receiver

# 1.3 торговое ядро + шлюз
docker compose --env-file .env up -d --build db-service ktor-gateway

# 1.4 статус
docker compose ps
```

**Ожидаемый результат 1.4** — 10 контейнеров `Up`, ключевые `(healthy)`:
```
rmp-clickhouse ... (healthy)   rmp-postgres ... (healthy)   rmp-rabbitmq ... (healthy)
rmp-redis ... (healthy)        rmp-driver ... (healthy)     rmp-go-receiver ... (healthy)
rmp-db-service ... (healthy)   rmp-gateway ... (healthy)     rmp-grafana / rmp-jaeger / rmp-otel-collector / rmp-prometheus ... Up
```

### Быстрые проверки бэкенда

```bash
curl http://localhost:8080/api/v1/health          # -> {"status":"UP"}
curl http://localhost:8080/api/v1/quotes/SBER     # -> {"symbol":"SBER","bid":"...","ask":"...","last":"...",...}
```
**Ожидается:** `health` отдаёт `UP`; котировка `SBER` — свежие меняющиеся цены (повтори через секунду — `last` другой). Если котировок нет, значит не поднят `driver`/`go-receiver`.

### Сквозная проверка торговли (через шлюз, без UI)

```bash
G=http://localhost:8080/api/v1
TOK=$(curl -s -XPOST $G/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
curl -s -XPOST $G/wallet/deposit -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"amount":1000000}'
curl -s -XPOST $G/orders/buy    -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"ticker":"SBER","quantity":10}'
curl -s $G/portfolio -H "Authorization: Bearer $TOK"
```
**Ожидается:**
- deposit → `{"cashBalance":"1000000.00000000"}`
- buy → `{"id":...,"side":"BUY","fillPrice":"<серверная цена>","notional":"...","status":"FILLED"}` (цена пришла с сервера, не от тебя)
- portfolio → позиция `SBER` qty `10.00000000`, `cashBalance` уменьшился, есть `totalValue`/`totalPnl`
- без токена `curl $G/portfolio` → **401** `{"code":"unauthorized",...}` (шлюз режет на входе)

### Наблюдаемость (браузер)

| URL | Логин | Что увидеть |
|-----|-------|-------------|
| http://localhost:3000 (Grafana) | admin / admin | дашборд **Quotes Receiver** — растущий publish-rate, латентность, 0 ошибок |
| http://localhost:16686 (Jaeger) | — | Service `db-service` → трейс `quotes.consume.q process → SETEX → basic.ack`; есть `go-receiver`, `ktor-gateway` |
| http://localhost:9090/targets (Prometheus) | — | таргеты `otel-collector`, `go-receiver`, `db-service`, `ktor-gateway` = **UP** |
| http://localhost:15672 (RabbitMQ) | admin / rmp_dev_pass | очередь `quotes.consume.q` осушается (consumers ≥1), `quotes.dlx.q` ≤ 50k |

ClickHouse-аналитика:
```bash
docker exec rmp-clickhouse clickhouse-client -u analytics --password analytics_dev_pass \
  -q "SELECT count() FROM analytics.quote_ticks"     # растёт
```

---

## 2. Нагрузочный прогон (опционально)

```bash
cd docker
docker compose --env-file .env --profile apps --profile imitator run --rm \
  -e CLIENTS=150 -e RAMP_UP_SECONDS=5 -e DURATION_SECONDS=30 imitator
```
**Ожидается** строки вида:
```
t= 30s active=150 req=1768 rps=63 err=0(0.00%) p50=500ms p95=... orders=319
DONE total_req=... errors=0 err_rate=0.00% orders=...
```
Ключевое: **err_rate ~0%** и растущие `orders`. В Grafana/Jaeger в это время видно всплеск трафика.

---

## 3. Android-приложение (`1-android-app`)

```bash
# вариант с эмулятором — BASE_URL по умолчанию http://10.0.2.2:8080
# 1. Открыть папку 1-android-app в Android Studio, дождаться Gradle sync
# 2. Запустить AVD (Pixel, API 34) и нажать Run ▶
#    Физическое устройство по USB:
#      adb reverse tcp:8080 tcp:8080
#      и поменять BASE_URL на http://localhost:8080 (см. ниже)
```
`BASE_URL` — в `app/build.gradle.kts` (`buildConfigField`).

**Ожидаемый результат:**
1. Экран входа → введи email/пароль, **Register** → попал в приложение (токен сохранён).
2. **Котировки** — список из 7 инструментов, `last` обновляется каждые ~2с.
3. Тап по инструменту → **Trade**: сделай **Deposit**, затем **Buy** 10 шт → видишь подтверждение ордера (статус FILLED, серверная цена).
4. **Portfolio** — позиция появилась, кэш уменьшился, считается P&L (меняется вместе с котировками).
5. Попытка **Sell** больше, чем есть → сообщение об ошибке с сервера («insufficient position»).
6. Logout → снова экран входа.

---

## 4. Кросс-платформенное приложение (`2-cross-platform-app`)

```bash
cd 2-cross-platform-app
npm install
npx expo start
# Android-эмулятор: нажать 'a'  (API http://10.0.2.2:8080)
# iOS-симулятор:    нажать 'i'  (API http://localhost:8080)
# web:              нажать 'w'  (API http://localhost:8080; CORS уже разрешён)
# телефон (Expo Go): отсканировать QR; для API нужен LAN-IP (см. таблицу в п.0)
```
`API_BASE_URL` — в `app.json` → `expo.extra.apiBaseUrl` (или авто per-platform).

**Ожидаемый результат** — те же 6 пунктов, что и для Android (вход → котировки → депозит/сделка → портфель/P&L → ошибка при перепродаже → выход). Контракт и бэкенд общие, поэтому поведение идентично.

---

## 5. Остановка

```bash
cd docker
docker compose --profile apps --profile imitator down          # остановить (данные в volume сохранятся)
docker compose --profile apps --profile imitator down -v       # + удалить данные (Postgres/ClickHouse/Redis/RabbitMQ)
```

---

## Типичные проблемы

| Симптом | Причина / решение |
|---------|-------------------|
| Котировки пустые / `/quotes/SBER` 404 | не запущены `driver-container` + `go-receiver` (шаг 1.2) |
| Клиент не достучался до API | сеть из п.0: эмулятор → `10.0.2.2`; телефон → `adb reverse` или LAN-IP + открыть порт |
| 401 на всех защищённых | протух/не сохранён токен — перелогинься; проверь, что шлёшь `Authorization: Bearer` |
| Buy → 400 insufficient funds | сначала `Deposit` |
| Jaeger пуст | дай ~10с (батч-экспорт); проверь, что `otel-collector` поднят |
