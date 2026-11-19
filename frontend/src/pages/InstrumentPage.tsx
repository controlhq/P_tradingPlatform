import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getInstrument, getPriceHistory } from '../api/market';
import { toProblem, type ProblemDetail } from '../api/problem';
import { getAccount, getPositions } from '../api/trading';
import type { Account, Instrument, Position, PricePoint } from '../api/types';
import { ErrorAlert } from '../components/ErrorAlert';
import { OrderForm } from '../components/OrderForm';
import { PriceChart } from '../components/PriceChart';
import { formatPercent, formatPrice, trendClass } from '../format';
import { useMarketStream } from '../stream/useMarketStream';

const HISTORY_LIMIT = 300;
// keeps the chart responsive on a page left open for a long time
const MAX_POINTS = 1000;

export function InstrumentPage() {
  const id = Number(useParams().id);
  const { subscribeTicks, subscribeOrders } = useMarketStream();

  const [instrument, setInstrument] = useState<Instrument | null>(null);
  const [points, setPoints] = useState<PricePoint[]>([]);
  const [account, setAccount] = useState<Account | null>(null);
  const [position, setPosition] = useState<Position | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  const loadHoldings = useCallback(async () => {
    const [acc, positions] = await Promise.all([getAccount(), getPositions()]);
    setAccount(acc);
    setPosition(positions.find((p) => p.instrumentId === id) ?? null);
  }, [id]);

  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) {
      setProblem({ title: 'Nie znaleziono', detail: 'Nieprawidłowy identyfikator instrumentu.' });
      return;
    }
    let cancelled = false;
    setProblem(null);
    Promise.all([getInstrument(id), getPriceHistory(id, HISTORY_LIMIT), loadHoldings()])
      .then(([inst, history]) => {
        if (!cancelled) {
          setInstrument(inst);
          setPoints(history);
        }
      })
      .catch((error) => !cancelled && setProblem(toProblem(error)));
    return () => {
      cancelled = true;
    };
  }, [id, loadHoldings]);

  // live ticks extend the history; older or repeated ticks (e.g. after a reconnect) are skipped
  useEffect(
    () =>
      subscribeTicks((tick) => {
        if (tick.instrumentId !== id) {
          return;
        }
        setPoints((current) => {
          const last = current[current.length - 1];
          // compared as instants: history has microseconds, stream events nanoseconds
          if (last && Date.parse(last.ts) >= Date.parse(tick.ts)) {
            return current;
          }
          return [...current, { price: tick.price, ts: tick.ts }].slice(-MAX_POINTS);
        });
      }),
    [id, subscribeTicks],
  );

  // a pending order may be filled at any time, which changes cash and shares
  useEffect(() => subscribeOrders(() => void loadHoldings().catch(() => undefined)), [loadHoldings, subscribeOrders]);

  const last = points[points.length - 1];
  const first = points[0];
  const change = last && first ? ((last.price - first.price) / first.price) * 100 : null;

  if (problem && !instrument) {
    return (
      <section>
        <ErrorAlert problem={problem} />
        <p>
          <Link to="/markets">← Wróć do rynków</Link>
        </p>
      </section>
    );
  }
  if (!instrument) {
    return <p className="muted">Ładowanie…</p>;
  }

  return (
    <section>
      <div className="page-header">
        <div>
          <Link to="/markets" className="muted back-link">
            ← Rynki
          </Link>
          <h1>
            {instrument.symbol} <span className="muted title-name">{instrument.name}</span>
          </h1>
        </div>
        <div className="quote">
          <span className="mono quote-price">{formatPrice(last?.price ?? instrument.lastPrice)}</span>
          {change !== null && (
            <span className={`mono ${trendClass(change)}`}>{formatPercent(change)} w widocznym okresie</span>
          )}
        </div>
      </div>

      <ErrorAlert problem={problem} />
      {!instrument.active && (
        <p className="alert-error">Instrument jest nieaktywny, nie można składać zleceń.</p>
      )}

      <div className="instrument-grid">
        <div className="panel">
          <PriceChart points={points} />
        </div>
        {instrument.active && (
          <OrderForm
            instrumentId={instrument.id}
            symbol={instrument.symbol}
            currentPrice={last?.price ?? instrument.lastPrice}
            availableFunds={account?.available ?? null}
            freeShares={position ? position.quantity - position.blockedQuantity : 0}
            onPlaced={() => void loadHoldings().catch(() => undefined)}
          />
        )}
      </div>
    </section>
  );
}
