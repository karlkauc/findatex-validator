import { AnnotatedSourceDto, FindingDto, Severity } from '../types/api';
import { buildCellIndex, columnLetters, worstSeverity } from '../lib/annotatedSource';

/** One cell of the first worksheet to highlight, with the findings that hit it. */
export interface ExcelMark {
  /** A1-style address on the first worksheet, e.g. "B4". */
  address: string;
  severity: Severity;
  /** Indices into ValidationResponse.findings, report order. */
  findingIndices: number[];
}

/**
 * Excel address of a mirror cell. The annotated-source grid mirrors the first
 * sheet 1:1 — mirror row k is Excel row k+1, and mirror column c (c >= 1) is
 * the 1-based Excel column c. Mirror column 0 is the synthetic "Row" column
 * for row-level findings; those land on column A of that row.
 */
export function excelAddress(mirrorRow: number, mirrorCol: number): string {
  const col = mirrorCol <= 0 ? 0 : mirrorCol - 1;
  return `${columnLetters(col)}${mirrorRow + 1}`;
}

/**
 * Turns the server's finding→cell join into one mark per Excel cell. Row-level
 * findings and cell findings in column A of the same row share one mark.
 * Findings with no cell (portfolio/file level) are not marked — see
 * {@link unplacedFindings}.
 */
export function toExcelMarks(dto: AnnotatedSourceDto, findings: FindingDto[]): ExcelMark[] {
  const index = buildCellIndex(dto, findings);
  const byAddress = new Map<string, number[]>();
  for (const [fi, [r, c]] of index.byFinding) {
    const addr = excelAddress(r, c);
    const list = byAddress.get(addr);
    if (list) list.push(fi);
    else byAddress.set(addr, [fi]);
  }
  const marks: ExcelMark[] = [];
  for (const [address, idxs] of byAddress) {
    idxs.sort((a, b) => a - b);
    const severity = worstSeverity(idxs.map((i) => findings[i]));
    if (severity) marks.push({ address, severity, findingIndices: idxs });
  }
  return marks;
}

/** Finding index → Excel address, for the "jump to cell" action of the list. */
export function addressByFinding(marks: ExcelMark[]): Map<number, string> {
  const out = new Map<number, string>();
  for (const m of marks) for (const fi of m.findingIndices) out.set(fi, m.address);
  return out;
}

/** Indices of findings that have no cell on the sheet (portfolio/file level). */
export function unplacedFindings(findings: FindingDto[], placed: Map<number, string>): number[] {
  const out: number[] = [];
  for (let i = 0; i < findings.length; i++) if (!placed.has(i)) out.push(i);
  return out;
}

/** Splits addresses into comma-joined chunks for Worksheet.getRanges (address strings are length-limited). */
export function chunkAddresses(addresses: string[], maxChars = 2000): string[] {
  const chunks: string[] = [];
  let cur = '';
  for (const a of addresses) {
    if (cur && cur.length + 1 + a.length > maxChars) {
      chunks.push(cur);
      cur = '';
    }
    cur = cur ? `${cur},${a}` : a;
  }
  if (cur) chunks.push(cur);
  return chunks;
}

export const SEVERITY_FILL: Record<Severity, string> = {
  ERROR: '#F8B4B4',
  WARNING: '#FDE68A',
  INFO: '#BFDBFE',
};
