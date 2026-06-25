package com.rmp.trader.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rmp.trader.data.Repository
import com.rmp.trader.ui.ViewModelFactory
import com.rmp.trader.ui.auth.AuthScreen
import com.rmp.trader.ui.auth.AuthViewModel
import com.rmp.trader.ui.portfolio.PortfolioScreen
import com.rmp.trader.ui.portfolio.PortfolioViewModel
import com.rmp.trader.ui.quotes.QuotesScreen
import com.rmp.trader.ui.quotes.QuotesViewModel
import com.rmp.trader.ui.trade.TradeScreen
import com.rmp.trader.ui.trade.TradeViewModel

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.QUOTES, "Quotes", Icons.Filled.ShowChart),
    TabItem(Routes.PORTFOLIO, "Portfolio", Icons.Filled.AccountBalanceWallet),
    TabItem(Routes.TRADE, "Trade", Icons.Filled.SwapHoriz)
)

@Composable
fun AppNav(repository: Repository) {
    val navController = rememberNavController()
    val factory = remember(repository) { ViewModelFactory(repository) }

    // When a protected call returns 401, drop the session and bounce to auth.
    LaunchedEffect(repository) {
        repository.authExpired.collect {
            repository.logout()
            navController.navigate(Routes.AUTH) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val startDestination = if (repository.isLoggedIn()) Routes.QUOTES else Routes.AUTH

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.AUTH) {
            val vm: AuthViewModel = viewModel(factory = factory)
            AuthScreen(
                viewModel = vm,
                onAuthenticated = {
                    navController.navigate(Routes.QUOTES) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.QUOTES) {
            MainScaffold(navController, repository, title = "Quotes") { padding ->
                val vm: QuotesViewModel = viewModel(factory = factory)
                QuotesScreen(
                    viewModel = vm,
                    onQuoteClick = { ticker -> navController.navigate(Routes.trade(ticker)) },
                    modifier = androidx.compose.ui.Modifier.padding(padding)
                )
            }
        }

        composable(Routes.PORTFOLIO) {
            MainScaffold(navController, repository, title = "Portfolio") { padding ->
                val vm: PortfolioViewModel = viewModel(factory = factory)
                PortfolioScreen(
                    viewModel = vm,
                    onPositionClick = { ticker -> navController.navigate(Routes.trade(ticker)) },
                    modifier = androidx.compose.ui.Modifier.padding(padding)
                )
            }
        }

        composable(
            route = Routes.TRADE_WITH_ARG,
            arguments = listOf(
                navArgument(Routes.TRADE_ARG_TICKER) {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val ticker = entry.arguments?.getString(Routes.TRADE_ARG_TICKER).orEmpty()
            MainScaffold(navController, repository, title = "Trade") { padding ->
                // Scope the Trade ViewModel to this back-stack entry so a new
                // ticker selection gets a fresh, correctly-seeded screen.
                val vm: TradeViewModel = viewModel(viewModelStoreOwner = entry, factory = factory)
                TradeScreen(
                    viewModel = vm,
                    ticker = ticker,
                    modifier = androidx.compose.ui.Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    navController: NavHostController,
    repository: Repository,
    title: String,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    IconButton(onClick = {
                        repository.logout()
                        navController.navigate(Routes.AUTH) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { dest ->
                        dest.route == tab.route || dest.route == Routes.TRADE_WITH_ARG && tab.route == Routes.TRADE
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val target = if (tab.route == Routes.TRADE) Routes.trade("") else tab.route
                            navController.navigate(target) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        content(padding)
    }
}
