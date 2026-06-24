# ClickHouse: MergeTree и AggregatingMergeTree

ClickHouse — колоночная СУБД для аналитики больших объёмов. В проекте — сток тиков
котировок и истории сделок (`docker/clickhouse/init/01_schema.sql`).

## MergeTree
Базовый движок. Данные хранятся колонками, сортируются по `ORDER BY`, бьются на
партиции (`PARTITION BY toYYYYMMDD(ts)`). `TTL ... DELETE` автоматически удаляет старое.
Таблица `quote_ticks` — по строке на тик.

## AggregatingMergeTree + materialized view
Хранит **состояния агрегатных функций** (`AggregateFunction(argMin, …)`), которые
досливаются при merge. Используется для OHLC-свечей по минутам (`quote_ohlc_1m`):
- материализованное представление `mv_quote_ohlc_1m` при вставке в `quote_ticks`
  считает `argMinState(last, ts)` (open), `maxState`, `minState`, `argMaxState` (close),
  `sumState(volume)` и пишет в целевую таблицу.
- при чтении применяем `-Merge` функции (`argMinMerge`, …) → готовые свечи.

## Зачем
Экран аналитики читает агрегированные свечи (быстро), а не сырые тики. Сырьё —
high-volume путь, ради которого ClickHouse и взят (требование задания). Запись из
DB-сервиса — батчами через `clickhouse-jdbc`. См. `[[opentelemetry]]` (логи/трейсы тоже
уходят в ClickHouse).
