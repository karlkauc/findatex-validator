import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ResultPanel } from './ResultPanel';
import { AnnotatedSourceDto, ValidationResponse } from '../types/api';

vi.mock('../api/client', () => ({
  fetchAnnotatedSource: vi.fn(),
  reportDownloadUrl: (id: string) => `/api/report/${id}`,
}));
import { fetchAnnotatedSource } from '../api/client';
const fetchMock = vi.mocked(fetchAnnotatedSource);

const result: ValidationResponse = {
  summary: {
    templateId: 'TPT',
    templateVersion: 'V7.0',
    filename: 'x.xlsx',
    rowCount: 2,
    cleanRowCount: 1,
    findingCount: 1,
    errorCount: 1,
    warningCount: 0,
    infoCount: 0,
    generatedAt: '2026-09-03T10:00:00Z',
  },
  scores: [{ dimension: 'OVERALL', value: 0.9, percentage: 90 }],
  perProfileScores: {},
  perFundScores: [],
  findings: [
    {
      severity: 'ERROR',
      ruleId: 'FORMAT/1',
      profileCode: null,
      profileDisplayName: null,
      fieldNum: '2',
      fieldName: 'Field',
      rowIndex: 1,
      value: null,
      message: 'bad',
      portfolioId: null,
      portfolioName: null,
      valuationDate: null,
      instrumentCode: null,
      instrumentName: null,
      valuationWeight: null,
    },
  ],
  reportId: 'abc',
  annotatedSourceAvailable: true,
};

const dto: AnnotatedSourceDto = {
  headerRowIndex: 0,
  headers: ['A', 'B'],
  columnsWithFindings: [2],
  rows: [
    { r: null, c: ['A', 'B'] },
    { r: 1, c: ['x', 'y'] },
  ],
  findingCells: [[0, 1, 2]],
};

function wrap(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe('ResultPanel', () => {
  beforeEach(() => {
    window.localStorage.clear();
    fetchMock.mockReset();
    fetchMock.mockResolvedValue(dto);
  });

  it('starts on the findings tab and does not load the source yet', () => {
    wrap(<ResultPanel result={result} appVersion="web" />);
    expect(screen.getByRole('tab', { name: 'Findings (1)' })).toHaveAttribute('aria-selected', 'true');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('"Source" switches to the annotated tab and highlights the cell', async () => {
    const user = userEvent.setup();
    wrap(<ResultPanel result={result} appVersion="web" />);
    await user.click(screen.getByRole('button', { name: 'Source' }));
    expect(screen.getByRole('tab', { name: 'Annotated Source' })).toHaveAttribute('aria-selected', 'true');
    const grid = await screen.findByTestId('annotated-source-grid');
    expect(grid).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith('abc');
    expect(document.getElementById('src-1-2')).toHaveClass('src-cell-jump');
  });
});
