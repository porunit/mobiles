-- Analytics store for the trading ecosystem.
-- Fed by the db-service (batched JDBC inserts) and the OTel collector (logs/traces).
-- The OTel collector auto-creates its own otel_logs / otel_traces tables (create_schema=true);
-- here we declare only the business-analytics tables.

CREATE DATABASE IF NOT EXISTS analytics;

-- High-volume tick sink (one row per quote tick).
CREATE TABLE IF NOT EXISTS analytics.quote_ticks
(
    symbol  LowCardinality(String),
    ts      DateTime64(3, 'UTC'),
    bid     Decimal(20, 8),
    ask     Decimal(20, 8),
    last    Decimal(20, 8),
    volume  UInt64,
    source  LowCardinality(String) DEFAULT 'driver'
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(ts)
ORDER BY (symbol, ts)
TTL toDateTime(ts) + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- 1-minute OHLC rollup (auto-maintained by the materialized view below).
CREATE TABLE IF NOT EXISTS analytics.quote_ohlc_1m
(
    symbol  LowCardinality(String),
    minute  DateTime('UTC'),
    open    AggregateFunction(argMin, Decimal(20, 8), DateTime64(3)),
    high    AggregateFunction(max,    Decimal(20, 8)),
    low     AggregateFunction(min,    Decimal(20, 8)),
    close   AggregateFunction(argMax, Decimal(20, 8), DateTime64(3)),
    volume  AggregateFunction(sum,    UInt64)
)
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMM(minute)
ORDER BY (symbol, minute);

CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.mv_quote_ohlc_1m
TO analytics.quote_ohlc_1m AS
SELECT
    symbol,
    toStartOfMinute(ts)        AS minute,
    argMinState(last, ts)      AS open,
    maxState(last)             AS high,
    minState(last)             AS low,
    argMaxState(last, ts)      AS close,
    sumState(volume)           AS volume
FROM analytics.quote_ticks
GROUP BY symbol, minute;

-- Order history sink (one row per order lifecycle event).
CREATE TABLE IF NOT EXISTS analytics.order_history
(
    order_id    UInt64,
    user_id     UInt64,
    symbol      LowCardinality(String),
    side        Enum8('BUY' = 1, 'SELL' = 2),
    order_type  Enum8('MARKET' = 1, 'LIMIT' = 2),
    status      Enum8('NEW' = 1, 'FILLED' = 2, 'REJECTED' = 3, 'CANCELLED' = 4),
    quantity    Decimal(20, 8),
    fill_price  Decimal(20, 8),
    notional    Decimal(20, 8),
    created_at  DateTime64(3, 'UTC'),
    event_ts    DateTime64(3, 'UTC') DEFAULT now64(3)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(created_at)
ORDER BY (user_id, created_at)
TTL toDateTime(created_at) + INTERVAL 365 DAY DELETE;
