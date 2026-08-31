import { Download } from 'lucide-react';

/**
 * Header link to the offline desktop build.
 *
 * This is the answer to the objection that keeps most of the target audience
 * from using the web app at all — fund data may not be uploaded to a third
 * party — so it belongs where someone sees it before they decide, not only in
 * a note below the form or once the quota is exhausted.
 *
 * Hidden when no URL is configured (`FINDATEX_WEB_DESKTOP_DOWNLOAD_URL`
 * empty), like every other optional action in this UI: an operator running
 * their own instance may have nothing to link to.
 */
export function DesktopDownloadLink({ url }: { url: string | null | undefined }) {
  const target = url?.trim();
  if (!target) return null;

  return (
    <a
      href={target}
      target="_blank"
      rel="noopener noreferrer"
      title="Download the desktop app — validates offline, your files never leave your machine"
      className="inline-flex items-center gap-1.5 rounded-md bg-white px-3 py-1.5 text-xs font-semibold text-navy-800 hover:bg-navy-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
    >
      <Download className="h-4 w-4" aria-hidden="true" />
      Offline app
    </a>
  );
}
