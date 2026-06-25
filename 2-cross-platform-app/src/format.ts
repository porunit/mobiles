/**
 * Display helpers for decimal-string money/quantity values.
 *
 * The wire keeps these as strings to preserve precision. We only ever call
 * Number()/parseFloat() here, at the display boundary, never to store state.
 */

/** Format a decimal string as a fixed-precision number for display. */
export function money(value: string | null | undefined, digits = 2): string {
  if (value == null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return value; // show raw if not numeric
  return n.toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

/** Format a quantity decimal string, trimming trailing zeros. */
export function qty(value: string | null | undefined): string {
  if (value == null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  return String(n);
}

/** Sign-aware P&L string with a leading + for non-negative values. */
export function pnl(value: string | null | undefined, digits = 2): string {
  if (value == null || value === '') return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return value;
  const formatted = Math.abs(n).toLocaleString(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
  return n < 0 ? `-${formatted}` : `+${formatted}`;
}

/** True when a decimal string represents a negative number. */
export function isNegative(value: string | null | undefined): boolean {
  if (value == null || value === '') return false;
  return Number(value) < 0;
}
