import { api } from './client';
import type { Instrument } from './types';

export interface CreateInstrumentRequest {
  symbol: string;
  name: string;
  initialPrice: number;
}

/** All instruments, including inactive ones. */
export async function listAllInstruments(): Promise<Instrument[]> {
  const { data } = await api.get<Instrument[]>('/api/admin/instruments');
  return data;
}

export async function createInstrument(request: CreateInstrumentRequest): Promise<Instrument> {
  const { data } = await api.post<Instrument>('/api/admin/instruments', request);
  return data;
}

export async function setInstrumentActive(id: number, active: boolean): Promise<Instrument> {
  const { data } = await api.patch<Instrument>(`/api/admin/instruments/${id}`, { active });
  return data;
}
