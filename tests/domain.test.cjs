const { test } = require('node:test');
const assert = require('node:assert/strict');
const D = require('../domain.js');
const today = '2026-11-02';

function withHabit() {
  return D.createHabit(D.createState(), { name: 'Read', unit: 'pages', target: 20 }, 'read', '2026-11-01');
}

test('partial commits accumulate and quick complete adds only the remainder', () => {
  let state = D.addLog(withHabit(), { habitId: 'read', amount: 5, date: today, id: 'one' });
  state = D.addLog(state, { habitId: 'read', amount: 7, date: today, id: 'two' });
  assert.equal(D.progress(state.habits[0], state.logs, today).total, 12);
  state = D.completeGoal(state, 'read', today, 'three');
  assert.equal(state.logs.at(-1).amount, 8);
  assert.equal(D.progress(state.habits[0], state.logs, today).complete, true);
  assert.equal(D.completeGoal(state, 'read', today, 'four'), state);
});

test('over-target amounts are retained but capped for scoring', () => {
  const state = D.addLog(withHabit(), { habitId: 'read', amount: 30, date: today, id: 'one' });
  assert.equal(D.progress(state.habits[0], state.logs, today).total, 30);
  assert.equal(D.dailyScore(state, today), 1);
});

test('rejects invalid amounts, targets, blank names and units', () => {
  for (const amount of [0, -1, 'oops', Infinity, 1000001, 0.00001]) {
    assert.throws(() => D.addLog(withHabit(), { habitId: 'read', amount, date: today, id: 'bad' }));
  }
  assert.throws(() => D.createHabit(D.createState(), { name: ' ', target: 2, unit: 'pages' }, 'x', today));
  assert.throws(() => D.createHabit(D.createState(), { name: 'Read', target: 2, unit: ' ' }, 'x', today));
});

test('fractional progress avoids floating point completion errors', () => {
  let state = D.createHabit(D.createState(), { name: 'Walk', target: 0.3, unit: 'km' }, 'walk', today);
  state = D.addLog(state, { habitId: 'walk', amount: 0.1, date: today, id: 'one' });
  state = D.addLog(state, { habitId: 'walk', amount: 0.2, date: today, id: 'two' });
  assert.equal(D.progress(state.habits[0], state.logs, today).total, 0.3);
  assert.equal(D.completeGoal(state, 'walk', today, 'three'), state);
});

test('overall score averages percentages rather than incompatible units', () => {
  let state = D.createHabit(withHabit(), { name: 'Move', target: 60, unit: 'minutes' }, 'move', today);
  state = D.addLog(state, { habitId: 'read', amount: 20, date: today, id: 'one' });
  assert.equal(D.dailyScore(state, today), 0.5);
  assert.equal(D.dailyScore(state, '2026-10-31'), null);
});

test('editing a target preserves previous-day target and result', () => {
  let state = D.addLog(withHabit(), { habitId: 'read', amount: 20, date: '2026-11-01', id: 'one' });
  state = D.updateHabit(state, 'read', { name: 'Read more', target: 40, unit: 'pages' }, today);
  assert.equal(D.progress(state.habits[0], state.logs, '2026-11-01').ratio, 1);
  assert.equal(D.progress(state.habits[0], state.logs, today).target, 40);
  assert.throws(() => D.updateHabit(state, 'read', { name: 'Read', target: 40, unit: 'books' }, today));
});

test('pausing and archiving retain historical activity and remove current expectations', () => {
  let state = D.addLog(withHabit(), { habitId: 'read', amount: 20, date: '2026-11-01', id: 'one' });
  state = D.changeStatus(state, 'read', 'paused', today);
  assert.equal(D.dailyScore(state, today), null);
  assert.equal(D.dailyScore(state, '2026-11-01'), 1);
  assert.throws(() => D.addLog(state, { habitId: 'read', amount: 2, date: today, id: 'two' }));
  state = D.changeStatus(state, 'read', 'archived', today);
  assert.equal(state.logs.length, 1);
  state = D.changeStatus(state, 'read', 'active', today);
  assert.equal(D.dailyScore(state, today), 0);
});

test('correction and removal immediately change derived totals', () => {
  let state = D.addLog(withHabit(), { habitId: 'read', amount: 20, date: today, id: 'one' });
  state = D.updateLog(state, 'one', 5, 'Correction');
  assert.equal(D.dailyScore(state, today), 0.25);
  state = D.removeLog(state, 'one');
  assert.equal(D.dailyScore(state, today), 0);
});

test('date arithmetic crosses month/year boundaries and leap days', () => {
  assert.equal(D.shiftDate('2026-01-01', -1), '2025-12-31');
  assert.equal(D.shiftDate('2024-02-28', 1), '2024-02-29');
  assert.equal(D.validDate('2026-02-30'), false);
  assert.deepEqual(D.daysEnding('2026-01-02', 3), ['2025-12-31', '2026-01-01', '2026-01-02']);
});

test('activity streak allows an unfinished today but not a missed yesterday', () => {
  let state = withHabit();
  state = D.addLog(state, { habitId: 'read', amount: 1, date: '2026-11-01', id: 'one' });
  assert.equal(D.streak(state, today), 1);
  assert.equal(D.streak(state, '2026-11-03'), 0);
  assert.equal(D.longestStreak(state, today), 1);
});

test('seed data is deterministic, internally consistent and storage-valid', () => {
  const state = D.createSampleState(today);
  assert.deepEqual(state, D.createSampleState(today));
  assert.equal(D.isValidState(state), true);
  assert.equal(state.habits.filter(habit => D.progress(habit, state.logs, today).complete).length, 3);
  assert.equal(D.isValidState({ ...state, logs: [{ amount: '<script>' }] }), false);
  assert.equal(D.isValidState(null), false);
  assert.equal(D.isValidState({ ...state, habits: [null] }), false);
  assert.equal(D.isValidState({ ...state, logs: [null] }), false);
  assert.equal(D.isValidState({ ...state, habits: [...state.habits, state.habits[0]] }), false);
});

test('domain operations do not mutate their inputs', () => {
  const state = withHabit();
  const before = structuredClone(state);
  D.addLog(state, { habitId: 'read', amount: 5, date: today, id: 'one' });
  D.changeStatus(state, 'read', 'paused', today);
  D.updateHabit(state, 'read', { name: 'Read more', unit: 'pages', target: 30 }, today);
  assert.deepEqual(state, before);
});
