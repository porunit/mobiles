package com.rmp.dbservice.quotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import com.rmp.dbservice.dto.QuoteTick
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.support.AmqpHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

/** Consumes quotes.consume.q with manual ack — ack after success, dead-letter on failure. */
@Component
class QuoteTickListener(
    private val quoteService: QuoteService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = ["\${RABBITMQ_QUOTES_QUEUE:quotes.consume.q}"], ackMode = "MANUAL")
    fun onMessage(body: ByteArray, channel: Channel, @Header(AmqpHeaders.DELIVERY_TAG) tag: Long) {
        try {
            val tick = objectMapper.readValue(body, QuoteTick::class.java)
            quoteService.onTick(tick)
            channel.basicAck(tag, false)
        } catch (e: Exception) {
            log.warn("quote tick processing failed; dead-lettering: {}", e.message)
            channel.basicNack(tag, false, false) // requeue=false -> DLX dlx.quotes
        }
    }
}
