package com.rmp.dbservice.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

/**
 * Money and quantities go over the wire as decimal JSON strings (API contract),
 * never as floating-point or scientific notation (e.g. "0.00000000", not 0E-8).
 */
@Configuration
class JacksonConfig {

    private class BigDecimalPlainStringSerializer : JsonSerializer<BigDecimal>() {
        override fun serialize(value: BigDecimal, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString(value.toPlainString())
        }
    }

    @Bean
    fun bigDecimalAsString(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.serializerByType(BigDecimal::class.java, BigDecimalPlainStringSerializer())
        }
}
