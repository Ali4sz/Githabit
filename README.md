# GitHabit

A working HTML, CSS, and JavaScript mock-up of a GitHub-inspired Android habit tracker, with Material 3–inspired navigation, themed surfaces, dialogs, and interaction states.

## Open the prototype

No npm install or build step is needed.

```sh
npm start
```

Open **http://127.0.0.1:4173**. This uses Python 3’s static server. Alternatively:

```sh
python3 -m http.server 4173 --bind 127.0.0.1
```

You can also open `index.html` directly in a modern browser. Local storage on `file://` URLs is browser-dependent, so the local server is recommended. All files must remain together.

Choose **Explore with sample habits** to preview the populated app, or use **made-up** sign-in details to try the onboarding and create a clean workspace.

### Screenshots

- [Desktop dashboard](previews/desktop-today.png)
- [Mobile dashboard](previews/mobile-today.png)
- [Mobile commit sheet](previews/mobile-commit.png)
- [Light-theme insights](previews/light-insights.png)

## Connected screens

- Demo Google/email sign-in and three-step onboarding.
- **Today:** overall contribution heatmap, active habits, daily goals, activity streak, and recent commits.
- **Repos:** search, active/paused/archived filters, creation, and editing.
- **Repo detail:** individual heatmap, daily progress, activity history, pause, archive, and resume.
- **Insights:** real calculations from the stored logs, weekly progress, and habit consistency.
- **Activity:** chronological commits, corrections, deletion, and export.
- **Settings:** profile, light/dark themes, help, sign-out, JSON export, and confirmed data reset.

Phone layouts use bottom navigation and bottom-sheet dialogs. Desktop layouts use a sidebar and a wider dashboard.

## Tracking rules

- Habits repeat daily and have a positive numeric target and unit. Up to three decimal places are supported.
- A commit adds progress; **Complete goal** adds only the remaining amount. Additional progress can exceed the goal, but completion scoring is capped at 100%.
- An individual heatmap shows progress toward that day’s target. The overall heatmap averages the capped percentages of that day’s active habits; pages and minutes are never added together.
- Days before habit creation or with no active habits are not applicable, rather than failed days.
- An **activity streak** counts consecutive days with any logged progress. An unfinished today does not break yesterday’s streak.
- Goal/status changes apply from today onward. Earlier calendar days retain their targets and active/paused/archived status.
- Units cannot be changed after the first commit. Create a separate repo to track a different unit.
- New commits are for today; existing historical commits can be corrected or removed. Dates use the browser’s local calendar.
- Small graph cells have readable labels. Use **Explore by date** for larger controls and numeric summaries, or use the arrow keys on the graph. Home/End go to the first/latest visible date.

## Prototype boundaries

This is **not a native Android app or production authentication system**. It has no backend, cloud sync, account isolation, reminders, push notifications, or GitHub integration. No sign-in credentials are saved or transmitted. All “accounts” share the same local prototype workspace.

Habit data and preferences are saved in this browser’s local storage under `githabit.prototype.v1`. Signing out keeps that data; reset removes the app’s local workspace. Clearing browser data can erase it. Export JSON for a copy; importing that file is not implemented.

The interface is Material 3–inspired, not an official Material component library or a certified accessibility implementation. No GitHub logos are used. Optional Google Fonts requests provide DM Sans/Manrope; system fonts are the offline fallback. The app does not otherwise require external services.

## Files

| File | Purpose |
| --- | --- |
| `index.html` | Entry point and dialog/live-announcement containers |
| `styles.css` | Theme tokens, responsive layouts, and component styles |
| `domain.js` | Pure date, habit, log, history, and analytics logic |
| `app.js` | Screens, routing, local persistence, and UI interactions |
| `tests/domain.test.cjs` | Dependency-free Node unit tests |
| `tests/browser.js` | End-to-end Playwright CLI journey |

## Verify

With Node 18 or newer:

```sh
npm test
npm run check
```

For the browser journey, start the local server, then use an installed Playwright CLI and browser:

```sh
playwright-cli -s=githabit-test open http://127.0.0.1:4173
playwright-cli -s=githabit-test run-code --filename=tests/browser.js
playwright-cli -s=githabit-test run-code --filename=tests/layouts.js
playwright-cli -s=githabit-test close
```

**Use an isolated test session:** the browser journey resets the local GitHabit workspace in that session. It covers sign-in, onboarding, creation, partial and full progress, undo, history correction/deletion, lifecycle changes, date exploration, search, insights, theme/profile persistence, export, reset, and layouts from 320px to 1440px. Run `layouts.js` afterward to check every primary screen and both edit/log forms at five viewport widths.
