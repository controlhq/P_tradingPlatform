import { api } from './client';
import type { Account, Order, PlaceOrderRequest, Position } from './types';

export async function getAccount(): Promise<Account> {
  const { data } = await api.get<Account>('/api/account');
  return data;
}

export async function deposit(amount: number): Promise<Account> {
  const { data } = await api.post<Account>('/api/account/deposit', { amount });
  return data;
}

export async function withdraw(amount: number): Promise<Account> {
  const { data } = await api.post<Account>('/api/account/withdraw', { amount });
  return data;
}

export async function getPositions(): Promise<Position[]> {
  const { data } = await api.get<Position[]>('/api/positions');
  return data;
}

export async function placeOrder(request: PlaceOrderRequest): Promise<Order> {
  const { data } = await api.post<Order>('/api/orders', request);
  return data;
}
