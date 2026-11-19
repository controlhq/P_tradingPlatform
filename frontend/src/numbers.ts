// Parsing of user-typed amounts and prices. Comma or dot as the decimal separator; at most 4 decimal
// places, like NUMERIC(19,4) on the backend.
const DECIMAL = /^\d+([.,]\d{1,4})?$/;

/** The number typed by the user, or null when it is not a non-negative decimal with up to 4 places. */
export function parseDecimal(input: string): number | null {
  const value = input.trim();
  return DECIMAL.test(value) ? Number(value.replace(',', '.')) : null;
}

/** Validation message for a required price or amount in (0, max], or undefined when valid. */
export function decimalError(input: string, max: number): string | undefined {
  if (!input.trim()) {
    return 'Pole wymagane.';
  }
  const value = parseDecimal(input);
  if (value === null) {
    return 'Podaj liczbę z najwyżej 4 miejscami po przecinku.';
  }
  if (value <= 0) {
    return 'Wartość musi być większa od 0.';
  }
  if (value > max) {
    return `Najwyżej ${max.toLocaleString('pl-PL')}.`;
  }
  return undefined;
}
