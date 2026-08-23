const { test, expect } = require('@playwright/test');
const { BASELINE_VIEWPORTS, assertNoHorizontalOverflow } = require('./helpers');

for (const viewport of BASELINE_VIEWPORTS) {
  test(`USDA entry point and unconfigured state are usable at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.goto('/profile');
    await page.getByRole('button', { name: /save profile/i }).click();

    await page.goto('/foods');
    const entryPoint = page.getByRole('link', { name: 'Search USDA' });
    await expect(entryPoint).toBeVisible();
    await entryPoint.click();

    await expect(page.getByRole('heading', { name: 'Search USDA' })).toBeVisible();
    await expect(page.getByText(/USDA food search is not configured/)).toBeVisible();
    await expect(page.getByLabel('Food name')).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Search USDA' })).toBeDisabled();
    await assertNoHorizontalOverflow(page);
  });
}
