package com.rmp.dbservice.quotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.rmp.dbservice.repo.InstrumentRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/** Serves the latest quote from Redis (the cache fed by the consumer). */
@Service
class QuoteQueryService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val instruments: InstrumentRepository,
) {
    fun latest(ticker: String): QuoteView? {
        val raw = redis.opsForValue().get("quote:last:${ticker.uppercase()}") ?: return null
        return objectMapper.readValue(raw, QuoteView::class.java)
    }

    fun all(): List<QuoteView> = instruments.findAll().mapNotNull { latest(it.ticker) }
}
