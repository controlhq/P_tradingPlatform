import { api } from './client';
import type { Account, Order, OrderStatus, Page, PlaceOrderRequest, Position } from './types';

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

export async function listOrders(page: number, size: number, status?: OrderStatus): Promise<Page<Order>> {
  const { data } = await api.get<Page<Order>>('/api/orders', { params: { page, size, status } });
  return data;
}

export async function cancelOrder(id: number): Promise<Order> {
  const { data } = await api.delete<Order>(`/api/orders/${id}`);
  return data;
}
