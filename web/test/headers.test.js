import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const headers = readFileSync(new URL('../src/_headers', import.meta.url), 'utf8');
const html = readFileSync(new URL('../src/index.html', import.meta.url), 'utf8');
const manifest = JSON.parse(readFileSync(new URL('../src/manifest.webmanifest', import.meta.url), 'utf8'));
const ui = readFileSync(new URL('../src/js/ui.js', import.meta.url), 'utf8');

test('the web manifest id is the scope root', () => {
  assert.equal(manifest.id, '/');
  assert.equal(manifest.start_url, '/');
  assert.equal(manifest.scope, '/');
});

test('the http CSP matches the meta policy and denies framing', () => {
  const meta = html.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/);
  assert.ok(meta);
  const http = headers.match(/^ {2}Content-Security-Policy: (.+)$/m);
  assert.ok(http);
  assert.equal(http[1], `${meta[1]}; frame-ancestors 'none'`);
  assert.match(headers, /^ {2}X-Frame-Options: DENY$/m);
  assert.match(headers, /^ {2}Strict-Transport-Security: max-age=86400$/m);
  assert.doesNotMatch(headers, /includeSubDomains|preload/);
  assert.match(headers, /^ {2}Permissions-Policy: camera=\(\), microphone=\(\), geolocation=\(\)$/m);
  assert.match(ui, /Web version 1\.1\.2/);
});

test('the service worker cache rule is last and is only no-cache', () => {
  const swAt = headers.indexOf('\n/sw.js\n');
  const starAt = headers.startsWith('/*\n') ? 0 : headers.indexOf('\n/*\n');
  assert.ok(starAt >= 0);
  assert.ok(swAt > starAt);
  assert.ok(swAt > headers.indexOf('\n/css/*\n'));
  assert.ok(swAt > headers.indexOf('\n/js/*\n'));
  const swRule = headers.slice(swAt);
  assert.match(swRule, /! Cache-Control\n {2}! Content-Security-Policy\n {2}Cache-Control: no-cache\n/);
  assert.match(swRule, /Cloudflare-CDN-Cache-Control: no-store/);
  assert.doesNotMatch(swRule, /max-age=/);
});
