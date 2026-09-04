import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'FOGWALL_DATABASE_TYPE=h2-mem ./gradlew :fogwall-dashboard:run',
    cwd: '../../',
    url: 'http://localhost:8080/api/health',
    // CI pre-builds the app (`:fogwall-dashboard:assemble`) before this runs, so `run` finds everything up-to-date and
    // only has to boot Spring — a matter of seconds. 120s is ample headroom for boot alone. See #550.
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'tests/.auth/admin.json',
      },
      dependencies: ['setup'],
    },
  ],
})
