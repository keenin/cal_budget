/**
 * Browser storage persistence and the dismissible warning.
 *
 * This module never reads or writes the budget database. Dismissal is a
 * timestamp in localStorage, separate from IndexedDB.
 */

export const STORAGE_NOTICE_KEY = 'calbudget-storage-notice-until';
export const STORAGE_NOTICE_SNOOZE_MS = 7 * 24 * 60 * 60 * 1000;

export function storageStatusLabel(persisted) {
  return persisted ? 'Storage: persistent' : 'Storage: may be cleared by browser';
}

export function shouldShowStorageNotice(persisted, dismissed) {
  return !persisted && !dismissed;
}

/**
 * If persist() exists, read persisted(). Call persist() only when the
 * browser has not already granted persistent storage.
 * Returns whether storage is persistent afterwards.
 */
export async function resolvePersistentStorage(storage) {
  try {
    if (typeof storage?.persist !== 'function') return false;
    if (typeof storage.persisted === 'function') {
      try {
        if (await storage.persisted()) return true;
      } catch {
        /* Fall through and ask. A failed check is not a grant. */
      }
    }
    return Boolean(await storage.persist());
  } catch {
    return false;
  }
}

export function browserStorage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export function isStorageNoticeDismissed(storage = browserStorage(), now = Date.now()) {
  if (!storage) return false;
  try {
    const until = Number(storage.getItem(STORAGE_NOTICE_KEY));
    return Number.isFinite(until) && until > now;
  } catch {
    return false;
  }
}

export function dismissStorageNotice(storage = browserStorage(), now = Date.now(), snoozeMs = STORAGE_NOTICE_SNOOZE_MS) {
  const until = now + snoozeMs;
  if (!storage) return until;
  try {
    storage.setItem(STORAGE_NOTICE_KEY, String(until));
  } catch {
    /* The banner still hides for this visit. */
  }
  return until;
}
