import { ApiError, TemplateInfo, ValidationResponse } from '../types/api';

async function handle<T>(res: Response): Promise<T> {
  if (res.ok) return res.json() as Promise<T>;
  const text = await res.text().catch(() => '');
  const retryAfter = res.headers.get('Retry-After');
  throw new ApiError(
    res.status,
    text || `${res.status} ${res.statusText}`,
    retryAfter ? Number(retryAfter) : undefined,
  );
}

export async function fetchTemplates(): Promise<TemplateInfo[]> {
  const res = await fetch('/api/templates');
  return handle<TemplateInfo[]>(res);
}

export interface ValidateArgs {
  templateId: string;
  templateVersion: string;
  profiles: string[];
  file: File;
  externalEnabled?: boolean;
  leiEnabled?: boolean;
  leiCheckLapsed?: boolean;
  leiCheckName?: boolean;
  leiCheckCountry?: boolean;
  isinEnabled?: boolean;
  isinCheckCurrency?: boolean;
  isinCheckCic?: boolean;
  openfigiApiKey?: string;
}

export async function validateUpload(args: ValidateArgs): Promise<ValidationResponse> {
  const fd = new FormData();
  fd.append('templateId', args.templateId);
  fd.append('templateVersion', args.templateVersion);
  for (const p of args.profiles) fd.append('profiles', p);
  fd.append('file', args.file);
  if (args.externalEnabled) {
    fd.append('externalEnabled', 'true');
    fd.append('leiEnabled', String(args.leiEnabled ?? true));
    fd.append('leiCheckLapsed', String(args.leiCheckLapsed ?? true));
    fd.append('leiCheckName', String(args.leiCheckName ?? false));
    fd.append('leiCheckCountry', String(args.leiCheckCountry ?? false));
    fd.append('isinEnabled', String(args.isinEnabled ?? true));
    fd.append('isinCheckCurrency', String(args.isinCheckCurrency ?? false));
    fd.append('isinCheckCic', String(args.isinCheckCic ?? false));
    if (args.openfigiApiKey) fd.append('openfigiApiKey', args.openfigiApiKey);
  }
  const res = await fetch('/api/validate', { method: 'POST', body: fd });
  return handle<ValidationResponse>(res);
}

export function reportDownloadUrl(reportId: string): string {
  return `/api/report/${reportId}`;
}

export async function fetchHelp(): Promise<string> {
  const res = await fetch('/api/help');
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new ApiError(res.status, text || `${res.status} ${res.statusText}`);
  }
  return res.text();
}

export interface RulesDocEntry {
  slug: string;
  templateId: string;
  templateDisplayName: string;
  version: string;
  label: string;
}

export async function fetchRulesIndex(): Promise<RulesDocEntry[]> {
  const res = await fetch('/api/help/rules');
  if (!res.ok) {
    if (res.status === 404) return [];
    const text = await res.text().catch(() => '');
    throw new ApiError(res.status, text || `${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<RulesDocEntry[]>;
}

export async function fetchRuleDoc(slug: string): Promise<string> {
  const res = await fetch(`/api/help/rules/${encodeURIComponent(slug)}`);
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new ApiError(res.status, text || `${res.status} ${res.statusText}`);
  }
  return res.text();
}

export async function fetchAbout(): Promise<string> {
  const res = await fetch('/api/about');
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new ApiError(res.status, text || `${res.status} ${res.statusText}`);
  }
  return res.text();
}
