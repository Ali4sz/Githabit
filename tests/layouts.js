// Run after browser.js in the same isolated Playwright CLI session.
async (page) => {
  const checks = [];
  const assertFits = async name => {
    const dimensions = await page.evaluate(() => ({ width: innerWidth, page: document.documentElement.scrollWidth,
      dialog: document.getElementById('sheet').open ? document.getElementById('sheet').scrollWidth : 0,
      dialogWidth: document.getElementById('sheet').clientWidth }));
    if (dimensions.page > dimensions.width || dimensions.dialog > dimensions.dialogWidth) {
      throw new Error(`${name}: overflow ${JSON.stringify(dimensions)}`);
    }
    checks.push(name);
  };
  for (const width of [320, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    for (const [route, title] of [['today', 'Today'], ['repos', 'Repositories'], ['insights', 'Insights'],
      ['activity', 'Activity log'], ['settings', 'Settings'], ['repo/read-every-day', 'Read every day']]) {
      await page.goto(`http://127.0.0.1:4173/#${route}`);
      await page.waitForFunction(expected => document.title === `GitHabit — ${expected}`, title);
      await assertFits(`${route} at ${width}px`);
    }
    await page.locator('[data-action="edit-repo"]').click();
    await page.getByRole('dialog').waitFor();
    await assertFits(`Repo form at ${width}px`);
    await page.keyboard.press('Escape');
    await page.locator('[data-action="log"]').click();
    await page.getByRole('dialog').waitFor();
    await assertFits(`Log form at ${width}px`);
    await page.keyboard.press('Escape');
  }
  await page.goto('http://127.0.0.1:4173/#settings');
  await page.locator('[data-action="set-theme"][data-value="light"]').click();
  await page.goto('http://127.0.0.1:4173/#today');
  await page.setViewportSize({ width: 390, height: 844 });
  await assertFits('Light theme at 390px');
  await page.locator('[data-action="theme"]').click();
  return { passed: checks.length, checks };
}
