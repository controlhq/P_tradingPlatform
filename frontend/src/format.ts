const money = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const price = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
const percent = new Intl.NumberFormat('pl-PL', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
});
const time = new Intl.DateTimeFormat('pl-PL', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
const dateTime = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'short' });

const orDash = (value: number | null | undefined, format: (n: number) => string) =>
  value === null || value === undefined || Number.isNaN(value) ? '—' : format(value);

export const formatMoney = (value: number | null | undefined) => orDash(value, (n) => money.format(n));
export const formatPrice = (value: number | null | undefined) => orDash(value, (n) => price.format(n));
/** {@code value} is already in percent, e.g. 12.5 for 12,5%. */
export const formatPercent = (value: number | null | undefined) => orDash(value, (n) => `${percent.format(n)}%`);
export const formatTime = (iso: string) => time.format(new Date(iso));
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso));

/** CSS class for a signed value: green above zero, red below. */
export const trendClass = (value: number | null | undefined) =>
  value === null || value === undefined || value === 0 ? '' : value > 0 ? 'up' : 'down';
