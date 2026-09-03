import { useEffect, useMemo, useState } from 'react'
import type { ThirdPartyNoticeModule, ThirdPartyNotices } from '../types'

function ModuleRow({ module }: { module: ThirdPartyNoticeModule }) {
  const [expanded, setExpanded] = useState(false)
  const hasText = Boolean(module.licenseText || module.noticeText)

  return (
    <div className="border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800">
      <div className="flex items-center gap-3 px-4 py-2">
        <span className="font-mono text-sm text-gray-800 dark:text-gray-200 truncate">
          {module.name}
        </span>
        <span className="text-xs text-gray-400 dark:text-gray-500 shrink-0">{module.version}</span>
        <span className="text-xs uppercase text-gray-400 dark:text-gray-500 shrink-0">
          {module.ecosystem}
        </span>
        <span className="flex-1" />
        <span className="text-sm text-gray-600 dark:text-gray-300 shrink-0">
          {module.declaredLicense ?? 'Unknown'}
        </span>
        {module.licenseTextSource === 'declared-only' ? (
          <span
            className="text-xs px-2 py-0.5 rounded bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300 shrink-0"
            title="No license/notice file embedded in the resolved package — only the declared license name/URL is shown, not that package's specific copyright text."
          >
            declared only
          </span>
        ) : (
          <span className="text-xs px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300 shrink-0">
            embedded text
          </span>
        )}
        {hasText && (
          <button
            onClick={() => setExpanded((e) => !e)}
            className="text-xs px-2 py-0.5 rounded border border-gray-300 dark:border-slate-600 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-slate-700 shrink-0"
          >
            {expanded ? 'Hide text' : 'View text'}
          </button>
        )}
      </div>
      {module.url && (
        <div className="px-4 pb-2 -mt-1">
          <a
            href={module.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-blue-500 hover:text-blue-600 no-underline hover:underline"
          >
            {module.url}
          </a>
        </div>
      )}
      {expanded && hasText && (
        <div className="border-t border-gray-200 dark:border-slate-700 px-4 py-3 space-y-3">
          {module.noticeText && (
            <div>
              <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1">
                NOTICE
              </div>
              <pre className="text-xs whitespace-pre-wrap font-mono bg-gray-50 dark:bg-slate-900 text-gray-800 dark:text-gray-200 rounded p-2 max-h-64 overflow-y-auto">
                {module.noticeText}
              </pre>
            </div>
          )}
          {module.licenseText && (
            <div>
              <div className="text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1">
                LICENSE
              </div>
              <pre className="text-xs whitespace-pre-wrap font-mono bg-gray-50 dark:bg-slate-900 text-gray-800 dark:text-gray-200 rounded p-2 max-h-64 overflow-y-auto">
                {module.licenseText}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export function Legal() {
  const [notices, setNotices] = useState<ThirdPartyNotices | null>(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  useEffect(() => {
    fetch('/THIRD-PARTY-NOTICES.json')
      .then((res) => (res.ok ? res.json() : null))
      .then(setNotices)
      .catch(() => setNotices(null))
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => {
    const modules = notices?.modules ?? []
    if (!search.trim()) return modules
    const q = search.toLowerCase()
    return modules.filter(
      (m) =>
        m.name.toLowerCase().includes(q) || (m.declaredLicense ?? '').toLowerCase().includes(q),
    )
  }, [notices, search])

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-gray-800 dark:text-gray-200">Legal</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
          fogwall is licensed under the{' '}
          <a
            href="https://github.com/RBC/fogwall/blob/main/LICENSE"
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
          >
            Apache License, Version 2.0
          </a>
          . Source is available on{' '}
          <a
            href="https://github.com/RBC/fogwall"
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
          >
            GitHub
          </a>
          .
        </p>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">
          Trademark notices
        </h3>

        <div className="border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 p-4 space-y-2 text-sm text-gray-600 dark:text-gray-300">
          <p className="font-medium text-gray-800 dark:text-gray-200">GitHub</p>
          <p>
            GITHUB®, the GITHUB® logo design, the INVERTOCAT logo design, OCTOCAT®, and the OCTOCAT®
            logo design are trademarks of GitHub, Inc., registered in the United States and other
            countries.
          </p>
          <p>
            The OCTOCAT design is the exclusive property of GitHub, Inc and has been federally
            registered with the United States Copyright Office. All rights reserved.
          </p>
          <p>
            No adaptation or use of any kind of any of GitHub&apos;s registered trademarks or
            copyrights is allowed without the express written permission of GitHub, Inc. The
            Invertocat mark used on the profile page&apos;s OAuth account-linking button is used per{' '}
            <a
              href="https://brand.github.com/foundations/logo#legal"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
            >
              GitHub&apos;s logo usage guidelines
            </a>{' '}
            as a permitted social/integration button, unmodified.
          </p>
        </div>

        <div className="border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 p-4 space-y-2 text-sm text-gray-600 dark:text-gray-300">
          <p className="font-medium text-gray-800 dark:text-gray-200">GitLab</p>
          <p>
            GITLAB™ is a trademark of GitLab Inc. in the United States and other countries and
            regions. fogwall integrates with GitLab as an OAuth account-linking provider and uses
            the GitLab logo, unmodified, solely to identify that integration on the profile page;
            this does not imply affiliation, endorsement, sponsorship, or approval by GitLab Inc.
            See{' '}
            <a
              href="https://handbook.gitlab.com/handbook/marketing/brand-experience/trademark-guidelines/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
            >
              GitLab&apos;s trademark guidelines
            </a>
            .
          </p>
        </div>

        <div className="border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 p-4 space-y-2 text-sm text-gray-600 dark:text-gray-300">
          <p className="font-medium text-gray-800 dark:text-gray-200">Codeberg</p>
          <p>
            Codeberg and the Codeberg Logo are trademarks of Codeberg e.V. fogwall integrates with
            Codeberg (a hosted Forgejo instance) as an OAuth account-linking provider and uses the
            Codeberg icon mark, unmodified, solely to identify that integration on the profile page;
            this does not imply affiliation, endorsement, sponsorship, or approval by Codeberg e.V.
            The logo artwork itself is released under{' '}
            <a
              href="http://creativecommons.org/publicdomain/zero/1.0/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
            >
              CC0
            </a>{' '}
            by Codeberg, per{' '}
            <a
              href="https://codeberg.org/Codeberg/Design"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
            >
              Codeberg&apos;s design guidelines
            </a>
            .
          </p>
        </div>

        <div className="border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 p-4 space-y-2 text-sm text-gray-600 dark:text-gray-300">
          <p className="font-medium text-gray-800 dark:text-gray-200">Gitea</p>
          <p>
            Gitea and the Gitea logo are trademarks of the Gitea project. fogwall integrates with
            gitea.com (a hosted Gitea instance) as an OAuth account-linking provider and uses the
            Gitea mascot mark, unmodified, solely to identify that integration on the profile page;
            this does not imply affiliation, endorsement, sponsorship, or approval by the Gitea
            project. The logo artwork itself is released under{' '}
            <a
              href="https://creativecommons.org/licenses/by-sa/4.0/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
            >
              CC BY-SA 4.0
            </a>{' '}
            per the (now archived){' '}
            <a
              href="https://gitea.com/gitea/design"
              target="_blank"
              rel="noopener noreferrer"
              className="text-blue-500 hover:text-blue-600 no-underline hover:underline"
            >
              gitea/design
            </a>{' '}
            repository.
          </p>
        </div>
      </div>

      {!loading && notices && (
        <p className="text-xs text-gray-400 dark:text-gray-500">
          Third-party notices for the {notices.variant ?? 'unknown'} image
          {notices.generatedAt && ` — generated ${new Date(notices.generatedAt).toLocaleString()}`}.
          Entries marked <span className="font-semibold">declared only</span> have no license/notice
          file embedded in the resolved package — only the declared license name/URL is shown, not
          that package&apos;s specific copyright text.
        </p>
      )}

      {loading && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">Loading…</div>
      )}

      {!loading && !notices?.modules?.length && (
        <div className="text-center text-gray-400 dark:text-gray-500 py-16">
          Third-party notices are not available for this build.
        </div>
      )}

      {!loading && Boolean(notices?.modules?.length) && (
        <>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter by package name or license…"
            className="w-full px-3 py-2 rounded border border-gray-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm text-gray-800 dark:text-gray-200"
          />
          <p className="text-xs text-gray-400 dark:text-gray-500">
            {filtered.length} of {notices!.modules.length} dependencies
          </p>
          <div className="space-y-2">
            {filtered.map((m) => (
              <ModuleRow key={`${m.ecosystem}:${m.name}:${m.version}`} module={m} />
            ))}
          </div>
        </>
      )}
    </div>
  )
}
