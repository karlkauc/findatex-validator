// Drives the web app through a scripted walkthrough with Playwright and dumps
// one screenshot per animation frame plus the manifest build_demo_gif.py
// expects (same shape as DesktopDemoRecorder.java writes).
//
//   node tools/demo/record_web_demo.mjs <base-url> <frames-dir> <sample.xlsx>
//
// Uses the Playwright pinned in the root package.json (installed for the
// scraper). The page is 1100x740 so the GIF lines up with the desktop ones.
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, basename } from 'node:path';

const [baseUrl, framesDir, sampleFile] = process.argv.slice(2);
if (!baseUrl || !framesDir || !sampleFile) {
  console.error('usage: record_web_demo.mjs <base-url> <frames-dir> <sample.xlsx>');
  process.exit(2);
}
mkdirSync(framesDir, { recursive: true });

const W = 1100, H = 740;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });

const manifest = [];
let frameNo = 0;
let caption = '';
let highlight = null;        // Locator or null
let cur = { x: 900, y: 600 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function frame(ms) {
  const file = `frame-${String(frameNo++).padStart(4, '0')}.png`;
  await page.screenshot({ path: join(framesDir, file), type: 'png' });
  let hl = null;
  if (highlight) {
    const b = await highlight.boundingBox().catch(() => null);
    if (b) hl = [b.x, b.y, b.width, b.height];
  }
  manifest.push({ file, part: 'app-demo', ms, caption, cursor: [cur.x, cur.y], highlight: hl });
}
const hold = async (ms) => { await sleep(120); await frame(ms); };

async function center(locator) {
  const b = await locator.boundingBox();
  if (!b) throw new Error('element has no box: ' + locator);
  return { x: b.x + b.width / 2, y: b.y + b.height / 2 };
}

async function moveTo(locator, frames = 10) {
  const t = await center(locator);
  const s = { ...cur };
  for (let i = 1; i <= frames; i++) {
    const p = i / frames;
    const e = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2;
    cur = { x: s.x + (t.x - s.x) * e, y: s.y + (t.y - s.y) * e };
    await page.mouse.move(cur.x, cur.y);
    await frame(50);
  }
}

async function click() {
  await page.mouse.click(cur.x, cur.y);
  await sleep(150);
  await frame(250);
}

/** Smoothly scroll the window so `locator` sits `margin` px below the fixed header. */
async function scrollTo(locator, frames = 7, margin = 16) {
  const headerH = await page.evaluate(() => document.querySelector('header')?.getBoundingClientRect().height ?? 0);
  const from = await page.evaluate(() => window.scrollY);
  let to = 0;
  if (locator) {
    const b = await locator.boundingBox();
    to = Math.max(0, from + b.y - headerH - margin);
  }
  for (let i = 1; i <= frames; i++) {
    const p = i / frames;
    const e = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2;
    const y = from + (to - from) * e;
    await page.evaluate((yy) => window.scrollTo(0, yy), y);
    await frame(50);
  }
}

// ===== Script ===========================================================

await page.goto(baseUrl, { waitUntil: 'networkidle' });
await page.getByRole('button', { name: 'Validate' }).waitFor();
await page.mouse.move(cur.x, cur.y);
await sleep(500);

caption = 'Step 1 — Pick the template (TPT, EET, EMT or EPT) and the spec version.';
await hold(1500);
const picker = page.locator('button', { hasText: /^EET$/ }).first();
await moveTo(picker, 12); await click(); await hold(800);
await moveTo(page.locator('button', { hasText: /^TPT$/ }).first(), 8); await click(); await hold(500);
const version = page.locator('#version-select');
highlight = version;
await moveTo(version, 8);
const v7 = await version.locator('option', { hasText: 'V7.0' }).first().getAttribute('value');
await version.selectOption(v7);
await hold(1400);
highlight = null;

caption = 'Step 2 — Keep the profiles that apply, then drop your .xlsx / .xlsm / .csv file (max 25 MB).';
const profiles = page.locator('button[aria-pressed]', { hasText: 'SST' }).first();
await moveTo(profiles, 10); await click(); await hold(900);
const dropzone = page.locator('input[type=file]').locator('..');
highlight = dropzone;
await moveTo(dropzone, 10);
await hold(800);
await page.setInputFiles('input[type=file]', sampleFile);
await hold(300);
highlight = page.getByText(basename(sampleFile)).locator('..').locator('..');
await hold(1300);
highlight = null;

caption = 'No file at hand? "Try an example" loads a bundled demo delivery and validates it in one click.';
const tryExample = page.getByRole('button', { name: /Try an example/ });
highlight = tryExample;
await moveTo(tryExample, 8);
await hold(1600);
highlight = null;

caption = 'Step 3 — Validate. The file is processed in memory and discarded as soon as the response is sent.';
const validate = page.getByRole('button', { name: 'Validate' });
highlight = validate;
await moveTo(validate, 8);
await hold(400);
await click();
highlight = null;
const resultHeader = page.getByText('Validation result');
for (let i = 0; i < 60; i++) {
  if (await resultHeader.isVisible().catch(() => false)) break;
  await frame(120);
  await sleep(60);
}
await hold(1500);

caption = 'Result — the quality score overall and per profile, plus the Excel report to download (single-use link, 5 minutes).';
const download = page.getByRole('link', { name: /Download Excel report/ });
highlight = download;
await moveTo(download, 10);
await hold(1400);
highlight = page.locator('#scores-grid');
await scrollTo(page.locator('#scores-grid'), 7, 60);
await hold(2000);
highlight = null;

caption = 'Findings — filter by severity or group by error to read one line per rule with its occurrence count.';
const findingsCard = page.locator('.card', { hasText: /^Findings/ }).first();
await scrollTo(findingsCard, 7, 16);
highlight = findingsCard;
const group = page.getByRole('button', { name: 'Group by error' });
await moveTo(group, 10);
await click();
await hold(2200);
highlight = null;

caption = 'Wrong finding? "Report" opens a pre-filled GitHub issue that you review and submit yourself.';
const report = page.getByRole('button', { name: 'Report' }).first();
// The Report column sits at the far right of the wide table: slide the table's
// own scroll container to the end so the button is on screen.
for (let i = 1; i <= 8; i++) {
  await report.evaluate((el, p) => {
    let c = el.parentElement;
    while (c && c.scrollWidth <= c.clientWidth + 1) c = c.parentElement;
    if (c) c.scrollLeft = (c.scrollWidth - c.clientWidth) * p;
  }, i / 8);
  await frame(50);
}
highlight = report;
await moveTo(report, 10);
await hold(1800);
highlight = null;

caption = 'No login. 30 uploads per hour per IP — and for confidential data the desktop app is one click away.';
await scrollTo(null, 7, 0);
const header = page.locator('header');
highlight = header.locator('a[title*="desktop app"]').first();
if (!(await highlight.count())) highlight = header;
await moveTo(highlight, 10);
await hold(2200);
highlight = null;

writeFileSync(join(framesDir, 'manifest.json'), JSON.stringify(manifest, null, 0));
console.log(`[demo] ${frameNo} frames written to ${framesDir}`);
await browser.close();
