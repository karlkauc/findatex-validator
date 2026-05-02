import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Gauge } from 'lucide-react';
import { fetchRateLimitStatus } from '../api/client';

export const RATE_LIMIT_QUERY_KEY = ['rateLimitStatus'] as const;

export function RateLimitBadge() {
  const { data, dataUpdatedAt, isError } = useQuery({
    queryKey: RATE_LIMIT_QUERY_KEY,
    queryFn: fetchRateLimitStatus,
    refetchInterval: 30_000,
    staleTime: 0,
  });

  // Local 1s tick that drives the countdown when the bucket is empty. We anchor
  // the displayed reset to the last server payload (dataUpdatedAt) so it can't
  // drift more than ~30s before the next refetch corrects it.
  const [now, setNow] = useState(() => Date.now());
  const exhausted = data ? data.remaining <= 0 : false;
  useEffect(() => {
    if (!exhausted) return;
    const id = setInterval(() => setNow(Date.now()), 1_000);
    return () => clearInterval(id);
  }, [exhausted, dataUpdatedAt]);

  if (isError || !data) return null;

  // Clamp to >= 0: dataUpdatedAt can land *after* the initial `now` snapshot
  // (the query resolves on a later tick), which would otherwise yield a phantom
  // "Reset in 1s" badge even when the server reports a full bucket.
  const elapsedSinceFetch = dataUpdatedAt
    ? Math.max(0, Math.floor((now - dataUpdatedAt) / 1_000))
    : 0;
  const liveResetIn =
    data.resetInSeconds === 0 ? 0 : Math.max(0, data.resetInSeconds - elapsedSinceFetch);

  const tone = exhausted
    ? 'border-red-300/60 bg-red-500/15 text-red-50'
    : 'border-white/20 bg-white/10 text-white';

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1.5 text-xs font-medium ${tone}`}
      title={`Per-IP quota: ${data.limit} validations / ${formatWindow(data.windowSeconds)}`}
      aria-live="polite"
      data-testid="rate-limit-badge"
    >
      <Gauge className="h-3.5 w-3.5" aria-hidden="true" />
      <span>
        Quota: {data.remaining} / {data.limit}
      </span>
      {liveResetIn > 0 && (
        <span className="opacity-80">· Reset in {formatDuration(liveResetIn)}</span>
      )}
    </span>
  );
}

export function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return m > 0 ? `${h}h ${m}m` : `${h}h`;
}

function formatWindow(seconds: number): string {
  if (seconds % 3600 === 0) {
    const h = seconds / 3600;
    return h === 1 ? 'hour' : `${h} hours`;
  }
  return formatDuration(seconds);
}
