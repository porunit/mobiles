/**
 * AuthContext: holds the bearer token + identity in memory, persists it to
 * AsyncStorage, and keeps the api client in sync (token + 401 handler).
 *
 * On a 401 from the gateway the api client invokes our unauthorized handler,
 * which clears the session — navigation then falls back to the auth screen
 * because `token` becomes null.
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import React, {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { api, setAuthToken, setUnauthorizedHandler } from './api';

const TOKEN_KEY = 'rmp.auth.token';
const EMAIL_KEY = 'rmp.auth.email';

export interface AuthState {
  token: string | null;
  email: string | null;
  /** True until the persisted token has been loaded from storage. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const clearSession = useCallback(async () => {
    setToken(null);
    setEmail(null);
    setAuthToken(null);
    await AsyncStorage.multiRemove([TOKEN_KEY, EMAIL_KEY]);
  }, []);

  const persistSession = useCallback(async (newToken: string, newEmail: string) => {
    setAuthToken(newToken);
    setToken(newToken);
    setEmail(newEmail);
    await AsyncStorage.multiSet([
      [TOKEN_KEY, newToken],
      [EMAIL_KEY, newEmail],
    ]);
  }, []);

  // Restore persisted session on mount.
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const pairs = await AsyncStorage.multiGet([TOKEN_KEY, EMAIL_KEY]);
        const storedToken = pairs.find((p) => p[0] === TOKEN_KEY)?.[1] ?? null;
        const storedEmail = pairs.find((p) => p[0] === EMAIL_KEY)?.[1] ?? null;
        if (active && storedToken) {
          setAuthToken(storedToken);
          setToken(storedToken);
          setEmail(storedEmail ?? null);
        }
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  // Register the 401 handler so any expired/invalid token logs the user out.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void clearSession();
    });
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  const login = useCallback(
    async (e: string, password: string) => {
      const res = await api.login(e.trim(), password);
      await persistSession(res.accessToken, res.email);
    },
    [persistSession],
  );

  const register = useCallback(
    async (e: string, password: string) => {
      const res = await api.register(e.trim(), password);
      await persistSession(res.accessToken, res.email);
    },
    [persistSession],
  );

  const logout = useCallback(async () => {
    await clearSession();
  }, [clearSession]);

  const value = useMemo<AuthState>(
    () => ({ token, email, loading, login, register, logout }),
    [token, email, loading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
