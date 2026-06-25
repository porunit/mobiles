/**
 * Typed HTTP client for ALL contract endpoints behind the Ktor gateway.
 *
 * Base path is /api/v1 (plus a root /health). The base URL is resolved from
 * expo.extra.apiBaseUrl (app.json), falling back to a per-platform default so
 * the Android emulator reaches the host via 10.0.2.2.
 *
 * Every protected call sends `Authorization: Bearer <token>`. A 401 from the
 * gateway triggers the registered unauthorized handler (used by AuthContext to
 * drop the session and send the user back to the auth screen).
 */
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import {
  ApiErrorBody,
  AuthResponse,
  HealthResponse,
  Instrument,
  OrderResponse,
  Page,
  Portfolio,
  PositionView,
  Quote,
  UserMe,
  WalletBalance,
} from './types';

/* ------------------------------------------------------ base URL config */

const DEFAULT_BASE_URL = Platform.select({
  android: 'http://10.0.2.2:8080',
  ios: 'http://localhost:8080',
  default: 'http://localhost:8080',
}) as string;

function resolveBaseUrl(): string {
  // expo-constants surfaces app.json -> expo.extra here.
  const fromConfig = (Constants.expoConfig?.extra as { apiBaseUrl?: string | null } | undefined)
    ?.apiBaseUrl;
  const base = (fromConfig && fromConfig.trim().length > 0 ? fromConfig : DEFAULT_BASE_URL).replace(
    /\/+$/,
    '',
  );
  return base;
}

export const BASE_URL = resolveBaseUrl();
export const API_PREFIX = `${BASE_URL}/api/v1`;

/* --------------------------------------------------------------- errors */

/** Error thrown for any non-2xx response, exposing the server `message`/`code`. */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly body?: ApiErrorBody;

  constructor(status: number, message: string, code?: string, body?: ApiErrorBody) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.body = body;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }
}

/* --------------------------------------------------------- token + 401 */

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

/** Set/clear the bearer token used for protected calls. */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

/** Register a callback invoked whenever the gateway returns 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

/* ----------------------------------------------------------- core fetch */

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Attach Authorization header. Defaults to true. */
  auth?: boolean;
  signal?: AbortSignal;
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, signal } = opts;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && authToken) headers['Authorization'] = `Bearer ${authToken}`;

  let res: Response;
  try {
    res = await fetch(`${API_PREFIX}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (e) {
    // Network failure / server unreachable.
    const msg = e instanceof Error ? e.message : 'Network request failed';
    throw new ApiError(0, `Network error: ${msg}`);
  }

  if (res.status === 401 && onUnauthorized) onUnauthorized();

  // 204 / empty body.
  const text = await res.text();
  const parsed: unknown = text.length > 0 ? safeJson(text) : undefined;

  if (!res.ok) {
    const errBody = (parsed ?? undefined) as ApiErrorBody | undefined;
    const message =
      errBody?.message ??
      errBody?.error ??
      (res.status === 401 ? 'missing or invalid token' : `Request failed (${res.status})`);
    throw new ApiError(res.status, message, errBody?.code, errBody);
  }

  return parsed as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/* ----------------------------------------------------------- public API */

export const api = {
  /* ---- auth (public) ---- */
  register(email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/auth/register', {
      method: 'POST',
      auth: false,
      body: { email, password },
    });
  },
  login(email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>('/auth/login', {
      method: 'POST',
      auth: false,
      body: { email, password },
    });
  },

  /* ---- quotes (public) ---- */
  getQuotes(signal?: AbortSignal): Promise<Quote[]> {
    return request<Quote[]>('/quotes', { auth: false, signal });
  },
  getQuote(ticker: string, signal?: AbortSignal): Promise<Quote> {
    return request<Quote>(`/quotes/${encodeURIComponent(ticker)}`, { auth: false, signal });
  },

  /* ---- instruments (public) ---- */
  getInstruments(signal?: AbortSignal): Promise<Instrument[]> {
    return request<Instrument[]>('/instruments', { auth: false, signal });
  },
  getStocks(page = 0, size = 20, signal?: AbortSignal): Promise<Page<Instrument>> {
    return request<Page<Instrument>>(`/stocks?page=${page}&size=${size}`, { auth: false, signal });
  },

  /* ---- user / wallet (protected) ---- */
  getMe(signal?: AbortSignal): Promise<UserMe> {
    return request<UserMe>('/users/me', { signal });
  },
  getBalance(signal?: AbortSignal): Promise<WalletBalance> {
    return request<WalletBalance>('/wallet/balance', { signal });
  },
  deposit(amount: number): Promise<WalletBalance> {
    return request<WalletBalance>('/wallet/deposit', { method: 'POST', body: { amount } });
  },
  withdraw(amount: number): Promise<WalletBalance> {
    return request<WalletBalance>('/wallet/withdraw', { method: 'POST', body: { amount } });
  },

  /* ---- portfolio (protected) ---- */
  getPortfolio(signal?: AbortSignal): Promise<Portfolio> {
    return request<Portfolio>('/portfolio', { signal });
  },
  getPositions(signal?: AbortSignal): Promise<PositionView[]> {
    return request<PositionView[]>('/portfolio/positions', { signal });
  },

  /* ---- orders (protected) ---- */
  buy(ticker: string, quantity: number): Promise<OrderResponse> {
    return request<OrderResponse>('/orders/buy', { method: 'POST', body: { ticker, quantity } });
  },
  sell(ticker: string, quantity: number): Promise<OrderResponse> {
    return request<OrderResponse>('/orders/sell', { method: 'POST', body: { ticker, quantity } });
  },
  getOrders(page = 0, size = 50, signal?: AbortSignal): Promise<OrderResponse[]> {
    return request<OrderResponse[]>(`/orders?page=${page}&size=${size}`, { signal });
  },

  /* ---- health (public) ---- */
  health(signal?: AbortSignal): Promise<HealthResponse> {
    return request<HealthResponse>('/health', { auth: false, signal });
  },
};
