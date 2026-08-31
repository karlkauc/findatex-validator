import { beforeEach, describe, expect, it, vi } from 'vitest';
import { __testables } from './pageView';

const { campaignOf, externalReferrer } = __testables;

describe('campaignOf', () => {
  it('reads utm_source, falling back to ref', () => {
    expect(campaignOf('?utm_source=linkedin')).toBe('linkedin');
    expect(campaignOf('?ref=conference-handout')).toBe('conference-handout');
  });

  it('prefers utm_source when both are present', () => {
    expect(campaignOf('?ref=a&utm_source=b')).toBe('b');
  });

  it('is undefined for organic traffic', () => {
    expect(campaignOf('')).toBeUndefined();
    expect(campaignOf('?template=TPT')).toBeUndefined();
  });
});

describe('externalReferrer', () => {
  it('keeps a referrer from another site', () => {
    expect(externalReferrer('https://www.linkedin.com/feed/', 'https://www.findatex-validator.eu'))
      .toBe('https://www.linkedin.com/feed/');
  });

  it('drops internal navigation and an absent referrer', () => {
    // Our own pages are not a traffic source.
    expect(externalReferrer('https://www.findatex-validator.eu/rules', 'https://www.findatex-validator.eu'))
      .toBeUndefined();
    expect(externalReferrer('', 'https://www.findatex-validator.eu')).toBeUndefined();
  });
});

describe('reportPageView', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
  });

  it('posts one beacon and never a second one', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const { reportPageView } = await import('./pageView');
    reportPageView();
    // StrictMode / fast refresh can call this again in the same page load.
    reportPageView();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/page-view');
    expect(init.method).toBe('POST');
    expect(init.keepalive).toBe(true);
    expect(JSON.parse(init.body)).toMatchObject({ path: '/' });
  });

  it('swallows a failed beacon', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('offline'));
    vi.stubGlobal('fetch', fetchMock);

    const { reportPageView } = await import('./pageView');
    // A counter must never surface an error to the user.
    expect(() => reportPageView()).not.toThrow();
    await Promise.resolve();
  });
});
