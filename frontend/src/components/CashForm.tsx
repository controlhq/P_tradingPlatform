import { useState, type FormEvent } from 'react';
import { fieldErrors, toProblem, type ProblemDetail } from '../api/problem';
import { deposit, withdraw } from '../api/trading';
import type { Account } from '../api/types';
import { formatMoney } from '../format';
import { decimalError, parseDecimal } from '../numbers';
import { ErrorAlert } from './ErrorAlert';
import { FormField } from './FormField';

// limit per single operation (BR29)
const MAX_AMOUNT = 1_000_000;

type Operation = 'deposit' | 'withdraw';

export function CashForm({ onChanged }: { onChanged(account: Account): void }) {
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [pending, setPending] = useState<Operation | null>(null);

  async function submit(operation: Operation) {
    setProblem(null);
    setResult(null);
    const clientError = decimalError(amount, MAX_AMOUNT);
    setError(clientError);
    if (clientError) {
      return;
    }

    const value = parseDecimal(amount)!;
    setPending(operation);
    try {
      const account = operation === 'deposit' ? await deposit(value) : await withdraw(value);
      setResult(`${operation === 'deposit' ? 'Wpłacono' : 'Wypłacono'} ${formatMoney(value)}.`);
      setAmount('');
      onChanged(account);
    } catch (err) {
      const serverProblem = toProblem(err);
      setProblem(serverProblem);
      setError(fieldErrors(serverProblem).amount);
    } finally {
      setPending(null);
    }
  }

  // Enter in the field means a deposit, the safer of the two operations
  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void submit('deposit');
  }

  return (
    <form className="form panel" onSubmit={handleSubmit} noValidate>
      <h2>Wpłata / wypłata</h2>
      <FormField
        label="Kwota"
        inputMode="decimal"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={error}
      />
      <p className="muted hint">Najwyżej {formatMoney(MAX_AMOUNT)} na operację. Wypłata tylko z dostępnych środków.</p>
      <ErrorAlert problem={problem} />
      {result && (
        <p className="alert-success" role="status">
          {result}
        </p>
      )}
      <div className="button-row">
        <button className="btn btn-primary" type="submit" disabled={pending !== null}>
          {pending === 'deposit' ? 'Wpłacanie…' : 'Wpłać'}
        </button>
        <button className="btn" type="button" disabled={pending !== null} onClick={() => void submit('withdraw')}>
          {pending === 'withdraw' ? 'Wypłacanie…' : 'Wypłać'}
        </button>
      </div>
    </form>
  );
}
