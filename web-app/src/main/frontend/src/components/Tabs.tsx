import { KeyboardEvent, ReactNode, useRef } from 'react';

export interface TabDef<K extends string> {
  id: K;
  label: ReactNode;
}

interface StripProps<K extends string> {
  tabs: TabDef<K>[];
  active: K;
  onChange: (id: K) => void;
  ariaLabel: string;
}

export function tabId(id: string): string {
  return `tab-${id}`;
}

export function panelId(id: string): string {
  return `panel-${id}`;
}

/**
 * Minimal WAI-ARIA tab strip: roving tabindex, arrow keys / Home / End move
 * both selection and focus. Styled like the toggle buttons in FindingsTable.
 */
export function TabStrip<K extends string>({ tabs, active, onChange, ariaLabel }: StripProps<K>) {
  const refs = useRef<Map<K, HTMLButtonElement>>(new Map());

  const select = (id: K) => {
    onChange(id);
    refs.current.get(id)?.focus();
  };

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    const idx = tabs.findIndex((t) => t.id === active);
    if (idx < 0) return;
    let next = idx;
    switch (e.key) {
      case 'ArrowRight': next = (idx + 1) % tabs.length; break;
      case 'ArrowLeft':  next = (idx - 1 + tabs.length) % tabs.length; break;
      case 'Home':       next = 0; break;
      case 'End':        next = tabs.length - 1; break;
      default: return;
    }
    e.preventDefault();
    select(tabs[next].id);
  };

  return (
    <div role="tablist" aria-label={ariaLabel} className="flex flex-wrap gap-2">
      {tabs.map((t) => {
        const selected = t.id === active;
        return (
          <button
            key={t.id}
            ref={(el) => {
              if (el) refs.current.set(t.id, el);
              else refs.current.delete(t.id);
            }}
            type="button"
            role="tab"
            id={tabId(t.id)}
            aria-selected={selected}
            aria-controls={panelId(t.id)}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(t.id)}
            onKeyDown={onKeyDown}
            className={
              'rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ' +
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-navy-500 ' +
              (selected
                ? 'border-navy-300 bg-navy-50 text-navy-700'
                : 'border-slate-300 bg-white text-slate-500 hover:bg-slate-100')
            }
          >
            {t.label}
          </button>
        );
      })}
    </div>
  );
}

interface PanelProps {
  id: string;
  active: boolean;
  children: ReactNode;
}

/** Stays mounted while hidden so filter state and fetched data survive tab switches. */
export function TabPanel({ id, active, children }: PanelProps) {
  return (
    <div role="tabpanel" id={panelId(id)} aria-labelledby={tabId(id)} hidden={!active}>
      {children}
    </div>
  );
}
