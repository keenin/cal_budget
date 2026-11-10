const LISTS = {
  BILL: '/bills',
  MORTGAGE: '/mortgage',
  PAY: '/pay',
};

export function listPath(kind) {
  return LISTS[kind] || '/';
}

export function editEventPath(kind, id) {
  const base = LISTS[kind];
  return `${base}/edit/${id == null ? 'new' : id}`;
}

export function editCardPath(id) {
  return `/cards/edit/${id == null ? 'new' : id}`;
}

export function parseHash(hash) {
  const raw = String(hash || '').replace(/^#/, '').replace(/^\//, '');
  const parts = raw.split('/').filter(Boolean);
  if (parts.length === 0) return { name: 'home' };
  if (parts[0] === 'breakdown' && ['until', 'next', 'following'].includes(parts[1])) {
    return { name: 'breakdown', window: parts[1] };
  }
  if (parts[0] === 'settings') return { name: 'settings' };
  if (parts[0] === 'cards') {
    if (parts[1] === 'edit') return { name: 'edit-card', id: parseId(parts[2]) };
    return { name: 'cards' };
  }
  const kind = kindFor(parts[0]);
  if (kind) {
    if (parts[1] === 'edit') return { name: 'edit-event', kind, id: parseId(parts[2]) };
    return { name: 'list', kind };
  }
  return { name: 'home' };
}

function kindFor(segment) {
  if (segment === 'bills') return 'BILL';
  if (segment === 'mortgage') return 'MORTGAGE';
  if (segment === 'pay') return 'PAY';
  return null;
}

function parseId(value) {
  if (value == null || value === 'new') return null;
  if (!/^\d+$/.test(value)) return Number.NaN;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : Number.NaN;
}
