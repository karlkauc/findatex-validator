import { useEffect, useMemo, useRef, useState } from 'react';
import { ExternalLink, X } from 'lucide-react';
import { FalsePositiveReport, issueBody, issueUrl } from '../feedback/githubIssue';

interface Props {
  open: boolean;
  onClose: () => void;
  githubRepo: string;
  /** The finding context without the user comment — the modal owns the comment. */
  report: Omit<FalsePositiveReport, 'userComment'> | null;
}

export function FeedbackModal({ open, onClose, githubRepo, report }: Props) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const [comment, setComment] = useState('');

  useEffect(() => {
    if (open) setComment('');
  }, [open, report]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    closeRef.current?.focus();
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);

  const full: FalsePositiveReport | null = useMemo(
    () => (report ? { ...report, userComment: comment } : null),
    [report, comment],
  );
  const preview = useMemo(() => (full ? issueBody(full) : ''), [full]);

  if (!open || !full) return null;

  const openIssue = () => {
    const url = issueUrl(githubRepo, full);
    window.open(url, '_blank', 'noopener');
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="feedback-modal-title"
      className="fixed inset-0 z-50 flex items-stretch justify-center bg-slate-900/60 px-2 py-4 sm:px-6 sm:py-10"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="flex max-h-full w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-5 py-3">
          <h2 id="feedback-modal-title" className="text-sm font-semibold text-slate-800">
            Report a false positive
          </h2>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-200 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto px-6 py-5">
          <p className="text-sm text-slate-600">
            This opens a pre-filled GitHub issue in a new tab. Review exactly what
            will be submitted below, then press <strong>Open GitHub issue</strong>.
            Nothing is sent automatically — you submit it on GitHub yourself.
          </p>

          <div>
            <label
              htmlFor="fp-comment"
              className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500"
            >
              Why is this a false positive? (optional, recommended)
            </label>
            <textarea
              id="fp-comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={3}
              placeholder="e.g. field 12 is not mandatory for our profile because …"
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-navy-500 focus:outline-none focus:ring-1 focus:ring-navy-500"
            />
          </div>

          <div>
            <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Exactly this will be placed in the GitHub issue
            </p>
            <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-md border border-slate-200 bg-slate-50 p-3 text-xs leading-relaxed text-slate-800">
              {preview}
            </pre>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={openIssue}
            className="btn-primary text-xs"
          >
            <ExternalLink className="h-4 w-4" /> Open GitHub issue
          </button>
        </div>
      </div>
    </div>
  );
}
