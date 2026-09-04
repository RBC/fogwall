import { test, expect } from './fixtures'
import type { Page } from '@playwright/test'

// Group CRUD: create a group, add a member and a rule, see the member inherit it, then take it all apart.
const card = (page: Page, name: string) =>
  page
    .locator('div')
    .filter({ hasText: new RegExp(`^${name}`) })
    .first()

test.describe('group CRUD', () => {
  test('create → add member → add rule → member inherits → remove rule/member → delete', async ({
    page,
  }) => {
    const name = `pw-group-${Date.now()}`
    const value = `/pw-${Date.now()}/**`

    await page.goto('/dashboard/groups')
    await page.getByPlaceholder('Name').fill(name)
    await page.getByPlaceholder('Description (optional)').first().fill('created by playwright')
    await page.getByRole('button', { name: /create/i }).click()
    await expect(page.getByText(name)).toBeVisible()
    await page.getByText(name).click()

    // DB group: editable name/description, no "config" badge
    await expect(page.getByPlaceholder('Group name')).toHaveValue(name)

    // member
    const members = page
      .locator('div')
      .filter({ has: page.getByRole('heading', { name: 'Members' }) })
      .last()
    await members.getByRole('combobox').selectOption('observer')
    await members.getByRole('button', { name: 'Add', exact: true }).click()
    await expect(members.getByText('observer', { exact: true })).toBeVisible()

    // rule
    const rules = page
      .locator('div')
      .filter({ has: page.getByRole('heading', { name: 'Permission Rules' }) })
      .last()
    const selects = rules.getByRole('combobox')
    await selects.nth(0).selectOption('gitlab')
    await rules.getByPlaceholder('Value (e.g. /myorg/**)').fill(value)
    await selects.nth(1).selectOption('GLOB')
    await selects.nth(2).selectOption('PUSH_AND_REVIEW')
    await rules.getByRole('button', { name: 'Add Rule' }).click()
    const ruleRow = rules.getByRole('row').filter({ hasText: value })
    await expect(ruleRow).toContainText('gitlab')
    await expect(ruleRow).toContainText('GLOB')
    await expect(ruleRow).toContainText('PUSH_AND_REVIEW')

    // list card reflects counts (after a reload — the sidebar counts are loaded with the list)
    await page.reload()
    await expect(card(page, name)).toContainText('1 member · 1 rule')
    await page.getByText(name).click()

    // the member inherits it: visible on their Permissions tab and honoured by the evaluator
    await page.goto('/dashboard/users/observer')
    await page.getByRole('button', { name: 'Permissions', exact: true }).click()
    await expect(page.getByText(name)).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: value })).toBeVisible()
    await page.getByRole('button', { name: 'Test Permission' }).click()
    const m = page.locator('.fixed.inset-0')
    await m.getByRole('combobox').nth(0).selectOption('gitlab')
    await m.getByPlaceholder('/owner/repo').fill(value.replace('/**', '/some-repo'))
    await m.getByRole('combobox').nth(1).selectOption('PUSH')
    await m.getByRole('button', { name: 'Run test' }).click()
    await expect(m.getByText('ALLOWED')).toBeVisible()
    await expect(m.getByText(new RegExp(`Granted via group membership: ${name}`))).toBeVisible()

    // tear down
    await page.goto('/dashboard/groups')
    await page.getByText(name).click()
    await rules
      .getByRole('row')
      .filter({ hasText: value })
      .getByRole('button', { name: 'Remove' })
      .click()
    await expect(rules.getByRole('row').filter({ hasText: value })).toHaveCount(0)
    await members
      .locator('li')
      .filter({ hasText: 'observer' })
      .getByRole('button', { name: 'Remove' })
      .click()
    await expect(members.getByText('No members.')).toBeVisible()
    await card(page, name).getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByText(name)).toHaveCount(0)
  })

  test('config groups cannot be edited or deleted', async ({ page }) => {
    await page.goto('/dashboard/groups')
    await expect(
      card(page, 'platform-reviewers').getByRole('button', { name: 'Delete' }),
    ).toHaveCount(0)
    await page.getByText('platform-reviewers').click()
    await expect(page.getByPlaceholder('Group name')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Add Rule' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Remove' })).toHaveCount(0)
  })

  test('rejects a duplicate group name', async ({ page }) => {
    await page.goto('/dashboard/groups')
    await page.getByPlaceholder('Name').fill('platform-reviewers')
    await page.getByRole('button', { name: /create/i }).click()
    await expect(page.getByText(/already exists|duplicate|conflict/i)).toBeVisible()
  })
})
