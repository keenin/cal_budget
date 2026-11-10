/**
 * Recurrence, statement dates, and payment due dates.
 * Ported from the Android Schedule object. Dates are ISO strings.
 */

import { clampedDay, epochDay, fromEpochDay, isAfter, isBefore, parseIso, plusDays } from './dates.js';

const GUARD = 10_000;

export function nextOnOrAfter(event, from) {
  const start = fromEpochDay(event.startEpochDay);
  const end = event.endEpochDay == null ? null : fromEpochDay(event.endEpochDay);
  if (end != null && isBefore(end, start)) return null;
  let candidate;
  if (!isAfter(from, start)) {
    candidate = start;
  } else {
    const step = dayStep(event);
    if (step != null) {
      const between = epochDay(from) - epochDay(start);
      const steps = Math.floor((between + step - 1) / step);
      candidate = plusDays(start, steps * step);
    } else {
      candidate = nextMonthly(start, from, monthInterval(event));
    }
  }
  if (end != null && isAfter(candidate, end)) return null;
  return candidate;
}

/** Latest occurrence strictly before `date`, if the schedule has one. */
export function previousStrictlyBefore(event, date) {
  let cursor = nextOnOrAfter(event, fromEpochDay(event.startEpochDay));
  if (cursor == null) return null;
  let previous = null;
  let guard = 0;
  while (isBefore(cursor, date) && guard < GUARD) {
    previous = cursor;
    const next = nextOnOrAfter(event, plusDays(cursor, 1));
    if (next == null || !isAfter(next, cursor)) break;
    cursor = next;
    guard += 1;
  }
  return previous;
}

export function occurrencesBetween(event, from, to) {
  if (isBefore(to, from)) return [];
  const dates = [];
  let cursor = nextOnOrAfter(event, from);
  if (cursor == null) return dates;
  let guard = 0;
  while (!isAfter(cursor, to) && guard < GUARD) {
    dates.push(cursor);
    const next = nextOnOrAfter(event, plusDays(cursor, 1));
    if (next == null || !isAfter(next, cursor)) break;
    cursor = next;
    guard += 1;
  }
  return dates;
}

export function statementOnOrBefore(statementDay, today) {
  const day = coerceIn(statementDay, 1, 31);
  const { y, m } = parseIso(today);
  const thisMonth = clampedDay(y, m, day);
  if (!isAfter(thisMonth, today)) return thisMonth;
  const prev = m === 1 ? { y: y - 1, m: 12 } : { y, m: m - 1 };
  return clampedDay(prev.y, prev.m, day);
}

export function paymentDueDate(statementDay, dueMode, daysAfterStatement, dueDay, statementDate) {
  if (dueMode === 'DAYS_AFTER_STATEMENT') {
    return plusDays(statementDate, coerceIn(daysAfterStatement, 0, 90));
  }
  const configuredStatement = coerceIn(statementDay, 1, 31);
  const configuredDue = coerceIn(dueDay, 1, 31);
  const { y, m } = parseIso(statementDate);
  const month = configuredDue > configuredStatement
    ? { y, m }
    : (m === 12 ? { y: y + 1, m: 1 } : { y, m: m + 1 });
  return clampedDay(month.y, month.m, configuredDue);
}

export function recurrenceLabel(event) {
  switch (event.recurrence) {
    case 'WEEKLY':
      return 'Weekly';
    case 'BIWEEKLY':
      return 'Every 2 weeks';
    case 'MONTHLY':
      return 'Monthly';
    case 'CUSTOM':
      return customLabel(event.interval, event.customUnit);
    default:
      return 'Repeats';
  }
}

export function customLabel(interval, unit) {
  const n = Math.max(1, interval | 0);
  if (unit === 'DAYS') return n === 1 ? 'Daily' : `Every ${n} days`;
  if (unit === 'WEEKS') return n === 1 ? 'Weekly' : `Every ${n} weeks`;
  return n === 1 ? 'Monthly' : `Every ${n} months`;
}

function dayStep(event) {
  switch (event.recurrence) {
    case 'WEEKLY':
      return 7;
    case 'BIWEEKLY':
      return 14;
    case 'CUSTOM':
      if (event.customUnit === 'DAYS') return Math.max(1, event.interval | 0);
      if (event.customUnit === 'WEEKS') return Math.max(1, event.interval | 0) * 7;
      return null;
    default:
      return null;
  }
}

function monthInterval(event) {
  if (event.recurrence === 'MONTHLY') return 1;
  if (event.recurrence === 'CUSTOM') return Math.max(1, event.interval | 0);
  return 1;
}

function nextMonthly(start, from, interval) {
  const step = Math.max(1, interval | 0);
  const anchor = parseIso(start);
  const anchorDay = anchor.d;
  function occurrence(n) {
    const index = anchor.y * 12 + (anchor.m - 1) + n * step;
    const y = Math.floor(index / 12);
    const m = index - y * 12 + 1;
    return clampedDay(y, m, anchorDay);
  }
  const fromParts = parseIso(from);
  const months = (fromParts.y - anchor.y) * 12 + (fromParts.m - anchor.m);
  let n = Math.max(0, Math.trunc(months / step));
  let occ = occurrence(n);
  while (n > 0 && !isBefore(occurrence(n - 1), from)) {
    n -= 1;
    occ = occurrence(n);
  }
  while (isBefore(occ, from)) {
    n += 1;
    occ = occurrence(n);
  }
  return occ;
}

function coerceIn(value, min, max) {
  const n = value | 0;
  if (n < min) return min;
  if (n > max) return max;
  return n;
}
