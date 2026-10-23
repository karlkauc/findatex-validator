import { describe, it, expect, vi, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { ValidationProgress } from './ValidationProgress';

describe('ValidationProgress', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('names the file and its source and counts the seconds', () => {
    vi.useFakeTimers();
    render(<ValidationProgress fileName="fund_TPT_V7.xlsx" source="Fund Data Converter" />);
    expect(screen.getByRole('status')).toHaveTextContent('Validating fund_TPT_V7.xlsx…');
    expect(screen.getByText('Received from Fund Data Converter.')).toBeInTheDocument();
    expect(screen.getByText('0 s')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByText('3 s')).toBeInTheDocument();
  });

  it('works without a file name or source', () => {
    render(<ValidationProgress />);
    expect(screen.getByRole('status')).toHaveTextContent('Validating…');
    expect(screen.queryByText(/Received from/)).toBeNull();
  });
});
