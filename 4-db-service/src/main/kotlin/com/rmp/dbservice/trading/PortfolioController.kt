package com.rmp.dbservice.trading

import com.rmp.dbservice.security.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/portfolio")
class PortfolioController(private val portfolio: PortfolioService) {

    @GetMapping
    fun get(@AuthenticationPrincipal jwt: Jwt): PortfolioResponse = portfolio.portfolio(jwt.userId())

    @GetMapping("/positions")
    fun positions(@AuthenticationPrincipal jwt: Jwt): List<PositionView> = portfolio.positionsOnly(jwt.userId())
}
