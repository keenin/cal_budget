/**
 * US dollar amounts as integer cents. Parsing and formatting never use
 * floating-point division, so 10 cents plus 20 cents is 30 cents.
 */

const inputPattern = /^\d+(\.\d{0,2})?$/;

export function parse(raw) {
  const cleaned = String(raw ?? '').trim().replaceAll('$', '').replaceAll(',', '').replaceAll(' ', '');
  if (cleaned === '' || !inputPattern.test(cleaned)) return null;
  const parts = cleaned.split('.');
  if (!/^\d+$/.test(parts[0])) return null;
  const dollars = Number(parts[0]);
  if (!Number.isSafeInteger(dollars) || dollars > 100_000_000) return null;
  let cents = 0;
  if (parts.length === 2 && parts[1] !== '') {
    cents = Number(parts[1].padEnd(2, '0').slice(0, 2));
    if (!Number.isSafeInteger(cents)) return null;
  }
  return dollars * 100 + cents;
}

export function format(cents) {
  if (!Number.isSafeInteger(cents)) {
    throw new TypeError('Money must be an integer number of cents');
  }
  const negative = cents < 0;
  const abs = negative ? -cents : cents;
  const dollars = Math.trunc(abs / 100);
  const fraction = abs % 100;
  const grouped = String(dollars).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negative ? '-' : ''}$${grouped}.${String(fraction).padStart(2, '0')}`;
}

/** Plain number for a text field that already shows a dollar prefix. */
export function toInput(cents) {
  if (!Number.isSafeInteger(cents)) {
    throw new TypeError('Money must be an integer number of cents');
  }
  const negative = cents < 0;
  const abs = negative ? -cents : cents;
  const dollars = Math.trunc(abs / 100);
  const fraction = abs % 100;
  const body = fraction === 0
    ? String(dollars)
    : `${dollars}.${String(fraction).padStart(2, '0')}`;
  return negative ? `-${body}` : body;
}

export function sanitizeInput(raw) {
  const cleaned = String(raw ?? '').replace(/[^\d.]/g, '');
  const dot = cleaned.indexOf('.');
  if (dot < 0) return cleaned.slice(0, 9);
  const whole = cleaned.slice(0, dot).slice(0, 9);
  const fraction = cleaned.slice(dot + 1).replace(/\D/g, '').slice(0, 2);
  return `${whole}.${fraction}`;
}

export function isCents(value) {
  return Number.isSafeInteger(value);
}

export function addCents(a, b) {
  if (!Number.isSafeInteger(a) || !Number.isSafeInteger(b)) {
    throw new TypeError('Money must be an integer number of cents');
  }
  const sum = a + b;
  if (!Number.isSafeInteger(sum)) throw new RangeError('Amount is too large');
  return sum;
}
