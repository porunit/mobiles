package com.rmp.dbservice.repo

import com.rmp.dbservice.domain.Instrument
import com.rmp.dbservice.domain.OrderEntity
import com.rmp.dbservice.domain.PortfolioPosition
import com.rmp.dbservice.domain.UserAccount
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<UserAccount, Long> {
    fun findByEmail(email: String): UserAccount?
    fun existsByEmail(email: String): Boolean

    /** Locks the user row (SELECT … FOR UPDATE) so concurrent trades serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): UserAccount?
}

interface InstrumentRepository : JpaRepository<Instrument, Long> {
    fun findByTicker(ticker: String): Instrument?
    fun existsByTicker(ticker: String): Boolean
}

interface OrderRepository : JpaRepository<OrderEntity, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<OrderEntity>
}

interface PortfolioPositionRepository : JpaRepository<PortfolioPosition, Long> {
    fun findByUserId(userId: Long): List<PortfolioPosition>
    fun findByUserIdAndInstrumentId(userId: Long, instrumentId: Long): PortfolioPosition?
}
