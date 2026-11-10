/**
 * Home-screen windows. Ported from the Android BudgetCalculator.
 *
 * Automatic payment is a reminder only. It does not mark a bill, housing
 * payment, or card remainder paid, and it does not drop the item on its due
 * date. An unpaid item stays in the totals until it is explicitly paid.
 * It is overdue starting the day after the due date (due today is not overdue).
 *
 * Amounts are integer cents. This module never divides them.
 */

import { epochDay, fromEpochDay, isAfter, isBefore, plusDays, spanLabel } from './dates.js';
import { isCents } from './money.js';
import { nextOnOrAfter, occurrencesBetween, paymentDueDate, statementOnOrBefore } from './schedule.js';

export function calculate(events, cards, today) {
  const payday = nextPayday(events, today);
  if (payday == null) {
    return snapshot(null, null, null, [], [], []);
  }
  const following = paydayAfter(events, payday);
  const third = following == null ? null : paydayAfter(events, following);
  const until = windowLines(events, cards, today, today, payday, true);
  const next = following == null
    ? []
    : windowLines(events, cards, today, plusDays(payday, 1), following, false);
  const afterNext = following == null || third == null
    ? []
    : windowLines(events, cards, today, plusDays(following, 1), third, false);
  return snapshot(payday, following, third, until, next, afterNext);
}

export function untilSpan(snap, today) {
  return spanLabel(today, snap.nextPayday);
}

export function nextSpan(snap) {
  if (snap.nextPayday == null) return null;
  return spanLabel(plusDays(snap.nextPayday, 1), snap.followingPayday);
}

export function followingSpan(snap) {
  if (snap.followingPayday == null) return null;
  return spanLabel(plusDays(snap.followingPayday, 1), snap.thirdPayday);
}

export function pendingStatements(cards, today, snoozedCardIds = new Set()) {
  const prompts = [];
  for (const card of cards) {
    if (snoozedCardIds.has(card.id)) continue;
    const statement = statementOnOrBefore(card.statementDay, today);
    if (card.lastCapturedCycleKey === statement) continue;
    prompts.push({
      cardId: card.id,
      cardName: card.name,
      statementDate: statement,
      dueDate: paymentDueDate(
        card.statementDay,
        card.dueMode,
        card.daysAfterStatement,
        card.dueDay,
        statement,
      ),
      cycleKey: statement,
    });
  }
  prompts.sort((a, b) => {
    if (a.statementDate !== b.statementDate) return a.statementDate < b.statementDate ? -1 : 1;
    const an = a.cardName.toLocaleLowerCase('en-US');
    const bn = b.cardName.toLocaleLowerCase('en-US');
    if (an !== bn) return an < bn ? -1 : 1;
    return a.cardId - b.cardId;
  });
  return prompts;
}

/** Statement balance still unpaid for the current cycle. Overpay counts as zero. */
export function remainingOwed(card) {
  return Math.max(0, cents(card.amountCents) - cents(card.paidTowardCents));
}

/** Add a payment toward the current statement. Does not change the statement balance. */
export function recordPayment(card, paymentCents) {
  const payment = cents(paymentCents);
  if (payment <= 0) return card;
  return { ...card, paidTowardCents: cents(card.paidTowardCents) + payment };
}

export function clearPayment(card) {
  return { ...card, paidTowardCents: 0 };
}

/**
 * Store a statement balance. A new cycle drops payments from the previous one.
 * The same cycle keeps them, so a corrected balance still subtracts what was paid.
 */
export function applyStatementBalance(card, amountCents, cycleKey) {
  const paid = card.lastCapturedCycleKey === cycleKey ? cents(card.paidTowardCents) : 0;
  return {
    ...card,
    amountCents: cents(amountCents),
    lastCapturedCycleKey: cycleKey,
    paidTowardCents: paid,
  };
}

/** Payments survive an edit only while the statement cycle stays the same. */
export function paidTowardForSave(existing, cycleKey) {
  if (existing == null || cycleKey !== existing.lastCapturedCycleKey) return 0;
  return cents(existing.paidTowardCents);
}

export function balanceDueDate(card, today) {
  const captured = parseCaptured(card.lastCapturedCycleKey);
  const statement = captured ?? statementOnOrBefore(card.statementDay, today);
  return paymentDueDate(
    card.statementDay,
    card.dueMode,
    card.daysAfterStatement,
    card.dueDay,
    statement,
  );
}

export function unpaidOccurrences(event, from, to) {
  return occurrencesBetween(event, from, to).filter((date) => stillOwed(event, date));
}

/**
 * An occurrence is still owed until that date is explicitly marked paid.
 * Automatic payment and the calendar date do not clear it.
 */
export function stillOwed(event, date) {
  if (event.paidThroughEpochDay == null) return true;
  return epochDay(date) > event.paidThroughEpochDay;
}

/**
 * What the breakdown sheet should offer for one bill or housing row.
 * markOn is the earliest unpaid date when this row is still unpaid, so one
 * tap covers one occurrence and does not skip a past-due one.
 */
export function billPayChoices(event, rowDue) {
  const rowPaid = !stillOwed(event, rowDue);
  return {
    markOn: rowPaid ? null : nextUnpaidOccurrence(event),
    canUndo: event.paidThroughEpochDay != null,
  };
}

/** Earliest due date not covered by paidThroughEpochDay. This can be before today. */
export function nextUnpaidOccurrence(event) {
  const from = event.paidThroughEpochDay == null
    ? fromEpochDay(event.startEpochDay)
    : plusDays(fromEpochDay(event.paidThroughEpochDay), 1);
  return nextOnOrAfter(event, from);
}

/**
 * The nearest payday on or after today. If today is itself a payday, this is
 * the following one, so the current check does not close the first window.
 */
export function nextPayday(events, today) {
  const pays = events.filter((event) => event.kind === 'PAY');
  const upcoming = minDate(pays.map((event) => nextOnOrAfter(event, today)));
  if (upcoming == null) return null;
  if (isAfter(upcoming, today)) return upcoming;
  return minDate(pays.map((event) => nextOnOrAfter(event, plusDays(today, 1)))) ?? upcoming;
}

/** The payday after `payday`, across every pay schedule. */
export function paydayAfter(events, payday) {
  const pays = events.filter((event) => event.kind === 'PAY');
  return minDate(pays.map((event) => nextOnOrAfter(event, plusDays(payday, 1))));
}

function windowLines(events, cards, today, from, to, includeEarlier) {
  const lines = [];
  for (const event of events) {
    if (event.kind === 'PAY') continue;
    const start = fromEpochDay(event.startEpochDay);
    const rangeStart = includeEarlier && isBefore(start, from) ? start : from;
    for (const date of unpaidOccurrences(event, rangeStart, to)) {
      lines.push({
        name: event.name,
        due: date,
        amountCents: cents(event.amountCents),
        overdue: isBefore(date, today),
        sourceId: event.id,
        source: event.kind === 'MORTGAGE' ? 'MORTGAGE' : 'BILL',
        autoPay: Boolean(event.autoPay),
      });
    }
  }
  for (const card of cards) {
    const remaining = remainingOwed(card);
    if (remaining <= 0) continue;
    const due = balanceDueDate(card, today);
    const earlierThanWindow = !includeEarlier && isBefore(due, from);
    if (earlierThanWindow || isAfter(due, to)) continue;
    lines.push({
      name: card.name,
      due,
      amountCents: remaining,
      overdue: isBefore(due, today),
      sourceId: card.id,
      source: 'CARD',
      autoPay: Boolean(card.autoPay),
    });
  }
  lines.sort((a, b) => {
    if (a.due !== b.due) return a.due < b.due ? -1 : 1;
    const an = a.name.toLocaleLowerCase('en-US');
    const bn = b.name.toLocaleLowerCase('en-US');
    if (an !== bn) return an < bn ? -1 : 1;
    return 0;
  });
  return lines;
}

function snapshot(nextPaydayDate, followingPayday, thirdPayday, untilPayday, nextPeriod, followingPeriod) {
  return {
    nextPayday: nextPaydayDate,
    followingPayday,
    thirdPayday,
    untilPayday,
    nextPeriod,
    followingPeriod,
    untilPaydayCents: sumCents(untilPayday),
    nextPeriodCents: sumCents(nextPeriod),
    followingPeriodCents: sumCents(followingPeriod),
    untilPaydayCount: untilPayday.length,
    nextPeriodCount: nextPeriod.length,
    followingPeriodCount: followingPeriod.length,
  };
}

function sumCents(lines) {
  let total = 0;
  for (const line of lines) {
    total += cents(line.amountCents);
  }
  return total;
}

function cents(value) {
  if (!isCents(value)) throw new TypeError('Money must be an integer number of cents');
  return value;
}

function parseCaptured(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  try {
    fromEpochDay(epochDay(value));
    return value;
  } catch {
    return null;
  }
}

function minDate(dates) {
  let best = null;
  for (const date of dates) {
    if (date == null) continue;
    if (best == null || isBefore(date, best)) best = date;
  }
  return best;
}
