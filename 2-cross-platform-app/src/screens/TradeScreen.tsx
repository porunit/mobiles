import { useFocusEffect } from '@react-navigation/native';
import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { ApiError, api } from '../api';
import { money } from '../format';
import { TradeScreenProps } from '../navigation';
import { colors, spacing } from '../theme';
import { OrderResponse, Quote, WalletBalance } from '../types';

const QUOTE_REFRESH_MS = 2000;
const DEPOSIT_AMOUNT = 1_000_000;

export default function TradeScreen({ route }: TradeScreenProps) {
  const initialTicker = route.params?.ticker ?? '';
  const [ticker, setTicker] = useState(initialTicker);
  const [quantity, setQuantity] = useState('1');
  const [quote, setQuote] = useState<Quote | null>(null);
  const [balance, setBalance] = useState<string | null>(null);
  const [lastOrder, setLastOrder] = useState<OrderResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Keep ticker in sync if navigated here from a quote row with a new param.
  useEffect(() => {
    if (route.params?.ticker) setTicker(route.params.ticker);
  }, [route.params?.ticker]);

  // Poll the live quote for the current ticker while focused.
  useFocusEffect(
    useCallback(() => {
      let active = true;
      const fetchQuote = async () => {
        const t = ticker.trim().toUpperCase();
        if (!t) {
          setQuote(null);
          return;
        }
        try {
          const q = await api.getQuote(t);
          if (active) setQuote(q);
        } catch {
          if (active) setQuote(null);
        }
      };
      void fetchQuote();
      const id = setInterval(fetchQuote, QUOTE_REFRESH_MS);
      return () => {
        active = false;
        clearInterval(id);
      };
    }, [ticker]),
  );

  // Load the wallet balance when the screen gains focus.
  const loadBalance = useCallback(async () => {
    try {
      const b: WalletBalance = await api.getBalance();
      setBalance(b.cashBalance);
    } catch (e) {
      if (e instanceof ApiError && e.isUnauthorized) return;
      // Non-fatal: balance is informational here.
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void loadBalance();
    }, [loadBalance]),
  );

  const parsedQty = Number(quantity);
  const qtyValid = Number.isInteger(parsedQty) && parsedQty > 0;

  const placeOrder = async (side: 'buy' | 'sell') => {
    setError(null);
    setInfo(null);
    setLastOrder(null);
    const t = ticker.trim().toUpperCase();
    if (!t) {
      setError('Enter a ticker.');
      return;
    }
    if (!qtyValid) {
      setError('Quantity must be a positive whole number.');
      return;
    }
    setBusy(true);
    try {
      const order = side === 'buy' ? await api.buy(t, parsedQty) : await api.sell(t, parsedQty);
      setLastOrder(order);
      await loadBalance();
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.isUnauthorized) return; // global handler logs out
        setError(e.message); // e.g. "insufficient funds" / "insufficient position"
      } else {
        setError(e instanceof Error ? e.message : 'Order failed');
      }
    } finally {
      setBusy(false);
    }
  };

  const deposit = async () => {
    setError(null);
    setInfo(null);
    setBusy(true);
    try {
      const res = await api.deposit(DEPOSIT_AMOUNT);
      setBalance(res.cashBalance);
      setInfo(`Deposited ${money(String(DEPOSIT_AMOUNT))}. New balance: ${money(res.cashBalance)}.`);
    } catch (e) {
      if (e instanceof ApiError && e.isUnauthorized) return;
      setError(e instanceof Error ? e.message : 'Deposit failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.balanceRow}>
          <Text style={styles.balanceLabel}>Cash balance</Text>
          <Text style={styles.balanceValue}>{balance == null ? '—' : money(balance)}</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.label}>Ticker</Text>
          <TextInput
            style={styles.input}
            value={ticker}
            onChangeText={(v) => setTicker(v.toUpperCase())}
            placeholder="SBER"
            placeholderTextColor={colors.textDim}
            autoCapitalize="characters"
            autoCorrect={false}
            editable={!busy}
          />

          <View style={styles.quoteBox}>
            {quote ? (
              <>
                <Text style={styles.quoteLast}>{money(quote.last)}</Text>
                <Text style={styles.quoteSub}>
                  bid {money(quote.bid)} · ask {money(quote.ask)}
                </Text>
              </>
            ) : (
              <Text style={styles.quoteSub}>
                {ticker.trim() ? 'No live quote for this ticker.' : 'Enter a ticker to see a quote.'}
              </Text>
            )}
          </View>

          <Text style={styles.label}>Quantity</Text>
          <TextInput
            style={styles.input}
            value={quantity}
            onChangeText={setQuantity}
            placeholder="1"
            placeholderTextColor={colors.textDim}
            keyboardType="number-pad"
            editable={!busy}
          />

          <View style={styles.actions}>
            <TouchableOpacity
              style={[styles.btn, styles.buyBtn, busy && styles.btnDisabled]}
              onPress={() => placeOrder('buy')}
              disabled={busy}
              accessibilityRole="button"
            >
              <Text style={styles.btnText}>BUY</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.btn, styles.sellBtn, busy && styles.btnDisabled]}
              onPress={() => placeOrder('sell')}
              disabled={busy}
              accessibilityRole="button"
            >
              <Text style={styles.btnText}>SELL</Text>
            </TouchableOpacity>
          </View>

          {busy ? <ActivityIndicator color={colors.primary} style={styles.spinner} /> : null}

          {error ? <Text style={styles.error}>{error}</Text> : null}
          {info ? <Text style={styles.info}>{info}</Text> : null}

          {lastOrder ? (
            <View style={styles.orderCard}>
              <Text style={styles.orderTitle}>
                {lastOrder.side} {lastOrder.ticker} · {lastOrder.status}
              </Text>
              <OrderRow label="Quantity" value={lastOrder.quantity} />
              <OrderRow label="Fill price" value={money(lastOrder.fillPrice)} />
              <OrderRow label="Notional" value={money(lastOrder.notional)} />
              <OrderRow label="Order #" value={String(lastOrder.id)} />
            </View>
          ) : null}
        </View>

        <View style={styles.card}>
          <Text style={styles.fundTitle}>Funding</Text>
          <Text style={styles.fundHint}>
            Demo account funding — deposits {money(String(DEPOSIT_AMOUNT))} so you can place orders.
          </Text>
          <TouchableOpacity
            style={[styles.btn, styles.depositBtn, busy && styles.btnDisabled]}
            onPress={deposit}
            disabled={busy}
            accessibilityRole="button"
          >
            <Text style={styles.btnText}>Deposit {money(String(DEPOSIT_AMOUNT))}</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function OrderRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.orderRow}>
      <Text style={styles.orderLabel}>{label}</Text>
      <Text style={styles.orderValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg },
  balanceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.md,
  },
  balanceLabel: { color: colors.textDim, fontSize: 14 },
  balanceValue: { color: colors.text, fontSize: 18, fontWeight: '700' },
  card: {
    backgroundColor: colors.card,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.lg,
  },
  label: { color: colors.textDim, fontSize: 13, marginBottom: spacing.xs, marginTop: spacing.sm },
  input: {
    backgroundColor: colors.inputBg,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: spacing.md,
    paddingVertical: Platform.OS === 'ios' ? 14 : 10,
    color: colors.text,
    fontSize: 16,
  },
  quoteBox: {
    marginTop: spacing.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
    backgroundColor: colors.cardAlt,
    borderRadius: 10,
  },
  quoteLast: { color: colors.text, fontSize: 26, fontWeight: '700' },
  quoteSub: { color: colors.textDim, fontSize: 13, marginTop: 2 },
  actions: { flexDirection: 'row', marginTop: spacing.lg, gap: spacing.md },
  btn: { flex: 1, borderRadius: 10, paddingVertical: 14, alignItems: 'center' },
  buyBtn: { backgroundColor: colors.buy },
  sellBtn: { backgroundColor: colors.sell },
  depositBtn: { backgroundColor: colors.primary, marginTop: spacing.md },
  btnDisabled: { opacity: 0.5 },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  spinner: { marginTop: spacing.md },
  error: { color: colors.danger, marginTop: spacing.md, fontSize: 14 },
  info: { color: colors.up, marginTop: spacing.md, fontSize: 14 },
  orderCard: {
    marginTop: spacing.lg,
    backgroundColor: colors.cardAlt,
    borderRadius: 10,
    padding: spacing.md,
  },
  orderTitle: { color: colors.text, fontSize: 15, fontWeight: '700', marginBottom: spacing.sm },
  orderRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 3 },
  orderLabel: { color: colors.textDim, fontSize: 14 },
  orderValue: { color: colors.text, fontSize: 14, fontWeight: '600' },
  fundTitle: { color: colors.text, fontSize: 16, fontWeight: '700' },
  fundHint: { color: colors.textDim, fontSize: 13, marginTop: spacing.xs, marginBottom: spacing.sm },
});
