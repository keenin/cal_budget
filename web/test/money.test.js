import assert from 'node:assert/strict';
import { test } from 'node:test';
import { addCents, format, parse, sanitizeInput, toInput } from '../src/js/money.js';

test('parses dollar text into integer cents', () => {
  assert.equal(parse('$240.18'), 24018);
  assert.equal(parse('20'), 2000);
  assert.equal(parse('0.5'), 50);
  assert.equal(parse('1,234.56'), 123456);
  assert.equal(parse(''), null);
  assert.equal(parse('12.345'), null);
  assert.equal(parse('12.3.4'), null);
  assert.equal(parse('1e2'), null);
  assert.equal(parse('100000001'), null);
  assert.equal(parse('10.10') + parse('0.20'), 1030);
});

test('formats cents without floating-point math', () => {
  assert.equal(format(123456), '$1,234.56');
  assert.equal(format(0), '$0.00');
  assert.equal(format(5), '$0.05');
  assert.equal(format(-2050), '-$20.50');
  assert.equal(toInput(2050), '20.50');
  assert.equal(toInput(2000), '20');
  assert.equal(sanitizeInput('20.5abc9'), '20.59');
  assert.equal(sanitizeInput('$1,234.567'), '1234.56');
  assert.equal(addCents(10, 20), 30);
  assert.equal(Number.isSafeInteger(addCents(parse('0.10'), parse('0.20'))), true);
});
