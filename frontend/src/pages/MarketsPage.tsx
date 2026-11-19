import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listInstruments } from '../api/market';
import { toProblem, type ProblemDetail } from '../api/problem';
import type { Instrument } from '../api/types';
import { ErrorAlert } from '../components/ErrorAlert';
import { MarketStatusBadge } from '../components/MarketStatusBadge';
import { formatPercent, formatPrice, formatTime, trendClass } from '../format';
import { useMarketStream } from '../stream/useMarketStream';

export function MarketsPage() {
  const { prices } = useMarketStream();
  const [instruments, setInstruments] = useState<Instrument[] | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  useEffect(() => {
    listInstruments()
      .then(setInstruments)
      .catch((error) => setProblem(toProblem(error)));
  }, []);

  return (
    <section>
      <div className="page-header">
        <h1>Rynki</h1>
        <MarketStatusBadge />
      </div>
      <ErrorAlert problem={problem} />
      {!instruments && !problem && <p className="muted">Ładowanie…</p>}
      {instruments && instruments.length === 0 && <p className="muted">Brak aktywnych instrumentów.</p>}
      {instruments && instruments.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Nazwa</th>
                <th className="num">Cena</th>
                <th className="num">Zmiana</th>
                <th className="num">Aktualizacja</th>
              </tr>
            </thead>
            <tbody>
              {instruments.map((instrument) => {
                const live = prices[instrument.id];
                const price = live?.price ?? instrument.lastPrice;
                const ts = live?.ts ?? instrument.lastPriceAt;
                const change = live?.previous ? live.price - live.previous : null;
                const changePct = change !== null && live?.previous ? (change / live.previous) * 100 : null;
                const direction = trendClass(change);
                return (
                  <tr key={instrument.id}>
                    <td>
                      <Link to={`/instruments/${instrument.id}`} className="symbol">
                        {instrument.symbol}
                      </Link>
                    </td>
                    <td className="muted">{instrument.name}</td>
                    {/* the key changes with every tick, so the flash animation runs again */}
                    <td key={ts ?? 'none'} className={`num mono ${direction ? `flash-${direction}` : ''}`}>
                      {formatPrice(price)}
                    </td>
                    <td className={`num mono ${direction}`}>
                      {change === null ? '—' : formatPercent(changePct)}
                    </td>
                    <td className="num muted mono">{ts ? formatTime(ts) : '—'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
