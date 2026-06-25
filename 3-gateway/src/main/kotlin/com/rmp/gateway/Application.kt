package com.rmp.gateway

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.time.Duration.Companion.seconds

fun main() {
    val port = System.getenv("GATEWAY_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val dbServiceBaseUrl = System.getenv("DB_SERVICE_BASE_URL") ?: "http://localhost:8081"
    val jwtSecret = System.getenv("JWT_SECRET") ?: "rmp_investment_dev_secret_change_me_32+chars"
    val jwtIssuer = System.getenv("JWT_ISSUER") ?: "rmp-trading"
    val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "rmp-clients"
    val rps = System.getenv("RATE_LIMIT_RPS")?.toIntOrNull() ?: 200

    val client = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        // Modest connection ceiling — on a single dev host, opening hundreds of
        // upstream connections just overwhelms db-service's Tomcat pool. For real
        // capacity tuning see docs/ARCHITECTURE.md §9 (the 10k methodology).
        engine { maxConnectionsCount = 256 }
    }

    install(ContentNegotiation) { jackson() }
    install(MicrometerMetrics) { this.registry = registry }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build(),
            )
            validate { cred -> cred.payload.subject?.let { JWTPrincipal(cred.payload) } }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("code" to "unauthorized", "message" to "missing or invalid token"),
                )
            }
        }
    }

    install(RateLimit) {
        register(RateLimitName("api")) {
            rateLimiter(limit = rps, refillPeriod = 1.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    install(CORS) {
        (System.getenv("CORS_ALLOWED_HOSTS") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { origin ->
                val scheme = origin.substringBefore("://", "http")
                val hostPort = origin.substringAfter("://")
                allowHost(hostPort, schemes = listOf(scheme))
            }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowCredentials = true
    }

    routing {
        // Gateway's own ops endpoints — not proxied, not rate limited.
        get("/health") { call.respond(mapOf("status" to "UP")) }
        get("/metrics") { call.respondText(registry.scrape()) }

        rateLimit(RateLimitName("api")) {
            // ---- public (no JWT; db-service permits these) ----
            route("/api/v1/auth") {
                route("{...}") { handle { proxyRequest(call, client, dbServiceBaseUrl) } }
            }
            route("/api/v1/health") { handle { proxyRequest(call, client, dbServiceBaseUrl) } }
            route("/api/v1/quotes") {
                handle { proxyRequest(call, client, dbServiceBaseUrl) }
                route("{...}") { handle { proxyRequest(call, client, dbServiceBaseUrl) } }
            }
            route("/api/v1/instruments") {
                handle { proxyRequest(call, client, dbServiceBaseUrl) }
                route("{...}") { handle { proxyRequest(call, client, dbServiceBaseUrl) } }
            }
            route("/api/v1/stocks") { handle { proxyRequest(call, client, dbServiceBaseUrl) } }

            // ---- protected — JWT validated at the edge; db-service re-validates ----
            authenticate("auth-jwt") {
                route("/api/v1/{...}") { handle { proxyRequest(call, client, dbServiceBaseUrl) } }
            }
        }
    }
}
