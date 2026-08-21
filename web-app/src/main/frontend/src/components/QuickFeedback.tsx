import { useState } from 'react';
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
 * Low-barrier star rating with an optional comment. The comment/send controls
 * appear only once a star is picked so the footer stays compact. Rendered only
 * when the server has a feedback store (see /api/quick-feedback-config gating
 * in App). Only rating, comment, source and template type are sent — no
 * personal data.
 */
export function QuickFeedback({ templateId }: { templateId?: string }) {
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState<number | null>(null);
  const [comment, setComment] = useState('');
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

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (rating < 1 || mutation.isPending) return;
        mutation.mutate({ rating, comment: comment.trim() || undefined, templateId });
      }}
      className="mt-3 border-t border-slate-200 pt-3"
      aria-label="Rate this validator"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-slate-700">Rate this validator</span>
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
              className="p-0.5 text-amber-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500/40"
            >
              <Star
                className="h-5 w-5"
                fill={value <= shown ? 'currentColor' : 'none'}
                aria-hidden="true"
              />
            </button>
          ))}
        </div>
      </div>
      {rating > 0 && (
        <div className="mt-2 flex flex-wrap items-start gap-2">
          <textarea
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={MAX_COMMENT_LENGTH}
            rows={2}
            placeholder="Optional: what works well, what is missing?"
            aria-label="Feedback comment"
            className="min-w-[16rem] flex-1 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs text-slate-700 focus:border-navy-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500/40"
          />
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
      )}
      <div aria-live="polite" aria-atomic="true">
        {result && (
          <p className={`mt-1 text-xs ${result.ok ? 'text-emerald-700' : 'text-red-700'}`}>
            {result.text}
          </p>
        )}
      </div>
    </form>
  );
}
