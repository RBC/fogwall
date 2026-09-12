import { test, expect } from './fixtures'
import type { Page } from '@playwright/test'

// The SCM API audit list, over the contributions scenarios in capture.py: gh, glab, fj and tea each created, edited and
// closed a PR/MR and an issue through their provider's listener, and each had one contribution refused.
const card = (page: Page, text: string | RegExp) =>
  page.locator('div.rounded-lg.shadow').filter({ hasText: text })

// 25 records per page, newest first: the gh block ran first, so its records sit on the second page.
async function findCard(page: Page, text: string | RegExp) {
  let c = card(page, text)
  if ((await c.count()) === 0) {
    await page.getByRole('button', { name: 'Next →' }).click()
    c = card(page, text)
  }
  return c
}

test.describe('SCM API actions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard/contributions')
    await expect(page.getByText('No SCM API action records found.')).toHaveCount(0)
  })

  test('every forwarded mutation links the entity it created or touched', async ({ page }) => {
    // A create, an edit and a close on both a PR and an issue, per client: the link is the same on all of them.
    await expect(page.getByRole('link', { name: 'PR #1' }).first()).toBeVisible()
    for (const provider of ['gitea', 'codeberg', 'gitlab']) {
      const rows = card(page, new RegExp(`${provider} ·`))
      await expect(rows.first()).toBeVisible()
      const linked = rows.filter({ has: page.getByRole('link', { name: /^(PR|Issue) #\d+$/ }) })
      await expect.poll(() => linked.count()).toBe(6)
    }
    const link = page.getByRole('link', { name: 'PR #1' }).first()
    await expect(link).toHaveAttribute('href', /https:\/\//)
    await page.getByRole('button', { name: 'Next →' }).click()
    const github = card(page, /github ·/)
    await expect
      .poll(() =>
        github.filter({ has: page.getByRole('link', { name: /^(PR|Issue) #\d+$/ }) }).count(),
      )
      .toBeGreaterThanOrEqual(3)
  })

  test('the entity link carries no state or title — a record is one past event', async ({
    page,
  }) => {
    const row = await findCard(page, /github · closePullRequest/)
    await expect(row.getByRole('link', { name: 'PR #1' })).toBeVisible()
    await expect(row).not.toContainText(/closed/i)
    await expect(row).not.toContainText(/Proposed via/)
    await expect(row).toContainText('upstream 200')
  })

  test('rejected contributions carry the reason in full and no payload', async ({ page }) => {
    await page.getByRole('button', { name: 'Rejected' }).click()
    await expect.poll(() => card(page, /Content rejected/).count()).toBe(4)
    // Two github · createIssue records exist (a forwarded issue and this rejected one); scope to the rejected
    // card so the locator can't match both mid-refilter, as the forwarded-mutation test does with FORWARDED.
    const secret = card(page, /github · createIssue/).filter({ hasText: 'REJECTED' })
    await expect(secret).toContainText(/secret detected/)
    await secret.click()
    // the row truncates; the expanded view wraps the whole reason
    await expect(secret.getByText(/generic-api-key/).last()).toBeVisible()
    await expect(secret.getByText('payload')).toHaveCount(0)
    await expect(secret.getByRole('link', { name: /#\d+/ })).toHaveCount(0)
  })

  test('a forwarded mutation records its payload on every dialect', async ({ page }) => {
    for (const op of [
      'gitlab · merge_requests.create',
      'gitea · pulls.create',
      'codeberg · issues.create',
    ]) {
      // newest first, so a client's refused issue create sits above its forwarded one
      const row = card(page, op).filter({ hasText: 'FORWARDED' }).first()
      await row.click()
      await expect(row.getByText('payload', { exact: true })).toBeVisible()
      await expect(row.locator('pre')).toContainText(/title/)
      await row.click()
    }
    // node ID and type are GitHub's; a REST record does not show them
    const rest = card(page, 'gitea · pulls.create').filter({ hasText: 'FORWARDED' }).first()
    await rest.click()
    await expect(rest.getByText(/nodeId/)).toHaveCount(0)
    const graphql = await findCard(page, 'github · createPullRequest')
    await graphql.click()
    await expect(graphql.getByText(/nodeId: /)).toBeVisible()
  })
})
