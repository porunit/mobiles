package com.rmp.trader.data

import com.rmp.trader.data.api.AmountRequest
import com.rmp.trader.data.api.ApiException
import com.rmp.trader.data.api.ApiErrorBody
import com.rmp.trader.data.api.ApiService
import com.rmp.trader.data.api.AuthRequest
import com.rmp.trader.data.api.AuthResponse
import com.rmp.trader.data.api.BalanceDto
import com.rmp.trader.data.api.InstrumentDto
import com.rmp.trader.data.api.Network
import com.rmp.trader.data.api.OrderRequest
import com.rmp.trader.data.api.OrderResponse
import com.rmp.trader.data.api.PortfolioDto
import com.rmp.trader.data.api.QuoteDto
import com.rmp.trader.data.api.TokenStore
import com.rmp.trader.data.api.UserDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import retrofit2.HttpException

/**
 * Single source of truth for data access. Wraps [ApiService], owns the
 * [TokenStore], converts [HttpException]s into a typed [ApiException] (parsing
 * the server `message`), and broadcasts auth failures via [authExpired] so the
 * UI can navigate back to the login screen.
 */
class Repository(
    private val api: ApiService,
    val tokenStore: TokenStore
) {

    private val _authExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits whenever a protected call comes back 401 (token missing/expired). */
    val authExpired: SharedFlow<Unit> = _authExpired

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun currentEmail(): String? = tokenStore.email

    fun logout() = tokenStore.clear()

    // ---------- Auth ----------

    suspend fun register(email: String, password: String): AuthResponse = call {
        api.register(AuthRequest(email.trim(), password)).also {
            tokenStore.save(it.accessToken, it.email.ifBlank { email.trim() })
        }
    }

    suspend fun login(email: String, password: String): AuthResponse = call {
        api.login(AuthRequest(email.trim(), password)).also {
            tokenStore.save(it.accessToken, it.email.ifBlank { email.trim() })
        }
    }

    // ---------- Quotes ----------

    suspend fun getQuotes(): List<QuoteDto> = call { api.getQuotes() }

    suspend fun getQuote(ticker: String): QuoteDto = call { api.getQuote(ticker) }

    // ---------- Instruments ----------

    suspend fun getInstruments(): List<InstrumentDto> = call { api.getInstruments() }

    // ---------- User / wallet ----------

    suspend fun getMe(): UserDto = call { api.getMe() }

    suspend fun getBalance(): BalanceDto = call { api.getBalance() }

    suspend fun deposit(amount: Long): BalanceDto = call { api.deposit(AmountRequest(amount)) }

    suspend fun withdraw(amount: Long): BalanceDto = call { api.withdraw(AmountRequest(amount)) }

    // ---------- Portfolio ----------

    suspend fun getPortfolio(): PortfolioDto = call { api.getPortfolio() }

    // ---------- Orders ----------

    suspend fun buy(ticker: String, quantity: Long): OrderResponse =
        call { api.buy(OrderRequest(ticker, quantity)) }

    suspend fun sell(ticker: String, quantity: Long): OrderResponse =
        call { api.sell(OrderRequest(ticker, quantity)) }

    suspend fun getOrders(): List<OrderResponse> = call { api.getOrders() }

    /**
     * Executes [block], translating Retrofit's [HttpException] into a typed
     * [ApiException] whose [ApiException.message] is the server-provided
     * human-readable message. Signals 401s to observers.
     */
    private suspend fun <T> call(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            val parsed = parseError(e)
            if (e.code() == 401) {
                _authExpired.tryEmit(Unit)
            }
            throw ApiException(
                httpCode = e.code(),
                errorCode = parsed?.code,
                message = parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(e.code())
            )
        }
    }

    private fun parseError(e: HttpException): ApiErrorBody? {
        return try {
            val raw = e.response()?.errorBody()?.string()
            if (raw.isNullOrBlank()) return null
            Network.moshi.adapter(ApiErrorBody::class.java).fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultMessage(code: Int): String = when (code) {
        401 -> "Session expired. Please sign in again."
        403 -> "You are not allowed to perform this action."
        404 -> "Not found."
        409 -> "Already exists."
        in 500..599 -> "Server error. Please try again."
        else -> "Request failed (HTTP $code)."
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        /** Process-wide singleton, created lazily from the application context. */
        fun get(context: android.content.Context): Repository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val store = TokenStore(context.applicationContext)
                    val service = Network.createService(store)
                    Repository(service, store).also { instance = it }
                }
            }
        }
    }
}
