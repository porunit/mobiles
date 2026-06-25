package com.rmp.dbservice.config

import com.rmp.dbservice.domain.Instrument
import com.rmp.dbservice.repo.InstrumentRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** Seeds the instrument catalog (the tickers the driver emits) on startup. */
@Component
class DataSeeder(private val instruments: InstrumentRepository) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    private val catalog = listOf(
        Triple("SBER", "Сбербанк", "RUB"),
        Triple("GAZP", "Газпром", "RUB"),
        Triple("YNDX", "Яндекс", "RUB"),
        Triple("LKOH", "Лукойл", "RUB"),
        Triple("VTBR", "ВТБ", "RUB"),
        Triple("AAPL", "Apple Inc.", "USD"),
        Triple("TSLA", "Tesla Inc.", "USD"),
    )

    override fun run(args: ApplicationArguments) {
        var added = 0
        for ((ticker, name, ccy) in catalog) {
            if (!instruments.existsByTicker(ticker)) {
                instruments.save(Instrument(ticker = ticker, name = name, currency = ccy))
                added++
            }
        }
        log.info("instrument catalog seeded: {} added, {} total", added, instruments.count())
    }
}
