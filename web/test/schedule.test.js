import assert from 'node:assert/strict';
import { test } from 'node:test';
import { epochDay } from '../src/js/dates.js';
import {
  customLabel,
  nextOnOrAfter,
  occurrencesBetween,
  paymentDueDate,
  previousStrictlyBefore,
  recurrenceLabel,
  statementOnOrBefore,
} from '../src/js/schedule.js';

function event(partial) {
  return {
    id: partial.id ?? 1,
    name: partial.name ?? 'Item',
    amountCents: partial.amount ?? 1000,
    kind: partial.kind ?? 'BILL',
    recurrence: partial.recurrence,
    interval: partial.interval ?? 1,
    customUnit: partial.unit ?? 'MONTHS',
    startEpochDay: epochDay(partial.start),
    endEpochDay: partial.end ? epochDay(partial.end) : null,
    notes: '',
    paidThroughEpochDay: null,
    autoPay: false,
  };
}

test('biweekly anchors on the start date', () => {
  const pay = event({ kind: 'PAY', recurrence: 'BIWEEKLY', start: '2026-09-11' });
  assert.equal(nextOnOrAfter(pay, '2026-09-11'), '2026-09-11');
  assert.equal(nextOnOrAfter(pay, '2026-09-12'), '2026-09-25');
  assert.equal(nextOnOrAfter(pay, '2026-09-25'), '2026-09-25');
});

test('weekly stops at the end date', () => {
  const bill = event({
    recurrence: 'WEEKLY',
    start: '2026-09-01',
    end: '2026-09-08',
  });
  assert.deepEqual(
    occurrencesBetween(bill, '2026-09-01', '2026-09-30'),
    ['2026-09-01', '2026-09-08'],
  );
  assert.equal(nextOnOrAfter(bill, '2026-09-09'), null);
});

test('monthly clamps short months and restores the anchor day', () => {
  const mortgage = event({ kind: 'MORTGAGE', recurrence: 'MONTHLY', start: '2026-01-31' });
  assert.equal(nextOnOrAfter(mortgage, '2026-02-01'), '2026-02-28');
  assert.equal(nextOnOrAfter(mortgage, '2026-03-01'), '2026-03-31');
});

test('custom day, week, and month intervals', () => {
  const days = event({ recurrence: 'CUSTOM', unit: 'DAYS', interval: 10, start: '2026-01-01' });
  assert.equal(nextOnOrAfter(days, '2026-01-15'), '2026-01-21');
  const weeks = event({ recurrence: 'CUSTOM', unit: 'WEEKS', interval: 3, start: '2026-01-01' });
  assert.equal(nextOnOrAfter(weeks, '2026-01-08'), '2026-01-22');
  const months = event({ recurrence: 'CUSTOM', unit: 'MONTHS', interval: 2, start: '2026-01-15' });
  assert.equal(nextOnOrAfter(months, '2026-04-01'), '2026-05-15');
});

test('previous occurrence and labels', () => {
  const bill = event({ recurrence: 'WEEKLY', start: '2026-09-11' });
  assert.equal(previousStrictlyBefore(bill, '2026-09-18'), '2026-09-11');
  assert.equal(previousStrictlyBefore(bill, '2026-09-11'), null);
  assert.equal(recurrenceLabel(event({ recurrence: 'BIWEEKLY', start: '2026-09-11' })), 'Every 2 weeks');
  assert.equal(customLabel(1, 'DAYS'), 'Daily');
  assert.equal(customLabel(3, 'WEEKS'), 'Every 3 weeks');
});

test('statement day and due day', () => {
  assert.equal(statementOnOrBefore(15, '2026-09-25'), '2026-09-15');
  assert.equal(statementOnOrBefore(28, '2026-09-10'), '2026-08-28');
  assert.equal(statementOnOrBefore(31, '2026-02-10'), '2026-01-31');
  assert.equal(
    paymentDueDate(15, 'DAY_OF_MONTH', 21, 10, '2026-09-15'),
    '2026-10-10',
  );
  assert.equal(
    paymentDueDate(5, 'DAYS_AFTER_STATEMENT', 12, 10, '2026-09-05'),
    '2026-09-17',
  );
  assert.equal(
    paymentDueDate(20, 'DAY_OF_MONTH', 21, 28, '2026-09-20'),
    '2026-09-28',
  );
});
