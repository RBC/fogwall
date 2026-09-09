import { test, expect } from './fixtures'

// Dashboard navigation (#548): a left sidebar with a primary group and, for admins, an Admin group.
// Collapsible to an icon rail; breadcrumbs at the top of the content area. Read-only.
test.describe('navigation', () => {
  test('sidebar lists the primary destinations and the admin group for an admin', async ({
    page,
  }) => {
    await page.goto('/dashboard/')
    const side = page.locator('aside')

    for (const label of ['Overview', 'Pushes', 'Proposals', 'Repos', 'Providers']) {
      await expect(side.getByRole('link', { name: label, exact: true })).toBeVisible()
    }
    // Issues is shown in place but not yet a destination.
    await expect(side.getByText('Issues', { exact: true })).toBeVisible()
    await expect(side.getByRole('link', { name: 'Issues' })).toHaveCount(0)

    for (const label of ['Users', 'Groups', 'Operations', 'Mirror cache']) {
      await expect(side.getByRole('link', { name: label, exact: true })).toBeVisible()
    }
  })

  test('a non-admin sees the primary group but no admin destinations', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/')
    const side = page.locator('aside')

    await expect(side.getByRole('link', { name: 'Pushes', exact: true })).toBeVisible()
    for (const label of ['Users', 'Groups', 'Operations', 'Mirror cache']) {
      await expect(side.getByRole('link', { name: label, exact: true })).toHaveCount(0)
    }
  })

  test('the sidebar collapses to an icon rail and back', async ({ page }) => {
    await page.goto('/dashboard/')
    await page.getByRole('button', { name: 'Collapse sidebar' }).click()
    await expect(page.getByRole('button', { name: 'Expand sidebar' })).toBeVisible()
    await page.getByRole('button', { name: 'Expand sidebar' }).click()
    await expect(page.getByRole('button', { name: 'Collapse sidebar' })).toBeVisible()
  })

  test('breadcrumbs reflect the route and link back up', async ({ page }) => {
    await page.goto('/dashboard/repos')
    const crumb = page.getByRole('navigation', { name: 'Breadcrumb' })
    await expect(crumb.getByText('Repos', { exact: true })).toBeVisible()
    await expect(crumb.getByRole('link', { name: 'Overview' })).toBeVisible()
    await crumb.getByRole('link', { name: 'Overview' }).click()
    await expect(page).toHaveURL(/\/dashboard\/?$/)
  })
})
