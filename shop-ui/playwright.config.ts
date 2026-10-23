import { defineConfig, devices } from '@playwright/test';

// Runs against e2e/stack/compose.yml (see its header), or E2E_BASE_URL
export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env['CI'],
  reporter: 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:18081',
    trace: 'retain-on-failure'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
  ]
});
