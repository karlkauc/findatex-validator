import { useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2, X } from 'lucide-react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { fetchAbout } from '../api/client';

interface Props {
  open: boolean;
  onClose: () => void;
}

export function AboutModal({ open, onClose }: Props) {
  const closeRef = useRef<HTMLButtonElement>(null);

  const about = useQuery({
    queryKey: ['about'],
    queryFn: fetchAbout,
    enabled: open,
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

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="about-modal-title"
      className="fixed inset-0 z-50 flex items-stretch justify-center bg-slate-900/60 px-2 py-4 sm:px-6 sm:py-10"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="flex max-h-full w-full max-w-4xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 bg-slate-50 px-5 py-3">
          <h2 id="about-modal-title" className="text-sm font-semibold text-slate-800">
            FinDatEx Validator — About
          </h2>
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label="Close about"
            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-200 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {about.isLoading && (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading about…
            </div>
          )}
          {about.isError && (
            <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
              Could not load the about content.
            </div>
          )}
          {about.data && <AboutMarkdown source={about.data} />}
        </div>
      </div>
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

function AboutMarkdown({ source }: { source: string }) {
  return (
    <article className="text-sm leading-relaxed text-slate-700">
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
        {source}
      </ReactMarkdown>
    </article>
  );
}
