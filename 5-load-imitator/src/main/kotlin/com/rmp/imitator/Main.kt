package com.rmp.imitator

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong

private fun env(key: String, default: String): String =
    System.getenv(key)?.takeIf { it.isNotBlank() } ?: default

private fun ts(): String {
    val s = System.currentTimeMillis() / 1000
    return "%02d:%02d:%02d".format((s / 3600) % 24, (s / 60) % 60, s % 60)
}

private fun log(msg: String) = println("${ts()} [imitator] $msg")

fun main() = runBlocking {
    val baseUrl = env("API_BASE_URL", "http://localhost:8080")
    val clients = env("CLIENTS", "100").toInt()
    val rampUp = env("RAMP_UP_SECONDS", "10").toLong()
    val duration = env("DURATION_SECONDS", "60").toLong()
    val thinkMin = env("THINK_MIN_MS", "500").toLong()
    val thinkMax = env("THINK_MAX_MS", "3000").toLong()
    val concurrency = env("HTTP_CONCURRENCY", "4096").toInt()
    val metricsInterval = env("METRICS_INTERVAL_SECONDS", "5").toLong()
    val tickers = listOf("SBER", "GAZP", "YNDX", "LKOH", "VTBR", "AAPL", "TSLA")

    log("start: clients=$clients rampUp=${rampUp}s duration=${duration}s think=${thinkMin}-${thinkMax}ms target=$baseUrl")

    val client = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { jackson() }
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        engine { maxConnectionsCount = concurrency }
    }

    val stats = Stats()
    val active = AtomicLong()
    val runStamp = System.currentTimeMillis()
    val startMs = runStamp
    val deadlineMs = startMs + duration * 1000

    val reporter = launch {
        var lastReq = 0L
        var lastT = startMs
        while (isActive) {
            delay(metricsInterval * 1000)
            val now = System.currentTimeMillis()
            val req = stats.req.get()
            val err = stats.err.get()
            val rps = (req - lastReq) * 1000.0 / (now - lastT).coerceAtLeast(1)
            log(
                "t=%3ds active=%5d req=%-9d rps=%-7.0f err=%d(%.2f%%) p50=%dms p95=%dms p99=%dms orders=%d".format(
                    (now - startMs) / 1000, active.get(), req, rps, err,
                    if (req > 0) err * 100.0 / req else 0.0,
                    stats.latency.percentile(0.50), stats.latency.percentile(0.95),
                    stats.latency.percentile(0.99), stats.orders.get(),
                ),
            )
            lastReq = req
            lastT = now
        }
    }

    val jobs = (0 until clients).map { i ->
        launch {
            if (clients > 1) delay(rampUp * 1000 * i / clients)
            active.incrementAndGet()
            try {
                runClient(i, runStamp, client, baseUrl, deadlineMs, thinkMin, thinkMax, tickers, stats)
            } finally {
                active.decrementAndGet()
            }
        }
    }
    jobs.forEach { it.join() }
    reporter.cancel()
    client.close()

    val req = stats.req.get()
    val err = stats.err.get()
    log(
        "DONE total_req=%d errors=%d err_rate=%.2f%% orders=%d p50=%dms p95=%dms p99=%dms".format(
            req, err, if (req > 0) err * 100.0 / req else 0.0, stats.orders.get(),
            stats.latency.percentile(0.50), stats.latency.percentile(0.95), stats.latency.percentile(0.99),
        ),
    )
}
