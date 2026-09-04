import { test, expect } from './fixtures'

// Repositories → Rules tab: allow + deny, LITERAL / GLOB / REGEX, SLUG / OWNER / NAME, enabled + disabled,
// PUSH / FETCH / BOTH — every shape declared under `rules:` in fogwall-playwright.yml.
test.describe('URL rules', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard/repos')
    await page.getByRole('button', { name: 'Rules' }).click()
  })

  const row = (page: import('@playwright/test').Page, value: string) =>
    page
      .locator('div.rounded-lg')
      .filter({ has: page.getByText(value, { exact: true }) })
      .first()

  test('renders allow and deny rules from config with their match type and target', async ({
    page,
  }) => {
    // deny · glob on repo NAME
    const denyGlob = row(page, '*.github.io')
    await expect(denyGlob.getByText('DENY')).toBeVisible()
    await expect(denyGlob.getByText('name:')).toBeVisible()
    await expect(denyGlob.getByText('glob', { exact: true })).toBeVisible()
    await expect(denyGlob.getByText('PUSH', { exact: true })).toBeVisible()

    // deny · literal on SLUG, both operations
    const denyLiteral = row(page, '/fixture-dev/archived-repo')
    await expect(denyLiteral.getByText('DENY')).toBeVisible()
    await expect(denyLiteral.getByText('literal', { exact: true })).toBeVisible()
    await expect(denyLiteral.getByText('PUSH & FETCH')).toBeVisible()

    // allow · literal on OWNER
    const allowOwner = row(page, 'fixture-dev')
    await expect(allowOwner.getByText('ALLOW')).toBeVisible()
    await expect(allowOwner.getByText('owner:')).toBeVisible()

    // allow · regex on SLUG (codeberg)
    const allowRegex = row(page, '/fixture-dev/.*')
    await expect(allowRegex.getByText('ALLOW')).toBeVisible()
    await expect(allowRegex.getByText('regex', { exact: true })).toBeVisible()
  })

  test('a config rule with enabled: false is not registered at all', async ({ page }) => {
    // JettyConfigurationBuilder skips disabled config rules at startup, so the UI never lists them.
    await expect(page.getByText('^(legacy|deprecated)-.*', { exact: true })).toHaveCount(0)
  })

  test('config rules are labelled config-sourced and cannot be deleted', async ({ page }) => {
    const allowOwner = row(page, 'fixture-dev')
    await expect(allowOwner.getByText('config', { exact: true })).toBeVisible()
    await expect(allowOwner.getByRole('button', { name: 'Delete' })).toHaveCount(0)
    // a FETCH-only rule renders its operation verbatim
    await expect(row(page, '*').getByText('FETCH', { exact: true })).toBeVisible()
  })

  test('scopes each rule to its provider', async ({ page }) => {
    await expect(row(page, '/fixture-dev/*').getByText('gitlab', { exact: true })).toBeVisible()
    await expect(row(page, '*.github.io').getByText('github', { exact: true })).toBeVisible()
  })

  test('rules are listed in priority order', async ({ page }) => {
    const orders = await page
      .locator('span[title="Priority order — lower runs first"]')
      .allTextContents()
    const numeric = orders.map((o) => Number(o.replace('#', '')))
    expect(numeric.length).toBeGreaterThanOrEqual(8)
    expect([...numeric].sort((a, b) => a - b)).toEqual(numeric)
  })
})
