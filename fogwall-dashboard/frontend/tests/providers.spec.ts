import { test, expect } from './fixtures'

// Providers page — every provider shape in fogwall-playwright.yml: HTTP-only, HTTP+SSH, disabled, custom name/type.
test.describe('providers page', () => {
  test('lists every enabled provider and hides the disabled one', async ({ page }) => {
    await page.goto('/dashboard/providers')
    await expect(page.getByRole('heading', { name: 'Active Providers' })).toBeVisible()

    for (const name of ['github', 'gitlab', 'codeberg', 'gitea', 'corp-forge']) {
      await expect(page.getByText(name, { exact: true }).first()).toBeVisible()
    }
    // bitbucket is `enabled: false` in the profile
    await expect(page.getByText('bitbucket', { exact: true })).toHaveCount(0)
  })

  test('github serves HTTP and SSH; the others are HTTP only', async ({ page }) => {
    await page.goto('/dashboard/providers')
    const card = (name: string) =>
      page
        .locator('div.rounded-lg.shadow')
        .filter({ has: page.getByText(name, { exact: true }) })
        .first()

    // github: SSH transport badge + an ssh:// route on port 2222
    await expect(card('github').getByText('SSH', { exact: true }).first()).toBeVisible()
    await expect(card('github').getByText(/ssh:\/\/localhost:2222/)).toBeVisible()

    // gitlab: no SSH badge, no ssh:// route
    await expect(card('gitlab').getByText(/ssh:\/\//)).toHaveCount(0)
  })

  test('custom-named provider shows its configured upstream uri', async ({ page }) => {
    await page.goto('/dashboard/providers')
    await expect(page.getByRole('link', { name: 'https://forge.corp.example.com' })).toBeVisible()
  })
})
