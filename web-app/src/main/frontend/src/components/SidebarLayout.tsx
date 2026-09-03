import { ReactNode } from 'react';
import { ChevronRight, PanelLeftOpen } from 'lucide-react';

export const INPUT_COLUMN_ID = 'input-column';

interface Props {
  collapsed: boolean;
  onExpand: () => void;
  sidebar: ReactNode;
  children: ReactNode;
}

/**
 * Two-column shell: a narrow input column on the left, results taking the
 * rest of the viewport. Collapsed, the input column is replaced by a slim
 * rail with a single button so the wide findings table gets the full width.
 * Below the `lg` breakpoint the columns stack; the rail then becomes a
 * one-line bar so the input can always be brought back.
 */
export function SidebarLayout({ collapsed, onExpand, sidebar, children }: Props) {
  return (
    <div
      className={
        'grid grid-cols-1 gap-6 ' +
        (collapsed ? 'lg:grid-cols-[44px_minmax(0,1fr)]' : 'lg:grid-cols-[320px_minmax(0,1fr)]')
      }
    >
      {collapsed ? (
        <aside className="self-start">
          <button
            type="button"
            onClick={onExpand}
            aria-label="Expand input panel"
            aria-expanded={false}
            aria-controls={INPUT_COLUMN_ID}
            title="Show input panel"
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 shadow-sm hover:bg-slate-50 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500 lg:w-11 lg:flex-col lg:gap-3 lg:px-0 lg:py-3"
          >
            <PanelLeftOpen className="h-4 w-4 lg:hidden" aria-hidden="true" />
            <ChevronRight className="hidden h-4 w-4 lg:block" aria-hidden="true" />
            <span className="lg:rotate-180 lg:[writing-mode:vertical-rl]">Input</span>
          </button>
        </aside>
      ) : (
        <aside id={INPUT_COLUMN_ID} className="space-y-5 self-start">
          {sidebar}
        </aside>
      )}
      <section className="min-w-0 space-y-5">{children}</section>
    </div>
  );
}
