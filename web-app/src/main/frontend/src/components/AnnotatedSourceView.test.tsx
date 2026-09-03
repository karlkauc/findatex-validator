import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { AnnotatedSourceView, PAGE_SIZE, cellDomId } from './AnnotatedSourceView';
import { AnnotatedSourceDto, ApiError, FindingDto } from '../types/api';

vi.mock('../api/client', () => ({
  fetchAnnotatedSource: vi.fn(),
}));
import { fetchAnnotatedSource } from '../api/client';
const fetchMock = vi.mocked(fetchAnnotatedSource);

function wrap(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

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

const findings: FindingDto[] = [
  f({ severity: 'ERROR', ruleId: 'FORMAT/1', rowIndex: 1, fieldNum: '2', message: 'bad isin' }),
  f({ severity: 'WARNING', ruleId: 'XF-1', rowIndex: 3, message: 'row-level warning' }),
  f({ severity: 'ERROR', ruleId: 'GLOBAL', message: 'file-level' }),
];

function dto(rowCount = 4): AnnotatedSourceDto {
  const rows: AnnotatedSourceDto['rows'] = [{ r: null, c: ['H1', 'H2', ''] }];
  for (let i = 1; i <= rowCount; i++) rows.push({ r: i, c: [`a${i}`, `b${i}`, `c${i}`] });
  return {
    headerRowIndex: 0,
    headers: ['H1', 'H2', ''],
    columnsWithFindings: [0, 2],
    rows,
    findingCells: [
      [0, 1, 2],
      [1, 3, 0],
    ],
  };
}

describe('AnnotatedSourceView', () => {
  beforeEach(() => {
    fetchMock.mockReset();
  });

  it('does not fetch until the tab is active', () => {
    wrap(<AnnotatedSourceView reportId="id" available findings={findings} active={false} jump={null} />);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the Excel hint when the artefact is unavailable', () => {
    wrap(<AnnotatedSourceView reportId="id" available={false} findings={findings} active jump={null} />);
    expect(screen.getByText(/too large for the browser/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows only rows with findings by default, with tinted cells and tooltips', async () => {
    fetchMock.mockResolvedValue(dto());
    wrap(<AnnotatedSourceView reportId="id" available findings={findings} active jump={null} />);
    const grid = await screen.findByTestId('annotated-source-grid');
    // rows 1 and 3 have findings; 2 and 4 are hidden
    expect(within(grid).getByText('a1')).toBeInTheDocument();
    expect(within(grid).getByText('a3')).toBeInTheDocument();
    expect(within(grid).queryByText('a2')).toBeNull();
    expect(screen.getByText(/rows 1–2 of 2/)).toBeInTheDocument();
    expect(screen.getByText('4 rows × 3 columns, 2 rows with findings')).toBeInTheDocument();

    const bad = document.getElementById(cellDomId(1, 2))!;
    expect(bad).toHaveClass('src-cell-error');
    expect(bad).toHaveAttribute('title', '[ERROR] FORMAT/1 — bad isin');
    const rowCell = document.getElementById(cellDomId(3, 0))!;
    expect(rowCell).toHaveClass('src-cell-warn');
    expect(rowCell).toHaveAttribute('title', '[WARNING] XF-1 — row-level warning');
    // blank header falls back to the spreadsheet letter
    expect(within(grid).getByText('C')).toBeInTheDocument();
  });

  it('shows all rows and pages when the row filter is off', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(dto(PAGE_SIZE + 5));
    wrap(<AnnotatedSourceView reportId="id" available findings={findings} active jump={null} />);
    await screen.findByTestId('annotated-source-grid');
    await user.click(screen.getByRole('checkbox', { name: 'Only rows with findings' }));
    expect(screen.getByText(`rows 1–${PAGE_SIZE} of ${PAGE_SIZE + 5}`)).toBeInTheDocument();
    expect(screen.getByText('page 1 / 2')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText(`rows ${PAGE_SIZE + 1}–${PAGE_SIZE + 5} of ${PAGE_SIZE + 5}`)).toBeInTheDocument();
    expect(screen.getByText(`a${PAGE_SIZE + 5}`)).toBeInTheDocument();
  });

  it('hides clean columns with the column filter', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(dto());
    wrap(<AnnotatedSourceView reportId="id" available findings={findings} active jump={null} />);
    const grid = await screen.findByTestId('annotated-source-grid');
    expect(within(grid).getByText('H1')).toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Only columns with findings' }));
    expect(within(grid).queryByText('H1')).toBeNull();
    expect(within(grid).getByText('H2')).toBeInTheDocument();
  });

  it('jumps to the finding cell on the right page and highlights it', async () => {
    fetchMock.mockResolvedValue(dto());
    const { rerender } = wrap(
      <AnnotatedSourceView reportId="id" available findings={findings} active jump={null} />,
    );
    await screen.findByTestId('annotated-source-grid');
    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <AnnotatedSourceView
          reportId="id"
          available
          findings={findings}
          active
          jump={{ findingIndex: 0, nonce: 1 }}
        />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(document.getElementById(cellDomId(1, 2))).toHaveClass('src-cell-jump'));
  });

  it('explains when a finding has no cell', async () => {
    fetchMock.mockResolvedValue(dto());
    wrap(
      <AnnotatedSourceView
        reportId="id"
        available
        findings={findings}
        active
        jump={{ findingIndex: 2, nonce: 1 }}
      />,
    );
    expect(await screen.findByText(/not tied to a single cell/)).toBeInTheDocument();
  });

  it('shows the TTL hint on 404', async () => {
    fetchMock.mockRejectedValue(new ApiError(404, 'gone'));
    wrap(<AnnotatedSourceView reportId="id" available findings={findings} active jump={null} />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/kept for 5 minutes/);
  });
});
