import { test, expect, FIXTURE } from './fixtures'

// Self-service profile actions as the developer: add/remove email, SSH key, SCM identity; locked and verified
// entries cannot be removed. The database is restored before every run, so these mutations are safe.
const EXTRA_KEY =
  'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIKKZ3EgguelbtKdTczUDWxdxPorihArViiZYeEK89qCn pw-extra@example.com'
const EXTRA_KEY_FINGERPRINT = 'SHA256:AsJ6BvLw1GZn9JjS7m7TmxJkpWzNY9AxYEr4CxbQWeY'

test.describe('profile self-service', () => {
  test('add and remove an email; the config email stays locked', async ({ asRole }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')
    const email = `pw-${Date.now()}@example.com`

    await page.getByPlaceholder('you@example.com').fill(email)
    await page.getByRole('button', { name: 'Add', exact: true }).click()
    const row = page.getByRole('listitem').filter({ hasText: email })
    await expect(row).toBeVisible()
    await expect(row.getByText(/verified/)).toHaveCount(0)
    await row.getByRole('button', { name: 'Remove' }).click()
    await expect(page.getByRole('listitem').filter({ hasText: email })).toHaveCount(0)

    // the config-declared email is locked: no Remove button
    const locked = page.getByRole('listitem').filter({ hasText: FIXTURE.email })
    await expect(locked.getByText(/locked \(config\)|verified/)).toBeVisible()
    await expect(locked.getByRole('button', { name: 'Remove' })).toHaveCount(0)
  })

  test('add and remove an SSH key; the config key stays locked', async ({ asRole }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')
    await page.getByRole('button', { name: 'SSH Keys' }).click()

    await page.getByPlaceholder('ssh-ed25519 AAAA... or ssh-rsa AAAA...').fill(EXTRA_KEY)
    await page.getByPlaceholder('Label (optional)').fill('playwright laptop')
    await page.getByRole('button', { name: 'Add', exact: true }).click()
    const row = page.getByRole('listitem').filter({ hasText: EXTRA_KEY_FINGERPRINT })
    await expect(row).toBeVisible()
    await expect(row).toContainText('playwright laptop')
    await row.getByRole('button', { name: 'Remove' }).click()
    await expect(page.getByRole('listitem').filter({ hasText: EXTRA_KEY_FINGERPRINT })).toHaveCount(
      0,
    )

    const locked = page.getByRole('listitem').filter({ hasText: FIXTURE.sshFingerprint })
    await expect(locked.getByRole('button', { name: 'Remove' })).toHaveCount(0)
  })

  test('rejects a malformed SSH key', async ({ asRole }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')
    await page.getByRole('button', { name: 'SSH Keys' }).click()
    await page.getByPlaceholder('ssh-ed25519 AAAA... or ssh-rsa AAAA...').fill('not a key')
    await page.getByRole('button', { name: 'Add', exact: true }).click()
    await expect(page.getByText(/invalid|malformed|not a valid|could not parse/i)).toBeVisible()
  })

  test('add and remove a manual SCM identity; verified and config ones cannot be removed', async ({
    asRole,
  }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/profile')
    await page.getByRole('button', { name: 'SCM Identities' }).click()

    const username = `pw-${Date.now()}`
    const form = page.locator('form').filter({ has: page.getByPlaceholder('your-username') })
    await form.getByRole('combobox').selectOption('corp-forge')
    await page.getByPlaceholder('your-username').fill(username)
    await form.getByRole('button', { name: 'Add', exact: true }).click()
    const row = page.getByRole('listitem').filter({ hasText: username })
    await expect(row).toBeVisible()
    await expect(row).toContainText('corp-forge')
    await expect(row.getByText('verified')).toHaveCount(0)
    await row.getByRole('button', { name: 'Remove' }).click()
    await expect(page.getByRole('listitem').filter({ hasText: username })).toHaveCount(0)

    // OAuth-verified github identity: no Remove (must be unlinked instead); config gitea identity: locked
    // (exclude the "Connected accounts" rows, which carry an Unlink button)
    const github = page
      .getByRole('listitem')
      .filter({ hasText: 'github' })
      .filter({ hasText: FIXTURE.handle })
      .filter({ hasNotText: 'Unlink' })
    await expect(github.first().getByText('verified')).toBeVisible()
    await expect(github.first().getByRole('button', { name: 'Remove' })).toHaveCount(0)
    const gitea = page
      .getByRole('listitem')
      .filter({ hasText: 'gitea' })
      .filter({ hasText: FIXTURE.handle })
      .filter({ hasNotText: 'Unlink' })
    await expect(gitea.first().getByText('locked (config)')).toBeVisible()
    await expect(gitea.first().getByRole('button', { name: 'Remove' })).toHaveCount(0)
  })

  test('cannot claim an SCM identity another user already holds', async ({ asRole }) => {
    // reviewer holds github:fixture-reviewer (seeded via the API during capture)
    const page = await asRole('observer')
    await page.goto('/dashboard/profile')
    await page.getByRole('button', { name: 'SCM Identities' }).click()
    const form = page.locator('form').filter({ has: page.getByPlaceholder('your-username') })
    await form.getByRole('combobox').selectOption('github')
    await page.getByPlaceholder('your-username').fill('fixture-reviewer')
    await form.getByRole('button', { name: 'Add', exact: true }).click()
    await expect(page.getByText(/already claimed/i)).toBeVisible()
  })
})
