import type { Page } from '@playwright/test'
import { test, expect, FIXTURE, requireCapture, atCaptureTime, AFTER_CAPTURE } from './fixtures'

// Contribution activity: the Overview tiles everyone sees, a person's own activity under /me, Audit → Participation
// for auditors with its drill-down into Decisions, a user's Activity tab for admins, and a repository's push history.
// Read-only. Figures are checked against the API response the page rendered, so a re-capture that changes the counts
// does not break them; the clock is pinned so the captured records stay inside the window.

interface Proposals {
  pullRequests: { opened: number; merged: number }
  issues: { opened: number }
  edited: number
  rejected: number
}

interface Group {
  provider: string
  owner?: string
  repo?: string
  pushes: Record<string, number>
  proposals?: Proposals
}

interface Activity {
  totals: { pushes: Record<string, number>; proposals?: Proposals }
  contributors?: number
  activeRepos?: number
  providers: Group[]
  repos: Group[]
}

const REPO = `github/${FIXTURE.handle}/${FIXTURE.repo}`

const total = (byStatus: Record<string, number>) =>
  Object.values(byStatus).reduce((a, b) => a + b, 0)

/** The JSON of the next successful response for `path`, from a listener set up before the request is made. */
function nextActivity(page: Page, path: string): Promise<Activity> {
  return page
    .waitForResponse((r) => new URL(r.url()).pathname === path && r.ok())
    .then((r) => r.json() as Promise<Activity>)
}

test.describe('contribution activity', () => {
  test.beforeEach(() => requireCapture())

  test('the Overview shows activity across fogwall to a reader', async ({ asRole }) => {
    const page = await asRole('observer')
    await atCaptureTime(page)
    const summary = nextActivity(page, '/api/activity/summary')
    await page.goto('/dashboard/')
    const a = await summary
    expect(a.contributors).toBeGreaterThan(0)

    const main = page.getByRole('main')
    const tile = (label: string) => main.getByRole('group', { name: label, exact: true })
    await expect(tile('Contributors')).toContainText(String(a.contributors))
    await expect(tile('Pushes forwarded')).toContainText(
      `${a.totals.pushes.FORWARDED ?? 0}of ${total(a.totals.pushes)} submitted`,
    )
    await expect(tile('Active repositories')).toContainText(String(a.activeRepos))
    await expect(
      main.getByRole('list', { name: 'Most active repositories' }).getByRole('listitem'),
    ).toHaveCount(a.repos.length)
    await expect(main.getByRole('list', { name: 'Most active repositories' })).toContainText(REPO)
  })

  test('your own activity adds up, and its window follows the period picked', async ({
    asRole,
  }) => {
    const page = await asRole('dev')
    await atCaptureTime(page)
    const first = nextActivity(page, '/api/activity/me')
    await page.goto('/dashboard/me')
    await expect(page).toHaveURL(/\/dashboard\/me\/activity$/)
    const a = await first

    const main = page.getByRole('main')
    const pushes = main.getByRole('group', { name: 'Pushes', exact: true })
    expect(total(a.totals.pushes)).toBeGreaterThan(0)
    await expect(pushes).toContainText(`Pushes${total(a.totals.pushes)}`)
    await expect(pushes.locator('dl > div').filter({ hasText: 'Forwarded' })).toContainText(
      String(a.totals.pushes.FORWARDED ?? 0),
    )
    await expect(main.getByRole('group', { name: 'Pull requests', exact: true })).toContainText(
      `Pull requests${a.totals.proposals?.pullRequests.opened ?? 0}`,
    )

    const byRepo = main.getByRole('region', { name: 'By repository' })
    await expect(byRepo.getByRole('row').filter({ hasText: REPO })).toBeVisible()
    // Your own figures do not open the Audit records behind them.
    await expect(byRepo.getByRole('link')).toHaveCount(0)
    await expect(main.getByRole('region', { name: 'By provider' })).toHaveCount(0)

    const narrowed = page.waitForRequest((r) => new URL(r.url()).pathname === '/api/activity/me')
    await main.getByLabel('Period').selectOption({ label: 'Last 30 days' })
    const from = new URL((await narrowed).url()).searchParams.get('from')
    expect(from).toBe(new Date(AFTER_CAPTURE.getTime() - 30 * 86_400_000).toISOString())
  })

  test('your permissions sit beside your activity', async ({ asRole }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/me/activity')
    await page
      .getByRole('navigation', { name: 'Your account' })
      .getByRole('link', { name: 'Permissions' })
      .click()
    await expect(page).toHaveURL(/\/dashboard\/me\/permissions$/)
    await expect(page.getByRole('main')).toContainText(`/${FIXTURE.handle}/${FIXTURE.repo}`)
  })

  test('the sidebar account links and the old profile routes lead to the split pages', async ({
    asRole,
  }) => {
    const page = await asRole('dev')
    await page.goto('/dashboard/')
    const side = page.locator('aside')

    await side.locator('a[href$="/me"]').click()
    await expect(page).toHaveURL(/\/dashboard\/me\/activity$/)
    await side.getByRole('link', { name: 'Profile settings' }).click()
    await expect(page).toHaveURL(/\/dashboard\/profile\/emails$/)

    await page.goto('/dashboard/profile/activity')
    await expect(page).toHaveURL(/\/dashboard\/me\/activity$/)
    await page.goto('/dashboard/profile/permissions')
    await expect(page).toHaveURL(/\/dashboard\/me\/permissions$/)
    // The OAuth link callback lands on /profile with its outcome in the query; the redirect keeps it.
    await page.goto('/dashboard/profile?linked=github')
    await expect(page).toHaveURL(/\/dashboard\/profile\/emails\?linked=github$/)
  })

  test('Participation is closed to a reader', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/audit/participation')
    await expect(page).toHaveURL(/\/dashboard\/?$/)
  })

  test('Participation shows everyone, then one person', async ({ asRole }) => {
    const page = await asRole('auditor')
    await atCaptureTime(page)
    const everyone = nextActivity(page, '/api/activity')
    await page.goto('/dashboard/audit/participation')
    const a = await everyone

    const main = page.getByRole('main')
    await expect(main.getByRole('group', { name: 'Contributors', exact: true })).toContainText(
      String(a.contributors),
    )
    const byProvider = main.getByRole('region', { name: 'By provider' })
    expect(a.providers.length).toBeGreaterThan(0)
    for (const g of a.providers) {
      await expect(
        byProvider
          .getByRole('row')
          .filter({ has: page.getByRole('cell', { name: g.provider, exact: true }) }),
      ).toContainText(String(total(g.pushes)))
    }

    const one = nextActivity(page, '/api/activity/users/dev')
    await main.getByLabel('Person').selectOption('dev')
    await expect(page).toHaveURL(/[?&]user=dev\b/)
    const d = await one
    await expect(main.getByRole('group', { name: 'Pushes', exact: true })).toContainText(
      `Pushes${total(d.totals.pushes)}`,
    )
    // One person's view has no per-provider split and no contributor counts.
    await expect(main.getByRole('region', { name: 'By provider' })).toHaveCount(0)
  })

  test('a Participation figure opens the Decisions records behind it', async ({ asRole }) => {
    const page = await asRole('auditor')
    await atCaptureTime(page)
    await page.goto('/dashboard/audit/participation?user=dev')

    const row = page
      .getByRole('region', { name: 'By repository' })
      .getByRole('row')
      .filter({ hasText: REPO })
    // The first link in a row is its push count.
    await row.getByRole('link').first().click()

    await expect(page).toHaveURL(/\/dashboard\/audit\/decisions\?/)
    const params = new URL(page.url()).searchParams
    expect(params.get('tab')).toBe('pushes')
    expect(params.get('provider')).toBe('github')
    expect(params.get('owner')).toBe(FIXTURE.handle)
    expect(params.get('repo')).toBe(FIXTURE.repo)
    expect(params.get('user')).toBe('dev')

    await expect(page.getByRole('tab', { name: 'Pushes', selected: true })).toBeVisible()
    await expect(page.getByLabel('Pusher')).toHaveValue('dev')
    await expect(page.getByLabel('Owner')).toHaveValue(FIXTURE.handle)
    await expect(page.locator('tbody tr').first()).toBeVisible()
  })

  test('a Rejected figure opens the rejected SCM API actions', async ({ asRole }) => {
    const page = await asRole('auditor')
    await atCaptureTime(page)
    const everyone = nextActivity(page, '/api/activity')
    await page.goto('/dashboard/audit/participation')
    const a = await everyone
    const repo = a.repos.find((g) => `${g.provider}/${g.owner}/${g.repo}` === REPO)
    expect(repo?.proposals?.rejected).toBeGreaterThan(0)

    const table = page.getByRole('region', { name: 'By repository' })
    const headers = await table.getByRole('columnheader').allInnerTexts()
    const column = headers.findIndex((h) => h.trim() === 'Rejected')
    expect(column).toBeGreaterThan(0)
    const cell = table.getByRole('row').filter({ hasText: REPO }).getByRole('cell').nth(column)
    await expect(cell).toHaveText(String(repo?.proposals?.rejected))
    await cell.getByRole('link').click()

    await expect(page).toHaveURL(/\/dashboard\/audit\/decisions\?/)
    const params = new URL(page.url()).searchParams
    expect(params.get('tab')).toBe('scm-api-actions')
    expect(params.get('status')).toBe('REJECTED')
    expect(params.get('owner')).toBe(FIXTURE.handle)
    expect(params.get('repo')).toBe(FIXTURE.repo)

    await expect(page.getByRole('tab', { name: 'SCM API actions', selected: true })).toBeVisible()
    await expect(page.getByLabel('Status')).toHaveValue('REJECTED')
    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible()
    for (const badge of await rows.locator('td:nth-child(2)').allInnerTexts()) {
      expect(badge.trim()).toBe('REJECTED')
    }
  })

  test("an admin reads a user's activity, with figures that open the records", async ({ page }) => {
    await atCaptureTime(page)
    await page.goto('/dashboard/users/dev')
    const activity = nextActivity(page, '/api/activity/users/dev')
    await page.getByRole('button', { name: 'Activity', exact: true }).click()
    const a = await activity

    const main = page.getByRole('main')
    await expect(main.getByRole('group', { name: 'Pushes', exact: true })).toContainText(
      `Pushes${total(a.totals.pushes)}`,
    )
    const row = main
      .getByRole('region', { name: 'By repository' })
      .getByRole('row')
      .filter({ hasText: REPO })
    await expect(row.getByRole('link').first()).toHaveAttribute(
      'href',
      /\/dashboard\/audit\/decisions\?.*user=dev/,
    )
  })

  test("a repository's push count opens its push history", async ({ page }) => {
    await atCaptureTime(page)
    await page.goto('/dashboard/repos')
    const history = page.waitForResponse(
      (r) => new URL(r.url()).pathname === '/api/activity/repo-pushes' && r.ok(),
    )
    await page.getByTitle('Show push activity').first().click()
    const h = (await history.then((r) => r.json())) as {
      periods: { pushes: Record<string, number> }[]
    }

    const withPushes = h.periods.filter((p) => total(p.pushes) > 0)
    expect(withPushes.length).toBeGreaterThan(0)
    const table = page
      .getByRole('table')
      .filter({ has: page.getByRole('columnheader', { name: 'By status' }) })
    await expect(table.locator('tbody tr')).toHaveCount(withPushes.length)
    const counts = await table.locator('tbody tr td:nth-child(2)').allInnerTexts()
    expect(counts.map(Number).reduce((a, b) => a + b, 0)).toBe(
      withPushes.reduce((n, p) => n + total(p.pushes), 0),
    )
  })
})
