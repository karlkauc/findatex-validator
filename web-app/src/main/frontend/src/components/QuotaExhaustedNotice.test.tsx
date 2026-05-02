import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { QuotaExhaustedNotice } from './QuotaExhaustedNotice';
import { RateLimitStatus } from '../types/api';

function wrap(ui: ReactNode) {
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

describe('QuotaExhaustedNotice', () => {
  let fetchSpy = vi.spyOn(globalThis, 'fetch');

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });
  afterEach(() => {
    fetchSpy.mockRestore();
  });

  it('renders nothing while remaining > 0', async () => {
    fetchSpy.mockResolvedValue(
      ok({ limit: 10, remaining: 3, windowSeconds: 3600, resetInSeconds: 1200 }),
    );
    const { container } = wrap(<QuotaExhaustedNotice />);
    // Wait long enough for the query to resolve, then assert nothing rendered.
    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());
    expect(container.querySelector('[data-testid="quota-exhausted-notice"]')).toBeNull();
  });

  it('renders the notice with reset duration when remaining === 0 and no desktop URL', async () => {
    fetchSpy.mockResolvedValue(
      ok({ limit: 5, remaining: 0, windowSeconds: 3600, resetInSeconds: 600 }),
    );
    wrap(<QuotaExhaustedNotice />);
    await waitFor(() =>
      expect(screen.getByTestId('quota-exhausted-notice')).toBeInTheDocument(),
    );
    expect(screen.getByText(/quota exhausted/i)).toBeInTheDocument();
    expect(screen.getByText(/all 5 validations/i)).toBeInTheDocument();
    expect(screen.getByText(/10m/)).toBeInTheDocument();
    // No download URL → no link, just the strong tag.
    expect(screen.queryByRole('link')).toBeNull();
    const strong = screen.getByText(/JavaFX desktop version/i);
    expect(strong.tagName.toLowerCase()).toBe('strong');
  });

  it('renders a clickable link when desktopDownloadUrl is provided', async () => {
    fetchSpy.mockResolvedValue(
      ok({
        limit: 5,
        remaining: 0,
        windowSeconds: 3600,
        resetInSeconds: 0,
        desktopDownloadUrl: 'https://example.test/findatex',
      }),
    );
    wrap(<QuotaExhaustedNotice />);
    const link = await screen.findByRole('link', { name: /JavaFX desktop version/i });
    expect(link).toHaveAttribute('href', 'https://example.test/findatex');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('treats a blank desktopDownloadUrl as missing (no link)', async () => {
    fetchSpy.mockResolvedValue(
      ok({
        limit: 5,
        remaining: 0,
        windowSeconds: 3600,
        resetInSeconds: 0,
        desktopDownloadUrl: '   ',
      }),
    );
    wrap(<QuotaExhaustedNotice />);
    await waitFor(() =>
      expect(screen.getByTestId('quota-exhausted-notice')).toBeInTheDocument(),
    );
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('omits the reset clause when resetInSeconds is 0', async () => {
    fetchSpy.mockResolvedValue(
      ok({ limit: 5, remaining: 0, windowSeconds: 3600, resetInSeconds: 0 }),
    );
    wrap(<QuotaExhaustedNotice />);
    await waitFor(() =>
      expect(screen.getByTestId('quota-exhausted-notice')).toBeInTheDocument(),
    );
    expect(screen.queryByText(/Resets in/i)).toBeNull();
  });
});
