/**
 * On-device database. IndexedDB is the only copy of the user's budget.
 *
 * Schema version history:
 *   1 — events, cards, and meta. Events store paidThroughEpochDay and autoPay.
 *       Cards store paidTowardCents and autoPay.
 *
 * Later versions must add stores, indexes, or fields inside upgradeSchema.
 * This file never deletes the database, so an update cannot wipe existing rows.
 * Erase and import clear object stores on purpose; they do not drop the schema.
 */

export const SCHEMA_VERSION = 1;
const DB_NAME = 'calbudget';

let opening = null;

export function openDatabase() {
  if (opening) return opening;
  opening = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('This browser has no IndexedDB'));
      return;
    }
    const request = indexedDB.open(DB_NAME, SCHEMA_VERSION);
    request.onupgradeneeded = (event) => {
      upgradeSchema(request.result, event.oldVersion);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  opening = opening.catch((error) => {
    opening = null;
    throw error;
  });
  return opening;
}

/** Additive upgrades only. oldVersion 0 means a brand-new database. */
export function upgradeSchema(db, oldVersion) {
  if (oldVersion < 1) {
    const events = db.createObjectStore('events', { keyPath: 'id', autoIncrement: true });
    events.createIndex('kind', 'kind', { unique: false });
    db.createObjectStore('cards', { keyPath: 'id', autoIncrement: true });
    db.createObjectStore('meta', { keyPath: 'key' });
  }
}

export async function loadAll() {
  const db = await openDatabase();
  const [events, cards, themeRow] = await Promise.all([
    getAll(db, 'events'),
    getAll(db, 'cards'),
    getKey(db, 'meta', 'theme'),
  ]);
  events.sort((a, b) => a.id - b.id);
  cards.sort((a, b) => a.id - b.id);
  const theme = themeRow && typeof themeRow.value === 'string' ? themeRow.value : 'SYSTEM';
  return { events, cards, theme };
}

export async function saveEvent(event) {
  const db = await openDatabase();
  const storeName = 'events';
  return putRecord(db, storeName, event);
}

export async function deleteEvent(id) {
  const db = await openDatabase();
  await deleteRecord(db, 'events', id);
}

export async function saveCard(card) {
  const db = await openDatabase();
  return putRecord(db, 'cards', card);
}

export async function deleteCard(id) {
  const db = await openDatabase();
  await deleteRecord(db, 'cards', id);
}

export async function saveTheme(theme) {
  const db = await openDatabase();
  await putRecord(db, 'meta', { key: 'theme', value: theme });
}

/** Deletes bills, cards, housing, and pay schedules. Theme stays. */
export async function clearBudget() {
  const db = await openDatabase();
  await run(db, ['events', 'cards'], (stores) => {
    stores.events.clear();
    stores.cards.clear();
  });
}

/** Replaces bills, cards, and (when present) the theme. Used by import. */
export async function replaceBudget({ events, cards, theme }) {
  const db = await openDatabase();
  const names = theme ? ['events', 'cards', 'meta'] : ['events', 'cards'];
  await run(db, names, (stores) => {
    stores.events.clear();
    stores.cards.clear();
    for (const event of events) stores.events.put(event);
    for (const card of cards) stores.cards.put(card);
    if (theme) stores.meta.put({ key: 'theme', value: theme });
  });
}

export async function requestPersistentStorage() {
  try {
    if (!navigator.storage?.persist) return false;
    return await navigator.storage.persist();
  } catch {
    return false;
  }
}

export async function persistentStorageGranted() {
  try {
    if (!navigator.storage?.persisted) return false;
    return await navigator.storage.persisted();
  } catch {
    return false;
  }
}

function getAll(db, storeName) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readonly');
    const request = tx.objectStore(storeName).getAll();
    request.onsuccess = () => resolve(request.result ?? []);
    request.onerror = () => reject(request.error);
  });
}

function getKey(db, storeName, key) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readonly');
    const request = tx.objectStore(storeName).get(key);
    request.onsuccess = () => resolve(request.result ?? null);
    request.onerror = () => reject(request.error);
  });
}

function putRecord(db, storeName, value) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    const request = tx.objectStore(storeName).put(value);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function deleteRecord(db, storeName, key) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    const request = tx.objectStore(storeName).delete(key);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
  });
}

function run(db, storeNames, work) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeNames, 'readwrite');
    const stores = {};
    for (const name of storeNames) stores[name] = tx.objectStore(name);
    try {
      work(stores);
    } catch (error) {
      reject(error);
      return;
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error('The save was aborted'));
  });
}
