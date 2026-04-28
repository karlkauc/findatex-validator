import { ChangeEvent } from 'react';

export interface ExternalValidationState {
  externalEnabled: boolean;
  leiEnabled: boolean;
  leiCheckLapsed: boolean;
  leiCheckName: boolean;
  leiCheckCountry: boolean;
  isinEnabled: boolean;
  isinCheckCurrency: boolean;
  isinCheckCic: boolean;
  apiKey: string;
}

interface Props extends ExternalValidationState {
  available: boolean;
  onExternalEnabledChange: (v: boolean) => void;
  onLeiEnabledChange: (v: boolean) => void;
  onLeiCheckLapsedChange: (v: boolean) => void;
  onLeiCheckNameChange: (v: boolean) => void;
  onLeiCheckCountryChange: (v: boolean) => void;
  onIsinEnabledChange: (v: boolean) => void;
  onIsinCheckCurrencyChange: (v: boolean) => void;
  onIsinCheckCicChange: (v: boolean) => void;
  onApiKeyChange: (v: string) => void;
}

export function ExternalValidationToggle(props: Props) {
  if (!props.available) return null;

  const onCheckbox = (cb: (v: boolean) => void) =>
    (e: ChangeEvent<HTMLInputElement>) => cb(e.target.checked);

  return (
    <div>
      <label className="mb-2 block text-xs font-semibold uppercase tracking-wide text-slate-500">
        Externe Online-Validierung
      </label>

      <CheckRow
        label="GLEIF / OpenFIGI Online-Prüfung aktivieren"
        checked={props.externalEnabled}
        onChange={onCheckbox(props.onExternalEnabledChange)}
        bold
      />

      {props.externalEnabled && (
        <div className="mt-3 space-y-4 rounded-md border border-slate-200 bg-slate-50 p-3">
          <div>
            <CheckRow
              label="LEI gegen GLEIF prüfen"
              checked={props.leiEnabled}
              onChange={onCheckbox(props.onLeiEnabledChange)}
              bold
            />
            <div className="ml-6 mt-2 space-y-1.5">
              <CheckRow
                label="Lapsed-Status"
                checked={props.leiCheckLapsed}
                disabled={!props.leiEnabled}
                onChange={onCheckbox(props.onLeiCheckLapsedChange)}
              />
              <CheckRow
                label="Issuer-Name"
                checked={props.leiCheckName}
                disabled={!props.leiEnabled}
                onChange={onCheckbox(props.onLeiCheckNameChange)}
              />
              <CheckRow
                label="Issuer-Land"
                checked={props.leiCheckCountry}
                disabled={!props.leiEnabled}
                onChange={onCheckbox(props.onLeiCheckCountryChange)}
              />
            </div>
          </div>

          <div>
            <CheckRow
              label="ISIN gegen OpenFIGI prüfen"
              checked={props.isinEnabled}
              onChange={onCheckbox(props.onIsinEnabledChange)}
              bold
            />
            <div className="ml-6 mt-2 space-y-1.5">
              <CheckRow
                label="Währung"
                checked={props.isinCheckCurrency}
                disabled={!props.isinEnabled}
                onChange={onCheckbox(props.onIsinCheckCurrencyChange)}
              />
              <CheckRow
                label="CIC-Konsistenz"
                checked={props.isinCheckCic}
                disabled={!props.isinEnabled}
                onChange={onCheckbox(props.onIsinCheckCicChange)}
              />
            </div>

            <div className="ml-6 mt-3">
              <label
                htmlFor="openfigi-key"
                className="mb-1 block text-xs font-medium text-slate-600"
              >
                OpenFIGI API-Key (optional)
              </label>
              <input
                id="openfigi-key"
                type="password"
                autoComplete="off"
                spellCheck={false}
                disabled={!props.isinEnabled}
                value={props.apiKey}
                onChange={(e) => props.onApiKeyChange(e.target.value)}
                placeholder="Server-Default wird verwendet, wenn leer"
                className="w-full rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs text-slate-800 placeholder:text-slate-500 focus:border-navy-500 focus:outline-none focus:ring-1 focus:ring-navy-500 disabled:bg-slate-100 disabled:text-slate-500"
              />
              <p className="mt-1 text-[11px] text-slate-500">
                Wird nur für diese Validierung verwendet und nicht gespeichert.
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

interface CheckRowProps {
  label: string;
  checked: boolean;
  disabled?: boolean;
  bold?: boolean;
  onChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

function CheckRow({ label, checked, disabled, bold, onChange }: CheckRowProps) {
  return (
    <label
      className={
        'flex items-center gap-2 text-xs ' +
        (disabled ? 'text-slate-500' : bold ? 'font-medium text-slate-800' : 'text-slate-700') +
        (disabled ? '' : ' cursor-pointer')
      }
    >
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={onChange}
        className="h-4 w-4 rounded border-slate-300 text-navy-700 focus:ring-navy-500 disabled:cursor-not-allowed"
      />
      <span>{label}</span>
    </label>
  );
}
