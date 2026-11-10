import { test, expect, requireCapture } from './fixtures'

// The Audit group: user activity, access changes and current access, each a page of record-type tabs with the shared
// filters and an export of what they select, and an export of everything. Read-only; the auditor login is the primary
// audience.
test.describe('audit', () => {
  test('Audit opens on user activity', async ({ asRole }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit')
    await expect(page).toHaveURL(/\/dashboard\/audit\/user-activity$/)
    for (const label of ['Pushes', 'SCM API actions', 'Fetch activity']) {
      await expect(page.getByRole('tab', { name: label, exact: true })).toBeVisible()
    }
  })

  test('an auditor reads the push record with the shared filters', async ({ asRole }) => {
    requireCapture()
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/user-activity')
    await expect(page.getByRole('tab', { name: 'Pushes', selected: true })).toBeVisible()

    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible()
    await expect(rows.first().getByRole('link')).toHaveAttribute('href', /\/dashboard\/push\//)

    await page.getByLabel('Status').selectOption('REJECTED')
    await page.getByRole('button', { name: 'Apply filters' }).click()
    await expect(rows.first()).toBeVisible()
    for (const badge of await rows.locator('td:nth-child(2)').allInnerTexts()) {
      expect(badge.trim()).toBe('REJECTED')
    }
  })

  test('access changes have a tab per type, kept in the URL', async ({ asRole }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/access-changes')
    for (const label of ['Permission changes', 'Membership changes', 'Role changes']) {
      await expect(page.getByRole('tab', { name: label, exact: true })).toBeVisible()
    }
    await page.getByRole('tab', { name: 'Role changes' }).click()
    await expect(page).toHaveURL(/tab=role-changes/)
    await page.reload()
    await expect(page.getByRole('tab', { name: 'Role changes', selected: true })).toBeVisible()
  })

  test('a filter the listing refuses shows the reason', async ({ asRole }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/access-changes?tab=permission-changes')
    await page.getByLabel('Owner').fill('fixture-dev')
    await page.getByRole('button', { name: 'Apply filters' }).click()
    await expect(page.getByRole('alert')).toContainText(/owner and repo/i)
  })

  test('the shown records export as a zip, with the columns chosen', async ({ asRole }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/access-changes?tab=role-changes')
    await page.getByRole('button', { name: 'Export these records' }).click()
    await page.getByRole('button', { name: /^Columns/ }).click()
    await page.getByRole('button', { name: 'None', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Download' })).toBeDisabled()
    await page.getByLabel('username').check()
    await page.getByLabel('roles', { exact: true }).check()

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Download' }).click(),
    ])
    expect(download.suggestedFilename()).toMatch(/^fogwall-role-changes-.*\.zip$/)
    expect(download.url()).toContain('columns=username%2Croles')
  })

  test('current grants are listed, filtered by choosing a holder from the users that exist', async ({
    asRole,
  }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/current-access')
    await expect(page.getByRole('tab', { name: 'Grants', selected: true })).toBeVisible()

    const holder = page.getByLabel('Holder')
    await expect(holder.locator('option', { hasText: 'reviewer' })).toHaveCount(1)
    await holder.selectOption('reviewer')
    await page.getByRole('button', { name: 'Apply filters' }).click()

    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible()
    for (const name of await rows.locator('td:nth-child(1)').allInnerTexts()) {
      expect(name.trim()).toBe('reviewer')
    }
    await expect(page.getByText(/^\d+ rows?$/)).toBeVisible()

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      (async () => {
        await page.getByRole('button', { name: 'Export these records' }).click()
        await page.getByRole('button', { name: 'Download' }).click()
      })(),
    ])
    expect(download.suggestedFilename()).toMatch(/^fogwall-grants-.*\.zip$/)
    expect(download.url()).toContain('subject=reviewer')
  })

  test('group membership is listed, with the groups that exist to choose from', async ({
    asRole,
  }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/current-access?tab=group-members')
    const group = page.getByLabel('Group')
    await expect(group.locator('option')).not.toHaveCount(1)
    await expect(page.locator('tbody tr').first()).toBeVisible()
  })

  test('everything exports in one zip, limited to the types chosen', async ({ asRole }) => {
    const page = await asRole('auditor')
    await page.goto('/dashboard/audit/export')
    await expect(page.getByLabel('Pushes', { exact: true })).toBeChecked()
    await page.getByLabel('Pushes', { exact: true }).uncheck()
    await page.getByLabel('Fetch activity', { exact: true }).uncheck()

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Download' }).click(),
    ])
    expect(download.suggestedFilename()).toMatch(/^fogwall-evidence-.*\.zip$/)
    const types = new URL(download.url()).searchParams.get('types')?.split(',')
    expect(types).not.toContain('pushes')
    expect(types).not.toContain('fetch-activity')
    expect(types).toContain('role-changes')
  })

  test('a reader has no Audit pages', async ({ asRole }) => {
    const page = await asRole('observer')
    await page.goto('/dashboard/audit/user-activity')
    await expect(page).toHaveURL(/\/dashboard\/?$/)
  })
})
