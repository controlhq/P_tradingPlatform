import { useMarketStream } from '../stream/useMarketStream';

const LABELS = {
  open: 'Na żywo',
  connecting: 'Łączenie…',
  reconnecting: 'Ponowne łączenie…',
} as const;

export function StreamIndicator() {
  const { state } = useMarketStream();
  return (
    <span className={`stream-indicator stream-${state}`} role="status">
      <span className="dot" aria-hidden="true" />
      {LABELS[state]}
    </span>
  );
}
