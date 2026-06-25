package com.rmp.dbservice.trading

import com.rmp.dbservice.domain.OrderEntity
import com.rmp.dbservice.domain.OrderSide
import com.rmp.dbservice.domain.OrderStatus
import com.rmp.dbservice.domain.OrderType
import com.rmp.dbservice.domain.PortfolioPosition
import com.rmp.dbservice.error.BadRequestException
import com.rmp.dbservice.error.NotFoundException
import com.rmp.dbservice.error.UnauthorizedException
import com.rmp.dbservice.quotes.OrderHistoryWriter
import com.rmp.dbservice.quotes.QuoteQueryService
import com.rmp.dbservice.repo.InstrumentRepository
import com.rmp.dbservice.repo.OrderRepository
import com.rmp.dbservice.repo.PortfolioPositionRepository
import com.rmp.dbservice.repo.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class OrderService(
    private val users: UserRepository,
    private val instruments: InstrumentRepository,
    private val positions: PortfolioPositionRepository,
    private val orders: OrderRepository,
    private val quotes: QuoteQueryService,
    private val orderHistory: OrderHistoryWriter,
) {
    /**
     * Executes a market order at the SERVER-side price (Redis last). Locks the user
     * row (SELECT … FOR UPDATE) so concurrent trades for the same user serialize and
     * the balance check is race-free. The client never supplies the price.
     */
    @Transactional
    fun execute(userId: Long, ticker: String, quantity: BigDecimal, side: OrderSide): OrderResponse {
        if (quantity.signum() <= 0) throw BadRequestException("quantity must be positive")
        val instrument = instruments.findByTicker(ticker.uppercase())
            ?: throw NotFoundException("unknown instrument $ticker")
        val quote = quotes.latest(instrument.ticker)
            ?: throw BadRequestException("no price available for ${instrument.ticker}")
        val price = quote.last
        val notional = price.multiply(quantity).setScale(8, RoundingMode.HALF_UP)

        val user = users.findByIdForUpdate(userId) ?: throw UnauthorizedException("unknown user")
        val position = positions.findByUserIdAndInstrumentId(userId, instrument.id)

        when (side) {
            OrderSide.BUY -> {
                if (user.cashBalance < notional) throw BadRequestException("insufficient funds")
                user.cashBalance = user.cashBalance.subtract(notional)
                if (position == null) {
                    positions.save(PortfolioPosition(userId, instrument.id, quantity, price))
                } else {
                    val newQty = position.quantity.add(quantity)
                    position.avgPrice = position.quantity.multiply(position.avgPrice)
                        .add(quantity.multiply(price))
                        .divide(newQty, 8, RoundingMode.HALF_UP)
                    position.quantity = newQty
                    positions.save(position)
                }
            }
            OrderSide.SELL -> {
                if (position == null || position.quantity < quantity) {
                    throw BadRequestException("insufficient position")
                }
                user.cashBalance = user.cashBalance.add(notional)
                position.quantity = position.quantity.subtract(quantity)
                positions.save(position)
            }
        }
        users.save(user)

        val order = orders.save(
            OrderEntity(
                userId = userId,
                instrumentId = instrument.id,
                side = side,
                orderType = OrderType.MARKET,
                quantity = quantity,
                fillPrice = price,
                status = OrderStatus.FILLED,
            ),
        )
        orderHistory.record(order, instrument.ticker, notional) // best-effort analytics
        return OrderResponse(
            order.id, instrument.ticker, side, OrderType.MARKET,
            quantity, price, notional, OrderStatus.FILLED, order.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun history(userId: Long, page: Int, size: Int): List<OrderResponse> {
        val pg = orders.findByUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200)),
        )
        val tickerById = instruments.findAll().associate { it.id to it.ticker }
        return pg.content.map { o ->
            OrderResponse(
                o.id, tickerById[o.instrumentId] ?: "?", o.side, o.orderType, o.quantity, o.fillPrice,
                o.fillPrice.multiply(o.quantity).setScale(8, RoundingMode.HALF_UP), o.status, o.createdAt,
            )
        }
    }
}
