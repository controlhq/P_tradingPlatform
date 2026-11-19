import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import { refreshTokens } from '../api/client';
import { onSessionExpired, tokens } from '../api/tokens';
import type { User } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthState {
  status: AuthStatus;
  user: User | null;
  login(email: string, password: string): Promise<void>;
  register(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<User | null>(null);

  const signOutLocally = useCallback(() => {
    tokens.clear();
    setUser(null);
    setStatus('anonymous');
  }, []);

  // after a reload only the refresh token is left: try to restore the session from it
  useEffect(() => {
    if (!tokens.getRefresh()) {
      setStatus('anonymous');
      return;
    }
    let cancelled = false;
    refreshTokens()
      .then(() => authApi.currentUser())
      .then((me) => {
        if (!cancelled) {
          setUser(me);
          setStatus('authenticated');
        }
      })
      .catch(() => {
        if (!cancelled) {
          signOutLocally();
        }
      });
    return () => {
      cancelled = true;
    };
  }, [signOutLocally]);

  useEffect(() => onSessionExpired(signOutLocally), [signOutLocally]);

  const login = useCallback(async (email: string, password: string) => {
    const pair = await authApi.login(email, password);
    tokens.set(pair.accessToken, pair.refreshToken);
    setUser(await authApi.currentUser());
    setStatus('authenticated');
  }, []);

  const register = useCallback(
    async (email: string, password: string) => {
      await authApi.register(email, password);
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokens.getRefresh();
    signOutLocally();
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch {
        // the local session is gone anyway; the token simply expires on the server
      }
    }
  }, [signOutLocally]);

  const value = useMemo(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
