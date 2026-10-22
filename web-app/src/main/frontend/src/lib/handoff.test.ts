import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { clearHandoffParam, handoffSource, receiveHandoff, rejectReason } from './handoff';

const SENDER = 'https://converter.status20.net';

function fileMessage(overrides: Record<string, unknown> = {}) {
  return {
    type: 'findatex-validator:file',
    name: 'fund_TPT_V7.xlsx',
    content: new Uint8Array([0x50, 0x4b, 3, 4]).buffer,
    templateId: 'TPT',
    templateVersion: 'V7.0',
    ...overrides,
  };
}

describe('handoffSource', () => {
  it('accepts only known senders', () => {
    expect(handoffSource('?from=tpt-converter')).toBe('tpt-converter');
    expect(handoffSource('?from=evil')).toBeNull();
    expect(handoffSource('')).toBeNull();
  });
});

describe('rejectReason', () => {
  it('allows spreadsheets and CSV up to 25 MB', () => {
    expect(rejectReason('a.xlsx', 10)).toBeNull();
    expect(rejectReason('A.CSV', 10)).toBeNull();
    expect(rejectReason('a.xml', 10)).toMatch(/only/);
    expect(rejectReason('a.xlsx', 26 * 1024 * 1024)).toMatch(/25 MB/);
  });
});

describe('receiveHandoff', () => {
  let opener: { postMessage: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    opener = { postMessage: vi.fn() };
    Object.defineProperty(window, 'opener', { value: opener, configurable: true });
  });
  afterEach(() => {
    Object.defineProperty(window, 'opener', { value: null, configurable: true });
  });

  const dispatch = (data: unknown, origin = SENDER, source: unknown = opener) =>
    window.dispatchEvent(new MessageEvent('message', { data, origin, source: source as Window }));

  it('announces readiness to the converter', () => {
    receiveHandoff(vi.fn(), vi.fn());
    expect(opener.postMessage).toHaveBeenCalledWith({ type: 'findatex-validator:ready' }, SENDER);
  });

  it('does nothing without an opener', () => {
    Object.defineProperty(window, 'opener', { value: null, configurable: true });
    const onFile = vi.fn();
    receiveHandoff(onFile, vi.fn());
    dispatch(fileMessage());
    expect(onFile).not.toHaveBeenCalled();
  });

  it('delivers the file with template and version, once', () => {
    const onFile = vi.fn();
    receiveHandoff(onFile, vi.fn());
    dispatch(fileMessage());
    dispatch(fileMessage({ name: 'second.xlsx' }));
    expect(onFile).toHaveBeenCalledTimes(1);
    const handoff = onFile.mock.calls[0][0];
    expect(handoff.file.name).toBe('fund_TPT_V7.xlsx');
    expect(handoff.file.size).toBe(4);
    expect(handoff.templateId).toBe('TPT');
    expect(handoff.templateVersion).toBe('V7.0');
  });

  it('ignores other origins, other windows and malformed messages', () => {
    const onFile = vi.fn();
    receiveHandoff(onFile, vi.fn());
    dispatch(fileMessage(), 'https://evil.example');
    dispatch(fileMessage(), SENDER, {});
    dispatch(fileMessage({ content: 'not a buffer' }));
    dispatch({ type: 'something-else' });
    expect(onFile).not.toHaveBeenCalled();
  });

  it('rejects files the upload would refuse', () => {
    const onFile = vi.fn();
    const onReject = vi.fn();
    receiveHandoff(onFile, onReject);
    dispatch(fileMessage({ name: 'fund.xml' }));
    expect(onFile).not.toHaveBeenCalled();
    expect(onReject).toHaveBeenCalledWith(expect.stringMatching(/fund\.xml/));
  });
});

describe('clearHandoffParam', () => {
  it('removes from and keeps the rest of the query', () => {
    window.history.replaceState(null, '', '/?from=tpt-converter&utm_source=tpt-converter#x');
    clearHandoffParam();
    expect(window.location.search).toBe('?utm_source=tpt-converter');
    expect(window.location.hash).toBe('#x');
  });
});
