import { useMemo, useState } from 'react';
import { FindingDto, Severity } from '../types/api';

interface Props {
  findings: FindingDto[];
}

const SEVERITIES: Severity[] = ['ERROR', 'WARNING', 'INFO'];

export function FindingsTable({ findings }: Props) {
  const [enabled, setEnabled] = useState<Set<Severity>>(new Set(SEVERITIES));
  const [query, setQuery] = useState('');

  const counts = useMemo(() => {
    const c: Record<Severity, number> = { ERROR: 0, WARNING: 0, INFO: 0 };
    for (const f of findings) c[f.severity]++;
    return c;
  }, [findings]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return findings.filter((f) => {
      if (!enabled.has(f.severity)) return false;
      if (!q) return true;
      return (
        f.message.toLowerCase().includes(q) ||
        (f.fieldName ?? '').toLowerCase().includes(q) ||
        (f.ruleId ?? '').toLowerCase().includes(q) ||
        (f.instrumentCode ?? '').toLowerCase().includes(q) ||
        (f.portfolioId ?? '').toLowerCase().includes(q)
      );
    });
  }, [findings, enabled, query]);

  const toggle = (s: Severity) => {
    const next = new Set(enabled);
    if (next.has(s)) next.delete(s);
    else next.add(s);
    setEnabled(next);
  };

  return (
    <div className="card">
      <div className="card-header flex flex-wrap items-center justify-between gap-3">
        <span>Findings ({findings.length})</span>
        <div className="flex flex-wrap items-center gap-2">
          {SEVERITIES.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => toggle(s)}
              aria-pressed={enabled.has(s)}
              className={
                'rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ' +
                'focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-navy-500 ' +
                (enabled.has(s)
                  ? severityActiveClass(s)
                  : 'border-slate-300 bg-white text-slate-500 hover:bg-slate-100')
              }
            >
              {s} ({counts[s]})
            </button>
          ))}
          <input
            type="search"
            placeholder="Filter…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="rounded-md border border-slate-300 px-2.5 py-1 text-xs shadow-sm focus:border-navy-500 focus:outline-none focus:ring-1 focus:ring-navy-500"
          />
        </div>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <th className="px-4 py-2 font-medium">Severity</th>
              <th className="px-4 py-2 font-medium">Rule</th>
              <th className="px-4 py-2 font-medium">Field</th>
              <th className="px-4 py-2 font-medium">Position</th>
              <th className="px-4 py-2 font-medium">Message</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((f, idx) => (
              <tr key={idx} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                <td className="px-4 py-2 align-top">
                  <span className={severityBadge(f.severity)}>{f.severity}</span>
                </td>
                <td className="px-4 py-2 align-top font-mono text-xs text-slate-600">{f.ruleId}</td>
                <td className="px-4 py-2 align-top">
                  <div className="font-medium text-slate-800">{f.fieldName ?? '—'}</div>
                  {f.fieldNum && <div className="font-mono text-xs text-slate-500">#{f.fieldNum}</div>}
                </td>
                <td className="px-4 py-2 align-top text-xs text-slate-600">
                  {f.rowIndex != null && <div>Row {f.rowIndex}</div>}
                  {f.instrumentCode && <div className="font-mono">{f.instrumentCode}</div>}
                  {f.instrumentName && <div className="truncate max-w-xs">{f.instrumentName}</div>}
                </td>
                <td className="px-4 py-2 align-top text-slate-700">{f.message}</td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-sm text-slate-500">
                  Keine Findings für die aktuelle Auswahl.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function severityBadge(s: Severity): string {
  switch (s) {
    case 'ERROR':   return 'badge-error';
    case 'WARNING': return 'badge-warning';
    case 'INFO':    return 'badge-info';
  }
}

function severityActiveClass(s: Severity): string {
  switch (s) {
    case 'ERROR':   return 'border-red-300 bg-red-50 text-red-700';
    case 'WARNING': return 'border-amber-300 bg-amber-50 text-amber-700';
    case 'INFO':    return 'border-sky-300 bg-sky-50 text-sky-700';
  }
}
