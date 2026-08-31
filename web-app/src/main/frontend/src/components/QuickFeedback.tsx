import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Loader2, Star } from 'lucide-react';
import { submitQuickFeedback } from '../api/client';
import { QuickFeedbackStatusWire } from '../types/api';

const MAX_COMMENT_LENGTH = 2000;

const MESSAGES: Record<QuickFeedbackStatusWire, { ok: boolean; text: string }> = {
  ok: { ok: true, text: 'Thank you for your feedback!' },
  invalid: { ok: false, text: 'Please pick a star rating first.' },
  rate_limited: {
    ok: false,
    text: 'Too many submissions — please try again later.',
  },
  unavailable: {
    ok: false,
    text: 'Feedback is not possible right now. Please try again later.',
  },
};

/**
 * Low-barrier star rating with an optional comment, shown in the app header.
 *
 * The stars sit inline next to the quota badge; the comment box and Send button
 * only appear — as a popover under the stars — once a star is picked, so the
 * header row stays one line high for everyone who never rates anything. On a
 * successful send the rating resets and the popover keeps only the confirmation
 * until the next click elsewhere.
 *
 * Rendered only when the server has a feedback store (see the
 * /api/quick-feedback-config gating in App). Only rating, comment, source and
 * template type are sent — no personal data.
 */
export function QuickFeedback({ templateId }: { templateId?: string }) {
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const mutation = useMutation({
    mutationFn: submitQuickFeedback,
    onSuccess: (res) => {
      if (res.status === 'ok') {
        setRating(0);
        setComment('');
      }
    },
  });

  const result = mutation.data ? MESSAGES[mutation.data.status] : null;
  const shown = hover ?? rating;
  const open = rating > 0 || result !== null;

  // Dismiss on click-away / Escape: the popover overlays page content, and a
  // header widget that stays open after the user has moved on is in the way.
  useEffect(() => {
    if (!open) return;
    const close = () => {
      setRating(0);
      mutation.reset();
    };
    const onPointerDown = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) close();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
    // mutation.reset is stable; re-subscribing on every render would thrash.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  return (
    <div ref={containerRef} className="relative" role="group" aria-label="Rate this validator">
      <div className="flex items-center gap-1.5">
        <span className="hidden text-xs font-medium text-navy-100/90 lg:inline">
          Rate this validator
        </span>
        <div className="flex items-center" role="radiogroup" aria-label="Star rating">
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={rating === value}
              aria-label={`Rate ${value} of 5`}
              onClick={() => setRating(value)}
              onMouseEnter={() => setHover(value)}
              onMouseLeave={() => setHover(null)}
              className="p-0.5 text-amber-300 hover:text-amber-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
            >
              <Star
                className="h-4 w-4"
                fill={value <= shown ? 'currentColor' : 'none'}
                aria-hidden="true"
              />
            </button>
          ))}
        </div>
      </div>

      {open && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (rating < 1 || mutation.isPending) return;
            mutation.mutate({ rating, comment: comment.trim() || undefined, templateId });
          }}
          className="absolute right-0 top-full z-40 mt-2 w-80 max-w-[calc(100vw-3rem)] rounded-md border border-slate-200 bg-white p-3 text-slate-700 shadow-lg"
        >
          {rating > 0 && (
            <>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                maxLength={MAX_COMMENT_LENGTH}
                rows={3}
                placeholder="Optional: what works well, what is missing?"
                aria-label="Feedback comment"
                className="w-full rounded-md border border-slate-300 px-2.5 py-1.5 text-xs text-slate-700 focus:border-navy-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500/40"
              />
              <div className="mt-2 flex justify-end">
                <button
                  type="submit"
                  className="btn-primary px-3 py-1.5 text-xs"
                  disabled={mutation.isPending}
                  aria-busy={mutation.isPending}
                >
                  {mutation.isPending ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                      Sending…
                    </>
                  ) : (
                    'Send'
                  )}
                </button>
              </div>
            </>
          )}
          <div aria-live="polite" aria-atomic="true">
            {result && (
              <p className={`text-xs ${result.ok ? 'text-emerald-700' : 'text-red-700'} ${rating > 0 ? 'mt-2' : ''}`}>
                {result.text}
              </p>
            )}
          </div>
        </form>
      )}
    </div>
  );
}
