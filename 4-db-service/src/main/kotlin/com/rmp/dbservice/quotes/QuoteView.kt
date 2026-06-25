package com.rmp.dbservice.quotes

import java.math.BigDecimal

/** Latest-quote projection cached in Redis (`quote:last:{symbol}`). */
data class QuoteView(
    val symbol: String = "",
    val bid: BigDecimal = BigDecimal.ZERO,
    val ask: BigDecimal = BigDecimal.ZERO,
    val last: BigDecimal = BigDecimal.ZERO,
    val volume: Long = 0,
    val ts: Long = 0,
)
