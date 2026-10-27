import { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { ValidationResponse } from '../types/api';
import { reportDownloadUrl } from '../api/client';
import { ScoreBadge } from './ScoreBadge';
import { FindingsTable } from './FindingsTable';
import { PerFundScores } from './PerFundScores';
import { CollapsibleSection } from './CollapsibleSection';
import { TabPanel, TabStrip } from './Tabs';
import { AnnotatedSourceView, JumpTarget } from './AnnotatedSourceView';

interface Props {
  result: ValidationResponse;
  githubRepo?: string | null;
  appVersion: string;
}

export function ResultPanel({ result, githubRepo, appVersion }: Props) {
  const overall = result.scores.find((s) => s.dimension === 'OVERALL');
  const others = result.scores.filter((s) => s.dimension !== 'OVERALL');
  // Tab and jump state are per validation run — App keys this component by reportId.
  const [tab, setTab] = useState<'findings' | 'source'>('findings');
  const [jump, setJump] = useState<JumpTarget | null>(null);
  const showInSource = (findingIndex: number) => {
    setJump({ findingIndex, nonce: Date.now() });
    setTab('source');
  };

  return (
    <div className="space-y-6">
      <div className="card">
        <div className="card-header flex flex-wrap items-center justify-between gap-3">
          <span>Validation result</span>
          <ReportDownload reportId={result.reportId} filename={result.summary.filename} />
        </div>
        <div className="card-body space-y-4">
          <SummaryGrid result={result} />
        </div>
      </div>

      <CollapsibleSection title="Scores" storageKey="scores" panelId="scores-grid">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          {overall && (
            <ScoreBadge label="Overall score" percentage={overall.percentage} prominent />
          )}
          {others.map((s) => (
            <ScoreBadge key={s.dimension} label={prettyDimension(s.dimension)} percentage={s.percentage} />
          ))}
        </div>
      </CollapsibleSection>

      <PerFundScores perFundScores={result.perFundScores ?? []} />

      <div className="space-y-3">
        <TabStrip
          ariaLabel="Result views"
          active={tab}
          onChange={setTab}
          tabs={[
            { id: 'findings', label: `Findings (${result.findings.length})` },
            { id: 'source', label: 'Annotated Source' },
          ]}
        />
        <TabPanel id="findings" active={tab === 'findings'}>
          <FindingsTable
            findings={result.findings}
            githubRepo={githubRepo}
            templateId={result.summary.templateId}
            templateVersion={result.summary.templateVersion}
            appVersion={appVersion}
            onShowInSource={showInSource}
          />
        </TabPanel>
        <TabPanel id="source" active={tab === 'source'}>
          <AnnotatedSourceView
            reportId={result.reportId}
            available={result.annotatedSourceAvailable ?? false}
            findings={result.findings}
            active={tab === 'source'}
            jump={jump}
          />
        </TabPanel>
      </div>
    </div>
  );
}

// The server keeps the Excel report for 5 minutes and hands it out once. Expire the
// button a little early: the server clock started before the response arrived.
const REPORT_TTL_MS = 5 * 60_000 - 15_000;

/**
 * Download link that knows the report's lifetime. A plain link to an expired or
 * already fetched report makes the browser save a broken file without a word, so
 * after the first click or the TTL it turns into a note instead.
 */
function ReportDownload({ reportId, filename }: { reportId: string; filename: string }) {
  const [state, setState] = useState<'ready' | 'downloaded' | 'expired'>('ready');
  useEffect(() => {
    const t = window.setTimeout(() => setState((s) => (s === 'ready' ? 'expired' : s)), REPORT_TTL_MS);
    return () => window.clearTimeout(t);
  }, []);

  if (state !== 'ready') {
    return (
      <span role="status" className="text-xs font-normal normal-case tracking-normal text-slate-500">
        {state === 'downloaded'
          ? 'Excel report downloaded — it can be fetched once. Validate the file again for another copy.'
          : 'The Excel report has expired — it is kept for 5 minutes. Validate the file again to download it.'}
      </span>
    );
  }
  return (
    <a
      href={reportDownloadUrl(reportId)}
      className="btn-primary text-xs"
      download={`findatex-report-${filename}.xlsx`}
      // Defer the swap so the browser still follows this click.
      onClick={() => window.setTimeout(() => setState('downloaded'), 0)}
    >
      <Download className="h-4 w-4" /> Download Excel report
    </a>
  );
}

function SummaryGrid({ result }: { result: ValidationResponse }) {
  const s = result.summary;
  const items: { label: string; value: string }[] = [
    { label: 'File',         value: s.filename },
    { label: 'Template',     value: `${s.templateId} ${s.templateVersion}` },
    { label: 'Rows',         value: String(s.rowCount) },
    // The score is a per-cell error rate, so it stays high even on a badly
    // broken file. This is the number that separates "a few bad rows" from
    // "nothing in here is usable".
    { label: 'Rows without errors', value: `${s.cleanRowCount} / ${s.rowCount}` },
    { label: 'Findings',     value: `${s.findingCount} (${s.errorCount} E / ${s.warningCount} W / ${s.infoCount} I)` },
    { label: 'Validated at', value: new Date(s.generatedAt).toLocaleString() },
  ];
  return (
    <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
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
