package com.rmp.dbservice.trading

import com.rmp.dbservice.domain.OrderSide
import com.rmp.dbservice.domain.OrderStatus
import com.rmp.dbservice.domain.OrderType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

data class OrderRequest(
    @field:NotBlank val ticker: String,
    @field:DecimalMin(value = "0.00000001") val quantity: BigDecimal,
)

data class OrderResponse(
    val id: Long,
    val ticker: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: BigDecimal,
    val fillPrice: BigDecimal,
    val notional: BigDecimal,
    val status: OrderStatus,
    val createdAt: Instant,
)

data class PositionView(
    val ticker: String,
    val quantity: BigDecimal,
    val avgPrice: BigDecimal,
    val currentPrice: BigDecimal,
    val marketValue: BigDecimal,
    val unrealizedPnl: BigDecimal,
)

data class PortfolioResponse(
    val cashBalance: BigDecimal,
    val positions: List<PositionView>,
    val totalValue: BigDecimal,
    val totalPnl: BigDecimal,
)
