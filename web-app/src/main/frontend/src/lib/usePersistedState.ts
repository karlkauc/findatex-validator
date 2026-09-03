import { useEffect, useState } from 'react';

export const UI_STORAGE_PREFIX = 'findatex.ui.';

type Updater = boolean | ((prev: boolean) => boolean);

function read(key: string): boolean | null {
  try {
    const raw = window.localStorage.getItem(UI_STORAGE_PREFIX + key);
    if (raw === '1') return true;
    if (raw === '0') return false;
  } catch {
    // Private mode, disabled storage, quota — fall through to the default.
  }
  return null;
}

/**
 * Boolean UI preference remembered per browser (collapsed panels and the like).
 * Storage is best-effort: every access is wrapped, so a browser that blocks
 * localStorage behaves like plain useState.
 */
export function usePersistedBoolean(
  key: string,
  initial: boolean,
): [boolean, (next: Updater) => void] {
  const [value, setValue] = useState<boolean>(() => read(key) ?? initial);

  useEffect(() => {
    try {
      window.localStorage.setItem(UI_STORAGE_PREFIX + key, value ? '1' : '0');
    } catch {
      // Ignore — nothing to persist to.
    }
  }, [key, value]);

  return [value, setValue];
}
