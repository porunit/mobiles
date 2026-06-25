package com.rmp.trader.data.api

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the auth token both in memory (for fast, synchronous interceptor reads)
 * and persisted in [SharedPreferences] (so the session survives app restarts).
 *
 * The in-memory [AtomicReference] is the source of truth at runtime; it is
 * seeded from SharedPreferences on construction.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val memoryToken = AtomicReference<String?>(prefs.getString(KEY_TOKEN, null))
    private val memoryEmail = AtomicReference<String?>(prefs.getString(KEY_EMAIL, null))

    val token: String?
        get() = memoryToken.get()

    val email: String?
        get() = memoryEmail.get()

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    fun save(token: String, email: String?) {
        memoryToken.set(token)
        memoryEmail.set(email)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clear() {
        memoryToken.set(null)
        memoryEmail.set(null)
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .apply()
    }

    private companion object {
        const val PREFS = "rmp_trader_auth"
        const val KEY_TOKEN = "access_token"
        const val KEY_EMAIL = "email"
    }
}
