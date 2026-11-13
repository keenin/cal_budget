import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { storageNoticeHtml, viewHtml } from '../src/js/ui.js';
import {
  STORAGE_NOTICE_KEY,
  STORAGE_NOTICE_SNOOZE_MS,
  dismissStorageNotice,
  isStorageNoticeDismissed,
  resolvePersistentStorage,
  shouldShowStorageNotice,
  storageStatusLabel,
} from '../src/js/storage-notice.js';

function memoryStorage(initial = {}) {
  const data = { ...initial };
  return {
    getItem(key) {
      return Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null;
    },
    setItem(key, value) {
      data[key] = String(value);
    },
  };
}

test('persistent storage is requested only when it is not already granted', async () => {
  let persistCalls = 0;
  const granted = await resolvePersistentStorage({
    async persisted() { return true; },
    async persist() { persistCalls += 1; return true; },
  });
  assert.equal(granted, true);
  assert.equal(persistCalls, 0);

  const asked = await resolvePersistentStorage({
    async persisted() { return false; },
    async persist() { persistCalls += 1; return true; },
  });
  assert.equal(asked, true);
  assert.equal(persistCalls, 1);
});

test('a missing or denied persist API is not persistent', async () => {
  assert.equal(await resolvePersistentStorage(undefined), false);
  assert.equal(await resolvePersistentStorage({}), false);
  assert.equal(await resolvePersistentStorage({
    async persisted() { return false; },
  }), false);
  assert.equal(await resolvePersistentStorage({
    async persisted() { return false; },
    async persist() { return false; },
  }), false);
  assert.equal(await resolvePersistentStorage({
    async persisted() { throw new Error('blocked'); },
    async persist() { return false; },
  }), false);
});

test('a failed persisted check still asks persist()', async () => {
  let persistCalls = 0;
  const granted = await resolvePersistentStorage({
    async persisted() { throw new Error('blocked'); },
    async persist() { persistCalls += 1; return true; },
  });
  assert.equal(granted, true);
  assert.equal(persistCalls, 1);
});

test('a thrown persist() call is treated as denied', async () => {
  assert.equal(await resolvePersistentStorage({
    async persisted() { return false; },
    async persist() { throw new Error('nope'); },
  }), false);
});

test('the storage warning stays dismissed for seven days', () => {
  const now = Date.parse('2026-11-13T00:00:00Z');
  const storage = memoryStorage();
  assert.equal(isStorageNoticeDismissed(storage, now), false);
  const until = dismissStorageNotice(storage, now);
  assert.equal(until, now + STORAGE_NOTICE_SNOOZE_MS);
  assert.equal(storage.getItem(STORAGE_NOTICE_KEY), String(until));
  assert.equal(isStorageNoticeDismissed(storage, now + STORAGE_NOTICE_SNOOZE_MS - 1), true);
  assert.equal(isStorageNoticeDismissed(storage, until), false);
  assert.equal(shouldShowStorageNotice(false, true), false);
  assert.equal(shouldShowStorageNotice(false, false), true);
  assert.equal(shouldShowStorageNotice(true, false), false);
});

test('a broken dismissal store does not throw and does not count as dismissed', () => {
  const broken = {
    getItem() { throw new Error('denied'); },
    setItem() { throw new Error('denied'); },
  };
  assert.equal(isStorageNoticeDismissed(broken, 0), false);
  assert.equal(dismissStorageNotice(broken, 0), STORAGE_NOTICE_SNOOZE_MS);
  assert.equal(isStorageNoticeDismissed(null, 0), false);
  assert.equal(isStorageNoticeDismissed(memoryStorage({ [STORAGE_NOTICE_KEY]: 'nope' }), 10), false);
});

test('the banner explains the risk and can be omitted', () => {
  const shown = storageNoticeHtml({ storageNotice: true });
  assert.match(shown, /may delete this app/);
  assert.match(shown, /home screen/);
  assert.match(shown, /clear data on exit/);
  assert.match(shown, /forget this site/);
  assert.match(shown, /Brave/);
  assert.match(shown, /Export backup/);
  assert.match(shown, /data-action="dismiss-storage-notice"/);
  assert.equal(storageNoticeHtml({ storageNotice: false }), '');
  assert.equal(storageNoticeHtml(null), '');
});

test('settings names the persistence status and the banner explains Brave', () => {
  assert.equal(storageStatusLabel(true), 'Storage: persistent');
  assert.equal(storageStatusLabel(false), 'Storage: may be cleared by browser');
  const settings = viewHtml({
    failed: false,
    ready: true,
    route: { name: 'settings' },
    theme: 'SYSTEM',
    textSize: 'default',
    persisted: false,
    events: [],
    cards: [],
    today: '2026-11-13',
    snoozed: new Set(),
  });
  assert.match(settings, /Storage: may be cleared by browser/);
  assert.match(settings, /Brave/);
  assert.match(settings, /Export backup/);
  const hidden = viewHtml({
    failed: false,
    ready: true,
    route: { name: 'settings' },
    theme: 'DARK',
    textSize: 'large',
    persisted: true,
    events: [],
    cards: [],
    today: '2026-11-13',
    snoozed: new Set(),
  });
  assert.match(hidden, /Storage: persistent/);
  assert.doesNotMatch(hidden, /storage-notice/);
});

test('service worker precaches the notice module under a new cache name', () => {
  const sw = readFileSync(new URL('../src/sw.js', import.meta.url), 'utf8');
  assert.match(sw, /calbudget-v8/);
  assert.doesNotMatch(sw, /calbudget-v7/);
  assert.match(sw, /\/js\/storage-notice\.js/);
});
