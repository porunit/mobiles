package com.rmp.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun main() {
    val port = System.getenv("GATEWAY_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val dbServiceBaseUrl = System.getenv("DB_SERVICE_BASE_URL") ?: "http://localhost:8081"

    val client = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    install(ContentNegotiation) { jackson() }
    install(MicrometerMetrics) { this.registry = registry }
    install(CORS) {
        (System.getenv("CORS_ALLOWED_HOSTS") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { origin ->
                val scheme = origin.substringBefore("://", "http")
                val hostPort = origin.substringAfter("://")
                allowHost(hostPort, schemes = listOf(scheme))
            }
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowCredentials = true
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "UP")) }
        get("/metrics") { call.respondText(registry.scrape()) }

        // Transparent reverse proxy to db-service for the whole API surface.
        route("/api/v1/{...}") {
            handle { proxyRequest(call, client, dbServiceBaseUrl) }
        }
    }
}
