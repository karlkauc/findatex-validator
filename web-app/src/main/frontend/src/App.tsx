import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { HelpCircle, Info, Loader2, ShieldCheck } from 'lucide-react';
import { fetchTemplates, validateUpload } from './api/client';
import { TemplatePicker } from './components/TemplatePicker';
import { ProfileSelector } from './components/ProfileSelector';
import { FileUpload } from './components/FileUpload';
import { ResultPanel } from './components/ResultPanel';
import { ErrorBanner } from './components/ErrorBanner';
import { ExternalValidationToggle } from './components/ExternalValidationToggle';
import { HelpModal } from './components/HelpModal';
import { AboutModal } from './components/AboutModal';
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
  const [helpOpen, setHelpOpen] = useState(false);
  const [aboutOpen, setAboutOpen] = useState(false);

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
        <div className="mx-auto flex max-w-[1600px] items-center gap-3 px-6 py-5 lg:px-8">
          <ShieldCheck className="h-7 w-7" aria-hidden="true" />
          <div className="flex-1">
            <h1 className="text-lg font-semibold tracking-tight">FinDatEx Validator</h1>
            <p className="text-xs text-navy-100/90">
              TPT · EET · EMT · EPT — quality and conformance against the official FinDatEx specs.
            </p>
          </div>
          <button
            type="button"
            onClick={() => setHelpOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-md border border-white/20 bg-white/10 px-3 py-1.5 text-xs font-medium text-white hover:bg-white/20 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
            aria-haspopup="dialog"
          >
            <HelpCircle className="h-4 w-4" aria-hidden="true" />
            Help
          </button>
          <button
            type="button"
            onClick={() => setAboutOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-md border border-white/20 bg-white/10 px-3 py-1.5 text-xs font-medium text-white hover:bg-white/20 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
            aria-haspopup="dialog"
          >
            <Info className="h-4 w-4" aria-hidden="true" />
            About
          </button>
        </div>
      </header>

      <HelpModal open={helpOpen} onClose={() => setHelpOpen(false)} />
      <AboutModal open={aboutOpen} onClose={() => setAboutOpen(false)} />

      <main className="mx-auto max-w-[1600px] space-y-6 px-6 py-8 lg:px-8">
        {templatesQuery.isLoading && (
          <p className="text-sm text-slate-500">Loading templates…</p>
        )}
        {templatesQuery.isError && <ErrorBanner error={templatesQuery.error} />}

        {!templatesQuery.isLoading && !templatesQuery.isError && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
            <section className="space-y-5">
              <div className="card">
                <div className="card-header">Input</div>
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
                    aria-busy={validateMutation.isPending}
                    onClick={submit}
                  >
                    {validateMutation.isPending ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" />
                        Validating…
                      </>
                    ) : (
                      'Validate'
                    )}
                  </button>
                  <div aria-live="polite" aria-atomic="true" className="sr-only">
                    {validateMutation.isPending ? 'Validation in progress' : ''}
                  </div>
                </div>
              </div>

              <div className="rounded-md border border-slate-200 bg-white p-4 text-xs text-slate-500">
                <p className="font-semibold text-slate-700">Notes</p>
                <ul className="mt-2 list-disc space-y-1 pl-4">
                  <li>Uploaded files are <strong>not persisted</strong> on the server and are deleted immediately after the response.</li>
                  <li>Excel reports are available for 5 minutes via a single-use URL.</li>
                  <li>External validation (GLEIF/OpenFIGI) is disabled by default in the web UI.</li>
                  <li>
                    For daily validations without web upload, the desktop app is available
                    for download — your data never leaves your machine.
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
                    <p className="text-base font-medium text-slate-700">Ready to validate</p>
                    <p className="mt-2">
                      Choose a template, version, and profiles, then upload a file.
                      Results will appear here.
                    </p>
                  </div>
                </div>
              )}
            </section>
          </div>
        )}
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-[1600px] px-6 py-4 text-xs text-slate-500 lg:px-8">
          FinDatEx Validator — Source &amp; Desktop-Build:&nbsp;
          <span className="font-mono">com.findatex/findatex-validator</span>
        </div>
      </footer>
    </div>
  );
}
