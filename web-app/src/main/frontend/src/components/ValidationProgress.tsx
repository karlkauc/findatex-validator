import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';

interface Props {
  fileName?: string;
  /** Where the file came from, e.g. "TPT Converter" for a hand-over. */
  source?: string | null;
}

/**
 * Shown in the result area while a file is validated. A large TPT file takes a
 * minute or more on the server; without this the area kept saying "Ready to
 * validate" and only the button in the sidebar showed that anything ran.
 */
export function ValidationProgress({ fileName, source }: Props) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const started = Date.now();
    const timer = window.setInterval(() => setSeconds(Math.floor((Date.now() - started) / 1000)), 1000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <div className="card" role="status" aria-live="polite">
      <div className="card-body flex items-start gap-4 text-sm text-slate-600">
        <Loader2 className="mt-0.5 h-6 w-6 shrink-0 animate-spin text-navy-600" aria-hidden="true" />
        <div className="min-w-0">
          <p className="text-base font-medium text-slate-800">
            Validating{fileName ? <> <span className="break-all">{fileName}</span></> : null}…
          </p>
          {source && <p className="mt-1">Received from {source}.</p>}
          <p className="mt-2">
            The file is being read and checked. Large files can take a minute or two; the results appear here.
          </p>
          <p className="mt-2 tabular-nums text-slate-500" aria-hidden="true">
            {seconds} s
          </p>
        </div>
      </div>
    </div>
  );
}
