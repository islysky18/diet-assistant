const { test, expect } = require('@playwright/test');
const {
  BASELINE_VIEWPORTS,
  assertNoHorizontalOverflow,
  assertReasonableCheckboxSize
} = require('./helpers');

for (const viewport of BASELINE_VIEWPORTS) {
  test(`Saved Foods is usable on ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const response = await page.goto('/foods');
    expect(response.ok()).toBeTruthy();

    await expect(page.getByRole('heading', { name: 'Saved foods', exact: true })).toBeVisible();
    await expect(page.getByLabel('Search by food or brand')).toBeVisible();
    const checkbox = page.getByRole('checkbox', { name: 'Include inactive foods' });
    await expect(checkbox).toBeVisible();
    await expect(page.getByRole('button', { name: 'Search', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Clear', exact: true })).toBeVisible();

    await assertReasonableCheckboxSize(checkbox);
    await assertNoHorizontalOverflow(page);
  });
}
