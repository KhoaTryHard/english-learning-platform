import { defineConfig, devices } from '@playwright/test';

// Browser contract fixtures only; no backend/database/Kafka acceptance.
export default defineConfig({
  testDir: './tests/w8',
  workers: 1,
  outputDir: './test-results/w8',
  reporter: [['list'], ['json', { outputFile: 'test-results/w8/report.json' }]],
  use: { baseURL: 'http://127.0.0.1:4180', serviceWorkers: 'block', trace: 'retain-on-failure' },
  projects: [
    {
      name: 'w8-desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1024 } },
    },
    { name: 'w8-mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' } },
  ],
  webServer: {
    command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 4180',
    url: 'http://127.0.0.1:4180',
    reuseExistingServer: true,
  },
});
