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
}

export async function validateUpload(args: ValidateArgs): Promise<ValidationResponse> {
  const fd = new FormData();
  fd.append('templateId', args.templateId);
  fd.append('templateVersion', args.templateVersion);
  for (const p of args.profiles) fd.append('profiles', p);
  fd.append('file', args.file);
  const res = await fetch('/api/validate', { method: 'POST', body: fd });
  return handle<ValidationResponse>(res);
}

export function reportDownloadUrl(reportId: string): string {
  return `/api/report/${reportId}`;
}
