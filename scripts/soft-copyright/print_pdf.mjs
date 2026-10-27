// Print an HTML file to PDF through Chromium's DevTools protocol.
// Usage: node print_pdf.mjs <input.html> <output.pdf> <options.json>
//
// options.json fields (all optional, inches unless noted):
//   paperWidth, paperHeight, marginTop, marginBottom, marginLeft, marginRight,
//   displayHeaderFooter, headerTemplate, footerTemplate, printBackground,
//   preferCSSPageSize, scale
//
// The templates may use Chromium's <span class="pageNumber"> /
// <span class="totalPages"> placeholders.
import { spawn } from 'node:child_process';
import { writeFileSync, mkdtempSync, existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [, , htmlArg, pdfArg, optArg] = process.argv;
if (!htmlArg || !pdfArg) {
  console.error('usage: node print_pdf.mjs <input.html> <output.pdf> [options.json]');
  process.exit(2);
}

const htmlPath = resolve(htmlArg);
const pdfPath = resolve(pdfArg);
const options = optArg ? JSON.parse(readFileSync(resolve(optArg), 'utf8')) : {};

const CANDIDATES = [
  process.env.CHROME_PATH,
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
].filter(Boolean);
const browser = CANDIDATES.find(p => existsSync(p));
if (!browser) {
  console.error('no Edge/Chrome executable found; set CHROME_PATH');
  process.exit(3);
}

const PORT = 19222 + (process.pid % 500);
const profile = mkdtempSync(join(tmpdir(), 'ihomy-pdf-'));
const child = spawn(browser, [
  '--headless=new',
  '--disable-gpu',
  '--no-first-run',
  '--no-default-browser-check',
  '--disable-extensions',
  '--allow-file-access-from-files',
  `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${profile}`,
  'about:blank',
], { stdio: 'ignore' });

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function cdpTarget() {
  for (let i = 0; i < 150; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/list`);
      if (r.ok) {
        const list = await r.json();
        const page = list.find(t => t.type === 'page');
        if (page) return page;
      }
    } catch {}
    await sleep(100);
  }
  throw new Error('DevTools endpoint not reachable');
}

const target = await cdpTarget();
const ws = new WebSocket(target.webSocketDebuggerUrl);
let seq = 0;
const pending = new Map();
const waiters = new Map();

ws.addEventListener('message', ev => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    const { res, rej } = pending.get(msg.id);
    pending.delete(msg.id);
    if (msg.error) rej(new Error(JSON.stringify(msg.error)));
    else res(msg.result);
    return;
  }
  if (msg.method) {
    const list = waiters.get(msg.method) || [];
    waiters.delete(msg.method);
    list.forEach(fn => fn(msg.params));
  }
});

const send = (method, params = {}) => new Promise((res, rej) => {
  const id = ++seq;
  pending.set(id, { res, rej });
  ws.send(JSON.stringify({ id, method, params }));
});

const once = (method, timeoutMs = 30000) => new Promise((res, rej) => {
  const timer = setTimeout(() => rej(new Error(`timeout waiting ${method}`)), timeoutMs);
  const list = waiters.get(method) || [];
  list.push(params => { clearTimeout(timer); res(params); });
  waiters.set(method, list);
});

try {
  await new Promise((res, rej) => {
    ws.addEventListener('open', res);
    ws.addEventListener('error', () => rej(new Error('websocket error')));
  });
  await send('Page.enable');
  const loaded = once('Page.loadEventFired');
  await send('Page.navigate', { url: pathToFileURL(htmlPath).href });
  await loaded;
  // Let web fonts, images and layout settle before pagination.
  await sleep(800);

  if (options.checkOverflow) {
    const probe = await send('Runtime.evaluate', {
      returnByValue: true,
      expression: `(() => {
        const rows = [];
        document.querySelectorAll('.page').forEach((pg, i) => {
          const issues = [];
          if (pg.scrollWidth > pg.clientWidth + 1) issues.push('page-x');
          if (pg.scrollHeight > pg.clientHeight + 1) issues.push('page-y');
          const code = pg.querySelector('.code');
          if (code) {
            if (code.scrollWidth > code.clientWidth + 1)
              issues.push('code-x ' + code.scrollWidth + '>' + code.clientWidth);
            const bottom = code.getBoundingClientRect().bottom;
            const pageBottom = pg.getBoundingClientRect().bottom;
            if (bottom > pageBottom - 10) issues.push('code-bottom ' + Math.round(bottom) + '>' + Math.round(pageBottom));
            const lines = (code.textContent.match(/\\n/g) || []).length + 1;
            if (lines < 50) issues.push('lines ' + lines);
          }
          rows.push({ page: i + 1, issues });
        });
        return rows;
      })()`,
    });
    const bad = (probe.result?.value || []).filter(r => r.issues.length);
    if (bad.length) {
      console.error('OVERFLOW: ' + JSON.stringify(bad.slice(0, 10)));
      throw new Error(bad.length + ' page(s) overflow');
    }
  }

  const params = {
    paperWidth: options.paperWidth ?? 8.27,
    paperHeight: options.paperHeight ?? 11.69,
    marginTop: options.marginTop ?? 0,
    marginBottom: options.marginBottom ?? 0,
    marginLeft: options.marginLeft ?? 0,
    marginRight: options.marginRight ?? 0,
    displayHeaderFooter: Boolean(options.displayHeaderFooter),
    printBackground: Boolean(options.printBackground),
    preferCSSPageSize: Boolean(options.preferCSSPageSize),
    scale: options.scale ?? 1,
  };
  if (options.headerTemplate) params.headerTemplate = options.headerTemplate;
  if (options.footerTemplate) params.footerTemplate = options.footerTemplate;

  const result = await send('Page.printToPDF', params);
  writeFileSync(pdfPath, Buffer.from(result.data, 'base64'));
  console.log(`wrote ${pdfPath}`);
} finally {
  try { ws.close(); } catch {}
  child.kill('SIGKILL');
}
