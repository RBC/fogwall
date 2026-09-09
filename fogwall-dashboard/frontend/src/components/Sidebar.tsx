import { type ReactNode } from 'react'
import { NavLink } from 'react-router'
import type { CurrentUser } from '../types'

interface SidebarProps {
  currentUser: CurrentUser | null
  dark: boolean
  toggleDark: () => void
  /** Collapsed state is owned by App (the toggle lives in the top bar); the Sidebar only renders from it. */
  collapsed: boolean
}

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function logout() {
  const token = getCsrfToken()
  await fetch('/logout', {
    method: 'POST',
    headers: token ? { 'X-XSRF-TOKEN': token } : {},
    credentials: 'same-origin',
  })
  window.location.href = '/login.html?logout'
}

// Icon set — one per destination. Stroked line icons sized to the 18px rail slot.
const icons: Record<string, ReactNode> = {
  overview: (
    <path
      d="M3 10.5 12 4l9 6.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"
      strokeLinejoin="round"
    />
  ),
  pushes: <path d="M12 19V5M12 5l-6 6M12 5l6 6" strokeLinecap="round" strokeLinejoin="round" />,
  proposals: (
    <>
      <circle cx="6" cy="6" r="2.4" />
      <circle cx="6" cy="18" r="2.4" />
      <circle cx="18" cy="9" r="2.4" />
      <path d="M6 8.4v7.2M8.4 6H14a2 2 0 0 1 2 2v.6" strokeLinecap="round" />
    </>
  ),
  issues: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 8v4M12 16h.01" strokeLinecap="round" />
    </>
  ),
  repos: (
    <path d="M5 4h11a2 2 0 0 1 2 2v14l-3-2-3 2-3-2-3 2V6a2 2 0 0 1 2-2z" strokeLinejoin="round" />
  ),
  providers: (
    <>
      <rect x="3" y="4" width="18" height="7" rx="1.6" />
      <rect x="3" y="13" width="18" height="7" rx="1.6" />
      <path d="M7 7.5h.01M7 16.5h.01" strokeLinecap="round" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8" r="3" />
      <path
        d="M3.5 20a5.5 5.5 0 0 1 11 0M16 5.2a3 3 0 0 1 0 5.6M18 13.5a5.5 5.5 0 0 1 3 5"
        strokeLinecap="round"
      />
    </>
  ),
  groups: (
    <>
      <circle cx="12" cy="7" r="3" />
      <circle cx="5.5" cy="16" r="2.4" />
      <circle cx="18.5" cy="16" r="2.4" />
      <path d="M12 10v3M9.5 15l-2 .5M14.5 15l2 .5" strokeLinecap="round" />
    </>
  ),
  operations: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path
        d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"
        strokeLinecap="round"
      />
    </>
  ),
  mirror: (
    <>
      <path d="M4 6c0-1.1 3.6-2 8-2s8 .9 8 2-3.6 2-8 2-8-.9-8-2z" />
      <path d="M4 6v12c0 1.1 3.6 2 8 2s8-.9 8-2V6" strokeLinecap="round" />
    </>
  ),
}

interface Dest {
  to: string
  label: string
  icon: string
  end?: boolean
  disabled?: boolean // not a live route yet; renders greyed out
}

const PRIMARY: Dest[] = [
  { to: '/', label: 'Overview', icon: 'overview', end: true },
  { to: '/pushes', label: 'Pushes', icon: 'pushes' },
  { to: '/proposals', label: 'Proposals', icon: 'proposals' },
  { to: '/issues', label: 'Issues', icon: 'issues' },
  { to: '/repos', label: 'Repos', icon: 'repos' },
  { to: '/providers', label: 'Providers', icon: 'providers' },
]

const ADMIN: Dest[] = [
  { to: '/users', label: 'Users', icon: 'users' },
  { to: '/groups', label: 'Groups', icon: 'groups' },
  { to: '/operations', label: 'Operations', icon: 'operations' },
  { to: '/mirror-cache', label: 'Mirror cache', icon: 'mirror' },
]

function Icon({ name }: { name: string }) {
  return (
    <svg
      className="h-[18px] w-[18px] shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.9}
      aria-hidden="true"
    >
      {icons[name]}
    </svg>
  )
}

function NavRow({ dest, collapsed }: { dest: Dest; collapsed: boolean }) {
  // Planned-but-not-live destination: shown in place, greyed out.
  if (dest.disabled) {
    return (
      <div
        title="Not available yet"
        className={
          'flex items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-sm font-medium text-slate-500 ' +
          (collapsed ? 'justify-center' : '')
        }
      >
        <Icon name={dest.icon} />
        {!collapsed && <span className="truncate">{dest.label}</span>}
      </div>
    )
  }

  return (
    <NavLink
      to={dest.to}
      end={dest.end}
      title={collapsed ? dest.label : undefined}
      className={({ isActive }) =>
        'flex items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-sm font-medium transition-colors ' +
        (collapsed ? 'justify-center ' : '') +
        (isActive ? 'bg-blue-600 text-white' : 'text-slate-200 hover:bg-slate-700')
      }
    >
      <Icon name={dest.icon} />
      {!collapsed && <span className="truncate">{dest.label}</span>}
    </NavLink>
  )
}

export function Sidebar({ currentUser, dark, toggleDark, collapsed }: SidebarProps) {
  const isAdmin = currentUser?.authorities.includes('ROLE_ADMIN') ?? false

  return (
    <aside
      className={
        'sticky top-0 flex h-screen shrink-0 flex-col border-r border-slate-900 bg-slate-800 text-slate-200 transition-[width] ' +
        (collapsed ? 'w-16' : 'w-56')
      }
    >
      {/* Header: brand → Overview. The collapse toggle lives in the top bar, next to the breadcrumb. */}
      <div
        className={
          'flex min-h-[60px] items-center gap-1 px-3 ' +
          (collapsed ? 'flex-col justify-center gap-2 py-2.5' : '')
        }
      >
        <NavLink
          to="/"
          end
          className={
            'flex items-center gap-2.5 hover:opacity-90 ' + (collapsed ? '' : 'min-w-0 flex-1')
          }
          title="fogwall — Overview"
        >
          <svg className="h-7 w-auto shrink-0" viewBox="0 0 28 32" fill="none" aria-hidden="true">
            <path
              d="M14 1 L26 6 L26 16 C26 23 14 31 14 31 C14 31 2 23 2 16 L2 6 Z"
              stroke="white"
              strokeWidth="1.8"
              strokeLinejoin="round"
              opacity="0.85"
            />
            <path
              d="M7 10 Q10 8 14 10 Q18 12 21 10"
              stroke="white"
              strokeWidth="2"
              strokeLinecap="round"
              opacity="0.9"
            />
            <path
              d="M7 15 Q10 13 14 15 Q18 17 21 15"
              stroke="white"
              strokeWidth="2"
              strokeLinecap="round"
              opacity="0.7"
            />
            <path
              d="M8 20 Q11 18 14 20 Q17 22 20 20"
              stroke="white"
              strokeWidth="2"
              strokeLinecap="round"
              opacity="0.5"
            />
          </svg>
          {!collapsed && (
            <span className="text-xl font-semibold tracking-tight text-white">fogwall</span>
          )}
        </NavLink>
      </div>

      {/* Destinations */}
      <nav className="flex-1 space-y-0.5 overflow-y-auto px-2.5 py-2">
        {PRIMARY.map((d) => (
          <NavRow key={d.to} dest={d} collapsed={collapsed} />
        ))}

        {isAdmin && (
          <>
            {!collapsed && (
              <div className="px-2.5 pb-1 pt-3 text-[10.5px] font-semibold uppercase tracking-wider text-slate-500">
                Admin
              </div>
            )}
            {collapsed && <div className="mx-2.5 my-2 border-t border-slate-700" />}
            {ADMIN.map((d) => (
              <NavRow key={d.to} dest={d} collapsed={collapsed} />
            ))}
          </>
        )}
      </nav>

      {/* Footer: preferences + profile, always visible */}
      <div className="border-t border-slate-900 px-2.5 py-2">
        <div className={'flex gap-1.5 ' + (collapsed ? 'flex-col' : '')}>
          <NavLink
            to="/setup"
            title="Setup & quick start"
            className={({ isActive }) =>
              'flex flex-1 items-center justify-center gap-1.5 rounded-md border border-slate-700 px-2 py-1.5 text-xs font-medium transition-colors ' +
              (isActive ? 'text-white' : 'text-slate-400 hover:bg-slate-700 hover:text-white')
            }
          >
            <svg
              className="h-[15px] w-[15px]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              aria-hidden="true"
            >
              <circle cx="12" cy="12" r="9.2" />
              <path d="M8.2 9a3.9 3.9 0 0 1 7.6 1c0 2-3 2.6-3 4M12 17h.01" strokeLinecap="round" />
            </svg>
            {!collapsed && <span>Setup</span>}
          </NavLink>
          <a
            href="https://rbc.github.io/fogwall/"
            target="_blank"
            rel="noopener noreferrer"
            title="Documentation (opens in a new tab)"
            className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-slate-700 px-2 py-1.5 text-xs font-medium text-slate-400 transition-colors hover:bg-slate-700 hover:text-white"
          >
            {!collapsed && <span>Docs</span>}
            <svg
              className="h-[14px] w-[14px]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              aria-hidden="true"
            >
              <path
                d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"
                strokeLinejoin="round"
              />
              <path d="M15 3h6v6" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M10 14 21 3" strokeLinecap="round" />
            </svg>
          </a>
          <button
            onClick={toggleDark}
            title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-slate-700 px-2 py-1.5 text-xs font-medium text-slate-400 transition-colors hover:bg-slate-700 hover:text-white"
          >
            {dark ? (
              <svg
                className="h-[15px] w-[15px]"
                fill="currentColor"
                viewBox="0 0 20 20"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464 4.95l.707.707a1 1 0 001.414-1.414l-.707-.707a1 1 0 00-1.414 1.414zm2.12-10.607a1 1 0 010 1.414l-.706.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM17 11a1 1 0 100-2h-1a1 1 0 100 2h1zm-7 4a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM5.05 6.464A1 1 0 106.465 5.05l-.708-.707a1 1 0 00-1.414 1.414l.707.707zm1.414 8.486l-.707.707a1 1 0 01-1.414-1.414l.707-.707a1 1 0 011.414 1.414zM4 11a1 1 0 100-2H3a1 1 0 000 2h1z"
                  clipRule="evenodd"
                />
              </svg>
            ) : (
              <svg
                className="h-[15px] w-[15px]"
                fill="currentColor"
                viewBox="0 0 20 20"
                aria-hidden="true"
              >
                <path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z" />
              </svg>
            )}
            {!collapsed && <span>{dark ? 'Light' : 'Dark'}</span>}
          </button>
        </div>

        {/* Profile — name and role (admin/user, from the mapped authorities), links to the profile page. */}
        {currentUser && (
          <NavLink
            to="/profile"
            title={currentUser.username}
            className={
              'mt-2 flex min-w-0 items-center gap-2 rounded-md px-2 py-1.5 hover:bg-slate-700 ' +
              (collapsed ? 'justify-center px-0' : '')
            }
          >
            <span className="grid h-8 w-8 shrink-0 place-items-center rounded-md bg-gradient-to-br from-blue-600 to-sky-500 text-[13px] font-bold text-white">
              {currentUser.username.charAt(0).toUpperCase()}
            </span>
            {!collapsed && (
              <span className="min-w-0">
                <span className="block truncate text-[13px] font-semibold text-white">
                  {currentUser.username}
                </span>
                <span className="block truncate text-[11px] text-slate-400">
                  {isAdmin ? 'admin' : 'user'}
                </span>
              </span>
            )}
          </NavLink>
        )}

        {/* Sign out — full width at the very bottom, away from the theme toggle so it isn't a mis-click. */}
        {currentUser && (
          <button
            onClick={logout}
            title="Sign out"
            aria-label="Sign out"
            className="mt-1.5 flex w-full items-center justify-center gap-1.5 rounded-md border border-slate-700 px-2 py-1.5 text-xs font-medium text-slate-400 transition-colors hover:bg-slate-700 hover:text-white"
          >
            <svg
              className="h-[15px] w-[15px]"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              aria-hidden="true"
            >
              <path
                d="M15 12H4M8 8l-4 4 4 4M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
            {!collapsed && <span>Sign out</span>}
          </button>
        )}

        {/* Compact legal/attribution line — always reachable in the persistent rail, costs no content height. */}
        {!collapsed && (
          <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-0.5 px-1 text-[10.5px] text-slate-500">
            <NavLink to="/legal" className="hover:text-slate-300">
              Legal
            </NavLink>
            <span aria-hidden="true">·</span>
            <a
              href="https://github.com/RBC/fogwall/blob/main/LICENSE"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-slate-300"
            >
              Apache-2.0
            </a>
            <span aria-hidden="true">·</span>
            <a
              href="https://github.com/RBC/fogwall"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-slate-300"
            >
              Source
            </a>
          </div>
        )}
      </div>
    </aside>
  )
}
