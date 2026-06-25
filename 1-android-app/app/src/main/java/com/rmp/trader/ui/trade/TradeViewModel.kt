package com.rmp.trader.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmp.trader.data.Repository
import com.rmp.trader.data.api.ApiException
import com.rmp.trader.data.api.OrderResponse
import com.rmp.trader.data.api.QuoteDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TradeUiState(
    val ticker: String = "",
    val quantity: String = "1",
    val quote: QuoteDto? = null,
    val cashBalance: String? = null,
    val submitting: Boolean = false,
    val lastOrder: OrderResponse? = null,
    val error: String? = null,
    val info: String? = null
)

class TradeViewModel(private val repository: Repository) : ViewModel() {

    private val _state = MutableStateFlow(TradeUiState())
    val state: StateFlow<TradeUiState> = _state.asStateFlow()

    private var initialized = false

    /** Seed the screen with the tapped ticker; only runs once. */
    fun init(ticker: String) {
        if (initialized) return
        initialized = true
        _state.update { it.copy(ticker = ticker.uppercase()) }
        refreshQuote()
        refreshBalance()
    }

    fun onTickerChange(value: String) =
        _state.update { it.copy(ticker = value.uppercase().trim(), error = null) }

    fun onQuantityChange(value: String) {
        // keep digits only
        val cleaned = value.filter { it.isDigit() }
        _state.update { it.copy(quantity = cleaned, error = null) }
    }

    fun refreshQuote() {
        val ticker = _state.value.ticker
        if (ticker.isBlank()) return
        viewModelScope.launch {
            try {
                val quote = repository.getQuote(ticker)
                _state.update { it.copy(quote = quote) }
            } catch (_: Exception) {
                // A missing quote should not block trading UI; just leave it null.
                _state.update { it.copy(quote = null) }
            }
        }
    }

    fun refreshBalance() {
        viewModelScope.launch {
            try {
                val balance = repository.getBalance()
                _state.update { it.copy(cashBalance = balance.cashBalance) }
            } catch (_: Exception) {
                // non-fatal
            }
        }
    }

    fun buy() = submitOrder(buy = true)

    fun sell() = submitOrder(buy = false)

    private fun submitOrder(buy: Boolean) {
        val current = _state.value
        if (current.submitting) return

        val ticker = current.ticker.trim()
        val qty = current.quantity.toLongOrNull()
        if (ticker.isBlank()) {
            _state.update { it.copy(error = "Enter a ticker.") }
            return
        }
        if (qty == null || qty <= 0) {
            _state.update { it.copy(error = "Enter a quantity greater than 0.") }
            return
        }

        _state.update { it.copy(submitting = true, error = null, info = null, lastOrder = null) }
        viewModelScope.launch {
            try {
                val order = if (buy) repository.buy(ticker, qty) else repository.sell(ticker, qty)
                _state.update {
                    it.copy(submitting = false, lastOrder = order, error = null)
                }
                refreshBalance()
            } catch (e: ApiException) {
                _state.update { it.copy(submitting = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(submitting = false, error = e.message ?: "Order failed.")
                }
            }
        }
    }

    fun deposit(amount: Long) {
        if (amount <= 0) {
            _state.update { it.copy(error = "Enter a deposit amount greater than 0.") }
            return
        }
        viewModelScope.launch {
            try {
                val balance = repository.deposit(amount)
                _state.update {
                    it.copy(cashBalance = balance.cashBalance, info = "Deposited $amount", error = null)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Deposit failed.") }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }
}
