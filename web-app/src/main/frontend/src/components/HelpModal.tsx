import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2, X } from 'lucide-react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { fetchHelp, fetchRuleDoc, fetchRulesIndex } from '../api/client';

interface Props {
  open: boolean;
  onClose: () => void;
}

const OPERATOR_GUIDE_KEY = '__operator_guide__';

export function HelpModal({ open, onClose }: Props) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const [activeKey, setActiveKey] = useState<string>(OPERATOR_GUIDE_KEY);

  const help = useQuery({
    queryKey: ['help'],
    queryFn: fetchHelp,
    enabled: open && activeKey === OPERATOR_GUIDE_KEY,
    staleTime: Infinity,
    gcTime: Infinity,
  });

  const rules = useQuery({
    queryKey: ['help', 'rules'],
    queryFn: fetchRulesIndex,
    enabled: open,
    staleTime: Infinity,
    gcTime: Infinity,
  });

  const ruleDoc = useQuery({
    queryKey: ['help', 'rules', activeKey],
    queryFn: () => fetchRuleDoc(activeKey),
    enabled: open && activeKey !== OPERATOR_GUIDE_KEY,
    staleTime: Infinity,
    gcTime: Infinity,
  });

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

  const sidebarItems = useMemo(() => {
    const items: { key: string; label: string; group: string }[] = [
      { key: OPERATOR_GUIDE_KEY, label: 'Operator guide', group: 'General' },
    ];
    for (const r of rules.data ?? []) {
      items.push({
        key: r.slug,
        label: `${r.templateDisplayName} ${r.version}`,
        group: 'Validation rules',
      });
    }
    return items;
  }, [rules.data]);

  if (!open) return null;

  const isOperatorGuide = activeKey === OPERATOR_GUIDE_KEY;
  const currentLoading = isOperatorGuide ? help.isLoading : ruleDoc.isLoading;
  const currentError = isOperatorGuide ? help.isError : ruleDoc.isError;
  const currentSource = isOperatorGuide ? help.data : ruleDoc.data;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="help-modal-title"
      className="fixed inset-0 z-50 flex items-stretch justify-center bg-slate-900/60 px-2 py-4 sm:px-6 sm:py-10"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="flex max-h-full w-full max-w-6xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-5 py-3">
          <h2 id="help-modal-title" className="text-sm font-semibold text-slate-800">
            FinDatEx Validator — Help
          </h2>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label="Close help"
            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-200 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="flex flex-1 overflow-hidden">
          <nav className="hidden w-60 shrink-0 overflow-y-auto border-r border-slate-200 bg-slate-50 p-3 text-sm sm:block">
            <SidebarGroup
              title="General"
              items={sidebarItems.filter((i) => i.group === 'General')}
              activeKey={activeKey}
              onSelect={setActiveKey}
            />
            <SidebarGroup
              title="Validation rules"
              items={sidebarItems.filter((i) => i.group === 'Validation rules')}
              activeKey={activeKey}
              onSelect={setActiveKey}
              emptyHint={rules.isLoading ? 'Loading…' : 'No rules reference bundled.'}
            />
          </nav>

          <div className="flex-1 overflow-y-auto px-6 py-5">
            <div className="mb-3 sm:hidden">
              <select
                aria-label="Help section"
                value={activeKey}
                onChange={(e) => setActiveKey(e.target.value)}
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800"
              >
                {sidebarItems.map((i) => (
                  <option key={i.key} value={i.key}>
                    {i.group === 'General' ? i.label : `${i.label} rules`}
                  </option>
                ))}
              </select>
            </div>

            {currentLoading && (
              <div className="flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading…
              </div>
            )}
            {currentError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                Could not load this section.
              </div>
            )}
            {currentSource && <HelpMarkdown source={currentSource} />}
          </div>
        </div>
      </div>
    </div>
  );
}

interface SidebarItem {
  key: string;
  label: string;
}

function SidebarGroup({
  title,
  items,
  activeKey,
  onSelect,
  emptyHint,
}: {
  title: string;
  items: SidebarItem[];
  activeKey: string;
  onSelect: (k: string) => void;
  emptyHint?: string;
}) {
  return (
    <div className="mb-4">
      <h3 className="mb-1 px-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
        {title}
      </h3>
      {items.length === 0 ? (
        <p className="px-2 text-xs italic text-slate-400">{emptyHint}</p>
      ) : (
        <ul className="space-y-0.5">
          {items.map((i) => (
            <li key={i.key}>
              <button
                type="button"
                onClick={() => onSelect(i.key)}
                className={
                  'block w-full rounded-md px-2 py-1.5 text-left text-sm transition-colors ' +
                  (activeKey === i.key
                    ? 'bg-navy-100 font-medium text-navy-900'
                    : 'text-slate-700 hover:bg-slate-200')
                }
              >
                {i.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const MARKDOWN_COMPONENTS: Components = {
  h1: (p) => <h1 className="mb-3 mt-2 text-xl font-semibold text-slate-900" {...p} />,
  h2: (p) => <h2 className="mb-2 mt-6 border-b border-slate-200 pb-1 text-lg font-semibold text-slate-900" {...p} />,
  h3: (p) => <h3 className="mb-2 mt-5 text-base font-semibold text-slate-800" {...p} />,
  h4: (p) => <h4 className="mb-2 mt-4 text-sm font-semibold text-slate-800" {...p} />,
  p:  (p) => <p className="mb-3" {...p} />,
  ul: (p) => <ul className="mb-3 list-disc space-y-1 pl-5" {...p} />,
  ol: (p) => <ol className="mb-3 list-decimal space-y-1 pl-5" {...p} />,
  li: (p) => <li className="text-slate-700" {...p} />,
  a:  ({ href, ...rest }) => (
    <a
      href={href}
      target={href?.startsWith('http') ? '_blank' : undefined}
      rel={href?.startsWith('http') ? 'noreferrer noopener' : undefined}
      className="text-navy-700 underline hover:text-navy-900"
      {...rest}
    />
  ),
  code: ({ className, children, ...rest }) => {
    const inline = !className;
    if (inline) {
      return (
        <code className="rounded bg-slate-100 px-1 py-0.5 font-mono text-[12px] text-slate-800" {...rest}>
          {children}
        </code>
      );
    }
    return (
      <code className={'font-mono text-xs ' + (className ?? '')} {...rest}>
        {children}
      </code>
    );
  },
  pre: (p) => (
    <pre className="mb-3 overflow-x-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs leading-relaxed text-slate-800" {...p} />
  ),
  blockquote: (p) => (
    <blockquote className="mb-3 border-l-4 border-navy-200 bg-navy-50/40 px-3 py-1 text-slate-700" {...p} />
  ),
  table: (p) => (
    <div className="mb-4 overflow-x-auto">
      <table className="min-w-full border border-slate-200 text-xs" {...p} />
    </div>
  ),
  thead: (p) => <thead className="bg-slate-100" {...p} />,
  th: (p) => <th className="border border-slate-200 px-3 py-1.5 text-left font-semibold text-slate-800" {...p} />,
  td: (p) => <td className="border border-slate-200 px-3 py-1.5 align-top text-slate-700" {...p} />,
  hr: (p) => <hr className="my-6 border-slate-200" {...p} />,
  strong: (p) => <strong className="font-semibold text-slate-900" {...p} />,
};

function HelpMarkdown({ source }: { source: string }) {
  return (
    <article className="text-sm leading-relaxed text-slate-700">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
        {source}
      </ReactMarkdown>
    </article>
  );
}
