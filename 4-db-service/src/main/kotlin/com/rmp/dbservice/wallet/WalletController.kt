package com.rmp.dbservice.wallet

import com.rmp.dbservice.error.BadRequestException
import com.rmp.dbservice.error.NotFoundException
import com.rmp.dbservice.error.UnauthorizedException
import com.rmp.dbservice.repo.UserRepository
import com.rmp.dbservice.security.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class AmountRequest(@field:DecimalMin(value = "0.00000001") val amount: BigDecimal)
data class WalletResponse(val cashBalance: BigDecimal)

@Service
class WalletService(private val users: UserRepository) {

    @Transactional(readOnly = true)
    fun balance(userId: Long): WalletResponse {
        val user = users.findById(userId).orElseThrow { NotFoundException("user not found") }
        return WalletResponse(user.cashBalance)
    }

    @Transactional
    fun deposit(userId: Long, amount: BigDecimal): WalletResponse {
        val user = users.findByIdForUpdate(userId) ?: throw UnauthorizedException("unknown user")
        user.cashBalance = user.cashBalance.add(amount)
        users.save(user)
        return WalletResponse(user.cashBalance)
    }

    @Transactional
    fun withdraw(userId: Long, amount: BigDecimal): WalletResponse {
        val user = users.findByIdForUpdate(userId) ?: throw UnauthorizedException("unknown user")
        if (user.cashBalance < amount) throw BadRequestException("insufficient funds")
        user.cashBalance = user.cashBalance.subtract(amount)
        users.save(user)
        return WalletResponse(user.cashBalance)
    }
}

@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(private val wallet: WalletService) {

    @GetMapping("/balance")
    fun balance(@AuthenticationPrincipal jwt: Jwt): WalletResponse = wallet.balance(jwt.userId())

    @PostMapping("/deposit")
    fun deposit(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: AmountRequest): WalletResponse =
        wallet.deposit(jwt.userId(), req.amount)

    @PostMapping("/withdraw")
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: AmountRequest): WalletResponse =
        wallet.withdraw(jwt.userId(), req.amount)
}
