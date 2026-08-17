const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './ui-tests',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['line'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.UI_TEST_BASE_URL || 'http://127.0.0.1:18080',
    browserName: 'chromium',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure'
  },
  outputDir: 'test-results'
});
