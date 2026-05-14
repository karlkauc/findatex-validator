import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FindingsTable } from './FindingsTable';
import { FindingDto } from '../types/api';

function f(over: Partial<FindingDto>): FindingDto {
  return {
    severity: 'INFO',
    ruleId: 'TEST/00',
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

describe('FindingsTable', () => {
  const fixtures: FindingDto[] = [
    f({ severity: 'ERROR',   ruleId: 'PRESENCE/5',  fieldName: 'TotalNetAssets', message: 'Mandatory field missing' }),
    f({ severity: 'WARNING', ruleId: 'XF-12',        fieldName: 'CouponFrequency', message: 'Frequency unusual' }),
    f({ severity: 'INFO',    ruleId: 'INFO/01',     fieldName: 'Note',             message: 'Heads up: legacy format' }),
    f({ severity: 'ERROR',   ruleId: 'FORMAT/14',   fieldName: 'ISIN',            message: 'Invalid ISIN checksum', instrumentCode: 'DE000ABC1234' }),
  ];

  it('renders a row for each finding by default', () => {
    render(<FindingsTable findings={fixtures} />);
    expect(screen.getByText(/Findings \(4\)/)).toBeInTheDocument();
    // Sanity-check: each ruleId appears.
    for (const fx of fixtures) expect(screen.getByText(fx.ruleId)).toBeInTheDocument();
  });

  it('shows per-severity counts in the toggle buttons', () => {
    render(<FindingsTable findings={fixtures} />);
    expect(screen.getByRole('button', { name: 'ERROR (2)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'WARNING (1)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'INFO (1)' })).toBeInTheDocument();
  });

  it('hides ERROR rows when the ERROR toggle is clicked off', async () => {
    const user = userEvent.setup();
    render(<FindingsTable findings={fixtures} />);

    await user.click(screen.getByRole('button', { name: 'ERROR (2)' }));

    expect(screen.queryByText('PRESENCE/5')).not.toBeInTheDocument();
    expect(screen.queryByText('FORMAT/14')).not.toBeInTheDocument();
    // Non-ERROR rows still visible:
    expect(screen.getByText('XF-12')).toBeInTheDocument();
    expect(screen.getByText('INFO/01')).toBeInTheDocument();
  });

  it('filters by free-text search across message, fieldName, ruleId, instrumentCode', async () => {
    const user = userEvent.setup();
    render(<FindingsTable findings={fixtures} />);

    const search = screen.getByPlaceholderText('Filter…');
    await user.type(search, 'isin');

    // Both 'FORMAT/14' (ISIN field) and 'PRESENCE/5' fail the filter — only ISIN matches.
    expect(screen.getByText('FORMAT/14')).toBeInTheDocument();
    expect(screen.queryByText('PRESENCE/5')).not.toBeInTheDocument();
    expect(screen.queryByText('XF-12')).not.toBeInTheDocument();
    expect(screen.queryByText('INFO/01')).not.toBeInTheDocument();
  });

  it('shows the empty-state hint when all severities are toggled off', async () => {
    const user = userEvent.setup();
    render(<FindingsTable findings={fixtures} />);
    await user.click(screen.getByRole('button', { name: 'ERROR (2)' }));
    await user.click(screen.getByRole('button', { name: 'WARNING (1)' }));
    await user.click(screen.getByRole('button', { name: 'INFO (1)' }));
    expect(screen.getByText(/No findings for the current selection/)).toBeInTheDocument();
  });

  it('renders an empty list as zero rows but still mounts without throwing', () => {
    render(<FindingsTable findings={[]} />);
    expect(screen.getByText('Findings (0)')).toBeInTheDocument();
    expect(screen.getByText(/No findings/)).toBeInTheDocument();
  });

  it('shows row index + instrument code when present', () => {
    render(<FindingsTable findings={[fixtures[3]]} />);
    expect(screen.getByText('DE000ABC1234')).toBeInTheDocument();
  });

  // Re-toggling a severity that was disabled brings the rows back.
  it('re-enables a severity after a second toggle click', async () => {
    const user = userEvent.setup();
    render(<FindingsTable findings={fixtures} />);
    const errorBtn = () => screen.getByRole('button', { name: 'ERROR (2)' });
    await user.click(errorBtn());
    expect(screen.queryByText('PRESENCE/5')).not.toBeInTheDocument();
    await user.click(errorBtn());
    expect(screen.getByText('PRESENCE/5')).toBeInTheDocument();
  });

  describe('group by error', () => {
    const repeated: FindingDto[] = [
      f({ severity: 'ERROR', ruleId: 'PRESENCE/5', fieldNum: '123', fieldName: 'TotalNetAssets', message: 'Row 1 missing', rowIndex: 1 }),
      f({ severity: 'ERROR', ruleId: 'PRESENCE/5', fieldNum: '123', fieldName: 'TotalNetAssets', message: 'Row 2 missing', rowIndex: 2 }),
      f({ severity: 'ERROR', ruleId: 'PRESENCE/5', fieldNum: '123', fieldName: 'TotalNetAssets', message: 'Row 3 missing', rowIndex: 3 }),
      f({ severity: 'WARNING', ruleId: 'XF-12', fieldNum: '40', fieldName: 'CouponFrequency', message: 'Unusual', rowIndex: 4 }),
    ];

    it('collapses identical (severity, rule, field) findings into a single group row with count', async () => {
      const user = userEvent.setup();
      render(<FindingsTable findings={repeated} />);

      await user.click(screen.getByRole('button', { name: /Group by error/ }));

      // PRESENCE/5 should appear only once with occurrence count 3.
      const rule = screen.getAllByText('PRESENCE/5');
      expect(rule).toHaveLength(1);
      expect(screen.getByText('3')).toBeInTheDocument();
      // Header switches to the grouped layout.
      expect(screen.getByText(/Occurrences/i)).toBeInTheDocument();
    });

    it('shows group count in the header', async () => {
      const user = userEvent.setup();
      render(<FindingsTable findings={repeated} />);
      await user.click(screen.getByRole('button', { name: /Group by error/ }));
      expect(screen.getByText(/Findings \(4, 2 groups\)/)).toBeInTheDocument();
    });

    it('respects severity filter before grouping', async () => {
      const user = userEvent.setup();
      render(<FindingsTable findings={repeated} />);
      await user.click(screen.getByRole('button', { name: /Group by error/ }));
      // Disable ERRORs — the PRESENCE/5 group should disappear.
      await user.click(screen.getByRole('button', { name: 'ERROR (3)' }));
      expect(screen.queryByText('PRESENCE/5')).not.toBeInTheDocument();
      expect(screen.getByText('XF-12')).toBeInTheDocument();
    });
  });

  // Sanity: the severity badge renders inside the row.
  it('puts the severity tag in the same row as the rule id', () => {
    const { container } = render(<FindingsTable findings={[fixtures[0]]} />);
    const row = container.querySelector('tbody tr')!;
    const cells = within(row as HTMLElement);
    expect(cells.getByText('ERROR')).toBeInTheDocument();
    expect(cells.getByText('PRESENCE/5')).toBeInTheDocument();
  });
});
