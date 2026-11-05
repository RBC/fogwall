import { test, expect } from './fixtures'

// A grant made in the dashboard is on the audit record: who made it, whose access, from where.
test('a dashboard grant appears under permission changes', async ({ page, asRole }) => {
  const path = `/pw-audit-${Date.now()}/repo`
  await page.goto('/dashboard/users/newcomer')
  await page.getByRole('button', { name: 'Permissions', exact: true }).click()
  await page.getByRole('button', { name: '+ Add Permission' }).click()
  const modal = page.locator('.fixed.inset-0')
  const selects = modal.getByRole('combobox')
  await selects.nth(0).selectOption('github')
  await modal.getByPlaceholder('/owner/repo').fill(path)
  await selects.nth(1).selectOption('LITERAL')
  await selects.nth(2).selectOption('PUSH')
  await modal.getByRole('button', { name: 'Add', exact: true }).click()
  await expect(modal).toHaveCount(0)

  const auditor = await asRole('auditor')
  await auditor.goto('/dashboard/audit/access-changes?tab=permission-changes')
  await auditor.getByLabel('Search').fill(path)
  await auditor.getByRole('button', { name: 'Apply filters' }).click()
  const row = auditor.locator('tbody tr', { hasText: path })
  await expect(row).toHaveCount(1)
  await expect(row).toContainText('GRANTED')
  await expect(row).toContainText('newcomer')
  await expect(row).toContainText('admin')
  await expect(row).toContainText('dashboard')

  // And it is current access: held by newcomer, granted by admin.
  await auditor.goto('/dashboard/audit/current-access')
  await auditor.getByLabel('Holder').selectOption('newcomer')
  await auditor.getByRole('button', { name: 'Apply filters' }).click()
  const grant = auditor.locator('tbody tr', { hasText: path })
  await expect(grant).toHaveCount(1)
  await expect(grant).toContainText('admin')
})
