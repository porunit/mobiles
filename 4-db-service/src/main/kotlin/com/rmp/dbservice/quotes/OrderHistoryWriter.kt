package com.rmp.dbservice.quotes

import com.rmp.dbservice.domain.OrderEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.Timestamp

/** Best-effort append of a filled order to ClickHouse `order_history` (analytics). */
@Component
class OrderHistoryWriter(private val clickhouse: ClickHouseConnection) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun record(order: OrderEntity, symbol: String, notional: BigDecimal) {
        val sql = "INSERT INTO order_history " +
            "(order_id, user_id, symbol, side, order_type, status, quantity, fill_price, notional, created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?)"
        try {
            clickhouse.open().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setLong(1, order.id)
                    ps.setLong(2, order.userId)
                    ps.setString(3, symbol)
                    ps.setString(4, order.side.name)
                    ps.setString(5, order.orderType.name)
                    ps.setString(6, order.status.name)
                    ps.setBigDecimal(7, order.quantity)
                    ps.setBigDecimal(8, order.fillPrice)
                    ps.setBigDecimal(9, notional)
                    ps.setTimestamp(10, Timestamp.from(order.createdAt))
                    ps.executeUpdate()
                }
            }
        } catch (e: Exception) {
            log.warn("order_history insert failed for order {}: {}", order.id, e.message)
        }
    }
}
