package com.rmp.trader.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the `Authorization: Bearer <token>` header to outgoing requests when a
 * token is present. Public endpoints simply have no token, so the header is
 * omitted and the gateway treats them as anonymous.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.token

        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
