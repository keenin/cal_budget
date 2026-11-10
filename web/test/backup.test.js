import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseBackup, serializeBackup, summaryText } from '../src/js/backup.js';
import { epochDay } from '../src/js/dates.js';
import { upgradeSchema } from '../src/js/store.js';

function sample() {
  return {
    theme: 'DARK',
    events: [
      {
        id: 1,
        name: 'Electric',
        amountCents: 8640,
        kind: 'BILL',
        recurrence: 'MONTHLY',
        interval: 1,
        customUnit: 'MONTHS',
        startEpochDay: epochDay('2026-11-12'),
        endEpochDay: null,
        notes: '',
        paidThroughEpochDay: null,
        autoPay: false,
      },
      {
        id: 2,
        name: 'Payday',
        amountCents: 0,
        kind: 'PAY',
        recurrence: 'BIWEEKLY',
        interval: 1,
        customUnit: 'WEEKS',
        startEpochDay: epochDay('2026-10-30'),
        endEpochDay: null,
        notes: 'varies',
        paidThroughEpochDay: null,
        autoPay: true,
      },
    ],
    cards: [
      {
        id: 7,
        name: 'Visa',
        statementDay: 1,
        dueMode: 'DAYS_AFTER_STATEMENT',
        daysAfterStatement: 21,
        dueDay: 10,
        amountCents: 24018,
        notes: '',
        lastCapturedCycleKey: '2026-11-01',
        paidTowardCents: 5000,
        autoPay: true,
      },
    ],
  };
}

test('a backup round-trips and rejects amounts that are not integer cents', () => {
  const parsed = parseBackup(serializeBackup({ ...sample(), exportedAt: '2026-11-10T00:00:00.000Z' }));
  assert.equal(parsed.ok, true);
  assert.equal(parsed.data.theme, 'DARK');
  assert.equal(parsed.data.events[0].amountCents, 8640);
  assert.equal(parsed.data.events[1].autoPay, false);
  assert.equal(parsed.data.events[1].amountCents, 0);
  assert.equal(parsed.data.cards[0].paidTowardCents, 5000);
  assert.equal(summaryText(parsed.summary), '1 bill, 0 housing payments, 1 card, 1 pay schedule');

  const broken = JSON.parse(serializeBackup(sample()));
  broken.events[0].amountCents = 86.4;
  const rejected = parseBackup(JSON.stringify(broken));
  assert.equal(rejected.ok, false);

  broken.events[0].amountCents = 8640;
  broken.cards[0].amountCents = 240.18;
  assert.equal(parseBackup(JSON.stringify(broken)).ok, false);
});

test('import rejects the wrong file, duplicate ids, and an end before the start', () => {
  assert.equal(parseBackup('').ok, false);
  assert.equal(parseBackup('[]').ok, false);
  assert.equal(parseBackup(JSON.stringify({ app: 'other', version: 1, events: [], cards: [] })).ok, false);
  assert.equal(parseBackup(JSON.stringify({ app: 'calbudget', version: 2, events: [], cards: [] })).ok, false);

  const duplicate = JSON.parse(serializeBackup(sample()));
  duplicate.events.push({ ...duplicate.events[0] });
  assert.match(parseBackup(JSON.stringify(duplicate)).error, /share id/);

  const backwards = JSON.parse(serializeBackup(sample()));
  backwards.events[0].endEpochDay = backwards.events[0].startEpochDay - 1;
  assert.equal(parseBackup(JSON.stringify(backwards)).ok, false);
});

test('schema upgrades only create stores on a new database', () => {
  const created = [];
  const db = {
    createObjectStore(name) {
      created.push(name);
      return { createIndex() {} };
    },
  };
  upgradeSchema(db, 0);
  upgradeSchema(db, 1);
  assert.deepEqual(created, ['events', 'cards', 'meta']);
});
