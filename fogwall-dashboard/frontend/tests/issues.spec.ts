import { test, expect } from './fixtures'

// Dashboard issue filing: the form lives under Contributions, and gates on the user having linked an
// OAuth account for an issue-enabled provider. The fixture enables issues on github but seeds no OAuth tokens, so the
// provider list resolves empty and the form shows its "link your account" guidance. The happy path calls a real
// upstream and is covered by backend tests, not here.
test.describe('issues page', () => {
  test('the Report an issue nav item is a live destination under Contributions', async ({
    page,
  }) => {
    await page.goto('/dashboard/')
    await page.locator('aside').getByRole('link', { name: 'Report an issue', exact: true }).click()
    await expect(page).toHaveURL(/\/dashboard\/contributions\/issues$/)
    await expect(page.getByRole('heading', { name: 'Report an issue' })).toBeVisible()
  })

  test('the old issues route lands on the page in its new home', async ({ page }) => {
    await page.goto('/dashboard/issues')
    await expect(page).toHaveURL(/\/dashboard\/contributions\/issues$/)
  })

  test('shows the link-your-account guidance when the user has no linked provider', async ({
    page,
  }) => {
    await page.goto('/dashboard/contributions/issues')
    await expect(page.getByText(/No providers are available for issue filing/)).toBeVisible()
    await expect(page.getByRole('link', { name: 'Profile' })).toBeVisible()
  })

  test.describe('pasting a URL', () => {
    test.beforeEach(async ({ page }) => {
      await page.route('**/api/issues/providers', (r) =>
        r.fulfill({ json: [{ name: 'github' }, { name: 'gitlab' }] }),
      )
      await page.route('**/api/providers', (r) =>
        r.fulfill({
          json: [
            { name: 'github', host: 'github.com' },
            { name: 'gitlab', host: 'gitlab.com' },
            { name: 'forgejo', host: 'codeberg.org' },
          ],
        }),
      )
      await page.goto('/dashboard/contributions/issues')
    })

    test('a repository URL picks the provider, owner and repository', async ({ page }) => {
      await page.getByLabel('Paste a repository or issue URL').fill('https://gitlab.com/g/sub/proj')
      await expect(page.getByText('gitlab · g/sub/proj')).toBeVisible()
      await expect(page.getByLabel('Provider', { exact: true })).toHaveValue('gitlab')
      await expect(page.getByLabel('Owner', { exact: true })).toHaveValue('g/sub')
      await expect(page.getByLabel('Repository', { exact: true })).toHaveValue('proj')
    })

    test('an issue URL also fills the issue number on the Edit / Close tab', async ({ page }) => {
      await page.getByRole('button', { name: 'Edit / Close' }).click()
      await page
        .getByLabel('Paste a repository or issue URL')
        .fill('https://github.com/RBC/fogwall/issues/42')
      await expect(page.getByLabel('Provider', { exact: true })).toHaveValue('github')
      await expect(page.getByLabel('Owner', { exact: true })).toHaveValue('RBC')
      await expect(page.getByLabel('Repository', { exact: true })).toHaveValue('fogwall')
      await expect(page.getByLabel('Issue number')).toHaveValue('42')
      await expect(page.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    })

    test('a pull request link fills the repository but never the issue number', async ({
      page,
    }) => {
      await page.getByRole('button', { name: 'Edit / Close' }).click()
      const paste = page.getByLabel('Paste a repository or issue URL')
      await paste.fill('https://github.com/RBC/fogwall/issues/42')
      await expect(page.getByLabel('Issue number')).toHaveValue('42')
      await paste.fill('https://gitlab.com/g/sub/proj/-/merge_requests/5')
      await expect(page.getByLabel('Owner', { exact: true })).toHaveValue('g/sub')
      await expect(page.getByLabel('Repository', { exact: true })).toHaveValue('proj')
      await expect(page.getByLabel('Issue number')).toHaveValue('')
      await expect(page.getByText(/pull request numbers are not issue numbers/)).toBeVisible()
    })

    test('a GitLab work item link fills the issue number', async ({ page }) => {
      await page.getByRole('button', { name: 'Edit / Close' }).click()
      await page
        .getByLabel('Paste a repository or issue URL')
        .fill('https://gitlab.com/coopernetes/fogwall-fixture/-/work_items/1')
      await expect(page.getByLabel('Provider', { exact: true })).toHaveValue('gitlab')
      await expect(page.getByLabel('Owner', { exact: true })).toHaveValue('coopernetes')
      await expect(page.getByLabel('Repository', { exact: true })).toHaveValue('fogwall-fixture')
      await expect(page.getByLabel('Issue number')).toHaveValue('1')
    })

    test('a link to a repository sub-page still resolves owner and repository', async ({
      page,
    }) => {
      await page
        .getByLabel('Paste a repository or issue URL')
        .fill('https://github.com/RBC/fogwall/tree/main/docs')
      await expect(page.getByLabel('Owner', { exact: true })).toHaveValue('RBC')
      await expect(page.getByLabel('Repository', { exact: true })).toHaveValue('fogwall')
    })

    test('a host with no issue-enabled provider is reported and leaves the form alone', async ({
      page,
    }) => {
      await page.getByLabel('Paste a repository or issue URL').fill('https://codeberg.org/a/b')
      await expect(
        page.getByText(/No provider you can file issues on serves codeberg.org/),
      ).toBeVisible()
      await expect(page.getByLabel('Owner', { exact: true })).toHaveValue('')
    })
  })
})
