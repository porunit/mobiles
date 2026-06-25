package com.rmp.dbservice.quotes

import com.rmp.dbservice.error.NotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/quotes")
class QuoteController(private val quotes: QuoteQueryService) {

    @GetMapping
    fun all(): List<QuoteView> = quotes.all()

    @GetMapping("/{ticker}")
    fun one(@PathVariable ticker: String): QuoteView =
        quotes.latest(ticker) ?: throw NotFoundException("no quote for $ticker")
}
