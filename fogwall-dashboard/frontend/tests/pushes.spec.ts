import { test, expect, FIXTURE, scenario, requireCapture } from './fixtures'
import type { Page } from '@playwright/test'

// Push list + push detail, one test per captured scenario (see test/capture/capture.py). Every test reads the
// scenario's push id from tests/fixtures/manifest.json and skips if the scenario isn't in the fixture yet, so the
// suite stays green on the bootstrap dump and turns these on the moment a capture is committed.
const REPO = `${FIXTURE.handle}/${FIXTURE.repo}`

async function openPush(page: Page, id: string) {
  await page.goto(`/dashboard/push/${id}`)
  await expect(page.getByText('Loading…')).toHaveCount(0)
}

/** The validation-steps card row for a given display name. */
const step = (page: Page, name: string) =>
  page
    .locator('div.flex.gap-3')
    .filter({ has: page.getByText(name, { exact: true }) })
    .first()

test.describe('push list', () => {
  test('status chips carry counts and filter the list', async ({ page }) => {
    requireCapture()
    await page.goto('/dashboard/')
    for (const status of ['Pending', 'Rejected', 'Forwarded', 'Canceled']) {
      await expect(
        page.getByRole('button', { name: new RegExp(`^${status} · \\d+$`) }),
      ).toBeVisible()
    }
    await page.getByRole('button', { name: /^Rejected · \d+$/ }).click()
    const badges = page.getByText('REJECTED', { exact: true })
    await expect(badges.first()).toBeVisible()
    await expect(page.getByText('PENDING', { exact: true })).toHaveCount(0)
  })

  test('rows show repo, ref, head sha and identity resolution', async ({ page }) => {
    const { id, ref } = scenario('pending-branch')
    await page.goto('/dashboard/')
    await page.getByRole('button', { name: /^Pending · \d+$/ }).click()
    const row = page.locator('div.cursor-pointer').filter({ hasText: ref })
    await expect(row).toContainText(REPO)
    await expect(row).toContainText('identity resolved')
    await expect(row).toContainText(FIXTURE.name)
    await row.click()
    await expect(page).toHaveURL(new RegExp(`/dashboard/push/${id}$`))
  })

  test('"My pushes" as the pusher shows only their own', async ({ asRole }) => {
    scenario('pending-branch')
    const page = await asRole('dev')
    await page.goto('/dashboard/')
    await page.getByRole('button', { name: 'My pushes' }).click()
    await expect(page.getByText(/\d+ records?/)).toBeVisible()
    await expect(page.getByText('identity unresolved')).toHaveCount(0)
  })
})

test.describe('pending pushes', () => {
  test('new branch: fully verified pusher, every check passed, awaiting review', async ({
    page,
  }) => {
    const { id, ref } = scenario('pending-branch')
    await openPush(page, id)

    await expect(page.getByText('PENDING', { exact: true })).toBeVisible()
    await expect(page.getByText(ref)).toBeVisible()
    await expect(page.getByText(REPO).first()).toBeVisible()
    await expect(page.getByText('✓ identity resolved')).toBeVisible()
    await expect(page.getByRole('link', { name: FIXTURE.handle })).toBeVisible()
    await expect(page.getByText('user: dev')).toBeVisible()

    await expect(page.getByText(/Validation passed \(\d+ checks?\)/)).toBeVisible()
    await expect(page.getByText('Pending review')).toBeVisible()
    for (const name of [
      'URL allow rules',
      'Check User Permission', // proxy-mode step name has no display mapping (server mode: 'Push permissions')
      'Author emails',
      'Commit messages',
      'Commit trailers',
      'Secret scanning',
      'Diff scan',
      'Commit attribution policy',
    ]) {
      await expect(step(page, name)).toContainText('✓')
    }
    await expect(page.getByRole('heading', { name: 'Commits (1)' })).toBeVisible()
    await expect(page.getByText(`Author: ${FIXTURE.name} <${FIXTURE.email}>`)).toBeVisible()
    await expect(page.getByText('Signed-off-by:')).toBeVisible()
  })

  test('annotated tag', async ({ page }) => {
    const { id, ref } = scenario('pending-tag')
    await openPush(page, id)
    await expect(page.getByText('PENDING', { exact: true })).toBeVisible()
    expect(ref).toMatch(/^refs\/tags\//)
    await expect(page.getByText(ref)).toBeVisible()
    await expect(page.getByText('Pending review')).toBeVisible()
  })

  test('second provider (gitea)', async ({ page }) => {
    const { id } = scenario('pending-gitea')
    await openPush(page, id)
    await expect(page.getByText('PENDING', { exact: true })).toBeVisible()
    await expect(page.getByText(/gitea\.com/).first()).toBeVisible()
    await expect(page.getByText('✓ identity resolved')).toBeVisible()
  })

  test('identity resolved on gitlab but commit email unregistered → amber warning, not blocked', async ({
    page,
  }) => {
    const { id } = scenario('pending-gitlab-email-warning')
    await openPush(page, id)
    await expect(page.getByText('PENDING', { exact: true })).toBeVisible()
    await expect(page.getByText('⚠ identity resolved, email unregistered')).toBeVisible()
    await expect(step(page, 'Commit attribution policy')).toContainText('⚠')
  })

  test('allow-listed co-author and matching sign-off render as trailers', async ({ page }) => {
    const { id } = scenario('pending-trailer-coauthor-allowed')
    await openPush(page, id)
    await expect(page.getByText('PENDING', { exact: true })).toBeVisible()
    await expect(step(page, 'Commit trailers')).toContainText('✓')
    await expect(page.getByText('Co-authored-by:')).toBeVisible()
    await expect(page.getByText(/Claude <noreply@anthropic\.com>/)).toBeVisible()
    await expect(page.getByText(/Pair Partner <pair@example\.com>/)).toBeVisible()
    await expect(page.getByText(`${FIXTURE.name} <${FIXTURE.email}>`).first()).toBeVisible()
  })
})

test.describe('review panel by role', () => {
  test('reviewer (not the pusher) can approve or reject with attestations', async ({ asRole }) => {
    const { id } = scenario('pending-branch')
    const page = await asRole('reviewer')
    await openPush(page, id)

    await expect(page.getByRole('heading', { name: 'Review' })).toBeVisible()
    await expect(page.getByText(/Reviewing as/)).toContainText('reviewer')
    await expect(page.getByText('Self-approval is not permitted')).toHaveCount(0)
    await expect(page.getByText('You are self-certifying')).toHaveCount(0)
    await expect(page.getByText('Attestation', { exact: true })).toBeVisible()
    await expect(
      page.getByText(
        'I have reviewed the diff and it contains no sensitive or proprietary information',
      ),
    ).toBeVisible()
    await expect(page.getByRole('link', { name: 'Data classification policy' })).toBeVisible()
    // Buttons exist but are disabled until reason + required attestations are filled
    await expect(page.getByRole('button', { name: '✓ Approve' })).toBeDisabled()
    await expect(page.getByRole('button', { name: '✗ Reject' })).toBeDisabled()
    // Not the pusher, not admin → no cancel
    await expect(page.getByRole('button', { name: 'Cancel push' })).toHaveCount(0)
  })

  test('pusher with SELF_CERTIFY sees the self-certify banner and can cancel', async ({
    asRole,
  }) => {
    const { id } = scenario('pending-branch')
    const page = await asRole('dev')
    await openPush(page, id)

    await expect(page.getByText('You are self-certifying your own push')).toBeVisible()
    await expect(page.getByText('Self-approval is not permitted')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Cancel push' })).toBeVisible()
  })

  test('pusher WITHOUT a self-certify grant (gitea repo) is told self-approval is not permitted', async ({
    asRole,
  }) => {
    const { id } = scenario('pending-gitea')
    const page = await asRole('dev')
    await openPush(page, id)
    await expect(page.getByText('Self-approval is not permitted')).toBeVisible()
    await expect(page.getByRole('button', { name: '✓ Approve' })).toBeDisabled()
  })

  test('admin sees the admin chip and can cancel', async ({ page }) => {
    const { id } = scenario('pending-branch')
    await openPush(page, id)
    await expect(page.getByText('admin', { exact: true }).first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Cancel push' })).toBeVisible()
  })

  test('observer (no grants) gets no review panel actions enabled', async ({ asRole }) => {
    const { id } = scenario('pending-branch')
    const page = await asRole('observer')
    await openPush(page, id)
    await expect(page.getByRole('button', { name: 'Cancel push' })).toHaveCount(0)
  })
})

test.describe('rejected pushes', () => {
  const rejected = async (page: Page, name: string, stepName: string, detail?: RegExp | string) => {
    const { id } = scenario(name)
    await openPush(page, id)
    await expect(page.getByText('REJECTED', { exact: true })).toBeVisible()
    await expect(page.getByText(/Validation failed \(\d+ issues?\)/)).toBeVisible()
    await expect(step(page, stepName)).toContainText('✗')
    if (detail) await expect(step(page, stepName)).toContainText(detail)
    await expect(page.getByText('Push blocked by validation — action required')).toBeVisible()
    await expect(page.getByText(/git commit --amend/)).toBeVisible()
  }

  test('author email local part blocked (noreply)', async ({ page }) => {
    await rejected(page, 'reject-author-noreply', 'Author emails', /block local/)
  })
  test('author email domain not allowed', async ({ page }) => {
    await rejected(page, 'reject-author-domain', 'Author emails', /not in allowlist/)
  })
  test('commit message blocked literal', async ({ page }) => {
    await rejected(page, 'reject-message-literal', 'Commit messages', /WIP/)
  })
  test('commit message blocked pattern', async ({ page }) => {
    await rejected(page, 'reject-message-pattern', 'Commit messages')
  })
  test('secret in diff', async ({ page }) => {
    await rejected(page, 'reject-secret', 'Secret scanning')
  })
  test('blocked hostname literal in diff', async ({ page }) => {
    await rejected(page, 'reject-diff-literal', 'Diff scan', /internal\.corp\.example\.com/)
  })
  test('blocked URL pattern in diff (transparent proxy)', async ({ page }) => {
    await rejected(page, 'reject-diff-pattern', 'Diff scan')
  })
  test('missing DCO sign-off', async ({ page }) => {
    await rejected(page, 'reject-trailer-no-signoff', 'Commit trailers', /Signed-off-by/)
  })
  test('sign-off does not match the author', async ({ page }) => {
    await rejected(page, 'reject-trailer-signoff-mismatch', 'Commit trailers')
  })
  test('co-author outside the allow-list', async ({ page }) => {
    await rejected(
      page,
      'reject-trailer-coauthor-denied',
      'Commit trailers',
      /Co-authored-by not allowed/,
    )
  })

  test('several validators at once: every failure is listed, all six commits shown', async ({
    page,
  }) => {
    const { id } = scenario('reject-multiple')
    await openPush(page, id)
    await expect(page.getByText('REJECTED', { exact: true })).toBeVisible()
    await expect(page.getByText(/Validation failed \([4-9] issues\)/)).toBeVisible()
    for (const name of [
      'Author emails',
      'Commit messages',
      'Secret scanning',
      'Diff scan',
      'Commit trailers',
    ]) {
      await expect(step(page, name)).toContainText('✗')
    }
    await expect(page.getByRole('heading', { name: 'Commits (6)' })).toBeVisible()
    await expect(
      page.getByText('test: commit 6 — missing DCO sign-off', { exact: true }),
    ).toBeVisible()
  })

  test('pusher has no SCM identity mapping (codeberg)', async ({ page }) => {
    const { id } = scenario('reject-unmapped-identity')
    await openPush(page, id)
    await expect(page.getByText('REJECTED', { exact: true })).toBeVisible()
    // No PAT→login resolution at all: no resolved user; the block reason is shown
    // Blocked either at identity resolution or at the permission check, depending on which ran first
    await expect(page.getByText(/Identity not linked|User not authorized/).first()).toBeVisible()
    await expect(page.getByText('✓ identity resolved')).toHaveCount(0)
    await expect(page.getByText('user: dev')).toHaveCount(0)
    await expect(page.getByText(/codeberg\.org/).first()).toBeVisible()
  })

  test('rejected by a reviewer with a reason', async ({ page }) => {
    const { id, reviewer, reason } = scenario('rejected-by-reviewer')
    await openPush(page, id)
    await expect(page.getByText('REJECTED', { exact: true })).toBeVisible()
    await expect(page.getByText(new RegExp(`Rejected by ${reviewer ?? 'reviewer'}`))).toBeVisible()
    await expect(page.getByText('Push rejected — action required')).toBeVisible()
    await expect(page.getByText(/Validation passed/)).toBeVisible()
    if (reason) {
      // header card: Reviewed by <reviewer> · "<reason>"; the banner quotes it again
      await expect(page.getByText(/Reviewed by/)).toContainText(reason)
      await expect(page.getByText(`"${reason}"`).first()).toBeVisible()
    }
  })
})

test.describe('reviewed pushes', () => {
  test('three commits approved and forwarded upstream, with the reviewer and reason shown', async ({
    page,
  }) => {
    const { id, reviewer, reason } = scenario('forwarded-multi-commit')
    await openPush(page, id)
    await expect(page.getByText('FORWARDED', { exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Commits (3)' })).toBeVisible()
    for (const msg of [
      'feat(alpha): first of three',
      'feat(beta): second of three',
      'fix(gamma): third of three',
    ]) {
      await expect(page.getByText(msg, { exact: true })).toBeVisible()
    }
    await expect(page.getByText(new RegExp(`Approved by ${reviewer ?? 'reviewer'}`))).toBeVisible()
    await expect(page.getByText('Forwarded to upstream')).toBeVisible()
    await expect(page.getByText(/Reviewed by/)).toContainText(reviewer ?? 'reviewer')
    if (reason) await expect(page.getByText(/Reviewed by/)).toContainText(reason)
    // required attestation answers are recorded on the timeline
    await expect(page.getByText(/reviewed-content: true/)).toBeVisible()
  })

  test('lightweight tag approved and forwarded', async ({ page }) => {
    const { id, ref } = scenario('forwarded-lightweight-tag')
    await openPush(page, id)
    await expect(page.getByText('FORWARDED', { exact: true })).toBeVisible()
    await expect(page.getByText(ref)).toBeVisible()
  })

  test('canceled by the pusher', async ({ page }) => {
    const { id } = scenario('canceled')
    await openPush(page, id)
    await expect(page.getByText('CANCELED', { exact: true })).toBeVisible()
    await expect(page.getByText(/Canceled by dev/)).toBeVisible()
    await expect(page.getByText(/canceled this push before review/)).toBeVisible()
  })

  test('self-certified by the pusher is labelled as such', async ({ page }) => {
    const { id } = scenario('self-certified')
    await openPush(page, id)
    await expect(page.getByText('FORWARDED', { exact: true })).toBeVisible()
    await expect(page.getByText(/Approved by dev.*\[self certified\]/)).toBeVisible()
  })

  test('server mode over SSH: forwarded, upstream shown as ssh:// and not linkified', async ({
    page,
  }) => {
    const { id } = scenario('ssh-server-forwarded')
    await openPush(page, id)
    await expect(page.getByText('FORWARDED', { exact: true })).toBeVisible()
    await expect(page.getByText('Forwarded to upstream')).toBeVisible()
    await expect(page.getByText('✓ identity resolved')).toBeVisible()
    await expect(
      page.getByText('feat: pushed over the SSH transport', { exact: true }).first(),
    ).toBeVisible()
  })
})
