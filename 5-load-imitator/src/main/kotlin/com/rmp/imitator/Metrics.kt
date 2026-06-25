package com.rmp.imitator

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/** Lock-free bucketed latency histogram for percentile estimates (ms upper bounds). */
class LatencyStats {
    private val bounds = longArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, Long.MAX_VALUE)
    private val buckets = AtomicLongArray(bounds.size)

    fun record(ms: Long) {
        var i = 0
        while (i < bounds.size - 1 && ms > bounds[i]) i++
        buckets.incrementAndGet(i)
    }

    fun percentile(p: Double): Long {
        var total = 0L
        for (i in 0 until buckets.length()) total += buckets.get(i)
        if (total == 0L) return 0
        val target = (total * p).toLong().coerceAtLeast(1)
        var cum = 0L
        for (i in 0 until buckets.length()) {
            cum += buckets.get(i)
            if (cum >= target) return bounds[i]
        }
        return bounds.last()
    }
}

/** Shared counters across all virtual clients. */
class Stats {
    val req = AtomicLong()
    val err = AtomicLong()      // transport errors + 5xx (business 4xx are valid responses)
    val orders = AtomicLong()   // filled buy/sell orders
    val latency = LatencyStats()
}
