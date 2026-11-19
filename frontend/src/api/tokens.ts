// The access token lives only in memory, so it is gone after a reload and never sits in storage that
// scripts can read for long. The refresh token is kept in localStorage to restore the session.
const REFRESH_KEY = 'trading.refreshToken';

let accessToken: string | null = null;
const expiryListeners = new Set<() => void>();

function readRefresh(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export const tokens = {
  getAccess: (): string | null => accessToken,

  getRefresh: readRefresh,

  set(access: string, refresh: string): void {
    accessToken = access;
    try {
      localStorage.setItem(REFRESH_KEY, refresh);
    } catch {
      // storage unavailable (private mode): the session just will not survive a reload
    }
  },

  clear(): void {
    accessToken = null;
    try {
      localStorage.removeItem(REFRESH_KEY);
    } catch {
      // nothing to remove
    }
  },
};

/** Called when the session cannot be renewed; returns an unsubscribe function. */
export function onSessionExpired(listener: () => void): () => void {
  expiryListeners.add(listener);
  return () => {
    expiryListeners.delete(listener);
  };
}

export function notifySessionExpired(): void {
  expiryListeners.forEach((listener) => listener());
}
