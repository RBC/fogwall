import { test, expect, scenario } from './fixtures'
import type { Page } from '@playwright/test'

// Review actions driven through the UI. These change the status of pending fixture records, so they live in the
// `mutations` Playwright project, which runs AFTER every read-only spec (see playwright.config.ts). The database is
// restored before each run, so the next run starts from the captured state again.
async function openPush(page: Page, id: string) {
  await page.goto(`/dashboard/push/${id}`)
  await expect(page.getByText('Loading…')).toHaveCount(0)
}

test.describe.configure({ mode: 'serial' })

test('reviewer approves a pending push with attestations and a reason', async ({ asRole }) => {
  const { id } = scenario('pending-tag')
  const page = await asRole('reviewer')
  await openPush(page, id)

  const approve = page.getByRole('button', { name: '✓ Approve' })
  await expect(approve).toBeDisabled()
  await page.getByPlaceholder(/Reason \(required/).fill('Playwright: approved via the UI')
  // still disabled until every required attestation is ticked
  await expect(approve).toBeDisabled()
  await page.getByLabel(/I have reviewed the diff/).check()
  await page.getByLabel(/complies with our open source contribution policy/).check()
  // the optional text attestation is an unlabeled input following its caption
  await page
    .locator('div')
    .filter({ hasText: /^Internal ticket or justification reference/ })
    .last()
    .getByRole('textbox')
    .fill('FW-PW-1')
  await expect(approve).toBeEnabled()
  await approve.click()

  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible()
  await expect(page.getByText(/Approved by reviewer/)).toBeVisible()
  await expect(page.getByText(/Reviewed by/)).toContainText('Playwright: approved via the UI')
  await expect(page.getByText(/ticket-ref: FW-PW-1/)).toBeVisible()
  // no review panel any more, and the pusher is told to re-push
  await expect(page.getByRole('button', { name: '✓ Approve' })).toHaveCount(0)
  await expect(page.getByText(/git push origin/).first()).toBeVisible()
})

test('reviewer rejects a pending push; the reason is shown to the pusher', async ({ asRole }) => {
  const { id } = scenario('pending-trailer-coauthor-allowed')
  const page = await asRole('reviewer')
  await openPush(page, id)

  const reject = page.getByRole('button', { name: '✗ Reject' })
  await expect(reject).toBeDisabled()
  await page.getByPlaceholder(/Reason \(required/).fill('Playwright: needs a ticket reference')
  await expect(reject).toBeEnabled() // attestations are not required to reject
  await reject.click()

  await expect(page.getByText('REJECTED', { exact: true })).toBeVisible()
  await expect(page.getByText('Push rejected — action required')).toBeVisible()
  await expect(page.getByText(/Rejected by reviewer/)).toBeVisible()
  await expect(page.getByText('"Playwright: needs a ticket reference"').first()).toBeVisible()
})

test('pusher cancels their own pending push', async ({ asRole }) => {
  const { id } = scenario('pending-gitlab-email-warning')
  const page = await asRole('dev')
  await openPush(page, id)

  await page.getByRole('button', { name: 'Cancel push' }).click()
  await expect(page.getByText('CANCELED', { exact: true })).toBeVisible()
  await expect(page.getByText(/Canceled by dev/)).toBeVisible()
  await expect(page.getByText(/canceled this push before review/)).toBeVisible()
})

test('the status chips reflect the three decisions', async ({ page }) => {
  await page.goto('/dashboard/')
  // Approved and Canceled chips exist only once such records exist; both were created above.
  await expect(page.getByRole('button', { name: /^Approved · \d+$/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /^Canceled · \d+$/ })).toBeVisible()
})
