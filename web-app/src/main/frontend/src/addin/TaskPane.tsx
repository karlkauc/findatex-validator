import { useEffect, useMemo, useState } from 'react';
import { fetchAnnotatedSource, fetchTemplates, validateUpload } from '../api/client';
import { ApiError, FindingDto, Severity, TemplateInfo, ValidationResponse } from '../types/api';
import {
  ExcelMark,
  addressByFinding,
  fieldLabel,
  toExcelMarks,
  unplacedFindings,
} from './marks';
import {
  SavedFills,
  applyMarkers,
  clearMarkers,
  firstSheetName,
  readWorkbookFile,
  selectCell,
} from './workbook';

type Phase = 'idle' | 'reading' | 'validating' | 'marking' | 'done';

const PHASE_TEXT: Record<Phase, string> = {
  idle: '',
  reading: 'Reading the workbook…',
  validating: 'Validating…',
  marking: 'Marking cells…',
  done: '',
};

const SEVERITY_BADGE: Record<Severity, string> = {
  ERROR: 'bg-red-100 text-red-800',
  WARNING: 'bg-amber-100 text-amber-800',
  INFO: 'bg-blue-100 text-blue-800',
};

/** Shown in the list; the full text is in the title tooltip. */
const MAX_LIST = 500;

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 429) {
      return `Rate limit reached.${e.retryAfterSeconds ? ` Try again in about ${e.retryAfterSeconds} s.` : ''}`;
    }
    if (e.status === 413) return 'The workbook is larger than the 25 MB upload limit.';
    return e.message;
  }
  return e instanceof Error ? e.message : String(e);
}

export default function TaskPane() {
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [templateId, setTemplateId] = useState('');
  const [version, setVersion] = useState('');
  const [profiles, setProfiles] = useState<string[]>([]);
  const [phase, setPhase] = useState<Phase>('idle');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ValidationResponse | null>(null);
  const [marks, setMarks] = useState<ExcelMark[]>([]);
  const [saved, setSaved] = useState<SavedFills>(new Map());
  const [sheet, setSheet] = useState('');
  const [severityFilter, setSeverityFilter] = useState<Severity | 'ALL'>('ALL');

  useEffect(() => {
    fetchTemplates()
      .then((t) => {
        setTemplates(t);
        if (t[0]) setTemplateId(t[0].id);
      })
      .catch((e) => setError(errorText(e)));
    firstSheetName().then(setSheet).catch(() => undefined);
  }, []);

  const template = templates.find((t) => t.id === templateId);
  useEffect(() => {
    if (template && !template.versions.some((v) => v.version === version)) {
      setVersion(template.versions[0]?.version ?? '');
    }
  }, [template, version]);
  useEffect(() => setProfiles([]), [templateId, version]);
  const versionInfo = template?.versions.find((v) => v.version === version);

  const busy = phase === 'reading' || phase === 'validating' || phase === 'marking';

  async function run() {
    setError(null);
    try {
      // Undo the previous run's colours first, so they are not uploaded as data
      // formatting and the saved originals stay the pre-add-in state.
      if (saved.size) {
        await clearMarkers(saved);
        setSaved(new Map());
      }
      setMarks([]);
      setResult(null);
      setPhase('reading');
      const name = await firstSheetName();
      setSheet(name);
      const file = await readWorkbookFile();
      setPhase('validating');
      const res = await validateUpload({ templateId, templateVersion: version, profiles, file });
      setResult(res);
      if (res.annotatedSourceAvailable && res.findings.length) {
        setPhase('marking');
        const dto = await fetchAnnotatedSource(res.reportId);
        const m = toExcelMarks(dto, res.findings);
        setMarks(m);
        setSaved(await applyMarkers(m, new Map()));
      }
      setPhase('done');
    } catch (e) {
      setError(errorText(e));
      setPhase('idle');
    }
  }

  async function removeMarkers() {
    setError(null);
    try {
      await clearMarkers(saved);
      setSaved(new Map());
    } catch (e) {
      setError(errorText(e));
    }
  }

  const placed = useMemo(() => addressByFinding(marks), [marks]);
  const unplaced = useMemo(
    () => (result ? unplacedFindings(result.findings, placed) : []),
    [result, placed],
  );
  const visible: [number, FindingDto][] = useMemo(() => {
    if (!result) return [];
    const out: [number, FindingDto][] = [];
    result.findings.forEach((f, i) => {
      if (severityFilter === 'ALL' || f.severity === severityFilter) out.push([i, f]);
    });
    return out;
  }, [result, severityFilter]);
  const overall = result?.scores.find((s) => s.dimension === 'OVERALL');

  return (
    <div className="p-3 text-sm text-slate-800">
      <h1 className="text-base font-semibold text-[#173049]">FinDatEx Validator</h1>
      <p className="mt-1 text-xs text-slate-500">
        Checks the first sheet{sheet ? ` (“${sheet}”)` : ''} of this workbook. The file is sent
        to findatex-validator.eu for validation and not kept.
      </p>

      <div className="mt-3 space-y-2">
        <label className="block">
          <span className="text-xs font-medium">Template</span>
          <select
            className="mt-0.5 w-full rounded border border-slate-300 p-1"
            value={templateId}
            disabled={busy}
            onChange={(e) => setTemplateId(e.target.value)}
          >
            {templates.map((t) => (
              <option key={t.id} value={t.id}>
                {t.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-medium">Version</span>
          <select
            className="mt-0.5 w-full rounded border border-slate-300 p-1"
            value={version}
            disabled={busy}
            onChange={(e) => setVersion(e.target.value)}
          >
            {template?.versions.map((v) => (
              <option key={v.version} value={v.version}>
                {v.label}
              </option>
            ))}
          </select>
        </label>
        {versionInfo && versionInfo.profiles.length > 0 && (
          <fieldset>
            <legend className="text-xs font-medium">Profiles (none = default)</legend>
            {versionInfo.profiles.map((p) => (
              <label key={p.code} className="mr-3 inline-flex items-center gap-1 text-xs">
                <input
                  type="checkbox"
                  disabled={busy}
                  checked={profiles.includes(p.code)}
                  onChange={(e) =>
                    setProfiles((cur) =>
                      e.target.checked ? [...cur, p.code] : cur.filter((c) => c !== p.code),
                    )
                  }
                />
                {p.displayName}
              </label>
            ))}
          </fieldset>
        )}
      </div>

      <div className="mt-3 flex gap-2">
        <button
          type="button"
          className="rounded bg-[#173049] px-3 py-1.5 font-medium text-white disabled:opacity-50"
          disabled={busy || !templateId || !version}
          onClick={run}
        >
          Validate
        </button>
        <button
          type="button"
          className="rounded border border-slate-300 px-3 py-1.5 disabled:opacity-50"
          disabled={busy || saved.size === 0}
          onClick={removeMarkers}
        >
          Remove markers
        </button>
      </div>
      {busy && <p className="mt-2 text-xs text-slate-600">{PHASE_TEXT[phase]}</p>}
      {error && <p className="mt-2 rounded bg-red-50 p-2 text-xs text-red-800">{error}</p>}

      {result && (
        <section className="mt-4">
          <p className="text-xs">
            {result.summary.rowCount} rows ·{' '}
            {overall ? <strong>score {Math.round(overall.percentage)}%</strong> : null} ·{' '}
            <span className="text-red-700">{result.summary.errorCount} errors</span> ·{' '}
            <span className="text-amber-700">{result.summary.warningCount} warnings</span> ·{' '}
            <span className="text-blue-700">{result.summary.infoCount} info</span>
          </p>
          {saved.size > 0 && (
            <p className="mt-1 text-xs text-slate-500">
              {marks.length} cells marked. The colours are saved with the workbook unless you
              remove them first.
            </p>
          )}
          {!result.annotatedSourceAvailable && result.findings.length > 0 && (
            <p className="mt-1 text-xs text-amber-700">
              File too large to mark cells; the list below still shows every finding.
            </p>
          )}

          <div className="mt-2 flex gap-1 text-xs">
            {(['ALL', 'ERROR', 'WARNING', 'INFO'] as const).map((s) => (
              <button
                key={s}
                type="button"
                className={`rounded px-2 py-0.5 ${severityFilter === s ? 'bg-slate-800 text-white' : 'bg-slate-100'}`}
                onClick={() => setSeverityFilter(s)}
              >
                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          <ul className="mt-2 divide-y divide-slate-100">
            {visible.slice(0, MAX_LIST).map(([i, f]) => {
              const addr = placed.get(i);
              return (
                <li key={i}>
                  <button
                    type="button"
                    className="w-full py-1.5 text-left hover:bg-slate-50 disabled:cursor-default"
                    disabled={!addr}
                    title={f.message}
                    onClick={() => addr && selectCell(addr).catch((e) => setError(errorText(e)))}
                  >
                    <span className={`mr-1 rounded px-1 text-[10px] font-semibold ${SEVERITY_BADGE[f.severity]}`}>
                      {f.severity}
                    </span>
                    <span className="font-mono text-xs">{addr ?? 'file'}</span>{' '}
                    <span className="text-xs text-slate-500">{f.ruleId}</span>
                    <span className="block text-xs">
                      {fieldLabel(f) ? `${fieldLabel(f)}: ` : ''}
                      {f.message}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
          {visible.length > MAX_LIST && (
            <p className="mt-1 text-xs text-slate-500">
              Showing the first {MAX_LIST} of {visible.length}. Filter by severity to see more.
            </p>
          )}
          {unplaced.length > 0 && marks.length > 0 && (
            <p className="mt-2 text-xs text-slate-500">
              {unplaced.length} finding(s) concern the file or a fund as a whole and have no cell.
            </p>
          )}
        </section>
      )}
    </div>
  );
}
