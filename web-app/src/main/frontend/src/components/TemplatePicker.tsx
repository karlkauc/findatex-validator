import { TemplateInfo, VersionInfo } from '../types/api';

interface Props {
  templates: TemplateInfo[];
  selectedTemplateId: string;
  selectedVersion: string;
  onTemplateChange: (id: string) => void;
  onVersionChange: (version: string) => void;
}

export function TemplatePicker({
  templates,
  selectedTemplateId,
  selectedVersion,
  onTemplateChange,
  onVersionChange,
}: Props) {
  const current = templates.find((t) => t.id === selectedTemplateId);

  return (
    <div className="space-y-3">
      <div>
        <label className="mb-2 block text-xs font-semibold uppercase tracking-wide text-slate-500">
          Template
        </label>
        <div className="inline-flex rounded-md border border-slate-300 bg-white p-1">
          {templates.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => onTemplateChange(t.id)}
              className={
                'rounded px-4 py-1.5 text-sm font-medium transition-colors ' +
                (t.id === selectedTemplateId
                  ? 'bg-navy-700 text-white shadow-sm'
                  : 'text-slate-600 hover:bg-slate-100')
              }
            >
              {t.displayName}
            </button>
          ))}
        </div>
      </div>

      {current && (
        <div>
          <label htmlFor="version-select" className="mb-2 block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Version
          </label>
          <select
            id="version-select"
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-navy-500 focus:outline-none focus:ring-1 focus:ring-navy-500"
            value={selectedVersion}
            onChange={(e) => onVersionChange(e.target.value)}
          >
            {current.versions.map((v: VersionInfo) => (
              <option key={v.version} value={v.version}>
                {v.label}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
}
