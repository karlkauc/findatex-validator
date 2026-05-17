import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Loader2, Mail } from 'lucide-react';
import { subscribeNewsletter } from '../api/client';
import { NewsletterStatusWire } from '../types/api';

const MESSAGES: Record<NewsletterStatusWire, { ok: boolean; text: string }> = {
  pending: {
    ok: true,
    text: 'Almost done — please confirm the link in the email we just sent.',
  },
  subscribed: { ok: true, text: 'You are subscribed. Thank you!' },
  already_subscribed: { ok: true, text: 'You are already subscribed.' },
  already_pending: {
    ok: true,
    text: 'Almost done — please confirm the link in the email we already sent.',
  },
  invalid_email: { ok: false, text: 'Please enter a valid email address.' },
  unavailable: {
    ok: false,
    text: 'Sign-up is not possible right now. Please try again later.',
  },
};

/**
 * Compact newsletter sign-up. Synchronous with a clear result message (the
 * address is a personal datum and the user is acting deliberately — unlike the
 * anonymous usage-stats ingest). Rendered only when the operator configured a
 * provider (see /api/newsletter-config gating in App).
 */
export function NewsletterSignup() {
  const [email, setEmail] = useState('');
  const mutation = useMutation({
    mutationFn: subscribeNewsletter,
    onSuccess: (res) => {
      if (res.status !== 'invalid_email') setEmail('');
    },
  });

  const result = mutation.data ? MESSAGES[mutation.data.status] : null;

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        const trimmed = email.trim();
        if (!trimmed || mutation.isPending) return;
        mutation.mutate(trimmed);
      }}
      className="mt-3 border-t border-slate-200 pt-3"
      aria-label="Newsletter sign-up"
    >
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="newsletter-email" className="flex items-center gap-1.5 font-medium text-slate-700">
          <Mail className="h-4 w-4" aria-hidden="true" />
          Newsletter
        </label>
        <input
          id="newsletter-email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          className="min-w-[14rem] flex-1 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs text-slate-700 focus:border-navy-500 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500/40"
        />
        <button
          type="submit"
          className="btn-primary px-3 py-1.5 text-xs"
          disabled={mutation.isPending || !email.trim()}
          aria-busy={mutation.isPending}
        >
          {mutation.isPending ? (
            <>
              <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
              Subscribing…
            </>
          ) : (
            'Subscribe'
          )}
        </button>
      </div>
      <p className="mt-2 text-[11px] text-slate-400">
        Double opt-in. We forward your address only to our newsletter provider;
        unsubscribe anytime via the link in every email.
      </p>
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
