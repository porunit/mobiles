# 7-linux-driver — эмулятор биржи

**Итерация 1.** Статус: **реализован user-space вариант** (`userspace/quote_gen.c`);
kernel-модуль (`.ko`) — шаг 2.

Эмулятор «биржи», отдающий котировки инструментов. Целевая форма — модуль ядра с
символьным устройством `/dev/financial_quotes` (см. `[[linux-char-device]]`). Т.к.
хост разработки — macOS, итерация 1 реализована как **user-space демон на C**: создаёт
FIFO в общем томе и стримит котировки в формате **NDJSON (одна `QuoteTick` на строку)**.
Тот же wire-контракт будет отдавать и kernel-модуль, поэтому `[6-quotes-receiver](../6-quotes-receiver/README.md)`
не меняется при переходе на `.ko`.

## Контракт (NDJSON, одна котировка на строку)
```json
{"symbol":"SBER","bid":285.500000,"ask":285.620000,"spread":0.120000,"timestamp":1750000000000,"volume":200000,"change_percent":0.34}
```
Цены — random-walk в fixed-point (×10⁶), ~200 тиков/с на инструмент.
ENV: `DEVICE_PATH` · `QUOTE_RATE_HZ` · `INSTRUMENT_SET` · `LOG_LEVEL`.
Сборка/запуск: `docker compose -f docker/docker-compose.yaml --profile apps up -d --build driver-container`.

---
## Целевой kernel-модуль (шаг 2) — проект

## Что делает
- Генерирует цены random-walk в fixed-point (масштаб ×10⁶), ~200 тиков/с на инструмент.
- Инструменты: `SBER, GAZP, YNDX, LKOH, VTBR, AAPL, TSLA` (настраивается).
- Отдаёт JSON-массив `QuoteTick` при чтении char-device.
- Управление в рантайме через sysfs `/sys/kernel/financial_quotes/` (старт/стоп, частота).

## Интерфейс `QuoteTick` (внутренний, snake_case, числа)
```json
{ "symbol":"SBER","bid":285.50,"ask":285.62,"spread":0.12,
  "timestamp":1750000000000,"volume":200000,"change_percent":0.34 }
```

## Файлы (план)
`financial_quotes_main.c` (init/exit, таймер, параметры) · `quotes_data.h` (структуры,
fixed-point) · `quotes_generator.c/.h` (random walk) · `char_device.c/.h` (`.read`,
JSON/CSV) · `sysfs_interface.c/.h` (управление) · `Makefile` (kbuild + docker-таргеты) ·
`Dockerfile` (сборка `.ko` + entrypoint `insmod`/`mknod`).

## Обязательные исправления vs эталон
- `mutex`/`rwlock` на глобальный массив инструментов (гонка таймер ↔ чтение).
- `kobject_create_and_add` вместо несуществующего `sysfs_create_kobject`.
- guard от деления на ноль в `change_percent` (`last_bid == 0`).
- `del_timer_sync` перед выгрузкой.

## Сборка/запуск
Только Linux-ядро. На macOS — в privileged-контейнере `driver-container` (Docker
Desktop LinuxKit-VM). Device-node кладётся на named-volume `dev_financial_quotes`,
который `[6-quotes-receiver](../6-quotes-receiver/README.md)` монтирует `:ro`. См. `docs/ARCHITECTURE.md` §7.
