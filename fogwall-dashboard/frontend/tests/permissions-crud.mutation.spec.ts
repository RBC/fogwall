import { test, expect } from './fixtures'
import type { Page } from '@playwright/test'

// Per-user permission CRUD on the user detail Permissions tab, plus the "Test Permission" evaluator: direct grants,
// group-inherited grants, denials. `observer` has no grants of its own, so it is the clean slate.
const modal = (page: Page) => page.locator('.fixed.inset-0')

async function openPermissions(page: Page, user: string) {
  await page.goto(`/dashboard/users/${user}`)
  await page.getByRole('button', { name: 'Permissions', exact: true }).click()
}

async function addPermission(
  page: Page,
  opts: { provider: string; path: string; matchType: string; grant: string },
) {
  await page.getByRole('button', { name: '+ Add Permission' }).click()
  const m = modal(page)
  await expect(m.getByRole('heading', { name: 'Add Permission' })).toBeVisible()
  const selects = m.getByRole('combobox')
  await selects.nth(0).selectOption(opts.provider)
  await m.getByPlaceholder('/owner/repo').fill(opts.path)
  await selects.nth(1).selectOption(opts.matchType)
  await selects.nth(2).selectOption(opts.grant)
  await m.getByRole('button', { name: 'Add', exact: true }).click()
  await expect(m).toHaveCount(0)
}

async function testPermission(
  page: Page,
  opts: { provider: string; path: string; grant: 'PUSH' | 'REVIEW' },
): Promise<import('@playwright/test').Locator> {
  await page.getByRole('button', { name: 'Test Permission' }).click()
  const m = modal(page)
  await expect(m.getByRole('heading', { name: 'Test Permission' })).toBeVisible()
  const selects = m.getByRole('combobox')
  await selects.nth(0).selectOption(opts.provider)
  await m.getByPlaceholder('/owner/repo').fill(opts.path)
  await selects.nth(1).selectOption(opts.grant)
  await m.getByRole('button', { name: 'Run test' }).click()
  return m
}

test.describe('per-user permissions', () => {
  test('every grant type and match type can be added, is badged, and is removable', async ({
    page,
  }) => {
    await openPermissions(page, 'observer')
    const stamp = Date.now()
    // Only the grant types the form offers by default (Review grants appear with require-review-permission,
    // which the fixture leaves off).
    const cases = [
      {
        provider: 'github',
        path: `/pw-${stamp}/literal`,
        matchType: 'LITERAL',
        grant: 'PUSH',
        badge: /push/i,
      },
      {
        provider: 'gitlab',
        path: `/pw-${stamp}/*`,
        matchType: 'GLOB',
        grant: 'PUSH',
        badge: /push/i,
      },
      {
        provider: 'gitea',
        path: `/pw-${stamp}/.*`,
        matchType: 'REGEX',
        grant: 'SELF_CERTIFY',
        badge: /self.?certify/i,
      },
    ]
    for (const c of cases) {
      await addPermission(page, c)
      const row = page.locator('tbody tr').filter({ hasText: c.path })
      await expect(row).toBeVisible()
      await expect(row).toContainText(c.provider)
      await expect(row).toContainText(new RegExp(c.matchType, 'i'))
      await expect(row.getByText(c.badge).first()).toBeVisible()
      await expect(row.getByText('local', { exact: true })).toBeVisible()
    }
    for (const c of cases) {
      const row = page.locator('tbody tr').filter({ hasText: c.path })
      await row.getByRole('button', { name: 'Remove' }).click()
      await expect(page.locator('tbody tr').filter({ hasText: c.path })).toHaveCount(0)
    }
  })

  test('Test Permission reports a direct grant, then a denial once it is removed', async ({
    page,
  }) => {
    await openPermissions(page, 'observer')
    const path = `/pw-direct-${Date.now()}`
    await addPermission(page, { provider: 'github', path, matchType: 'LITERAL', grant: 'PUSH' })

    let m = await testPermission(page, { provider: 'github', path, grant: 'PUSH' })
    await expect(m.getByText('ALLOWED')).toBeVisible()
    await expect(m.getByText(/Granted by direct permission/)).toBeVisible()
    await m.getByRole('button', { name: 'Close' }).click()

    // REVIEW was not granted
    m = await testPermission(page, { provider: 'github', path, grant: 'REVIEW' })
    await expect(m.getByText('DENIED')).toBeVisible()
    await m.getByRole('button', { name: 'Close' }).click()

    await page
      .locator('tbody tr')
      .filter({ hasText: path })
      .getByRole('button', { name: 'Remove' })
      .click()
    m = await testPermission(page, { provider: 'github', path, grant: 'PUSH' })
    await expect(m.getByText('DENIED')).toBeVisible()
  })

  test('Test Permission attributes a group-inherited grant to the group', async ({ page }) => {
    // admin is a member of platform-reviewers (REVIEW on github /fixture-dev/.*) and has no direct github grant
    await openPermissions(page, 'admin')
    await expect(page.getByText('platform-reviewers')).toBeVisible()
    const m = await testPermission(page, {
      provider: 'github',
      path: '/fixture-dev/anything',
      grant: 'REVIEW',
    })
    await expect(m.getByText('ALLOWED')).toBeVisible()
    await expect(m.getByText(/Granted via group membership: platform-reviewers/)).toBeVisible()
  })

  test('config-sourced permissions are locked and not removable; SELF_CERTIFY is scoped to one repo', async ({
    page,
  }) => {
    await openPermissions(page, 'dev')
    const selfCertify = page.locator('tbody tr').filter({ hasText: /self.?certify/i })
    await expect(selfCertify).toContainText('/fixture-dev/fogwall-fixture')
    await expect(selfCertify.getByText('locked (config)')).toBeVisible()
    await expect(selfCertify.getByRole('button', { name: 'Remove' })).toHaveCount(0)
  })
})
