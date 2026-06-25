package com.rmp.dbservice.quotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.rmp.dbservice.dto.QuoteTick
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Processes one quote tick: caches the latest price in Redis (`quote:last:{symbol}`,
 * TTL 5s) and enqueues it for the batched ClickHouse sink.
 */
@Service
class QuoteService(
    private val redis: StringRedisTemplate,
    private val clickhouse: ClickHouseQuoteWriter,
    private val objectMapper: ObjectMapper,
    meter: MeterRegistry,
) {
    private val consumed = meter.counter("dbservice_quotes_consumed_total")
    private val archived = meter.counter("dbservice_quotes_archived_total")
    private val ttl = Duration.ofSeconds(5)

    /** From quotes.consume.q: cache the latest price in Redis for fast reads. */
    fun cacheLatest(tick: QuoteTick) {
        consumed.increment()
        val last = tick.bid.add(tick.ask).divide(BigDecimal(2), 8, RoundingMode.HALF_UP)
        val snapshot = linkedMapOf(
            "symbol" to tick.symbol,
            "bid" to tick.bid,
            "ask" to tick.ask,
            "last" to last,
            "volume" to tick.volume,
            "ts" to tick.timestamp,
        )
        redis.opsForValue().set("quote:last:${tick.symbol}", objectMapper.writeValueAsString(snapshot), ttl)
    }

    /** From quotes.clickhouse.q: buffer the tick for the batched ClickHouse sink. */
    fun archive(tick: QuoteTick) {
        archived.increment()
        clickhouse.enqueue(tick)
    }
}
