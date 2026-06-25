package com.rmp.trader.data.api

/**
 * Typed exception carrying the parsed server error so the UI can show the
 * human-readable `message` and the data layer can react to auth failures (401).
 */
class ApiException(
    val httpCode: Int,
    val errorCode: String?,
    override val message: String
) : Exception(message) {

    val isUnauthorized: Boolean
        get() = httpCode == 401
}
