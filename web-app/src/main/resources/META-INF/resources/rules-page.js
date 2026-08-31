/*
 * Page-view beacon for the server-rendered rule pages (/rules/…).
 *
 * Those pages deliberately load no application bundle, so they cannot use
 * src/api/pageView.ts — this is the same three fields, in plain ES5-ish
 * JavaScript, as a separate file rather than inline because script-src is
 * strict 'self' with no 'unsafe-inline'.
 *
 * Best-effort by design: a counter must never surface an error to a reader.
 */
(function () {
  try {
    var referrer = document.referrer && document.referrer.indexOf(location.origin) !== 0
      ? document.referrer
      : undefined;
    var params = new URLSearchParams(location.search);
    fetch('/api/page-view', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: location.pathname,
        referrer: referrer,
        campaign: params.get('utm_source') || params.get('ref') || undefined
      }),
      keepalive: true
    }).catch(function () {});
  } catch (e) {
    /* no analytics is better than a broken page */
  }
})();
