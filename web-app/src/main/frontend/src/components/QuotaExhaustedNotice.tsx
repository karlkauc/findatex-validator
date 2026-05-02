import { useQuery } from '@tanstack/react-query';
import { Clock, MonitorDown } from 'lucide-react';
import { fetchRateLimitStatus } from '../api/client';
import { RATE_LIMIT_QUERY_KEY, formatDuration } from './RateLimitBadge';

/**
 * Banner shown over the result area when the per-IP quota hits zero. Points
 * the user at the JavaFX desktop build (no quota, no upload) so they have a
 * way forward without waiting for the bucket to refill.
 */
export function QuotaExhaustedNotice() {
  const { data } = useQuery({
    queryKey: RATE_LIMIT_QUERY_KEY,
    queryFn: fetchRateLimitStatus,
    refetchInterval: 30_000,
    staleTime: 0,
  });

  if (!data || data.remaining > 0) return null;

  const downloadUrl = data.desktopDownloadUrl?.trim() || null;

  return (
    <div
      className="flex items-start gap-3 rounded-md border border-amber-300 bg-amber-50 p-4"
      role="status"
      data-testid="quota-exhausted-notice"
    >
      <Clock className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" aria-hidden="true" />
      <div className="text-sm text-amber-900">
        <p className="font-semibold">Validation quota exhausted</p>
        <p className="mt-1">
          You have used all {data.limit} validations for the current window.
          {data.resetInSeconds > 0 && (
            <> Resets in <strong>{formatDuration(data.resetInSeconds)}</strong>.</>
          )}
        </p>
        <p className="mt-2">
          Need to validate now? Use the{' '}
          {downloadUrl ? (
            <a
              href={downloadUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 font-semibold text-amber-900 underline decoration-amber-700 underline-offset-2 hover:text-amber-800"
            >
              <MonitorDown className="h-4 w-4" aria-hidden="true" />
              JavaFX desktop version
            </a>
          ) : (
            <strong>JavaFX desktop version</strong>
          )}
          {' '}— no quota, and your file never leaves your machine.
        </p>
      </div>
    </div>
  );
}
