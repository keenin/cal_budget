/**
 * Calendar dates as ISO strings (YYYY-MM-DD) and Unix epoch days.
 * Epoch days match java.time.LocalDate.toEpochDay (1970-01-01 = 0).
 * Day arithmetic uses integers only, so a timezone never shifts a due date.
 */

const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTHS_LONG = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export function lengthOfMonth(year, month) {
  if (month === 2) {
    const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
    return leap ? 29 : 28;
  }
  return [31, 0, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
}

export function parseIso(iso) {
  const match = ISO.exec(iso);
  if (!match) throw new Error(`Bad date: ${iso}`);
  const y = Number(match[1]);
  const m = Number(match[2]);
  const d = Number(match[3]);
  if (m < 1 || m > 12 || d < 1 || d > lengthOfMonth(y, m)) {
    throw new Error(`Bad date: ${iso}`);
  }
  return { y, m, d };
}

export function toIso(y, m, d) {
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** Howard Hinnant's days_from_civil, shifted so 1970-01-01 is 0. */
export function epochDay(iso) {
  const { y, m, d } = parseIso(iso);
  let year = y;
  if (m <= 2) year -= 1;
  const era = Math.floor(year / 400);
  const yoe = year - era * 400;
  const doy = Math.floor((153 * (m + (m > 2 ? -3 : 9)) + 2) / 5) + d - 1;
  const doe = yoe * 365 + Math.floor(yoe / 4) - Math.floor(yoe / 100) + doy;
  return era * 146097 + doe - 719468;
}

export function fromEpochDay(day) {
  if (!Number.isSafeInteger(day)) throw new Error('Bad epoch day');
  let z = day + 719468;
  const era = Math.floor(z / 146097);
  const doe = z - era * 146097;
  const yoe = Math.floor((doe - Math.floor(doe / 1460) + Math.floor(doe / 36524) - Math.floor(doe / 146096)) / 365);
  let y = yoe + era * 400;
  const doy = doe - (365 * yoe + Math.floor(yoe / 4) - Math.floor(yoe / 100));
  const mp = Math.floor((5 * doy + 2) / 153);
  const d = doy - Math.floor((153 * mp + 2) / 5) + 1;
  const m = mp + (mp < 10 ? 3 : -9);
  if (m <= 2) y += 1;
  return toIso(y, m, d);
}

export function isBefore(a, b) {
  return a < b;
}

export function isAfter(a, b) {
  return a > b;
}

export function plusDays(iso, days) {
  return fromEpochDay(epochDay(iso) + days);
}

/** java.time.LocalDate.plusMonths: the day clamps to the end of a short month. */
export function plusMonths(iso, months) {
  const { y, m, d } = parseIso(iso);
  const index = y * 12 + (m - 1) + months;
  const ny = Math.floor(index / 12);
  const nm = index - ny * 12 + 1;
  const nd = Math.min(d, lengthOfMonth(ny, nm));
  return toIso(ny, nm, nd);
}

export function clampedDay(year, month, day) {
  return toIso(year, month, Math.min(day, lengthOfMonth(year, month)));
}

export function formatWindow(iso) {
  const { m, d } = parseIso(iso);
  return `${MONTHS[m - 1]} ${d}`;
}

export function formatLong(iso) {
  const { y, m, d } = parseIso(iso);
  return `${MONTHS_LONG[m - 1]} ${d}, ${y}`;
}

export function formatField(iso) {
  const { y, m, d } = parseIso(iso);
  return `${MONTHS[m - 1]} ${d}, ${y}`;
}

export function todayIso(now = new Date()) {
  return toIso(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

export function spanLabel(start, end) {
  if (end == null || isBefore(end, start)) return null;
  const startText = formatWindow(start);
  if (start === end) return startText;
  return `${startText} – ${formatWindow(end)}`;
}
