import { useState, type FormEvent } from 'react';
import { fieldErrors, toProblem, type ProblemDetail } from '../api/problem';
import { placeOrder } from '../api/trading';
import type { Order, OrderSide, OrderType, PlaceOrderRequest } from '../api/types';
import { formatMoney, formatPrice } from '../format';
import { decimalError, parseDecimal } from '../numbers';
import { ErrorAlert } from './ErrorAlert';
import { FormField } from './FormField';

// mirror the backend: quantity 1..10000 (BR03), prices fit NUMERIC(19,4)
const MAX_QUANTITY = 10_000;
const MAX_PRICE = 999_999_999_999_999;
// backend default of orders.stop-buy-reserve-factor (BR42); used only for the preview
const STOP_BUY_RESERVE_FACTOR = 1.05;

type Field = 'quantity' | 'limitPrice' | 'stopPrice';

interface OrderFormProps {
  instrumentId: number;
  symbol: string;
  currentPrice: number | null;
  availableFunds: number | null;
  freeShares: number;
  onPlaced(order: Order): void;
}

function quantityError(input: string): string | undefined {
  if (!/^\d+$/.test(input.trim())) {
    return 'Podaj liczbę całkowitą.';
  }
  const value = Number(input);
  return value < 1 || value > MAX_QUANTITY ? `Od 1 do ${MAX_QUANTITY.toLocaleString('pl-PL')}.` : undefined;
}

export function OrderForm({ instrumentId, symbol, currentPrice, availableFunds, freeShares, onPlaced }: OrderFormProps) {
  const [side, setSide] = useState<OrderSide>('BUY');
  const [type, setType] = useState<OrderType>('MARKET');
  const [quantity, setQuantity] = useState('1');
  const [limitPrice, setLimitPrice] = useState('');
  const [stopPrice, setStopPrice] = useState('');
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // ----- preview: what the order would cost or bring, based on the price relevant for its type
  const qty = quantityError(quantity) ? null : Number(quantity);
  const referencePrice =
    type === 'LIMIT' ? parseDecimal(limitPrice) : type === 'STOP' ? parseDecimal(stopPrice) : currentPrice;
  const value = qty !== null && referencePrice !== null ? qty * referencePrice : null;
  const reservation = value !== null && side === 'BUY' && type === 'STOP' ? value * STOP_BUY_RESERVE_FACTOR : value;
  const exceedsFunds = side === 'BUY' && reservation !== null && availableFunds !== null && reservation > availableFunds;
  const exceedsShares = side === 'SELL' && qty !== null && qty > freeShares;

  function validate(): Partial<Record<Field, string>> {
    const result: Partial<Record<Field, string>> = { quantity: quantityError(quantity) };
    if (type === 'LIMIT') {
      result.limitPrice = decimalError(limitPrice, MAX_PRICE);
    }
    if (type === 'STOP') {
      result.stopPrice = decimalError(stopPrice, MAX_PRICE);
    }
    return result;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setProblem(null);
    setResult(null);
    const clientErrors = validate();
    setErrors(clientErrors);
    if (Object.values(clientErrors).some(Boolean)) {
      return;
    }

    const request: PlaceOrderRequest = { side, type, instrumentId, quantity: Number(quantity) };
    if (type === 'LIMIT') {
      request.limitPrice = parseDecimal(limitPrice)!;
    }
    if (type === 'STOP') {
      request.stopPrice = parseDecimal(stopPrice)!;
    }

    setSubmitting(true);
    try {
      const order = await placeOrder(request);
      const verb = order.side === 'BUY' ? 'Kupiono' : 'Sprzedano';
      setResult(
        order.status === 'FILLED'
          ? `${verb} ${order.quantity} szt. ${symbol} po ${formatPrice(order.filledPrice)}.`
          : `Zlecenie ${order.type} #${order.id} czeka na realizację.`,
      );
      onPlaced(order);
    } catch (error) {
      const serverProblem = toProblem(error);
      setProblem(serverProblem);
      setErrors(fieldErrors(serverProblem));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form panel" onSubmit={handleSubmit} noValidate>
      <h2>Nowe zlecenie</h2>

      <div className="segmented" role="group" aria-label="Strona zlecenia">
        <button type="button" className={side === 'BUY' ? 'active buy' : ''} aria-pressed={side === 'BUY'} onClick={() => setSide('BUY')}>
          Kup
        </button>
        <button type="button" className={side === 'SELL' ? 'active sell' : ''} aria-pressed={side === 'SELL'} onClick={() => setSide('SELL')}>
          Sprzedaj
        </button>
      </div>

      <div className="field">
        <label htmlFor="order-type">Typ</label>
        <select id="order-type" value={type} onChange={(e) => setType(e.target.value as OrderType)}>
          <option value="MARKET">MARKET: od razu po bieżącej cenie</option>
          <option value="LIMIT">LIMIT: po cenie nie gorszej niż limit</option>
          <option value="STOP">STOP: gdy cena przekroczy próg</option>
        </select>
      </div>

      <FormField
        label="Ilość (szt.)"
        inputMode="numeric"
        value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        error={errors.quantity}
      />
      {type === 'LIMIT' && (
        <FormField
          label="Cena limitu"
          inputMode="decimal"
          value={limitPrice}
          onChange={(e) => setLimitPrice(e.target.value)}
          error={errors.limitPrice}
        />
      )}
      {type === 'STOP' && (
        <FormField
          label="Cena stop"
          inputMode="decimal"
          value={stopPrice}
          onChange={(e) => setStopPrice(e.target.value)}
          error={errors.stopPrice}
        />
      )}

      <dl className="summary">
        {side === 'BUY' ? (
          <>
            <dt>{type === 'STOP' ? 'Rezerwacja (z buforem 5%)' : type === 'LIMIT' ? 'Rezerwacja' : 'Szacowany koszt'}</dt>
            <dd className={`mono ${exceedsFunds ? 'down' : ''}`}>{formatMoney(reservation)}</dd>
            <dt>Dostępne środki</dt>
            <dd className="mono">{formatMoney(availableFunds)}</dd>
          </>
        ) : (
          <>
            <dt>Szacowany przychód</dt>
            <dd className="mono">{formatMoney(value)}</dd>
            <dt>Wolne akcje</dt>
            <dd className={`mono ${exceedsShares ? 'down' : ''}`}>{freeShares}</dd>
          </>
        )}
      </dl>
      {exceedsFunds && <p className="field-error">Za mało dostępnych środków.</p>}
      {exceedsShares && <p className="field-error">Za mało wolnych akcji.</p>}

      <ErrorAlert problem={problem} />
      {result && (
        <p className="alert-success" role="status">
          {result}
        </p>
      )}

      <button className={`btn ${side === 'BUY' ? 'btn-buy' : 'btn-sell'}`} type="submit" disabled={submitting}>
        {submitting ? 'Wysyłanie…' : side === 'BUY' ? `Kup ${symbol}` : `Sprzedaj ${symbol}`}
      </button>
    </form>
  );
}
