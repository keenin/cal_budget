import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  epochDay,
  formatWindow,
  fromEpochDay,
  plusDays,
  plusMonths,
  spanLabel,
} from '../src/js/dates.js';

test('epoch days match java.time and round-trip', () => {
  assert.equal(epochDay('1970-01-01'), 0);
  assert.equal(epochDay('2000-01-01'), 10957);
  assert.equal(epochDay('2026-09-11'), 20707);
  assert.equal(fromEpochDay(0), '1970-01-01');
  assert.equal(fromEpochDay(20707), '2026-09-11');
  assert.equal(fromEpochDay(epochDay('2024-02-29')), '2024-02-29');
  assert.equal(plusDays('2026-09-11', 14), '2026-09-25');
  assert.equal(plusMonths('2026-01-31', 1), '2026-02-28');
  assert.equal(plusMonths('2024-01-31', 1), '2024-02-29');
  assert.equal(plusMonths('2026-01-31', 2), '2026-03-31');
});

test('window labels use an en dash and skip a missing end', () => {
  assert.equal(formatWindow('2026-09-10'), 'Sep 10');
  assert.equal(formatWindow('2026-10-02'), 'Oct 2');
  assert.equal(spanLabel('2026-09-10', '2026-09-18'), 'Sep 10 – Sep 18');
  assert.equal(spanLabel('2026-09-19', '2026-09-19'), 'Sep 19');
  assert.equal(spanLabel('2026-10-03', null), null);
});
