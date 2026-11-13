import { describe, expect, it } from 'vitest';
import { AnnotatedSourceDto, FindingDto, Severity } from '../types/api';
import {
  addressByFinding,
  chunkAddresses,
  excelAddress,
  toExcelMarks,
  unplacedFindings,
} from './marks';

function finding(severity: Severity, ruleId: string): FindingDto {
  return {
    severity, ruleId, profileCode: null, profileDisplayName: null, fieldNum: null,
    fieldName: null, rowIndex: null, value: null, message: ruleId, portfolioId: null,
    portfolioName: null, valuationDate: null, instrumentCode: null, instrumentName: null,
    valuationWeight: null,
  };
}

function dto(findingCells: [number, number, number][]): AnnotatedSourceDto {
  // Header in Excel row 3 (mirror row 2): two preamble rows above it.
  return { headerRowIndex: 2, headers: [], columnsWithFindings: [], rows: [], findingCells };
}

describe('excelAddress', () => {
  it('maps mirror coordinates 1:1 onto the sheet', () => {
    expect(excelAddress(3, 2)).toBe('B4'); // first data row under a header in row 3
    expect(excelAddress(0, 1)).toBe('A1');
    expect(excelAddress(9, 27)).toBe('AA10');
    expect(excelAddress(4, 703)).toBe('AAA5');
  });

  it('puts row-level findings (mirror column 0) on column A', () => {
    expect(excelAddress(5, 0)).toBe('A6');
  });
});

describe('toExcelMarks', () => {
  it('groups findings per cell and keeps the worst severity', () => {
    const findings = [finding('INFO', 'a'), finding('ERROR', 'b'), finding('WARNING', 'c')];
    const marks = toExcelMarks(dto([[0, 3, 2], [1, 3, 2], [2, 4, 30]]), findings);
    expect(marks).toEqual([
      { address: 'B4', severity: 'ERROR', findingIndices: [0, 1] },
      { address: 'AD5', severity: 'WARNING', findingIndices: [2] },
    ]);
  });

  it('merges a row-level finding with a column-A finding of the same row', () => {
    const findings = [finding('WARNING', 'row'), finding('INFO', 'cell')];
    const marks = toExcelMarks(dto([[0, 6, 0], [1, 6, 1]]), findings);
    expect(marks).toEqual([{ address: 'A7', severity: 'WARNING', findingIndices: [0, 1] }]);
  });

  it('leaves findings without a cell unmarked and lists them', () => {
    const findings = [finding('ERROR', 'placed'), finding('ERROR', 'portfolio')];
    const marks = toExcelMarks(dto([[0, 3, 5]]), findings);
    const placed = addressByFinding(marks);
    expect(placed.get(0)).toBe('E4');
    expect(unplacedFindings(findings, placed)).toEqual([1]);
  });

  it('ignores out-of-range finding indices', () => {
    expect(toExcelMarks(dto([[7, 3, 2]]), [finding('ERROR', 'x')])).toEqual([]);
  });
});

describe('chunkAddresses', () => {
  it('splits into comma-joined chunks below the limit', () => {
    const chunks = chunkAddresses(['A1', 'B2', 'C3', 'D4'], 6);
    expect(chunks).toEqual(['A1,B2', 'C3,D4']);
    expect(chunkAddresses([])).toEqual([]);
  });
});
