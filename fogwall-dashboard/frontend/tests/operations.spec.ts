import { test, expect } from './fixtures'

// Operations page: config reload plus the connectivity/probe diagnostics. The probes make live outbound
// calls (button-triggered), so only the static controls are asserted here.
test.describe('operations page', () => {
  test('shows the reload and connectivity controls for an admin', async ({ page }) => {
    await page.goto('/dashboard/operations')
    await expect(page.getByRole('heading', { name: 'Operations' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Configuration Reload' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Provider Connectivity' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Targeted Git Probe' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Reload config' })).toBeVisible()
  })
})
