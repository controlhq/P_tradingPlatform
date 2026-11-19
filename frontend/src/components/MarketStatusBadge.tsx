import { useEffect, useState } from 'react';
import { getMarketStatus } from '../api/market';
import type { MarketStatus } from '../api/types';
import { formatDateTime } from '../format';

const POLL_MS = 60_000;

/** Open/closed state of the market; re-checked every minute and right after the next scheduled change. */
export function MarketStatusBadge() {
  const [status, setStatus] = useState<MarketStatus | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = () =>
      getMarketStatus()
        .then((s) => !cancelled && setStatus(s))
        .catch(() => !cancelled && setStatus(null));
    void load();
    const timer = window.setInterval(load, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!status?.nextChange) {
      return;
    }
    const delay = new Date(status.nextChange).getTime() - Date.now() + 1000;
    if (delay <= 0 || delay > POLL_MS) {
      return;
    }
    const timer = window.setTimeout(() => void getMarketStatus().then(setStatus).catch(() => undefined), delay);
    return () => window.clearTimeout(timer);
  }, [status]);

  if (!status) {
    return null;
  }
  let detail = 'notowania całą dobę';
  if (status.nextChange) {
    detail = `${status.open ? 'zamknięcie' : 'otwarcie'}: ${formatDateTime(status.nextChange)}`;
  }
  return (
    <span className={`badge ${status.open ? 'badge-open' : 'badge-closed'}`}>
      {status.open ? 'Rynek otwarty' : 'Rynek zamknięty'}
      <span className="badge-detail">{detail}</span>
    </span>
  );
}
