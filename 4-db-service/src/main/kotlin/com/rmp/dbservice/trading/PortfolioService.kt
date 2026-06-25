package com.rmp.dbservice.trading

import com.rmp.dbservice.error.NotFoundException
import com.rmp.dbservice.quotes.QuoteQueryService
import com.rmp.dbservice.repo.InstrumentRepository
import com.rmp.dbservice.repo.PortfolioPositionRepository
import com.rmp.dbservice.repo.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PortfolioService(
    private val users: UserRepository,
    private val positions: PortfolioPositionRepository,
    private val instruments: InstrumentRepository,
    private val quotes: QuoteQueryService,
) {
    @Transactional(readOnly = true)
    fun portfolio(userId: Long): PortfolioResponse {
        val user = users.findById(userId).orElseThrow { NotFoundException("user not found") }
        val tickerById = instruments.findAll().associate { it.id to it.ticker }

        val views = positions.findByUserId(userId)
            .filter { it.quantity.signum() > 0 }
            .map { p ->
                val ticker = tickerById[p.instrumentId] ?: "?"
                val current = quotes.latest(ticker)?.last ?: p.avgPrice
                val marketValue = current.multiply(p.quantity).setScale(8, RoundingMode.HALF_UP)
                val pnl = current.subtract(p.avgPrice).multiply(p.quantity).setScale(8, RoundingMode.HALF_UP)
                PositionView(ticker, p.quantity, p.avgPrice, current, marketValue, pnl)
            }

        val invested = views.fold(BigDecimal.ZERO) { acc, v -> acc.add(v.marketValue) }
        val totalPnl = views.fold(BigDecimal.ZERO) { acc, v -> acc.add(v.unrealizedPnl) }
        return PortfolioResponse(
            cashBalance = user.cashBalance,
            positions = views,
            totalValue = user.cashBalance.add(invested).setScale(8, RoundingMode.HALF_UP),
            totalPnl = totalPnl.setScale(8, RoundingMode.HALF_UP),
        )
    }

    fun positionsOnly(userId: Long): List<PositionView> = portfolio(userId).positions
}
