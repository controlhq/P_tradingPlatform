import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toProblem, type ProblemDetail } from '../api/problem';
import { cancelOrder, listOrders } from '../api/trading';
import type { Order, OrderStatus, Page } from '../api/types';
import { ErrorAlert } from '../components/ErrorAlert';
import { Pagination } from '../components/Pagination';
import { formatDateTime, formatMoney, formatPrice } from '../format';
import { useMarketStream } from '../stream/useMarketStream';

const PAGE_SIZE = 20;

const STATUS_LABELS: Record<OrderStatus, string> = {
  NEW: 'Oczekuje',
  FILLED: 'Wykonane',
  CANCELLED: 'Anulowane',
  REJECTED: 'Odrzucone',
};

export function OrdersPage() {
  const { subscribeOrders } = useMarketStream();
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Order> | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [cancelling, setCancelling] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      setData(await listOrders(page, PAGE_SIZE, status || undefined));
    } catch (error) {
      setProblem(toProblem(error));
    }
  }, [page, status]);

  useEffect(() => {
    void load();
  }, [load]);

  // a fill or rejection changes a row and, with a status filter, which rows belong on the page
  useEffect(() => subscribeOrders(() => void load()), [load, subscribeOrders]);

  function changeStatus(value: OrderStatus | '') {
    setStatus(value);
    setPage(0);
  }

  async function handleCancel(order: Order) {
    setProblem(null);
    setCancelling(order.id);
    try {
      await cancelOrder(order.id);
    } catch (error) {
      // e.g. ORDER_NOT_CANCELLABLE when matching filled it a moment earlier
      setProblem(toProblem(error));
    } finally {
      setCancelling(null);
      await load();
    }
  }

  return (
    <section>
      <div className="page-header">
        <h1>Zlecenia</h1>
        <div className="field field-inline">
          <label htmlFor="status-filter">Status</label>
          <select id="status-filter" value={status} onChange={(e) => changeStatus(e.target.value as OrderStatus | '')}>
            <option value="">Wszystkie</option>
            {(Object.keys(STATUS_LABELS) as OrderStatus[]).map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
      </div>
      <ErrorAlert problem={problem} />

      {!data && !problem && <p className="muted">Ładowanie…</p>}
      {data && data.content.length === 0 && <p className="muted">Brak zleceń.</p>}
      {data && data.content.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Złożono</th>
                <th>Symbol</th>
                <th>Strona</th>
                <th>Typ</th>
                <th className="num">Ilość</th>
                <th className="num">Limit / stop</th>
                <th className="num">Rezerwacja</th>
                <th>Status</th>
                <th className="num">Cena wykonania</th>
                <th aria-label="Akcje" />
              </tr>
            </thead>
            <tbody>
              {data.content.map((order) => (
                <tr key={order.id}>
                  <td className="muted">{formatDateTime(order.createdAt)}</td>
                  <td>
                    <Link to={`/instruments/${order.instrumentId}`} className="symbol">
                      {order.symbol}
                    </Link>
                  </td>
                  <td className={order.side === 'BUY' ? 'up' : 'down'}>{order.side === 'BUY' ? 'Kupno' : 'Sprzedaż'}</td>
                  <td>{order.type}</td>
                  <td className="num mono">{order.quantity}</td>
                  <td className="num mono">{formatPrice(order.limitPrice ?? order.stopPrice)}</td>
                  <td className="num mono">{order.reservedAmount > 0 ? formatMoney(order.reservedAmount) : '—'}</td>
                  <td>
                    <span className={`status status-${order.status.toLowerCase()}`}>{STATUS_LABELS[order.status]}</span>
                  </td>
                  <td className="num mono">{formatPrice(order.filledPrice)}</td>
                  <td className="num">
                    {order.status === 'NEW' && (
                      <button
                        className="btn btn-small"
                        type="button"
                        disabled={cancelling === order.id}
                        onClick={() => void handleCancel(order)}
                      >
                        {cancelling === order.id ? 'Anulowanie…' : 'Anuluj'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
  );
}
