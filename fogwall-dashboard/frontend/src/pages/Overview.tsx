import { NavLink } from 'react-router'
import type { CurrentUser } from '../types'

interface OverviewProps {
  currentUser: CurrentUser | null
}

// Placeholder activity tiles. The live per-contribution and aggregate numbers are #573 (needs its own
// aggregate endpoints); until then the shape is shown but marked unavailable rather than faked.
const ACTIVITY = [
  { label: 'Pushes', hint: 'submitted · approved · forwarded' },
  { label: 'Proposals', hint: 'PRs & MRs opened' },
  { label: 'Merged', hint: 'outcome known through fogwall' },
  { label: 'Issues', hint: 'arrives with #563', tag: '#563' },
] as const

const QUICK_LINKS = [
  {
    to: '/pushes',
    title: 'Pushes',
    body: 'Every push fogwall validated — filter by status, repo, or just yours.',
  },
  {
    to: '/proposals',
    title: 'Proposals',
    body: 'Pull and merge requests opened, merged, or refused through the proxy.',
  },
  { to: '/repos', title: 'Repos', body: 'Proxied repositories and their access rules.' },
  { to: '/setup', title: 'Setup & quick start', body: 'Remotes, tokens, and your first push.' },
]

export function Overview({ currentUser }: OverviewProps) {
  return (
    <div className="max-w-6xl px-6 py-8">
      <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">
        {currentUser ? `Welcome back, ${currentUser.username}` : 'Welcome to fogwall'}
      </h1>
      <p className="mt-1 max-w-[62ch] text-sm text-gray-500 dark:text-gray-400">
        Everything that moved through fogwall — your pushes and proposals, and, for operators, the
        whole estate. This is contribution made <em>through</em> fogwall, not a full inventory of
        every repository.
      </p>

      <div className="mb-3 mt-8 text-xs font-bold uppercase tracking-wide text-gray-400 dark:text-gray-500">
        Your activity
      </div>
      <div className="grid grid-cols-2 gap-3.5 lg:grid-cols-4">
        {ACTIVITY.map((a) => (
          <div
            key={a.label}
            className="rounded-lg border border-gray-200 bg-white p-4 opacity-70 shadow-sm dark:border-slate-700 dark:bg-slate-800"
          >
            <div className="flex items-center gap-2 text-xs font-semibold text-gray-500 dark:text-gray-400">
              {a.label}
              {'tag' in a && a.tag && (
                <span className="rounded-full bg-gray-100 px-1.5 py-0.5 text-[9.5px] font-semibold text-gray-400 dark:bg-slate-700 dark:text-gray-500">
                  {a.tag}
                </span>
              )}
            </div>
            <div className="my-1 text-3xl font-bold tracking-tight text-gray-300 dark:text-slate-600">
              —
            </div>
            <div className="text-xs text-gray-400 dark:text-gray-500">{a.hint}</div>
          </div>
        ))}
      </div>
      <div className="mt-3 flex items-start gap-2 rounded-lg border border-dashed border-gray-200 bg-gray-50 px-3 py-2.5 text-xs text-gray-500 dark:border-slate-700 dark:bg-slate-800/40 dark:text-gray-400">
        <svg
          className="mt-0.5 h-3.5 w-3.5 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="9.2" />
          <path d="M12 11v5M12 8h.01" strokeLinecap="round" />
        </svg>
        <span>
          Activity metrics — per person and in aggregate for operators — arrive with the
          contribution activity view (#573).
        </span>
      </div>

      <div className="mb-3 mt-8 text-xs font-bold uppercase tracking-wide text-gray-400 dark:text-gray-500">
        Jump to
      </div>
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
