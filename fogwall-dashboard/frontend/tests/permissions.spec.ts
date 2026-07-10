import { test, expect } from '@playwright/test'

test('add and remove a permission from the user detail page', async ({ page }) => {
  const testPath = `/playwright-test/${Date.now()}/**`

  await page.goto('/dashboard/users/admin')

  // Switch to the Permissions tab
  await page.getByRole('button', { name: 'Permissions', exact: true }).click()
  await expect(page.getByRole('button', { name: '+ Add Permission' })).toBeVisible()

  // Open the modal and fill in the path; provider/matchType/operations use their defaults
  await page.getByRole('button', { name: '+ Add Permission' }).click()
  await expect(page.getByRole('heading', { name: 'Add Permission' })).toBeVisible()
  await page.getByPlaceholder('/owner/repo').fill(testPath)
  await page.getByRole('button', { name: 'Add', exact: true }).click()

  // New row should appear in the table with the correct badges
  const row = page.locator('tbody tr').filter({ hasText: testPath })
  await expect(row).toBeVisible()
  await expect(row.getByText('glob')).toBeVisible()
  await expect(row.getByText('push', { exact: true })).toBeVisible()

  // Remove the permission and verify it disappears
  await row.getByRole('button', { name: 'Remove' }).click()
  await expect(page.getByText(testPath)).not.toBeVisible()
})
