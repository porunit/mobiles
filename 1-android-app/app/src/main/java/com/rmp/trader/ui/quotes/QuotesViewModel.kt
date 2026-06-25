package com.rmp.trader.ui.quotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmp.trader.data.Repository
import com.rmp.trader.data.api.ApiException
import com.rmp.trader.data.api.QuoteDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QuotesUiState(
    val quotes: List<QuoteDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class QuotesViewModel(private val repository: Repository) : ViewModel() {

    private val _state = MutableStateFlow(QuotesUiState(loading = true))
    val state: StateFlow<QuotesUiState> = _state.asStateFlow()

    init {
        startAutoRefresh()
    }

    /** Polls /quotes roughly every 2 seconds while this ViewModel is alive. */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val quotes = repository.getQuotes().sortedBy { it.symbol }
                _state.update { it.copy(quotes = quotes, loading = false, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: Exception) {
                // Keep showing the last good list, surface the error transiently.
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Failed to load quotes.")
                }
            }
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}
