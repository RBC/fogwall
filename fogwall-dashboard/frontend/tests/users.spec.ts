import { test, expect, FIXTURE } from './fixtures'

// Users list + user detail: the four fixture users, dev's identities, and per-user permission rules
// (direct + inherited from groups) with every grant type and match type the profile declares.
test.describe('users', () => {
  test('lists every fixture user', async ({ page }) => {
    await page.goto('/dashboard/users')
    await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible()
    for (const u of ['admin', 'dev', 'reviewer', 'observer']) {
      await expect(page.getByRole('cell', { name: u, exact: true })).toBeVisible()
    }
  })

  test("dev's overview shows SCM identities and email", async ({ page }) => {
    await page.goto('/dashboard/users/dev')
    const scm = page.locator('section').filter({ has: page.getByText('SCM Identities') })
    for (const provider of ['github', 'gitlab', 'gitea']) {
      await expect(scm.getByText(provider, { exact: true })).toBeVisible()
    }
    await expect(scm.getByText(FIXTURE.handle).first()).toBeVisible()
    await expect(page.getByText(FIXTURE.email)).toBeVisible()
  })

  test("dev's permissions tab shows PUSH and SELF_CERTIFY grants plus the inherited group grant", async ({
    page,
  }) => {
    await page.goto('/dashboard/users/dev')
    await page.getByRole('button', { name: 'Permissions', exact: true }).click()

    const rows = page.getByRole('row')
    await expect(rows.filter({ hasText: 'SELF_CERTIFY' })).toContainText(
      `/${FIXTURE.handle}/${FIXTURE.repo}`,
    )
    // match-type and grant badges render lowercase
    await expect(
      rows.filter({ hasText: 'github' }).filter({ hasText: /push/i }).first(),
    ).toBeVisible()
    await expect(rows.filter({ hasText: 'gitea' })).toContainText(/regex/i)
    // inherited via the gitlab-contributors group
    await expect(page.getByText('gitlab-contributors')).toBeVisible()
    // config-sourced rows are locked, not removable
    await expect(page.getByRole('button', { name: 'Remove' })).toHaveCount(0)
  })

  test("reviewer's permissions show REVIEW and PUSH_AND_REVIEW", async ({ page }) => {
    await page.goto('/dashboard/users/reviewer')
    await page.getByRole('button', { name: 'Permissions', exact: true }).click()
    const rows = page.getByRole('row')
    await expect(rows.filter({ hasText: 'PUSH_AND_REVIEW' })).toContainText('gitlab')
    await expect(
      rows
        .filter({ hasText: 'github' })
        .filter({ hasText: /review/i })
        .filter({ hasNotText: /push/i })
        .first(),
    ).toBeVisible()
    await expect(page.getByText('platform-reviewers')).toBeVisible()
  })

  test('observer has no grants', async ({ page }) => {
    await page.goto('/dashboard/users/observer')
    await page.getByRole('button', { name: 'Permissions', exact: true }).click()
    await expect(
      page.getByRole('row').filter({ hasText: /push|review|self.certify/i }),
    ).toHaveCount(0)
  })
})
