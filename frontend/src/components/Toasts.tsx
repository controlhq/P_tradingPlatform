import { useCallback, useEffect, useRef, useState } from 'react';
import { formatPrice } from '../format';
import { useMarketStream } from '../stream/useMarketStream';

interface Toast {
  id: number;
  kind: 'success' | 'error';
  text: string;
}

const VISIBLE_MS = 6000;

/** Short notices when matching fills or rejects one of the user's pending orders, on any page. */
export function OrderToasts() {
  const { subscribeOrders } = useMarketStream();
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id: number) => setToasts((current) => current.filter((t) => t.id !== id)), []);

  useEffect(
    () =>
      subscribeOrders((event) => {
        const id = nextId.current++;
        const toast: Toast =
          event.status === 'FILLED'
            ? { id, kind: 'success', text: `Zlecenie #${event.orderId} wykonane po ${formatPrice(event.filledPrice)}.` }
            : { id, kind: 'error', text: `Zlecenie #${event.orderId} odrzucone: za mało środków przy realizacji.` };
        setToasts((current) => [...current, toast]);
        window.setTimeout(() => dismiss(id), VISIBLE_MS);
      }),
    [dismiss, subscribeOrders],
  );

  return (
    <div className="toasts" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast toast-${toast.kind}`}>
          <span>{toast.text}</span>
          <button type="button" className="toast-close" aria-label="Zamknij" onClick={() => dismiss(toast.id)}>
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
