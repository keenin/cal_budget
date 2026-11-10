import {
  balanceDueDate,
  billPayChoices,
  calculate,
  followingSpan,
  nextSpan,
  nextUnpaidOccurrence,
  pendingStatements,
  remainingOwed,
  untilSpan,
} from './calculator.js';
import { formatLong, formatWindow, fromEpochDay, isAfter, isBefore } from './dates.js';
import { format, toInput } from './money.js';
import { nextOnOrAfter, recurrenceLabel } from './schedule.js';
import { editCardPath, editEventPath, listPath } from './router.js';

const COPY = {
  BILL: {
    title: 'Bills',
    add: 'Add bill',
    emptyTitle: 'No bills yet',
    emptyBody: 'Add rent extras, utilities, subscriptions, and anything else that repeats.',
    editNew: 'New bill',
    editExisting: 'Edit bill',
  },
  MORTGAGE: {
    title: 'Mortgage / housing',
    add: 'Add payment',
    emptyTitle: 'No housing payments',
    emptyBody: 'Add a mortgage, rent, or HOA. Use monthly, or a custom schedule if it isn’t.',
    editNew: 'New housing payment',
    editExisting: 'Edit housing payment',
  },
  PAY: {
    title: 'Pay schedule',
    add: 'Add schedule',
    emptyTitle: 'No pay schedule',
    emptyBody: 'Set when you get paid. Weekly, biweekly, and monthly are the usual choices. The next payday sets the home screen.',
    editNew: 'New pay schedule',
    editExisting: 'Edit pay schedule',
  },
};

const WINDOWS = {
  until: { title: 'Until payday', lines: (s) => s.untilPayday, dates: (s, today) => untilSpan(s, today) },
  next: { title: 'Next period', lines: (s) => s.nextPeriod, dates: (s) => nextSpan(s) },
  following: { title: 'Following period', lines: (s) => s.followingPeriod, dates: (s) => followingSpan(s) },
};

export function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

export function barHtml(state) {
  const route = state.route;
  if (!state.ready) {
    return `${menuButton()}<h1 class="quiet">What I owe</h1>`;
  }
  if (route.name === 'home') return `${menuButton()}<h1 class="quiet">What I owe</h1>`;
  if (route.name === 'breakdown') {
    return `${backButton('/')}<h1>${esc(WINDOWS[route.window].title)}</h1>`;
  }
  if (route.name === 'list') {
    return `${menuButton()}<h1>${esc(COPY[route.kind].title)}</h1>`;
  }
  if (route.name === 'cards') return `${menuButton()}<h1>Credit cards</h1>`;
  if (route.name === 'settings') return `${menuButton()}<h1>Settings</h1>`;
  if (route.name === 'edit-event') {
    const copy = COPY[route.kind];
    const title = route.id == null ? copy.editNew : copy.editExisting;
    return `${backButton(listPath(route.kind))}<h1>${esc(title)}</h1>`;
  }
  if (route.name === 'edit-card') {
    return `${backButton('/cards')}<h1>${route.id == null ? 'New card' : 'Edit card'}</h1>`;
  }
  return `${menuButton()}<h1 class="quiet">What I owe</h1>`;
}

export function viewHtml(state) {
  if (state.failed) {
    return `<div class="view-inner empty"><h2>Can’t store data</h2><p>This browser has no IndexedDB, so Cal Budget can’t keep a budget on this device.</p></div>`;
  }
  if (!state.ready) return '';
  const route = state.route;
  if (route.name === 'home') return homeHtml(state);
  if (route.name === 'breakdown') return breakdownHtml(state);
  if (route.name === 'list') return listHtml(state);
  if (route.name === 'edit-event') return editEventHtml(state);
  if (route.name === 'cards') return cardListHtml(state);
  if (route.name === 'edit-card') return editCardHtml(state);
  if (route.name === 'settings') return settingsHtml(state);
  return homeHtml(state);
}

export function menuHtml(state) {
  if (!state.menuOpen) return '';
  const route = state.route;
  const items = [
    ['/', 'Home', iconHome()],
    ['/bills', 'Bills', iconReceipt()],
    ['/cards', 'Credit cards', iconCard()],
    ['/mortgage', 'Mortgage / housing', iconHouse()],
    ['/pay', 'Pay schedule', iconPay()],
    ['/settings', 'Settings', iconSettings()],
  ];
  const links = items.map(([path, label, icon]) => {
    const current = menuCurrent(route, path);
    return `<button type="button" class="nav-item" data-action="nav" data-path="${path}" ${current ? 'aria-current="page"' : ''}>${icon}<span>${esc(label)}</span></button>`;
  }).join('');
  return `<button type="button" class="scrim" data-action="close-menu" aria-label="Close menu"></button>
    <nav class="drawer" aria-label="Menu">
      <h2>Cal Budget</h2>
      <p class="fine">Until payday · next · following</p>
      <hr>
      ${links}
      <p class="drawer-foot fine">Local only · USD</p>
    </nav>`;
}

export function sheetHtml(state) {
  if (!state.sheet) return '';
  const { source, id, due } = state.sheet;
  const event = state.events.find((item) => item.id === id);
  const card = state.cards.find((item) => item.id === id);
  const name = source === 'CARD' ? card?.name : event?.name;
  const amount = source === 'CARD' ? (card ? remainingOwed(card) : null) : event?.amountCents;
  let actions = '';
  if (source === 'CARD') {
    actions = card ? cardPayHtml(card, 'sheet-payment') : '<p class="help">This item is no longer on the device.</p>';
  } else if (!event) {
    actions = '<p class="help">This item is no longer on the device.</p>';
  } else {
    const choices = billPayChoices(event, due, state.today);
    const note = choices.markOn && choices.markOn !== due
      ? `<p class="help">Marks ${esc(formatWindow(choices.markOn))}, the next unpaid date.</p>`
      : '';
    const mark = choices.markOn
      ? `<button type="button" class="btn btn-wide" data-action="mark-paid" data-id="${event.id}">Mark paid</button>`
      : '';
    const undo = choices.canUndo
      ? `<button type="button" class="btn-text btn-wide" data-action="undo-paid" data-id="${event.id}">Undo paid</button>`
      : '';
    actions = `${note}${mark}${undo}`;
  }
  const edit = name
    ? `<button type="button" class="btn-text btn-wide" data-action="edit-line" data-source="${esc(source)}" data-id="${id}">Edit details</button>`
    : '';
  return `<button type="button" class="scrim sheet-scrim" data-action="close-sheet" aria-label="Close"></button>
    <section class="sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title">
      <div class="grab"></div>
      <h2 id="sheet-title">${esc(name || 'Not found')}</h2>
      <p class="help">Due ${esc(formatWindow(due))}</p>
      ${amount == null ? '' : `<p class="amount-line">${esc(format(amount))}</p>`}
      ${actions}
      ${edit}
    </section>`;
}

export function modalHtml(state) {
  const modal = state.modal;
  if (modal?.type === 'message') return dialogHtml(modal.title, `<p>${esc(modal.body)}</p>`, cancelButtons('OK'));
  if (modal?.type === 'confirm-erase') {
    return dialogHtml(
      'Erase everything?',
      '<p>Bills, cards, housing, and pay schedules will be deleted from this device.</p>',
      `<div class="dialog-actions"><button type="button" class="btn-text" data-action="cancel-modal">Cancel</button><button type="button" class="btn-danger" data-action="confirm-erase">Erase</button></div>`,
    );
  }
  if (modal?.type === 'confirm-delete') {
    return dialogHtml(
      `Delete ${modal.name || 'this item'}?`,
      `<p>${esc(modal.body)}</p>`,
      `<div class="dialog-actions"><button type="button" class="btn-text" data-action="cancel-modal">Cancel</button><button type="button" class="btn-danger" data-action="confirm-delete">Delete</button></div>`,
    );
  }
  if (modal?.type === 'confirm-import') {
    return dialogHtml(
      'Replace everything on this device?',
      `<p>This backup has ${esc(modal.summary)}. Saving it deletes the bills, cards, housing, and pay schedules already stored here.</p>`,
      `<div class="dialog-actions"><button type="button" class="btn-text" data-action="cancel-modal">Cancel</button><button type="button" class="btn" data-action="confirm-import">Replace</button></div>`,
    );
  }
  if (state.route.name === 'home' && state.ready) {
    const prompts = pendingStatements(state.cards, state.today, state.snoozed);
    const prompt = prompts[0];
    if (prompt) return statementHtml(state, prompt, prompts.length);
  }
  return '';
}

export function paidSlotHtml(state) {
  const route = state.route;
  if (route.name !== 'edit-event' || route.id == null) return '';
  const event = state.events.find((item) => item.id === route.id);
  if (!event || event.kind === 'PAY') return '';
  return paidStatusHtml(event, state.today);
}

export function cardPaySlotHtml(state) {
  const route = state.route;
  if (route.name !== 'edit-card' || route.id == null) return '';
  const card = state.cards.find((item) => item.id === route.id);
  if (!card || (card.amountCents <= 0 && card.paidTowardCents <= 0)) return '';
  return cardPayHtml(card, `pay-${card.id}`);
}

function homeHtml(state) {
  const snapshot = calculate(state.events, state.cards, state.today);
  const blocks = [
    ['until', 'Until payday', displayCents(snapshot, 'until'), untilSpan(snapshot, state.today), true],
    ['next', 'Next period', displayCents(snapshot, 'next'), nextSpan(snapshot), false],
    ['following', 'Following period', displayCents(snapshot, 'following'), followingSpan(snapshot), false],
  ];
  const periods = blocks.map(([window, label, cents, dates, primary]) => {
    const text = format(cents);
    const described = dates ? `${label}, ${dates}, ${text}` : `${label}, ${text}`;
    return `<button type="button" class="period" data-action="breakdown" data-window="${window}" aria-label="${esc(described)}">
      <span class="period-label">${esc(label)}</span>
      ${dates ? `<span class="period-dates">${esc(dates)}</span>` : ''}
      <span class="${amountClass(text, primary)}">${esc(text)}</span>
    </button>`;
  }).join('');
  const hint = snapshot.nextPayday
    ? ''
    : '<p class="hint">Add a pay schedule and these dates show up here.</p>';
  return `<div class="home"><div class="periods">${periods}${hint}</div></div>`;
}

function breakdownHtml(state) {
  const spec = WINDOWS[state.route.window];
  const snapshot = calculate(state.events, state.cards, state.today);
  const lines = spec.lines(snapshot);
  const dates = spec.dates(snapshot, state.today);
  const header = `<div class="total-block">${dates ? `<p class="total-dates">${esc(dates)}</p>` : ''}<p class="total-amount">${esc(format(sum(lines)))}</p></div>`;
  if (lines.length === 0) {
    return `<div class="view-inner">${header}<div class="empty"><h2>Nothing due</h2><p>No unpaid bills, housing, or card balances fall in this window.</p></div></div>`;
  }
  const rows = lines.map((line) => {
    const when = line.overdue ? `Overdue · ${formatWindow(line.due)}` : `Due ${formatWindow(line.due)}`;
    return `<button type="button" class="row" data-action="sheet" data-source="${line.source}" data-id="${line.sourceId}" data-due="${line.due}">
      <span class="row-text"><span class="row-title">${esc(line.name)}</span><span class="row-meta${line.overdue ? ' overdue' : ''}">${esc(when)}</span></span>
      <span class="row-amount">${esc(format(line.amountCents))}</span>
    </button>`;
  }).join('');
  return `<div class="view-inner stack">${header}${rows}</div>`;
}

function listHtml(state) {
  const copy = COPY[state.route.kind];
  const events = state.events.filter((event) => event.kind === state.route.kind);
  const add = `<button type="button" class="fab" data-action="nav" data-path="${editEventPath(state.route.kind, null)}">${esc(copy.add)}</button>`;
  if (events.length === 0) {
    return `${add}<div class="view-inner empty"><h2>${esc(copy.emptyTitle)}</h2><p>${esc(copy.emptyBody)}</p></div>`;
  }
  const cards = events.map((event) => eventRowHtml(event, state.today)).join('');
  return `${add}<div class="view-inner stack">${cards}</div>`;
}

function eventRowHtml(event, today) {
  const next = event.kind === 'PAY' ? nextOnOrAfter(event, today) : nextUnpaidOccurrence(event, today);
  let when = 'Ended';
  let overdue = false;
  if (next == null && event.paidThroughEpochDay != null) when = 'Paid';
  else if (next == null) when = 'Ended';
  else if (event.kind !== 'PAY' && isBefore(next, today)) {
    when = `Overdue ${formatWindow(next)}`;
    overdue = true;
  } else when = `next ${formatWindow(next)}`;
  const pay = event.kind === 'PAY' && event.amountCents > 0 ? ` · ${format(event.amountCents)}` : '';
  const sub = `${recurrenceLabel(event)}${event.autoPay ? ' · Automatic' : ''} · ${when}${pay}`;
  const amount = event.kind === 'PAY' ? '' : `<span class="card-amount">${esc(format(event.amountCents))}</span>`;
  const actions = event.kind === 'PAY' ? '' : `<div class="card-actions">${paidButtons(event, today)}</div>`;
  return `<article class="card">
    <button type="button" class="card-main" data-action="nav" data-path="${editEventPath(event.kind, event.id)}">
      <span class="row-text"><span class="card-title${overdue ? ' overdue' : ''}">${esc(event.name)}</span><span class="card-sub${overdue ? ' overdue' : ''}">${esc(sub)}</span></span>
      ${amount}
    </button>
    ${actions}
  </article>`;
}

function editEventHtml(state) {
  const route = state.route;
  const existing = state.events.find((event) => event.id === route.id);
  if (route.id != null && !existing) {
    return '<div class="view-inner empty"><h2>Not found</h2><p>This item is no longer on the device.</p></div>';
  }
  const scheduleOnly = route.kind === 'PAY';
  const recurrence = existing?.recurrence || (scheduleOnly ? 'BIWEEKLY' : 'MONTHLY');
  const unit = existing?.customUnit || 'MONTHS';
  const start = existing ? fromEpochDay(existing.startEpochDay) : state.today;
  const ongoing = existing ? existing.endEpochDay == null : true;
  const end = existing?.endEpochDay != null ? fromEpochDay(existing.endEpochDay) : plusYear(state.today);
  const helper = scheduleOnly
    ? '<p class="help">Pick a recent or upcoming payday. The home screen uses these dates only. Paycheck amount is optional, because pay varies, and it is never added into what you owe.</p>'
    : '';
  return `<form class="view-inner form" data-action="save-event" data-kind="${route.kind}" data-id="${existing ? existing.id : ''}">
    ${helper}
    <div data-live="paid">${existing && !scheduleOnly ? paidStatusHtml(existing, state.today) : ''}</div>
    ${textField('name', scheduleOnly ? 'Schedule name' : 'Name', existing?.name || (scheduleOnly ? 'Payday' : ''))}
    ${scheduleOnly ? moneyField('amount', 'Paycheck amount (optional)', existing && existing.amountCents > 0 ? toInput(existing.amountCents) : '', false) : moneyField('amount', 'Amount', existing ? toInput(existing.amountCents) : '', true)}
    <p class="group-label">Repeats</p>
    ${chips('recurrence', [
      ['WEEKLY', 'Weekly'],
      ['BIWEEKLY', 'Biweekly'],
      ['MONTHLY', 'Monthly'],
      ['CUSTOM', 'Custom'],
    ], recurrence)}
    <div data-panel="custom" ${recurrence === 'CUSTOM' ? '' : 'hidden'}>
      ${textField('interval', 'Every', String(existing?.interval || 2), 'number')}
      ${chips('customUnit', [['DAYS', 'Days'], ['WEEKS', 'Weeks'], ['MONTHS', 'Months']], unit)}
    </div>
    ${dateField('start', scheduleOnly ? 'Payday date' : 'Start date', start)}
    ${switchRow('ongoing', 'Ongoing', 'Turn off to set an end date', ongoing)}
    <div data-panel="end" ${ongoing ? 'hidden' : ''}>${dateField('end', 'End date', end)}</div>
    ${scheduleOnly ? '' : switchRow('autoPay', 'Automatic payment', 'Counts until the due date. On that day it is treated as paid and drops off.', Boolean(existing?.autoPay))}
    ${notesField(existing?.notes || '')}
    <button type="submit" class="btn btn-wide">Save</button>
    ${existing ? `<button type="button" class="btn-danger btn-wide" data-action="ask-delete" data-target="event" data-id="${existing.id}">Delete</button>` : ''}
  </form>`;
}

function cardListHtml(state) {
  const add = `<button type="button" class="fab" data-action="nav" data-path="${editCardPath(null)}">Add card</button>`;
  if (state.cards.length === 0) {
    return `${add}<div class="view-inner empty"><h2>No cards yet</h2><p>Add a card with its statement day. The first time you open the app on or after that day, Cal asks for the statement balance.</p></div>`;
  }
  const prompts = new Set(pendingStatements(state.cards, state.today, state.snoozed).map((prompt) => prompt.cardId));
  const rows = state.cards.map((card) => {
    const due = balanceDueDate(card, state.today);
    const remaining = remainingOwed(card);
    const clearedByAutopay = card.autoPay && !isAfter(due, state.today);
    const dueLabel = card.dueMode === 'DAYS_AFTER_STATEMENT'
      ? `Due ${card.daysAfterStatement} days after statement`
      : `Due on day ${card.dueDay}`;
    const needs = prompts.has(card.id);
    const overdue = !needs && remaining > 0 && !clearedByAutopay && isBefore(due, state.today);
    const status = needs ? 'Statement needed' : (overdue ? `Overdue · ${formatWindow(due)}` : `Payment ${formatWindow(due)}`);
    const pay = card.amountCents > 0 || card.paidTowardCents > 0 ? cardPayHtml(card, `pay-${card.id}`) : '';
    return `<article class="card">
      <button type="button" class="card-main" data-action="nav" data-path="${editCardPath(card.id)}">
        <span class="row-text">
          <span class="card-title${overdue ? ' overdue' : ''}">${esc(card.name)}</span>
          <span class="card-sub">Statement day ${card.statementDay} · ${esc(dueLabel)}${card.autoPay ? ' · Automatic' : ''}</span>
          <span class="card-sub${overdue ? ' overdue' : ''}">${esc(status)}</span>
        </span>
        <span class="card-amount">${esc(format(remaining))}</span>
      </button>
      ${pay}
    </article>`;
  }).join('');
  return `${add}<div class="view-inner stack">${rows}</div>`;
}

function editCardHtml(state) {
  const existing = state.cards.find((card) => card.id === state.route.id);
  if (state.route.id != null && !existing) {
    return '<div class="view-inner empty"><h2>Not found</h2><p>This card is no longer on the device.</p></div>';
  }
  const dueMode = existing?.dueMode || 'DAYS_AFTER_STATEMENT';
  return `<form class="view-inner form" data-action="save-card" data-id="${existing ? existing.id : ''}">
    <p class="help">After each statement date, the home screen asks for that cycle’s balance before you rely on the numbers.</p>
    ${textField('name', 'Card name', existing?.name || '')}
    ${textField('statementDay', 'Statement day', String(existing?.statementDay ?? 15), 'number')}
    <p class="fine">Day of the month the statement closes, 1–31</p>
    <p class="group-label">Payment due</p>
    ${chips('dueMode', [['DAYS_AFTER_STATEMENT', 'Days after statement'], ['DAY_OF_MONTH', 'Day of month']], dueMode)}
    <div data-panel="days" ${dueMode === 'DAYS_AFTER_STATEMENT' ? '' : 'hidden'}>
      ${textField('daysAfter', 'Days after statement', String(existing?.daysAfterStatement ?? 21), 'number')}
      <p class="fine">Often 21. Use 0 to 90.</p>
    </div>
    <div data-panel="dom" ${dueMode === 'DAY_OF_MONTH' ? '' : 'hidden'}>
      ${textField('dueDay', 'Due day', String(existing?.dueDay ?? 10), 'number')}
      <p class="fine">If this day is on or before the statement day, it’s the next month.</p>
    </div>
    ${moneyField('amount', 'Amount owed', existing && existing.amountCents > 0 ? toInput(existing.amountCents) : '', false)}
    <p class="help">Full statement balance for this cycle. A payment reduces what the home numbers count and leaves this balance as entered. Leave blank until the statement prompt if you don’t know it yet.</p>
    ${switchRow('autoPay', 'Automatic payment', 'Counts until the payment due date. On that day the unpaid remainder is treated as paid and drops off.', Boolean(existing?.autoPay))}
    <div data-live="card-pay">${existing && (existing.amountCents > 0 || existing.paidTowardCents > 0) ? cardPayHtml(existing, `pay-${existing.id}`) : ''}</div>
    ${notesField(existing?.notes || '')}
    <button type="submit" class="btn btn-wide">Save</button>
    ${existing ? `<button type="button" class="btn-danger btn-wide" data-action="ask-delete" data-target="card" data-id="${existing.id}">Delete</button>` : ''}
  </form>`;
}

function settingsHtml(state) {
  const persisted = state.persisted
    ? 'This browser agreed to keep data on this device. Export a backup before you clear browser data.'
    : 'Persistent storage is not granted yet. Browsers can still remove site data. Export a backup, and use Keep data on this device after you have added Cal Budget to your home screen.';
  return `<div class="view-inner settings">
    <h2 class="section-title">How the numbers work</h2>
    <p>Until payday is unpaid bills, housing, and card payments due through the next payday. Without automatic payment, a past-due amount stays in that number. If today is payday, the next payday is the one after today.</p>
    <p>Next period is what is due after that payday, through the payday after it. Following period is what is due after that, through the third payday. If a later payday does not exist, that number is $0.00.</p>
    <p>Automatic payment drops a bill, housing payment, or card remainder off on its due date. Without it, that amount stays until you mark the occurrence paid or record a card payment that covers it. It is overdue, and shown in red, only after the due date. A partial card payment reduces only what is still unpaid. Each occurrence is counted once. Paycheck amounts are not part of any number.</p>
    <p>Amounts are US dollars. Everything is stored on this device.</p>
    <h2 class="section-title">Appearance</h2>
    <p class="group-label">Dark mode</p>
    <div class="chips">
      ${themeChip('SYSTEM', 'System default', state.theme)}
      ${themeChip('LIGHT', 'Light', state.theme)}
      ${themeChip('DARK', 'Dark', state.theme)}
    </div>
    <h2 class="section-title">Data</h2>
    <button type="button" class="btn-secondary" data-action="export">Export backup</button>
    <button type="button" class="btn-secondary" data-action="import">Import backup</button>
    <button type="button" class="btn-secondary" data-action="persist">Keep data on this device</button>
    <p>${esc(persisted)}</p>
    <button type="button" class="btn-secondary" data-action="erase">Erase all data</button>
    <p class="fine">Web version 1.0</p>
  </div>`;
}

function statementHtml(state, prompt, total) {
  const card = state.cards.find((item) => item.id === prompt.cardId);
  const initial = card && card.amountCents > 0 ? toInput(card.amountCents) : '';
  const counter = total > 1 ? `<p class="fine">Card 1 of ${total}</p>` : '';
  return `<div class="scrim modal-scrim"></div>
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="statement-title">
      <h2 id="statement-title">New statement</h2>
      ${counter}
      <h3>${esc(prompt.cardName)}</h3>
      <p class="help">Statement date · ${esc(formatLong(prompt.statementDate))}</p>
      <p class="fine">Payment due · ${esc(formatLong(prompt.dueDate))}</p>
      <p class="help">Enter the statement balance. Use 0 if nothing is owed. It counts toward Until payday, Next period, or Following period, depending on when it is due. A partial payment can be recorded later on the card.</p>
      <form data-action="save-statement" data-card="${prompt.cardId}" data-cycle="${esc(prompt.cycleKey)}">
        ${moneyField('amount', 'Statement balance', initial, false)}
        <button type="submit" class="btn btn-wide">Save balance</button>
      </form>
      <button type="button" class="btn-text btn-wide" data-action="skip-statement" data-id="${prompt.cardId}">Skip for now</button>
    </section>`;
}

function paidStatusHtml(event, today) {
  const next = nextUnpaidOccurrence(event, today);
  const text = next == null
    ? 'Nothing left to pay on this schedule.'
    : isBefore(next, today)
      ? `Overdue since ${formatWindow(next)}. It stays until you mark it paid.`
      : `Next due ${formatWindow(next)}. Mark it paid if you already covered it.`;
  return `<p class="status-copy${isBefore(next || today, today) ? ' overdue' : ''}">${esc(text)}</p><div class="inline-actions">${paidButtons(event, today)}</div>`;
}

function paidButtons(event, today) {
  const next = nextUnpaidOccurrence(event, today);
  if (next == null && event.paidThroughEpochDay == null) return '';
  const mark = next ? `<button type="button" class="btn-text" data-action="mark-paid" data-id="${event.id}">Mark paid</button>` : '';
  const undo = event.paidThroughEpochDay != null
    ? `<button type="button" class="btn-text" data-action="undo-paid" data-id="${event.id}">Undo paid</button>`
    : '';
  return mark + undo;
}

function cardPayHtml(card, inputId) {
  const remaining = remainingOwed(card);
  const status = card.paidTowardCents > 0
    ? `<p class="fine">${remaining === 0 ? `Paid in full · ${esc(format(card.amountCents))}` : `Paid ${esc(format(card.paidTowardCents))} of ${esc(format(card.amountCents))}`}</p>`
    : '';
  const pay = remaining > 0 ? `
    <button type="button" class="btn btn-wide" data-action="pay-full" data-id="${card.id}">Paid</button>
    <label class="field"><span>Payment</span>
      <span class="money-input"><span aria-hidden="true">$</span><input id="${esc(inputId)}" class="js-money" inputmode="decimal" autocomplete="off" data-pay-for="${card.id}"></span>
      <span class="field-error" data-error-for="pay-${card.id}"></span>
    </label>
    <button type="button" class="btn-secondary btn-wide" data-action="record-payment" data-id="${card.id}">Record payment</button>` : '';
  const clear = card.paidTowardCents > 0
    ? `<button type="button" class="btn-text" data-action="clear-payment" data-id="${card.id}">Clear payment</button>`
    : '';
  return `${status}${pay}${clear}`;
}

function dialogHtml(title, body, actions) {
  return `<button type="button" class="scrim modal-scrim" data-action="cancel-modal" aria-label="Close"></button>
    <section class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
      <h2 id="dialog-title">${esc(title)}</h2>
      ${body}
      ${actions}
    </section>`;
}

function cancelButtons(label) {
  return `<div class="dialog-actions"><button type="button" class="btn-text" data-action="cancel-modal">${esc(label)}</button></div>`;
}

function textField(name, label, value, mode) {
  const extra = mode === 'number' ? ' inputmode="numeric" ' : '';
  return `<label class="field"><span>${esc(label)}</span><input name="${name}" value="${esc(value)}" maxlength="80" autocomplete="off"${extra}><span class="field-error" data-error-for="${name}"></span></label>`;
}

function moneyField(name, label, value, required) {
  return `<label class="field"><span>${esc(label)}</span><span class="money-input"><span aria-hidden="true">$</span><input class="js-money" name="${name}" inputmode="decimal" autocomplete="off" value="${esc(value)}" ${required ? 'required' : ''}></span><span class="field-error" data-error-for="${name}"></span></label>`;
}

function dateField(name, label, value) {
  return `<label class="field"><span>${esc(label)}</span><input type="date" name="${name}" value="${esc(value)}" required><span class="field-error" data-error-for="${name}"></span></label>`;
}

function notesField(value) {
  return `<label class="field"><span>Notes (optional)</span><textarea name="notes" maxlength="400">${esc(value)}</textarea></label>`;
}

function chips(group, options, selected) {
  const buttons = options.map(([value, label]) => {
    const pressed = value === selected ? 'true' : 'false';
    return `<button type="button" class="chip" data-action="chip" data-group="${group}" data-value="${value}" aria-pressed="${pressed}">${esc(label)}</button>`;
  }).join('');
  return `<div class="chips" role="group">${buttons}</div><input type="hidden" name="${group}" value="${esc(selected)}">`;
}

function switchRow(name, title, help, checked) {
  return `<label class="switch"><span class="switch-copy"><strong>${esc(title)}</strong><span>${esc(help)}</span></span><span class="switch-control"><input type="checkbox" name="${name}" ${checked ? 'checked' : ''}><span class="track"></span></span></label>`;
}

function themeChip(mode, label, selected) {
  return `<button type="button" class="chip${selected === mode ? ' selected' : ''}" data-action="theme" data-theme="${mode}" aria-pressed="${selected === mode}">${esc(label)}</button>`;
}

function menuButton() {
  return `<button type="button" class="icon-button" data-action="menu" aria-label="Menu">${iconMenu()}</button>`;
}

function backButton(path) {
  return `<button type="button" class="icon-button" data-action="nav" data-path="${path}" aria-label="Back">${iconBack()}</button>`;
}

function menuCurrent(route, path) {
  if (path === '/') return route.name === 'home' || route.name === 'breakdown';
  if (path === '/bills') return route.kind === 'BILL' || (route.name === 'list' && route.kind === 'BILL') || (route.name === 'edit-event' && route.kind === 'BILL');
  if (path === '/mortgage') return route.kind === 'MORTGAGE';
  if (path === '/pay') return route.kind === 'PAY';
  if (path === '/cards') return route.name === 'cards' || route.name === 'edit-card';
  if (path === '/settings') return route.name === 'settings';
  return false;
}

function amountClass(text, primary) {
  const size = text.length >= 14 ? 'xs' : text.length >= 12 ? 'sm' : text.length >= 10 ? 'md' : 'lg';
  return `amount ${primary ? 'primary' : 'secondary'} ${size}`;
}

function sum(lines) {
  return lines.reduce((total, line) => total + line.amountCents, 0);
}

function displayCents(snapshot, which) {
  if (which === 'until') return snapshot.nextPayday == null ? 0 : snapshot.untilPaydayCents;
  if (which === 'next') return snapshot.followingPayday == null ? 0 : snapshot.nextPeriodCents;
  return snapshot.thirdPayday == null ? 0 : snapshot.followingPeriodCents;
}

function plusYear(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  const day = m === 2 && d === 29 ? 28 : d;
  return `${y + 1}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function iconMenu() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
}
function iconBack() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5 8 12l7 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}
function iconHome() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 11.5 12 4l8 7.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1v-8.5Z" fill="currentColor"/></svg>';
}
function iconReceipt() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-2-1.2L14 21l-2-1.2L10 21l-2-1.2L6 21V3Zm2 4v2h8V7H8Zm0 4v2h8v-2H8Zm0 4v2h5v-2H8Z" fill="currentColor"/></svg>';
}
function iconCard() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 6.5A2.5 2.5 0 0 1 5.5 4h13A2.5 2.5 0 0 1 21 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-11ZM5 8h14V6.5a.5.5 0 0 0-.5-.5h-13a.5.5 0 0 0-.5.5V8Zm0 2.5V17.5c0 .28.22.5.5.5h13a.5.5 0 0 0 .5-.5V10.5H5Z" fill="currentColor"/></svg>';
}
function iconHouse() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 3 8v2h2v9h6v-5h2v5h6v-9h2V8l-9-5Zm4 14h-2v-5H10v5H8v-7.2l4-2.22 4 2.22V17Z" fill="currentColor"/></svg>';
}
function iconPay() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 7.5A2.5 2.5 0 0 1 5.5 5h11A2.5 2.5 0 0 1 19 7.5v7a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 3 14.5v-7ZM11 8.2a2.3 2.3 0 1 0 0 4.6 2.3 2.3 0 0 0 0-4.6ZM16 18.5V17H6.2v1.5A1.5 1.5 0 0 0 7.7 21H19.5A2.5 2.5 0 0 0 22 18.5V9h-1.5v9.5c0 .28-.22.5-.5.5H16Z" fill="currentColor"/></svg>';
}
function iconSettings() {
  return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19.4 13.5a7.7 7.7 0 0 0 .1-1.5 7.7 7.7 0 0 0-.1-1.5l2-1.6-2-3.4-2.4 1a7.4 7.4 0 0 0-2.6-1.5l-.4-2.6h-4l-.4 2.6a7.4 7.4 0 0 0-2.6 1.5l-2.4-1-2 3.4 2 1.6a7.7 7.7 0 0 0-.1 1.5 7.7 7.7 0 0 0 .1 1.5l-2 1.6 2 3.4 2.4-1a7.4 7.4 0 0 0 2.6 1.5l.4 2.6h4l.4-2.6a7.4 7.4 0 0 0 2.6-1.5l2.4 1 2-3.4-2-1.6ZM12 15.5A3.5 3.5 0 1 1 12 8.5a3.5 3.5 0 0 1 0 7Z" fill="currentColor"/></svg>';
}
