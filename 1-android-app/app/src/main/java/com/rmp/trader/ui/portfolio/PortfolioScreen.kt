package com.rmp.trader.ui.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmp.trader.data.api.PositionDto
import com.rmp.trader.ui.ErrorBox
import com.rmp.trader.ui.LoadingBox
import com.rmp.trader.ui.Money
import com.rmp.trader.ui.theme.LossRed
import com.rmp.trader.ui.theme.ProfitGreen

@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onPositionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.portfolio == null -> LoadingBox()
            state.portfolio == null && state.error != null -> ErrorBox(state.error!!)
            else -> {
                val portfolio = state.portfolio
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 12.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (portfolio != null) {
                        item { SummaryCard(portfolio.cashBalance, portfolio.totalValue, portfolio.totalPnl) }
                        if (portfolio.positions.isEmpty()) {
                            item {
                                Text(
                                    text = "No open positions yet. Buy something from the Trade screen.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            item { PositionsHeader() }
                            items(portfolio.positions, key = { it.ticker }) { pos ->
                                PositionRow(pos, onClick = { onPositionClick(pos.ticker) })
                            }
                        }
                    }
                    if (state.error != null) {
                        item {
                            Text(
                                text = state.error!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::refresh,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun SummaryCard(cashBalance: String, totalValue: String, totalPnl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow("Cash balance", Money.format(cashBalance))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            SummaryRow("Total value", Money.format(totalValue))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            SummaryRow(
                label = "Total P&L",
                value = Money.formatSigned(totalPnl),
                valueColor = pnlColor(totalPnl)
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PositionsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Cell("Ticker", weight = 1.4f, header = true)
        Cell("Qty", weight = 0.8f, header = true)
        Cell("Avg", weight = 1f, header = true)
        Cell("Cur", weight = 1f, header = true)
        Cell("P&L", weight = 1.2f, header = true)
    }
}

@Composable
private fun PositionRow(pos: PositionDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cell(pos.ticker, weight = 1.4f, bold = true)
            Cell(Money.formatQuantity(pos.quantity), weight = 0.8f)
            Cell(Money.format(pos.avgPrice), weight = 1f)
            Cell(Money.format(pos.currentPrice), weight = 1f)
            Cell(
                text = Money.formatSigned(pos.unrealizedPnl),
                weight = 1.2f,
                color = pnlColor(pos.unrealizedPnl)
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String,
    weight: Float,
    header: Boolean = false,
    bold: Boolean = false,
    color: Color? = null
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color ?: if (header) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
}

@Composable
private fun pnlColor(value: String): Color = when {
    Money.isPositive(value) -> ProfitGreen
    Money.isNegative(value) -> LossRed
    else -> MaterialTheme.colorScheme.onSurface
}
