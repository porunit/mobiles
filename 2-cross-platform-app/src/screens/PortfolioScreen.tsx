import { useFocusEffect } from '@react-navigation/native';
import React, { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { ApiError, api } from '../api';
import { isNegative, money, pnl, qty } from '../format';
import { colors, spacing } from '../theme';
import { Portfolio } from '../types';

export default function PortfolioScreen() {
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api.getPortfolio();
      setPortfolio(data);
      setError(null);
    } catch (e) {
      // A 401 is handled globally (logs out). Show message for everything else.
      if (e instanceof ApiError && e.isUnauthorized) return;
      setError(e instanceof Error ? e.message : 'Failed to load portfolio');
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      let active = true;
      setLoading(true);
      void load().finally(() => {
        if (active) setLoading(false);
      });
      return () => {
        active = false;
      };
    }, [load]),
  );

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }, [load]);

  if (loading && !portfolio) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
        <Text style={styles.dim}>Loading portfolio…</Text>
      </View>
    );
  }

  const positions = portfolio?.positions ?? [];

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />
      }
    >
      {error ? (
        <View style={styles.banner}>
          <Text style={styles.bannerText}>{error}</Text>
        </View>
      ) : null}

      <View style={styles.summary}>
        <SummaryItem label="Cash" value={money(portfolio?.cashBalance)} />
        <SummaryItem label="Total value" value={money(portfolio?.totalValue)} />
        <SummaryItem
          label="Total P&L"
          value={pnl(portfolio?.totalPnl)}
          tone={isNegative(portfolio?.totalPnl) ? 'down' : 'up'}
        />
      </View>

      <Text style={styles.sectionTitle}>Positions</Text>

      <View style={styles.table}>
        <View style={[styles.tr, styles.thead]}>
          <Text style={[styles.th, styles.colTicker]}>Ticker</Text>
          <Text style={[styles.th, styles.colNum]}>Qty</Text>
          <Text style={[styles.th, styles.colNum]}>Avg</Text>
          <Text style={[styles.th, styles.colNum]}>Last</Text>
          <Text style={[styles.th, styles.colNum]}>P&L</Text>
        </View>

        {positions.length === 0 ? (
          <Text style={styles.empty}>No open positions. Buy something on the Trade tab.</Text>
        ) : (
          positions.map((p) => {
            const down = isNegative(p.unrealizedPnl);
            return (
              <View key={p.ticker} style={styles.tr}>
                <Text style={[styles.td, styles.colTicker, styles.bold]}>{p.ticker}</Text>
                <Text style={[styles.td, styles.colNum]}>{qty(p.quantity)}</Text>
                <Text style={[styles.td, styles.colNum]}>{money(p.avgPrice)}</Text>
                <Text style={[styles.td, styles.colNum]}>{money(p.currentPrice)}</Text>
                <Text
                  style={[styles.td, styles.colNum, { color: down ? colors.down : colors.up }]}
                >
                  {pnl(p.unrealizedPnl)}
                </Text>
              </View>
            );
          })
        )}
      </View>
    </ScrollView>
  );
}

function SummaryItem({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone?: 'up' | 'down';
}) {
  const color = tone === 'up' ? colors.up : tone === 'down' ? colors.down : colors.text;
  return (
    <View style={styles.summaryItem}>
      <Text style={styles.summaryLabel}>{label}</Text>
      <Text style={[styles.summaryValue, { color }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.bg,
  },
  dim: { color: colors.textDim, marginTop: spacing.sm },
  banner: { backgroundColor: colors.cardAlt, padding: spacing.sm, borderRadius: 8, marginBottom: spacing.md },
  bannerText: { color: colors.down, fontSize: 13, textAlign: 'center' },
  summary: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: colors.card,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
  },
  summaryItem: { flex: 1, alignItems: 'center' },
  summaryLabel: { color: colors.textDim, fontSize: 12 },
  summaryValue: { fontSize: 17, fontWeight: '700', marginTop: spacing.xs },
  sectionTitle: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '700',
    marginTop: spacing.xl,
    marginBottom: spacing.sm,
  },
  table: {
    backgroundColor: colors.card,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  tr: {
    flexDirection: 'row',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  thead: { backgroundColor: colors.cardAlt },
  th: { color: colors.textDim, fontSize: 12, fontWeight: '600' },
  td: { color: colors.text, fontSize: 14 },
  bold: { fontWeight: '700' },
  colTicker: { flex: 1.2 },
  colNum: { flex: 1, textAlign: 'right' },
  empty: { color: colors.textDim, padding: spacing.lg, textAlign: 'center' },
});
