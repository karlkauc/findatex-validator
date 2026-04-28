interface Props {
  label: string;
  percentage: number;
  prominent?: boolean;
}

export function ScoreBadge({ label, percentage, prominent }: Props) {
  const tone = scoreTone(percentage);

  return (
    <div
      className={
        'rounded-lg border p-4 ' +
        (prominent ? 'border-navy-200 bg-navy-50' : 'border-slate-200 bg-white')
      }
    >
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-2 flex items-baseline gap-2">
        <span className={'text-3xl font-bold tabular-nums ' + tone.text}>{percentage}</span>
        <span className="text-sm text-slate-500">/ 100</span>
      </div>
      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-200">
        <div
          className={'h-full ' + tone.bar}
          style={{ width: `${Math.max(0, Math.min(100, percentage))}%` }}
        />
      </div>
    </div>
  );
}

function scoreTone(pct: number): { text: string; bar: string } {
  if (pct >= 90) return { text: 'text-emerald-700', bar: 'bg-emerald-500' };
  if (pct >= 70) return { text: 'text-amber-700', bar: 'bg-amber-500' };
  return { text: 'text-red-700', bar: 'bg-red-500' };
}
