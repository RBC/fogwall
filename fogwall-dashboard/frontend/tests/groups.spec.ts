import { test, expect } from './fixtures'

// Groups page — config-sourced groups from fogwall-playwright.yml are listed read-only with members and grants.
test.describe('permission groups', () => {
  test('lists config groups with member and rule counts', async ({ page }) => {
    await page.goto('/dashboard/groups')

    const reviewers = page
      .locator('div')
      .filter({ hasText: /^platform-reviewers/ })
      .first()
    await expect(reviewers).toBeVisible()
    await expect(page.getByText('Reviewers for the fixture-dev org')).toBeVisible()
    await expect(page.getByText('2 members · 1 rule')).toBeVisible()

    await expect(page.getByText('gitlab-contributors')).toBeVisible()
    await expect(page.getByText('1 member · 1 rule')).toBeVisible()
  })

  test('config group detail is read-only and shows members and grants', async ({ page }) => {
    await page.goto('/dashboard/groups')
    await page.getByText('platform-reviewers').click()

    await expect(page.getByRole('heading', { name: 'platform-reviewers' })).toBeVisible()
    const members = page
      .locator('div')
      .filter({ has: page.getByRole('heading', { name: 'Members' }) })
      .last()
    await expect(members.getByText('reviewer', { exact: true })).toBeVisible()
    await expect(members.getByText('admin', { exact: true })).toBeVisible()

    await expect(page.getByRole('heading', { name: 'Permission Rules' })).toBeVisible()
    const rule = page.getByRole('row').filter({ hasText: 'github' })
    await expect(rule).toContainText('fixture-dev')
    await expect(rule).toContainText('REGEX')
    await expect(rule).toContainText('REVIEW')

    // Read-only: no add-member select, no remove buttons, no rule editor
    await expect(page.getByRole('button', { name: 'Remove' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Add Rule' })).toHaveCount(0)
  })

  test('a DB group can be created and deleted', async ({ page }) => {
    await page.goto('/dashboard/groups')
    const name = `pw-group-${Date.now()}`
    await page.getByPlaceholder('Name').fill(name)
    await page.getByPlaceholder('Description (optional)').first().fill('created by playwright')
    await page.getByRole('button', { name: /create/i }).click()
    await expect(page.getByText(name)).toBeVisible()

    const card = page
      .locator('div')
      .filter({ hasText: new RegExp(`^${name}`) })
      .first()
    page.once('dialog', (d) => d.accept())
    await card.getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByText(name)).toHaveCount(0)
  })
})
