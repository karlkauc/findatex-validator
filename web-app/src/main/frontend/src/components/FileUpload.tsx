import { useDropzone } from 'react-dropzone';
import { Upload, X, FileSpreadsheet } from 'lucide-react';

const MAX_BYTES = 25 * 1024 * 1024; // mirror quarkus.http.limits.max-body-size=25M

interface Props {
  file: File | null;
  onFileChange: (file: File | null) => void;
}

export function FileUpload({ file, onFileChange }: Props) {
  const { getRootProps, getInputProps, isDragActive, isDragReject, fileRejections } = useDropzone({
    multiple: false,
    maxSize: MAX_BYTES,
    accept: {
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx', '.xlsm'],
      'text/csv': ['.csv'],
      'text/plain': ['.tsv', '.txt'],
    },
    onDrop: (accepted) => {
      if (accepted.length > 0) onFileChange(accepted[0]);
    },
  });

  const reject = isDragReject || fileRejections.length > 0;

  if (file) {
    return (
      <div className="flex items-center gap-3 rounded-md border border-slate-300 bg-white px-4 py-3">
        <FileSpreadsheet className="h-5 w-5 shrink-0 text-navy-700" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-slate-900">{file.name}</p>
          <p className="text-xs text-slate-500">{formatSize(file.size)}</p>
        </div>
        <button
          type="button"
          onClick={() => onFileChange(null)}
          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
          aria-label="Datei entfernen"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    );
  }

  return (
    <div
      {...getRootProps()}
      className={
        'flex cursor-pointer flex-col items-center justify-center rounded-md border-2 border-dashed px-6 py-10 text-center transition-colors ' +
        (reject
          ? 'border-red-300 bg-red-50'
          : isDragActive
          ? 'border-navy-500 bg-navy-50'
          : 'border-slate-300 bg-slate-50 hover:border-slate-400 hover:bg-slate-100')
      }
    >
      <input {...getInputProps()} />
      <Upload className="mb-3 h-8 w-8 text-slate-400" aria-hidden="true" />
      <p className="text-sm font-medium text-slate-700">
        {isDragActive ? 'Loslassen, um Datei zu wählen' : 'Datei hierher ziehen oder klicken'}
      </p>
      <p className="mt-1 text-xs text-slate-500">.xlsx, .xlsm oder .csv — max 25&nbsp;MB</p>
      {fileRejections.length > 0 && (
        <p className="mt-3 text-xs text-red-600">
          {fileRejections[0].errors[0]?.message ?? 'Datei abgelehnt'}
        </p>
      )}
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
