import { useEffect, useMemo, useRef, useState } from 'react';
import { FindingDto, Severity } from '../types/api';

interface Props {
  findings: FindingDto[];
}

const SEVERITIES: Severity[] = ['ERROR', 'WARNING', 'INFO'];

const COLUMN_COUNT = 13;

export function FindingsTable({ findings }: Props) {
  const [enabled, setEnabled] = useState<Set<Severity>>(new Set(SEVERITIES));
  const [query, setQuery] = useState('');

  // Double-scrollbar pattern: a thin scroll strip above the table mirrors the
  // table's natural width, so users at the top of the page can see (and use)
  // a horizontal scrollbar without first scrolling to the bottom of the table.
  const topScrollRef = useRef<HTMLDivElement>(null);
  const tableScrollRef = useRef<HTMLDivElement>(null);
  const [innerWidth, setInnerWidth] = useState(0);
  const syncing = useRef(false);

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
        (f.fieldNum ?? '').toLowerCase().includes(q) ||
        (f.ruleId ?? '').toLowerCase().includes(q) ||
        (f.profileDisplayName ?? '').toLowerCase().includes(q) ||
        (f.profileCode ?? '').toLowerCase().includes(q) ||
        (f.portfolioId ?? '').toLowerCase().includes(q) ||
        (f.portfolioName ?? '').toLowerCase().includes(q) ||
        (f.valuationDate ?? '').toLowerCase().includes(q) ||
        (f.instrumentCode ?? '').toLowerCase().includes(q) ||
        (f.instrumentName ?? '').toLowerCase().includes(q)
      );
    });
  }, [findings, enabled, query]);

  const toggle = (s: Severity) => {
    const next = new Set(enabled);
    if (next.has(s)) next.delete(s);
    else next.add(s);
    setEnabled(next);
  };

  useEffect(() => {
    const el = tableScrollRef.current;
    if (!el) return;
    const update = () => setInnerWidth(el.scrollWidth);
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    // Children width changes (filter, severity toggle) also affect scrollWidth.
    if (el.firstElementChild) ro.observe(el.firstElementChild);
    return () => ro.disconnect();
  }, [filtered.length]);

  const onTopScroll = () => {
    if (syncing.current) return;
    syncing.current = true;
    if (tableScrollRef.current && topScrollRef.current) {
      tableScrollRef.current.scrollLeft = topScrollRef.current.scrollLeft;
    }
    syncing.current = false;
  };
  const onTableScroll = () => {
    if (syncing.current) return;
    syncing.current = true;
    if (tableScrollRef.current && topScrollRef.current) {
      topScrollRef.current.scrollLeft = tableScrollRef.current.scrollLeft;
    }
    syncing.current = false;
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
      <div
        ref={topScrollRef}
        onScroll={onTopScroll}
        aria-hidden="true"
        className="overflow-x-auto"
        style={{ height: 14 }}
      >
        <div style={{ width: innerWidth, height: 1 }} />
      </div>
      <div ref={tableScrollRef} onScroll={onTableScroll} className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <th className="whitespace-nowrap px-3 py-2 font-medium">Severity</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Profile</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Fund ID</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Fund name</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Valuation date</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Row</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Instrument code</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Instrument</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Weight</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Rule</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Field</th>
              <th className="whitespace-nowrap px-3 py-2 font-medium">Field name</th>
              <th className="px-3 py-2 font-medium">Message</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((f, idx) => (
              <tr key={idx} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                <td className="whitespace-nowrap px-3 py-2 align-top">
                  <span className={severityBadge(f.severity)}>{f.severity}</span>
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-slate-700">
                  {f.profileDisplayName ?? f.profileCode ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top font-mono text-xs text-slate-600">
                  {f.portfolioId ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-slate-700">
                  {f.portfolioName ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-xs text-slate-600">
                  {f.valuationDate ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-right text-xs text-slate-600">
                  {f.rowIndex != null ? f.rowIndex : ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top font-mono text-xs text-slate-600">
                  {f.instrumentCode ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-slate-700">
                  {f.instrumentName ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-right text-xs text-slate-600">
                  {formatWeight(f.valuationWeight)}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top font-mono text-xs text-slate-600">{f.ruleId}</td>
                <td className="whitespace-nowrap px-3 py-2 align-top font-mono text-xs text-slate-500">
                  {f.fieldNum ?? ''}
                </td>
                <td className="whitespace-nowrap px-3 py-2 align-top text-slate-700">{f.fieldName ?? ''}</td>
                <td className="whitespace-normal break-words px-3 py-2 align-top text-slate-700">{f.message}</td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={COLUMN_COUNT} className="px-4 py-6 text-center text-sm text-slate-500">
                  No findings for the current selection.
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

// Mirrors FindingRow.formatWeight in javafx-app: "0.1234" → "12.34 %".
function formatWeight(raw: string | null): string {
  if (raw == null || raw.trim() === '') return '';
  const d = Number.parseFloat(raw.replace(',', '.'));
  if (!Number.isFinite(d)) return raw;
  return `${(d * 100).toFixed(2)} %`;
}
