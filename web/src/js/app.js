import { parseBackup, serializeBackup, summaryText } from './backup.js';
import {
  applyStatementBalance,
  clearPayment,
  nextUnpaidOccurrence,
  paidTowardForSave,
  recordPayment,
  remainingOwed,
} from './calculator.js';
import { epochDay, fromEpochDay, todayIso } from './dates.js';
import { parse, sanitizeInput } from './money.js';
import { editCardPath, editEventPath, listPath, parseHash } from './router.js';
import { previousStrictlyBefore, statementOnOrBefore } from './schedule.js';
import {
  clearBudget,
  deleteCard,
  deleteEvent,
  loadAll,
  persistentStorageGranted,
  replaceBudget,
  requestPersistentStorage,
  saveCard,
  saveEvent,
  saveTheme,
} from './store.js';
import {
  barHtml,
  cardPaySlotHtml,
  menuHtml,
  modalHtml,
  paidSlotHtml,
  sheetHtml,
  viewHtml,
} from './ui.js';

const state = {
  ready: false,
  failed: false,
  events: [],
  cards: [],
  theme: 'SYSTEM',
  today: todayIso(),
  snoozed: new Set(),
  menuOpen: false,
  route: parseHash(location.hash),
  sheet: null,
  modal: null,
  pendingImport: null,
  persisted: false,
  revision: 0,
  saving: false,
};

const keys = { bar: '', view: '', menu: '', sheet: '', modal: '' };
let toastTimer = 0;

const bar = document.getElementById('bar');
const view = document.getElementById('view');
const menu = document.getElementById('menu');
const sheet = document.getElementById('sheet');
const modal = document.getElementById('modal');
const toastEl = document.getElementById('toast');
const importFile = document.getElementById('import-file');

document.addEventListener('click', onClick);
document.addEventListener('submit', onSubmit);
document.addEventListener('input', onInput);
document.addEventListener('change', onChange);
document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  if (state.modal?.type === 'message' || state.modal?.type?.startsWith('confirm')) {
    state.modal = null;
    state.pendingImport = null;
    paint();
    return;
  }
  if (state.sheet) {
    state.sheet = null;
    paint();
    return;
  }
  if (state.menuOpen) {
    state.menuOpen = false;
    paint();
  }
});
window.addEventListener('hashchange', () => {
  state.route = parseHash(location.hash);
  state.menuOpen = false;
  state.sheet = null;
  state.modal = null;
  paint();
});
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') return;
  const next = todayIso();
  if (next !== state.today) {
    state.today = next;
    state.revision += 1;
    paint();
  }
});
importFile.addEventListener('change', async () => {
  const file = importFile.files?.[0];
  importFile.value = '';
  if (!file) return;
  let text = '';
  try {
    text = await file.text();
  } catch {
    message('Couldn’t read that file', 'Choose the JSON backup again.');
    return;
  }
  const parsed = parseBackup(text);
  if (!parsed.ok) {
    message('Can’t import that file', parsed.error);
    return;
  }
  state.pendingImport = parsed.data;
  state.modal = { type: 'confirm-import', summary: summaryText(parsed.summary) };
  paint();
});

const colorQuery = window.matchMedia('(prefers-color-scheme: dark)');
colorQuery.addEventListener('change', () => applyTheme(state.theme));

boot();
registerServiceWorker();

async function boot() {
  try {
    const data = await loadAll();
    state.events = data.events;
    state.cards = data.cards;
    state.theme = data.theme || 'SYSTEM';
    mirrorTheme(state.theme);
    state.ready = true;
    state.persisted = await persistentStorageGranted();
    paint();
    requestPersistentStorage().then(async (granted) => {
      state.persisted = granted || await persistentStorageGranted();
      paint();
    });
  } catch {
    state.failed = true;
    state.ready = true;
    paint();
  }
}

function paint() {
  const routeKey = JSON.stringify(state.route);
  const barKey = `${routeKey}:${state.ready}`;
  if (keys.bar !== barKey) {
    bar.innerHTML = barHtml(state);
    keys.bar = barKey;
  }
  const settingsExtra = state.route.name === 'settings' ? `:${state.theme}:${state.persisted}` : '';
  const editing = state.route.name === 'edit-event' || state.route.name === 'edit-card';
  const viewKey = `${routeKey}:${editing ? 'form' : state.revision}:${state.ready}:${state.failed}${settingsExtra}`;
  const viewChanged = keys.view !== viewKey;
  if (viewChanged) {
    view.innerHTML = viewHtml(state);
    keys.view = viewKey;
    window.scrollTo(0, 0);
  }
  syncLive();
  const menuKey = `${state.menuOpen}:${routeKey}`;
  if (keys.menu !== menuKey) {
    menu.innerHTML = menuHtml(state);
    keys.menu = menuKey;
  }
  const sheetKey = state.sheet
    ? `${state.sheet.source}:${state.sheet.id}:${state.sheet.due}:${state.revision}`
    : '';
  if (keys.sheet !== sheetKey) {
    sheet.innerHTML = sheetHtml(state);
    keys.sheet = sheetKey;
  }
  const modalKey = `${JSON.stringify(state.modal)}:${routeKey}:${[...state.snoozed].join(',')}:${state.revision}:${state.ready}`;
  if (keys.modal !== modalKey) {
    modal.innerHTML = modalHtml(state);
    keys.modal = modalKey;
    const field = modal.querySelector('input');
    if (field && state.route.name === 'home' && !state.modal) field.focus();
  }
  applyTheme(state.theme);
}

let liveKey = '';

function syncLive() {
  const key = `${state.revision}:${JSON.stringify(state.route)}:${state.ready}`;
  if (key === liveKey) return;
  liveKey = key;
  const paid = view.querySelector('[data-live="paid"]');
  if (paid) paid.innerHTML = paidSlotHtml(state);
  const cardPay = view.querySelector('[data-live="card-pay"]');
  if (cardPay) cardPay.innerHTML = cardPaySlotHtml(state);
}

function onClick(event) {
  const button = event.target.closest('[data-action]');
  if (!button || button.closest('form') && button.type === 'submit') return;
  const action = button.dataset.action;
  if (action === 'menu') {
    state.menuOpen = true;
    paint();
  } else if (action === 'close-menu') {
    state.menuOpen = false;
    paint();
  } else if (action === 'nav') {
    go(button.dataset.path);
  } else if (action === 'breakdown') {
    go(`/breakdown/${button.dataset.window}`);
  } else if (action === 'sheet') {
    state.sheet = { source: button.dataset.source, id: Number(button.dataset.id), due: button.dataset.due };
    paint();
  } else if (action === 'close-sheet') {
    state.sheet = null;
    paint();
  } else if (action === 'edit-line') {
    const id = Number(button.dataset.id);
    const source = button.dataset.source;
    state.sheet = null;
    go(source === 'CARD' ? editCardPath(id) : editEventPath(source === 'MORTGAGE' ? 'MORTGAGE' : 'BILL', id));
  } else if (action === 'mark-paid') {
    markPaid(Number(button.dataset.id));
  } else if (action === 'undo-paid') {
    undoPaid(Number(button.dataset.id));
  } else if (action === 'pay-full') {
    payFull(Number(button.dataset.id));
  } else if (action === 'record-payment') {
    recordPartial(Number(button.dataset.id));
  } else if (action === 'clear-payment') {
    clearCardPayment(Number(button.dataset.id));
  } else if (action === 'chip') {
    setChip(button);
  } else if (action === 'theme') {
    setTheme(button.dataset.theme);
  } else if (action === 'export') {
    exportBackup();
  } else if (action === 'import') {
    importFile.click();
  } else if (action === 'persist') {
    askPersist();
  } else if (action === 'erase') {
    state.modal = { type: 'confirm-erase' };
    paint();
  } else if (action === 'confirm-erase') {
    eraseAll();
  } else if (action === 'ask-delete') {
    askDelete(button.dataset.target, Number(button.dataset.id));
  } else if (action === 'confirm-delete') {
    confirmDelete();
  } else if (action === 'confirm-import') {
    confirmImport();
  } else if (action === 'cancel-modal') {
    state.modal = null;
    state.pendingImport = null;
    paint();
  } else if (action === 'skip-statement') {
    state.snoozed.add(Number(button.dataset.id));
    paint();
  }
}

function onSubmit(event) {
  const form = event.target;
  if (!(form instanceof HTMLFormElement)) return;
  event.preventDefault();
  if (form.dataset.action === 'save-event') saveEventForm(form);
  else if (form.dataset.action === 'save-card') saveCardForm(form);
  else if (form.dataset.action === 'save-statement') saveStatementForm(form);
}

function onInput(event) {
  const input = event.target;
  if (!(input instanceof HTMLInputElement) || !input.classList.contains('js-money')) return;
  const clean = sanitizeInput(input.value);
  if (clean !== input.value) {
    input.value = clean;
    const end = clean.length;
    input.setSelectionRange(end, end);
  }
}

function onChange(event) {
  const input = event.target;
  if (!(input instanceof HTMLInputElement) || input.name !== 'ongoing') return;
  const panel = input.form?.querySelector('[data-panel="end"]');
  if (panel) panel.hidden = input.checked;
}

function setChip(button) {
  const form = button.closest('form');
  if (!form) return;
  const group = button.dataset.group;
  const hidden = form.elements[group];
  hidden.value = button.dataset.value;
  form.querySelectorAll(`[data-group="${group}"]`).forEach((chip) => {
    chip.setAttribute('aria-pressed', chip === button ? 'true' : 'false');
  });
  if (group === 'recurrence') {
    const panel = form.querySelector('[data-panel="custom"]');
    if (panel) panel.hidden = hidden.value !== 'CUSTOM';
  }
  if (group === 'dueMode') {
    const days = form.querySelector('[data-panel="days"]');
    const dom = form.querySelector('[data-panel="dom"]');
    if (days) days.hidden = hidden.value !== 'DAYS_AFTER_STATEMENT';
    if (dom) dom.hidden = hidden.value !== 'DAY_OF_MONTH';
  }
}

async function saveEventForm(form) {
  if (state.saving) return;
  const kind = form.dataset.kind;
  const id = form.dataset.id ? Number(form.dataset.id) : null;
  const existing = id ? state.events.find((event) => event.id === id) : null;
  const scheduleOnly = kind === 'PAY';
  clearErrors(form);
  let ok = true;
  const name = form.elements.name.value.trim();
  if (!name) {
    setError(form, 'name', 'Add a name');
    ok = false;
  }
  let amountCents = 0;
  const amountRaw = form.elements.amount.value.trim();
  if (!scheduleOnly) {
    const parsed = parse(amountRaw);
    if (parsed == null || parsed <= 0) {
      setError(form, 'amount', 'Enter an amount greater than zero');
      ok = false;
    } else amountCents = parsed;
  } else if (amountRaw !== '') {
    const parsed = parse(amountRaw);
    if (parsed == null) {
      setError(form, 'amount', 'Enter a dollar amount, or leave it blank');
      ok = false;
    } else amountCents = parsed;
  }
  const recurrence = form.elements.recurrence.value;
  let interval = 1;
  if (recurrence === 'CUSTOM') {
    interval = Number(form.elements.interval.value);
    if (!Number.isInteger(interval) || interval < 1) {
      setError(form, 'interval', 'Use a number of 1 or more');
      ok = false;
    }
  }
  const start = form.elements.start.value;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start)) {
    setError(form, 'start', 'Choose a date');
    ok = false;
  }
  const ongoing = form.elements.ongoing.checked;
  const end = form.elements.end?.value || '';
  if (!ongoing && end < start) {
    setError(form, 'end', 'End date can’t be before the start date');
    ok = false;
  }
  if (!ok) return;
  const event = {
    name: name.slice(0, 80),
    amountCents,
    kind,
    recurrence,
    interval: recurrence === 'CUSTOM' ? interval : 1,
    customUnit: form.elements.customUnit?.value || 'MONTHS',
    startEpochDay: epochDay(start),
    endEpochDay: ongoing ? null : epochDay(end),
    notes: form.elements.notes.value.trim().slice(0, 400),
    paidThroughEpochDay: existing?.paidThroughEpochDay ?? null,
    autoPay: !scheduleOnly && Boolean(form.elements.autoPay?.checked),
  };
  if (existing) event.id = existing.id;
  await runSave(async () => {
    await saveEvent(event);
    await reload();
    go(listPath(kind));
  });
}

async function saveCardForm(form) {
  if (state.saving) return;
  const id = form.dataset.id ? Number(form.dataset.id) : null;
  const existing = id ? state.cards.find((card) => card.id === id) : null;
  clearErrors(form);
  let ok = true;
  const name = form.elements.name.value.trim();
  if (!name) {
    setError(form, 'name', 'Add a name');
    ok = false;
  }
  const statementDay = Number(form.elements.statementDay.value);
  if (!Number.isInteger(statementDay) || statementDay < 1 || statementDay > 31) {
    setError(form, 'statementDay', 'Use a day from 1 to 31');
    ok = false;
  }
  const dueMode = form.elements.dueMode.value;
  const daysAfter = Number(form.elements.daysAfter.value);
  const dueDay = Number(form.elements.dueDay.value);
  if (dueMode === 'DAYS_AFTER_STATEMENT' && (!Number.isInteger(daysAfter) || daysAfter < 0 || daysAfter > 90)) {
    setError(form, 'daysAfter', 'Use 0 to 90 days');
    ok = false;
  }
  if (dueMode === 'DAY_OF_MONTH' && (!Number.isInteger(dueDay) || dueDay < 1 || dueDay > 31)) {
    setError(form, 'dueDay', 'Use a day from 1 to 31');
    ok = false;
  }
  const amountRaw = form.elements.amount.value.trim();
  const parsedAmount = amountRaw === '' ? 0 : parse(amountRaw);
  if (parsedAmount == null) {
    setError(form, 'amount', 'Enter a dollar amount, or leave it blank');
    ok = false;
  }
  if (!ok || parsedAmount == null || !Number.isInteger(statementDay)) return;
  const amountChanged = existing != null && parsedAmount !== existing.amountCents;
  const cycleKey = existing == null
    ? null
    : amountChanged
      ? statementOnOrBefore(statementDay, state.today)
      : existing.lastCapturedCycleKey;
  const card = {
    name: name.slice(0, 80),
    statementDay,
    dueMode,
    daysAfterStatement: Number.isInteger(daysAfter) ? Math.min(90, Math.max(0, daysAfter)) : 21,
    dueDay: Number.isInteger(dueDay) ? Math.min(31, Math.max(1, dueDay)) : 10,
    amountCents: parsedAmount,
    notes: form.elements.notes.value.trim().slice(0, 400),
    lastCapturedCycleKey: cycleKey,
    paidTowardCents: paidTowardForSave(existing, cycleKey),
    autoPay: Boolean(form.elements.autoPay?.checked),
  };
  if (existing) card.id = existing.id;
  await runSave(async () => {
    await saveCard(card);
    await reload();
    go('/cards');
  });
}

async function saveStatementForm(form) {
  const card = state.cards.find((item) => item.id === Number(form.dataset.card));
  if (!card) return;
  const parsed = parse(form.elements.amount.value);
  if (parsed == null) {
    setError(form, 'amount', 'Enter a dollar amount, like 240.18');
    return;
  }
  await runSave(async () => {
    await saveCard(applyStatementBalance(card, parsed, form.dataset.cycle));
    state.snoozed.delete(card.id);
    await reload();
  });
}

async function markPaid(id) {
  const event = state.events.find((item) => item.id === id);
  if (!event || event.kind === 'PAY') return;
  const next = nextUnpaidOccurrence(event);
  if (!next) return;
  await runSave(async () => {
    await saveEvent({ ...event, paidThroughEpochDay: epochDay(next) });
    await reload();
  });
}

async function undoPaid(id) {
  const event = state.events.find((item) => item.id === id);
  if (!event || event.paidThroughEpochDay == null) return;
  const previous = previousStrictlyBefore(event, fromEpochDay(event.paidThroughEpochDay));
  await runSave(async () => {
    await saveEvent({ ...event, paidThroughEpochDay: previous ? epochDay(previous) : null });
    await reload();
  });
}

async function payFull(id) {
  const card = state.cards.find((item) => item.id === id);
  if (!card) return;
  const remaining = remainingOwed(card);
  if (remaining <= 0) return;
  await runSave(async () => {
    await saveCard(recordPayment(card, remaining));
    await reload();
  });
}

async function recordPartial(id) {
  const card = state.cards.find((item) => item.id === id);
  const input = document.querySelector(`[data-pay-for="${id}"]`);
  const error = document.querySelector(`[data-error-for="pay-${id}"]`);
  if (!card || !(input instanceof HTMLInputElement)) return;
  const parsed = parse(input.value);
  if (parsed == null || parsed <= 0) {
    if (error) error.textContent = 'Enter a payment amount';
    return;
  }
  if (error) error.textContent = '';
  await runSave(async () => {
    await saveCard(recordPayment(card, parsed));
    await reload();
  });
}

async function clearCardPayment(id) {
  const card = state.cards.find((item) => item.id === id);
  if (!card) return;
  await runSave(async () => {
    await saveCard(clearPayment(card));
    await reload();
  });
}

async function setTheme(theme) {
  state.theme = theme;
  mirrorTheme(theme);
  paint();
  try {
    await saveTheme(theme);
  } catch {
    toast('Couldn’t save the appearance setting.');
  }
}

function exportBackup() {
  const json = serializeBackup({
    events: state.events,
    cards: state.cards,
    theme: state.theme,
  });
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `calbudget-backup-${state.today}.json`;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast('Backup downloaded.');
}

async function askPersist() {
  const granted = await requestPersistentStorage();
  state.persisted = granted || await persistentStorageGranted();
  paint();
  toast(state.persisted
    ? 'This browser will try to keep your data.'
    : 'This browser did not grant persistent storage. Export a backup to be safe.');
}

async function eraseAll() {
  await runSave(async () => {
    await clearBudget();
    state.modal = null;
    state.snoozed = new Set();
    await reload();
    toast('Erased bills, cards, housing, and pay schedules.');
  });
}

function askDelete(target, id) {
  if (target === 'card') {
    const card = state.cards.find((item) => item.id === id);
    state.modal = {
      type: 'confirm-delete',
      target,
      id,
      name: card?.name || 'this card',
      body: 'The statement balance is removed with the card.',
    };
  } else {
    const event = state.events.find((item) => item.id === id);
    state.modal = {
      type: 'confirm-delete',
      target,
      id,
      name: event?.name || 'this item',
      body: 'This removes it from the device. The home number updates immediately.',
    };
  }
  paint();
}

async function confirmDelete() {
  const modalState = state.modal;
  if (!modalState || modalState.type !== 'confirm-delete') return;
  await runSave(async () => {
    if (modalState.target === 'card') {
      await deleteCard(modalState.id);
      state.snoozed.delete(modalState.id);
      state.modal = null;
      await reload();
      go('/cards');
    } else {
      const event = state.events.find((item) => item.id === modalState.id);
      await deleteEvent(modalState.id);
      state.modal = null;
      await reload();
      go(listPath(event?.kind || 'BILL'));
    }
  });
}

async function confirmImport() {
  const data = state.pendingImport;
  if (!data) return;
  await runSave(async () => {
    await replaceBudget(data);
    state.pendingImport = null;
    state.modal = null;
    state.snoozed = new Set();
    state.sheet = null;
    await reload();
    if (data.theme) {
      state.theme = data.theme;
      mirrorTheme(data.theme);
    }
    toast('Backup restored on this device.');
    go('/');
  });
}

async function reload() {
  const data = await loadAll();
  state.events = data.events;
  state.cards = data.cards;
  if (data.theme) state.theme = data.theme;
  state.revision += 1;
  mirrorTheme(state.theme);
  paint();
}

async function runSave(work) {
  if (state.saving) return;
  state.saving = true;
  try {
    await work();
  } catch {
    toast('Couldn’t save that. The rest of your data is still on this device.');
  } finally {
    state.saving = false;
  }
}

function go(path) {
  const next = `#${path}`;
  if (location.hash === next || (path === '/' && (location.hash === '' || location.hash === '#' || location.hash === '#/'))) {
    state.route = parseHash(next);
    state.menuOpen = false;
    state.sheet = null;
    state.modal = null;
    paint();
    return;
  }
  location.hash = path;
}

function message(title, body) {
  state.modal = { type: 'message', title, body };
  paint();
}

function toast(text) {
  toastEl.hidden = false;
  toastEl.textContent = text;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toastEl.hidden = true;
  }, 3200);
}

function clearErrors(form) {
  form.querySelectorAll('.field-error').forEach((node) => {
    node.textContent = '';
  });
  form.querySelectorAll('.invalid').forEach((node) => node.classList.remove('invalid'));
}

function setError(form, name, text) {
  const slot = form.querySelector(`[data-error-for="${name}"]`);
  if (slot) slot.textContent = text;
  const field = form.elements[name];
  const box = field?.closest('.money-input') || field;
  box?.classList.add('invalid');
}

function applyTheme(theme) {
  const root = document.documentElement;
  if (theme === 'LIGHT') root.dataset.theme = 'light';
  else if (theme === 'DARK') root.dataset.theme = 'dark';
  else delete root.dataset.theme;
  const dark = theme === 'DARK' || (theme !== 'LIGHT' && colorQuery.matches);
  const color = dark ? '#121412' : '#F7F6F3';
  document.querySelectorAll('meta[name="theme-color"]').forEach((meta) => {
    meta.content = color;
  });
}

function mirrorTheme(theme) {
  try {
    if (theme === 'LIGHT') localStorage.setItem('calbudget-theme', 'light');
    else if (theme === 'DARK') localStorage.setItem('calbudget-theme', 'dark');
    else localStorage.removeItem('calbudget-theme');
  } catch {
    /* The indexed database still has the choice. */
  }
}

function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  let hadController = Boolean(navigator.serviceWorker.controller);
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController) {
      hadController = true;
      return;
    }
    window.location.reload();
  });
  navigator.serviceWorker.register('/sw.js').catch(() => {
    /* Offline support waits until the next visit if registration fails. */
  });
}
