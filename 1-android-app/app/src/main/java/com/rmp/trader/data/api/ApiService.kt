package com.rmp.trader.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface covering ALL gateway contract endpoints under /api/v1.
 *
 * Public endpoints (auth, quotes, instruments, health) do not require a token.
 * Protected endpoints (users, wallet, portfolio, orders) require the
 * Authorization: Bearer <token> header, which is added transparently by
 * [AuthInterceptor].
 */
interface ApiService {

    // ---------- Auth (public) ----------

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: AuthRequest): AuthResponse

    // ---------- Quotes (public) ----------

    @GET("api/v1/quotes")
    suspend fun getQuotes(): List<QuoteDto>

    @GET("api/v1/quotes/{ticker}")
    suspend fun getQuote(@Path("ticker") ticker: String): QuoteDto

    // ---------- Instruments (public) ----------

    @GET("api/v1/instruments")
    suspend fun getInstruments(): List<InstrumentDto>

    @GET("api/v1/stocks")
    suspend fun getStocks(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PageDto<InstrumentDto>

    // ---------- User / wallet (protected) ----------

    @GET("api/v1/users/me")
    suspend fun getMe(): UserDto

    @GET("api/v1/wallet/balance")
    suspend fun getBalance(): BalanceDto

    @POST("api/v1/wallet/deposit")
    suspend fun deposit(@Body body: AmountRequest): BalanceDto

    @POST("api/v1/wallet/withdraw")
    suspend fun withdraw(@Body body: AmountRequest): BalanceDto

    // ---------- Portfolio (protected) ----------

    @GET("api/v1/portfolio")
    suspend fun getPortfolio(): PortfolioDto

    @GET("api/v1/portfolio/positions")
    suspend fun getPositions(): List<PositionDto>

    // ---------- Orders (protected) ----------

    @POST("api/v1/orders/buy")
    suspend fun buy(@Body body: OrderRequest): OrderResponse

    @POST("api/v1/orders/sell")
    suspend fun sell(@Body body: OrderRequest): OrderResponse

    @GET("api/v1/orders")
    suspend fun getOrders(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): List<OrderResponse>

    // ---------- Health (public) ----------

    @GET("api/v1/health")
    suspend fun health(): HealthDto
}
