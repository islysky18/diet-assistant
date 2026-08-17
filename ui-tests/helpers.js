const { expect } = require('@playwright/test');

const DESKTOP_VIEWPORT = { name: 'desktop', width: 1280, height: 800 };
const MOBILE_VIEWPORT = { name: 'mobile', width: 390, height: 844 };
const BASELINE_VIEWPORTS = [DESKTOP_VIEWPORT, MOBILE_VIEWPORT];

async function assertNoHorizontalOverflow(page) {
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth
  }));
  expect(
    dimensions.scrollWidth,
    `Expected no horizontal overflow: scrollWidth=${dimensions.scrollWidth}, clientWidth=${dimensions.clientWidth}`
  ).toBeLessThanOrEqual(dimensions.clientWidth + 1);
}

async function assertReasonableCheckboxSize(locator) {
  await expect(locator, 'Expected checkbox to be visible before measuring it').toBeVisible();
  const box = await locator.boundingBox();
  expect(box, 'Expected visible checkbox to have a bounding box').not.toBeNull();
  expect(box.width, `Expected reasonable checkbox width, received ${box.width}px`).toBeGreaterThan(8);
  expect(box.width, `Expected reasonable checkbox width, received ${box.width}px`).toBeLessThan(32);
  expect(box.height, `Expected reasonable checkbox height, received ${box.height}px`).toBeGreaterThan(8);
  expect(box.height, `Expected reasonable checkbox height, received ${box.height}px`).toBeLessThan(32);
}

module.exports = {
  BASELINE_VIEWPORTS,
  assertNoHorizontalOverflow,
  assertReasonableCheckboxSize
};
