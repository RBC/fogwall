import { test, expect } from './fixtures'

// URL rule CRUD on Repositories → Rules: create rules of each shape, see them listed with the right badges and
// 'local' source, exercise them through "Test a rule", delete them. Config rules stay read-only.
test.describe('URL rule CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard/repos')
    await page.getByRole('button', { name: 'Rules', exact: true }).click()
  })

  const modal = (page: import('@playwright/test').Page) => page.locator('.fixed.inset-0')
  const row = (page: import('@playwright/test').Page, value: string) =>
    page
      .locator('div.rounded-lg')
      .filter({ has: page.getByText(value, { exact: true }) })
      .first()

  test('literal slug ALLOW rule: created with badges, matches in the tester, deleted', async ({
    page,
  }) => {
    const slug = `/pw-org/pw-repo-${Date.now()}`
    await page.getByRole('button', { name: '+ Add rule' }).click()
    const m = modal(page)
    await expect(m.getByRole('heading', { name: 'Add Rule' })).toBeVisible()
    await m.getByRole('combobox').first().selectOption('slug')
    await m.getByRole('combobox').nth(1).selectOption('LITERAL')
    await m.getByRole('textbox').last().fill(slug)
    await m.getByRole('button', { name: 'Add ALLOW rule' }).click()

    const r = row(page, slug)
    await expect(r.getByText('ALLOW')).toBeVisible()
    await expect(r.getByText('slug:')).toBeVisible()
    await expect(r.getByText('literal', { exact: true })).toBeVisible()
    await expect(r.getByText('local', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Test a rule' }).click()
    await modal(page).getByPlaceholder('myorg').fill('pw-org')
    await modal(page).getByPlaceholder('myrepo').fill(slug.split('/')[2])
    await modal(page).getByRole('button', { name: 'Run test' }).click()
    await expect(modal(page).getByText('ALLOW', { exact: true }).first()).toBeVisible()
    await modal(page).getByRole('button', { name: 'Close' }).click()

    await r.getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByText(slug, { exact: true })).toHaveCount(0)
  })

  test('regex NAME DENY rule blocks a matching repo name and is deletable', async ({ page }) => {
    const pattern = `^pw-blocked-${Date.now()}-.*`
    await page.getByRole('button', { name: '+ Add rule' }).click()
    const m = modal(page)
    await m.getByText('DENY', { exact: true }).click()
    await m.getByRole('combobox').first().selectOption('name')
    await m.getByRole('combobox').nth(1).selectOption('REGEX')
    await m.getByRole('textbox').last().fill(pattern)
    await m.getByRole('button', { name: 'Add DENY rule' }).click()

    const r = row(page, pattern)
    await expect(r.getByText('DENY')).toBeVisible()
    await expect(r.getByText('name:')).toBeVisible()
    await expect(r.getByText('regex', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: 'Test a rule' }).click()
    await modal(page).getByPlaceholder('myorg').fill('anyone')
    await modal(page)
      .getByPlaceholder('myrepo')
      .fill(pattern.replace(/^\^|-\.\*$/g, '') + '-x')
    await modal(page).getByRole('button', { name: 'Run test' }).click()
    await expect(modal(page).getByText('DENY', { exact: true }).first()).toBeVisible()
    await modal(page).getByRole('button', { name: 'Close' }).click()

    await r.getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByText(pattern, { exact: true })).toHaveCount(0)
  })

  test('an invalid regex is refused by the form', async ({ page }) => {
    await page.getByRole('button', { name: '+ Add rule' }).click()
    const m = modal(page)
    await m.getByRole('combobox').nth(1).selectOption('REGEX')
    await m.getByRole('textbox').last().fill('/fixture-dev/(unclosed')
    await expect(m.getByText(/Invalid regular expression|Invalid regex/)).toBeVisible()
    // submit is ignored while the pattern is invalid: the modal stays open
    await m.getByRole('button', { name: 'Add ALLOW rule' }).click()
    await expect(m.getByRole('heading', { name: 'Add Rule' })).toBeVisible()
  })

  test('config rules have no delete button', async ({ page }) => {
    await expect(row(page, '*.github.io').getByRole('button', { name: 'Delete' })).toHaveCount(0)
  })
})
