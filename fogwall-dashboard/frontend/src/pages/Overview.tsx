import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import { fetchContributionActivity } from '../api'
import type { ContributionActivity, CurrentUser } from '../types'

interface OverviewProps {
  currentUser: CurrentUser | null
}

/** The window both tile rows cover. */
const DAYS = 30

const QUICK_LINKS = [
  {
    to: '/pushes',
    title: 'Pushes',
    body: 'Every push fogwall validated — filter by status, repo, or just yours.',
  },
  {
    to: '/contributions',
    title: 'PRs & Issues',
    body: 'Pull/merge requests, issues and comments opened through the proxy.',
  },
  { to: '/repos', title: 'Repos', body: 'Proxied repositories and their access rules.' },
  { to: '/setup', title: 'Setup & quick start', body: 'Remotes, tokens, and your first push.' },
]

const SECTION =
  'mb-3 mt-8 text-xs font-bold uppercase tracking-wide text-gray-400 dark:text-gray-500'

interface Tile {
  label: string
  value: number | null
  hint: string
}

function sum(values: Record<string, number> | undefined): number {
  return Object.values(values ?? {}).reduce((a, b) => a + b, 0)
}

function useActivity(kind: 'me' | 'summary'): ContributionActivity | null | 'error' {
  const [activity, setActivity] = useState<ContributionActivity | null | 'error'>(null)
  useEffect(() => {
    const params = new URLSearchParams({
      from: new Date(Date.now() - DAYS * 86_400_000).toISOString(),
    })
    let live = true
    fetchContributionActivity({ kind }, params)
      .then((a) => live && setActivity(a))
      .catch(() => live && setActivity('error'))
    return () => {
      live = false
    }
  }, [kind])
  return activity
}

function Tiles({ tiles }: { tiles: Tile[] }) {
  return (
    <div className="grid grid-cols-2 gap-3.5 lg:grid-cols-4">
      {tiles.map((t) => (
        <div
          key={t.label}
          className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800"
        >
          <div className="text-xs font-semibold text-gray-500 dark:text-gray-400">{t.label}</div>
          <div className="my-1 text-3xl font-bold tabular-nums tracking-tight text-gray-900 dark:text-gray-100">
            {t.value === null ? '—' : t.value.toLocaleString()}
          </div>
          <div className="text-xs text-gray-400 dark:text-gray-500">{t.hint}</div>
        </div>
      ))}
    </div>
  )
}

function mineTiles(a: ContributionActivity | null): Tile[] {
  const p = a?.totals.proposals
  const pushes = a ? sum(a.totals.pushes) : null
  const forwarded = a?.totals.pushes.FORWARDED ?? 0
  const tiles: Tile[] = [
    {
      label: 'Pushes',
      value: pushes,
      hint: a ? `${forwarded.toLocaleString()} forwarded` : 'submitted · forwarded',
    },
  ]
  if (!a || a.proposalsAvailable) {
    tiles.push(
      {
        label: 'Pull requests',
        value: p ? p.pullRequests.opened : null,
        hint: p ? `${p.pullRequests.merged.toLocaleString()} merged` : 'opened · merged',
      },
      {
        label: 'Issues',
        value: p ? p.issues.opened : null,
        hint: p ? `${p.issues.closed.toLocaleString()} closed` : 'opened · closed',
      },
      {
        label: 'Edits & comments',
        value: p ? p.edited + p.commented : null,
        hint: 'on pull requests and issues',
      },
    )
  }
  return tiles
}

function everyoneTiles(a: ContributionActivity | null): Tile[] {
  const p = a?.totals.proposals
  const tiles: Tile[] = [
    {
      label: 'Contributors',
      value: a?.contributors ?? null,
      hint: 'people who pushed or proposed',
    },
    {
      label: 'Pushes forwarded',
      value: a ? (a.totals.pushes.FORWARDED ?? 0) : null,
      hint: a ? `of ${sum(a.totals.pushes).toLocaleString()} submitted` : 'of submitted',
    },
  ]
  if (!a || a.proposalsAvailable) {
    tiles.push({
      label: 'Pull requests merged',
      value: p ? p.pullRequests.merged : null,
      hint: p ? `of ${p.pullRequests.opened.toLocaleString()} opened` : 'of opened',
    })
  }
  tiles.push({
    label: 'Active repositories',
    value: a ? a.activeRepos : null,
    hint: 'with a push or a proposal',
  })
  return tiles
}

export function Overview({ currentUser }: OverviewProps) {
  const mine = useActivity('me')
  const everyone = useActivity('summary')
  const mineData = mine === 'error' ? null : mine
  const everyoneData = everyone === 'error' ? null : everyone

  return (
    <div className="max-w-6xl px-6 py-8">
      <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">
        {currentUser ? `Welcome to fogwall, ${currentUser.username}` : 'Welcome to fogwall'}
      </h1>
      <p className="mt-1 max-w-[62ch] text-sm text-gray-500 dark:text-gray-400">
        Git, secured. Every push, fetch, pull request, and issue, inspected and audited on the way
        through.
      </p>

      <div className={SECTION}>Your activity · last {DAYS} days</div>
      <Tiles tiles={mineTiles(mineData)} />

      <div className={SECTION}>Across fogwall · last {DAYS} days</div>
      <Tiles tiles={everyoneTiles(everyoneData)} />
      {everyoneData && everyoneData.repos.length > 0 && (
        <div className="mt-3.5 rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800">
          <div className="text-xs font-semibold text-gray-500 dark:text-gray-400">
            Most active repositories
          </div>
          <ul className="mt-2 divide-y divide-gray-100 text-sm dark:divide-slate-700">
            {everyoneData.repos.map((r) => {
              const pushes = sum(r.pushes)
              const proposals = r.proposals
                ? r.proposals.pullRequests.opened + r.proposals.issues.opened
                : 0
              return (
                <li
                  key={`${r.provider}/${r.owner}/${r.repo}`}
                  className="flex items-center justify-between py-1.5"
                >
                  <span className="font-mono text-xs text-gray-700 dark:text-gray-300">
                    {r.provider}/{r.owner}/{r.repo}
                  </span>
                  <span className="text-xs tabular-nums text-gray-500 dark:text-gray-400">
                    {pushes.toLocaleString()} pushes
                    {everyoneData.proposalsAvailable &&
                      ` · ${proposals.toLocaleString()} pull requests & issues`}
                    {` · ${(r.contributors ?? 0).toLocaleString()} contributors`}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      )}
      {(mine === 'error' || everyone === 'error') && (
        <p className="mt-3 text-xs text-gray-500 dark:text-gray-400">
          Activity could not be loaded.
        </p>
      )}

      <div className={SECTION}>Jump to</div>
      <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
        {QUICK_LINKS.map((q) => (
          <NavLink
            key={q.to}
            to={q.to}
            className="group rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-colors hover:border-blue-400 dark:border-slate-700 dark:bg-slate-800 dark:hover:border-blue-500"
          >
            <div className="flex items-center justify-between">
              <span className="font-semibold text-gray-800 dark:text-gray-200">{q.title}</span>
              <span className="text-gray-300 transition-colors group-hover:text-blue-500 dark:text-slate-600">
                →
              </span>
            </div>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{q.body}</p>
          </NavLink>
        ))}
      </div>
    </div>
  )
}
