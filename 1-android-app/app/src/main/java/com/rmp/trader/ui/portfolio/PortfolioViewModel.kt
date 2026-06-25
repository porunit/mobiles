package com.rmp.trader.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rmp.trader.data.Repository
import com.rmp.trader.data.api.ApiException
import com.rmp.trader.data.api.PortfolioDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PortfolioUiState(
    val portfolio: PortfolioDto? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null
)

class PortfolioViewModel(private val repository: Repository) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioUiState(loading = true))
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.portfolio == null, error = null) }
        fetch()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            try {
                val portfolio = repository.getPortfolio()
                _state.update {
                    it.copy(portfolio = portfolio, loading = false, refreshing = false, error = null)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "Failed to load portfolio."
                    )
                }
            }
        }
    }
}
