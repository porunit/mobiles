package com.rmp.dbservice.instrument

import com.rmp.dbservice.domain.Instrument
import com.rmp.dbservice.repo.InstrumentRepository
import com.rmp.dbservice.web.PagedResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class InstrumentController(private val instruments: InstrumentRepository) {

    @GetMapping("/api/v1/instruments")
    fun all(): List<Instrument> = instruments.findAll(Sort.by("ticker"))

    @GetMapping("/api/v1/stocks")
    fun stocks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PagedResponse<Instrument> {
        val p = instruments.findAll(PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200), Sort.by("ticker")))
        return PagedResponse(p.content, p.totalElements, p.totalPages, p.number, p.size)
    }
}
