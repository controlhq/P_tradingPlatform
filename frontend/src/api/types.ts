// Shapes of the backend DTOs. Money and prices are NUMERIC(19,4) on the server and arrive as JSON numbers;
// the frontend only displays them and makes estimates, the server does all real calculations.

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

export interface Instrument {
  id: number;
  symbol: string;
  name: string;
  active: boolean;
  lastPrice: number | null;
  lastPriceAt: string | null;
}

export interface PricePoint {
  price: number;
  ts: string;
}

export interface MarketStatus {
  open: boolean;
  /** Next opening or closing; null when the market is configured as always open. */
  nextChange: string | null;
}

export interface Account {
  balance: number;
  blockedBalance: number;
  available: number;
}

export interface Position {
  instrumentId: number;
  symbol: string;
  quantity: number;
  blockedQuantity: number;
  avgPrice: number;
  currentPrice: number | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
  unrealizedPnlPct: number | null;
}

export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT' | 'STOP';
export type OrderStatus = 'NEW' | 'FILLED' | 'CANCELLED' | 'REJECTED';

export interface Order {
  id: number;
  instrumentId: number;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  status: OrderStatus;
  quantity: number;
  limitPrice: number | null;
  stopPrice: number | null;
  reservedAmount: number;
  filledPrice: number | null;
  filledAt: string | null;
  createdAt: string;
}

export interface PlaceOrderRequest {
  side: OrderSide;
  type: OrderType;
  instrumentId: number;
  quantity: number;
  limitPrice?: number;
  stopPrice?: number;
}

/** SSE "price" event. */
export interface PriceEvent {
  instrumentId: number;
  symbol: string;
  price: number;
  ts: string;
}

/** SSE "order" event, sent only to the order's owner. */
export interface OrderEvent {
  orderId: number;
  status: OrderStatus;
  filledPrice: number | null;
}
