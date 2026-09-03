import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { UI_STORAGE_PREFIX, usePersistedBoolean } from './usePersistedState';

describe('usePersistedBoolean', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts from the initial value when nothing is stored', () => {
    const { result } = renderHook(() => usePersistedBoolean('x', true));
    expect(result.current[0]).toBe(true);
  });

  it('reads a persisted value', () => {
    window.localStorage.setItem(UI_STORAGE_PREFIX + 'x', '0');
    const { result } = renderHook(() => usePersistedBoolean('x', true));
    expect(result.current[0]).toBe(false);
  });

  it('writes on change and accepts an updater function', () => {
    const { result } = renderHook(() => usePersistedBoolean('x', false));
    act(() => result.current[1](true));
    expect(result.current[0]).toBe(true);
    expect(window.localStorage.getItem(UI_STORAGE_PREFIX + 'x')).toBe('1');
    act(() => result.current[1]((v) => !v));
    expect(result.current[0]).toBe(false);
    expect(window.localStorage.getItem(UI_STORAGE_PREFIX + 'x')).toBe('0');
  });

  it('falls back to the initial value when storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const { result } = renderHook(() => usePersistedBoolean('x', true));
    expect(result.current[0]).toBe(true);
    act(() => result.current[1](false));
    expect(result.current[0]).toBe(false);
  });
});
