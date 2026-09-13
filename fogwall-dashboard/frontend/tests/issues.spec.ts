import { test, expect } from './fixtures'

// Dashboard issue filing: the form lives under Contributions, and gates on the user having linked an
// OAuth account for an issue-enabled provider. The fixture enables issues on github but seeds no OAuth tokens, so the
// provider list resolves empty and the form shows its "link your account" guidance. The happy path calls a real
// upstream and is covered by backend tests, not here.
test.describe('issues page', () => {
  test('the Report an issue nav item is a live destination under Contributions', async ({
    page,
  }) => {
    await page.goto('/dashboard/')
    await page.locator('aside').getByRole('link', { name: 'Report an issue', exact: true }).click()
    await expect(page).toHaveURL(/\/dashboard\/contributions\/issues$/)
    await expect(page.getByRole('heading', { name: 'Report an issue' })).toBeVisible()
  })

  test('the old issues route lands on the page in its new home', async ({ page }) => {
    await page.goto('/dashboard/issues')
    await expect(page).toHaveURL(/\/dashboard\/contributions\/issues$/)
  })

  test('shows the link-your-account guidance when the user has no linked provider', async ({
    page,
  }) => {
    await page.goto('/dashboard/contributions/issues')
    await expect(page.getByText(/No providers are available for issue filing/)).toBeVisible()
    await expect(page.getByRole('link', { name: 'Profile' })).toBeVisible()
  })
})
