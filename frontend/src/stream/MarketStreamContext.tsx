import { createContext, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { API_URL, freshAccessToken } from '../api/client';
import { notifySessionExpired, tokens } from '../api/tokens';
import type { OrderEvent, PriceEvent } from '../api/types';

export interface LivePrice {
  price: number;
  ts: string;
  /** Price before this tick, to show the direction of the change. */
  previous: number | null;
}

export type StreamState = 'connecting' | 'open' | 'reconnecting';

export interface MarketStreamValue {
  /** Latest streamed price per instrument id. */
  prices: Record<number, LivePrice>;
  state: StreamState;
  /** Called for every price tick; returns an unsubscribe function. */
  subscribeTicks(listener: (event: PriceEvent) => void): () => void;
  /** Called when one of the user's orders is filled or rejected; returns an unsubscribe function. */
  subscribeOrders(listener: (event: OrderEvent) => void): () => void;
}

export const MarketStreamContext = createContext<MarketStreamValue | null>(null);

const MAX_RETRY_DELAY_MS = 15_000;

/** One SSE connection per logged-in tab, shared by every page. */
export function MarketStreamProvider({ children }: { children: ReactNode }) {
  const [prices, setPrices] = useState<Record<number, LivePrice>>({});
  const [state, setState] = useState<StreamState>('connecting');
  const tickListeners = useRef(new Set<(event: PriceEvent) => void>());
  const orderListeners = useRef(new Set<(event: OrderEvent) => void>());

  useEffect(() => {
    let source: EventSource | null = null;
    let retryTimer: number | undefined;
    let attempt = 0;
    let stopped = false;

    async function connect() {
      let token: string;
      try {
        token = await freshAccessToken();
      } catch {
        tokens.clear();
        notifySessionExpired();
        return;
      }
      if (stopped) {
        return;
      }

      source = new EventSource(`${API_URL}/api/stream?token=${encodeURIComponent(token)}`);
      source.onopen = () => {
        attempt = 0;
        setState('open');
      };
      source.addEventListener('price', (message) => {
        const event = JSON.parse((message as MessageEvent<string>).data) as PriceEvent;
        setPrices((current) => ({
          ...current,
          [event.instrumentId]: {
            price: event.price,
            ts: event.ts,
            previous: current[event.instrumentId]?.price ?? null,
          },
        }));
        tickListeners.current.forEach((listener) => listener(event));
      });
      source.addEventListener('order', (message) => {
        const event = JSON.parse((message as MessageEvent<string>).data) as OrderEvent;
        orderListeners.current.forEach((listener) => listener(event));
      });
      // EventSource retries a dropped connection by itself, but gives up on an HTTP error such as 401 for an
      // expired token. Reconnecting here with a fresh token and a growing delay covers both cases.
      source.onerror = () => {
        source?.close();
        source = null;
        if (stopped) {
          return;
        }
        setState('reconnecting');
        const delay = Math.min(1000 * 2 ** attempt, MAX_RETRY_DELAY_MS);
        attempt += 1;
        retryTimer = window.setTimeout(connect, delay);
      };
    }

    void connect();
    return () => {
      stopped = true;
      source?.close();
      window.clearTimeout(retryTimer);
    };
  }, []);

  const subscribeTicks = useCallback((listener: (event: PriceEvent) => void) => {
    tickListeners.current.add(listener);
    return () => {
      tickListeners.current.delete(listener);
    };
  }, []);

  const subscribeOrders = useCallback((listener: (event: OrderEvent) => void) => {
    orderListeners.current.add(listener);
    return () => {
      orderListeners.current.delete(listener);
    };
  }, []);

  const value = useMemo(
    () => ({ prices, state, subscribeTicks, subscribeOrders }),
    [prices, state, subscribeTicks, subscribeOrders],
  );
  return <MarketStreamContext.Provider value={value}>{children}</MarketStreamContext.Provider>;
}
