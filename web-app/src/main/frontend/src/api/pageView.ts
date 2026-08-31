/**
 * Fires the page-view beacon once per page load.
 *
 * Server-side counting was the alternative and would need no client code at
 * all, but it counts every crawler and probe as a visitor — at this traffic
 * level that would drown the one number this is for: how many people arrive
 * versus how many actually validate a file.
 *
 * Nothing is stored on the device: no cookie, no localStorage, no id. A failed
 * beacon is ignored — a counter must never surface an error to the user.
 */

/**
 * Module-level guard. React 18 StrictMode mounts effects twice in dev, and the
 * effect also re-runs on a fast refresh; both would double-count.
 */
let sent = false;

/** Campaign tag, so a LinkedIn post can be told apart from organic traffic. */
function campaignOf(search: string): string | undefined {
  const params = new URLSearchParams(search);
  return params.get('utm_source') ?? params.get('ref') ?? undefined;
}

/** Same-origin referrers are internal navigation, not a traffic source. */
function externalReferrer(referrer: string, origin: string): string | undefined {
  if (!referrer || referrer.startsWith(origin)) return undefined;
  return referrer;
}

export function reportPageView(): void {
  if (sent || typeof window === 'undefined') return;
  sent = true;

  const body = JSON.stringify({
    path: window.location.pathname,
    referrer: externalReferrer(document.referrer, window.location.origin),
    campaign: campaignOf(window.location.search),
  });

  // keepalive so the request survives an immediate navigation away — the
  // bounce we most want to count is exactly the one that leaves fastest.
  void fetch('/api/page-view', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    keepalive: true,
  }).catch(() => {
    /* counting is best-effort; never surface this */
  });
}

/** Exported for tests. */
export const __testables = { campaignOf, externalReferrer };
