package com.rmp.dbservice.quotes

import com.clickhouse.jdbc.ClickHouseDataSource
import com.rmp.dbservice.dto.QuoteTick
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Buffers quote ticks and flushes them to ClickHouse `quote_ticks` in batches
 * (best-effort analytics sink, separate from the authoritative Redis/Postgres path).
 * Uses its own ClickHouse JDBC connection — NOT a Spring DataSource bean — to avoid
 * clashing with the primary Postgres datasource.
 */
@Component
class ClickHouseQuoteWriter(
    @Value("\${CLICKHOUSE_URL:jdbc:clickhouse://localhost:8123/analytics}") url: String,
    @Value("\${CLICKHOUSE_USER:analytics}") user: String,
    @Value("\${CLICKHOUSE_PASSWORD:analytics_dev_pass}") password: String,
    meter: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ds = ClickHouseDataSource(
        url,
        Properties().apply {
            setProperty("user", user)
            setProperty("password", password)
            setProperty("compress", "0") // no LZ4 lib on the classpath; skip compression
        },
    )
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
            ds.connection.use { conn ->
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
