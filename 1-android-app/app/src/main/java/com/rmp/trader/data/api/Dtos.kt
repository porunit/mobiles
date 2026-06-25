package com.rmp.trader.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data transfer objects for the RMP gateway contract (base path /api/v1).
 *
 * IMPORTANT: money and quantity fields arrive over the wire as DECIMAL STRINGS
 * (e.g. "217.34626900"). They are deliberately typed as [String] here and must be
 * parsed with [java.math.BigDecimal], never as Float/Double, to avoid precision loss.
 */

// ---------- Auth ----------

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 0,
    val userId: Long = 0,
    val email: String = ""
)

// ---------- Quotes ----------

@JsonClass(generateAdapter = true)
data class QuoteDto(
    val symbol: String,
    val bid: String,
    val ask: String,
    val last: String,
    val volume: Long = 0,
    val ts: Long = 0
)

// ---------- Instruments ----------

@JsonClass(generateAdapter = true)
data class InstrumentDto(
    val id: Long,
    val ticker: String,
    val name: String,
    val currency: String
)

@JsonClass(generateAdapter = true)
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val page: Int = 0,
    val size: Int = 0
)

// ---------- User / wallet ----------

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: Long,
    val email: String,
    val cashBalance: String,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BalanceDto(
    val cashBalance: String
)

@JsonClass(generateAdapter = true)
data class AmountRequest(
    val amount: Long
)

// ---------- Portfolio ----------

@JsonClass(generateAdapter = true)
data class PositionDto(
    val ticker: String,
    val quantity: String,
    val avgPrice: String,
    val currentPrice: String,
    val marketValue: String,
    val unrealizedPnl: String
)

@JsonClass(generateAdapter = true)
data class PortfolioDto(
    val cashBalance: String,
    val positions: List<PositionDto> = emptyList(),
    val totalValue: String,
    val totalPnl: String
)

// ---------- Orders ----------

@JsonClass(generateAdapter = true)
data class OrderRequest(
    val ticker: String,
    val quantity: Long
)

@JsonClass(generateAdapter = true)
data class OrderResponse(
    val id: Long,
    val ticker: String,
    val side: String,
    val type: String,
    val quantity: String,
    val fillPrice: String,
    val notional: String,
    val status: String,
    val createdAt: String? = null
)

// ---------- Misc ----------

@JsonClass(generateAdapter = true)
data class HealthDto(
    val status: String
)

/**
 * Error body returned by the db-service for non-2xx responses, and the
 * gateway's auth error shape. All fields are nullable so a single adapter
 * can decode either variant.
 *
 * db-service: {"timestamp","status","error","code","message","path","traceId"}
 * gateway 401: {"code":"unauthorized","message":"missing or invalid token"}
 */
@JsonClass(generateAdapter = true)
data class ApiErrorBody(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
    val path: String? = null,
    val traceId: String? = null
)
