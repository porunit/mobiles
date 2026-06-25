package com.rmp.dbservice.quotes

import com.rmp.dbservice.dto.QuoteTick
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/** Buffers quote ticks and flushes them to ClickHouse `quote_ticks` in batches. */
@Component
class ClickHouseQuoteWriter(
    private val clickhouse: ClickHouseConnection,
    meter: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = ConcurrentLinkedQueue<QuoteTick>()
    private val written = meter.counter("dbservice_clickhouse_rows_written_total")
    private val dropped = AtomicLong()

    private val maxBuffer = 200_000
    private val batchLimit = 5_000

    fun enqueue(tick: QuoteTick) {
        if (buffer.size >= maxBuffer) {
            dropped.incrementAndGet()
            return
        }
        buffer.add(tick)
    }

    @Scheduled(fixedDelay = 1000)
    fun flush() {
        val batch = ArrayList<QuoteTick>(batchLimit)
        while (batch.size < batchLimit) {
            batch.add(buffer.poll() ?: break)
        }
        if (batch.isEmpty()) return

        val sql = "INSERT INTO quote_ticks (symbol, ts, bid, ask, last, volume, source) VALUES (?,?,?,?,?,?,?)"
        try {
            clickhouse.open().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    for (t in batch) {
                        val last = t.bid.add(t.ask).divide(BigDecimal(2), 8, RoundingMode.HALF_UP)
                        ps.setString(1, t.symbol)
                        ps.setTimestamp(2, Timestamp(t.timestamp))
                        ps.setBigDecimal(3, t.bid)
                        ps.setBigDecimal(4, t.ask)
                        ps.setBigDecimal(5, last)
                        ps.setLong(6, t.volume)
                        ps.setString(7, "driver")
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            written.increment(batch.size.toDouble())
        } catch (e: Exception) {
            log.warn("clickhouse batch insert failed ({} rows): {}", batch.size, e.message)
        }
    }
}
