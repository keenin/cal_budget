import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  applyStatementBalance,
  balanceDueDate,
  billPayChoices,
  calculate,
  clearPayment,
  followingSpan,
  isOverdue,
  nextPayday,
  nextSpan,
  nextUnpaidOccurrence,
  paidTowardForSave,
  pendingStatements,
  recordPayment,
  remainingOwed,
  untilSpan,
} from '../src/js/calculator.js';
import { epochDay } from '../src/js/dates.js';
import { previousStrictlyBefore } from '../src/js/schedule.js';

function event(partial) {
  return {
    id: partial.id ?? 1,
    name: partial.name ?? 'Item',
    amountCents: partial.amount ?? 1000,
    kind: partial.kind ?? 'BILL',
    recurrence: partial.recurrence ?? 'MONTHLY',
    interval: partial.interval ?? 1,
    customUnit: partial.unit ?? 'MONTHS',
    startEpochDay: epochDay(partial.start),
    endEpochDay: partial.end ? epochDay(partial.end) : null,
    notes: '',
    paidThroughEpochDay: partial.paidThrough ? epochDay(partial.paidThrough) : null,
    autoPay: partial.autoPay ?? false,
  };
}

function pay(start, extra = {}) {
  return event({
    id: extra.id ?? 9,
    kind: 'PAY',
    recurrence: 'BIWEEKLY',
    start,
    amount: extra.amount ?? 200000,
    name: extra.name ?? 'Pay',
    end: extra.end,
  });
}

function card(partial) {
  return {
    id: partial.id ?? 1,
    name: partial.name ?? 'Card',
    statementDay: partial.statementDay,
    dueMode: partial.dueMode ?? 'DAYS_AFTER_STATEMENT',
    daysAfterStatement: partial.daysAfter ?? 21,
    dueDay: partial.dueDay ?? 10,
    amountCents: partial.amount ?? 0,
    notes: '',
    lastCapturedCycleKey: partial.captured ?? null,
    paidTowardCents: partial.paidToward ?? 0,
    autoPay: partial.autoPay ?? false,
  };
}

function view(line) {
  return {
    name: line.name,
    due: line.due,
    amountCents: line.amountCents,
    overdue: line.overdue,
    source: line.source,
    sourceId: line.sourceId,
  };
}

function views(lines) {
  return lines.map(view);
}

function assertNoDuplicates(snapshot) {
  const keys = [...snapshot.untilPayday, ...snapshot.nextPeriod, ...snapshot.followingPeriod]
    .map((line) => `${line.source}:${line.sourceId}:${line.due}`);
  assert.equal(keys.length, new Set(keys).size);
}

test('home total includes bills, housing, and the card inside each window', () => {
  const today = '2026-09-10';
  const events = [
    pay('2026-09-04'),
    event({ id: 11, kind: 'BILL', recurrence: 'WEEKLY', start: '2026-09-11', amount: 4000, name: 'Groceries' }),
    event({ id: 12, kind: 'MORTGAGE', recurrence: 'MONTHLY', start: '2026-09-15', amount: 150000, name: 'Mortgage' }),
    event({ id: 13, kind: 'BILL', recurrence: 'MONTHLY', start: '2026-10-01', amount: 8000, name: 'Later' }),
  ];
  const visa = card({ statementDay: 5, amount: 20000, captured: '2026-09-05', daysAfter: 12 });
  const snapshot = calculate(events, [visa], today);
  assert.equal(snapshot.nextPayday, '2026-09-18');
  assert.equal(snapshot.followingPayday, '2026-10-02');
  assert.equal(snapshot.untilPaydayCents, 8000 + 150000 + 20000);
  assert.equal(snapshot.untilPaydayCount, 4);
  assert.equal(snapshot.nextPeriodCents, 8000 + 8000);
  assert.equal(snapshot.nextPeriodCount, 3);
  assert.deepEqual(views(snapshot.untilPayday), [
    view({ name: 'Groceries', due: '2026-09-11', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
    view({ name: 'Mortgage', due: '2026-09-15', amountCents: 150000, overdue: false, source: 'MORTGAGE', sourceId: 12 }),
    view({ name: 'Card', due: '2026-09-17', amountCents: 20000, overdue: false, source: 'CARD', sourceId: 1 }),
    view({ name: 'Groceries', due: '2026-09-18', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
  ]);
  assert.deepEqual(views(snapshot.nextPeriod), [
    view({ name: 'Groceries', due: '2026-09-25', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
    view({ name: 'Later', due: '2026-10-01', amountCents: 8000, overdue: false, source: 'BILL', sourceId: 13 }),
    view({ name: 'Groceries', due: '2026-10-02', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
  ]);
  assert.equal(snapshot.thirdPayday, '2026-10-16');
  assert.deepEqual(views(snapshot.followingPeriod), [
    view({ name: 'Groceries', due: '2026-10-09', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
    view({ name: 'Mortgage', due: '2026-10-15', amountCents: 150000, overdue: false, source: 'MORTGAGE', sourceId: 12 }),
    view({ name: 'Groceries', due: '2026-10-16', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
  ]);
  assert.equal(Number.isSafeInteger(snapshot.untilPaydayCents), true);
});

test('a card due after payday moves forward, and an overdue remainder stays', () => {
  const today = '2026-09-20';
  const events = [pay('2026-09-25')];
  const later = card({ statementDay: 20, amount: 5000, captured: '2026-09-20', daysAfter: 10, name: 'Later' });
  const overdue = card({ id: 2, statementDay: 1, amount: 7500, captured: '2026-09-01', daysAfter: 5, name: 'Overdue' });
  const beyond = card({ id: 3, statementDay: 15, amount: 9000, captured: '2026-10-15', daysAfter: 0, name: 'Beyond' });
  const snapshot = calculate(events, [later, overdue, beyond], today);
  assert.equal(snapshot.nextPayday, '2026-09-25');
  assert.equal(snapshot.followingPayday, '2026-10-09');
  assert.equal(snapshot.untilPaydayCents, 7500);
  assert.equal(snapshot.nextPeriodCents, 5000);
  assert.equal(snapshot.thirdPayday, '2026-10-23');
  assert.deepEqual(views(snapshot.untilPayday), [
    view({ name: 'Overdue', due: '2026-09-06', amountCents: 7500, overdue: true, source: 'CARD', sourceId: 2 }),
  ]);
  assert.deepEqual(views(snapshot.nextPeriod), [
    view({ name: 'Later', due: '2026-09-30', amountCents: 5000, overdue: false, source: 'CARD', sourceId: 1 }),
  ]);
  assert.deepEqual(views(snapshot.followingPeriod), [
    view({ name: 'Beyond', due: '2026-10-15', amountCents: 9000, overdue: false, source: 'CARD', sourceId: 3 }),
  ]);
});

test('today being payday looks through the following payday', () => {
  const today = '2026-09-11';
  const events = [
    pay('2026-09-11'),
    event({ kind: 'BILL', recurrence: 'WEEKLY', start: '2026-09-11', amount: 1000 }),
  ];
  const snapshot = calculate(events, [], today);
  assert.equal(snapshot.nextPayday, '2026-09-25');
  assert.equal(snapshot.followingPayday, '2026-10-09');
  assert.equal(snapshot.untilPaydayCents, 3000);
  assert.equal(snapshot.untilPaydayCount, 3);
  assert.equal(snapshot.nextPeriodCents, 2000);
  assert.equal(snapshot.thirdPayday, '2026-10-23');
  assert.deepEqual(views(snapshot.followingPeriod), [
    view({ name: 'Item', due: '2026-10-16', amountCents: 1000, overdue: false, source: 'BILL', sourceId: 1 }),
    view({ name: 'Item', due: '2026-10-23', amountCents: 1000, overdue: false, source: 'BILL', sourceId: 1 }),
  ]);
});

test('a paid occurrence is skipped and later ones still count, including after the date passes', () => {
  const today = '2026-09-10';
  const bill = event({ kind: 'BILL', recurrence: 'WEEKLY', start: '2026-09-11', amount: 4000, name: 'Groceries', id: 11 });
  const before = calculate([pay('2026-09-04'), bill], [], today);
  assert.equal(before.untilPaydayCents, 8000);
  assert.equal(before.nextPeriodCents, 8000);
  assert.equal(nextUnpaidOccurrence(bill, today), '2026-09-11');

  const paid = { ...bill, paidThroughEpochDay: epochDay('2026-09-11') };
  const after = calculate([pay('2026-09-04'), paid], [], today);
  assert.equal(after.untilPaydayCents, 4000);
  assert.deepEqual(views(after.untilPayday), [
    view({ name: 'Groceries', due: '2026-09-18', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
  ]);
  assert.equal(nextUnpaidOccurrence(paid, today), '2026-09-18');
  assert.equal(previousStrictlyBefore(paid, '2026-09-18'), '2026-09-11');
  assert.equal(previousStrictlyBefore(paid, '2026-09-11'), null);

  const nextCycle = calculate([pay('2026-10-02'), paid], [], '2026-10-01');
  assert.equal(nextCycle.untilPaydayCents, 12000);
  assert.deepEqual(views(nextCycle.untilPayday), [
    view({ name: 'Groceries', due: '2026-09-18', amountCents: 4000, overdue: true, source: 'BILL', sourceId: 11 }),
    view({ name: 'Groceries', due: '2026-09-25', amountCents: 4000, overdue: true, source: 'BILL', sourceId: 11 }),
    view({ name: 'Groceries', due: '2026-10-02', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 11 }),
  ]);
  assertNoDuplicates(nextCycle);
});

test('paycheck amount is ignored, and a missing payday shows an empty home', () => {
  const today = '2026-09-10';
  const bill = event({ kind: 'BILL', recurrence: 'WEEKLY', start: '2026-09-11', amount: 4000 });
  const unpaid = { ...pay('2026-09-04'), amountCents: 0 };
  const salaried = { ...unpaid, amountCents: 500000 };
  const withoutIncome = calculate([unpaid, bill], [], today);
  const withIncome = calculate([salaried, bill], [], today);
  assert.deepEqual(withoutIncome, withIncome);
  assert.equal(withoutIncome.untilPayday.every((line) => line.name !== 'Pay'), true);

  const empty = calculate([], [], today);
  assert.equal(empty.nextPayday, null);
  assert.equal(empty.untilPaydayCents, 0);
  assert.equal(empty.nextPeriodCents, 0);
  assert.equal(empty.followingPeriodCents, 0);
  assert.equal(untilSpan(empty, today), null);
});

test('window spans use payday bounds and omit a missing payday', () => {
  const today = '2026-09-10';
  const snapshot = calculate([pay('2026-09-04')], [], today);
  assert.equal(untilSpan(snapshot, today), 'Sep 10 – Sep 18');
  assert.equal(nextSpan(snapshot), 'Sep 19 – Oct 2');
  assert.equal(followingSpan(snapshot), 'Oct 3 – Oct 16');

  const ended = calculate([
    event({
      id: 4,
      kind: 'PAY',
      recurrence: 'BIWEEKLY',
      start: '2026-09-18',
      end: '2026-10-02',
      name: 'Two pays',
      amount: 0,
    }),
  ], [], today);
  assert.equal(untilSpan(ended, today), 'Sep 10 – Sep 18');
  assert.equal(nextSpan(ended), 'Sep 19 – Oct 2');
  assert.equal(followingSpan(ended), null);
});

test('partial, full, and overpaid card amounts stay in integer cents', () => {
  const today = '2026-09-10';
  const events = [pay('2026-09-04')];
  const untilCard = recordPayment(card({
    statementDay: 5,
    amount: 100000,
    captured: '2026-09-05',
    daysAfter: 12,
  }), 50000);
  const nextCard = recordPayment(card({
    id: 2,
    statementDay: 1,
    amount: 40000,
    captured: '2026-10-01',
    daysAfter: 0,
    name: 'Next',
  }), 10000);
  const snapshot = calculate(events, [untilCard, nextCard], today);
  assert.equal(snapshot.untilPaydayCents, 50000);
  assert.equal(snapshot.nextPeriodCents, 30000);
  assert.equal(remainingOwed(recordPayment(untilCard, remainingOwed(untilCard))), 0);

  const owed = card({ statementDay: 1, amount: 100000, captured: '2026-09-01', daysAfter: 5, name: 'Overdue' });
  const covered = calculate([pay('2026-09-25')], [recordPayment(recordPayment(owed, 40000), 60000)], '2026-09-20');
  assert.equal(covered.untilPaydayCents, 0);
  const overpaid = recordPayment(owed, 150000);
  assert.equal(remainingOwed(overpaid), 0);
  assert.equal(calculate([pay('2026-09-25')], [overpaid], '2026-09-20').untilPaydayCents, 0);
  const restored = calculate([pay('2026-09-25')], [clearPayment(overpaid)], '2026-09-20');
  assert.equal(restored.untilPaydayCents, 100000);
});

test('a new statement balance drops payments from the previous cycle', () => {
  const starting = recordPayment(card({
    statementDay: 5,
    amount: 100000,
    captured: '2026-09-05',
  }), 50000);
  const nextCycle = applyStatementBalance(starting, 80000, '2026-10-05');
  assert.equal(nextCycle.amountCents, 80000);
  assert.equal(nextCycle.paidTowardCents, 0);
  assert.equal(remainingOwed(nextCycle), 80000);
  const sameCycle = applyStatementBalance(starting, 90000, '2026-09-05');
  assert.equal(sameCycle.paidTowardCents, 50000);
  assert.equal(remainingOwed(sameCycle), 40000);
  assert.equal(paidTowardForSave(starting, '2026-10-05'), 0);
  assert.equal(paidTowardForSave(starting, '2026-09-05'), 50000);
  assert.equal(recordPayment(starting, 0).paidTowardCents, 50000);
});

test('statement prompts queue uncaptured cards and skip snoozed ones', () => {
  const today = '2026-09-25';
  const captured = card({ id: 1, name: 'Captured', statementDay: 15, captured: '2026-09-15' });
  const zeta = card({ id: 2, name: 'Zeta', statementDay: 20, captured: null });
  const alpha = card({ id: 3, name: 'Alpha', statementDay: 10, captured: null });
  const prompts = pendingStatements([captured, zeta, alpha], today);
  assert.deepEqual(prompts.map((prompt) => prompt.cardName), ['Alpha', 'Zeta']);
  assert.equal(prompts[0].cycleKey, '2026-09-10');
  assert.equal(prompts[1].cycleKey, '2026-09-20');
  assert.equal(
    pendingStatements([captured, zeta, alpha], today, new Set([3])).some((prompt) => prompt.cardName === 'Alpha'),
    false,
  );
});

test('overdue starts the day after the due date', () => {
  const due = '2026-09-11';
  assert.equal(isOverdue(due, '2026-09-10'), false);
  assert.equal(isOverdue(due, due), false);
  assert.equal(isOverdue(due, '2026-09-12'), true);
});

test('automatic payment drops off on the due date and stays off', () => {
  const due = '2026-09-11';
  const bill = event({
    id: 31,
    kind: 'BILL',
    recurrence: 'WEEKLY',
    start: due,
    amount: 4000,
    name: 'Auto weekly',
    autoPay: true,
  });
  const housing = event({
    id: 32,
    kind: 'MORTGAGE',
    recurrence: 'MONTHLY',
    start: due,
    amount: 10000,
    name: 'Auto housing',
    autoPay: true,
  });
  const autoCard = card({
    statementDay: 21,
    amount: 5000,
    captured: '2026-08-21',
    daysAfter: 21,
    name: 'Auto card',
    autoPay: true,
  });
  const events = [pay('2026-09-04'), bill, housing];

  const before = calculate(events, [autoCard], '2026-09-10');
  assert.deepEqual(views(before.untilPayday), [
    view({ name: 'Auto card', due, amountCents: 5000, overdue: false, source: 'CARD', sourceId: 1 }),
    view({ name: 'Auto housing', due, amountCents: 10000, overdue: false, source: 'MORTGAGE', sourceId: 32 }),
    view({ name: 'Auto weekly', due, amountCents: 4000, overdue: false, source: 'BILL', sourceId: 31 }),
    view({ name: 'Auto weekly', due: '2026-09-18', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 31 }),
  ]);
  assert.equal(before.untilPayday.every((line) => line.overdue === false), true);
  assert.equal(nextUnpaidOccurrence(bill, '2026-09-10'), due);

  const onDue = calculate(events, [autoCard], due);
  assert.deepEqual(views(onDue.untilPayday), [
    view({ name: 'Auto weekly', due: '2026-09-18', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 31 }),
  ]);
  assert.equal(onDue.untilPayday.some((line) => line.due === due), false);
  assert.equal(onDue.untilPayday.some((line) => line.overdue), false);
  assert.equal(nextUnpaidOccurrence(bill, due), '2026-09-18');

  const after = calculate(events, [autoCard], '2026-09-12');
  assert.deepEqual(views(after.untilPayday), views(onDue.untilPayday));
  const all = [...after.untilPayday, ...after.nextPeriod, ...after.followingPeriod];
  assert.equal(all.some((line) => line.due === due), false);
  assert.equal(all.some((line) => line.overdue), false);
  assertNoDuplicates(after);
});

test('manual expenses stay outstanding and turn overdue the day after the due date', () => {
  const due = '2026-09-11';
  const bill = event({
    id: 41,
    kind: 'BILL',
    recurrence: 'WEEKLY',
    start: due,
    amount: 4000,
    name: 'Manual weekly',
  });
  const housing = event({
    id: 42,
    kind: 'MORTGAGE',
    recurrence: 'MONTHLY',
    start: due,
    amount: 10000,
    name: 'Manual housing',
  });
  const manualCard = card({
    statementDay: 21,
    amount: 5000,
    captured: '2026-08-21',
    daysAfter: 21,
    name: 'Manual card',
  });
  const events = [pay('2026-09-04'), bill, housing];

  const before = calculate(events, [manualCard], '2026-09-10');
  const dueTomorrow = before.untilPayday.filter((line) => line.due === due);
  assert.equal(dueTomorrow.length, 3);
  assert.equal(dueTomorrow.every((line) => line.overdue === false), true);

  const onDue = calculate(events, [manualCard], due);
  const dueLines = onDue.untilPayday.filter((line) => line.due === due);
  assert.equal(dueLines.length, 3);
  assert.equal(dueLines.every((line) => line.overdue === false), true);
  assert.equal(onDue.untilPayday.some((line) => line.name === 'Manual weekly' && line.due === '2026-09-18' && !line.overdue), true);

  const after = calculate(events, [manualCard], '2026-09-12');
  assert.deepEqual(views(after.untilPayday), [
    view({ name: 'Manual card', due, amountCents: 5000, overdue: true, source: 'CARD', sourceId: 1 }),
    view({ name: 'Manual housing', due, amountCents: 10000, overdue: true, source: 'MORTGAGE', sourceId: 42 }),
    view({ name: 'Manual weekly', due, amountCents: 4000, overdue: true, source: 'BILL', sourceId: 41 }),
    view({ name: 'Manual weekly', due: '2026-09-18', amountCents: 4000, overdue: false, source: 'BILL', sourceId: 41 }),
  ]);
  assert.equal(nextUnpaidOccurrence(bill, '2026-09-12'), due);
  assertNoDuplicates(after);
});

test('a partial autopay card drops off on its due date and a manual remainder stays overdue', () => {
  const events = [pay('2026-09-04')];
  const owed = card({
    statementDay: 5,
    amount: 100000,
    captured: '2026-09-05',
    daysAfter: 12,
    name: 'Card',
  });
  const half = recordPayment(owed, 40000);
  const beforeDue = calculate(events, [half], '2026-09-10');
  assert.equal(remainingOwed(half), 60000);
  assert.deepEqual(views(beforeDue.untilPayday), [
    view({ name: 'Card', due: '2026-09-17', amountCents: 60000, overdue: false, source: 'CARD', sourceId: 1 }),
  ]);

  const manualAfter = calculate(events, [half], '2026-09-18');
  assert.deepEqual(views(manualAfter.untilPayday.filter((line) => line.source === 'CARD')), [
    view({ name: 'Card', due: '2026-09-17', amountCents: 60000, overdue: true, source: 'CARD', sourceId: 1 }),
  ]);

  const auto = { ...half, autoPay: true };
  const autoBefore = calculate(events, [auto], '2026-09-16');
  assert.equal(autoBefore.untilPaydayCents, 60000);
  assert.equal(autoBefore.untilPayday[0].overdue, false);
  const autoOnDue = calculate(events, [auto], '2026-09-17');
  assert.equal(autoOnDue.untilPayday.length, 0);
  assert.equal(remainingOwed(auto), 60000);
  const autoAfter = calculate(events, [auto], '2026-09-18');
  const autoLines = [...autoAfter.untilPayday, ...autoAfter.nextPeriod, ...autoAfter.followingPeriod];
  assert.equal(autoLines.some((line) => line.name === 'Card'), false);
  assert.equal(autoLines.some((line) => line.overdue), false);
});


test('bill pay choices mark the earliest unpaid date', () => {
  const bill = event({ kind: 'BILL', recurrence: 'WEEKLY', start: '2026-09-11', amount: 4000, name: 'Groceries' });
  const today = '2026-09-10';
  const laterRow = billPayChoices(bill, '2026-09-18', today);
  assert.equal(laterRow.markOn, '2026-09-11');
  assert.equal(laterRow.canUndo, false);
  const paid = { ...bill, paidThroughEpochDay: epochDay('2026-09-11') };
  assert.equal(billPayChoices(paid, '2026-09-11', today).markOn, null);
  assert.equal(billPayChoices(paid, '2026-09-11', today).canUndo, true);
  assert.equal(billPayChoices(paid, '2026-09-18', today).markOn, '2026-09-18');
});

test('the soonest pay schedule wins, and a single payday leaves the later windows at zero', () => {
  const today = '2026-09-10';
  const weekly = event({ id: 1, kind: 'PAY', recurrence: 'WEEKLY', start: '2026-09-12', amount: 0, name: 'Weekly' });
  const monthly = event({ id: 2, kind: 'PAY', recurrence: 'MONTHLY', start: '2026-09-30', amount: 0, name: 'Monthly' });
  assert.equal(nextPayday([weekly, monthly], today), '2026-09-12');
  const lastPay = event({
    id: 3,
    kind: 'PAY',
    recurrence: 'BIWEEKLY',
    start: '2026-09-18',
    end: '2026-09-18',
    amount: 0,
    name: 'Last pay',
  });
  const bill = event({ id: 4, kind: 'BILL', recurrence: 'WEEKLY', start: '2026-09-18', amount: 2500, name: 'Weekly bill' });
  const snapshot = calculate([lastPay, bill], [], today);
  assert.equal(snapshot.nextPayday, '2026-09-18');
  assert.equal(snapshot.followingPayday, null);
  assert.equal(snapshot.nextPeriodCents, 0);
  assert.equal(snapshot.followingPeriodCents, 0);
  assert.equal(snapshot.untilPaydayCents, 2500);
});
