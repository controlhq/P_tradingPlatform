import { useContext } from 'react';
import { MarketStreamContext, type MarketStreamValue } from './MarketStreamContext';

/** Live prices and order events from the shared SSE connection (see MarketStreamProvider). */
export function useMarketStream(): MarketStreamValue {
  const stream = useContext(MarketStreamContext);
  if (!stream) {
    throw new Error('useMarketStream must be used inside <MarketStreamProvider>');
  }
  return stream;
}
