import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { fetchTemplates, validateUpload, reportDownloadUrl } from './client';
import { ApiError } from '../types/api';

// Minimal mock that matches the bits of Response we use: ok/status/statusText/json/text/headers.get.
function ok(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    statusText: 'OK',
    json: async () => body,
    text: async () => JSON.stringify(body),
    headers: new Headers(),
  } as unknown as Response;
}

function err(status: number, body: string, headers: Record<string, string> = {}): Response {
  return {
    ok: false,
    status,
    statusText: '',
    json: async () => { throw new Error('not json'); },
    text: async () => body,
    headers: new Headers(headers),
  } as unknown as Response;
}

describe('api/client', () => {
  // Vitest's spyOn return type is over-narrow for typeof fetch's overloads, so let TS infer.
  let fetchSpy = vi.spyOn(globalThis, 'fetch');

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });

  afterEach(() => {
    fetchSpy.mockRestore();
  });

  describe('fetchTemplates', () => {
    it('GETs /api/templates and returns the JSON body', async () => {
      fetchSpy.mockResolvedValue(ok([{ id: 'TPT', displayName: 'TPT', versions: [] }]));
      const result = await fetchTemplates();
      expect(fetchSpy).toHaveBeenCalledWith('/api/templates');
      expect(result).toEqual([{ id: 'TPT', displayName: 'TPT', versions: [] }]);
    });

    it('throws ApiError on a 5xx response', async () => {
      fetchSpy.mockResolvedValue(err(503, 'Service Unavailable'));
      await expect(fetchTemplates()).rejects.toBeInstanceOf(ApiError);
      await expect(fetchTemplates()).rejects.toMatchObject({ status: 503 });
    });
  });

  describe('validateUpload', () => {
    const file = new File(['x'], 'sample.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });

    it('POSTs multipart/form-data to /api/validate with all fields', async () => {
      fetchSpy.mockResolvedValue(ok({ summary: { filename: 'sample.xlsx' }, scores: [], findings: [], reportId: 'r' }));
      await validateUpload({
        templateId: 'TPT',
        templateVersion: 'V7.0',
        profiles: ['SOLVENCY_II', 'NW_675'],
        file,
      });

      expect(fetchSpy).toHaveBeenCalledTimes(1);
      const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
      expect(url).toBe('/api/validate');
      expect(init.method).toBe('POST');
      const fd = init.body as FormData;
      expect(fd).toBeInstanceOf(FormData);
      expect(fd.get('templateId')).toBe('TPT');
      expect(fd.get('templateVersion')).toBe('V7.0');
      expect(fd.getAll('profiles')).toEqual(['SOLVENCY_II', 'NW_675']);
      const sent = fd.get('file');
      expect(sent).toBeInstanceOf(File);
      expect((sent as File).name).toBe('sample.xlsx');
    });

    it('maps a 429 response into an ApiError carrying the Retry-After value', async () => {
      fetchSpy.mockResolvedValue(err(429, 'Rate limit exceeded.', { 'Retry-After': '42' }));
      try {
        await validateUpload({ templateId: 'TPT', templateVersion: 'V7.0', profiles: [], file });
        throw new Error('should have thrown');
      } catch (e) {
        expect(e).toBeInstanceOf(ApiError);
        expect((e as ApiError).status).toBe(429);
        expect((e as ApiError).retryAfterSeconds).toBe(42);
        expect((e as ApiError).message).toContain('Rate limit');
      }
    });

    it('maps a 413 response into an ApiError', async () => {
      fetchSpy.mockResolvedValue(err(413, 'Payload Too Large'));
      const p = validateUpload({ templateId: 'TPT', templateVersion: 'V7.0', profiles: [], file });
      await expect(p).rejects.toMatchObject({ status: 413 });
    });

    it('falls back to "<status> <statusText>" when the body is empty', async () => {
      const empty = {
        ok: false, status: 502, statusText: 'Bad Gateway',
        json: async () => { throw new Error('not json'); },
        text: async () => '',
        headers: new Headers(),
      } as unknown as Response;
      fetchSpy.mockResolvedValue(empty);
      const p = validateUpload({ templateId: 'TPT', templateVersion: 'V7.0', profiles: [], file });
      await expect(p).rejects.toMatchObject({ status: 502, message: '502 Bad Gateway' });
    });
  });

  describe('reportDownloadUrl', () => {
    it('builds the report download URL', () => {
      expect(reportDownloadUrl('abc-123')).toBe('/api/report/abc-123');
    });
  });
});
