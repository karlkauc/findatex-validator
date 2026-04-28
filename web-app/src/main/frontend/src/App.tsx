import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Loader2, ShieldCheck } from 'lucide-react';
import { fetchTemplates, validateUpload } from './api/client';
import { TemplatePicker } from './components/TemplatePicker';
import { ProfileSelector } from './components/ProfileSelector';
import { FileUpload } from './components/FileUpload';
import { ResultPanel } from './components/ResultPanel';
import { ErrorBanner } from './components/ErrorBanner';
import { ExternalValidationToggle } from './components/ExternalValidationToggle';
import { ValidationResponse } from './types/api';

export default function App() {
  const templatesQuery = useQuery({ queryKey: ['templates'], queryFn: fetchTemplates });

  const [templateId, setTemplateId] = useState<string>('TPT');
  const [version, setVersion] = useState<string>('');
  const [profiles, setProfiles] = useState<string[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ValidationResponse | null>(null);

  // Defaults match AppSettings.defaults() in core: master OFF, LEI lapsed ON, the rest OFF.
  const [externalEnabled, setExternalEnabled] = useState(false);
  const [leiEnabled, setLeiEnabled] = useState(true);
  const [leiCheckLapsed, setLeiCheckLapsed] = useState(true);
  const [leiCheckName, setLeiCheckName] = useState(false);
  const [leiCheckCountry, setLeiCheckCountry] = useState(false);
  const [isinEnabled, setIsinEnabled] = useState(true);
  const [isinCheckCurrency, setIsinCheckCurrency] = useState(false);
  const [isinCheckCic, setIsinCheckCic] = useState(false);
  const [openfigiApiKey, setOpenfigiApiKey] = useState('');

  const templates = templatesQuery.data ?? [];
  const currentTemplate = templates.find((t) => t.id === templateId);
  const currentVersion = currentTemplate?.versions.find((v) => v.version === version);

  // Default to the latest version of the selected template; reset profiles when switching.
  useEffect(() => {
    if (!currentTemplate) return;
    if (!version || !currentTemplate.versions.find((v) => v.version === version)) {
      setVersion(currentTemplate.versions[0]?.version ?? '');
    }
  }, [currentTemplate, version]);

  useEffect(() => {
    setProfiles([]);
  }, [templateId, version]);

  const validateMutation = useMutation({
    mutationFn: validateUpload,
    onSuccess: (data) => setResult(data),
  });

  const canSubmit = useMemo(
    () => Boolean(file && templateId && version && !validateMutation.isPending),
    [file, templateId, version, validateMutation.isPending],
  );

  const submit = () => {
    if (!file || !version) return;
    setResult(null);
    const useExternal = externalEnabled && (currentTemplate?.externalAvailable ?? false);
    validateMutation.mutate({
      templateId,
      templateVersion: version,
      profiles,
      file,
      externalEnabled: useExternal,
      leiEnabled,
      leiCheckLapsed,
      leiCheckName,
      leiCheckCountry,
      isinEnabled,
      isinCheckCurrency,
      isinCheckCic,
      openfigiApiKey: openfigiApiKey.trim() || undefined,
    });
    // Don't keep the user-entered key around once it's been sent.
    setOpenfigiApiKey('');
  };

  return (
    <div className="min-h-screen">
      <header className="bg-gradient-to-b from-navy-700 to-navy-800 text-white">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-6 py-5">
          <ShieldCheck className="h-7 w-7" aria-hidden="true" />
          <div>
            <h1 className="text-lg font-semibold tracking-tight">FinDatEx Validator</h1>
            <p className="text-xs text-navy-100/90">
              TPT · EET · EMT · EPT — Quality &amp; Conformance gegen die offiziellen FinDatEx-Specs.
            </p>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl space-y-6 px-6 py-8">
        {templatesQuery.isLoading && (
          <p className="text-sm text-slate-500">Templates laden…</p>
        )}
        {templatesQuery.isError && <ErrorBanner error={templatesQuery.error} />}

        {!templatesQuery.isLoading && !templatesQuery.isError && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
            <section className="space-y-5">
              <div className="card">
                <div className="card-header">Eingabe</div>
                <div className="card-body space-y-5">
                  <TemplatePicker
                    templates={templates}
                    selectedTemplateId={templateId}
                    selectedVersion={version}
                    onTemplateChange={setTemplateId}
                    onVersionChange={setVersion}
                  />
                  {currentVersion && (
                    <ProfileSelector
                      profiles={currentVersion.profiles}
                      selected={profiles}
                      onChange={setProfiles}
                    />
                  )}
                  <ExternalValidationToggle
                    available={currentTemplate?.externalAvailable ?? false}
                    externalEnabled={externalEnabled}
                    leiEnabled={leiEnabled}
                    leiCheckLapsed={leiCheckLapsed}
                    leiCheckName={leiCheckName}
                    leiCheckCountry={leiCheckCountry}
                    isinEnabled={isinEnabled}
                    isinCheckCurrency={isinCheckCurrency}
                    isinCheckCic={isinCheckCic}
                    apiKey={openfigiApiKey}
                    onExternalEnabledChange={setExternalEnabled}
                    onLeiEnabledChange={setLeiEnabled}
                    onLeiCheckLapsedChange={setLeiCheckLapsed}
                    onLeiCheckNameChange={setLeiCheckName}
                    onLeiCheckCountryChange={setLeiCheckCountry}
                    onIsinEnabledChange={setIsinEnabled}
                    onIsinCheckCurrencyChange={setIsinCheckCurrency}
                    onIsinCheckCicChange={setIsinCheckCic}
                    onApiKeyChange={setOpenfigiApiKey}
                  />
                  <FileUpload file={file} onFileChange={setFile} />
                  <button
                    type="button"
                    className="btn-primary w-full"
                    disabled={!canSubmit}
                    onClick={submit}
                  >
                    {validateMutation.isPending ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" />
                        Validiere…
                      </>
                    ) : (
                      'Validieren'
                    )}
                  </button>
                </div>
              </div>

              <div className="rounded-md border border-slate-200 bg-white p-4 text-xs text-slate-500">
                <p className="font-semibold text-slate-700">Hinweise</p>
                <ul className="mt-2 list-disc space-y-1 pl-4">
                  <li>Hochgeladene Dateien werden serverseitig <strong>nicht persistiert</strong> und nach der Antwort sofort gelöscht.</li>
                  <li>Excel-Reports stehen 5 Minuten lang unter einer einmal-gültigen URL bereit.</li>
                  <li>Externe Validierung (GLEIF/OpenFIGI) ist im Web-Modus standardmäßig deaktiviert.</li>
                  <li>
                    Für tägliche Validierungen ohne Web-Upload steht die Desktop-App zum Download
                    bereit — Daten verlassen Ihren Rechner dabei nicht.
                  </li>
                </ul>
              </div>
            </section>

            <section className="space-y-5">
              {validateMutation.isError && <ErrorBanner error={validateMutation.error} />}
              {result ? (
                <ResultPanel result={result} />
              ) : (
                <div className="card">
                  <div className="card-body text-center text-sm text-slate-500">
                    <p className="text-base font-medium text-slate-700">Bereit zur Validierung</p>
                    <p className="mt-2">
                      Wählen Sie Template, Version, Profile und laden Sie eine Datei hoch.
                      Die Ergebnisse erscheinen hier.
                    </p>
                  </div>
                </div>
              )}
            </section>
          </div>
        )}
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-6 py-4 text-xs text-slate-500">
          FinDatEx Validator — Source &amp; Desktop-Build:&nbsp;
          <span className="font-mono">com.findatex/findatex-validator</span>
        </div>
      </footer>
    </div>
  );
}
