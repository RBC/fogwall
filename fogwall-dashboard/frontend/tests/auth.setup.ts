import { test as setup } from '@playwright/test'
import path from 'path'
import fs from 'fs'
import { USERS, authFile, type Role } from './fixtures'

// Logs in once per fixture user and saves a storage state per role (tests/.auth/<role>.json).
// Credentials come from tests/fixtures/fogwall-playwright.yml ({noop} local auth — fixture-only).
for (const role of Object.keys(USERS) as Role[]) {
  setup(`authenticate as ${role}`, async ({ page }) => {
    fs.mkdirSync(path.dirname(authFile(role)), { recursive: true })

    await page.goto('/login.html')
    await page.fill('#username', USERS[role].username)
    await page.fill('#password', USERS[role].password)
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard/**')

    await page.context().storageState({ path: authFile(role) })
  })
}
