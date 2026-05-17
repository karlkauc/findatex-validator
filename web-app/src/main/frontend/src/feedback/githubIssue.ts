// TypeScript mirror of
// core/src/main/java/com/findatex/validator/feedback/GitHubIssueLink.java —
// keep the two in sync (title format, body layout, slug rules, truncation).
// Same convention as formatWeight()'s mirror note in FindingsTable.tsx.

export interface FalsePositiveReport {
  templateId: string;
  templateVersion: string;
  severity: string;
  ruleId: string;
  profile: string | null;
  fieldNum: string | null;
  fieldName: string | null;
  value: string | null;
  message: string;
  portfolioId: string | null;
  portfolioName: string | null;
  valuationDate: string | null;
  instrumentCode: string | null;
  instrumentName: string | null;
  valuationWeight: string | null;
  appVersion: string;
  userComment: string;
}

const MAX_URL_LENGTH = 7000;
const TRUNCATION_MARKER = ' …[truncated]';

export function isValidRepoSlug(slug: string | null | undefined): boolean {
  if (slug == null) return false;
  const s = slug.trim();
  if (s === '' || s.includes('..')) return false;
  return /^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/.test(s);
}

function notBlank(s: string | null | undefined): boolean {
  return s != null && s.trim() !== '';
}

function blankToDash(s: string | null | undefined): string {
  return notBlank(s) ? (s as string).trim() : '—';
}

function join(a: string | null | undefined, b: string | null | undefined): string {
  const ha = notBlank(a);
  const hb = notBlank(b);
  if (ha && hb) return `${(a as string).trim()} — ${(b as string).trim()}`;
  if (ha) return (a as string).trim();
  if (hb) return (b as string).trim();
  return '';
}

export function issueTitle(r: FalsePositiveReport): string {
  let t = `[False positive] ${blankToDash(r.ruleId)}`;
  if (notBlank(r.fieldNum) || notBlank(r.fieldName)) {
    t += ` · field ${blankToDash(r.fieldNum)}`;
    if (notBlank(r.fieldName)) t += ` ${r.fieldName}`;
  }
  return t;
}

function row(label: string, value: string | null | undefined): string {
  return `| ${label} | ${blankToDash(value).replace(/\|/g, '\\|').replace(/\n/g, ' ')} |\n`;
}

export function issueBody(r: FalsePositiveReport): string {
  let b = '**Why this is a false positive**\n\n';
  b += notBlank(r.userComment)
    ? r.userComment.trim()
    : '_(no explanation provided — please describe why this finding is wrong)_';
  b += '\n\n---\n\n';
  b += '**Finding context** (auto-filled from the validator)\n\n';
  b += '| Field | Value |\n|---|---|\n';
  b += row('Template', join(r.templateId, r.templateVersion));
  b += row('Severity', r.severity);
  b += row('Rule', r.ruleId);
  b += row('Profile', r.profile);
  b += row('Field', join(r.fieldNum, r.fieldName));
  b += row('Reported value', r.value);
  b += row('Message', r.message);
  b += row('Fund', join(r.portfolioId, r.portfolioName));
  b += row('Valuation date', r.valuationDate);
  b += row('Instrument', join(r.instrumentCode, r.instrumentName));
  b += row('Weight', r.valuationWeight);
  b += row('App version', r.appVersion);
  b +=
    '\n> ⚠️ Submitting this issue publishes the values above on GitHub. ' +
    'Remove or redact anything confidential before pressing **Submit**.';
  return b;
}

function enc(s: string): string {
  // encodeURIComponent already encodes spaces as %20 and newlines as %0A.
  return encodeURIComponent(s);
}

function compose(base: string, r: FalsePositiveReport): string {
  return `${base}&title=${enc(issueTitle(r))}&body=${enc(issueBody(r))}`;
}

function trim(s: string, chars: number): string {
  const keep = Math.max(0, s.length - Math.max(chars, 1) - TRUNCATION_MARKER.length);
  return s.slice(0, keep) + TRUNCATION_MARKER;
}

export function issueUrl(repoSlug: string, report: FalsePositiveReport): string {
  if (!isValidRepoSlug(repoSlug)) {
    throw new Error(`Invalid GitHub repo slug: ${repoSlug}`);
  }
  const base = `https://github.com/${repoSlug.trim()}/issues/new?labels=false-positive`;
  let r = report;
  let url = compose(base, r);
  while (url.length > MAX_URL_LENGTH) {
    const over = url.length - MAX_URL_LENGTH;
    if (r.userComment.length > TRUNCATION_MARKER.length) {
      r = { ...r, userComment: trim(r.userComment, over) };
    } else if (r.message.length > TRUNCATION_MARKER.length) {
      r = { ...r, message: trim(r.message, over) };
    } else {
      break;
    }
    url = compose(base, r);
  }
  return url;
}
