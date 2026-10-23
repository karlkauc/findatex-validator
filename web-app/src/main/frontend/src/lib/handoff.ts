/**
 * Receive a file handed over from a sibling site.
 *
 * Flow: the Fund Data Converter (formerly TPT ⇄ FundsXML converter) opens
 * `https://www.findatex-validator.eu/?from=tpt-converter` in a new tab while
 * keeping the generated TPT file in memory. Once mounted we post
 * `{type: "findatex-validator:ready"}` to the opener; it answers with
 * `{type: "findatex-validator:file", name, content, templateId, templateVersion}`
 * (content is a transferred ArrayBuffer). The file never touches a server
 * before it is validated like any upload.
 *
 * Origins are checked strictly on both sides; without the `from` parameter
 * nothing here runs. The page needs `Cross-Origin-Opener-Policy: unsafe-none`
 * (see SpaHeadersFilter), otherwise the browser severs `window.opener`.
 */

export const HANDOFF_SOURCES = ['tpt-converter'] as const;
export type HandoffSource = (typeof HANDOFF_SOURCES)[number];

// Keep in step with the validator origins in the Fund Data Converter (lib/handoff.ts). The old
// address stays while it redirects to the new one.
const SENDER_ORIGINS = ['https://www.fund-data-converter.eu', 'https://converter.status20.net'];

export const HANDOFF_LABELS: Record<HandoffSource, string> = {
  'tpt-converter': 'Fund Data Converter',
};

/** Mirrors FileUpload / quarkus.http.limits.max-body-size=25M. */
export const HANDOFF_MAX_BYTES = 25 * 1024 * 1024;
const EXTENSIONS = ['.xlsx', '.xlsm', '.csv'];

export interface HandoffFile {
  file: File;
  templateId: string;
  templateVersion: string;
}

interface FileMessage {
  type: 'findatex-validator:file';
  name: string;
  content: ArrayBuffer;
  templateId: string;
  templateVersion: string;
}

function isAllowedOrigin(origin: string): boolean {
  if (SENDER_ORIGINS.includes(origin)) return true;
  // Local development: both apps run on localhost dev servers.
  return import.meta.env.DEV && /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin);
}

export function handoffSource(search: string = window.location.search): HandoffSource | null {
  const from = new URLSearchParams(search).get('from');
  return (HANDOFF_SOURCES as readonly string[]).includes(from ?? '') ? (from as HandoffSource) : null;
}

function isFileMessage(data: unknown): data is FileMessage {
  if (!data || typeof data !== 'object') return false;
  const d = data as Record<string, unknown>;
  return (
    d.type === 'findatex-validator:file' &&
    typeof d.name === 'string' &&
    d.content instanceof ArrayBuffer &&
    typeof d.templateId === 'string' &&
    typeof d.templateVersion === 'string'
  );
}

function mimeType(name: string): string {
  const lower = name.toLowerCase();
  if (lower.endsWith('.csv')) return 'text/csv';
  return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
}

/** Why a well-formed message is still not accepted, or null. */
export function rejectReason(name: string, size: number): string | null {
  if (!EXTENSIONS.some((ext) => name.toLowerCase().endsWith(ext))) {
    return `${name}: only .xlsx, .xlsm and .csv files can be handed over`;
  }
  if (size > HANDOFF_MAX_BYTES) return `${name} is larger than 25 MB`;
  return null;
}

/**
 * Announce readiness to the opener and resolve the handed-over file.
 * Returns a cleanup function; `onFile` / `onReject` are called at most once.
 */
export function receiveHandoff(
  onFile: (handoff: HandoffFile) => void,
  onReject: (reason: string) => void,
): () => void {
  const opener = window.opener as Window | null;
  if (!opener) return () => {};

  let done = false;
  const onMessage = (event: MessageEvent) => {
    if (done || event.source !== opener || !isAllowedOrigin(event.origin)) return;
    if (!isFileMessage(event.data)) return;
    done = true;
    window.removeEventListener('message', onMessage);
    const { name, content, templateId, templateVersion } = event.data;
    const reason = rejectReason(name, content.byteLength);
    if (reason) {
      onReject(reason);
      return;
    }
    onFile({ file: new File([content], name, { type: mimeType(name) }), templateId, templateVersion });
  };
  window.addEventListener('message', onMessage);

  // targetOrigin must be a single value; posting to a non-matching origin is
  // silently dropped, so announce to every sender we accept.
  const targets = import.meta.env.DEV ? [...SENDER_ORIGINS, '*'] : SENDER_ORIGINS;
  for (const origin of targets) {
    try {
      opener.postMessage({ type: 'findatex-validator:ready' }, origin);
    } catch {
      /* opener closed or cross-origin severed — nothing to do */
    }
  }
  return () => window.removeEventListener('message', onMessage);
}

/** Drop `from` from the address bar once the hand-over is over (a reload must not wait again). */
export function clearHandoffParam(): void {
  const url = new URL(window.location.href);
  if (!url.searchParams.has('from')) return;
  url.searchParams.delete('from');
  window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
}
