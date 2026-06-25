package com.rmp.dbservice.quotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import com.rmp.dbservice.dto.QuoteTick
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

/**
 * Consumes quotes.clickhouse.q (the analytics fan-out queue) with manual ack and
 * buffers ticks for the batched ClickHouse sink. Separate from quotes.consume.q,
 * which feeds the Redis last-price cache.
 */
@Component
class ClickHouseQuoteListener(
    private val quoteService: QuoteService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = ["\${RABBITMQ_CLICKHOUSE_QUEUE:quotes.clickhouse.q}"], ackMode = "MANUAL")
    fun onMessage(body: ByteArray, channel: Channel, @Header(AmqpHeaders.DELIVERY_TAG) tag: Long) {
        try {
            quoteService.archive(objectMapper.readValue(body, QuoteTick::class.java))
            channel.basicAck(tag, false)
        } catch (e: Exception) {
            log.warn("clickhouse-queue tick failed; dead-lettering: {}", e.message)
            channel.basicNack(tag, false, false)
        }
    }
}
