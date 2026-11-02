import { defineConfig } from '@playwright/test';
export default defineConfig({
  outputDir: 'test-results-real',
  testDir: './e2e',
  testMatch: '*.real.spec.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 120000,
  expect: { timeout: 10000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-real' }]],
  use: {
    baseURL: 'http://127.0.0.1:4200',
    browserName: 'chromium',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'node scripts/preview.mjs http://127.0.0.1:18082',
    url: 'http://127.0.0.1:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 30000,
  },
  projects: [
    { name: 'desktop', use: { viewport: { width: 1440, height: 1000 } } },
    {
      name: 'mobile',
      use: { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
    },
  ],
});
