import { Download } from 'lucide-react';
import { ValidationResponse } from '../types/api';
import { reportDownloadUrl } from '../api/client';
import { ScoreBadge } from './ScoreBadge';
import { FindingsTable } from './FindingsTable';

interface Props {
  result: ValidationResponse;
}

export function ResultPanel({ result }: Props) {
  const overall = result.scores.find((s) => s.dimension === 'OVERALL');
  const others = result.scores.filter((s) => s.dimension !== 'OVERALL');

  return (
    <div className="space-y-6">
      <div className="card">
        <div className="card-header flex flex-wrap items-center justify-between gap-3">
          <span>Validation result</span>
          <a
            href={reportDownloadUrl(result.reportId)}
            className="btn-primary text-xs"
            download={`findatex-report-${result.summary.filename}.xlsx`}
          >
            <Download className="h-4 w-4" /> Download Excel report
          </a>
        </div>
        <div className="card-body space-y-4">
          <SummaryGrid result={result} />
        </div>
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Scores</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {overall && (
            <ScoreBadge label="Overall score" percentage={overall.percentage} prominent />
          )}
          {others.map((s) => (
            <ScoreBadge key={s.dimension} label={prettyDimension(s.dimension)} percentage={s.percentage} />
          ))}
        </div>
      </div>

      <FindingsTable findings={result.findings} />
    </div>
  );
}

function SummaryGrid({ result }: { result: ValidationResponse }) {
  const s = result.summary;
  const items: { label: string; value: string }[] = [
    { label: 'File',         value: s.filename },
    { label: 'Template',     value: `${s.templateId} ${s.templateVersion}` },
    { label: 'Rows',         value: String(s.rowCount) },
    { label: 'Findings',     value: `${s.findingCount} (${s.errorCount} E / ${s.warningCount} W / ${s.infoCount} I)` },
    { label: 'Validated at', value: new Date(s.generatedAt).toLocaleString() },
  ];
  return (
    <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
      {items.map((it) => (
        <div key={it.label}>
          <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{it.label}</dt>
          <dd className="mt-1 truncate text-sm font-medium text-slate-900">{it.value}</dd>
        </div>
      ))}
    </dl>
  );
}

function prettyDimension(d: string): string {
  switch (d) {
    case 'MANDATORY_COMPLETENESS':  return 'Mandatory';
    case 'FORMAT_CONFORMANCE':      return 'Format';
    case 'CLOSED_LIST_CONFORMANCE': return 'Closed-List';
    case 'CROSS_FIELD_CONSISTENCY': return 'Cross-Field';
    case 'PROFILE_COMPLETENESS':    return 'Profile';
    default: return d;
  }
}
