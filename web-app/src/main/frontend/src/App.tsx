import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, GitFork, HelpCircle, Info, Loader2, ShieldCheck, Sparkles } from 'lucide-react';
import {
  fetchBuildInfo,
  fetchFeedbackConfig,
  fetchNewsletterConfig,
  fetchQuickFeedbackConfig,
  fetchRateLimitStatus,
  fetchSampleFile,
  fetchTemplates,
  validateUpload,
} from './api/client';
import { TemplatePicker } from './components/TemplatePicker';
import { ProfileSelector } from './components/ProfileSelector';
import { FileUpload } from './components/FileUpload';
import { ResultPanel } from './components/ResultPanel';
import { ErrorBanner } from './components/ErrorBanner';
import { ExternalValidationToggle } from './components/ExternalValidationToggle';
import { DesktopDownloadLink } from './components/DesktopDownloadLink';
import { HelpModal } from './components/HelpModal';
import { AboutModal } from './components/AboutModal';
import { RATE_LIMIT_QUERY_KEY, RateLimitBadge } from './components/RateLimitBadge';
import { QuotaExhaustedNotice } from './components/QuotaExhaustedNotice';
import { NewsletterSignup } from './components/NewsletterSignup';
import { QuickFeedback } from './components/QuickFeedback';
import { RateLimitStatus, ValidationResponse } from './types/api';

const GITHUB_URL = 'https://github.com/karlkauc/findatex-validator';

export default function App() {
  const queryClient = useQueryClient();
  const templatesQuery = useQuery({ queryKey: ['templates'], queryFn: fetchTemplates });
  const rateLimitQuery = useQuery({
    queryKey: RATE_LIMIT_QUERY_KEY,
    queryFn: fetchRateLimitStatus,
    refetchInterval: 30_000,
    staleTime: 0,
  });

  const buildInfoQuery = useQuery({
    queryKey: ['build-info'],
    queryFn: fetchBuildInfo,
    staleTime: Infinity,
    retry: false,
  });

  const feedbackConfigQuery = useQuery({
    queryKey: ['feedback-config'],
    queryFn: fetchFeedbackConfig,
    staleTime: Infinity,
    retry: false,
  });

  const newsletterConfigQuery = useQuery({
    queryKey: ['newsletter-config'],
    queryFn: fetchNewsletterConfig,
    staleTime: Infinity,
    retry: false,
  });

  const quickFeedbackConfigQuery = useQuery({
    queryKey: ['quick-feedback-config'],
    queryFn: fetchQuickFeedbackConfig,
    staleTime: Infinity,
    retry: false,
  });

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
  // The header is fixed, not sticky: `position: sticky` can only stick within
  // its own containing block, which ends where the React app ends — the static
  // landing content below #root lives outside it, so the header would slide
  // away there. Fixed takes it out of flow, so the spacer below reserves its
  // height; measured rather than hardcoded because the bar wraps to two rows on
  // narrow screens.
  const headerRef = useRef<HTMLElement>(null);
  const [headerHeight, setHeaderHeight] = useState(0);
  useEffect(() => {
    const el = headerRef.current;
    if (!el) return;
    const update = () => setHeaderHeight(el.offsetHeight);
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const [sampleLoading, setSampleLoading] = useState(false);
  const [sampleError, setSampleError] = useState<unknown>(null);
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
    onSuccess: (data) => {
      setResult(data);
      queryClient.invalidateQueries({ queryKey: RATE_LIMIT_QUERY_KEY });
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: RATE_LIMIT_QUERY_KEY });
    },
  });

  const quotaExhausted = isQuotaExhausted(rateLimitQuery.data);
  const canSubmit = useMemo(
    () => Boolean(file && templateId && version && !validateMutation.isPending && !quotaExhausted),
    [file, templateId, version, validateMutation.isPending, quotaExhausted],
  );

  // The override exists for the sample-file action: React state updates are not
  // visible synchronously, so loading the file and validating it in one click
  // has to pass the file and version explicitly.
  const submit = (override?: { file: File; version: string; profiles: string[] }) => {
    const activeFile = override?.file ?? file;
    const activeVersion = override?.version ?? version;
    const activeProfiles = override?.profiles ?? profiles;
    if (!activeFile || !activeVersion) return;
    setResult(null);
    setSampleError(null);
    const useExternal = externalEnabled && (currentTemplate?.externalAvailable ?? false);
    validateMutation.mutate({
      templateId,
      templateVersion: activeVersion,
      profiles: activeProfiles,
      file: activeFile,
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

  // A first-time visitor evaluating the tool rarely has a TPT/EET/EMT/EPT file
  // to hand, and going to find one is where they leave. One click loads the
  // bundled example and validates it.
  const runSample = async () => {
    const sample = currentTemplate?.sample;
    if (!sample) return;
    setSampleError(null);
    setSampleLoading(true);
    try {
      const sampleFile = await fetchSampleFile(sample);
      setFile(sampleFile);
      // The fixture is generated for one specific spec version; validating it
      // against another would report findings that are artefacts of that.
      setVersion(sample.version);
      setProfiles([]);
      submit({ file: sampleFile, version: sample.version, profiles: [] });
    } catch (err) {
      setSampleError(err);
    } finally {
      setSampleLoading(false);
    }
  };

  return (
    <div className="min-h-screen">
      <header
        ref={headerRef}
        className="fixed inset-x-0 top-0 z-30 bg-gradient-to-b from-navy-700 to-navy-800 text-white shadow-md"
      >
        <div className="mx-auto flex max-w-[1600px] flex-wrap items-center gap-3 px-6 py-5 lg:px-8">
          <ShieldCheck className="h-7 w-7" aria-hidden="true" />
          <div className="flex-1">
            <h1 className="text-lg font-semibold tracking-tight">FinDatEx Validator</h1>
            <p className="text-xs text-navy-100/90">
              TPT · EET · EMT · EPT — quality and conformance against the official FinDatEx specs.
            </p>
          </div>
          {quickFeedbackConfigQuery.data?.enabled && <QuickFeedback templateId={templateId} />}
          <RateLimitBadge />
          <DesktopDownloadLink url={rateLimitQuery.data?.desktopDownloadUrl} />
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 rounded-md border border-white/20 bg-white/10 px-3 py-1.5 text-xs font-medium text-white hover:bg-white/20 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
          >
            <GitFork className="h-4 w-4" aria-hidden="true" />
            GitHub
          </a>
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

      {/* Reserves the fixed header's space in the document flow. */}
      <div style={{ height: headerHeight }} aria-hidden="true" />

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
                    onClick={() => submit()}
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
                  {currentTemplate?.sample && (
                    <button
                      type="button"
                      className="flex w-full items-center justify-center gap-2 rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-500"
                      disabled={sampleLoading || validateMutation.isPending || quotaExhausted}
                      onClick={runSample}
                    >
                      {sampleLoading ? (
                        <>
                          <Loader2 className="h-4 w-4 animate-spin" />
                          Loading example…
                        </>
                      ) : (
                        <>
                          <Sparkles className="h-4 w-4" aria-hidden="true" />
                          No file at hand? Try an example
                        </>
                      )}
                    </button>
                  )}
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
                    For daily validations without web upload,{' '}
                    {rateLimitQuery.data?.desktopDownloadUrl ? (
                      <a
                        href={rateLimitQuery.data.desktopDownloadUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 font-medium text-navy-700 underline underline-offset-2 hover:text-navy-500"
                      >
                        <Download className="h-3.5 w-3.5" aria-hidden="true" />
                        download the desktop app
                      </a>
                    ) : (
                      <>the desktop app is available</>
                    )}{' '}
                    — your data never leaves your machine.
                  </li>
                </ul>
              </div>
            </section>

            <section className="space-y-5">
              <QuotaExhaustedNotice />
              {sampleError != null && <ErrorBanner error={sampleError} />}
              {validateMutation.isError && <ErrorBanner error={validateMutation.error} />}
              {result ? (
                <ResultPanel
                  result={result}
                  githubRepo={feedbackConfigQuery.data?.githubRepo ?? null}
                  appVersion={
                    buildInfoQuery.data?.version
                      ? `web v${buildInfoQuery.data.version}`
                      : 'web'
                  }
                />
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
          {buildInfoQuery.data && (
            <div>
              FinDatEx Validator{' '}
              {buildInfoQuery.data.version && <>v{buildInfoQuery.data.version}</>}
              {buildInfoQuery.data.commit && (
                <>
                  {' · '}
                  <span className="font-mono">
                    {buildInfoQuery.data.commit}
                    {buildInfoQuery.data.dirty ? '-dirty' : ''}
                  </span>
                </>
              )}
              {buildInfoQuery.data.buildTime && (
                <> · built {formatBuildDate(buildInfoQuery.data.buildTime)}</>
              )}
            </div>
          )}
          <div>
            — Source &amp; Desktop-Build:&nbsp;
            <a
              href={GITHUB_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="font-mono underline hover:text-slate-700"
            >
              com.findatex/findatex-validator
            </a>
          </div>
          {newsletterConfigQuery.data?.enabled && <NewsletterSignup />}
        </div>
      </footer>
    </div>
  );
}

function formatBuildDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toISOString().slice(0, 10); // YYYY-MM-DD
}

function isQuotaExhausted(status: RateLimitStatus | undefined): boolean {
  // Treat unknown / not-yet-loaded status as "ok to submit" so the very first render
  // doesn't disable the button before the status query has resolved.
  return Boolean(status && status.remaining <= 0);
}
