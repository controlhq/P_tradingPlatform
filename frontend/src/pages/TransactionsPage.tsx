import { useEffect, useState } from 'react';
import { toProblem, type ProblemDetail } from '../api/problem';
import { listTransactions } from '../api/trading';
import type { Page, Transaction, TransactionType } from '../api/types';
import { ErrorAlert } from '../components/ErrorAlert';
import { Pagination } from '../components/Pagination';
import { formatDateTime, formatMoney } from '../format';

const PAGE_SIZE = 20;

const TYPE_LABELS: Record<TransactionType, string> = {
  DEPOSIT: 'Wpłata',
  WITHDRAW: 'Wypłata',
  BUY: 'Kupno',
  SELL: 'Sprzedaż',
};

// money comes in with deposits and sales, goes out with withdrawals and purchases
const isInflow = (type: TransactionType) => type === 'DEPOSIT' || type === 'SELL';

export function TransactionsPage() {
  const [type, setType] = useState<TransactionType | ''>('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<Transaction> | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);

  useEffect(() => {
    let cancelled = false;
    listTransactions(page, PAGE_SIZE, type || undefined)
      .then((result) => !cancelled && setData(result))
      .catch((error) => !cancelled && setProblem(toProblem(error)));
    return () => {
      cancelled = true;
    };
  }, [page, type]);

  function changeType(value: TransactionType | '') {
    setType(value);
    setPage(0);
  }

  return (
    <section>
      <div className="page-header">
        <h1>Historia transakcji</h1>
        <div className="field field-inline">
          <label htmlFor="type-filter">Typ</label>
          <select id="type-filter" value={type} onChange={(e) => changeType(e.target.value as TransactionType | '')}>
            <option value="">Wszystkie</option>
            {(Object.keys(TYPE_LABELS) as TransactionType[]).map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </div>
      </div>
      <ErrorAlert problem={problem} />

      {!data && !problem && <p className="muted">Ładowanie…</p>}
      {data && data.content.length === 0 && <p className="muted">Brak transakcji.</p>}
      {data && data.content.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Data</th>
                <th>Typ</th>
                <th className="num">Kwota</th>
                <th className="num">Saldo po</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((transaction) => {
                const inflow = isInflow(transaction.type);
                return (
                  <tr key={transaction.id}>
                    <td className="muted">{formatDateTime(transaction.createdAt)}</td>
                    <td>{TYPE_LABELS[transaction.type]}</td>
                    <td className={`num mono ${inflow ? 'up' : 'down'}`}>
                      {inflow ? '+' : '−'}
                      {formatMoney(transaction.amount)}
                    </td>
                    <td className="num mono">{formatMoney(transaction.balanceAfter)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {data && (
        <>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          <p className="muted table-footnote">Łącznie: {data.totalElements}</p>
        </>
      )}
    </section>
  );
}
