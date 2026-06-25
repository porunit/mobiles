package com.rmp.trader.ui.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rmp.trader.data.api.OrderResponse
import com.rmp.trader.ui.Money
import com.rmp.trader.ui.theme.LossRed
import com.rmp.trader.ui.theme.ProfitGreen

@Composable
fun TradeScreen(
    viewModel: TradeViewModel,
    ticker: String,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(ticker) {
        viewModel.init(ticker)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ----- Account / balance -----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cash balance", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = state.cashBalance?.let { Money.format(it) } ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                DepositRow(onDeposit = viewModel::deposit)
            }
        }

        // ----- Quote -----
        state.quote?.let { quote ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = quote.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bid ${Money.format(quote.bid)}")
                        Text("Last ${Money.format(quote.last)}", fontWeight = FontWeight.Bold)
                        Text("Ask ${Money.format(quote.ask)}")
                    }
                }
            }
        }

        // ----- Order entry -----
        OutlinedTextField(
            value = state.ticker,
            onValueChange = viewModel::onTickerChange,
            label = { Text("Ticker") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.quantity,
            onValueChange = viewModel::onQuantityChange,
            label = { Text("Quantity") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = viewModel::buy,
                enabled = !state.submitting,
                colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen),
                modifier = Modifier.weight(1f)
            ) {
                Text("BUY")
            }
            Button(
                onClick = viewModel::sell,
                enabled = !state.submitting,
                colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                modifier = Modifier.weight(1f)
            ) {
                Text("SELL")
            }
        }

        OutlinedButton(
            onClick = viewModel::refreshQuote,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh quote")
        }

        // ----- Result / messages -----
        state.lastOrder?.let { OrderResultCard(it) }

        state.info?.let { msg ->
            Text(
                text = msg,
                color = ProfitGreen,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        state.error?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DepositRow(onDeposit: (Long) -> Unit) {
    var amount by remember { mutableStateOf("1000000") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() } },
            label = { Text("Deposit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Button(onClick = { onDeposit(amount.toLongOrNull() ?: 0L) }) {
            Text("Fund")
        }
    }
}

@Composable
private fun OrderResultCard(order: OrderResponse) {
    val sideColor = if (order.side.equals("BUY", ignoreCase = true)) ProfitGreen else LossRed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${order.side} ${order.ticker}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = sideColor
                )
                Text(
                    text = order.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            ResultRow("Quantity", Money.formatQuantity(order.quantity))
            ResultRow("Fill price", Money.format(order.fillPrice))
            ResultRow("Notional", Money.format(order.notional))
            ResultRow("Order #", order.id.toString())
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
