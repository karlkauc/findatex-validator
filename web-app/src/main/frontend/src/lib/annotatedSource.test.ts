import { describe, it, expect } from 'vitest';
import {
  buildCellIndex,
  columnLetters,
  columnTitle,
  describeFindings,
  gridSummary,
  worstSeverity,
} from './annotatedSource';
import { AnnotatedSourceDto, FindingDto } from '../types/api';

function f(over: Partial<FindingDto>): FindingDto {
  return {
    severity: 'INFO',
    ruleId: 'R',
    profileCode: null,
    profileDisplayName: null,
    fieldNum: null,
    fieldName: null,
    rowIndex: null,
    value: null,
    message: '',
    portfolioId: null,
    portfolioName: null,
    valuationDate: null,
    instrumentCode: null,
    instrumentName: null,
    valuationWeight: null,
    ...over,
  };
}

describe('columnLetters / columnTitle', () => {
  it('matches spreadsheet labels', () => {
    expect(columnLetters(0)).toBe('A');
    expect(columnLetters(25)).toBe('Z');
    expect(columnLetters(26)).toBe('AA');
    expect(columnLetters(27)).toBe('AB');
    expect(columnLetters(701)).toBe('ZZ');
    expect(columnLetters(702)).toBe('AAA');
  });

  it('prefers the trimmed header and falls back to letters', () => {
    expect(columnTitle(3, '  14_Identification code ')).toBe('14_Identification code');
    expect(columnTitle(3, '')).toBe('D');
    expect(columnTitle(3, null)).toBe('D');
  });

  it('formats the summary like the desktop pane', () => {
    expect(gridSummary(60, 150, 12)).toBe('60 rows × 150 columns, 12 rows with findings');
  });
});

describe('describeFindings', () => {
  it('orders by severity then rule id and truncates long messages', () => {
    const text = describeFindings([
      f({ severity: 'INFO', ruleId: 'B', message: 'info' }),
      f({ severity: 'ERROR', ruleId: 'Z', message: 'err z' }),
      f({ severity: 'ERROR', ruleId: 'A', message: 'x'.repeat(500) }),
      f({ severity: 'WARNING', ruleId: 'C', message: 'warn' }),
    ]);
    const lines = text.split('\n\n');
    expect(lines[0]).toBe('[ERROR] A — ' + 'x'.repeat(400) + '…');
    expect(lines[1]).toBe('[ERROR] Z — err z');
    expect(lines[2]).toBe('[WARNING] C — warn');
    expect(lines[3]).toBe('[INFO] B — info');
  });

  it('caps the whole text at 1500 characters', () => {
    const many = Array.from({ length: 10 }, (_, i) =>
      f({ severity: 'ERROR', ruleId: 'R' + i, message: 'y'.repeat(300) }),
    );
    const text = describeFindings(many);
    expect(text.endsWith('\n…(truncated)')).toBe(true);
    expect(text.length).toBeLessThanOrEqual(1500 + '\n…(truncated)'.length);
  });
});

describe('buildCellIndex', () => {
  const findings = [
    f({ severity: 'WARNING', ruleId: 'W', rowIndex: 1, fieldNum: '5' }),
    f({ severity: 'ERROR', ruleId: 'E', rowIndex: 1, fieldNum: '5' }),
    f({ severity: 'INFO', ruleId: 'I', rowIndex: 2 }),
    f({ severity: 'ERROR', ruleId: 'G' }), // file-level, no cell
  ];
  const dto: AnnotatedSourceDto = {
    headerRowIndex: 0,
    headers: ['a', 'b', 'c', 'd', 'e'],
    columnsWithFindings: [0, 5],
    rows: [
      { r: null, c: ['a', 'b', 'c', 'd', 'e'] },
      { r: 1, c: ['1', '2', '3', '4', '5'] },
      { r: 2, c: ['1', '2', '3', '4', '5'] },
    ],
    findingCells: [
      [0, 1, 5],
      [1, 1, 5],
      [2, 2, 0],
      [99, 2, 0], // out of range → ignored
    ],
  };

  it('derives the worst severity per cell and per row', () => {
    const idx = buildCellIndex(dto, findings);
    expect(idx.byCell.get('1:5')).toEqual([0, 1]);
    expect(idx.cellSeverity.get('1:5')).toBe('ERROR');
    expect(idx.rowSeverity.get(1)).toBe('ERROR');
    expect(idx.rowSeverity.get(2)).toBe('INFO');
    expect(idx.byFinding.get(1)).toEqual([1, 5]);
    expect(idx.byFinding.has(3)).toBe(false);
    expect(idx.byFinding.has(99)).toBe(false);
  });

  it('worstSeverity handles the empty list', () => {
    expect(worstSeverity([])).toBeNull();
    expect(worstSeverity([f({ severity: 'INFO' }), f({ severity: 'WARNING' })])).toBe('WARNING');
  });
});
