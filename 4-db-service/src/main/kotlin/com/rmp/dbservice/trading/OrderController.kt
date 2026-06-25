package com.rmp.dbservice.trading

import com.rmp.dbservice.domain.OrderSide
import com.rmp.dbservice.security.userId
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping("/buy")
    fun buy(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: OrderRequest): OrderResponse =
        orderService.execute(jwt.userId(), req.ticker, req.quantity, OrderSide.BUY)

    @PostMapping("/sell")
    fun sell(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: OrderRequest): OrderResponse =
        orderService.execute(jwt.userId(), req.ticker, req.quantity, OrderSide.SELL)

    @GetMapping
    fun history(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): List<OrderResponse> = orderService.history(jwt.userId(), page, size)
}
