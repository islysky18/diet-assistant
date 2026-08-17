const { test, expect } = require('@playwright/test');
const { BASELINE_VIEWPORTS, assertNoHorizontalOverflow } = require('./helpers');

for (const viewport of BASELINE_VIEWPORTS) {
  test(`Food Entry is usable on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const response = await page.goto('/food');
    expect(response.ok()).toBeTruthy();

    await expect(page.getByRole('heading', { name: 'Record food', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'New food entry', exact: true })).toBeVisible();
    await expect(page.getByLabel('Saved food')).toBeVisible();
    await expect(page.getByLabel('Consumed amount')).toBeVisible();
    await expect(page.getByLabel('Meal type')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save food entry', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Recent Foods', exact: true })).toBeVisible();

    const recentCardsOrEmptyState = page.locator('.table-wrap').filter({ has: page.getByRole('button', { name: 'Quick Log' }) })
      .or(page.getByText('No recent foods yet.', { exact: true }));
    await expect(recentCardsOrEmptyState.first()).toBeVisible();
    await assertNoHorizontalOverflow(page);
  });
}
