package com.rmp.trader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rmp.trader.data.Repository
import com.rmp.trader.ui.auth.AuthViewModel
import com.rmp.trader.ui.portfolio.PortfolioViewModel
import com.rmp.trader.ui.quotes.QuotesViewModel
import com.rmp.trader.ui.trade.TradeViewModel

/**
 * Lightweight factory that injects the [Repository] singleton into ViewModels
 * without pulling in a DI framework.
 */
class ViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(repository) as T
            modelClass.isAssignableFrom(QuotesViewModel::class.java) ->
                QuotesViewModel(repository) as T
            modelClass.isAssignableFrom(PortfolioViewModel::class.java) ->
                PortfolioViewModel(repository) as T
            modelClass.isAssignableFrom(TradeViewModel::class.java) ->
                TradeViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
