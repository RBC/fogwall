import { test, expect } from './fixtures'

// Legal page: license statement, trademark notices, and the generated third-party notices table.
test('legal page renders license, trademark notices and third-party notices', async ({
  asRole,
}) => {
  const page = await asRole('observer')
  await page.goto('/dashboard/legal')

  await expect(page.getByRole('heading', { name: 'Legal' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Apache License, Version 2.0' })).toHaveAttribute(
    'href',
    'https://github.com/RBC/fogwall/blob/main/LICENSE',
  )
  await expect(page.getByRole('heading', { name: 'Trademark notices' })).toBeVisible()
  for (const mark of ['GitHub', 'GitLab']) {
    await expect(page.getByText(mark, { exact: true }).first()).toBeVisible()
  }
  // The generated THIRD-PARTY-NOTICES.json is served alongside the app; the page lists at least one module
  await expect(page.getByText(/Apache-2\.0|MIT/).first()).toBeVisible()
})
