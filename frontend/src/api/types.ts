export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export type Role = 'USER' | 'ADMIN';

export interface User {
  id: number;
  email: string;
  role: Role;
}
