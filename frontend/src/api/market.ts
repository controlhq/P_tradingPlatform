import { api } from './client';
import type { Instrument, MarketStatus, PricePoint } from './types';

export async function listInstruments(): Promise<Instrument[]> {
  const { data } = await api.get<Instrument[]>('/api/instruments');
  return data;
}

export async function getInstrument(id: number): Promise<Instrument> {
  const { data } = await api.get<Instrument>(`/api/instruments/${id}`);
  return data;
}

/** The most recent {@code limit} ticks, oldest first (backend allows 1-1000). */
export async function getPriceHistory(id: number, limit = 200): Promise<PricePoint[]> {
  const { data } = await api.get<PricePoint[]>(`/api/instruments/${id}/prices`, { params: { limit } });
  return data;
}

export async function getMarketStatus(): Promise<MarketStatus> {
  const { data } = await api.get<MarketStatus>('/api/market/status');
  return data;
}
