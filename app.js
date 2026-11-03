/* GitHabit is a local design prototype. Authentication is deliberately simulated. */
(function () {
  'use strict';

  const D = window.GitHabitDomain;
  const STORAGE_KEY = 'githabit.prototype.v1';
  const app = document.getElementById('app');
  const sheet = document.getElementById('sheet');
  const toast = document.getElementById('toast');
  const icons = {
    branch: '<path d="M7 6v12m0-7c10 0 10-3 10-5"/><circle cx="7" cy="4" r="2"/><circle cx="7" cy="20" r="2"/><circle cx="17" cy="4" r="2"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    repo: '<path d="M5 19V5a2 2 0 0 1 2-2h12v18H7a2 2 0 0 1 0-4h12M8 6h7M9 17v5l2-1 2 1v-5"/>',
    book: '<path d="M12 5v15m0-15C9 3 5 3 2 4v15c3-1 7-1 10 1m0-15c3-2 7-2 10-1v15c-3-1-7-1-10 1"/>',
    activity: '<path d="M2 12h5l3-8 4 16 3-8h5"/>',
    code: '<path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18"/>',
    water: '<path d="M12 3s-7 8-7 12a7 7 0 0 0 14 0c0-4-7-12-7-12Z"/><path d="M8 15a4 4 0 0 0 4 4"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
    journal: '<path d="M5 3h14v18H5zM9 3v18m3-13h4m-4 4h4M3 7h4M3 12h4M3 17h4"/>',
    chart: '<path d="M4 3v17h17M8 15v-4m5 4V7m5 8v-6"/>',
    history: '<path d="M3 11a9 9 0 1 1 2.4 7M3 4v7h7m2-4v5l3 2"/>',
    settings: '<path d="m9 3-1 3-3 1-1 3 2 2-1 3 2 3 3-1 2 2 3-1 1-3 3-1 1-3-2-2 1-3-3-2-3 1-2-2Z"/><circle cx="12" cy="11" r="3"/>',
    help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 0 1 5 .5c0 2-2.5 2-2.5 4M12 17h.01"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    circleCheck: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
    arrow: '<path d="M5 12h14m-5-5 5 5-5 5"/>',
    back: '<path d="M19 12H5m5-5-5 5 5 5"/>',
    chevron: '<path d="m9 5 7 7-7 7"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    flame: '<path d="M12 3c1 5 6 6 6 11a6 6 0 0 1-12 0c0-3 2-5 3-6 0 4 3 4 3-5Z"/><path d="M12 13c3 3 3 6 0 7-3-1-3-3 0-7Z"/>',
    commit: '<circle cx="12" cy="12" r="4"/><path d="M2 12h6m8 0h6"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18m-13 4h2m4 0h2m-8 3h2"/>',
    moon: '<path d="M20 14a8.5 8.5 0 0 1-10-10 8.5 8.5 0 1 0 10 10Z"/>',
    lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
    search: '<circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/>',
    close: '<path d="m6 6 12 12M6 18 18 6"/>',
    edit: '<path d="m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-5-5L4 14l-1 7Zm0 0 5-1"/>',
    archive: '<rect x="3" y="3" width="18" height="5" rx="1"/><path d="M5 8v13h14V8M10 12h4"/>',
    pause: '<path d="M8 5v14M16 5v14"/>',
    play: '<path d="m8 4 13 8-13 8Z"/>',
    trash: '<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>',
    download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
    logout: '<path d="M9 4H4v16h5m4-13 5 5-5 5m-5-5h13"/>',
    spark: '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Zm7-1v4m-2-2h4"/>',
    heart: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/>',
    target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/>'
  };

  const icon = name => `<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.repo}</svg>`;
  const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const number = value => Number(value).toLocaleString(undefined, { maximumFractionDigits: 3 });
  const today = () => D.dateKey();
  const uid = () => globalThis.crypto?.randomUUID?.() || `id-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const readableDate = (day, options = { month: 'short', day: 'numeric', year: 'numeric' }) =>
    new Date(`${day}T12:00:00`).toLocaleDateString('en-US', options);
  let storageWarning = '';
  let state = loadState();
  let onboardingStep = 0;
  let repoFilter = 'active';
  let todayFilter = 'all';
  let searchQuery = '';
  let heatmapWeeks = 26;
  let historyLimit = 20;
  let toastTimer;
  let undoAction;
  let dialogReturnFocus;
  let scoreCache = new Map();

  function loadState() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (!stored) return D.createState();
      const parsed = JSON.parse(stored);
      if (D.isValidState(parsed)) return parsed;
      storageWarning = 'Saved demo data could not be read. A fresh workspace is open.';
    } catch {
      storageWarning = 'Local storage is unavailable. Changes will last for this page session only.';
    }
    return D.createState();
  }

  function saveState() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      return true;
    } catch {
      notify('Could not save locally. Your changes remain in this page until it closes.');
      return false;
    }
  }

  function setState(next, rerender = true) {
    state = next;
    scoreCache.clear();
    const saved = saveState();
    if (rerender) render();
    return saved;
  }

  function getRoute() {
    const route = location.hash.slice(1);
    return /^(today|repos|insights|activity|settings|repo\/.+)$/.test(route) ? route : 'today';
  }

  function go(route) {
    if (getRoute() === route && location.hash) render();
    else location.hash = route;
  }

  function getHabit(id) { return state.habits.find(habit => habit.id === id); }
  function currentStatus(habit) { return D.stateAt(habit, today())?.status || 'active'; }
  function activeHabits() { return state.habits.filter(habit => currentStatus(habit) === 'active'); }
  function completedHabits() { return activeHabits().filter(habit => D.progress(habit, state.logs, today()).complete); }
  function score(day, habitId) {
    const key = `${day}/${habitId || 'all'}`;
    if (!scoreCache.has(key)) {
      const habit = habitId ? getHabit(habitId) : null;
      scoreCache.set(key, habit ? D.progress(habit, state.logs, day).ratio : D.dailyScore(state, day));
    }
    return scoreCache.get(key);
  }

  function averageScore(days, habitId) {
    const values = days.map(day => score(day, habitId)).filter(value => value !== null);
    return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0;
  }

  function level(value) { return value === null || value <= 0 ? 0 : value >= 1 ? 4 : Math.ceil(value * 3); }
  function brand() { return `<a class="brand" href="#today" aria-label="GitHabit home"><span class="brand-mark">${icon('branch')}</span><span class="brand-name">git<span>habit</span></span></a>`; }
  function miniGrid(count = 64, columns = 16) {
    return `<div class="mini-grid" style="grid-template-columns:repeat(${columns},1fr)" aria-hidden="true">${Array.from({ length: count }, (_, i) => `<i class="level-${(i * 13 + Math.floor(i / columns) * 7) % 9 < 3 ? 0 : (i * 7) % 4 + 1}"></i>`).join('')}</div>`;
  }

  function navigationItem(route, name, glyph, current, extra = '') {
    const isActive = route === 'repos' ? current.startsWith('repo') : current === route;
    return `<a class="nav-item ${isActive ? 'active' : ''}" href="#${route}" ${isActive ? 'aria-current="page"' : ''}>${icon(glyph)}<span>${name}</span>${extra}</a>`;
  }

  function renderShell(content, route) {
    const remaining = activeHabits().length - completedHabits().length;
    const title = route.startsWith('repo/') ? 'Repository' : ({ today: 'Overview', repos: 'Repositories', insights: 'Insights', activity: 'Activity log', settings: 'Settings' }[route]);
    const initial = escape(state.profile.name.charAt(0).toUpperCase());
    return `<aside class="sidebar" aria-label="Workspace navigation">
      ${brand()}<div class="workspace-label">YOUR WORKSPACE</div>
      <nav aria-label="Main navigation">
        ${navigationItem('today', 'Today', 'grid', route, remaining ? `<span class="count">${remaining}</span>` : '')}
        ${navigationItem('repos', 'Repositories', 'repo', route)}
        ${navigationItem('insights', 'Insights', 'chart', route)}
      </nav>
      <div class="nav-divider"></div>
      ${navigationItem('activity', 'Activity log', 'history', route)}
      ${navigationItem('settings', 'Settings', 'settings', route)}
      <button class="nav-item" data-action="guide">${icon('help')}<span>How it works</span></button>
      <div class="sidebar-bottom">
        <div class="sidebar-note">${miniGrid()}<p>One commit at a time.</p><small>Small steps. A different tomorrow.</small></div>
        <button class="profile-link" data-action="settings"><span class="avatar">${initial}</span><span><strong>${escape(state.profile.name)}</strong><small>Personal workspace</small></span>${icon('chevron')}</button>
      </div>
    </aside>
    <div class="app-content">
      <header class="topbar"><div class="breadcrumb">${icon('repo')}<span>My workspace</span><span class="slash">/</span><strong>${title}</strong></div>
        <div class="mobile-brand">${brand()}</div>
        <div class="topbar-actions"><span class="local-status"><span class="dot"></span>LOCAL DEMO</span>
          <button class="icon-button" data-action="theme" aria-label="Switch to ${state.theme === 'dark' ? 'light' : 'dark'} theme" title="Switch theme">${icon(state.theme === 'dark' ? 'sun' : 'moon')}</button>
          <button class="icon-button" data-action="settings" aria-label="Profile and settings"><span class="avatar">${initial}</span></button>
        </div>
      </header>
      <main id="main" class="main" tabindex="-1">${content}
        <footer class="app-footer"><span>${icon('branch')} Made of small, everyday commits.</span><span>${icon('lock')} Local-only prototype · v1.0</span></footer>
      </main>
    </div>
    <nav class="bottom-nav" aria-label="Mobile navigation">${[['today', 'Today', 'grid'], ['repos', 'Repos', 'repo'], ['insights', 'Insights', 'chart']].map(([key, label, glyph]) => {
      const selected = key === 'repos' ? route.startsWith('repo') : route === key;
      return `<a href="#${key}" class="${selected ? 'active' : ''}" ${selected ? 'aria-current="page"' : ''}><span class="nav-icon">${icon(glyph)}</span>${label}</a>`;
    }).join('')}</nav>`;
  }

  function renderAuth() {
    return `<main id="main" class="auth-shell">
      <section class="auth-story">${brand()}<div class="auth-story-main">
        <div class="eyebrow">A little progress. Every single day.</div>
        <h1>Small commits.<br><em>Meaningful<br>change.</em></h1>
        <p>Your life is your most important project. Build better habits, one contribution at a time.</p>
        <div class="auth-art panel"><div class="auth-art-top">${icon('commit')} A year of showing up <span class="badge green" style="margin-left:auto">YOUR POTENTIAL</span></div>
          ${miniGrid(140, 20)}<div class="auth-art-footer"><span>Every little square is a little win.</span><span>LESS ▪ ▪ ▪ MORE</span></div></div>
      </div><div class="auth-story-footer">${icon('heart')} Built for progress. Not perfection.</div></section>
      <section class="auth-form-side" aria-label="Demo sign in">
        <button class="icon-button auth-theme" data-action="theme" aria-label="Switch theme">${icon(state.theme === 'dark' ? 'sun' : 'moon')}</button>
        <div class="auth-form"><span class="badge green">INTERACTIVE DESIGN PREVIEW</span><h2>Your next chapter starts here.</h2>
          <p>A personal space for the person you’re becoming.</p>
          <button class="button secondary full" data-action="google-demo"><span class="google-mark">G</span> Continue with Google <span class="badge">DEMO</span></button>
          <div class="auth-divider">or try the email flow</div>
          <form id="auth-form"><div class="field"><label for="email">Demo email</label><input id="email" type="email" name="email" placeholder="you@example.com" autocomplete="off" required></div>
            <div class="field"><label for="password">Demo password</label><input id="password" type="password" name="password" placeholder="Use a made-up password" autocomplete="off" minlength="6" required></div>
            <button class="button full" type="submit">Continue ${icon('arrow')}</button>
          </form>
          <p class="auth-note">${icon('lock')} This is a local prototype, not real sign-in. Use made-up details. Credentials are never saved or sent.</p>
          <div class="auth-bottom"><p>Just looking around?</p><button class="text-button" data-action="explore">Explore with sample habits ${icon('arrow')}</button></div>
        </div>
      </section>
    </main>`;
  }

  function renderOnboarding() {
    const steps = [
      ['Create your first repo.', 'Every habit is a little project. Give yours a name and a daily goal—like reading 20 pages or moving for 30 minutes.', 'repo'],
      ['Make a small commit.', 'Log a little progress throughout your day, or complete your remaining goal with one tap. Every step forward counts.', 'commit'],
      ['Watch consistency grow.', 'Your heatmap turns greener as you get closer to your goals. Build a history of showing up, without chasing perfection.', 'grid']
    ];
    const [title, description, glyph] = steps[onboardingStep];
    return `<main id="main" class="onboarding-shell"><section class="onboarding-card panel">${brand()}
      <div class="onboarding-art">${onboardingStep === 2 ? miniGrid(112) : icon(glyph)}</div>
      <div class="onboarding-steps" aria-label="Step ${onboardingStep + 1} of 3">${steps.map((_, index) => `<i class="${index === onboardingStep ? 'active' : ''}"></i>`).join('')}</div>
      <h1>${title}</h1><p>${description}</p>
      ${onboardingStep < 2 ? `<button class="button full" data-action="onboarding-next">Continue ${icon('arrow')}</button><button class="text-button" data-action="onboarding-skip">Skip introduction</button>`
        : `<button class="button full" data-action="start-empty">Create my first repo ${icon('plus')}</button><button class="text-button" data-action="explore">Try it with sample habits first ${icon('arrow')}</button>`}
    </section></main>`;
  }

  function statCard(glyph, label, value, suffix = '', note = '') {
    return `<div class="stat panel"><span class="stat-icon">${icon(glyph)}</span><div><p>${label}</p><strong>${value}${suffix ? `<small>${suffix}</small>` : ''}</strong></div>${note ? `<span class="stat-note">${note}</span>` : ''}</div>`;
  }

  function heatmap(habitId = '') {
    const date = today();
    const currentSunday = D.shiftDate(date, -new Date(`${date}T12:00:00`).getDay());
    const first = D.shiftDate(currentSunday, -(heatmapWeeks - 1) * 7);
    const days = Array.from({ length: heatmapWeeks * 7 }, (_, index) => D.shiftDate(first, index));
    let previousMonth = '';
    const months = Array.from({ length: heatmapWeeks }, (_, index) => {
      const month = readableDate(days[index * 7], { month: 'short' });
      const visible = month !== previousMonth;
      previousMonth = month;
      return `<span>${visible ? month : ''}</span>`;
    }).join('');
    const cells = days.map(day => {
      if (day > date) return '<span class="heat-cell future"></span>';
      const value = score(day, habitId);
      const label = `${readableDate(day)}: ${value === null ? 'no scheduled habits' : `${Math.round(value * 100)}% of daily goal${habitId ? '' : 's'}`}`;
      return `<button class="heat-cell level-${level(value)} ${day === date ? 'today' : ''}" data-action="day" data-day="${day}" data-id="${escape(habitId)}" tabindex="${day === date ? 0 : -1}" aria-label="${label}" title="${label}"></button>`;
    }).join('');
    return `<section class="panel contribution-panel"><div class="panel-header"><div><h2>${icon('grid')} ${habitId ? 'Your consistency' : 'Your contributions'}</h2><p>Every square is a day you showed up.</p></div>
      <label><span class="sr-only">Heatmap time range</span><select class="range-select" data-heatmap-range>${[13, 26, 52].map(weeks => `<option value="${weeks}" ${weeks === heatmapWeeks ? 'selected' : ''}>${weeks === 13 ? '3 months' : weeks === 26 ? '6 months' : '1 year'}</option>`).join('')}</select></label></div>
      <div class="heatmap-scroll"><div class="heatmap" style="--weeks:${heatmapWeeks};${heatmapWeeks === 52 ? 'min-width:700px' : ''}"><div class="heatmap-labels" aria-hidden="true"><span></span><span>Mon</span><span></span><span>Wed</span><span></span><span>Fri</span><span></span></div><div class="heatmap-data"><div class="heatmap-months" aria-hidden="true">${months}</div><div class="heatmap-cells" role="group" aria-label="Daily progress. Use arrow keys to explore dates, or use Explore by date below.">${cells}</div></div></div></div>
      <div class="heatmap-footer"><button class="text-button" data-action="day" data-day="${date}" data-id="${escape(habitId)}">${icon('calendar')} Explore by date</button><div class="heatmap-legend" aria-label="Color intensity represents progress from zero to 100 percent"><span>Less</span>${[0, 1, 2, 3, 4].map(value => `<i class="level-${value}"></i>`).join('')}<span>More</span></div></div>
    </section>`;
  }

  function habitCard(habit, library = false) {
    const item = D.progress(habit, state.logs, today());
    const status = currentStatus(habit);
    const streak = D.streak(state, today(), habit.id);
    return `<article class="habit-card panel" style="--habit-color:var(--${habit.color})">
      <div class="habit-top"><span class="habit-icon">${icon(habit.icon)}</span>${status !== 'active' ? `<span class="badge">${status}</span>` : item.complete ? `<span class="complete-label">${icon('circleCheck')} Goal reached</span>` : '<span class="badge">DAILY</span>'}</div>
      <button class="habit-title" data-action="open-repo" data-id="${escape(habit.id)}">${escape(habit.name)}</button>
      <p class="habit-description">${escape(habit.description || 'A small step toward your better everyday.')}</p>
      <div class="habit-progress-label"><span><strong>${number(item.total)}</strong> / ${number(item.target)} ${escape(habit.unit)}</span><span>${status === 'active' ? `${Math.round(item.ratio * 100)}%` : 'Not scheduled'}</span></div>
      <div class="progress-track" role="progressbar" aria-label="${escape(habit.name)} progress" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.round((item.ratio || 0) * 100)}" style="--progress:${Math.min(item.total / item.target, 1) * 100}%"><span></span></div>
      <div class="habit-bottom"><span class="habit-streak">${icon('flame')} ${streak ? `${streak}-day activity streak` : 'A fresh start'}</span><div class="habit-actions">
      ${status === 'active' ? `<button class="log-button" data-action="log" data-id="${escape(habit.id)}" aria-label="Log progress for ${escape(habit.name)}">${icon('plus')} Log</button>${!item.complete ? `<button class="complete-button" data-action="complete" data-id="${escape(habit.id)}" aria-label="Complete today's goal for ${escape(habit.name)}" title="Complete remaining goal">${icon('check')}</button>` : ''}`
        : `<button class="log-button" data-action="resume" data-id="${escape(habit.id)}">${icon('play')} Resume</button>`}
      </div></div>${library ? `<div class="repo-meta">${icon('repo')} Created ${readableDate(habit.createdOn, { month: 'short', day: 'numeric' })}</div>` : ''}
    </article>`;
  }

  function emptyState(title, message, action = true) {
    return `<div class="empty-state">${icon('repo')}<h2>${title}</h2><p>${message}</p>${action ? `<button class="button" data-action="create">${icon('plus')} Create a repo</button>` : ''}</div>`;
  }

  function recentCommits() {
    const logs = [...state.logs].sort((a, b) => b.time.localeCompare(a.time)).slice(0, 4);
    return `<section class="panel recent-panel"><div class="panel-header"><h2>Recent commits</h2><button class="icon-button" data-action="activity" aria-label="View all activity">${icon('arrow')}</button></div>
      ${logs.length ? logs.map(log => {
        const habit = getHabit(log.habitId);
        return `<div class="commit-row"><span class="commit-node">${icon('commit')}</span><div class="commit-content"><strong>${escape(habit.name)}</strong><p>+${number(log.amount)} ${escape(habit.unit)} committed</p><small>${log.date === today() ? 'TODAY' : readableDate(log.date, { month: 'short', day: 'numeric' }).toUpperCase()} · ${new Date(log.time).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}</small></div></div>`;
      }).join('') : '<p class="muted" style="font-size:12px;padding-bottom:18px">Your first commit is the start of something good.</p>'}
    </section>`;
  }

  function renderToday() {
    const active = activeHabits();
    const completed = completedHabits().length;
    const list = active.filter(habit => todayFilter === 'all' || !D.progress(habit, state.logs, today()).complete);
    const allDone = active.length > 0 && completed === active.length;
    return `<section class="hero"><div><div class="eyebrow"><span class="dot"></span> YOUR PERSONAL CHANGELOG</div><h1>A little better,<br><em>every day.</em></h1><p>Welcome back, ${escape(state.profile.name.split(' ')[0])}. Let’s make today count.</p></div><div class="hero-right"><span class="date-label">${icon('calendar')} ${readableDate(today(), { weekday: 'short', month: 'short', day: 'numeric' }).toUpperCase()}</span><button class="button" data-action="create" aria-label="New repo">${icon('plus')}<span>New repo</span></button></div></section>
      <section class="stats-strip" aria-label="Your statistics">
        ${statCard('circleCheck', 'Goals reached today', `${completed}<small>/ ${active.length}</small>`, '', allDone ? 'All done!' : 'Keep going')}
        ${statCard('flame', 'Current activity streak', D.streak(state, today()), 'days')}
        ${statCard('commit', 'Total contributions', number(state.logs.length), '', 'One at a time')}
      </section>
      <div class="dashboard-grid"><div class="stack">${heatmap()}
        <section><div class="section-heading"><h2>Today’s repos <span class="count">${active.length}</span></h2><div class="segmented" aria-label="Filter today's repos"><button class="${todayFilter === 'all' ? 'active' : ''}" data-action="today-filter" data-value="all" aria-pressed="${todayFilter === 'all'}">All habits</button><button class="${todayFilter === 'remaining' ? 'active' : ''}" data-action="today-filter" data-value="remaining" aria-pressed="${todayFilter === 'remaining'}">To do</button></div></div>
          <div class="habits-grid">${list.length ? list.map(habit => habitCard(habit)).join('') : active.length ? emptyState('That’s a wrap for today.', 'You’ve reached every goal. Take a moment to enjoy that.', false) : emptyState('Your next chapter starts here.', 'Create your first repo and turn a small action into a daily habit.')}</div>
        </section>
      </div><aside class="stack dashboard-aside" aria-label="Daily focus and recent activity">
        <section class="panel focus-panel"><div class="panel-header"><h2>Daily focus</h2>${icon('target')}</div>
          <div class="ring" style="--ratio:${active.length ? completed / active.length : 0}"><svg viewBox="0 0 160 160" aria-hidden="true"><circle class="track" cx="80" cy="80" r="68"/><circle class="value" cx="80" cy="80" r="68"/></svg><div class="ring-label"><strong>${completed}<span> / ${active.length}</span></strong><small>goals completed</small></div></div>
          <h3>${allDone ? 'Look at you showing up.' : completed ? 'You’re making progress.' : 'Start with something small.'}</h3><p>${allDone ? 'Every goal reached. That’s another good day in the books.' : 'You don’t need a perfect day. Just one small step forward.'}</p>
          <div class="focus-footer"><span>Overall goal progress</span><strong>${Math.round((score(today()) || 0) * 100)}%</strong></div>
        </section>${recentCommits()}<div class="quote-card">${icon('spark')}<p>Consistency isn’t doing everything.<br>It’s coming back to what matters.</p><small>A NOTE TO YOUR FUTURE SELF</small></div>
      </aside></div>`;
  }

  function filteredRepos() {
    return state.habits.filter(habit => (repoFilter === 'all' || currentStatus(habit) === repoFilter)
      && `${habit.name} ${habit.description}`.toLowerCase().includes(searchQuery.toLowerCase()));
  }

  function repoGrid() {
    const habits = filteredRepos();
    return habits.length ? habits.map(habit => habitCard(habit, true)).join('')
      : emptyState(searchQuery ? 'No matching repos.' : `No ${repoFilter === 'all' ? '' : repoFilter + ' '}repos yet.`, searchQuery ? 'Try another name or clear your search.' : 'Give a small daily intention a place to grow.', !searchQuery);
  }

  function renderRepos() {
    return `<div class="page-heading"><div><h1>Your repositories</h1><p>Small projects. A bigger picture.</p></div><button class="button" data-action="create">${icon('plus')} New repo</button></div>
      <div class="filter-row"><label class="search-field">${icon('search')}<span class="sr-only">Search repositories</span><input type="search" id="repo-search" placeholder="Find a repository…" value="${escape(searchQuery)}"></label>
        <div class="segmented" aria-label="Repository status">${['active', 'paused', 'archived', 'all'].map(value => `<button class="${repoFilter === value ? 'active' : ''}" data-action="repo-filter" data-value="${value}" aria-pressed="${repoFilter === value}">${value.charAt(0).toUpperCase() + value.slice(1)}</button>`).join('')}</div></div>
      <div id="repo-grid" class="habits-grid repos-grid">${repoGrid()}</div>`;
  }

  function renderDetail(id) {
    const habit = getHabit(id);
    if (!habit) return `${emptyState('This repo isn’t here.', 'It may have been removed from your local workspace.', false)}<button class="text-button" data-action="repos">${icon('back')} Back to repositories</button>`;
    const status = currentStatus(habit);
    const item = D.progress(habit, state.logs, today());
    const logs = state.logs.filter(log => log.habitId === id);
    return `<button class="text-button back-button" data-action="repos">${icon('back')} All repositories</button>
      <div class="page-heading detail-header"><div class="detail-title-row" style="--habit-color:var(--${habit.color})"><span class="habit-icon">${icon(habit.icon)}</span><div><h1>${escape(habit.name)}</h1><p>${escape(habit.description || 'A small step, every day.')}</p></div></div>
      <div class="row"><span class="badge ${status === 'active' ? 'green' : ''}">${status}</span><button class="button secondary" data-action="edit-repo" data-id="${escape(id)}">${icon('edit')} Edit repo</button></div></div>
      <div class="detail-overview"><div class="stack">${heatmap(id)}<div class="detail-stats panel"><div><strong>${D.streak(state, today(), id)}</strong><small>day activity streak</small></div><div><strong>${D.longestStreak(state, today(), id)}</strong><small>longest streak</small></div><div><strong>${number(logs.length)}</strong><small>total commits</small></div></div></div>
        <section class="target-panel panel" style="--habit-color:var(--${habit.color})"><div class="row between"><h2>Today’s progress</h2><span class="habit-icon">${icon(habit.icon)}</span></div><div class="target-number">${number(item.total)} <span>/ ${number(item.target)}</span></div><p class="muted">${escape(habit.unit)} ${status === 'active' ? 'per day' : `· ${status}`}</p><div class="progress-track" style="--progress:${Math.min(item.total / item.target, 1) * 100}%"><span></span></div>
        ${status === 'active' ? `<button class="button full" data-action="log" data-id="${escape(id)}">${icon('plus')} Log progress</button><button class="text-button full" style="width:100%" data-action="complete" data-id="${escape(id)}" ${item.complete ? 'disabled' : ''}>${icon('check')} ${item.complete ? 'Daily goal reached' : 'Complete remaining goal'}</button>`
          : `<p class="form-note">Your history is safe. Resume this repo whenever you’re ready.</p><button class="button full" data-action="resume" data-id="${escape(id)}">${icon('play')} Resume repo</button>`}
        <div class="row">${status === 'active' ? `<button class="button secondary small" data-action="pause" data-id="${escape(id)}">${icon('pause')} Pause</button>` : ''}${status !== 'archived' ? `<button class="button secondary small" data-action="archive" data-id="${escape(id)}">${icon('archive')} Archive</button>` : ''}</div>
      </section></div>${historyPanel(logs, 'Commit history')}`;
  }

  function historyPanel(logs, title = 'Your activity') {
    const sorted = [...logs].sort((a, b) => b.time.localeCompare(a.time));
    let lastDay = '';
    const rows = sorted.slice(0, historyLimit).map(log => {
      const habit = getHabit(log.habitId);
      const group = log.date !== lastDay ? `<div class="history-group-label">${log.date === today() ? 'TODAY' : readableDate(log.date, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }).toUpperCase()}</div>` : '';
      lastDay = log.date;
      return `${group}<div class="history-row" style="--habit-color:var(--${habit.color})"><span class="habit-icon">${icon(habit.icon)}</span><div class="history-info"><strong>${escape(habit.name)}</strong>${log.note ? `<p>${escape(log.note)}</p>` : ''}<small>${new Date(log.time).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}</small></div><div class="history-amount">+${number(log.amount)}<small>${escape(habit.unit)}</small></div><button class="icon-button" data-action="edit-log" data-id="${escape(log.id)}" aria-label="Edit ${escape(habit.name)} commit from ${readableDate(log.date)}">${icon('edit')}</button></div>`;
    }).join('');
    return `<section class="history-panel panel"><div class="panel-header"><h2>${title}</h2><span class="badge">${number(logs.length)} commits</span></div>${rows || '<div style="padding:30px;text-align:center" class="muted">No commits yet. Your story is still being written.</div>'}${sorted.length > historyLimit ? '<div style="padding:12px;text-align:center"><button class="text-button" data-action="more-history">Show more commits</button></div>' : ''}</section>`;
  }

  function renderActivity() {
    return `<div class="page-heading"><div><h1>Your changelog</h1><p>A record of the little things that add up.</p></div><button class="button secondary" data-action="export">${icon('download')} Export</button></div>${historyPanel(state.logs, 'All contributions')}`;
  }

  function renderInsights() {
    const days = D.daysEnding(today(), 7);
    const average = Math.round(averageScore(days) * 100);
    const weekLogs = state.logs.filter(log => days.includes(log.date));
    const activeDays = new Set(weekLogs.map(log => log.date)).size;
    const ranks = state.habits.filter(habit => days.some(day => score(day, habit.id) !== null))
      .map(habit => ({ habit, value: averageScore(days, habit.id) })).sort((a, b) => b.value - a.value);
    return `<div class="page-heading"><div><h1>The bigger picture</h1><p>Progress you can see. Consistency you can feel.</p></div><span class="badge">LAST 7 DAYS</span></div>
      <section class="stats-strip">${statCard('target', 'Average goal progress', `${average}%`)}${statCard('calendar', 'Days you showed up', activeDays, '/ 7 days')}${statCard('commit', 'This week’s commits', weekLogs.length)}</section>
      <div class="insights-grid"><section class="chart-panel panel"><div class="panel-header"><div><h2>A week of small wins</h2><p>Average progress across each day’s active repos</p></div>${icon('chart')}</div>
        <div class="weekly-chart" role="img" aria-label="Daily goal progress: ${days.map(day => `${readableDate(day, { weekday: 'long' })} ${score(day) === null ? 'not scheduled' : `${Math.round(score(day) * 100)} percent`}`).join(', ')}"><div class="chart-scale"><span>100%</span><span>75%</span><span>50%</span><span>25%</span><span>0%</span></div><div class="chart-bars">${days.map(day => {
          const value = score(day);
          return `<div class="bar-column"><div class="bar-track"><div class="bar-fill" style="--height:${(value || 0) * 100}%"><span class="bar-value">${value === null ? '—' : `${Math.round(value * 100)}%`}</span></div></div><span>${readableDate(day, { weekday: 'short' })}</span></div>`;
        }).join('')}</div></div></section>
      <section class="chart-panel panel"><div class="panel-header"><div><h2>Your steady habits</h2><p>Average daily goal progress this week</p></div>${icon('spark')}</div>${ranks.length ? ranks.slice(0, 6).map(({ habit, value }) => `<div class="rank-row" style="--habit-color:var(--${habit.color})"><span class="habit-icon">${icon(habit.icon)}</span><div class="rank-info"><strong>${escape(habit.name)}</strong><div class="progress-track" style="--progress:${value * 100}%"><span></span></div></div><span class="rank-score">${Math.round(value * 100)}%</span></div>`).join('') : '<p class="muted">Your habits will find their rhythm here.</p>'}</section></div>
      <div class="insight-note panel">${icon('spark')}<div><strong>${activeDays ? `You made time for yourself on ${activeDays} of the last 7 days.` : 'Your first small win is waiting.'}</strong><p>${activeDays ? 'Partial progress counts, too. Focus on returning, not on being perfect.' : 'Create a repo and log your first contribution. Your insights will grow with you.'}</p></div></div>${heatmap()}`;
  }

  function renderSettings() {
    return `<div class="page-heading"><div><h1>Make it your space</h1><p>A few preferences for your everyday.</p></div></div><div class="settings-layout">
      <section class="settings-panel panel"><h2>Personal workspace</h2><div class="profile-summary"><span class="avatar large">${escape(state.profile.name.charAt(0).toUpperCase())}</span><div><h3>${escape(state.profile.name)}</h3><p>${escape(state.profile.bio)}</p></div></div>
      <form id="profile-form"><div class="field-row"><div class="field"><label for="profile-name">Display name</label><input id="profile-name" name="name" maxlength="40" required value="${escape(state.profile.name)}"></div><div class="field"><label for="profile-bio">A note to yourself</label><input id="profile-bio" name="bio" maxlength="160" value="${escape(state.profile.bio)}"></div></div><p class="form-error" role="alert"></p><button class="button secondary" type="submit">Save profile</button></form></section>
      <section class="settings-panel panel"><h2>Look & feel</h2><div class="settings-row"><div><strong>Color theme</strong><p>A familiar green, in your favorite light.</p></div><div class="segmented" aria-label="Color theme"><button data-action="set-theme" data-value="light" class="${state.theme === 'light' ? 'active' : ''}" aria-pressed="${state.theme === 'light'}">${icon('sun')} Light</button><button data-action="set-theme" data-value="dark" class="${state.theme === 'dark' ? 'active' : ''}" aria-pressed="${state.theme === 'dark'}">${icon('moon')} Dark</button></div></div></section>
      <section class="settings-panel panel"><h2>Your data, your device</h2><div class="settings-row"><div><strong>Export your workspace</strong><p>Download your habits and commit history as JSON.</p></div><button class="button secondary" data-action="export">${icon('download')} Export</button></div>
        <div class="settings-row"><div><strong>Replay the introduction</strong><p>A quick refresher on repos, commits, and consistency.</p></div><button class="button secondary" data-action="guide">View guide</button></div>
        <div class="settings-row"><div><strong>Sign out of the demo</strong><p>Local data stays on this browser. This is not a secure multi-account app.</p></div><button class="button secondary" data-action="signout">Sign out</button></div>
        <div class="settings-row"><div><strong style="color:var(--danger)">Reset this prototype</strong><p>Remove all local habits, commits, and profile preferences.</p></div><button class="button danger" data-action="reset">Reset data</button></div>
      </section><p class="form-note">${icon('info')} GitHabit is an interactive design prototype. Sign-in is simulated. There is no cloud sync, real account, or background notification service. Data is stored only in this browser. Export it before clearing browser data.</p>
    </div>`;
  }

  function render() {
    const previousFocus = document.activeElement;
    const focusAction = app.contains(previousFocus) ? previousFocus.dataset.action : null;
    const focusId = previousFocus?.dataset.id;
    const focusValue = previousFocus?.dataset.value;
    scoreCache.clear();
    document.documentElement.dataset.theme = state.theme;
    document.querySelector('meta[name="theme-color"]').content = state.theme === 'dark' ? '#0d1117' : '#f7f9f8';
    if (!state.session) { app.innerHTML = renderAuth(); return; }
    if (!state.onboarded) { app.innerHTML = renderOnboarding(); return; }
    const route = getRoute();
    const pages = { today: renderToday, repos: renderRepos, insights: renderInsights, activity: renderActivity, settings: renderSettings };
    const content = route.startsWith('repo/') ? renderDetail(route.slice(5)) : (pages[route] || renderToday)();
    app.innerHTML = renderShell(content, route);
    document.title = `GitHabit — ${route.startsWith('repo/') ? getHabit(route.slice(5))?.name || 'Repository' : { today: 'Today', repos: 'Repositories', insights: 'Insights', activity: 'Activity log', settings: 'Settings' }[route]}`;
    // Scroll wide contribution graphs to the most recent week without moving the page.
    scrollHeatmapsToToday();
    if (focusAction) {
      const replacement = [...app.querySelectorAll('[data-action]')].find(element =>
        element.dataset.action === focusAction && element.dataset.id === focusId && element.dataset.value === focusValue);
      (replacement || document.getElementById('main'))?.focus({ preventScroll: true });
    }
  }

  function scrollHeatmapsToToday() {
    app.querySelectorAll('.heatmap-scroll').forEach(element => { element.scrollLeft = element.scrollWidth; });
  }

  function notify(message, action) {
    clearTimeout(toastTimer);
    undoAction = action;
    toast.innerHTML = `<span>${escape(message)}</span>${action ? '<button data-action="undo">Undo</button>' : ''}`;
    toast.classList.add('visible');
    toastTimer = setTimeout(() => { toast.classList.remove('visible'); undoAction = null; }, action ? 10000 : 5500);
  }

  function openSheet(title, body, afterRender) {
    const wasOpen = sheet.open;
    if (!wasOpen) dialogReturnFocus = document.activeElement;
    sheet.innerHTML = `<div class="sheet-head"><h2 id="sheet-title">${title}</h2><button class="icon-button" data-action="close-sheet" aria-label="Close dialog">${icon('close')}</button></div><div class="sheet-body">${body}</div>`;
    if (!wasOpen) sheet.showModal();
    else sheet.querySelector('[autofocus], button, input')?.focus({ preventScroll: true });
    afterRender?.();
  }

  function closeSheet() {
    sheet.close();
    if (dialogReturnFocus?.isConnected) dialogReturnFocus.focus({ preventScroll: true });
  }

  function formError(form, message) {
    const target = form.querySelector('.form-error');
    if (target) target.textContent = message;
    else notify(message);
  }

  function openRepoForm(id) {
    const habit = id ? getHabit(id) : null;
    const target = habit ? D.stateAt(habit, today()).target : 20;
    const hasHistory = habit && state.logs.some(log => log.habitId === id);
    openSheet(habit ? 'Edit your repo' : 'A new beginning', `<p class="sheet-subtitle">${habit ? 'Make this habit work for the person you are now.' : 'Give a small daily intention a place to grow.'}</p>
      <form id="repo-form" data-id="${escape(id || '')}"><div class="field"><label for="habit-name">Repo name</label><input id="habit-name" name="name" placeholder="e.g. Read every day" maxlength="48" value="${escape(habit?.name || '')}" required autofocus></div>
        <div class="field"><label for="habit-description">A little motivation <span class="muted">· optional</span></label><input id="habit-description" name="description" placeholder="Why does this matter to you?" maxlength="160" value="${escape(habit?.description || '')}"></div>
        <div class="field-row"><div class="field"><label for="habit-target">Daily target</label><input id="habit-target" name="target" type="number" inputmode="decimal" min="0.001" max="1000000" step="0.001" value="${target}" required></div><div class="field"><label for="habit-unit">Unit</label><input id="habit-unit" name="unit" placeholder="pages, minutes, glasses" maxlength="20" value="${escape(habit?.unit || '')}" ${hasHistory ? 'readonly' : ''} required></div></div>
        ${hasHistory ? '<p class="form-note">Goal changes apply from today. Past goals stay unchanged. Units stay fixed to keep your history meaningful.</p>' : '<p class="form-note">Start small. This repo repeats every day, and you can adjust the target later.</p>'}
        <fieldset class="choice-group"><legend>Choose an icon</legend><div class="choices">${D.ICONS.map(glyph => `<label class="choice"><input type="radio" name="icon" value="${glyph}" ${glyph === (habit?.icon || 'book') ? 'checked' : ''} aria-label="${glyph}">${icon(glyph)}</label>`).join('')}</div></fieldset>
        <fieldset class="choice-group"><legend>Make it yours</legend><div class="choices">${D.COLORS.map(color => `<label class="choice"><input type="radio" name="color" value="${color}" ${color === (habit?.color || 'green') ? 'checked' : ''} aria-label="${color}"><span class="swatch" style="--swatch:var(--${color})"></span></label>`).join('')}</div></fieldset>
        <p class="form-error" role="alert"></p><div class="sheet-actions"><button type="button" class="button secondary" data-action="close-sheet">Cancel</button><button class="button" type="submit">${icon(habit ? 'check' : 'plus')} ${habit ? 'Save changes' : 'Create repo'}</button></div>
      </form>`);
  }

  function openLogForm(habitId, logId) {
    const log = logId ? state.logs.find(entry => entry.id === logId) : null;
    const habit = getHabit(log?.habitId || habitId);
    if (!habit) return;
    const day = log?.date || today();
    const progress = D.progress(habit, state.logs, day);
    const suggested = Math.max(D.round(progress.target - progress.total), 1);
    const amounts = [...new Set([D.round(progress.target * .25), D.round(progress.target * .5), progress.target].filter(amount => amount > 0))];
    openSheet(log ? 'Edit your commit' : 'A little progress counts.', `<p class="sheet-subtitle">${log ? `Correct your entry from ${readableDate(day)}.` : 'What did you make time for today?'}</p>
      <div class="sheet-repo" style="--habit-color:var(--${habit.color})"><span class="habit-icon">${icon(habit.icon)}</span><div><strong>${escape(habit.name)}</strong><p>${number(progress.total)} of ${number(progress.target)} ${escape(habit.unit)} ${log ? 'on this day' : 'today'}</p></div></div>
      <form id="log-form" data-habit="${escape(habit.id)}" data-id="${escape(logId || '')}"><div class="field"><label for="log-amount">${log ? 'Corrected amount' : 'Amount to add'}</label><div class="input-unit"><input id="log-amount" name="amount" type="number" inputmode="decimal" min="0.001" max="1000000" step="0.001" value="${log ? log.amount : suggested}" required autofocus><span>${escape(habit.unit)}</span></div></div>
      ${!log ? `<div class="quick-amounts">${amounts.map(amount => `<button type="button" data-action="quick-amount" data-value="${amount}">+${number(amount)}</button>`).join('')}</div>` : ''}
      <div class="field"><label for="log-note">Commit message <span class="muted">· optional</span></label><textarea id="log-note" name="note" placeholder="A small note for your future self…" maxlength="200">${escape(log?.note || '')}</textarea></div><p class="form-error" role="alert"></p>
      <div class="sheet-actions">${log ? `<button type="button" class="button danger" data-action="delete-log" data-id="${escape(logId)}">${icon('trash')} Delete</button>` : '<button type="button" class="button secondary" data-action="close-sheet">Cancel</button>'}<button class="button" type="submit">${icon('commit')} ${log ? 'Save changes' : 'Commit progress'}</button></div></form>`);
  }

  function openDay(day = today(), habitId = '') {
    if (!D.validDate(day) || day > today()) return;
    const value = score(day, habitId);
    const habits = habitId ? state.habits.filter(habit => habit.id === habitId) : state.habits;
    const visible = habits.filter(habit => D.stateAt(habit, day));
    openSheet('A day in your story', `<div class="field"><label for="day-picker">Explore a date</label><input id="day-picker" type="date" value="${day}" max="${today()}" data-day-picker data-habit="${escape(habitId)}"></div>
      <div class="day-summary">${value === null ? '—' : `${Math.round(value * 100)}%`}</div><p class="sheet-subtitle">${value === null ? 'No habits scheduled on this day.' : `Of ${habitId ? 'your daily goal' : 'daily goals'}, with each active habit weighted equally.`}</p>
      ${visible.map(habit => {
        const item = D.progress(habit, state.logs, day);
        return `<div class="day-habit"><strong>${escape(habit.name)}</strong><span>${number(item.total)} / ${number(item.target)} ${escape(habit.unit)}${!item.eligible ? ` · ${D.stateAt(habit, day).status}` : item.complete ? ' · Complete' : ' · In progress'}</span></div>`;
      }).join('')}
      <div class="sheet-actions"><button class="button secondary" data-action="day" data-day="${D.shiftDate(day, -1)}" data-id="${escape(habitId)}">${icon('back')} Previous</button><button class="button secondary" data-action="day" data-day="${D.shiftDate(day, 1)}" data-id="${escape(habitId)}" ${day === today() ? 'disabled' : ''}>Next ${icon('arrow')}</button></div>`);
  }

  function openGuide() {
    openSheet('Small commits. Real progress.', `<p class="sheet-subtitle">A quick guide to your personal workspace.</p>
      <div class="guide-step"><span class="step-number">01</span><div><h3>Your habits are repos.</h3><p>Create a repo for something you want to practice. Set a small, specific daily goal with a unit you can measure.</p></div></div>
      <div class="guide-step"><span class="step-number">02</span><div><h3>Your progress is a commit.</h3><p>Log any positive amount, or tap the checkmark to fill the remaining goal. Already finished? You can still log extra progress.</p></div></div>
      <div class="guide-step"><span class="step-number">03</span><div><h3>Your consistency has a color.</h3><p>More progress means a greener square. The overall heatmap averages each active habit’s percentage, capped at 100% per habit. Tap a day or use “Explore by date” for numbers.</p></div></div>
      <div class="guide-step"><span class="step-number">04</span><div><h3>Leave room for real life.</h3><p>Correct commits in your activity log. Pause or archive a habit without removing its history. An activity streak counts days with any logged progress—not just perfect days.</p></div></div>
      <button class="button full" data-action="close-sheet">Got it ${icon('check')}</button>`);
  }

  function confirmAction(title, message, action, id = '') {
    openSheet(title, `<p class="sheet-subtitle">${message}</p><div class="sheet-actions"><button class="button secondary" data-action="close-sheet">Cancel</button><button class="button ${action === 'confirm-reset' || action === 'confirm-delete-log' ? 'danger' : ''}" data-action="${action}" data-id="${escape(id)}">${action === 'confirm-reset' ? 'Reset everything' : action === 'confirm-delete-log' ? 'Delete commit' : 'Archive repo'}</button></div>`);
  }

  function updateStatus(id, status) {
    closeSheet();
    const previous = currentStatus(getHabit(id));
    const saved = setState(D.changeStatus(state, id, status, today()));
    if (saved) notify(status === 'active' ? 'Repo resumed. A fresh start awaits.' : `Repo ${status}. Your history is safe.`, () => setState(D.changeStatus(state, id, previous, today())));
  }

  function complete(id) {
    const logId = uid();
    const next = D.completeGoal(state, id, today(), logId);
    if (next === state) { notify('You’ve already reached today’s goal.'); return; }
    if (setState(next)) notify('Daily goal reached. A little win worth keeping.', () => setState(D.removeLog(state, logId)));
  }

  function explore() {
    // Re-entering the preview never overwrites an existing workspace.
    if (state.habits.length || state.onboarded) setState({ ...state, session: true, onboarded: true });
    else setState({ ...D.createSampleState(today(), new Date()), theme: state.theme });
    go('today');
  }

  function exportData() {
    const { session, ...data } = state;
    const blob = new Blob([JSON.stringify({ ...data, exportedAt: new Date().toISOString() }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `githabit-${today()}.json`;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notify('Your workspace export is ready.');
  }

  const actions = {
    theme: () => setState({ ...state, theme: state.theme === 'dark' ? 'light' : 'dark' }),
    'set-theme': element => setState({ ...state, theme: element.dataset.value }),
    settings: () => go('settings'), repos: () => go('repos'), activity: () => go('activity'),
    create: () => openRepoForm(), 'edit-repo': element => openRepoForm(element.dataset.id),
    'open-repo': element => { historyLimit = 20; go(`repo/${element.dataset.id}`); },
    log: element => openLogForm(element.dataset.id), 'edit-log': element => openLogForm(null, element.dataset.id),
    complete: element => complete(element.dataset.id),
    'close-sheet': closeSheet, guide: openGuide,
    'quick-amount': element => { document.getElementById('log-amount').value = element.dataset.value; },
    'today-filter': element => { todayFilter = element.dataset.value; render(); },
    'repo-filter': element => { repoFilter = element.dataset.value; render(); },
    'more-history': () => { const position = window.scrollY; historyLimit += 30; render(); window.scrollTo(0, position); },
    day: element => openDay(element.dataset.day, element.dataset.id),
    pause: element => updateStatus(element.dataset.id, 'paused'),
    resume: element => updateStatus(element.dataset.id, 'active'),
    archive: element => confirmAction('Close this chapter?', 'Archiving removes this repo from your daily goals. All past commits remain, and you can resume it any time.', 'confirm-archive', element.dataset.id),
    'confirm-archive': element => updateStatus(element.dataset.id, 'archived'),
    'delete-log': element => confirmAction('Remove this commit?', 'This entry will be removed from your progress and heatmaps. You can undo immediately afterward.', 'confirm-delete-log', element.dataset.id),
    'confirm-delete-log': element => {
      const log = state.logs.find(item => item.id === element.dataset.id);
      closeSheet();
      if (setState(D.removeLog(state, element.dataset.id))) notify('Commit removed.', () => setState({ ...state, logs: [...state.logs, log] }));
    },
    undo: () => { const callback = undoAction; undoAction = null; callback?.(); notify('Undone. You’re all set.'); },
    explore,
    'google-demo': () => { onboardingStep = 0; setState({ ...state, session: true }); if (state.onboarded) go('today'); },
    'onboarding-next': () => { onboardingStep = Math.min(onboardingStep + 1, 2); render(); },
    'onboarding-skip': () => { onboardingStep = 2; render(); },
    'start-empty': () => { setState({ ...state, session: true, onboarded: true }); history.replaceState(null, '', '#today'); render(); openRepoForm(); },
    signout: () => { clearTimeout(toastTimer); toast.classList.remove('visible'); undoAction = null; setState({ ...state, session: false }); },
    reset: () => confirmAction('Start with a clean slate?', 'This permanently removes all GitHabit data stored in this browser: repos, commits, profile, and preferences. Export a copy first if you want to keep it.', 'confirm-reset'),
    'confirm-reset': () => { closeSheet(); clearTimeout(toastTimer); undoAction = null; toast.classList.remove('visible'); onboardingStep = 0; repoFilter = 'active'; todayFilter = 'all'; searchQuery = ''; historyLimit = 20; heatmapWeeks = 26; setState(D.createState()); history.replaceState(null, '', '#today'); notify('Local workspace reset. A new beginning.'); },
    export: exportData
  };

  document.addEventListener('click', event => {
    const target = event.target.closest('[data-action]');
    if (!target || target.disabled) return;
    const handler = actions[target.dataset.action];
    if (handler) {
      event.preventDefault();
      try { handler(target); } catch (error) { notify(error.message || 'Something went wrong. Please try again.'); }
    }
  });

  document.addEventListener('input', event => {
    if (event.target.id === 'repo-search') {
      searchQuery = event.target.value;
      document.getElementById('repo-grid').innerHTML = repoGrid();
    }
  });

  document.addEventListener('change', event => {
    if (event.target.matches('[data-heatmap-range]')) {
      heatmapWeeks = Number(event.target.value);
      render();
    }
    if (event.target.matches('[data-day-picker]')) openDay(event.target.value, event.target.dataset.habit);
  });

  // One tab stop for the graph, with arrow navigation instead of hundreds of tabs.
  document.addEventListener('keydown', event => {
    if (!event.target.matches('.heat-cell')) return;
    const offsets = { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -1, ArrowDown: 1 };
    const cells = [...event.target.closest('.heatmap-cells').querySelectorAll('button')];
    const index = cells.indexOf(event.target);
    const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? cells.length - 1
      : event.key in offsets ? Math.max(0, Math.min(cells.length - 1, index + offsets[event.key])) : null;
    if (nextIndex === null) return;
    event.preventDefault();
    event.target.tabIndex = -1;
    cells[nextIndex].tabIndex = 0;
    cells[nextIndex].focus({ preventScroll: true });
    cells[nextIndex].scrollIntoView({ block: 'nearest', inline: 'nearest' });
  });

  document.addEventListener('submit', event => {
    const form = event.target;
    if (!['auth-form', 'repo-form', 'log-form', 'profile-form'].includes(form.id)) return;
    event.preventDefault();
    const values = Object.fromEntries(new FormData(form));
    try {
      if (form.id === 'auth-form') {
        // Intentionally discard email/password. They are never persisted or transmitted.
        form.reset();
        onboardingStep = 0;
        setState({ ...state, session: true });
        if (state.onboarded) go('today');
      } else if (form.id === 'repo-form') {
        const id = form.dataset.id || uid();
        const next = form.dataset.id ? D.updateHabit(state, id, values, today()) : D.createHabit(state, values, id, today());
        closeSheet();
        if (setState(next)) notify(form.dataset.id ? 'Repo updated. Keep growing.' : 'Your new repo is ready. Start with a small commit.');
      } else if (form.id === 'log-form') {
        const id = form.dataset.id || uid();
        const original = state.logs.find(log => log.id === id);
        const next = original ? D.updateLog(state, id, values.amount, values.note)
          : D.addLog(state, { habitId: form.dataset.habit, amount: values.amount, note: values.note, date: today(), id });
        closeSheet();
        if (setState(next)) notify(original ? 'Commit corrected.' : 'Progress committed. That’s a little win.', () => setState(original
          ? { ...state, logs: state.logs.map(log => log.id === id ? original : log) } : D.removeLog(state, id)));
      } else if (form.id === 'profile-form') {
        const name = values.name.trim();
        if (!name || name.length > 40) throw new Error('Enter a display name of 1–40 characters.');
        if (setState({ ...state, profile: { name, bio: values.bio.trim().slice(0, 160) } })) notify('Your workspace feels a little more like you.');
      }
    } catch (error) { formError(form, error.message); }
  });

  sheet.addEventListener('click', event => {
    if (event.target !== sheet) return;
    const bounds = sheet.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeSheet();
  });

  window.addEventListener('hashchange', () => {
    if (sheet.open) closeSheet();
    render();
    window.scrollTo({ top: 0, behavior: 'instant' });
    document.getElementById('main')?.focus({ preventScroll: true });
  });

  window.addEventListener('resize', scrollHeatmapsToToday);

  // Recompute today after midnight or when returning to the prototype.
  let lastDay = today();
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && lastDay !== today()) { lastDay = today(); render(); }
  });
  setInterval(() => {
    if (lastDay !== today()) { lastDay = today(); render(); }
  }, 60000);

  render();
  if (storageWarning) notify(storageWarning);
})();
