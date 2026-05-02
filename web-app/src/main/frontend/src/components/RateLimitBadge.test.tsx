import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { RateLimitBadge, formatDuration } from './RateLimitBadge';
import { RateLimitStatus } from '../types/api';

function wrap(ui: ReactNode) {
  // One-shot client per test so cached data from a prior test doesn't leak.
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function ok(body: RateLimitStatus): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    json: async () => body,
    text: async () => JSON.stringify(body),
    headers: new Headers(),
  } as unknown as Response;
}

describe('formatDuration', () => {
  it.each([
    [0, '0s'],
    [5, '5s'],
    [59, '59s'],
    [60, '1m'],
    [125, '2m'],
    [3599, '59m'],
    [3600, '1h'],
    [3660, '1h 1m'],
    [7320, '2h 2m'],
  ])('formats %i seconds as %s', (input, expected) => {
    expect(formatDuration(input)).toBe(expected);
  });

  it('clamps negative values to 0s', () => {
    expect(formatDuration(-5)).toBe('0s');
  });
});

describe('RateLimitBadge', () => {
  let fetchSpy = vi.spyOn(globalThis, 'fetch');

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });
  afterEach(() => {
    fetchSpy.mockRestore();
  });

  it('renders quota with reset suffix when remaining > 0', async () => {
    fetchSpy.mockResolvedValue(
      ok({ limit: 10, remaining: 7, windowSeconds: 3600, resetInSeconds: 1380 }),
    );
    wrap(<RateLimitBadge />);
    await waitFor(() => expect(screen.getByTestId('rate-limit-badge')).toBeInTheDocument());
    const badge = screen.getByTestId('rate-limit-badge');
    expect(badge.textContent).toMatch(/Quota:\s*7\s*\/\s*10/);
    expect(badge.textContent).toMatch(/Reset in\s*23m/);
  });

  it('omits the reset suffix when the bucket is full (resetInSeconds === 0)', async () => {
    fetchSpy.mockResolvedValue(
      ok({ limit: 10, remaining: 10, windowSeconds: 3600, resetInSeconds: 0 }),
    );
    wrap(<RateLimitBadge />);
    await waitFor(() => expect(screen.getByTestId('rate-limit-badge')).toBeInTheDocument());
    expect(screen.getByTestId('rate-limit-badge').textContent).not.toMatch(/Reset in/);
  });

  it('uses the red tone when remaining === 0', async () => {
    fetchSpy.mockResolvedValue(
      ok({ limit: 10, remaining: 0, windowSeconds: 3600, resetInSeconds: 600 }),
    );
    wrap(<RateLimitBadge />);
    await waitFor(() => expect(screen.getByTestId('rate-limit-badge')).toBeInTheDocument());
    const badge = screen.getByTestId('rate-limit-badge');
    expect(badge.className).toMatch(/red/);
    expect(badge.textContent).toMatch(/Quota:\s*0\s*\/\s*10/);
  });

  it('renders nothing while the status is still loading', () => {
    // Pending fetch — the query has no data yet.
    fetchSpy.mockReturnValue(new Promise(() => {}) as ReturnType<typeof globalThis.fetch>);
    const { container } = wrap(<RateLimitBadge />);
    expect(container.querySelector('[data-testid="rate-limit-badge"]')).toBeNull();
  });

  it('renders nothing on fetch error (so a transient failure does not break the header)', async () => {
    fetchSpy.mockRejectedValue(new Error('boom'));
    const { container } = wrap(<RateLimitBadge />);
    // Give react-query a tick to settle into the error state.
    await waitFor(() => {
      expect(container.querySelector('[data-testid="rate-limit-badge"]')).toBeNull();
    });
  });
});
