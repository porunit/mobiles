package com.rmp.imitator

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlin.random.Random

/** One virtual client: authenticate, fund, then loop reads/trades with think-time. */
suspend fun runClient(
    id: Int,
    runStamp: Long,
    client: HttpClient,
    baseUrl: String,
    deadlineMs: Long,
    thinkMin: Long,
    thinkMax: Long,
    tickers: List<String>,
    stats: Stats,
) {
    val email = "load_${runStamp}_$id@example.com"
    val pass = "loadtest123"

    val token = register(client, baseUrl, email, pass, stats)
        ?: login(client, baseUrl, email, pass, stats)
        ?: return

    deposit(client, baseUrl, token, stats)

    while (System.currentTimeMillis() < deadlineMs) {
        when (Random.nextInt(10)) {
            in 0..3 -> timed(stats) { client.get("$baseUrl/api/v1/quotes/${tickers.random()}") }
            in 4..5 -> timed(stats) { client.get("$baseUrl/api/v1/portfolio") { bearer(token) } }
            in 6..7 -> order(client, baseUrl, token, "buy", tickers.random(), stats)
            else -> order(client, baseUrl, token, "sell", tickers.random(), stats)
        }
        delay(Random.nextLong(thinkMin, thinkMax + 1))
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) =
    header(HttpHeaders.Authorization, "Bearer $token")

/** Times a request, records latency, and flags transport errors / 5xx (not business 4xx). */
private suspend inline fun timed(stats: Stats, block: () -> HttpResponse): HttpResponse? {
    val t0 = System.currentTimeMillis()
    return try {
        val resp = block()
        stats.req.incrementAndGet()
        stats.latency.record(System.currentTimeMillis() - t0)
        if (resp.status.value >= 500) stats.err.incrementAndGet()
        resp
    } catch (e: Exception) {
        stats.req.incrementAndGet()
        stats.err.incrementAndGet()
        stats.latency.record(System.currentTimeMillis() - t0)
        null
    }
}

private suspend fun register(client: HttpClient, baseUrl: String, email: String, pass: String, stats: Stats): String? {
    val resp = timed(stats) {
        client.post("$baseUrl/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to pass))
        }
    } ?: return null
    return if (resp.status.value == 200) tokenOf(resp) else null
}

private suspend fun login(client: HttpClient, baseUrl: String, email: String, pass: String, stats: Stats): String? {
    val resp = timed(stats) {
        client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to email, "password" to pass))
        }
    } ?: return null
    return if (resp.status.value == 200) tokenOf(resp) else null
}

private suspend fun tokenOf(resp: HttpResponse): String? =
    runCatching { resp.body<Map<String, Any?>>()["accessToken"] as? String }.getOrNull()

private suspend fun deposit(client: HttpClient, baseUrl: String, token: String, stats: Stats) {
    timed(stats) {
        client.post("$baseUrl/api/v1/wallet/deposit") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(mapOf("amount" to 1_000_000))
        }
    }
}

private suspend fun order(client: HttpClient, baseUrl: String, token: String, side: String, ticker: String, stats: Stats) {
    val resp = timed(stats) {
        client.post("$baseUrl/api/v1/orders/$side") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(mapOf("ticker" to ticker, "quantity" to 1))
        }
    }
    if (resp?.status?.value == 200) stats.orders.incrementAndGet()
}
