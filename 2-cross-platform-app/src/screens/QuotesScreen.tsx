import { useFocusEffect } from '@react-navigation/native';
import React, { useCallback, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { api } from '../api';
import { money } from '../format';
import { QuotesTabScreenProps } from '../navigation';
import { colors, spacing } from '../theme';
import { Quote } from '../types';

const REFRESH_MS = 2000;

export default function QuotesScreen({ navigation }: QuotesTabScreenProps) {
  const [quotes, setQuotes] = useState<Quote[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Track the latest in-flight controller so we can cancel on unmount/blur.
  const abortRef = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const data = await api.getQuotes(controller.signal);
      if (!controller.signal.aborted) {
        setQuotes(data);
        setError(null);
      }
    } catch (e) {
      if (!controller.signal.aborted) {
        setError(e instanceof Error ? e.message : 'Failed to load quotes');
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  // Poll every ~2s while the screen is focused; stop when it blurs.
  useFocusEffect(
    useCallback(() => {
      let active = true;
      setLoading(true);
      void load();
      const id = setInterval(() => {
        if (active) void load();
      }, REFRESH_MS);
      return () => {
        active = false;
        clearInterval(id);
        abortRef.current?.abort();
      };
    }, [load]),
  );

  const renderItem = useCallback(
    ({ item }: { item: Quote }) => (
      <TouchableOpacity
        style={styles.row}
        onPress={() => navigation.navigate('Trade', { ticker: item.symbol })}
        accessibilityRole="button"
      >
        <View style={styles.rowLeft}>
          <Text style={styles.symbol}>{item.symbol}</Text>
          <Text style={styles.bidask}>
            bid {money(item.bid)} · ask {money(item.ask)}
          </Text>
        </View>
        <View style={styles.rowRight}>
          <Text style={styles.last}>{money(item.last)}</Text>
          <Text style={styles.volume}>vol {item.volume.toLocaleString()}</Text>
        </View>
      </TouchableOpacity>
    ),
    [navigation],
  );

  if (loading && quotes.length === 0) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} />
        <Text style={styles.dim}>Loading quotes…</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {error ? (
        <View style={styles.banner}>
          <Text style={styles.bannerText}>{error}</Text>
        </View>
      ) : null}
      <FlatList
        data={quotes}
        keyExtractor={(q) => q.symbol}
        renderItem={renderItem}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        contentContainerStyle={quotes.length === 0 ? styles.center : undefined}
        ListEmptyComponent={<Text style={styles.dim}>No quotes available.</Text>}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  center: { flexGrow: 1, justifyContent: 'center', alignItems: 'center', padding: spacing.xl },
  dim: { color: colors.textDim, marginTop: spacing.sm },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    backgroundColor: colors.card,
  },
  rowLeft: { flex: 1 },
  rowRight: { alignItems: 'flex-end' },
  symbol: { color: colors.text, fontSize: 17, fontWeight: '700' },
  bidask: { color: colors.textDim, fontSize: 13, marginTop: 2 },
  last: { color: colors.text, fontSize: 18, fontWeight: '600' },
  volume: { color: colors.textDim, fontSize: 12, marginTop: 2 },
  separator: { height: 1, backgroundColor: colors.border },
  banner: { backgroundColor: colors.cardAlt, padding: spacing.sm },
  bannerText: { color: colors.down, fontSize: 13, textAlign: 'center' },
});
