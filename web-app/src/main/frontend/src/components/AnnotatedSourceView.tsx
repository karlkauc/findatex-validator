import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2 } from 'lucide-react';
import { fetchAnnotatedSource } from '../api/client';
import { ApiError, FindingDto } from '../types/api';
import {
  buildCellIndex,
  cellKey,
  columnTitle,
  describeFindings,
  gridSummary,
  severityCellClass,
} from '../lib/annotatedSource';

export const PAGE_SIZE = 200;

export interface JumpTarget {
  /** Index into the findings array of the validation response. */
  findingIndex: number;
  /** Changes on every request so the same finding can be jumped to twice. */
  nonce: number;
}

interface Props {
  reportId: string;
  available: boolean;
  findings: FindingDto[];
  /** The tab is visible; data is fetched lazily on the first activation. */
  active: boolean;
  jump: JumpTarget | null;
}

export function cellDomId(mirrorRow: number, mirrorCol: number): string {
  return `src-${mirrorRow}-${mirrorCol}`;
}

/**
 * The original file as a grid with every finding painted on its cell — the
 * web twin of the desktop "Annotated Source" tab and the Excel sheet. There is
 * no virtualisation, so the defaults keep the DOM small: only rows with
 * findings, 200 rows per page.
 */
export function AnnotatedSourceView({ reportId, available, findings, active, jump }: Props) {
  const query = useQuery({
    queryKey: ['annotated-source', reportId],
    queryFn: () => fetchAnnotatedSource(reportId),
    enabled: available && active,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
  });

  const [onlyRowsWithFindings, setOnlyRowsWithFindings] = useState(true);
  const [onlyColumnsWithFindings, setOnlyColumnsWithFindings] = useState(false);
  const [page, setPage] = useState(0);
  const [highlight, setHighlight] = useState<{ r: number; c: number } | null>(null);
  const [jumpNote, setJumpNote] = useState<string | null>(null);

  const dto = query.data;
  const index = useMemo(() => (dto ? buildCellIndex(dto, findings) : null), [dto, findings]);

  // Body rows: everything except the header row (shown as column titles).
  const bodyRows = useMemo(() => {
    if (!dto) return [];
    const rows: number[] = [];
    for (let k = 0; k < dto.rows.length; k++) if (k !== dto.headerRowIndex) rows.push(k);
    return rows;
  }, [dto]);

  const visibleRows = useMemo(() => {
    if (!index) return bodyRows;
    return onlyRowsWithFindings ? bodyRows.filter((k) => index.rowSeverity.has(k)) : bodyRows;
  }, [bodyRows, index, onlyRowsWithFindings]);

  const visibleCols = useMemo(() => {
    if (!dto) return [];
    const all = dto.headers.map((_, c) => c);
    if (!onlyColumnsWithFindings) return all;
    const withFindings = new Set(dto.columnsWithFindings);
    return all.filter((c) => withFindings.has(c + 1));
  }, [dto, onlyColumnsWithFindings]);

  const pageCount = Math.max(1, Math.ceil(visibleRows.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount - 1);
  const pageRows = visibleRows.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  // Jump: find the finding's cell, make sure the row is on the current page,
  // highlight it and bring it into view.
  useEffect(() => {
    if (!jump || !dto || !index) return;
    const target = index.byFinding.get(jump.findingIndex);
    if (!target) {
      setJumpNote('This finding is not tied to a single cell (file-level finding).');
      setHighlight(null);
      return;
    }
    setJumpNote(null);
    const [r, c] = target;
    // Rows with findings are always visible under the default filter; a hidden
    // column can only happen with the column filter, and such a column has
    // findings by definition, so it is visible too.
    const pos = visibleRows.indexOf(r);
    if (pos >= 0) setPage(Math.floor(pos / PAGE_SIZE));
    setHighlight({ r, c });
    // Let the page change render before scrolling.
    const handle = window.setTimeout(() => {
      document.getElementById(cellDomId(r, c))?.scrollIntoView?.({ block: 'center', inline: 'center' });
    }, 0);
    return () => window.clearTimeout(handle);
    // visibleRows is derived from dto + filter; re-running on filter change is fine.
  }, [jump, dto, index, visibleRows]);

  if (!available) {
    return (
      <Notice>
        The annotated view is not available for this file (too large for the browser). The
        <em> Annotated Source</em> sheet of the Excel report has the same content.
      </Notice>
    );
  }
  if (!active && !dto) {
    return null;
  }
  if (query.isPending) {
    return (
      <Notice>
        <Loader2 className="mr-2 inline h-4 w-4 animate-spin" aria-hidden="true" />
        Loading the annotated source…
      </Notice>
    );
  }
  if (query.isError) {
    const err = query.error;
    const expired = err instanceof ApiError && err.status === 404;
    return (
      <Notice role="alert">
        {expired
          ? 'The annotated source has expired — it is kept for 5 minutes, like the Excel report. Validate the file again to see it.'
          : `Could not load the annotated source: ${err instanceof Error ? err.message : String(err)}`}
      </Notice>
    );
  }
  if (!dto || !index) return null;
  if (bodyRows.length === 0) {
    return <Notice>The original file has no data rows.</Notice>;
  }

  const firstRow = safePage * PAGE_SIZE + 1;
  const lastRow = Math.min(visibleRows.length, (safePage + 1) * PAGE_SIZE);

  return (
    <div className="card">
      <div className="card-header flex flex-wrap items-center justify-between gap-3">
        <span>Annotated Source</span>
        <div className="flex flex-wrap items-center gap-4 normal-case tracking-normal">
          <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
            <input
              type="checkbox"
              checked={onlyRowsWithFindings}
              onChange={(e) => {
                setOnlyRowsWithFindings(e.target.checked);
                setPage(0);
              }}
              className="h-3.5 w-3.5 rounded border-slate-300 text-navy-700 focus:ring-navy-500"
            />
            Only rows with findings
          </label>
          <label className="inline-flex items-center gap-2 text-xs font-medium text-slate-600">
            <input
              type="checkbox"
              checked={onlyColumnsWithFindings}
              onChange={(e) => setOnlyColumnsWithFindings(e.target.checked)}
              className="h-3.5 w-3.5 rounded border-slate-300 text-navy-700 focus:ring-navy-500"
            />
            Only columns with findings
          </label>
          <Legend />
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-slate-50 px-5 py-2 text-xs text-slate-600">
        <span>
          {gridSummary(bodyRows.length, dto.headers.length, index.rowSeverity.size)}
          {jumpNote && <span className="ml-3 text-amber-700">{jumpNote}</span>}
        </span>
        <span className="inline-flex items-center gap-2">
          <span>
            {visibleRows.length === 0 ? 'no rows' : `rows ${firstRow}–${lastRow} of ${visibleRows.length}`}
          </span>
          {pageCount > 1 && (
            <>
              <button
                type="button"
                className="btn-secondary px-2 py-0.5 text-xs"
                disabled={safePage === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Prev
              </button>
              <span aria-live="polite">
                page {safePage + 1} / {pageCount}
              </span>
              <button
                type="button"
                className="btn-secondary px-2 py-0.5 text-xs"
                disabled={safePage >= pageCount - 1}
                onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
              >
                Next
              </button>
            </>
          )}
        </span>
      </div>
      <div className="max-h-[70vh] overflow-auto">
        <table className="border-collapse text-xs" data-testid="annotated-source-grid">
          <thead className="sticky top-0 z-20 bg-slate-50">
            <tr className="text-left uppercase tracking-wide text-slate-500">
              <th className="sticky left-0 z-30 whitespace-nowrap border-b border-r border-slate-200 bg-slate-100 px-2 py-1.5 font-medium">
                Row
              </th>
              {visibleCols.map((c) => (
                <th
                  key={c}
                  className="max-w-[16rem] truncate whitespace-nowrap border-b border-slate-200 px-2 py-1.5 font-medium"
                  title={columnTitle(c, dto.headers[c])}
                >
                  {columnTitle(c, dto.headers[c])}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pageRows.map((k) => {
              const row = dto.rows[k];
              const rowSev = index.rowSeverity.get(k);
              const rowFindings = index.byCell.get(cellKey(k, 0));
              return (
                <tr key={k} className="border-b border-slate-100 last:border-0">
                  <RowCell
                    id={cellDomId(k, 0)}
                    text={row.r != null ? String(row.r) : ''}
                    className={
                      'sticky left-0 z-10 whitespace-nowrap border-r border-slate-200 px-2 py-1 text-right font-mono text-slate-600 ' +
                      (severityCellClass(rowSev) || 'bg-slate-50') +
                      (highlight && highlight.r === k && highlight.c === 0 ? ' src-cell-jump' : '')
                    }
                    title={rowFindings ? describeFindings(rowFindings.map((i) => findings[i])) : undefined}
                  />
                  {visibleCols.map((c) => {
                    const mirrorCol = c + 1;
                    const key = cellKey(k, mirrorCol);
                    const idxs = index.byCell.get(key);
                    const sev = index.cellSeverity.get(key);
                    const jumped = highlight && highlight.r === k && highlight.c === mirrorCol;
                    return (
                      <td
                        key={c}
                        id={cellDomId(k, mirrorCol)}
                        className={
                          'max-w-[16rem] truncate whitespace-nowrap px-2 py-1 text-slate-700 ' +
                          severityCellClass(sev) +
                          (jumped ? ' src-cell-jump' : '')
                        }
                        title={idxs ? describeFindings(idxs.map((i) => findings[i])) : undefined}
                        data-severity={sev ?? undefined}
                      >
                        {row.c[c] ?? ''}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
            {pageRows.length === 0 && (
              <tr>
                <td colSpan={visibleCols.length + 1} className="px-4 py-6 text-center text-slate-500">
                  No rows match the current filter.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function RowCell({
  id,
  text,
  className,
  title,
}: {
  id: string;
  text: string;
  className: string;
  title?: string;
}) {
  return (
    <th scope="row" id={id} className={className} title={title}>
      {text}
    </th>
  );
}

function Legend() {
  return (
    <span className="inline-flex items-center gap-2 text-xs text-slate-500" aria-label="Legend">
      <Swatch cls="src-cell-error" label="Error" />
      <Swatch cls="src-cell-warn" label="Warning" />
      <Swatch cls="src-cell-info" label="Info" />
    </span>
  );
}

function Swatch({ cls, label }: { cls: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className={'inline-block h-3 w-3 rounded-sm border border-slate-300 ' + cls} aria-hidden="true" />
      {label}
    </span>
  );
}

function Notice({ children, role }: { children: React.ReactNode; role?: string }) {
  return (
    <div className="card" role={role}>
      <div className="card-body text-sm text-slate-600">{children}</div>
    </div>
  );
}
