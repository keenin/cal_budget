/**
 * JSON backup for moving data between browsers. Version 1 matches the
 * fields stored for bills, housing, pay schedules, cards, and theme.
 * Amounts must already be integer cents; decimal dollar amounts are rejected.
 */

import { epochDay, fromEpochDay } from './dates.js';
import { isCents } from './money.js';

export const BACKUP_VERSION = 1;
const APP = 'calbudget';
const MAX_CHARS = 2_000_000;

const KINDS = new Set(['BILL', 'MORTGAGE', 'PAY']);
const RECURRENCES = new Set(['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'CUSTOM']);
const UNITS = new Set(['DAYS', 'WEEKS', 'MONTHS']);
const DUE_MODES = new Set(['DAYS_AFTER_STATEMENT', 'DAY_OF_MONTH']);
const THEMES = new Set(['SYSTEM', 'LIGHT', 'DARK']);

export function serializeBackup({ events, cards, theme, exportedAt }) {
  return JSON.stringify({
    app: APP,
    version: BACKUP_VERSION,
    exportedAt: exportedAt ?? new Date().toISOString(),
    events: events.map(eventRecord),
    cards: cards.map(cardRecord),
    settings: { theme: THEMES.has(theme) ? theme : 'SYSTEM' },
  });
}

export function parseBackup(text) {
  if (typeof text !== 'string' || text.trim() === '') {
    return fail('That file is empty.');
  }
  if (text.length > MAX_CHARS) return fail('That file is too large.');
  let raw;
  try {
    raw = JSON.parse(text);
  } catch {
    return fail('That file is not valid JSON.');
  }
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return fail('That file is not a Cal Budget backup.');
  }
  if (raw.app !== APP) return fail('That file is not a Cal Budget backup.');
  if (raw.version !== BACKUP_VERSION) {
    return fail('This backup is from a newer or unknown version of Cal Budget.');
  }
  if (!Array.isArray(raw.events) || !Array.isArray(raw.cards)) {
    return fail('This backup is missing bills or cards.');
  }

  const events = [];
  const seenEvents = new Set();
  for (let i = 0; i < raw.events.length; i += 1) {
    const parsed = parseEvent(raw.events[i], i);
    if (!parsed.ok) return parsed;
    if (seenEvents.has(parsed.value.id)) return fail(`Two items share id ${parsed.value.id}.`);
    seenEvents.add(parsed.value.id);
    events.push(parsed.value);
  }

  const cards = [];
  const seenCards = new Set();
  for (let i = 0; i < raw.cards.length; i += 1) {
    const parsed = parseCard(raw.cards[i], i);
    if (!parsed.ok) return parsed;
    if (seenCards.has(parsed.value.id)) return fail(`Two cards share id ${parsed.value.id}.`);
    seenCards.add(parsed.value.id);
    cards.push(parsed.value);
  }

  let theme = null;
  if (raw.settings != null) {
    if (typeof raw.settings !== 'object' || Array.isArray(raw.settings)) {
      return fail('Settings in this backup are not valid.');
    }
    if (raw.settings.theme != null) {
      if (!THEMES.has(raw.settings.theme)) return fail('The appearance setting is not valid.');
      theme = raw.settings.theme;
    }
  }

  return {
    ok: true,
    data: { events, cards, theme },
    summary: {
      bills: events.filter((event) => event.kind === 'BILL').length,
      housing: events.filter((event) => event.kind === 'MORTGAGE').length,
      pays: events.filter((event) => event.kind === 'PAY').length,
      cards: cards.length,
    },
  };
}

export function summaryText(summary) {
  return [
    count(summary.bills, 'bill', 'bills'),
    count(summary.housing, 'housing payment', 'housing payments'),
    count(summary.cards, 'card', 'cards'),
    count(summary.pays, 'pay schedule', 'pay schedules'),
  ].join(', ');
}

function parseEvent(value, index) {
  const label = `Item ${index + 1}`;
  if (!isRecord(value)) return fail(`${label} is not valid.`);
  const kind = value.kind;
  if (!KINDS.has(kind)) return fail(`${label} has an unknown type.`);
  const name = requiredName(value.name, label);
  if (!name.ok) return name;
  const amount = centsField(value.amountCents, `${label} amount`, 0);
  if (!amount.ok) return amount;
  if (kind !== 'PAY' && amount.value <= 0) return fail(`${label} needs an amount greater than zero.`);
  if (!RECURRENCES.has(value.recurrence)) return fail(`${label} has an unknown repeat.`);
  const interval = intField(value.interval, `${label} interval`, 1, 999);
  if (!interval.ok) return interval;
  if (!UNITS.has(value.customUnit)) return fail(`${label} has an unknown repeat unit.`);
  const start = epochField(value.startEpochDay, `${label} start date`);
  if (!start.ok) return start;
  let end = null;
  if (value.endEpochDay != null) {
    const parsedEnd = epochField(value.endEpochDay, `${label} end date`);
    if (!parsedEnd.ok) return parsedEnd;
    if (parsedEnd.value < start.value) return fail(`${label} ends before it starts.`);
    end = parsedEnd.value;
  }
  let paidThrough = null;
  if (value.paidThroughEpochDay != null) {
    const parsedPaid = epochField(value.paidThroughEpochDay, `${label} paid date`);
    if (!parsedPaid.ok) return parsedPaid;
    paidThrough = parsedPaid.value;
  }
  const notes = notesField(value.notes, label);
  if (!notes.ok) return notes;
  const id = idField(value.id, label);
  if (!id.ok) return id;
  return ok({
    id: id.value,
    name: name.value,
    amountCents: amount.value,
    kind,
    recurrence: value.recurrence,
    interval: value.recurrence === 'CUSTOM' ? interval.value : 1,
    customUnit: value.customUnit,
    startEpochDay: start.value,
    endEpochDay: end,
    notes: notes.value,
    paidThroughEpochDay: paidThrough,
    autoPay: kind !== 'PAY' && value.autoPay === true,
  });
}

function parseCard(value, index) {
  const label = `Card ${index + 1}`;
  if (!isRecord(value)) return fail(`${label} is not valid.`);
  const id = idField(value.id, label);
  if (!id.ok) return id;
  const name = requiredName(value.name, label);
  if (!name.ok) return name;
  const statementDay = intField(value.statementDay, `${label} statement day`, 1, 31);
  if (!statementDay.ok) return statementDay;
  if (!DUE_MODES.has(value.dueMode)) return fail(`${label} has an unknown due date.`);
  const daysAfter = intField(value.daysAfterStatement, `${label} days after statement`, 0, 90);
  if (!daysAfter.ok) return daysAfter;
  const dueDay = intField(value.dueDay, `${label} due day`, 1, 31);
  if (!dueDay.ok) return dueDay;
  const amount = centsField(value.amountCents, `${label} balance`, 0);
  if (!amount.ok) return amount;
  const paid = centsField(value.paidTowardCents ?? 0, `${label} payment`, 0);
  if (!paid.ok) return paid;
  let cycle = null;
  if (value.lastCapturedCycleKey != null) {
    if (typeof value.lastCapturedCycleKey !== 'string' || !isIsoDate(value.lastCapturedCycleKey)) {
      return fail(`${label} has a statement date Cal Budget does not understand.`);
    }
    cycle = value.lastCapturedCycleKey;
  }
  const notes = notesField(value.notes, label);
  if (!notes.ok) return notes;
  return ok({
    id: id.value,
    name: name.value,
    statementDay: statementDay.value,
    dueMode: value.dueMode,
    daysAfterStatement: daysAfter.value,
    dueDay: dueDay.value,
    amountCents: amount.value,
    notes: notes.value,
    lastCapturedCycleKey: cycle,
    paidTowardCents: paid.value,
    autoPay: value.autoPay === true,
  });
}

function eventRecord(event) {
  return {
    id: event.id,
    name: event.name,
    amountCents: event.amountCents,
    kind: event.kind,
    recurrence: event.recurrence,
    interval: event.interval,
    customUnit: event.customUnit,
    startEpochDay: event.startEpochDay,
    endEpochDay: event.endEpochDay ?? null,
    notes: event.notes ?? '',
    paidThroughEpochDay: event.paidThroughEpochDay ?? null,
    autoPay: Boolean(event.autoPay),
  };
}

function cardRecord(card) {
  return {
    id: card.id,
    name: card.name,
    statementDay: card.statementDay,
    dueMode: card.dueMode,
    daysAfterStatement: card.daysAfterStatement,
    dueDay: card.dueDay,
    amountCents: card.amountCents,
    notes: card.notes ?? '',
    lastCapturedCycleKey: card.lastCapturedCycleKey ?? null,
    paidTowardCents: card.paidTowardCents ?? 0,
    autoPay: Boolean(card.autoPay),
  };
}

function requiredName(value, label) {
  if (typeof value !== 'string') return fail(`${label} needs a name.`);
  const name = value.trim();
  if (name === '' || name.length > 80) return fail(`${label} needs a name of 1 to 80 characters.`);
  return ok(name);
}

function notesField(value, label) {
  if (value == null) return ok('');
  if (typeof value !== 'string') return fail(`${label} notes are not text.`);
  if (value.length > 400) return fail(`${label} notes are too long.`);
  return ok(value);
}

function centsField(value, label, min) {
  if (typeof value === 'boolean' || !isCents(value) || value < min) {
    return fail(`${label} must be a whole number of cents.`);
  }
  return ok(value);
}

function intField(value, label, min, max) {
  if (typeof value === 'boolean' || !isCents(value) || value < min || value > max) {
    return fail(`${label} is not valid.`);
  }
  return ok(value);
}

function epochField(value, label) {
  if (typeof value === 'boolean' || !isCents(value) || value < -365_000 || value > 3_650_000) {
    return fail(`${label} is not valid.`);
  }
  try {
    epochDay(fromEpochDay(value));
  } catch {
    return fail(`${label} is not valid.`);
  }
  return ok(value);
}

function idField(value, label) {
  if (typeof value === 'boolean' || !isCents(value) || value <= 0) {
    return fail(`${label} is missing an id.`);
  }
  return ok(value);
}

function isIsoDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  try {
    return fromEpochDay(epochDay(value)) === value;
  } catch {
    return false;
  }
}

function isRecord(value) {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function count(n, one, many) {
  return `${n} ${n === 1 ? one : many}`;
}

function ok(value) {
  return { ok: true, value };
}

function fail(error) {
  return { ok: false, error };
}
