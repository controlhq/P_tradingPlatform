import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { notifySessionExpired, tokens } from './tokens';
import type { TokenResponse } from './types';

export const API_URL: string = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

const AUTH_PREFIX = '/api/auth/';

/** Client for all API calls: adds the bearer token and renews it once on 401. */
export const api = axios.create({ baseURL: API_URL });

// no interceptors, so a failing refresh cannot trigger another refresh
const bare = axios.create({ baseURL: API_URL });

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

const isAuthCall = (config: InternalAxiosRequestConfig) => config.url?.startsWith(AUTH_PREFIX) ?? false;

api.interceptors.request.use((config) => {
  const token = tokens.getAccess();
  if (token && !isAuthCall(config)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing: Promise<string> | null = null;

/**
 * Exchanges the stored refresh token for a new pair. Concurrent callers share one request, because the
 * backend rotates the refresh token and treats a second use of the old one as theft (BR16).
 */
export function refreshTokens(): Promise<string> {
  refreshing ??= (async () => {
    const refreshToken = tokens.getRefresh();
    if (!refreshToken) {
      throw new Error('No refresh token');
    }
    const { data } = await bare.post<TokenResponse>(`${AUTH_PREFIX}refresh`, { refreshToken });
    tokens.set(data.accessToken, data.refreshToken);
    return data.accessToken;
  })().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

const EXPIRY_MARGIN_MS = 30_000;

function expiresSoon(token: string): boolean {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const { exp } = JSON.parse(atob(payload)) as { exp?: number };
    return exp === undefined || exp * 1000 - Date.now() < EXPIRY_MARGIN_MS;
  } catch {
    return true;
  }
}

/**
 * An access token valid for at least the next 30 s, refreshed if needed. For connections that cannot go
 * through the interceptor, such as EventSource, which the server checks only when it is opened.
 */
export async function freshAccessToken(): Promise<string> {
  const token = tokens.getAccess();
  return token && !expiresSoon(token) ? token : refreshTokens();
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    if (error.response?.status !== 401 || !config || config._retried || isAuthCall(config)) {
      throw error;
    }
    config._retried = true;

    let token: string;
    try {
      token = await refreshTokens();
    } catch {
      // the session cannot be renewed: log out and report the original 401
      tokens.clear();
      notifySessionExpired();
      throw error;
    }
    config.headers.Authorization = `Bearer ${token}`;
    return api(config);
  },
);
