import { ProfileInfo } from '../types/api';

interface Props {
  profiles: ProfileInfo[];
  selected: string[];
  onChange: (selected: string[]) => void;
}

export function ProfileSelector({ profiles, selected, onChange }: Props) {
  if (profiles.length === 0) return null;
  const toggle = (code: string) => {
    if (selected.includes(code)) onChange(selected.filter((c) => c !== code));
    else onChange([...selected, code]);
  };
  return (
    <div>
      <label className="mb-2 block text-xs font-semibold uppercase tracking-wide text-slate-500">
        Aktive Profile
      </label>
      <div className="flex flex-wrap gap-2">
        {profiles.map((p) => {
          const isSel = selected.includes(p.code);
          return (
            <button
              key={p.code}
              type="button"
              onClick={() => toggle(p.code)}
              className={
                'rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ' +
                (isSel
                  ? 'border-navy-700 bg-navy-700 text-white'
                  : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-100')
              }
            >
              {p.displayName}
            </button>
          );
        })}
      </div>
      {selected.length === 0 && (
        <p className="mt-2 text-xs text-slate-500">
          Keine Auswahl = alle Profile gleichzeitig auswerten.
        </p>
      )}
    </div>
  );
}
