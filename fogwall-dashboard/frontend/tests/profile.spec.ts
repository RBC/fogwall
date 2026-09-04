import { test, expect, FIXTURE, requireCapture } from './fixtures'

// Profile page as the fixture developer: connected accounts, emails, SCM identities, SSH keys, and the
// "verified (provider)" badges that only exist once the capture run has linked real accounts via OAuth.
test.describe('profile', () => {
  test('shows connected-account slots for every OAuth-enabled provider', async ({ asRole }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')
    await expect(page.getByRole('heading', { name: 'Profile' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Connected accounts' })).toBeVisible()
    // corp-forge and bitbucket have no oauth block → not offered
    await expect(page.getByText('corp-forge', { exact: true })).toHaveCount(0)
  })

  test('lists the config-sourced email, identities and SSH key', async ({ asRole }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')

    await expect(page.getByText(FIXTURE.email)).toBeVisible()

    await page.getByRole('button', { name: 'SCM Identities' }).click()
    for (const provider of ['github', 'gitlab', 'gitea']) {
      // "Connected accounts" also lists providers, so match the identity row by provider + handle together
      await expect(
        page
          .getByRole('listitem')
          .filter({ hasText: provider })
          .filter({ hasText: FIXTURE.handle })
          .first(),
      ).toBeVisible()
    }

    await page.getByRole('button', { name: 'SSH Keys' }).click()
    // keys render as fingerprint + label, not the raw blob
    await expect(page.getByText(FIXTURE.sshFingerprint)).toBeVisible()
  })

  test('OAuth-linked identities, emails and keys carry verified-by-provider badges', async ({
    asRole,
  }) => {
    requireCapture()
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')

    // Connected accounts: at least github is linked and shows the handle + Unlink
    const github = page.getByRole('listitem').filter({ hasText: 'github' }).first()
    await expect(github).toContainText(FIXTURE.handle)
    await expect(github.getByRole('button', { name: 'Unlink' })).toBeVisible()

    // Emails: primary email verified by a provider
    const email = page.getByRole('listitem').filter({ hasText: FIXTURE.email })
    await expect(email.getByText(/verified \(\w+\)/)).toBeVisible()

    // Identities: github identity verified
    await page.getByRole('button', { name: 'SCM Identities' }).click()
    await expect(
      page.getByRole('listitem').filter({ hasText: 'github' }).getByText('verified'),
    ).toBeVisible()

    // SSH key: config-declared; carries a verified-by-provider badge only if the linked provider also reported it
    await page.getByRole('button', { name: 'SSH Keys' }).click()
    const key = page.getByRole('listitem').filter({ hasText: FIXTURE.sshFingerprint })
    await expect(key.getByText(/verified \(\w+\)|locked \(config\)/)).toBeVisible()
  })
})
