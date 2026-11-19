import type { Role } from './types';

export interface AccessTokenClaims {
  sub?: string;
  /** Seconds since the epoch. */
  exp?: number;
  role?: Role;
}

/**
 * Reads the claims of a JWT without verifying it. Only for UI decisions (when to refresh, which links to
 * show); the server verifies the signature on every request.
 */
export function decodeClaims(token: string): AccessTokenClaims | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)) as AccessTokenClaims;
  } catch {
    return null;
  }
}
