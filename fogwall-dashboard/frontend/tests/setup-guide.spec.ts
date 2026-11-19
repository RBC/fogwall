import { test, expect } from './fixtures'

// Developer quick start (/dashboard/setup). The static prose is not asserted; what matters is the generated,
// deployment-specific content: the service URL, one provider choice per enabled provider, and the clone, credential
// and per-transport git config blocks.
test.describe('setup guide', () => {
  test('quick start targets this deployment: reads via /proxy, pushes via /server', async ({
    asRole,
  }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    await expect(page.getByRole('heading', { name: 'Connect your git client' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Quick start' })).toBeVisible()

    const blocks = page.locator('pre')
    await expect(
      blocks.filter({
        hasText:
          /git clone -c remote\.origin\.pushurl=http:\/\/localhost:8080\/server\/github\.com\//,
      }),
    ).toBeVisible()
    await expect(
      blocks.filter({ hasText: /http:\/\/localhost:8080\/proxy\/github\.com\// }).first(),
    ).toBeVisible()
    await expect(
      blocks.filter({
        hasText: 'git config --global credential.http://localhost:8080.useHttpPath true',
      }),
    ).toBeVisible()
    // service-url is set in the profile, so no "derived from the browser" warning
    await expect(page.getByText('Heads up:')).toHaveCount(0)
  })

  test('provider choice lists every enabled provider, none for the disabled one', async ({
    asRole,
  }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    const options = page.getByLabel('Provider').locator('option')
    for (const provider of ['github', 'gitlab', 'codeberg', 'gitea', 'corp-forge']) {
      await expect(options.filter({ hasText: provider })).toHaveCount(1)
    }
    await expect(options.filter({ hasText: 'bitbucket' })).toHaveCount(0)
  })

  test('choosing a provider switches the clone commands to its host', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    await page.getByLabel('Provider').selectOption({ label: 'gitlab (gitlab.com)' })
    await expect(
      page
        .locator('pre')
        .filter({ hasText: /\/server\/gitlab\.com\/<owner>\/<repo>\.git/ })
        .first(),
    ).toBeVisible()
  })

  test('github more-options shows an SSH block pointing at fogwall', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    const header = page.getByRole('button', { expanded: false }).filter({ hasText: 'More options' })
    await expect(header.getByText('SSH', { exact: true })).toBeVisible()
    await header.click()
    await expect(
      page
        .locator('pre')
        .filter({ hasText: /ssh:\/\/[^\s]*localhost:2222/ })
        .first(),
    ).toBeVisible()
  })

  test('HTTP-only provider offers no SSH block', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    await page.getByLabel('Provider').selectOption({ label: 'gitlab (gitlab.com)' })
    const header = page.getByRole('button', { expanded: false }).filter({ hasText: 'More options' })
    await expect(header.getByText('SSH', { exact: true })).toHaveCount(0)
    await header.click()
    await expect(page.locator('pre').filter({ hasText: /ssh:\/\/[^\s]*gitlab/ })).toHaveCount(0)
  })

  test('custom-typed provider uses its configured host', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/setup')
    await page.getByLabel('Provider').selectOption({ label: 'corp-forge (forge.corp.example.com)' })
    await expect(
      page.locator('pre').filter({ hasText: 'forge.corp.example.com' }).first(),
    ).toBeVisible()
  })
})
