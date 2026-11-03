(function (root) {
  'use strict';

  const COLORS = ['green', 'blue', 'orange', 'purple', 'pink', 'teal'];
  const ICONS = ['book', 'activity', 'code', 'water', 'sun', 'journal'];
  const STATUSES = ['active', 'paused', 'archived'];

  function dateKey(date = new Date()) {
    return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0')].join('-');
  }

  // Calendar arithmetic at local noon avoids UTC day shifts and DST-length days.
  function shiftDate(key, offset) {
    const date = new Date(`${key}T12:00:00`);
    date.setDate(date.getDate() + offset);
    return dateKey(date);
  }

  function daysEnding(end, count) {
    return Array.from({ length: count }, (_, index) => shiftDate(end, index - count + 1));
  }

  function validDate(key) {
    return typeof key === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(key)
      && dateKey(new Date(`${key}T12:00:00`)) === key;
  }

  function round(value) {
    return Math.round((value + Number.EPSILON) * 1000) / 1000;
  }

  function positiveNumber(value) {
    const amount = Number(value);
    if (!Number.isFinite(amount) || amount <= 0 || amount > 1000000 || round(amount) <= 0) {
      throw new Error('Enter an amount between 0.001 and 1,000,000.');
    }
    return round(amount);
  }

  function createState() {
    return { version: 1, theme: 'dark', session: false, onboarded: false,
      profile: { name: 'Alex', bio: 'Building a better everyday.' }, habits: [], logs: [] };
  }

  function stateAt(habit, day) {
    if (day < habit.createdOn) return null;
    return habit.changes.filter(change => change.date <= day)
      .sort((a, b) => b.date.localeCompare(a.date))[0] || null;
  }

  function progress(habit, logs, day) {
    const settings = stateAt(habit, day);
    const total = round(logs.filter(log => log.habitId === habit.id && log.date === day)
      .reduce((sum, log) => sum + log.amount, 0));
    const target = settings?.target ?? habit.changes[0].target;
    const eligible = settings?.status === 'active';
    return { total, target, eligible, ratio: eligible ? Math.min(total / target, 1) : null,
      complete: eligible && total >= target };
  }

  function dailyScore(state, day) {
    const values = state.habits.map(habit => progress(habit, state.logs, day))
      .filter(item => item.eligible);
    return values.length ? values.reduce((sum, item) => sum + item.ratio, 0) / values.length : null;
  }

  function streak(state, today, habitId) {
    const dates = new Set(state.logs.filter(log => !habitId || log.habitId === habitId)
      .filter(log => log.date <= today).map(log => log.date));
    let day = dates.has(today) ? today : shiftDate(today, -1);
    let result = 0;
    while (dates.has(day)) {
      result += 1;
      day = shiftDate(day, -1);
    }
    return result;
  }

  function longestStreak(state, today, habitId) {
    const dates = [...new Set(state.logs.filter(log => (!habitId || log.habitId === habitId)
      && log.date <= today).map(log => log.date))].sort();
    let best = 0;
    let current = 0;
    dates.forEach((day, index) => {
      current = index && shiftDate(dates[index - 1], 1) === day ? current + 1 : 1;
      best = Math.max(best, current);
    });
    return best;
  }

  function normalizeHabit(values) {
    const name = String(values.name || '').trim();
    const unit = String(values.unit || '').trim();
    if (!name || name.length > 48) throw new Error('Give your repo a name of 1–48 characters.');
    if (!unit || unit.length > 20) throw new Error('Add a unit of 1–20 characters, like pages or minutes.');
    const description = String(values.description || '').trim();
    if (description.length > 160) throw new Error('Keep your description within 160 characters.');
    return { name, unit, description, target: positiveNumber(values.target),
      icon: ICONS.includes(values.icon) ? values.icon : 'book',
      color: COLORS.includes(values.color) ? values.color : 'green' };
  }

  function createHabit(state, values, id, day) {
    const { target, ...fields } = normalizeHabit(values);
    const habit = { ...fields, id, createdOn: day, changes: [{ date: day, target, status: 'active' }] };
    return { ...state, habits: [...state.habits, habit] };
  }

  function updateHabit(state, id, values, day) {
    const current = state.habits.find(habit => habit.id === id);
    if (!current) throw new Error('This repo could not be found.');
    const fields = normalizeHabit(values);
    if (fields.unit !== current.unit && state.logs.some(log => log.habitId === id)) {
      throw new Error('Units cannot change after logging progress. Create a new repo for a different unit.');
    }
    const previous = stateAt(current, day) || current.changes[0];
    const { target, ...details } = fields;
    return { ...state, habits: state.habits.map(habit => habit.id !== id ? habit : {
      ...habit, ...details, changes: [...habit.changes.filter(change => change.date !== day),
        { ...previous, date: day, target }].sort((a, b) => a.date.localeCompare(b.date))
    }) };
  }

  function changeStatus(state, id, status, day) {
    if (!STATUSES.includes(status)) throw new Error('Unknown repo status.');
    const current = state.habits.find(habit => habit.id === id);
    if (!current) throw new Error('This repo could not be found.');
    const settings = stateAt(current, day) || current.changes[0];
    return { ...state, habits: state.habits.map(habit => habit.id !== id ? habit : {
      ...habit, changes: [...habit.changes.filter(change => change.date !== day),
        { ...settings, date: day, status }].sort((a, b) => a.date.localeCompare(b.date))
    }) };
  }

  function addLog(state, { habitId, amount, note = '', date, id, time = new Date().toISOString() }) {
    const habit = state.habits.find(item => item.id === habitId);
    if (!habit || stateAt(habit, date)?.status !== 'active') throw new Error('Resume this repo before logging progress.');
    if (String(note).length > 200) throw new Error('Keep your note within 200 characters.');
    const log = { habitId, amount: positiveNumber(amount), note: String(note).trim(), date, id, time };
    return { ...state, logs: [...state.logs, log] };
  }

  function completeGoal(state, habitId, day, id) {
    const habit = state.habits.find(item => item.id === habitId);
    if (!habit) throw new Error('This repo could not be found.');
    const item = progress(habit, state.logs, day);
    const remaining = round(item.target - item.total);
    if (remaining <= 0) return state;
    return addLog(state, { habitId, amount: remaining, date: day, id, note: 'Daily goal completed' });
  }

  function removeLog(state, id) {
    return { ...state, logs: state.logs.filter(log => log.id !== id) };
  }

  function updateLog(state, id, amount, note) {
    const value = positiveNumber(amount);
    if (String(note).length > 200) throw new Error('Keep your note within 200 characters.');
    if (!state.logs.some(log => log.id === id)) throw new Error('This commit could not be found.');
    return { ...state, logs: state.logs.map(log => log.id === id
      ? { ...log, amount: value, note: String(note).trim() } : log) };
  }

  function createSampleState(today = dateKey(), now = new Date(`${today}T18:00:00`)) {
    const definitions = [
      ['read-every-day', 'Read every day', 'A few pages. A new perspective.', 20, 'pages', 'book', 'green', 12],
      ['move-your-body', 'Move your body', 'Make a little room for movement.', 30, 'minutes', 'activity', 'orange', 30],
      ['deep-work', 'Deep work', 'Less distraction. More intention.', 60, 'minutes', 'code', 'blue', 35],
      ['stay-hydrated', 'Stay hydrated', 'A small sip in the right direction.', 8, 'glasses', 'water', 'teal', 4],
      ['a-moment-of-calm', 'A moment of calm', 'Breathe in. Find your focus.', 10, 'minutes', 'sun', 'purple', 10],
      ['daily-journal', 'Daily journal', 'Make sense of the everyday.', 1, 'entry', 'journal', 'pink', 1]
    ];
    const habits = definitions.map(([id, name, description, target, unit, icon, color]) => ({
      id, name, description, unit, icon, color, createdOn: shiftDate(today, -167),
      changes: [{ date: shiftDate(today, -167), target, status: 'active' }]
    }));
    const logs = daysEnding(today, 168).flatMap((date, dayIndex) => definitions.flatMap((definition, index) => {
      const [habitId, , , target, , , , todayAmount] = definition;
      const pattern = (dayIndex * 17 + index * 11) % 23;
      const amount = date === today ? todayAmount : dayIndex % 31 === 7 || pattern < 3 ? 0
        : pattern < 9 ? Math.max(1, Math.round(target * 0.5)) : target;
      if (!amount) return [];
      // Today’s sample commits predate the preview, so new user entries sort first.
      const time = date === today ? new Date(Math.max(new Date(`${today}T00:00:00`).getTime(),
        now.getTime() - (definitions.length - index) * 30 * 60 * 1000)).toISOString()
        : new Date(`${date}T${String(8 + index).padStart(2, '0')}:30:00`).toISOString();
      return [{ id: `sample-${habitId}-${date}`, habitId, date, amount, time,
        note: date === today && index === 0 ? 'A quiet chapter with my morning coffee.' : '' }];
    }));
    return { ...createState(), session: true, onboarded: true, habits, logs };
  }

  function isValidState(value) {
    if (!value || value.version !== 1 || !['light', 'dark'].includes(value.theme)
      || typeof value.session !== 'boolean' || typeof value.onboarded !== 'boolean'
      || typeof value.profile?.name !== 'string' || value.profile.name.length > 40
      || typeof value.profile?.bio !== 'string' || value.profile.bio.length > 160
      || !Array.isArray(value.habits) || value.habits.length > 500
      || !Array.isArray(value.logs) || value.logs.length > 100000) return false;
    if (value.habits.some(habit => !habit || typeof habit !== 'object')
      || value.logs.some(log => !log || typeof log !== 'object')) return false;
    const ids = new Set(value.habits.map(habit => habit.id));
    if (ids.size !== value.habits.length) return false;
    const habitsValid = value.habits.every(habit => typeof habit.id === 'string' && validDate(habit.createdOn)
      && Array.isArray(habit.changes) && habit.changes.length > 0
      && habit.changes.every(change => change && validDate(change.date) && STATUSES.includes(change.status)
        && typeof change.target === 'number' && change.target > 0 && change.target <= 1000000)
      && typeof habit.name === 'string' && habit.name.length > 0 && habit.name.length <= 48
      && typeof habit.unit === 'string' && habit.unit.length > 0 && habit.unit.length <= 20
      && typeof habit.description === 'string' && habit.description.length <= 160
      && COLORS.includes(habit.color) && ICONS.includes(habit.icon));
    const logIds = new Set(value.logs.map(log => log.id));
    return habitsValid && logIds.size === value.logs.length && value.logs.every(log => ids.has(log.habitId)
      && typeof log.id === 'string' && validDate(log.date) && typeof log.amount === 'number'
      && Number.isFinite(log.amount) && log.amount > 0 && log.amount <= 1000000
      && typeof log.time === 'string' && Number.isFinite(new Date(log.time).getTime())
      && typeof log.note === 'string' && log.note.length <= 200);
  }

  const api = { COLORS, ICONS, dateKey, shiftDate, daysEnding, validDate, round, createState,
    stateAt, progress, dailyScore, streak, longestStreak, createHabit, updateHabit, changeStatus,
    addLog, completeGoal, removeLog, updateLog, createSampleState, isValidState };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.GitHabitDomain = api;
})(globalThis);
