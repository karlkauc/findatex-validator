import { ReactNode, useId } from 'react';
import { ChevronDown } from 'lucide-react';
import { usePersistedBoolean } from '../lib/usePersistedState';

interface Props {
  title: ReactNode;
  /** Suffix of the localStorage key; the open/closed state is remembered per browser. */
  storageKey: string;
  defaultOpen?: boolean;
  /** Stable id for the panel (aria-controls); generated when omitted. */
  panelId?: string;
  /** Rendered on the right of the header row, outside the toggle button. */
  headerExtra?: ReactNode;
  className?: string;
  children: ReactNode;
}

/**
 * Section with a chevron header that folds its body away. Used for Scores,
 * Per Fund and Notes so a user who only wants the findings can get there fast;
 * the state survives reloads.
 */
export function CollapsibleSection({
  title,
  storageKey,
  defaultOpen = true,
  panelId,
  headerExtra,
  className,
  children,
}: Props) {
  const generatedId = useId();
  const id = panelId ?? `section-${generatedId}`;
  const [open, setOpen] = usePersistedBoolean('section.' + storageKey, defaultOpen);

  return (
    <div className={className}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          aria-controls={id}
          className="inline-flex items-center gap-2 rounded-md text-sm font-semibold uppercase tracking-wide text-slate-500 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500 focus-visible:ring-offset-1"
        >
          <ChevronDown
            className={'h-4 w-4 transition-transform ' + (open ? '' : '-rotate-90')}
            aria-hidden="true"
          />
          {title}
        </button>
        {headerExtra}
      </div>
      {open && <div id={id}>{children}</div>}
    </div>
  );
}
