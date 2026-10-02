import { test, expect } from './fixtures'

// A READER session (observer) sees the dashboard without its controls.
test.describe('reader', () => {
  test('sidebar labels the session reader and hides the write destinations', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/')
    const side = page.locator('aside')
    await expect(side.getByText('reader', { exact: true })).toBeVisible()
    await expect(side.getByRole('link', { name: 'Pushes', exact: true })).toBeVisible()
    await expect(side.getByRole('link', { name: 'Report an issue', exact: true })).toHaveCount(0)
  })

  test('profile shows the READER badge and no edit controls', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/profile')
    await expect(page.getByText('READER', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'SCM Identities' }).click()
    await expect(page.getByPlaceholder('your-username')).toHaveCount(0)
  })

  test('the issue form redirects to contributions', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/contributions/issues')
    await expect(page).toHaveURL(/\/dashboard\/contributions$/)
  })
})
