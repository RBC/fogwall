import { test as base, type Browser, type Page } from '@playwright/test'
import path from 'path'
import fs from 'fs'

// Shared knowledge about the fixture database + profile (tests/fixtures/). Keep the values here in sync with
// fogwall-playwright.yml and with the placeholders test/capture/capture.py scrubs to.

/** Local-auth users defined in fogwall-playwright.yml. */
export const USERS = {
  admin: { username: 'admin', password: 'admin' },
  dev: { username: 'dev', password: 'password' },
  reviewer: { username: 'reviewer', password: 'password' },
  observer: { username: 'observer', password: 'password' },
} as const

export type Role = keyof typeof USERS

export const authFile = (role: Role) => path.join(import.meta.dirname, `.auth/${role}.json`)

/** Placeholder identity every captured push resolves to (see capture.py MAPPING). */
export const FIXTURE = {
  handle: 'fixture-dev',
  name: 'Fixture Developer',
  email: 'fixture-dev@example.com',
  altEmail: 'fixture-alt@example.com',
  repo: 'fogwall-fixture',
  sshKeyComment: 'fixture-dev@example.com',
  sshFingerprint: 'SHA256:0i4Mdq2XSfL9suHQSgmZ5zbEakHS29Tyv6m9h7sDND4',
} as const

/** Scenario name → { id, ref } as written by capture.py. */
export type Manifest = Record<
  string,
  { id: string; ref: string; reviewer?: string; reason?: string }
>

export function loadManifest(): Manifest {
  const file = path.join(import.meta.dirname, 'fixtures/manifest.json')
  return JSON.parse(fs.readFileSync(file, 'utf8')) as Manifest
}

/**
 * Resolves a capture scenario for a spec. Locally a scenario that isn't in the manifest skips the test — convenient
 * while a capture is partial. On CI (process.env.CI) it FAILS instead: a missing scenario there means someone
 * dropped it from the manifest or forgot to re-capture after changing what a push records, and silently skipping
 * would hide exactly the regression this suite exists to catch.
 */
export function scenario(name: string): Manifest[string] {
  const entry = loadManifest()[name]
  if (!entry?.id) {
    const msg = `scenario "${name}" is not in tests/fixtures/manifest.json — run test/capture/capture.py and commit the result`
    if (process.env.CI) throw new Error(msg)
    base.skip(true, msg)
  }
  return entry
}

/** True when the fixture database was produced by a capture (not just the config-only bootstrap dump). */
export const captured = Object.keys(loadManifest()).length > 0

/** Like `scenario()` for specs that need any capture at all rather than one scenario. */
export function requireCapture(): void {
  if (!captured) {
    const msg = 'fixture database not captured yet — run test/capture/capture.py'
    if (process.env.CI) throw new Error(msg)
    base.skip(true, msg)
  }
}

async function pageAs(browser: Browser, role: Role): Promise<Page> {
  const context = await browser.newContext({ storageState: authFile(role) })
  return context.newPage()
}

/**
 * `test` with an `asRole` fixture: `const page = await asRole('reviewer')` gives a page logged in as that user in
 * its own browser context. The default `page` fixture stays logged in as admin (playwright.config.ts).
 */
export const test = base.extend<{ asRole: (role: Role) => Promise<Page> }>({
  asRole: async ({ browser }, provide) => {
    const pages: Page[] = []
    await provide(async (role) => {
      const page = await pageAs(browser, role)
      pages.push(page)
      return page
    })
    await Promise.all(pages.map((p) => p.context().close()))
  },
})

export { expect } from '@playwright/test'
