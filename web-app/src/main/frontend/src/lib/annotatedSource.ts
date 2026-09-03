import { AnnotatedSourceDto, FindingDto, Severity } from '../types/api';

// Ports of core's AnnotatedSourceModel.describe / javafx AnnotatedSourceColumns,
// so the web grid reads exactly like the desktop tab and the Excel comments.

export const MAX_FINDING_MSG = 400;
export const MAX_COMMENT_TEXT = 1500;

const SEVERITY_ORDER: Record<Severity, number> = { ERROR: 0, WARNING: 1, INFO: 2 };

/** 0 → A, 25 → Z, 26 → AA … like a spreadsheet column label. */
export function columnLetters(index: number): string {
  let s = '';
  let n = index;
  do {
    s = String.fromCharCode(65 + (n % 26)) + s;
    n = Math.floor(n / 26) - 1;
  } while (n >= 0);
  return s;
}

/** Header text when present, otherwise the spreadsheet letter for the 0-based source column. */
export function columnTitle(sourceCol: number, header: string | null | undefined): string {
  if (header && header.trim() !== '') return header.trim();
  return columnLetters(sourceCol);
}

export function gridSummary(rows: number, columns: number, rowsWithFindings: number): string {
  return `${rows} rows × ${columns} columns, ${rowsWithFindings} rows with findings`;
}

export function worstSeverity(findings: FindingDto[]): Severity | null {
  let worst: Severity | null = null;
  for (const f of findings) {
    if (f.severity === 'ERROR') return 'ERROR';
    if (f.severity === 'WARNING') worst = 'WARNING';
    else if (worst === null) worst = 'INFO';
  }
  return worst;
}

/**
 * Human-readable list of findings for one cell: ERROR → WARNING → INFO, then by
 * rule id, each as `[SEVERITY] ruleId — message`. Messages are capped at 400
 * characters and the whole text at 1500 — identical to the Excel cell comment.
 */
export function describeFindings(findings: FindingDto[]): string {
  const sorted = [...findings].sort(
    (x, y) =>
      SEVERITY_ORDER[x.severity] - SEVERITY_ORDER[y.severity] ||
      String(x.ruleId).localeCompare(String(y.ruleId)),
  );
  let out = '';
  for (let i = 0; i < sorted.length; i++) {
    const f = sorted[i];
    if (i > 0) out += '\n\n';
    let msg = f.message ?? '';
    if (msg.length > MAX_FINDING_MSG) msg = msg.slice(0, MAX_FINDING_MSG) + '…';
    out += `[${f.severity}] `;
    if (f.ruleId != null) out += `${f.ruleId} — `;
    out += msg;
    if (out.length > MAX_COMMENT_TEXT) {
      out = out.slice(0, MAX_COMMENT_TEXT) + '\n…(truncated)';
      break;
    }
  }
  return out;
}

export function cellKey(mirrorRow: number, mirrorCol: number): string {
  return `${mirrorRow}:${mirrorCol}`;
}

export interface CellIndex {
  /** "row:col" (mirror coordinates) → indices into the findings array. */
  byCell: Map<string, number[]>;
  /** mirror row → worst severity over all cells of that row (incl. row-level). */
  rowSeverity: Map<number, Severity>;
  /** "row:col" → worst severity of that cell. */
  cellSeverity: Map<string, Severity>;
  /** finding index → [mirrorRow, mirrorCol] for findings that landed on a cell. */
  byFinding: Map<number, [number, number]>;
}

/** Joins the server's finding→cell triples with the findings the client already has. */
export function buildCellIndex(dto: AnnotatedSourceDto, findings: FindingDto[]): CellIndex {
  const byCell = new Map<string, number[]>();
  const byFinding = new Map<number, [number, number]>();
  for (const [fi, r, c] of dto.findingCells) {
    if (fi < 0 || fi >= findings.length) continue;
    const k = cellKey(r, c);
    const list = byCell.get(k);
    if (list) list.push(fi);
    else byCell.set(k, [fi]);
    byFinding.set(fi, [r, c]);
  }
  const rowSeverity = new Map<number, Severity>();
  const cellSeverity = new Map<string, Severity>();
  for (const [k, idxs] of byCell) {
    const sev = worstSeverity(idxs.map((i) => findings[i]));
    if (!sev) continue;
    cellSeverity.set(k, sev);
    const row = Number(k.slice(0, k.indexOf(':')));
    const prev = rowSeverity.get(row);
    if (!prev || SEVERITY_ORDER[sev] < SEVERITY_ORDER[prev]) rowSeverity.set(row, sev);
  }
  return { byCell, rowSeverity, cellSeverity, byFinding };
}

export function severityCellClass(sev: Severity | undefined): string {
  switch (sev) {
    case 'ERROR':   return 'src-cell-error';
    case 'WARNING': return 'src-cell-warn';
    case 'INFO':    return 'src-cell-info';
    default:        return '';
  }
}
