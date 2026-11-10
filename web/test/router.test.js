import assert from 'node:assert/strict';
import { test } from 'node:test';
import { editCardPath, editEventPath, parseHash } from '../src/js/router.js';

test('hash routes match the Android screens', () => {
  assert.deepEqual(parseHash(''), { name: 'home' });
  assert.deepEqual(parseHash('#/'), { name: 'home' });
  assert.deepEqual(parseHash('#/breakdown/until'), { name: 'breakdown', window: 'until' });
  assert.deepEqual(parseHash('#/breakdown/next'), { name: 'breakdown', window: 'next' });
  assert.deepEqual(parseHash('#/breakdown/following'), { name: 'breakdown', window: 'following' });
  assert.deepEqual(parseHash('#/bills'), { name: 'list', kind: 'BILL' });
  assert.deepEqual(parseHash('#/mortgage/edit/new'), { name: 'edit-event', kind: 'MORTGAGE', id: null });
  assert.deepEqual(parseHash('#/pay/edit/4'), { name: 'edit-event', kind: 'PAY', id: 4 });
  assert.deepEqual(parseHash('#/cards/edit/7'), { name: 'edit-card', id: 7 });
  assert.deepEqual(parseHash('#/settings'), { name: 'settings' });
  assert.equal(editEventPath('BILL', null), '/bills/edit/new');
  assert.equal(editCardPath(7), '/cards/edit/7');
});
