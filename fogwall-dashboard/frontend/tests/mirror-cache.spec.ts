import { test, expect } from './fixtures'

// Mirror cache page. The cache is per-pod runtime state populated only by real clones, so on replay only the
// empty state is reachable; invalidation itself is covered by LocalRepositoryCacheTest / AdminCacheControllerTest.
test.describe('mirror cache page', () => {
  test('renders both proxy modes empty for an admin', async ({ page }) => {
    await page.goto('/dashboard/mirror-cache')
    await expect(page.getByRole('heading', { name: 'Local mirror cache' })).toBeVisible()
    await expect(page.getByRole('heading', { name: /Server mode.*\(0 mirrors\)/ })).toBeVisible()
    await expect(
      page.getByRole('heading', { name: /Transparent proxy.*\(0 mirrors\)/ }),
    ).toBeVisible()
    await expect(page.getByText('No mirrors cached.')).toHaveCount(2)
    // nothing to invalidate → no bulk button
    await expect(page.getByRole('button', { name: 'Invalidate all' })).toHaveCount(0)
  })

  test('cache API is refused for a non-admin', async ({ asRole }) => {
    const page = await asRole('observer')
    const res = await page.request.get('/api/admin/cache')
    expect(res.status()).toBe(403)
    await page.goto('/dashboard/mirror-cache')
    await expect(page.getByText('No mirrors cached.')).toHaveCount(0)
  })
})
