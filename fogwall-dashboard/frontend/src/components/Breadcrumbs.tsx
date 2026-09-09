import { Link, useLocation } from 'react-router'

interface Crumb {
  label: string
  to?: string
}

// First-segment → label for the flat pages. Dynamic trails (push/:id, users/:username) are built below.
const LABELS: Record<string, string> = {
  pushes: 'Pushes',
  proposals: 'Proposals',
  issues: 'Issues',
  repos: 'Repos',
  providers: 'Providers',
  users: 'Users',
  groups: 'Groups',
  operations: 'Operations',
  'mirror-cache': 'Mirror cache',
  profile: 'Profile',
  setup: 'Setup',
  legal: 'Legal',
}

// Push/proposal record ids are long UUIDs — show a legible prefix in the trail, not the whole thing.
function shortId(id: string): string {
  return id.length > 10 ? id.slice(0, 8) + '…' : id
}

function buildTrail(pathname: string): Crumb[] {
  const seg = pathname.replace(/^\/+/, '').split('/').filter(Boolean)
  if (seg.length === 0) return [{ label: 'Overview' }]

  const trail: Crumb[] = [{ label: 'Overview', to: '/' }]
  const [first, second, third] = seg

  if (first === 'push') {
    trail.push({ label: 'Pushes', to: '/pushes' })
    const id = second ?? ''
    const onDiff = third === 'diff'
    trail.push({ label: `Push ${shortId(id)}`, to: onDiff ? `/push/${id}` : undefined })
    if (onDiff) trail.push({ label: 'Diff' })
    return trail
  }

  if (first === 'users' && second) {
    trail.push({ label: 'Users', to: '/users' })
    trail.push({ label: second })
    return trail
  }

  trail.push({ label: LABELS[first] ?? first })
  return trail
}

export function Breadcrumbs({
  collapsed,
  onToggleCollapsed,
}: {
  collapsed: boolean
  onToggleCollapsed: () => void
}) {
  const { pathname } = useLocation()
  const trail = buildTrail(pathname)

  return (
    <header className="sticky top-0 z-20 flex items-center gap-2 border-b border-gray-200/70 bg-white/70 px-4 py-2.5 backdrop-blur-md dark:border-slate-700/70 dark:bg-slate-800/80">
      <button
        onClick={onToggleCollapsed}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-expanded={!collapsed}
        title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        className="flex shrink-0 items-center justify-center rounded-lg border border-gray-300 p-1.5 text-gray-500 transition-colors hover:bg-gray-200 hover:text-gray-800 dark:border-slate-600 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-white"
      >
        <svg
          className="h-5 w-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" />
        </svg>
      </button>
      <nav aria-label="Breadcrumb" className="min-w-0 flex-1">
        <ol className="flex flex-wrap items-center gap-1.5 text-[13px] text-gray-500 dark:text-gray-400">
          {trail.map((c, i) => {
            const last = i === trail.length - 1
            return (
              <li key={i} className="flex items-center gap-1.5">
                {i > 0 && (
                  <svg
                    className="h-3.5 w-3.5 text-gray-300 dark:text-slate-600"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={2}
                    aria-hidden="true"
                  >
                    <path d="M9 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
                {c.to && !last ? (
                  <Link
                    to={c.to}
                    className="transition-colors hover:text-gray-800 dark:hover:text-gray-200"
                  >
                    {c.label}
                  </Link>
                ) : (
                  <span
                    className={last ? 'font-medium text-gray-700 dark:text-gray-300' : undefined}
                    aria-current={last ? 'page' : undefined}
                  >
                    {c.label}
                  </span>
                )}
              </li>
            )
          })}
        </ol>
      </nav>
    </header>
  )
}
