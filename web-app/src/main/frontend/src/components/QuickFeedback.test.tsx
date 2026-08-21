import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { QuickFeedback } from './QuickFeedback';
import { QuickFeedbackResult } from '../types/api';

function wrap(ui: ReactNode) {
  // One-shot client per test so cached data from a prior test doesn't leak.
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function response(status: number, body: QuickFeedbackResult): Response {
  return {
    ok: status < 400,
    status,
    statusText: '',
    json: async () => body,
    text: async () => JSON.stringify(body),
    headers: new Headers(),
  } as unknown as Response;
}

describe('QuickFeedback', () => {
  let fetchSpy = vi.spyOn(globalThis, 'fetch');

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });
  afterEach(() => {
    fetchSpy.mockRestore();
  });

  it('hides comment and send until a star is picked', () => {
    wrap(<QuickFeedback />);
    expect(screen.queryByRole('button', { name: 'Send' })).toBeNull();
    expect(screen.queryByLabelText('Feedback comment')).toBeNull();
  });

  it('picking star 4 fills stars 1..4 and reveals the send controls', async () => {
    const user = userEvent.setup();
    wrap(<QuickFeedback />);
    await user.click(screen.getByRole('radio', { name: 'Rate 4 of 5' }));
    const stars = screen.getAllByRole('radio');
    const filled = stars.map((s) => s.querySelector('svg')?.getAttribute('fill'));
    expect(filled).toEqual(['currentColor', 'currentColor', 'currentColor', 'currentColor', 'none']);
    expect(screen.getByRole('button', { name: 'Send' })).toBeTruthy();
  });

  it('sends rating, comment, source and templateId, then resets on success', async () => {
    const user = userEvent.setup();
    fetchSpy.mockResolvedValue(response(200, { status: 'ok' }));
    wrap(<QuickFeedback templateId="TPT" />);

    await user.click(screen.getByRole('radio', { name: 'Rate 5 of 5' }));
    await user.type(screen.getByLabelText('Feedback comment'), 'great tool');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() =>
      expect(screen.getByText('Thank you for your feedback!')).toBeTruthy(),
    );
    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe('/api/quick-feedback');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      rating: 5,
      comment: 'great tool',
      source: 'web',
      templateId: 'TPT',
    });
    // Success resets the widget back to the compact star row.
    expect(screen.queryByRole('button', { name: 'Send' })).toBeNull();
  });

  it('shows the throttle message on 429', async () => {
    const user = userEvent.setup();
    fetchSpy.mockResolvedValue(response(429, { status: 'rate_limited' }));
    wrap(<QuickFeedback />);

    await user.click(screen.getByRole('radio', { name: 'Rate 1 of 5' }));
    await user.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() =>
      expect(
        screen.getByText('Too many submissions — please try again later.'),
      ).toBeTruthy(),
    );
    // State is kept so the user can retry later.
    expect(screen.getByRole('button', { name: 'Send' })).toBeTruthy();
  });
});
