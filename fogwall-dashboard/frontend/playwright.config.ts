import { defineConfig, devices } from '@playwright/test'

// UI regression suite. The dashboard boots against a REAL pre-populated H2 database — tests/fixtures/fogwall.sql,
// produced by test/capture/capture.py from real pushes through real providers and scrubbed of personal values — and
// the matching config profile tests/fixtures/fogwall-playwright.yml. Nothing is seeded through the app; the fixture
// database is restored fresh before every run, so specs may mutate freely.
//
// Users (all `{noop}` local auth, see the profile): admin/admin, dev/password, reviewer/password, observer/password.
// Per-role storage states are written by tests/auth.setup.ts; pick one with the `asRole` fixture in tests/fixtures.ts.
export default defineConfig({
  testDir: './tests',
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
  },
  webServer: {
    // restoreFixtureDb recreates build/playwright-db/fogwall.mv.db from tests/fixtures/fogwall.sql, then `run` boots
    // the dashboard on it with the playwright profile (this directory is put on the classpath via -PconfigDir).
    // The migrator applies only migrations newer than the capture.
    // --no-daemon so the forked app is a child of the Gradle client Playwright launched and dies with it; with a
    // daemon it belongs to the daemon's process tree and outlives the run, and the next run would reuse it and
    // skip the database restore. `:stop` first is a cheap safety net; reuseExistingServer stays off.
    command:
      './gradlew -q :fogwall-dashboard:stop; ' +
      'FOGWALL_CONFIG_PROFILES=playwright ' +
      'FOGWALL_DATABASE_TYPE=h2-file ' +
      // Relative to the run task's working dir (fogwall-dashboard/); H2 insists on an explicit ./ for relative paths.
      'FOGWALL_DATABASE_PATH=./build/playwright-db/fogwall ' +
      './gradlew --no-daemon :fogwall-dashboard:restoreFixtureDb :fogwall-dashboard:run ' +
      '-PconfigDir=fogwall-dashboard/frontend/tests/fixtures',
    cwd: '../../',
    url: 'http://localhost:8080/api/health',
    // CI pre-builds the app (`:fogwall-dashboard:assemble`) before this runs, so `run` finds everything up-to-date and
    // only has to boot Spring — a matter of seconds. 120s is ample headroom for boot alone. See #550.
    timeout: 120_000,
    reuseExistingServer: false,
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'chromium',
      testIgnore: /\.mutation\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        // Default identity; specs that need another role use the `asRole` fixture.
        storageState: 'tests/.auth/admin.json',
      },
      dependencies: ['setup'],
    },
    {
      // Specs that change the status of captured push records (approve / reject / cancel through the UI). They run
      // only after every read-only spec has finished, so the fixture is still pristine for those.
      name: 'mutations',
      testMatch: /\.mutation\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'tests/.auth/admin.json',
      },
      dependencies: ['chromium'],
    },
  ],
})
