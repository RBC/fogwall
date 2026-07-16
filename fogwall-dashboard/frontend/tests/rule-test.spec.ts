import { test, expect } from '@playwright/test'

test('test a rule reports ALLOW and DENY for two newly added rules', async ({ page }) => {
  const suffix = Date.now()
  const allowOwner = `pw-allow-${suffix}`
  const denyOwner = `pw-deny-${suffix}`

  await page.goto('/dashboard/repos')
  await page.getByRole('button', { name: 'Rules', exact: true }).click()

  // Add an ALLOW rule matching allowOwner
  await page.getByRole('button', { name: '+ Add rule' }).click()
  let modal = page.locator('.fixed.inset-0')
  await expect(modal.getByRole('heading', { name: 'Add Rule' })).toBeVisible()
  await modal.getByRole('combobox').first().selectOption('owner')
  await modal.getByPlaceholder('myorg-*').fill(allowOwner)
  await modal.getByRole('button', { name: 'Add ALLOW rule' }).click()
  await expect(page.getByText(allowOwner)).toBeVisible()

  // Add a DENY rule matching denyOwner
  await page.getByRole('button', { name: '+ Add rule' }).click()
  modal = page.locator('.fixed.inset-0')
  await expect(modal.getByRole('heading', { name: 'Add Rule' })).toBeVisible()
  await modal.getByText('DENY', { exact: true }).click()
  await modal.getByRole('combobox').first().selectOption('owner')
  await modal.getByPlaceholder('myorg-*').fill(denyOwner)
  await modal.getByRole('button', { name: 'Add DENY rule' }).click()
  await expect(page.getByText(denyOwner)).toBeVisible()

  // Open the rule test panel and confirm the allow path matches
  await page.getByRole('button', { name: 'Test a rule' }).click()
  modal = page.locator('.fixed.inset-0')
  await expect(modal.getByRole('heading', { name: 'Test a rule' })).toBeVisible()
  await modal.getByPlaceholder('myorg').fill(allowOwner)
  await modal.getByPlaceholder('myrepo').fill('some-repo')
  await modal.getByRole('button', { name: 'Run test' }).click()
  await expect(modal.getByText('ALLOW', { exact: true }).first()).toBeVisible()

  // Reset and confirm the deny path matches
  await modal.getByRole('button', { name: 'Reset' }).click()
  await modal.getByPlaceholder('myorg').fill(denyOwner)
  await modal.getByPlaceholder('myrepo').fill('some-repo')
  await modal.getByRole('button', { name: 'Run test' }).click()
  await expect(modal.getByText('DENY', { exact: true }).first()).toBeVisible()

  await modal.getByRole('button', { name: 'Close' }).click()
})
