import { describe, it, expect } from 'vitest';
import { issueBody, type FalsePositiveReport } from './githubIssue';

function sample(overrides: Partial<FalsePositiveReport> = {}): FalsePositiveReport {
  return {
    templateId: 'TPT',
    templateVersion: 'V7',
    severity: 'ERROR',
    ruleId: 'PRESENCE/TPT-F1',
    profile: 'INSURANCE_PRIIPS',
    fieldNum: '12',
    fieldName: 'Portfolio Currency',
    value: 'XYZ',
    message: 'bad value',
    portfolioId: 'LU000',
    portfolioName: 'Some Fund',
    valuationDate: '2026-03-31',
    instrumentCode: 'DE000',
    instrumentName: 'Some Bond',
    valuationWeight: '0.1234',
    appVersion: '1.0.0',
    userComment: 'comment',
    ...overrides,
  };
}

describe('issueBody', () => {
  it('escapes backslashes so they cannot neutralise pipe escaping', () => {
    // "C:\temp|x" must render as "C:\\temp\|x" — a raw backslash left
    // unescaped would turn the following escaped pipe back into a cell break.
    const body = issueBody(sample({ message: 'C:\\temp|x' }));
    expect(body).toContain('| Message | C:\\\\temp\\|x |');
  });
});
