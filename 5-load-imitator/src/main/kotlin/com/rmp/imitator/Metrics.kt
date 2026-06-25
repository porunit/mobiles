package com.rmp.imitator

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/** Lock-free bucketed latency histogram for percentile estimates (ms upper bounds). */
class LatencyStats {
    // Finite ms upper bounds; one extra overflow bucket for ">5000ms".
    private val bounds = longArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000)
    private val buckets = AtomicLongArray(bounds.size + 1)

    fun record(ms: Long) {
        var i = 0
        while (i < bounds.size && ms > bounds[i]) i++ // i == bounds.size => overflow
        buckets.incrementAndGet(i)
    }

    /** ms upper bound for the requested percentile, or -1 meaning ">5000ms" (overflow). */
    fun percentile(p: Double): Long {
        var total = 0L
        for (i in 0 until buckets.length()) total += buckets.get(i)
        if (total == 0L) return 0
        val target = (total * p).toLong().coerceAtLeast(1)
        var cum = 0L
        for (i in 0 until buckets.length()) {
            cum += buckets.get(i)
            if (cum >= target) return if (i < bounds.size) bounds[i] else -1L
        }
        return -1L
    }
}

/** Shared counters across all virtual clients. */
class Stats {
    val req = AtomicLong()
    val err = AtomicLong()      // transport errors + 5xx (business 4xx are valid responses)
    val orders = AtomicLong()   // filled buy/sell orders
    val latency = LatencyStats()
}
