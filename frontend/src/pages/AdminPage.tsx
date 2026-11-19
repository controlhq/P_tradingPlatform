import { useEffect, useState } from 'react';
import { listAllInstruments, setInstrumentActive } from '../api/admin';
import { toProblem, type ProblemDetail } from '../api/problem';
import type { Instrument } from '../api/types';
import { CreateInstrumentForm } from '../components/CreateInstrumentForm';
import { ErrorAlert } from '../components/ErrorAlert';
import { formatDateTime, formatPrice } from '../format';

const bySymbol = (a: Instrument, b: Instrument) => a.symbol.localeCompare(b.symbol);

export function AdminPage() {
  const [instruments, setInstruments] = useState<Instrument[] | null>(null);
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    listAllInstruments()
      .then(setInstruments)
      .catch((error) => setProblem(toProblem(error)));
  }, []);

  function replace(updated: Instrument) {
    setInstruments((current) => (current ?? []).map((i) => (i.id === updated.id ? updated : i)));
  }

  async function toggle(instrument: Instrument) {
    setProblem(null);
    setBusyId(instrument.id);
    try {
      replace(await setInstrumentActive(instrument.id, !instrument.active));
    } catch (error) {
      setProblem(toProblem(error));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section>
      <div className="page-header">
        <h1>Administracja</h1>
      </div>
      <ErrorAlert problem={problem} />

      <div className="portfolio-grid">
        <div>
          <h2 className="section-title">Instrumenty</h2>
          {!instruments && !problem && <p className="muted">Ładowanie…</p>}
          {instruments && instruments.length === 0 && <p className="muted">Brak instrumentów.</p>}
          {instruments && instruments.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Symbol</th>
                    <th>Nazwa</th>
                    <th className="num">Ostatnia cena</th>
                    <th className="num">Z dnia</th>
                    <th>Status</th>
                    <th aria-label="Akcje" />
                  </tr>
                </thead>
                <tbody>
                  {instruments.map((instrument) => (
                    <tr key={instrument.id} className={instrument.active ? '' : 'row-inactive'}>
                      <td className="symbol">{instrument.symbol}</td>
                      <td>{instrument.name}</td>
                      <td className="num mono">{formatPrice(instrument.lastPrice)}</td>
                      <td className="num muted">
                        {instrument.lastPriceAt ? formatDateTime(instrument.lastPriceAt) : '—'}
                      </td>
                      <td>
                        <span className={`status ${instrument.active ? 'status-filled' : 'status-cancelled'}`}>
                          {instrument.active ? 'Aktywny' : 'Wyłączony'}
                        </span>
                      </td>
                      <td className="num">
                        <button
                          className="btn btn-small"
                          type="button"
                          disabled={busyId === instrument.id}
                          onClick={() => void toggle(instrument)}
                        >
                          {instrument.active ? 'Wyłącz' : 'Włącz'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="muted hint table-footnote">
            Wyłączony instrument znika z listy rynków, ale jego historia cen i pozycje zostają.
          </p>
        </div>
        <CreateInstrumentForm onCreated={(created) => setInstruments((current) => [...(current ?? []), created].sort(bySymbol))} />
      </div>
    </section>
  );
}
