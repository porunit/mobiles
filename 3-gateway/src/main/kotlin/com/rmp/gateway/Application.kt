package com.rmp.gateway

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun main() {
    val port = System.getenv("GATEWAY_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(ContentNegotiation) { jackson() }
    install(MicrometerMetrics) { this.registry = registry }

    routing {
        get("/health") { call.respond(mapOf("status" to "UP")) }
        get("/metrics") { call.respondText(registry.scrape()) }
    }
}
