import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { PricePoint } from '../api/types';
import { formatPrice, formatTime } from '../format';

export function PriceChart({ points }: { points: PricePoint[] }) {
  if (points.length === 0) {
    return <p className="muted chart-empty">Brak notowań.</p>;
  }
  return (
    <div className="chart">
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
          <XAxis
            dataKey="ts"
            tickFormatter={formatTime}
            minTickGap={48}
            stroke="var(--text-muted)"
            tick={{ fontSize: 12 }}
          />
          <YAxis
            domain={['auto', 'auto']}
            tickFormatter={(value: number) => formatPrice(value)}
            width={80}
            stroke="var(--text-muted)"
            tick={{ fontSize: 12 }}
          />
          <Tooltip
            labelFormatter={(label) => formatTime(String(label))}
            formatter={(value) => [formatPrice(Number(value)), 'Cena']}
            contentStyle={{ background: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 8 }}
            labelStyle={{ color: 'var(--text-muted)' }}
          />
          {/* animation off: new ticks arrive every few seconds and would restart it */}
          <Line type="monotone" dataKey="price" stroke="var(--accent)" strokeWidth={2} dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
