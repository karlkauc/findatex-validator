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
        ? ` Versuchen Sie es in ca. ${error.retryAfterSeconds} Sekunden erneut.`
        : ' Bitte versuchen Sie es später erneut.';
      return { title: 'Rate-Limit erreicht', body: error.message + wait };
    }
    if (error.status === 413) {
      return {
        title: 'Datei zu groß',
        body: 'Das Limit für Uploads liegt bei 25 MB. Bitte wählen Sie eine kleinere Datei.',
      };
    }
    if (error.status === 400) {
      return { title: 'Ungültige Anfrage', body: error.message };
    }
    return { title: `HTTP ${error.status}`, body: error.message };
  }
  if (error instanceof Error) return { title: 'Fehler', body: error.message };
  return { title: 'Fehler', body: 'Unbekannter Fehler bei der Validierung.' };
}
