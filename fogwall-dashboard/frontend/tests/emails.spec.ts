import { test, expect } from '@playwright/test'

test('add and remove an email on the user detail page', async ({ page }) => {
  const testEmail = `playwright-${Date.now()}@example.com`

  await page.goto('/dashboard/users/admin')

  // Overview tab is default — scope actions to the Email Addresses section
  const emailSection = page.locator('section').filter({ has: page.getByText('Email Addresses') })

  await emailSection.getByRole('button', { name: '+ Add' }).click()
  await expect(page.getByRole('heading', { name: 'Add Email' })).toBeVisible()
  await page.getByPlaceholder('user@example.com').fill(testEmail)
  await page.getByRole('button', { name: 'Add', exact: true }).click()

  // New email should appear in the list
  await expect(emailSection.getByText(testEmail)).toBeVisible()

  // Remove it and verify it disappears
  const row = emailSection.locator('li').filter({ hasText: testEmail })
  await row.getByRole('button', { name: 'Remove' }).click()
  await expect(emailSection.getByText(testEmail)).not.toBeVisible()
})
