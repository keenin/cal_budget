/**
 * Home-screen windows. Ported from the Android BudgetCalculator.
 *
 * Automatic payment drops a bill, housing occurrence, or card remainder on its
 * due date. That item is not listed that day or after, and it is never overdue.
 * With automatic payment off, the item stays until it is marked paid, and it
 * is overdue only once today is after the due date.
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

export function unpaidOccurrences(event, from, to, today) {
  return occurrencesBetween(event, from, to).filter((date) => stillOwed(event, date, today));
}

/**
 * Still owed unless this date was explicitly marked paid, or automatic payment
 * has reached it. Automatic payment counts the due date itself as paid.
 */
export function stillOwed(event, date, today) {
  if (event.paidThroughEpochDay != null && epochDay(date) <= event.paidThroughEpochDay) return false;
  if (event.autoPay && !isAfter(date, today)) return false;
  return true;
}

/** True only when today is strictly after the due date. The due date itself is not overdue. */
export function isOverdue(due, today) {
  return isBefore(due, today);
}

/**
 * What the breakdown sheet should offer for one bill or housing row.
 * markOn is the earliest date still owed. Undo is available once any
 * occurrence has been marked paid.
 */
export function billPayChoices(event, rowDue, today) {
  const rowPaid = !stillOwed(event, rowDue, today);
  return {
    markOn: rowPaid ? null : nextUnpaidOccurrence(event, today),
    canUndo: event.paidThroughEpochDay != null,
  };
}

/**
 * Earliest due date that is still owed. Automatic payment skips dates on or
 * before today. Without it, a past-due date stays until it is marked paid.
 */
export function nextUnpaidOccurrence(event, today) {
  const from = event.paidThroughEpochDay == null
    ? fromEpochDay(event.startEpochDay)
    : plusDays(fromEpochDay(event.paidThroughEpochDay), 1);
  const candidate = nextOnOrAfter(event, from);
  if (candidate == null) return null;
  if (!event.autoPay || isAfter(candidate, today)) return candidate;
  return nextOnOrAfter(event, plusDays(today, 1));
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
            for (const date of unpaidOccurrences(event, rangeStart, to, today)) {
                lines.push({
                    name: event.name,
                    due: date,
                    amountCents: cents(event.amountCents),
                    overdue: isOverdue(date, today),
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
            if (card.autoPay && !isAfter(due, today)) continue;
            const earlierThanWindow = !includeEarlier && isBefore(due, from);
            if (earlierThanWindow || isAfter(due, to)) continue;
            lines.push({
                name: card.name,
                due,
                amountCents: remaining,
                overdue: isOverdue(due, today),
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
