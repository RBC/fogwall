import { test, expect } from '@playwright/test'

test('add and remove an SCM identity on the user detail page', async ({ page }) => {
  const testUsername = `playwright-test-${Date.now()}`

  await page.goto('/dashboard/users/admin')

  // Overview tab is default — scope actions to the SCM Identities section
  const scmSection = page.locator('section').filter({ has: page.getByText('SCM Identities') })

  await scmSection.getByRole('button', { name: '+ Add' }).click()
  await expect(page.getByRole('heading', { name: 'Add SCM Identity' })).toBeVisible()
  // Provider select auto-populates with the first configured provider
  await page.getByPlaceholder('github-handle').fill(testUsername)
  await page.getByRole('button', { name: 'Add', exact: true }).click()

  // New identity should appear in the list
  await expect(scmSection.getByText(testUsername)).toBeVisible()

  // Remove it and verify it disappears
  const row = scmSection.locator('li').filter({ hasText: testUsername })
  await row.getByRole('button', { name: 'Remove' }).click()
  await expect(scmSection.getByText(testUsername)).not.toBeVisible()
})
