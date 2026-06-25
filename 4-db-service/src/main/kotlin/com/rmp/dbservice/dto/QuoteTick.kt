package com.rmp.dbservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/** Quote tick as published by the receiver (NDJSON, snake_case) onto quotes.topic. */
data class QuoteTick(
    val symbol: String = "",
    val bid: BigDecimal = BigDecimal.ZERO,
    val ask: BigDecimal = BigDecimal.ZERO,
    val spread: BigDecimal = BigDecimal.ZERO,
    val timestamp: Long = 0,
    val volume: Long = 0,
    @JsonProperty("change_percent") val changePercent: BigDecimal = BigDecimal.ZERO,
)
