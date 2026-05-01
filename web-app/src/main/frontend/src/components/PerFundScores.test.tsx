import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PerFundScores } from './PerFundScores';
import { PerFundScoreDto } from '../types/api';

const make = (id: string, overall: number): PerFundScoreDto => ({
  portfolioId: id,
  portfolioName: id + ' Fund',
  valuationDate: '2025-12-31',
  scores: [
    { dimension: 'OVERALL',                 value: overall, percentage: Math.round(overall * 100) },
    { dimension: 'MANDATORY_COMPLETENESS',  value: 1.0,     percentage: 100 },
    { dimension: 'FORMAT_CONFORMANCE',      value: 1.0,     percentage: 100 },
    { dimension: 'CLOSED_LIST_CONFORMANCE', value: 1.0,     percentage: 100 },
    { dimension: 'CROSS_FIELD_CONSISTENCY', value: 1.0,     percentage: 100 },
  ],
});

describe('PerFundScores', () => {
  it('renders nothing for an empty list', () => {
    const { container } = render(<PerFundScores perFundScores={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders one block per fund', () => {
    render(<PerFundScores perFundScores={[
      make('FR0010000001', 0.99),
      make('DE0010000002', 0.55),
      make('LU0010000003', 0.85),
    ]} />);
    // portfolioId appears in the ID span; portfolioName also contains the id string,
    // so use getAllByText and verify at least one match is present.
    expect(screen.getAllByText(/FR0010000001/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/DE0010000002/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/LU0010000003/).length).toBeGreaterThan(0);
  });
});
