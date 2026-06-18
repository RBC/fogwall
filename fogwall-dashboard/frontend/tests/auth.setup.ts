import { test as setup } from '@playwright/test'
import path from 'path'
import fs from 'fs'

const authFile = path.join(import.meta.dirname, '.auth/admin.json')

setup('authenticate as admin', async ({ page }) => {
  fs.mkdirSync(path.dirname(authFile), { recursive: true })

  await page.goto('/login.html')
  await page.fill('#username', 'admin')
  await page.fill('#password', 'admin')
  await page.click('button[type="submit"]')
  await page.waitForURL('**/dashboard/**')

  await page.context().storageState({ path: authFile })
})
