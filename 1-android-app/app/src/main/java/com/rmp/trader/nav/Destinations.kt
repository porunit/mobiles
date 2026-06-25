package com.rmp.trader.nav

/**
 * Centralised route definitions for the Navigation-Compose graph.
 */
object Routes {
    const val AUTH = "auth"
    const val QUOTES = "quotes"
    const val PORTFOLIO = "portfolio"

    // Trade takes an optional ticker argument.
    const val TRADE_ARG_TICKER = "ticker"
    const val TRADE = "trade"
    const val TRADE_WITH_ARG = "trade?$TRADE_ARG_TICKER={$TRADE_ARG_TICKER}"

    fun trade(ticker: String = ""): String = "trade?$TRADE_ARG_TICKER=$ticker"
}
