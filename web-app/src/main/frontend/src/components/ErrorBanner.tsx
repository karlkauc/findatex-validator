import { AlertTriangle } from 'lucide-react';
import { ApiError } from '../types/api';

interface Props {
  error: unknown;
}

export function ErrorBanner({ error }: Props) {
  const { title, body } = describe(error);
  return (
    <div className="flex items-start gap-3 rounded-md border border-red-200 bg-red-50 p-4">
      <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" aria-hidden="true" />
      <div className="text-sm">
        <p className="font-semibold text-red-900">{title}</p>
        <p className="mt-1 text-red-800">{body}</p>
      </div>
    </div>
  );
}

function describe(error: unknown): { title: string; body: string } {
  if (error instanceof ApiError) {
    if (error.status === 429) {
      const wait = error.retryAfterSeconds
        ? ` Try again in about ${error.retryAfterSeconds} seconds.`
        : ' Please try again later.';
      return { title: 'Rate limit exceeded', body: error.message + wait };
    }
    if (error.status === 413) {
      return {
        title: 'File too large',
        body: 'The upload limit is 25 MB. Please choose a smaller file.',
      };
    }
    if (error.status === 400) {
      return { title: 'Invalid request', body: error.message };
    }
    return { title: `HTTP ${error.status}`, body: error.message };
  }
  if (error instanceof Error) return { title: 'Error', body: error.message };
  return { title: 'Error', body: 'Unknown validation error.' };
}
