package com.rmp.trader.ui

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Helpers for displaying money/quantity values that arrive as decimal strings.
 * All parsing goes through [BigDecimal] to avoid binary floating-point error.
 */
object Money {

    fun parse(value: String?): BigDecimal =
        value?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

    /** Formats a decimal string to 2 fraction digits, e.g. "217.35". */
    fun format(value: String?, scale: Int = 2): String =
        parse(value).setScale(scale, RoundingMode.HALF_UP).toPlainString()

    /** Formats with a leading sign and 2 decimals, e.g. "+12.30" / "-4.10". */
    fun formatSigned(value: String?, scale: Int = 2): String {
        val bd = parse(value).setScale(scale, RoundingMode.HALF_UP)
        val sign = if (bd.signum() > 0) "+" else ""
        return sign + bd.toPlainString()
    }

    /** Quantities may be integral ("1") or fractional; trim trailing zeros. */
    fun formatQuantity(value: String?): String =
        parse(value).stripTrailingZeros().toPlainString()

    fun isNegative(value: String?): Boolean = parse(value).signum() < 0

    fun isPositive(value: String?): Boolean = parse(value).signum() > 0
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (_: NumberFormatException) {
        null
    }
