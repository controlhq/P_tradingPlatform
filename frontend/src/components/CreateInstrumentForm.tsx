import { useState, type FormEvent } from 'react';
import { createInstrument } from '../api/admin';
import { fieldErrors, toProblem, type ProblemDetail } from '../api/problem';
import type { Instrument } from '../api/types';
import { decimalError, parseDecimal } from '../numbers';
import { ErrorAlert } from './ErrorAlert';
import { FormField } from './FormField';

// same rules as CreateInstrumentRequest (BR20, BR21)
const SYMBOL_PATTERN = /^[A-Z0-9.]{1,16}$/;
const NAME_MAX = 255;
const MAX_PRICE = 999_999_999_999_999;

type Field = 'symbol' | 'name' | 'initialPrice';

export function CreateInstrumentForm({ onCreated }: { onCreated(instrument: Instrument): void }) {
  const [symbol, setSymbol] = useState('');
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): Partial<Record<Field, string>> {
    const result: Partial<Record<Field, string>> = {};
    if (!SYMBOL_PATTERN.test(symbol)) {
      result.symbol = '1–16 znaków: wielkie litery, cyfry lub kropka.';
    }
    if (!name.trim()) {
      result.name = 'Podaj nazwę.';
    } else if (name.trim().length > NAME_MAX) {
      result.name = `Najwyżej ${NAME_MAX} znaków.`;
    }
    const priceError = decimalError(price, MAX_PRICE);
    if (priceError) {
      result.initialPrice = priceError;
    }
    return result;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setProblem(null);
    const clientErrors = validate();
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      const created = await createInstrument({ symbol, name: name.trim(), initialPrice: parseDecimal(price)! });
      setSymbol('');
      setName('');
      setPrice('');
      onCreated(created);
    } catch (error) {
      const serverProblem = toProblem(error);
      setProblem(serverProblem);
      const serverErrors: Partial<Record<Field, string>> = fieldErrors(serverProblem);
      if (serverProblem.code === 'SYMBOL_TAKEN') {
        serverErrors.symbol = 'Taki symbol już istnieje.';
      }
      setErrors(serverErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form panel" onSubmit={handleSubmit} noValidate>
      <h2>Nowy instrument</h2>
      <FormField
        label="Symbol"
        value={symbol}
        maxLength={16}
        // symbols are upper case on the backend, so typing in lower case just works
        onChange={(e) => setSymbol(e.target.value.toUpperCase().trim())}
        error={errors.symbol}
      />
      <FormField label="Nazwa" value={name} maxLength={NAME_MAX} onChange={(e) => setName(e.target.value)} error={errors.name} />
      <FormField
        label="Cena początkowa"
        inputMode="decimal"
        value={price}
        onChange={(e) => setPrice(e.target.value)}
        error={errors.initialPrice}
      />
      <ErrorAlert problem={problem} />
      <button className="btn btn-primary" type="submit" disabled={submitting}>
        {submitting ? 'Dodawanie…' : 'Dodaj instrument'}
      </button>
    </form>
  );
}
