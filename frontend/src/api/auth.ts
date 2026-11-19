import { api } from './client';
import type { TokenResponse, User } from './types';

export async function login(email: string, password: string): Promise<TokenResponse> {
  const { data } = await api.post<TokenResponse>('/api/auth/login', { email, password });
  return data;
}

export async function register(email: string, password: string): Promise<User> {
  const { data } = await api.post<User>('/api/auth/register', { email, password });
  return data;
}

/** Revokes the refresh token on the server; always succeeds there (BR17). */
export async function logout(refreshToken: string): Promise<void> {
  await api.post('/api/auth/logout', { refreshToken });
}

export async function currentUser(): Promise<User> {
  const { data } = await api.get<User>('/api/users/me');
  return data;
}
