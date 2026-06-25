package com.rmp.dbservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "users")
class UserAccount(
    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "cash_balance", nullable = false, precision = 20, scale = 8)
    var cashBalance: BigDecimal = BigDecimal.ZERO,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "instruments")
class Instrument(
    @Column(nullable = false, unique = true)
    var ticker: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var currency: String = "RUB",
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
}

enum class OrderSide { BUY, SELL }
enum class OrderType { MARKET, LIMIT }
enum class OrderStatus { NEW, FILLED, REJECTED, CANCELLED }

@Entity
@Table(name = "orders")
class OrderEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "instrument_id", nullable = false)
    var instrumentId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    var side: OrderSide,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 8)
    var orderType: OrderType,

    @Column(nullable = false, precision = 20, scale = 8)
    var quantity: BigDecimal,

    @Column(name = "fill_price", nullable = false, precision = 20, scale = 8)
    var fillPrice: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    var status: OrderStatus,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(
    name = "portfolio_positions",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_position_user_instrument", columnNames = ["user_id", "instrument_id"]),
    ],
)
class PortfolioPosition(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "instrument_id", nullable = false)
    var instrumentId: Long,

    @Column(nullable = false, precision = 20, scale = 8)
    var quantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "avg_price", nullable = false, precision = 20, scale = 8)
    var avgPrice: BigDecimal = BigDecimal.ZERO,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
}
