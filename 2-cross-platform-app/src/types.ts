/**
 * Wire-contract types for the RMP trading gateway (base path /api/v1).
 *
 * IMPORTANT: money & quantity fields arrive as DECIMAL STRINGS
 * (e.g. "217.34626900"), NOT numbers. They are typed as `string`
 * everywhere and must only be passed through Number()/parseFloat() at
 * the very last moment for display math — never stored as float.
 */

/* ----------------------------------------------------------------- auth */

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // seconds, e.g. 86400
  userId: number;
  email: string;
}

/* --------------------------------------------------------------- quotes */

export interface Quote {
  symbol: string;
  bid: string; // decimal string
  ask: string; // decimal string
  last: string; // decimal string
  volume: number;
  ts: number; // epoch millis
}

/* ---------------------------------------------------------- instruments */

export interface Instrument {
  id: number;
  ticker: string;
  name: string;
  currency: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

/* ------------------------------------------------------- user & wallet */

export interface UserMe {
  id: number;
  email: string;
  cashBalance: string; // decimal string
  createdAt: string;
}

export interface WalletBalance {
  cashBalance: string; // decimal string
}

/* ------------------------------------------------------------ portfolio */

export interface PositionView {
  ticker: string;
  quantity: string; // decimal string
  avgPrice: string; // decimal string
  currentPrice: string; // decimal string
  marketValue: string; // decimal string
  unrealizedPnl: string; // decimal string (may be negative)
}

export interface Portfolio {
  cashBalance: string; // decimal string
  positions: PositionView[];
  totalValue: string; // decimal string
  totalPnl: string; // decimal string
}

/* --------------------------------------------------------------- orders */

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET';
export type OrderStatus = 'FILLED' | 'REJECTED' | string;

export interface OrderResponse {
  id: number;
  ticker: string;
  side: OrderSide;
  type: OrderType;
  quantity: string; // decimal string
  fillPrice: string; // decimal string (server-side price)
  notional: string; // decimal string
  status: OrderStatus;
  createdAt: string;
}

/** Body for /orders/buy and /orders/sell. NOTE: no price — server prices it. */
export interface OrderRequest {
  ticker: string;
  quantity: number;
}

/* --------------------------------------------------------------- errors */

/** Unified error body from db-service for non-2xx responses. */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  path?: string;
  traceId?: string;
}

export interface HealthResponse {
  status: string; // "UP"
}
