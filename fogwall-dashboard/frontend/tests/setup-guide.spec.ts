import { test, expect } from './fixtures'

// Developer setup guide (/dashboard/setup). The static prose is not asserted; what matters is the generated,
// deployment-specific content: service URL, one accordion per enabled provider, per-transport git config blocks.
test.describe('setup guide', () => {
  test('quick start clone command targets this deployment and the first provider', async ({
    asRole,
  }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    await expect(page.getByRole('heading', { name: 'Connect your git client' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Quick start' })).toBeVisible()
    await expect(
      page.getByText(/git clone http:\/\/localhost:8080\/server\/github\.com\//),
    ).toBeVisible()
    // service-url is set in the profile, so no "derived from the browser" warning
    await expect(page.getByText('Heads up:')).toHaveCount(0)
  })

  test('one accordion per enabled provider, none for the disabled one', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    for (const provider of ['github', 'gitlab', 'codeberg', 'gitea', 'corp-forge']) {
      await expect(
        page.getByRole('button', { expanded: false }).filter({ hasText: provider }),
      ).toBeVisible()
    }
    await expect(page.getByRole('button').filter({ hasText: 'bitbucket' })).toHaveCount(0)
  })

  test('github accordion shows HTTPS and SSH config blocks pointing at fogwall', async ({
    asRole,
  }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    const header = page.getByRole('button', { expanded: false }).filter({ hasText: 'github' })
    await expect(header.getByText('SSH', { exact: true })).toBeVisible()
    await header.click()
    await expect(
      page.getByRole('button', { expanded: true }).filter({ hasText: 'github' }),
    ).toBeVisible()

    // Global reroute: an HTTPS block and an SSH block, both referencing this fogwall and the upstream host
    const blocks = page.locator('pre')
    await expect(blocks.filter({ hasText: 'localhost:8080' }).first()).toBeVisible()
    await expect(blocks.filter({ hasText: 'github.com' }).first()).toBeVisible()
    await expect(blocks.filter({ hasText: /ssh:\/\/[^\s]*localhost:2222/ }).first()).toBeVisible()
    await expect(page.getByText(/Global — reroutes your github\.com pushes/)).toBeVisible()
  })

  test('HTTP-only provider shows no SSH block', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    const header = page.getByRole('button', { expanded: false }).filter({ hasText: 'gitlab' })
    await expect(header.getByText('SSH', { exact: true })).toHaveCount(0)
    await header.click()
    await expect(page.getByText(/Global — reroutes your gitlab\.com pushes/)).toBeVisible()
    await expect(page.locator('pre').filter({ hasText: /ssh:\/\/[^\s]*gitlab/ })).toHaveCount(0)
  })

  test('custom-typed provider uses its configured host', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    await page.getByRole('button', { expanded: false }).filter({ hasText: 'corp-forge' }).click()
    await expect(
      page.locator('pre').filter({ hasText: 'forge.corp.example.com' }).first(),
    ).toBeVisible()
  })
})
