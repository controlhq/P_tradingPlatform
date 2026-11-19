import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toProblem, type ProblemDetail } from '../api/problem';
import { getAccount, getPositions } from '../api/trading';
import type { Account, Position } from '../api/types';
import { CashForm } from '../components/CashForm';
import { ErrorAlert } from '../components/ErrorAlert';
import { formatMoney, formatPercent, formatPrice, trendClass } from '../format';
import type { LivePrice } from '../stream/MarketStreamContext';
import { useMarketStream } from '../stream/useMarketStream';

interface Valuation {
  currentPrice: number | null;
  marketValue: number | null;
  pnl: number | null;
  pnlPct: number | null;
}

/** The server values positions when they are loaded; live ticks re-value them with the same formula (BR41). */
function value(position: Position, live: LivePrice | undefined): Valuation {
  const currentPrice = live?.price ?? position.currentPrice;
  if (currentPrice === null) {
    return { currentPrice: null, marketValue: null, pnl: null, pnlPct: null };
  }
  const marketValue = position.quantity * currentPrice;
  const cost = position.quantity * position.avgPrice;
  const pnl = marketValue - cost;
  return { currentPrice, marketValue, pnl, pnlPct: cost === 0 ? 0 : (pnl / cost) * 100 };
}

export function PortfolioPage() {
  const { prices, subscribeOrders } = useMarketStream();
  const [account, setAccount] = useState<Account | null>(null);
  const [positions, setPositions] = useState<Position[] | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  const load = useCallback(async () => {
    try {
      const [acc, pos] = await Promise.all([getAccount(), getPositions()]);
      setAccount(acc);
      setPositions(pos);
      setProblem(null);
    } catch (error) {
      setProblem(toProblem(error));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // a filled pending order changes both cash and positions
  useEffect(() => subscribeOrders(() => void load()), [load, subscribeOrders]);

  const valued = (positions ?? []).map((p) => ({ position: p, valuation: value(p, prices[p.instrumentId]) }));
  const totalValue = valued.reduce((sum, { valuation }) => sum + (valuation.marketValue ?? 0), 0);
  const totalPnl = valued.reduce((sum, { valuation }) => sum + (valuation.pnl ?? 0), 0);

  return (
    <section>
      <div className="page-header">
        <h1>Portfel</h1>
      </div>
      <ErrorAlert problem={problem} />

      <div className="stats">
        <Stat label="Saldo" value={formatMoney(account?.balance)} />
        <Stat label="Zablokowane" value={formatMoney(account?.blockedBalance)} hint="rezerwacje zleceń oczekujących" />
        <Stat label="Dostępne" value={formatMoney(account?.available)} />
        <Stat label="Wartość pozycji" value={positions ? formatMoney(totalValue) : '—'} />
        <Stat
          label="Niezrealizowany wynik"
          value={positions ? formatMoney(totalPnl) : '—'}
          className={positions ? trendClass(totalPnl) : ''}
        />
      </div>

      <div className="portfolio-grid">
        <div>
          <h2 className="section-title">Pozycje</h2>
          {!positions && !problem && <p className="muted">Ładowanie…</p>}
          {positions && positions.length === 0 && (
            <p className="muted">
              Brak pozycji. Kup coś na stronie <Link to="/markets">Rynki</Link>.
            </p>
          )}
          {positions && positions.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th className="num">Ilość</th>
                    <th className="num">Śr. cena</th>
                    <th className="num">Bieżąca</th>
                    <th className="num">Wartość</th>
                    <th className="num">Wynik</th>
                    <th className="num">Wynik %</th>
                  </tr>
                </thead>
                <tbody>
                  {valued.map(({ position, valuation }) => (
                    <tr key={position.instrumentId}>
                      <td>
                        <Link to={`/instruments/${position.instrumentId}`} className="symbol">
                          {position.symbol}
                        </Link>
                      </td>
                      <td className="num mono">
                        {position.quantity}
                        {position.blockedQuantity > 0 && (
                          <span className="muted" title="zablokowane przez zlecenia sprzedaży">
                            {' '}
                            ({position.blockedQuantity} zabl.)
                          </span>
                        )}
                      </td>
                      <td className="num mono">{formatPrice(position.avgPrice)}</td>
                      <td className="num mono">{formatPrice(valuation.currentPrice)}</td>
                      <td className="num mono">{formatMoney(valuation.marketValue)}</td>
                      <td className={`num mono ${trendClass(valuation.pnl)}`}>{formatMoney(valuation.pnl)}</td>
                      <td className={`num mono ${trendClass(valuation.pnl)}`}>{formatPercent(valuation.pnlPct)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        <CashForm onChanged={setAccount} />
      </div>
    </section>
  );
}

function Stat({ label, value, hint, className = '' }: { label: string; value: string; hint?: string; className?: string }) {
  return (
    <div className="stat" title={hint}>
      <span className="stat-label">{label}</span>
      <span className={`stat-value mono ${className}`}>{value}</span>
    </div>
  );
}
